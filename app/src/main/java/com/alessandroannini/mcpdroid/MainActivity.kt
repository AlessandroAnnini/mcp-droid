package com.alessandroannini.mcpdroid

import android.Manifest
import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings as AndroidSettings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.alessandroannini.mcpdroid.data.Settings
import com.alessandroannini.mcpdroid.infra.EventLog
import com.alessandroannini.mcpdroid.infra.ScreenCaptureSession
import com.alessandroannini.mcpdroid.notifications.McpNotificationListenerService
import com.alessandroannini.mcpdroid.server.McpServerService
import com.alessandroannini.mcpdroid.server.ServerHost
import com.alessandroannini.mcpdroid.ui.LogScreen
import com.alessandroannini.mcpdroid.ui.MainScreen
import com.alessandroannini.mcpdroid.ui.MainScreenState
import com.alessandroannini.mcpdroid.ui.PermissionsState

class MainActivity : ComponentActivity() {

    private val projectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            McpServerService.startProjection(this, result.resultCode, result.data!!)
            screenshotEnabled = true
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ -> refreshPermissions() }

    private var screenshotEnabled by mutableStateOf(ScreenCaptureSession.isActive)
    private var permissionsState by mutableStateOf(PermissionsState())
    private var permissionsInitialized = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MaterialTheme {
                var isRunning by remember { mutableStateOf(ServerHost.isRunning) }
                var autostartEnabled by remember {
                    mutableStateOf(Settings.getAutostart(this@MainActivity))
                }
                var selectedTab by remember { mutableIntStateOf(0) }

                Scaffold(
                    modifier = Modifier
                        .fillMaxSize()
                        .safeDrawingPadding(),
                    bottomBar = {
                        NavigationBar {
                            NavigationBarItem(
                                selected = selectedTab == 0,
                                onClick = { selectedTab = 0 },
                                icon = { Icon(Icons.Default.Home, contentDescription = null) },
                                label = { Text("Server") },
                            )
                            NavigationBarItem(
                                selected = selectedTab == 1,
                                onClick = { selectedTab = 1 },
                                icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = null) },
                                label = { Text("Log") },
                            )
                        }
                    },
                ) { innerPadding ->
                    when (selectedTab) {
                        0 -> MainScreen(
                            modifier = Modifier.padding(innerPadding),
                            state = MainScreenState(
                                isRunning = isRunning,
                                port = Settings.getPort(this@MainActivity),
                                token = Settings.getToken(this@MainActivity),
                                autostartEnabled = autostartEnabled,
                                screenshotEnabled = screenshotEnabled,
                            ),
                            permissions = permissionsState,
                            onStartStop = {
                                if (isRunning) {
                                    McpServerService.stop(this@MainActivity)
                                } else {
                                    McpServerService.start(this@MainActivity)
                                }
                                isRunning = !isRunning
                            },
                            onAutostartToggle = { enabled ->
                                Settings.setAutostart(this@MainActivity, enabled)
                                autostartEnabled = enabled
                            },
                            onEnableScreenshot = {
                                val mpm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                                projectionLauncher.launch(mpm.createScreenCaptureIntent())
                            },
                            onDisableScreenshot = {
                                McpServerService.stopProjection(this@MainActivity)
                                screenshotEnabled = false
                            },
                            onRequestCamera = { requestOrOpenSettings(Manifest.permission.CAMERA) },
                            onRequestLocation = { requestOrOpenSettings(Manifest.permission.ACCESS_FINE_LOCATION) },
                            onRequestBluetooth = { requestOrOpenSettings(Manifest.permission.BLUETOOTH_CONNECT) },
                            onRequestMedia = { requestMediaPermissions() },
                            onRequestNotifications = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    requestOrOpenSettings(Manifest.permission.POST_NOTIFICATIONS)
                                }
                            },
                            onRequestNotificationListener = { openNotificationListenerSettings() },
                            onOpenAutostart = { openXiaomiAutostart() },
                        )
                        else -> LogScreen(modifier = Modifier.padding(innerPadding))
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        screenshotEnabled = ScreenCaptureSession.isActive
        refreshPermissions()
    }

    private fun refreshPermissions() {
        val old = permissionsState
        val new = PermissionsState(
            cameraGranted = hasPermission(Manifest.permission.CAMERA),
            locationGranted = hasPermission(Manifest.permission.ACCESS_FINE_LOCATION),
            bluetoothGranted = hasPermission(Manifest.permission.BLUETOOTH_CONNECT),
            mediaGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                hasPermission(Manifest.permission.READ_MEDIA_IMAGES) &&
                    hasPermission(Manifest.permission.READ_MEDIA_VIDEO) &&
                    hasPermission(Manifest.permission.READ_MEDIA_AUDIO)
            } else true,
            notificationsGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                hasPermission(Manifest.permission.POST_NOTIFICATIONS)
            } else true,
            notificationListenerGranted = isNotificationListenerEnabled(),
        )
        if (permissionsInitialized) {
            logPermissionChanges(old, new)
        }
        permissionsInitialized = true
        permissionsState = new
    }

    private fun logPermissionChanges(old: PermissionsState, new: PermissionsState) {
        fun log(name: String, wasGranted: Boolean, isGranted: Boolean) {
            if (wasGranted == isGranted) return
            val change = if (isGranted) "granted" else "revoked"
            EventLog.info("permission", "$name $change")
        }
        log("Camera", old.cameraGranted, new.cameraGranted)
        log("Location", old.locationGranted, new.locationGranted)
        log("Bluetooth", old.bluetoothGranted, new.bluetoothGranted)
        log("Media files", old.mediaGranted, new.mediaGranted)
        log("Notifications", old.notificationsGranted, new.notificationsGranted)
        log("Notification listener", old.notificationListenerGranted, new.notificationListenerGranted)
    }

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    private fun requestMediaPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val needed = arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_AUDIO,
            ).filter { !hasPermission(it) }.toTypedArray()
            if (needed.isNotEmpty()) {
                permissionLauncher.launch(needed)
            } else {
                refreshPermissions()
            }
        }
    }

    private fun requestOrOpenSettings(permission: String) {
        if (shouldShowRequestPermissionRationale(permission) ||
            ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
        ) {
            // Can still show the system dialog (first ask, or rationale-eligible).
            permissionLauncher.launch(arrayOf(permission))
        } else {
            // Permanently denied or never asked with "Don't ask again": open app settings.
            openAppSettings()
        }
    }

    private fun openAppSettings() {
        startActivity(
            Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", packageName, null)
            }
        )
    }

    private fun isNotificationListenerEnabled(): Boolean {
        val cn = ComponentName(this, McpNotificationListenerService::class.java)
        val flat = AndroidSettings.Secure.getString(contentResolver, "enabled_notification_listeners")
            ?: return false
        return flat.split(":").any { ComponentName.unflattenFromString(it) == cn }
    }

    private fun openNotificationListenerSettings() {
        startActivity(Intent(AndroidSettings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
    }

    private fun openXiaomiAutostart() {
        // Try MIUI-specific autostart manager; fall back to app details.
        val miuiIntent = Intent().apply {
            component = ComponentName(
                "com.miui.securitycenter",
                "com.miui.permcenter.autostart.AutoStartManagementActivity",
            )
        }
        if (miuiIntent.resolveActivity(packageManager) != null) {
            startActivity(miuiIntent)
        } else {
            openAppSettings()
        }
    }
}
