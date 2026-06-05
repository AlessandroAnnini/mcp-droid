package com.alessandroannini.mcpdroid.server

import android.content.Context
import androidx.lifecycle.LifecycleOwner
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import com.alessandroannini.mcpdroid.tools.registerBrowserTools
import com.alessandroannini.mcpdroid.tools.registerCameraTools
import com.alessandroannini.mcpdroid.tools.registerClipboardTools
import com.alessandroannini.mcpdroid.tools.registerDeviceTools
import com.alessandroannini.mcpdroid.tools.registerFileTools
import com.alessandroannini.mcpdroid.tools.registerLocationTools
import com.alessandroannini.mcpdroid.tools.registerNotificationTools
import com.alessandroannini.mcpdroid.tools.registerScreenshotTools
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Builds a fresh MCP [Server] with all registered tools.
 *
 * Called once per MCP client session by [ServerHost]. Each session gets its
 * own Server instance; handlers are stateless and delegate to infrastructure
 * wrappers in the tools/ and infra/ packages.
 */
fun buildMcpServer(context: Context, lifecycleOwner: LifecycleOwner): Server {
    val server = Server(
        serverInfo = Implementation(name = "phone", version = "1.0.0"),
        options = ServerOptions(
            capabilities = ServerCapabilities(
                tools = ServerCapabilities.Tools(listChanged = true),
            ),
        ),
    )

    // M0: health check
    server.addTool(
        name = "ping",
        description = "Health check. Returns ok and a Unix timestamp from the phone.",
    ) { _ ->
        withToolLog("ping") {
            toolResult(buildJsonObject {
                put("ok", true)
                put("ts", System.currentTimeMillis())
            })
        }
    }

    // M2: easy tools (no special permissions)
    server.registerDeviceTools(context)
    server.registerClipboardTools(context)
    server.registerBrowserTools(context)

    // M3: location and files
    server.registerLocationTools(context)
    server.registerFileTools(context)

    // M4: camera and screenshot
    server.registerCameraTools(context, lifecycleOwner)
    server.registerScreenshotTools()

    // M5: notifications
    server.registerNotificationTools(context)

    return server
}
