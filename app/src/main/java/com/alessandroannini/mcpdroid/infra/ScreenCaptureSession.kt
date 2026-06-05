package com.alessandroannini.mcpdroid.infra

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

private const val TAG = "ScreenCaptureSession"

/**
 * Manages a single, persistent [MediaProjection] session: one VirtualDisplay
 * and one ImageReader that keeps the most recent rendered frame in memory so
 * any number of captures can be served from a single user-consent token.
 *
 * Android 14+ rules enforced here:
 * - [MediaProjection.registerCallback] is called before [createVirtualDisplay].
 * - [createVirtualDisplay] is called exactly once per token.
 * - When the system stops the projection (screen lock, another session, etc.)
 *   [MediaProjection.Callback.onStop] fires, which calls [stop] and then
 *   invokes [onStopped] so the caller can release the FGS type.
 *
 * Thread-safety: [start] and [stop] are synchronized. [capture] dispatches to
 * IO and reads the latest image under a short lock.
 */
object ScreenCaptureSession {

    @Volatile
    private var imageReader: ImageReader? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var projection: MediaProjection? = null

    val isActive: Boolean get() = imageReader != null

    /**
     * Starts the capture session. Must be called AFTER the service is already
     * running as FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION (the caller enforces
     * this ordering). Returns false if the session could not be created.
     *
     * [onStopped] is invoked on the main thread when the system stops the
     * projection (e.g. screen lock or another app starting a projection).
     */
    @Synchronized
    fun start(context: Context, mp: MediaProjection, onStopped: () -> Unit): Boolean {
        stop() // clean up any previous session
        return try {
            val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getRealMetrics(metrics)

            val width = metrics.widthPixels
            val height = metrics.heightPixels
            val density = metrics.densityDpi

            val reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)

            // registerCallback is required before createVirtualDisplay on Android 14+.
            mp.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() {
                    Log.i(TAG, "MediaProjection stopped by system")
                    stop()
                    onStopped()
                }

                override fun onCapturedContentResize(width: Int, height: Int) {
                    Log.d(TAG, "Captured content resized to ${width}x${height}")
                    // For a personal tool, we ignore resize; the next capture will
                    // simply have a potential crop mismatch. Resizing the VirtualDisplay
                    // dynamically is deferred to a future enhancement.
                }
            }, Handler(Looper.getMainLooper()))

            val vd = mp.createVirtualDisplay(
                "hermesScreenshot",
                width, height, density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                reader.surface, null, null,
            )

            imageReader = reader
            virtualDisplay = vd
            projection = mp
            Log.i(TAG, "Screen capture session started (${width}x${height})")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start screen capture session: ${e.message}")
            false
        }
    }

    /**
     * Captures the latest rendered frame and returns it as a downscaled JPEG.
     * Returns null if the session is inactive or no frame is available.
     */
    suspend fun capture(maxEdgePx: Int, quality: Int): ByteArray? =
        withContext(Dispatchers.IO) {
            val reader = imageReader ?: return@withContext null

            val image = try {
                reader.acquireLatestImage()
                    ?: run {
                        // No frame yet (display may need a moment after session start).
                        delay(300)
                        reader.acquireLatestImage()
                    }
            } catch (_: IllegalStateException) {
                // Reader was closed between our null-check and the acquire call.
                return@withContext null
            } ?: return@withContext null

            try {
                val plane = image.planes[0]
                val pixelStride = plane.pixelStride
                val rowStride = plane.rowStride
                val rowPadding = rowStride - pixelStride * image.width

                val raw = Bitmap.createBitmap(
                    image.width + rowPadding / pixelStride,
                    image.height,
                    Bitmap.Config.ARGB_8888,
                )
                raw.copyPixelsFromBuffer(plane.buffer)

                val cropped = if (rowPadding > 0) {
                    val c = Bitmap.createBitmap(raw, 0, 0, image.width, image.height)
                    raw.recycle()
                    c
                } else {
                    raw
                }

                val scaled = scaleBitmap(cropped, maxEdgePx)
                if (scaled !== cropped) cropped.recycle()

                val result = ByteArrayOutputStream().also { out ->
                    scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)
                }.toByteArray()
                scaled.recycle()
                result
            } finally {
                image.close()
            }
        }

    /** Releases all resources. Safe to call multiple times. */
    @Synchronized
    fun stop() {
        val wasActive = imageReader != null
        virtualDisplay?.release()
        virtualDisplay = null
        imageReader?.close()
        imageReader = null
        // Null out the reference before calling stop() so that if stop() throws
        // on a misbehaving device, the reference doesn't linger. Also avoids
        // re-stopping an already-stopped projection in the onStop callback chain.
        val mp = projection
        projection = null
        runCatching { mp?.stop() }
        if (wasActive) Log.i(TAG, "Screen capture session stopped")
    }

    private fun scaleBitmap(bm: Bitmap, maxEdge: Int): Bitmap {
        val w = bm.width
        val h = bm.height
        if (w <= maxEdge && h <= maxEdge) return bm
        val scale = maxEdge.toFloat() / maxOf(w, h)
        return Bitmap.createScaledBitmap(bm, (w * scale).toInt(), (h * scale).toInt(), true)
    }
}
