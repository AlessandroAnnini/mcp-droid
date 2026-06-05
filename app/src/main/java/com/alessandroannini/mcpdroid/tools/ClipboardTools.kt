package com.alessandroannini.mcpdroid.tools

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import com.alessandroannini.mcpdroid.server.errorResult
import com.alessandroannini.mcpdroid.server.toolResult
import com.alessandroannini.mcpdroid.server.withToolLog
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

fun Server.registerClipboardTools(context: Context) {
    addTool(
        name = "read_clipboard",
        description = "Returns the current text on the clipboard. Note: on Android 10+, " +
            "reading the clipboard from the background returns empty unless this app " +
            "is in the foreground. If the result is empty, open the app and try again.",
    ) { _ ->
        withToolLog("read_clipboard") {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val text = cm.primaryClip
                ?.getItemAt(0)
                ?.coerceToText(context)
                ?.toString()
                .orEmpty()
            if (text.isEmpty()) {
                toolResult(buildJsonObject {
                    put("text", "")
                    put("note", "Clipboard is empty or could not be read from the background. " +
                        "Open the MCPDroid app and try again.")
                })
            } else {
                toolResult(buildJsonObject { put("text", text) })
            }
        }
    }

    addTool(
        name = "write_clipboard",
        description = "Writes text to the device clipboard.",
        inputSchema = ToolSchema(
            properties = buildJsonObject {
                putJsonObject("text") {
                    put("type", "string")
                    put("description", "The text to write to the clipboard")
                }
            },
            required = listOf("text"),
        ),
    ) { request ->
        withToolLog("write_clipboard") {
            val text = request.arguments?.get("text")?.jsonPrimitive?.content
                ?: return@withToolLog errorResult("Missing required argument: text")
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("hermes", text))
            toolResult(buildJsonObject { put("ok", true) })
        }
    }
}
