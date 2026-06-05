package com.alessandroannini.mcpdroid.data

import android.content.Context
import android.content.SharedPreferences
import java.security.SecureRandom

private const val PREFS_NAME = "mcpdroid_prefs"
private const val KEY_TOKEN = "bearer_token"
private const val KEY_PORT = "server_port"
private const val KEY_AUTOSTART = "autostart_on_boot"
private const val DEFAULT_PORT = 8765
private const val TOKEN_BYTES = 32

/**
 * App settings backed by private SharedPreferences.
 *
 * Security note: the bearer token is stored in app-private SharedPreferences
 * (mode_private). Android's file-based encryption (FBE, mandatory on API 29+)
 * protects the file at rest. The token is never transmitted in plaintext because
 * all network traffic goes over the Tailscale WireGuard tunnel. This is adequate
 * for a personal sideloaded tool; for a distributed app add Android Keystore
 * wrapping.
 */
object Settings {

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Returns the existing bearer token, generating and persisting one on first call. */
    fun getToken(context: Context): String {
        val p = prefs(context)
        val stored = p.getString(KEY_TOKEN, null)
        if (stored != null) return stored
        val token = generateToken()
        p.edit().putString(KEY_TOKEN, token).apply()
        return token
    }

    fun getPort(context: Context): Int =
        prefs(context).getInt(KEY_PORT, DEFAULT_PORT)

    fun setPort(context: Context, port: Int) {
        prefs(context).edit().putInt(KEY_PORT, port).apply()
    }

    fun getAutostart(context: Context): Boolean =
        prefs(context).getBoolean(KEY_AUTOSTART, false)

    fun setAutostart(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_AUTOSTART, enabled).apply()
    }

    private fun generateToken(): String {
        val bytes = ByteArray(TOKEN_BYTES)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
