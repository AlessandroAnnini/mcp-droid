package com.alessandroannini.mcpdroid.tools

import android.util.Base64
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.ImageContent
import com.alessandroannini.mcpdroid.infra.ScreenCaptureSession
import com.alessandroannini.mcpdroid.server.errorResult
import com.alessandroannini.mcpdroid.server.withToolLog

private const val MAX_EDGE_PX = 1280
private const val JPEG_QUALITY = 70

fun Server.registerScreenshotTools() {
    addTool(
        name = "capture_screenshot",
        description = "Captures a screenshot and returns it as a base64-encoded JPEG " +
            "(longest edge max ${MAX_EDGE_PX}px, quality $JPEG_QUALITY). " +
            "Requires one-time setup: open the MCPDroid app and tap " +
            "'Enable Screenshot'. After that, screenshots can be taken repeatedly " +
            "without re-prompting until the session is stopped or revoked by the system " +
            "(e.g. when the screen locks). If capture returns an error, re-enable in the app.",
    ) { _ ->
        withToolLog("capture_screenshot") {
            if (!ScreenCaptureSession.isActive) {
                return@withToolLog errorResult(
                    "Screen capture is not enabled. " +
                        "Open the MCPDroid app and tap 'Enable Screenshot'."
                )
            }

            val bytes = ScreenCaptureSession.capture(MAX_EDGE_PX, JPEG_QUALITY)
                ?: return@withToolLog errorResult(
                    "Could not capture a frame. The session may have been revoked. " +
                        "Open the app and tap 'Enable Screenshot' to restart it."
                )

            CallToolResult(
                content = listOf(
                    ImageContent(
                        data = Base64.encodeToString(bytes, Base64.NO_WRAP),
                        mimeType = "image/jpeg",
                    )
                )
            )
        }
    }
}
