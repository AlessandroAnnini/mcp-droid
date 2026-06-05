package com.alessandroannini.mcpdroid.tools

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import com.alessandroannini.mcpdroid.infra.ForegroundTypeCoordinator
import com.alessandroannini.mcpdroid.server.errorResult
import com.alessandroannini.mcpdroid.server.toolResult
import com.alessandroannini.mcpdroid.server.withToolLog
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import kotlin.coroutines.resume

fun Server.registerLocationTools(context: Context) {
    addTool(
        name = "get_location",
        description = "Returns the current device GPS location (latitude, longitude, accuracy in metres, " +
            "and a Unix timestamp). Requires ACCESS_FINE_LOCATION permission. " +
            "Grant it in the MCPDroid app before calling.",
        inputSchema = ToolSchema(
            properties = buildJsonObject {
                putJsonObject("high_accuracy") {
                    put("type", "boolean")
                    put("description", "Use GPS (true, default) or network provider (false)")
                }
            },
        ),
    ) { request ->
        withToolLog("get_location") {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED
            ) {
                return@withToolLog errorResult(
                    "ACCESS_FINE_LOCATION permission is not granted. " +
                        "Open the MCPDroid app and grant Location permission."
                )
            }

            val highAccuracy = request.arguments?.get("high_accuracy")
                ?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: true

            val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

            // Try last known location first for a fast response
            val lastKnown: Location? = sequenceOf(
                LocationManager.GPS_PROVIDER,
                LocationManager.NETWORK_PROVIDER,
                LocationManager.PASSIVE_PROVIDER,
            ).firstNotNullOfOrNull { provider ->
                runCatching { lm.getLastKnownLocation(provider) }.getOrNull()
            }

            // Request a fresh fix with a 10-second timeout, elevated to the
            // location FGS type so Android 14+ grants background location access.
            val provider = if (highAccuracy) LocationManager.GPS_PROVIDER
            else LocationManager.NETWORK_PROVIDER

            val fresh: Location? = ForegroundTypeCoordinator.withType(
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            ) {
                if (lm.isProviderEnabled(provider)) {
                    withTimeoutOrNull(10_000) {
                        suspendCancellableCoroutine { cont ->
                            val listener = object : android.location.LocationListener {
                                override fun onLocationChanged(loc: Location) {
                                    lm.removeUpdates(this)
                                    cont.resume(loc)
                                }
                                @Deprecated("Required on older API levels")
                                override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) = Unit
                            }
                            runCatching {
                                @Suppress("DEPRECATION")
                                lm.requestSingleUpdate(provider, listener, android.os.Looper.getMainLooper())
                            }.onFailure { lm.removeUpdates(listener) }
                            cont.invokeOnCancellation { lm.removeUpdates(listener) }
                        }
                    }
                } else null
            }

            val location = fresh ?: lastKnown
                ?: return@withToolLog errorResult(
                    "Could not obtain location. Ensure GPS is enabled and the app has location permission."
                )

            toolResult(buildJsonObject {
                put("lat", location.latitude)
                put("lon", location.longitude)
                put("accuracy_m", location.accuracy.toDouble())
                put("timestamp", location.time)
                put("source", if (fresh != null) "fresh" else "last_known")
            })
        }
    }
}
