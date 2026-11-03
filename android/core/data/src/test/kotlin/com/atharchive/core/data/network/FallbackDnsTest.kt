package com.atharchive.core.data.network

import java.net.InetAddress
import java.net.UnknownHostException
import okhttp3.Dns
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class FallbackDnsTest {
    private fun ips(vararg s: String) = s.map { InetAddress.getByName(it) }
    private fun answers(vararg s: String) = object : Dns { override fun lookup(hostname: String) = ips(*s) }
    private val unresolvable = object : Dns { override fun lookup(hostname: String): List<InetAddress> = throw UnknownHostException(hostname) }
    private val fallback = ips("104.21.84.124", "172.67.193.3")

    @Test
    fun interleavesOurIpsAfterEachSystemAnswerWithoutDuplicates() {
        val dns = FallbackDns(fallback, answers("188.114.96.6", "188.114.97.6"))
        assertEquals(ips("188.114.96.6", "104.21.84.124", "188.114.97.6", "172.67.193.3"), dns.lookup("app-content.arthurarchive.com"))
        assertEquals(ips("104.21.84.124", "172.67.193.3"), FallbackDns(fallback, answers("104.21.84.124")).lookup("search.arthurarchive.com"))
    }

    @Test
    fun unresolvableOwnHostStillGetsFallbackButOtherHostsDoNot() {
        assertEquals(fallback, FallbackDns(fallback, unresolvable).lookup("app-content.arthurarchive.com"))
        assertThrows(UnknownHostException::class.java) { FallbackDns(fallback, unresolvable).lookup("example.com") }
        assertEquals(ips("93.184.216.34"), FallbackDns(fallback, answers("93.184.216.34")).lookup("example.com"))
    }
}
