package com.alessandroannini.mcpdroid.server

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.alessandroannini.mcpdroid.MainActivity
import com.alessandroannini.mcpdroid.R
import com.alessandroannini.mcpdroid.data.Settings
import com.alessandroannini.mcpdroid.infra.ForegroundTypeCoordinator
import com.alessandroannini.mcpdroid.infra.ScreenCaptureSession

private const val TAG = "McpServerService"
private const val NOTIFICATION_ID = 1001
const val CHANNEL_ID = "mcp_server_channel"

const val ACTION_START = "com.alessandroannini.mcpdroid.ACTION_START"
const val ACTION_STOP = "com.alessandroannini.mcpdroid.ACTION_STOP"
const val ACTION_START_PROJECTION = "com.alessandroannini.mcpdroid.ACTION_START_PROJECTION"
const val ACTION_STOP_PROJECTION = "com.alessandroannini.mcpdroid.ACTION_STOP_PROJECTION"

private const val EXTRA_RESULT_CODE = "resultCode"
private const val EXTRA_DATA = "data"

/**
 * Foreground service that owns the MCP server lifecycle.
 *
 * Implements [LifecycleOwner] so CameraX can bind inside the service context.
 *
 * FGS type management is delegated to [ForegroundTypeCoordinator]: the service
 * starts as [ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE] and applies
 * whatever mask the coordinator computes each time a type is acquired or
 * released. [applyForegroundType] is always called on the main thread to avoid
 * IPC ordering issues with the system.
 *
 * MediaProjection lifecycle (Android 14+ order):
 * 1. Activity obtains user consent via createScreenCaptureIntent().
 * 2. Activity calls [startProjection] which sends ACTION_START_PROJECTION.
 * 3. Service acquires the mediaProjection type first, then calls
 *    getMediaProjection(), then starts the capture session.
 * 4. On stop or system revocation, the session releases the type.
 */
class McpServerService : Service(), LifecycleOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry

    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onCreate() {
        super.onCreate()
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }

            ACTION_START_PROJECTION -> handleStartProjection(intent)

            ACTION_STOP_PROJECTION -> ScreenCaptureSession.stop()

            else -> handleStart()
        }
        return START_STICKY
    }

    private fun handleStart() {
        if (lifecycleRegistry.currentState == Lifecycle.State.RESUMED) {
            Log.w(TAG, "handleStart called while already running; ignoring")
            return
        }
        val token = Settings.getToken(this)
        val port = Settings.getPort(this)
        // Start with specialUse only. The coordinator will elevate via
        // additional startForeground calls as tools are invoked.
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
        )
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
        ForegroundTypeCoordinator.attach { mask -> applyForegroundType(mask) }
        ServerHost.start(this, port, token, this)
        Log.i(TAG, "Service started on port $port")
    }

    private fun handleStartProjection(intent: Intent) {
        val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
        @Suppress("DEPRECATION")
        val data = intent.getParcelableExtra<Intent>(EXTRA_DATA) ?: run {
            Log.e(TAG, "ACTION_START_PROJECTION missing data extra")
            return
        }
        // Android 14+ order: acquire type -> getMediaProjection -> start session.
        ForegroundTypeCoordinator.acquire(ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        try {
            val mpm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            val mp = mpm.getMediaProjection(resultCode, data) ?: run {
                Log.e(TAG, "getMediaProjection returned null")
                ForegroundTypeCoordinator.release(ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
                return
            }
            val started = ScreenCaptureSession.start(this, mp) {
                ForegroundTypeCoordinator.release(ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
            }
            if (!started) {
                mp.stop()
                ForegroundTypeCoordinator.release(ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start projection: ${e.message}")
            ForegroundTypeCoordinator.release(ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        }
    }

    override fun onDestroy() {
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        ScreenCaptureSession.stop()
        ForegroundTypeCoordinator.detach()
        ServerHost.stop()
        Log.i(TAG, "Service destroyed")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /**
     * Applies [mask] to the foreground service type by re-calling
     * [ServiceCompat.startForeground]. Must run on the main thread to avoid
     * IPC ordering issues; if already on main, runs immediately, else posts.
     */
    private fun applyForegroundType(mask: Int) {
        val apply = Runnable {
            if (lifecycleRegistry.currentState != Lifecycle.State.RESUMED) return@Runnable
            ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(), mask)
            Log.d(TAG, "FGS type mask updated to 0x${mask.toString(16)}")
        }
        if (Looper.myLooper() == Looper.getMainLooper()) apply.run() else mainHandler.post(apply)
    }

    private fun buildNotification() =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(
                if (ScreenCaptureSession.isActive)
                    getString(R.string.notification_text_with_capture)
                else
                    getString(R.string.notification_text)
            )
            .setSmallIcon(android.R.drawable.ic_menu_share)
            .setOngoing(true)
            .setContentIntent(
                PendingIntent.getActivity(
                    this, 0,
                    Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE,
                )
            )
            .build()

    private fun createNotificationChannel() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.notification_channel_description)
        }
        manager.createNotificationChannel(channel)
    }

    companion object {
        fun start(context: Context) {
            context.startForegroundService(
                Intent(context, McpServerService::class.java).apply {
                    action = ACTION_START
                }
            )
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, McpServerService::class.java).apply {
                    action = ACTION_STOP
                }
            )
        }

        /**
         * Forward the MediaProjection consent result to the service.
         * The service acquires the mediaProjection FGS type before calling
         * getMediaProjection(), satisfying the Android 14+ ordering requirement.
         */
        fun startProjection(context: Context, resultCode: Int, data: Intent) {
            context.startService(
                Intent(context, McpServerService::class.java).apply {
                    action = ACTION_START_PROJECTION
                    putExtra(EXTRA_RESULT_CODE, resultCode)
                    putExtra(EXTRA_DATA, data)
                }
            )
        }

        fun stopProjection(context: Context) {
            context.startService(
                Intent(context, McpServerService::class.java).apply {
                    action = ACTION_STOP_PROJECTION
                }
            )
        }
    }
}
