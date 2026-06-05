package com.alessandroannini.mcpdroid.server

import android.util.Log
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.UUID

private const val TAG = "McpTool"

/**
 * Returns a successful [CallToolResult] with [payload] serialized as a JSON string
 * inside a [TextContent] block.
 */
fun toolResult(payload: JsonObject): CallToolResult =
    CallToolResult(content = listOf(TextContent(text = payload.toString())))

/**
 * Returns an error [CallToolResult] that the MCP client will surface to the user.
 * [message] should be human-readable and, where relevant, tell the user which
 * Android permission to grant or which UI action to take.
 */
fun errorResult(message: String): CallToolResult =
    CallToolResult(
        content = listOf(
            TextContent(
                text = buildJsonObject { put("error", message) }.toString()
            )
        ),
        isError = true,
    )

/**
 * Wraps a tool handler with structured logging: one event per call with tool name,
 * request_id, duration_ms, and outcome. Per logging.mdc.
 *
 * Usage inside McpServerFactory:
 * ```
 * server.addTool("my_tool", "...") { request ->
 *     withToolLog("my_tool") {
 *         // handler body
 *     }
 * }
 * ```
 */
suspend fun withToolLog(
    toolName: String,
    block: suspend () -> CallToolResult,
): CallToolResult {
    val requestId = UUID.randomUUID().toString().take(8)
    val start = System.currentTimeMillis()
    return try {
        val result = block()
        val duration = System.currentTimeMillis() - start
        val outcome = if (result.isError == true) "error" else "success"
        Log.i(
            TAG,
            """{"tool":"$toolName","request_id":"$requestId","duration_ms":$duration,"outcome":"$outcome"}"""
        )
        result
    } catch (e: Exception) {
        val duration = System.currentTimeMillis() - start
        Log.e(
            TAG,
            """{"tool":"$toolName","request_id":"$requestId","duration_ms":$duration,"outcome":"error","error_type":"${e.javaClass.simpleName}","error_message":"${e.message}"}"""
        )
        errorResult("Unexpected error in $toolName: ${e.message}")
    }
}
