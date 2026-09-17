package dev.satotek.cellscope.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.TimeUnit

/**
 * One-shot latency probe. ICMP via /system/bin/ping (allowed for apps: ping_group_range is wide open on Pixel),
 * falling back to a TCP connect to :443 when the binary is missing. Returns ms, or -1.0 on loss/timeout.
 */
object Pinger {
    suspend fun probe(host: String, timeoutMs: Int = 1000): Double = withContext(Dispatchers.IO) {
        icmp(host, timeoutMs) ?: tcp(host, timeoutMs)
    }

    private fun icmp(host: String, timeoutMs: Int): Double? = runCatching {
        val p = ProcessBuilder("/system/bin/ping", "-c", "1", "-W", "1", "-n", host).redirectErrorStream(true).start()
        val out = p.inputStream.bufferedReader().readText()
        if (!p.waitFor(timeoutMs + 800L, TimeUnit.MILLISECONDS)) { p.destroyForcibly(); return -1.0 }
        Regex("time=([0-9.]+) ms").find(out)?.groupValues?.get(1)?.toDoubleOrNull() ?: -1.0
    }.getOrNull()

    private fun tcp(host: String, timeoutMs: Int): Double = runCatching {
        val t0 = System.nanoTime()
        Socket().use { it.connect(InetSocketAddress(host, 443), timeoutMs) }
        (System.nanoTime() - t0) / 1e6
    }.getOrDefault(-1.0)
}
