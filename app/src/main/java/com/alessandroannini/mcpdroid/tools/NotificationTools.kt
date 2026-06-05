package com.alessandroannini.mcpdroid.tools

import android.content.Context
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import com.alessandroannini.mcpdroid.notifications.McpNotificationListenerService
import com.alessandroannini.mcpdroid.server.toolResult
import com.alessandroannini.mcpdroid.server.withToolLog
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import kotlinx.serialization.json.add

fun Server.registerNotificationTools(@Suppress("UNUSED_PARAMETER") context: Context) {
    addTool(
        name = "list_notifications",
        description = "Returns recent active notifications. Optional 'app' argument filters " +
            "by package name (substring match). Optional 'limit' argument (default 20, max 50). " +
            "Requires notification listener access granted in Android Settings > " +
            "Apps > Special app access > Notification access.",
        inputSchema = ToolSchema(
            properties = buildJsonObject {
                putJsonObject("app") {
                    put("type", "string")
                    put("description", "Filter by app package name (substring match)")
                }
                putJsonObject("limit") {
                    put("type", "integer")
                    put("description", "Max notifications to return (default 20, max 50)")
                }
            },
        ),
    ) { request ->
        withToolLog("list_notifications") {
            val appFilter = (request.arguments?.get("app") as? kotlinx.serialization.json.JsonPrimitive)?.content
            val limit = request.arguments?.get("limit")
                ?.jsonPrimitive?.content?.toIntOrNull()
                ?.coerceIn(1, 50)
                ?: 20

            val entries = McpNotificationListenerService.getRecent(appFilter, limit)

            toolResult(buildJsonObject {
                put("notifications", buildJsonArray {
                    entries.forEach { entry ->
                        add(buildJsonObject {
                            put("app", entry.packageName)
                            put("title", entry.title)
                            put("text", entry.text)
                            put("time_ms", entry.postTime)
                        })
                    }
                })
                put("count", entries.size)
            })
        }
    }
}
