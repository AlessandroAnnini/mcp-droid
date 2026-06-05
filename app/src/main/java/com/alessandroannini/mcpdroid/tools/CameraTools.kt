package com.alessandroannini.mcpdroid.tools

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.ImageContent
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import com.alessandroannini.mcpdroid.infra.ForegroundTypeCoordinator
import com.alessandroannini.mcpdroid.server.errorResult
import com.alessandroannini.mcpdroid.server.withToolLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private const val MAX_EDGE_PX = 1280
private const val JPEG_QUALITY = 70

fun Server.registerCameraTools(context: Context, lifecycleOwner: LifecycleOwner) {
    addTool(
        name = "capture_photo",
        description = "Takes a photo and returns it as a base64-encoded JPEG (longest edge " +
            "max ${MAX_EDGE_PX}px, quality $JPEG_QUALITY). Optional 'lens' argument: " +
            "'back' (default) or 'front'. Requires CAMERA permission.",
        inputSchema = ToolSchema(
            properties = buildJsonObject {
                putJsonObject("lens") {
                    put("type", "string")
                    put("description", "'back' (default) or 'front'")
                }
            },
        ),
    ) { request ->
        withToolLog("capture_photo") {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED
            ) {
                return@withToolLog errorResult(
                    "CAMERA permission is not granted. " +
                        "Open the MCPDroid app and grant Camera permission."
                )
            }

            val lensName = (request.arguments?.get("lens") as? kotlinx.serialization.json.JsonPrimitive)?.content ?: "back"
            val lensFacing = if (lensName == "front") CameraSelector.LENS_FACING_FRONT
            else CameraSelector.LENS_FACING_BACK

            val jpegBytes = ForegroundTypeCoordinator.withType(
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
            ) { capturePhotoBytes(context, lifecycleOwner, lensFacing) }
            val b64 = Base64.encodeToString(jpegBytes, Base64.NO_WRAP)
            CallToolResult(
                content = listOf(
                    ImageContent(data = b64, mimeType = "image/jpeg")
                )
            )
        }
    }
}

private suspend fun capturePhotoBytes(
    context: Context,
    lifecycleOwner: LifecycleOwner,
    lensFacing: Int,
): ByteArray = withContext(Dispatchers.Main) {
    val tempFile = File(context.cacheDir, "mcp_photo_${System.currentTimeMillis()}.jpg")

    suspendCancellableCoroutine { cont ->
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            val cameraProvider = future.get()
            val selector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
            val imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()

            runCatching { cameraProvider.unbindAll() }
            cameraProvider.bindToLifecycle(lifecycleOwner, selector, imageCapture)

            val outputOptions = ImageCapture.OutputFileOptions.Builder(tempFile).build()
            imageCapture.takePicture(
                outputOptions,
                ContextCompat.getMainExecutor(context),
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                        runCatching { cameraProvider.unbindAll() }
                        val resized = resizeAndCompress(tempFile)
                        tempFile.delete()
                        cont.resume(resized)
                    }

                    override fun onError(exception: ImageCaptureException) {
                        runCatching { cameraProvider.unbindAll() }
                        tempFile.delete()
                        cont.resumeWithException(exception)
                    }
                }
            )

            cont.invokeOnCancellation { runCatching { cameraProvider.unbindAll() } }
        }, ContextCompat.getMainExecutor(context))
    }
}

private fun resizeAndCompress(file: File): ByteArray {
    val original = BitmapFactory.decodeFile(file.absolutePath)
    val scaled = scaleBitmap(original)
    if (scaled !== original) original.recycle()
    val result = ByteArrayOutputStream().also { out ->
        scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
    }.toByteArray()
    scaled.recycle()
    return result
}

private fun scaleBitmap(bm: Bitmap): Bitmap {
    val maxEdge = MAX_EDGE_PX
    val w = bm.width
    val h = bm.height
    if (w <= maxEdge && h <= maxEdge) return bm
    val scale = maxEdge.toFloat() / maxOf(w, h)
    return Bitmap.createScaledBitmap(bm, (w * scale).toInt(), (h * scale).toInt(), true)
}
