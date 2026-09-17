package dev.satotek.cellscope.data.speed

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Dns
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

/** Binds an OkHttp client to TRANSPORT_CELLULAR so a speed test ignores Wi-Fi. */
class CellularBind(
    private val cm: ConnectivityManager,
    private val callback: ConnectivityManager.NetworkCallback?,
    val network: Network?,
    val viaCellular: Boolean,
) {
    fun client(userAgent: String): OkHttpClient {
        val b = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .pingInterval(0, TimeUnit.SECONDS)
            .addNetworkInterceptor { chain ->
                chain.proceed(chain.request().newBuilder().header("User-Agent", userAgent).build())
            }
        network?.let { n ->
            b.socketFactory(n.socketFactory)
            // Resolve on the cellular Network first; some carriers' DNS misses
            // measurementlab.net, so fall back to the system resolver. Sockets
            // still go through [n] so the transfer itself stays on cellular.
            b.dns(object : Dns {
                override fun lookup(hostname: String): List<java.net.InetAddress> {
                    val cellular = runCatching { n.getAllByName(hostname).toList() }.getOrNull().orEmpty()
                    if (cellular.isNotEmpty()) return cellular
                    return Dns.SYSTEM.lookup(hostname)
                }
            })
        }
        return b.build()
    }

    fun close() {
        callback?.let { runCatching { cm.unregisterNetworkCallback(it) } }
    }
}

object CellularNetwork {
    suspend fun bind(context: Context, timeoutMs: Long = 5_000): CellularBind {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        val fallback = CellularBind(cm, null, null, false)
        val req = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        val main = Handler(Looper.getMainLooper())
        return withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine { cont ->
                val cb = object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        android.util.Log.i("Speed", "cellular onAvailable $network")
                        if (cont.isActive) cont.resume(CellularBind(cm, this, network, true))
                    }
                    override fun onUnavailable() {
                        android.util.Log.i("Speed", "cellular onUnavailable")
                        if (cont.isActive) cont.resume(fallback)
                    }
                }
                cont.invokeOnCancellation { runCatching { cm.unregisterNetworkCallback(cb) } }
                try {
                    cm.requestNetwork(req, cb, main)
                    android.util.Log.i("Speed", "requestNetwork cellular posted")
                } catch (e: Exception) {
                    android.util.Log.i("Speed", "requestNetwork failed: $e")
                    if (cont.isActive) cont.resume(fallback)
                }
            }
        } ?: fallback
    }
}
