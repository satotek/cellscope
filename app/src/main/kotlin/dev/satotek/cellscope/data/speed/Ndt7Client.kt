package dev.satotek.cellscope.data.speed

import android.content.Context
import android.util.Log
import dev.satotek.cellscope.data.model.CellEntry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.json.JSONObject
import java.net.URI
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

object Ndt7Client {
    private const val TAG = "Speed"
    private const val LOCATE = "https://locate.measurementlab.net/v2/nearest/ndt/ndt7"
    private const val SUBPROTOCOL = "net.measurementlab.ndt.v7"
    private const val TEST_MS = 10_000L
    private const val SKIP_MS = 2_000L
    private const val QUEUE_CAP = 8L * 1024 * 1024
    private const val MSG_MIN = 8 * 1024
    private const val MSG_MAX = 1 * 1024 * 1024

    fun run(context: Context, customServer: String?, serving: CellEntry?): Flow<SpeedProgress> = channelFlow {
        val ver = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "0"
        val ua = "CellScope/$ver"
        trySend(SpeedProgress(SpeedPhase.LOCATE))
        withContext(Dispatchers.IO) {
        val bound = CellularNetwork.bind(context)
        val client = bound.client(ua)
        Log.i(TAG, "viaCellular=${bound.viaCellular} serverPref=${customServer ?: "mlab"}")
        try {
            trySend(SpeedProgress(SpeedPhase.LOCATE, viaCellular = bound.viaCellular))
            val ep = if (customServer.isNullOrBlank()) locate(client) else parseCustom(customServer)
            trySend(SpeedProgress(SpeedPhase.LOCATE, server = ep.label, viaCellular = bound.viaCellular))
            Log.i(TAG, "viaCellular=${bound.viaCellular} server=${ep.label}")

            val startedAt = System.currentTimeMillis()
            var minRtt: Double? = null

            val dl = measure(client, ep.download, upload = false, server = ep.label, via = bound.viaCellular) { p ->
                minRtt = minOfRtt(minRtt, p.minRttMs)
                trySend(p.copy(minRttMs = minRtt, server = ep.label, viaCellular = bound.viaCellular))
            }
            minRtt = minOfRtt(minRtt, dl.minRtt)

            val ul = measure(client, ep.upload, upload = true, server = ep.label, via = bound.viaCellular) { p ->
                minRtt = minOfRtt(minRtt, p.minRttMs)
                trySend(p.copy(minRttMs = minRtt, server = ep.label, viaCellular = bound.viaCellular))
            }
            minRtt = minOfRtt(minRtt, ul.minRtt)

            val result = SpeedResult(
                t = startedAt,
                dlMbps = dl.avg,
                ulMbps = ul.avg,
                minRttMs = minRtt,
                server = ep.label,
                rat = serving?.rat?.name ?: "—",
                band = serving?.bandLabel ?: "—",
                pci = serving?.pci,
                rsrp = serving?.rsrp,
                viaCellular = bound.viaCellular,
            )
            Log.i(TAG, "viaCellular=${bound.viaCellular} server=${ep.label} dl=${result.dlMbps} ul=${result.ulMbps} minRtt=$minRtt")
            trySend(
                SpeedProgress(
                    SpeedPhase.DONE,
                    elapsedMs = TEST_MS,
                    mbps = ul.avg,
                    avgMbps = ul.avg,
                    minRttMs = minRtt,
                    server = ep.label,
                    viaCellular = bound.viaCellular,
                    result = result,
                ),
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "error viaCellular=${bound.viaCellular}", e)
            trySend(SpeedProgress(SpeedPhase.ERROR, viaCellular = bound.viaCellular, error = e.toString()))
        } finally {
            bound.close()
            runCatching { client.dispatcher.executorService.shutdown() }
        }
        }
    }

    private data class Endpoints(val download: String, val upload: String, val label: String)

