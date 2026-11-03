package com.atharchive.core.data.network

import java.net.InetAddress
import java.net.UnknownHostException
import okhttp3.Dns

/**
 * Cloudflare rotates the anycast IPs it answers with, and Sudanese carriers blackhole some of them
 * (measured on Sudani, Nov 2026: 188.114.96.6/97.6 dead, our zone IPs fine). Any Cloudflare IP serves
 * our hosts by SNI, so interleave the zone's own IPs with the system answer: OkHttp tries the next
 * address when one fails to connect, and a blocked or unresolvable answer still leaves a working route.
 * ponytail: fixed list; if Cloudflare ever moves the zone, refresh these from `dig app-content.arthurarchive.com`.
 */
class FallbackDns(
    private val fallback: List<InetAddress> = listOf("104.21.84.124", "172.67.193.3").map { InetAddress.getByName(it) },
    private val system: Dns = Dns.SYSTEM,
) : Dns {
    override fun lookup(hostname: String): List<InetAddress> {
        val resolved = try { system.lookup(hostname) } catch (e: UnknownHostException) { emptyList() }
        if (!hostname.endsWith("arthurarchive.com")) return resolved.ifEmpty { throw UnknownHostException(hostname) }
        val extra = fallback.filterNot { it in resolved }
        return (0 until maxOf(resolved.size, extra.size)).flatMap { listOfNotNull(resolved.getOrNull(it), extra.getOrNull(it)) }
    }
}
