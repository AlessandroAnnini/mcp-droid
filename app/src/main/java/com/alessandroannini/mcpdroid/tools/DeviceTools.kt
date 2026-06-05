package com.alessandroannini.mcpdroid.tools

import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import io.modelcontextprotocol.kotlin.sdk.server.Server
import com.alessandroannini.mcpdroid.server.toolResult
import com.alessandroannini.mcpdroid.server.withToolLog
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

fun Server.registerDeviceTools(context: Context) {
    addTool(
        name = "get_device_status",
        description = "Returns battery level, charging state, network type, Wi-Fi state, " +
            "Bluetooth state, and device model.",
    ) { _ ->
        withToolLog("get_device_status") {
            val batteryIntent = context.registerReceiver(
                null,
                IntentFilter(Intent.ACTION_BATTERY_CHANGED),
            )
            val batteryLevel = batteryIntent?.let { intent ->
                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                if (level >= 0 && scale > 0) (level * 100 / scale) else -1
            } ?: -1

            val batteryStatus = batteryIntent?.getIntExtra(
                BatteryManager.EXTRA_STATUS, -1
            ) ?: -1
            val isCharging = batteryStatus == BatteryManager.BATTERY_STATUS_CHARGING ||
                batteryStatus == BatteryManager.BATTERY_STATUS_FULL

            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = cm.activeNetwork
            val caps = network?.let { cm.getNetworkCapabilities(it) }
            val networkType = when {
                caps == null -> "none"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "cellular"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ethernet"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "vpn"
                else -> "other"
            }

            val bluetoothState = try {
                val bm = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager?
                bm?.adapter?.state == android.bluetooth.BluetoothAdapter.STATE_ON
            } catch (e: SecurityException) {
                null
            }

            toolResult(buildJsonObject {
                put("battery_percent", batteryLevel)
                put("charging", isCharging)
                put("network_type", networkType)
                put("device_model", Build.MODEL)
                put("android_version", Build.VERSION.RELEASE)
                if (bluetoothState != null) {
                    put("bluetooth_on", bluetoothState)
                }
            })
        }
    }
}
