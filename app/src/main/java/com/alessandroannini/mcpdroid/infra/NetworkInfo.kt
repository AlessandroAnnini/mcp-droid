package com.alessandroannini.mcpdroid.infra

import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface

data class AddressInfo(
    val address: String,
    val hostname: String?,
    val isTailscale: Boolean,
)

/**
 * Returns all non-loopback IPv4 addresses visible to the app, labelling
 * Tailscale addresses (100.64.0.0/10 CGNAT range, 100.x.y.z) so the UI
 * can highlight the recommended one to put in the MCP client config.
 *
 * For Tailscale addresses, attempts a reverse DNS lookup to resolve the
 * MagicDNS hostname (e.g. "xiaomi-13t-pro"). The hostname is preferred
 * over the raw IP in the MCP client config to avoid raw-IP warnings.
 */
fun getNetworkAddresses(): List<AddressInfo> =
    NetworkInterface.getNetworkInterfaces()
        ?.toList()
        .orEmpty()
        .filter { !it.isLoopback && it.isUp }
        .flatMap { iface -> iface.inetAddresses.toList() }
        .filterIsInstance<Inet4Address>()
        .filter { !it.isLoopbackAddress }
        .map { addr ->
            val hostAddress = addr.hostAddress ?: ""
            val isTailscale = isTailscaleAddress(hostAddress)
            AddressInfo(
                address = hostAddress,
                hostname = if (isTailscale) resolveTailscaleHostname(addr) else null,
                isTailscale = isTailscale,
            )
        }

/** Tailscale uses the 100.64.0.0/10 CGNAT block (100.64 - 100.127). */
private fun isTailscaleAddress(ip: String): Boolean {
    val parts = ip.split(".")
    if (parts.size != 4) return false
    val first = parts[0].toIntOrNull() ?: return false
    val second = parts[1].toIntOrNull() ?: return false
    return first == 100 && second in 64..127
}

/**
 * Attempts reverse DNS on a Tailscale IP to get the MagicDNS hostname.
 * Returns the short name (before the first dot) or null if lookup fails
 * or returns the IP itself.
 */
private fun resolveTailscaleHostname(addr: InetAddress): String? =
    runCatching {
        val fqdn = addr.canonicalHostName
        if (fqdn == addr.hostAddress) return@runCatching null
        fqdn
    }.getOrNull()
