package com.alessandroannini.mcpdroid.notifications

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import java.util.ArrayDeque

private const val TAG = "McpNLS"
private const val RING_BUFFER_SIZE = 100

/**
 * Captures active notifications into an in-memory ring buffer.
 * The MCP tool list_notifications (M5) reads from [recent].
 *
 * The user must grant special access in Android settings
 * (Settings > Apps > Special app access > Notification access).
 */
class McpNotificationListenerService : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val entry = NotificationEntry(
            packageName = sbn.packageName,
            title = sbn.notification.extras
                .getCharSequence("android.title")?.toString() ?: "",
            text = sbn.notification.extras
                .getCharSequence("android.text")?.toString() ?: "",
            postTime = sbn.postTime,
        )
        synchronized(recent) {
            recent.addFirst(entry)
            if (recent.size > RING_BUFFER_SIZE) recent.removeLast()
        }
        Log.d(TAG, "Captured notification from ${sbn.packageName}")
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        // Remove matching entry from the buffer so list_notifications
        // only shows currently active notifications.
        synchronized(recent) {
            recent.removeIf {
                it.packageName == sbn.packageName && it.postTime == sbn.postTime
            }
        }
    }

    companion object {
        private val recent = ArrayDeque<NotificationEntry>(RING_BUFFER_SIZE)

        /** Returns a snapshot of recent notifications, optionally filtered by package name. */
        fun getRecent(packageFilter: String? = null, limit: Int = 20): List<NotificationEntry> =
            synchronized(recent) {
                val filtered = if (packageFilter != null)
                    recent.filter { it.packageName.contains(packageFilter, ignoreCase = true) }
                else
                    recent.toList()
                filtered.take(limit)
            }
    }
}

data class NotificationEntry(
    val packageName: String,
    val title: String,
    val text: String,
    val postTime: Long,
)
