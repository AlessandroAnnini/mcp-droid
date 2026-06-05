package com.alessandroannini.mcpdroid.tools

import android.content.Context
import android.content.Intent
import android.net.Uri
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import com.alessandroannini.mcpdroid.server.errorResult
import com.alessandroannini.mcpdroid.server.toolResult
import com.alessandroannini.mcpdroid.server.withToolLog
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

private val ALLOWED_SCHEMES = setOf("http", "https")

fun Server.registerBrowserTools(context: Context) {
    addTool(
        name = "open_url",
        description = "Opens a URL in the default browser. Only http and https URLs are allowed.",
        inputSchema = ToolSchema(
            properties = buildJsonObject {
                putJsonObject("url") {
                    put("type", "string")
                    put("description", "The URL to open (http or https)")
                }
            },
            required = listOf("url"),
        ),
    ) { request ->
        withToolLog("open_url") {
            val url = request.arguments?.get("url")?.jsonPrimitive?.content
                ?: return@withToolLog errorResult("Missing required argument: url")
            val uri = runCatching { Uri.parse(url) }.getOrNull()
                ?: return@withToolLog errorResult("Invalid URL: $url")
            if (uri.scheme?.lowercase() !in ALLOWED_SCHEMES) {
                return@withToolLog errorResult(
                    "URL scheme '${uri.scheme}' is not allowed. Only http and https are permitted."
                )
            }
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            toolResult(buildJsonObject {
                put("ok", true)
                put("url", url)
            })
        }
    }
}
