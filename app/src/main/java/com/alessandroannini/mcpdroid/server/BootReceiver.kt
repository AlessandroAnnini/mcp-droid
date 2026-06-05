package com.alessandroannini.mcpdroid.server

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.alessandroannini.mcpdroid.data.Settings

/**
 * Starts the MCP server on device boot, but only when the user has opted in
 * via the settings toggle (default: off).
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        if (Settings.getAutostart(context)) {
            McpServerService.start(context)
        }
    }
}
