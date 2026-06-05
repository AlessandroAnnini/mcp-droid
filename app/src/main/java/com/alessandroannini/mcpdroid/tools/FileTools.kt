package com.alessandroannini.mcpdroid.tools

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import android.util.Base64
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import com.alessandroannini.mcpdroid.server.errorResult
import com.alessandroannini.mcpdroid.server.toolResult
import com.alessandroannini.mcpdroid.server.withToolLog
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import kotlinx.serialization.json.add

private const val MAX_FILE_BYTES = 5 * 1024 * 1024 // 5 MB
private const val DEFAULT_LIST_LIMIT = 20

private val MEDIA_COLLECTIONS = listOf(
    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
    MediaStore.Files.getContentUri("external"),
)

fun Server.registerFileTools(context: Context) {
    addTool(
        name = "list_files",
        description = "Lists recent files from MediaStore (Downloads, Images, Documents). " +
            "Returns name, size in bytes, MIME type, and content URI. " +
            "Optional 'limit' argument (default $DEFAULT_LIST_LIMIT, max 100).",
        inputSchema = ToolSchema(
            properties = buildJsonObject {
                putJsonObject("limit") {
                    put("type", "integer")
                    put("description", "Max files to return (default $DEFAULT_LIST_LIMIT, max 100)")
                }
            },
        ),
    ) { request ->
        withToolLog("list_files") {
            val limit = request.arguments?.get("limit")
                ?.jsonPrimitive?.content?.toIntOrNull()
                ?.coerceIn(1, 100)
                ?: DEFAULT_LIST_LIMIT

            val projection = arrayOf(
                MediaStore.MediaColumns.DISPLAY_NAME,
                MediaStore.MediaColumns.SIZE,
                MediaStore.MediaColumns.MIME_TYPE,
                MediaStore.MediaColumns._ID,
            )

            val results = mutableListOf<kotlinx.serialization.json.JsonObject>()
            for (collectionUri in MEDIA_COLLECTIONS) {
                if (results.size >= limit) break
                val remaining = limit - results.size
                runCatching {
                    context.contentResolver.query(
                        collectionUri,
                        projection,
                        null, null,
                        "${MediaStore.MediaColumns.DATE_ADDED} DESC",
                    )?.use { cursor ->
                        val nameCol = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                        val sizeCol = cursor.getColumnIndex(MediaStore.MediaColumns.SIZE)
                        val mimeCol = cursor.getColumnIndex(MediaStore.MediaColumns.MIME_TYPE)
                        val idCol = cursor.getColumnIndex(MediaStore.MediaColumns._ID)
                        var count = 0
                        while (cursor.moveToNext() && count < remaining) {
                            val id = cursor.getLong(idCol)
                            val contentUri = Uri.withAppendedPath(collectionUri, id.toString())
                            results.add(buildJsonObject {
                                put("name", cursor.getString(nameCol) ?: "")
                                put("size_bytes", cursor.getLong(sizeCol))
                                put("mime_type", cursor.getString(mimeCol) ?: "")
                                put("uri", contentUri.toString())
                            })
                            count++
                        }
                    }
                }
            }

            toolResult(buildJsonObject {
                put("files", buildJsonArray { results.forEach { add(it) } })
                put("count", results.size)
            })
        }
    }

    addTool(
        name = "read_file",
        description = "Reads a file by content URI. Returns text for text/* MIME types, " +
            "or base64-encoded content for binary files. " +
            "Files larger than 5 MB are rejected.",
        inputSchema = ToolSchema(
            properties = buildJsonObject {
                putJsonObject("uri") {
                    put("type", "string")
                    put("description", "Content URI of the file (from list_files)")
                }
            },
            required = listOf("uri"),
        ),
    ) { request ->
        withToolLog("read_file") {
            val uriString = request.arguments?.get("uri")?.jsonPrimitive?.content
                ?: return@withToolLog errorResult("Missing required argument: uri")
            val uri = runCatching { Uri.parse(uriString) }.getOrNull()
                ?: return@withToolLog errorResult("Invalid URI: $uriString")

            val mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"
            val bytes = context.contentResolver.openInputStream(uri)?.use { stream ->
                stream.readBytes()
            } ?: return@withToolLog errorResult("Could not open file: $uriString")

            if (bytes.size > MAX_FILE_BYTES) {
                return@withToolLog errorResult(
                    "File is ${bytes.size / 1024 / 1024} MB, which exceeds the 5 MB limit."
                )
            }

            toolResult(buildJsonObject {
                put("uri", uriString)
                put("mime_type", mimeType)
                put("size_bytes", bytes.size)
                if (mimeType.startsWith("text/")) {
                    put("encoding", "utf8")
                    put("content", bytes.toString(Charsets.UTF_8))
                } else {
                    put("encoding", "base64")
                    put("content", Base64.encodeToString(bytes, Base64.NO_WRAP))
                }
            })
        }
    }

    addTool(
        name = "share_file",
        description = "Shares a file via Android's share sheet. Provide a MediaStore content URI.",
        inputSchema = ToolSchema(
            properties = buildJsonObject {
                putJsonObject("uri") {
                    put("type", "string")
                    put("description", "Content URI of the file to share (from list_files)")
                }
            },
            required = listOf("uri"),
        ),
    ) { request ->
        withToolLog("share_file") {
            val uriString = request.arguments?.get("uri")?.jsonPrimitive?.content
                ?: return@withToolLog errorResult("Missing required argument: uri")
            val uri = runCatching { Uri.parse(uriString) }.getOrNull()
                ?: return@withToolLog errorResult("Invalid URI: $uriString")

            val mimeType = context.contentResolver.getType(uri) ?: "*/*"
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Share via").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
            toolResult(buildJsonObject { put("ok", true) })
        }
    }
}
