package com.alessandroannini.mcpdroid.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.alessandroannini.mcpdroid.infra.AddressInfo
import com.alessandroannini.mcpdroid.infra.getNetworkAddresses

data class MainScreenState(
    val isRunning: Boolean,
    val port: Int,
    val token: String,
    val autostartEnabled: Boolean,
    val screenshotEnabled: Boolean,
)

data class PermissionsState(
    val cameraGranted: Boolean = false,
    val locationGranted: Boolean = false,
    val bluetoothGranted: Boolean = false,
    val mediaGranted: Boolean = false,
    val notificationsGranted: Boolean = false,
    val notificationListenerGranted: Boolean = false,
)

@Composable
fun MainScreen(
    state: MainScreenState,
    permissions: PermissionsState = PermissionsState(),
    modifier: Modifier = Modifier,
    onStartStop: () -> Unit,
    onAutostartToggle: (Boolean) -> Unit,
    onEnableScreenshot: () -> Unit,
    onDisableScreenshot: () -> Unit = {},
    onRequestCamera: () -> Unit = {},
    onRequestLocation: () -> Unit = {},
    onRequestBluetooth: () -> Unit = {},
    onRequestMedia: () -> Unit = {},
    onRequestNotifications: () -> Unit = {},
    onRequestNotificationListener: () -> Unit = {},
    onOpenAutostart: () -> Unit = {},
) {
    val context = LocalContext.current
    val addresses = remember(state.isRunning) { getNetworkAddresses() }
    val primaryAddress = remember(addresses) { addresses.firstOrNull { it.isTailscale } ?: addresses.firstOrNull() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "MCPDroid",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )

        StatusCard(isRunning = state.isRunning, port = state.port)

        Button(
            onClick = onStartStop,
            modifier = Modifier.fillMaxWidth(),
            colors = if (state.isRunning)
                ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            else
                ButtonDefaults.buttonColors(),
        ) {
            Text(if (state.isRunning) "Stop Server" else "Start Server")
        }

        TokenCard(token = state.token)

        AddressesCard(addresses = addresses)

        if (primaryAddress != null) {
            McpConfigCard(
                address = primaryAddress.address,
                hostname = primaryAddress.hostname,
                port = state.port,
                token = state.token,
            )
        }

        BatteryOptimizationCard()

        PermissionsCard(
            permissions = permissions,
            onRequestCamera = onRequestCamera,
            onRequestLocation = onRequestLocation,
            onRequestBluetooth = onRequestBluetooth,
            onRequestMedia = onRequestMedia,
            onRequestNotifications = onRequestNotifications,
            onRequestNotificationListener = onRequestNotificationListener,
            onOpenAutostart = onOpenAutostart,
        )

        ScreenshotConsentCard(
            enabled = state.screenshotEnabled,
            onEnable = onEnableScreenshot,
            onDisable = onDisableScreenshot,
        )

        SettingsCard(
            autostartEnabled = state.autostartEnabled,
            onAutostartToggle = onAutostartToggle,
        )
    }
}

@Composable
private fun StatusCard(isRunning: Boolean, port: Int) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isRunning)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surfaceVariant,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(
                    text = if (isRunning) "Server running" else "Server stopped",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                if (isRunning) {
                    Text(
                        text = "Listening on port $port",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            val indicator = if (isRunning) "\u25CF" else "\u25CB"
            Text(
                text = indicator,
                style = MaterialTheme.typography.headlineSmall,
                color = if (isRunning) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outline,
            )
        }
    }
}

@Composable
private fun TokenCard(token: String) {
    val context = LocalContext.current
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Bearer Token", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${token.take(16)}...",
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { copyToClipboard(context, "Bearer Token", token) }) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy token")
                }
            }
        }
    }
}

