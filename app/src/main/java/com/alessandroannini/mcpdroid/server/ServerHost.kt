package com.alessandroannini.mcpdroid.server

import android.content.Context
import android.util.Log
import androidx.lifecycle.LifecycleOwner
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.EngineConnectorBuilder
import io.ktor.server.engine.embeddedServer
import io.modelcontextprotocol.kotlin.sdk.server.mcpStreamableHttp

private const val TAG = "ServerHost"

/**
 * Manages the lifecycle of the embedded Ktor server hosting the MCP endpoint.
 *
 * [start] is non-blocking: the server runs on its own thread pool managed by
 * the CIO engine. [stop] performs a graceful shutdown.
 */
object ServerHost {

    @Volatile
    private var server: EmbeddedServer<*, *>? = null

    val isRunning: Boolean get() = server != null

    /**
     * Starts the MCP HTTP server on [port], binding to all interfaces.
     * DNS-rebinding protection is disabled because the MCP client connects from a
     * non-localhost address (Tailscale). Security is the bearer token + tailnet.
     */
    fun start(context: Context, port: Int, token: String, lifecycleOwner: LifecycleOwner) {
        if (server != null) {
            Log.w(TAG, "start() called while already running; ignoring")
            return
        }
        Log.i(TAG, "Starting MCP server on port $port")
        server = embeddedServer(CIO, configure = {
            connectors.add(EngineConnectorBuilder().apply {
                host = "0.0.0.0"
                this.port = port
            })
            connectionIdleTimeoutSeconds = 15
        }) {
            installBearerAuth(token)
            mcpStreamableHttp(enableDnsRebindingProtection = false) {
                buildMcpServer(context, lifecycleOwner)
            }
        }.start(wait = false)
        Log.i(TAG, "MCP server started")
    }

    fun stop() {
        val s = server ?: return
        Log.i(TAG, "Stopping MCP server")
        s.stop(gracePeriodMillis = 1_000, timeoutMillis = 3_000)
        server = null
        Log.i(TAG, "MCP server stopped")
    }
}
