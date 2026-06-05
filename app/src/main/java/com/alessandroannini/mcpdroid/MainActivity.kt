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
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.alessandroannini.mcpdroid.data.Settings
import com.alessandroannini.mcpdroid.infra.ScreenCaptureSession
import com.alessandroannini.mcpdroid.notifications.McpNotificationListenerService
import com.alessandroannini.mcpdroid.server.McpServerService
import com.alessandroannini.mcpdroid.server.ServerHost
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .safeDrawingPadding(),
                ) {
                    var isRunning by remember { mutableStateOf(ServerHost.isRunning) }
                    var autostartEnabled by remember {
                        mutableStateOf(Settings.getAutostart(this@MainActivity))
                    }

                    MainScreen(
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
        permissionsState = PermissionsState(
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