@Composable
private fun AddressesCard(addresses: List<AddressInfo>) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Reachable Addresses", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            if (addresses.isEmpty()) {
                Text("No network interfaces found", style = MaterialTheme.typography.bodySmall)
            } else {
                addresses.forEach { addr ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = if (addr.hostname != null) "${addr.hostname} (${addr.address})"
                                else addr.address,
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        if (addr.isTailscale) {
                            Text(
                                text = "Tailscale",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun McpConfigCard(address: String, hostname: String?, port: Int, token: String) {
    val context = LocalContext.current
    val host = hostname ?: address
    val snippet = """
mcp_servers:
  phone:
    url: "http://$host:$port/mcp"
    headers:
      Authorization: "Bearer $token"
    timeout: 180
    connect_timeout: 60
""".trimIndent()

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    "MCP client config snippet",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                IconButton(onClick = { copyToClipboard(context, "MCP config", snippet) }) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy config")
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = snippet,
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(8.dp)
                    .horizontalScroll(rememberScrollState()),
            )
        }
    }
}

@Composable
private fun BatteryOptimizationCard() {
    val context = LocalContext.current
    val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    val isIgnoring = pm.isIgnoringBatteryOptimizations(context.packageName)
    if (isIgnoring) return

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                Icons.Default.Info,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.error,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Battery optimization active",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "The server may be killed by Doze. Tap to disable optimization.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        OutlinedButton(
            onClick = {
                val intent = Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:${context.packageName}"),
                )
                context.startActivity(intent)
            },
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .fillMaxWidth(),
        ) {
            Text("Disable battery optimization")
        }
    }
}

@Composable
private fun SettingsCard(
    autostartEnabled: Boolean,
    onAutostartToggle: (Boolean) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Settings", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Start on boot", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "Auto-start the server when the device boots",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = autostartEnabled,
                    onCheckedChange = onAutostartToggle,
                )
            }
        }
    }
}

@Composable
private fun PermissionsCard(
    permissions: PermissionsState,
    onRequestCamera: () -> Unit,
    onRequestLocation: () -> Unit,
    onRequestBluetooth: () -> Unit,
    onRequestMedia: () -> Unit,
    onRequestNotifications: () -> Unit,
    onRequestNotificationListener: () -> Unit,
    onOpenAutostart: () -> Unit,
) {
    val allGranted = permissions.cameraGranted && permissions.locationGranted &&
        permissions.bluetoothGranted && permissions.mediaGranted &&
        permissions.notificationsGranted && permissions.notificationListenerGranted

    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (allGranted) MaterialTheme.colorScheme.surfaceVariant
            else MaterialTheme.colorScheme.errorContainer,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Permissions",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(8.dp))

            PermissionRow("Camera", permissions.cameraGranted, onRequestCamera)
            PermissionRow("Location", permissions.locationGranted, onRequestLocation)
            PermissionRow("Bluetooth", permissions.bluetoothGranted, onRequestBluetooth)
            PermissionRow("Media files", permissions.mediaGranted, onRequestMedia)
            PermissionRow("Notifications", permissions.notificationsGranted, onRequestNotifications)
            PermissionRow("Notification listener", permissions.notificationListenerGranted, onRequestNotificationListener)

            Spacer(Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))

            Text(
                "Xiaomi/MIUI: enable Autostart for MCPDroid to prevent the system " +
                    "from killing the server in the background.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            OutlinedButton(
                onClick = onOpenAutostart,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Open Autostart settings")
            }
        }
    }
}

@Composable
private fun PermissionRow(label: String, granted: Boolean, onRequest: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(
                if (granted) "Granted" else "Not granted",
                style = MaterialTheme.typography.bodySmall,
                color = if (granted) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.error,
            )
        }
        if (!granted) {
            OutlinedButton(onClick = onRequest) {
                Text("Grant")
            }
        }
    }
}

@Composable
private fun ScreenshotConsentCard(
    enabled: Boolean,
    onEnable: () -> Unit,
    onDisable: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (enabled) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Screenshot (MediaProjection)",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                if (enabled) "Session active. capture_screenshot is ready; consent persists " +
                    "until you stop it or the system revokes it (e.g. screen lock)."
                else "Tap to grant one-time consent for the capture_screenshot tool. " +
                    "Multiple screenshots can then be taken without re-prompting.",
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(8.dp))
            if (enabled) {
                OutlinedButton(
                    onClick = onDisable,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Text("Stop Screenshot Session")
                }
            } else {
                OutlinedButton(
                    onClick = onEnable,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Enable Screenshot")
                }
            }
        }
    }
}

private fun copyToClipboard(context: Context, label: String, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText(label, text))
    Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
}
