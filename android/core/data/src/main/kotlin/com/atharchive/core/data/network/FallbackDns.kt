package com.atharchive.core.data.network

import java.io.File
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Dns
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Cloudflare rotates the anycast IPs it answers with, and Sudanese carriers blackhole some of them
 * (measured on Sudani, Nov 2026: 188.114.96.6/97.6 dead, our zone IPs fine). Any Cloudflare IP serves
 * our hosts by SNI, so for our own hosts the answer is, in order:
 *  1. the address that last connected (kept in [store] across launches, set by [remember]),
 *  2. the system answer interleaved with the zone's own IPs, or, when the system resolver fails,
 *     DNS over HTTPS (Cloudflare, then Google, both reached by IP so no lookup is needed) interleaved
 *     with the zone's own IPs.
 * OkHttp tries the next address when one fails to connect, so a dead first entry costs one connect timeout.
 * fixed zone IPs; if Cloudflare ever moves the zone, refresh them from `dig app-content.arthurarchive.com`.
 */
class FallbackDns(
    private val fallback: List<InetAddress> = listOf("104.21.84.124", "172.67.193.3").map { InetAddress.getByName(it) },
    private val system: Dns = Dns.SYSTEM,
    private val store: File? = null,
    private val doh: (String) -> List<InetAddress> = ::overHttps,
) : Dns {
    private val lastGood = ConcurrentHashMap<String, InetAddress>().apply {
        runCatching { store?.takeIf { it.exists() }?.readLines() }.getOrNull().orEmpty().forEach { line ->
            val (host, ip) = line.split(' ').takeIf { it.size == 2 } ?: return@forEach
            if (ip.isLiteralIp()) put(host, InetAddress.getByName(ip))
        }
    }

    override fun lookup(hostname: String): List<InetAddress> {
        val resolved = try { system.lookup(hostname) } catch (e: UnknownHostException) { emptyList() }
        if (!hostname.isOurs()) return resolved.ifEmpty { throw UnknownHostException(hostname) }
        val answer = resolved.ifEmpty { runCatching { doh(hostname) }.getOrDefault(emptyList()) }
        val extra = fallback.filterNot { it in answer }
        val mixed = (0 until maxOf(answer.size, extra.size)).flatMap { listOfNotNull(answer.getOrNull(it), extra.getOrNull(it)) }
        return listOfNotNull(lastGood[hostname]) + mixed.filterNot { it == lastGood[hostname] }
    }

    /** Network interceptor: records the address each of our hosts actually connected to. */
    val remember = Interceptor { chain ->
        val host = chain.request().url.host
        val address = chain.connection()?.route()?.socketAddress?.address
        if (address != null && host.isOurs() && lastGood.put(host, address) != address) {
            runCatching { store?.writeText(lastGood.entries.joinToString("") { "${it.key} ${it.value.hostAddress}\n" }) }
        }
        chain.proceed(chain.request())
    }

    private fun String.isOurs() = endsWith("arthurarchive.com")

    private companion object {
        fun String.isLiteralIp() = matches(Regex("""[0-9.]+|[0-9a-fA-F:]+"""))

        val dohClient by lazy {
            OkHttpClient.Builder().connectTimeout(3, TimeUnit.SECONDS).callTimeout(5, TimeUnit.SECONDS).build()
        }

        /** DNS-JSON A records; empty when both resolvers fail. */
        fun overHttps(hostname: String): List<InetAddress> {
            for (base in listOf("https://1.1.1.1/dns-query", "https://8.8.8.8/resolve")) {
                val request = Request.Builder().url("$base?name=$hostname&type=A").header("accept", "application/dns-json").build()
                val ips = runCatching {
                    dohClient.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) return@use emptyList()
                        Json.parseToJsonElement(response.body.string()).jsonObject["Answer"]?.jsonArray.orEmpty()
                            .map { it.jsonObject }
                            .filter { it["type"]?.jsonPrimitive?.int == 1 }
                            .mapNotNull { it["data"]?.jsonPrimitive?.content?.takeIf { ip -> ip.isLiteralIp() } }
                            .map { InetAddress.getByName(it) }
                    }
                }.getOrDefault(emptyList())
                if (ips.isNotEmpty()) return ips
            }
            return emptyList()
        }
    }
}