    private fun locate(client: OkHttpClient): Endpoints {
        val req = Request.Builder().url(LOCATE).build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IllegalStateException("locate HTTP ${resp.code}")
            val body = resp.body?.string().orEmpty()
            val r0 = JSONObject(body).getJSONArray("results").getJSONObject(0)
            val urls = r0.getJSONObject("urls")
            val dl = urls.getString("wss:///ndt/v7/download")
            val ul = urls.getString("wss:///ndt/v7/upload")
            val city = r0.optJSONObject("location")?.optString("city").orEmpty()
            val host = runCatching { URI(dl).host }.getOrNull() ?: dl
            val label = if (city.isBlank()) host else "$host · $city"
            return Endpoints(dl, ul, label)
        }
    }

    private fun parseCustom(raw: String): Endpoints {
        var base = raw.trim().trimEnd('/')
        when {
            base.endsWith("/download") -> base = base.removeSuffix("/download")
            base.endsWith("/upload") -> base = base.removeSuffix("/upload")
        }
        if (!base.contains("/ndt/v7")) base = "$base/ndt/v7"
        val host = runCatching { URI(base).host }.getOrNull() ?: base
        return Endpoints("$base/download", "$base/upload", host)
    }

    private data class Measure(val avg: Double, val minRtt: Double?)

    private suspend fun measure(
        client: OkHttpClient,
        url: String,
        upload: Boolean,
        server: String,
        via: Boolean,
        onProgress: (SpeedProgress) -> Unit,
    ): Measure = coroutineScope {
        val opened = CompletableDeferred<WebSocket>()
        val failed = AtomicReference<Throwable?>(null)
        val localBytes = AtomicLong(0)
        val localAt2s = AtomicLong(-1)
        val serverBytes = AtomicLong(0)
        val serverAt2s = AtomicLong(-1)
        val minRtt = AtomicReference<Double?>(null)
        val lastBytes = AtomicLong(0)
        val lastT = AtomicLong(0)

        val req = Request.Builder()
            .url(url)
            .header("Sec-WebSocket-Protocol", SUBPROTOCOL)
            .build()
        val ws = client.newWebSocket(req, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                opened.complete(webSocket)
            }
            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                if (!upload) localBytes.addAndGet(bytes.size.toLong())
            }
            override fun onMessage(webSocket: WebSocket, text: String) {
                parseTcp(text, minRtt, serverBytes)
            }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (!opened.isCompleted) opened.completeExceptionally(t)
                failed.compareAndSet(null, t)
            }
        })
        try {
            withTimeout(15_000) { opened.await() }
            val t0 = System.nanoTime()
            fun elapsed() = (System.nanoTime() - t0) / 1_000_000L
            lastT.set(t0)

            val ticker = launch {
                while (isActive && elapsed() < TEST_MS) {
                    delay(200)
                    val e = elapsed()
                    snapshot2s(e, localBytes, localAt2s, serverBytes, serverAt2s)
                    val now = localBytes.get()
                    val inst = mbps(now - lastBytes.getAndSet(now), ((System.nanoTime() - lastT.getAndSet(System.nanoTime())) / 1_000_000L).coerceAtLeast(1))
                    onProgress(
                        SpeedProgress(
                            if (upload) SpeedPhase.UPLOAD else SpeedPhase.DOWNLOAD,
                            elapsedMs = e,
                            mbps = inst,
                            avgMbps = avgMbps(e, localBytes, localAt2s, serverBytes, serverAt2s, upload),
                            minRttMs = minRtt.get(),
                            server = server,
                            viaCellular = via,
                        ),
                    )
                }
            }
            val sender = if (upload) launch { sendUpload(ws, t0, localBytes) } else null
            try {
                while (elapsed() < TEST_MS && isActive) {
                    failed.get()?.let { throw it }
                    delay(50)
                }
            } finally {
                ticker.cancel()
                sender?.cancel()
            }
            val e = elapsed().coerceAtLeast(1)
            snapshot2s(e, localBytes, localAt2s, serverBytes, serverAt2s)
            Log.i(TAG, "measure upload=$upload local=${localBytes.get()} server=${serverBytes.get()} minRtt=${minRtt.get()}")
            Measure(avgMbps(e, localBytes, localAt2s, serverBytes, serverAt2s, upload) ?: 0.0, minRtt.get())
        } finally {
            runCatching { ws.close(1000, null) }
        }
    }

    private suspend fun sendUpload(ws: WebSocket, t0: Long, sent: AtomicLong) {
        val buf = ByteArray(MSG_MAX)
        val pat = ByteArray(4096) { it.toByte() }
        var off = 0
        while (off < buf.size) {
            val n = minOf(pat.size, buf.size - off)
            System.arraycopy(pat, 0, buf, off, n)
            off += n
        }
        var size = MSG_MIN
        while ((System.nanoTime() - t0) / 1_000_000L < TEST_MS) {
            val elapsed = { (System.nanoTime() - t0) / 1_000_000L }
            while (ws.queueSize() + size > QUEUE_CAP && elapsed() < TEST_MS) delay(8)
            if (elapsed() >= TEST_MS) break
            if (ws.queueSize() + size > QUEUE_CAP) {
                // 16 MiB messages don't fit in an 8 MiB queue: hold at the largest size that does.
                size = QUEUE_CAP.toInt() / 2
                continue
            }
            if (ws.send(buf.toByteString(0, size))) {
                sent.addAndGet(size.toLong())
                if (size < MSG_MAX) size = (size * 2).coerceAtMost(MSG_MAX)
            } else {
                delay(20)
            }
        }
    }

    private fun parseTcp(text: String, minRtt: AtomicReference<Double?>, serverBytes: AtomicLong) {
        runCatching {
            val o = JSONObject(text)
            val tcp = o.optJSONObject("TCPInfo") ?: return
            if (tcp.has("MinRTT")) {
                val ms = tcp.getLong("MinRTT") / 1000.0
                if (ms > 0) minRtt.updateAndGet { cur -> if (cur == null || ms < cur) ms else cur }
            }
            if (tcp.has("BytesReceived")) {
                val n = tcp.getLong("BytesReceived")
                if (n > 0) serverBytes.set(n)
            }
            val app = o.optJSONObject("AppInfo")
            if (app != null && app.has("NumBytes")) {
                val n = app.getLong("NumBytes")
                if (n > serverBytes.get()) serverBytes.set(n)
            }
        }
    }

    private fun snapshot2s(e: Long, local: AtomicLong, localAt2s: AtomicLong, server: AtomicLong, serverAt2s: AtomicLong) {
        if (e >= SKIP_MS) {
            localAt2s.compareAndSet(-1, local.get())
            val sb = server.get()
            if (sb > 0) serverAt2s.compareAndSet(-1, sb)
        }
    }

    private fun avgMbps(
        e: Long,
        local: AtomicLong,
        localAt2s: AtomicLong,
        server: AtomicLong,
        serverAt2s: AtomicLong,
        upload: Boolean,
    ): Double? {
        val total = maxOf(local.get(), server.get())
        val at2 = maxOf(localAt2s.get(), serverAt2s.get())
        return if (e > SKIP_MS && at2 >= 0 && total > at2) {
            val w = mbps(total - at2, e - SKIP_MS)
            if (w > 0.0) w else mbps(total, e)
        } else mbps(total, e.coerceAtLeast(1))
    }

    private fun mbps(bytes: Long, ms: Long): Double {
        if (ms <= 0L || bytes <= 0L) return 0.0
        return bytes * 8.0 / ms / 1_000.0
    }

    private fun minOfRtt(a: Double?, b: Double?): Double? = when {
        a == null -> b
        b == null -> a
        else -> minOf(a, b)
    }
}
