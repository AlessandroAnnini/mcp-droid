package com.alessandroannini.mcpdroid.infra

import android.content.pm.ServiceInfo

/**
 * Ref-counted, thread-safe manager of the active foreground service type mask.
 *
 * The service starts as [ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE] and
 * stays there when idle. Tool handlers call [withType] (or [acquire]/[release]
 * for session-long types such as mediaProjection) to elevate the running type
 * mask while they need it.
 *
 * Multiple tools can run concurrently: each type is ref-counted so the mask
 * only drops a type when all holders have released it.
 *
 * [attach] is called by [McpServerService] on start, providing the applier
 * callback. [detach] is called on destroy. When no service is attached,
 * elevation calls are no-ops so tools remain functional (best-effort) even
 * when the coordinator is unattached.
 */
object ForegroundTypeCoordinator {

    private val counts = HashMap<Int, Int>()
    private var lastMask = 0

    @Volatile
    private var applier: ((Int) -> Unit)? = null

    @Synchronized
    fun attach(a: (Int) -> Unit) {
        applier = a
        lastMask = 0
        apply()
    }

    @Synchronized
    fun detach() {
        applier = null
        counts.clear()
        lastMask = 0
    }

    @Synchronized
    fun acquire(type: Int) {
        counts[type] = (counts[type] ?: 0) + 1
        apply()
    }

    @Synchronized
    fun release(type: Int) {
        val n = (counts[type] ?: 0) - 1
        if (n <= 0) counts.remove(type) else counts[type] = n
        apply()
    }

    /**
     * Elevates to [type] for the duration of [block], then releases it.
     * Thread-safe; the acquire/release pair is guaranteed by try/finally.
     */
    suspend fun <T> withType(type: Int, block: suspend () -> T): T {
        acquire(type)
        try {
            return block()
        } finally {
            release(type)
        }
    }

    private fun apply() {
        val mask = counts.keys.fold(ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE) { acc, t ->
            acc or t
        }
        if (mask == lastMask) return
        lastMask = mask
        applier?.invoke(mask)
    }
}
