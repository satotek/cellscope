package dev.satotek.cellscope.data.update

import android.content.Context
import dev.satotek.cellscope.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** [apkUrl] is the release's own APK asset; null when a release ships none. [notes] is its Markdown body. */
data class ReleaseInfo(val version: String, val url: String, val apkUrl: String? = null, val notes: String? = null)

sealed interface UpdateState {
    data object Unknown : UpdateState
    data object Checking : UpdateState
    data class UpToDate(val latest: ReleaseInfo, val checkedAt: Long) : UpdateState
    data class Available(val latest: ReleaseInfo, val checkedAt: Long) : UpdateState
    data class Failed(val message: String, val checkedAt: Long?) : UpdateState
}

data class NoteLine(val text: String, val heading: Boolean)

/** Pure parts, kept apart from Android so they can be unit-tested. */
object Releases {
    const val LATEST_API = "https://api.github.com/repos/satotek/cellscope/releases/latest"

    fun parse(json: String): ReleaseInfo {
        val o = JSONObject(json)
        val tag = o.getString("tag_name")
        val assets = o.optJSONArray("assets")
        val apk = (0 until (assets?.length() ?: 0)).map { assets!!.getJSONObject(it) }
            .firstOrNull { it.getString("name") == apkAssetName(tag) }?.getString("browser_download_url")
        val notes = o.optString("body").takeIf { o.has("body") && !o.isNull("body") && it.isNotBlank() }
        return ReleaseInfo(tag, o.getString("html_url"), apk, notes)
    }

    /** The name release.yml gives the APK it attaches. */
    fun apkAssetName(tag: String) = "cellscope-$tag.apk"

    /**
     * The notes as display lines: "## Features" → heading, "- **scope**: text (abc1234)" → bullet.
     * The compare link and Markdown emphasis are noise on a phone screen.
     */
    fun noteLines(notes: String): List<NoteLine> = notes.lineSequence().map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("**Full changelog**", ignoreCase = true) }
        .map { line ->
            val text = line.replace("**", "").replace(Regex("""\s+\([0-9a-f]{7,40}\)$"""), "")
            when {
                text.startsWith("#") -> NoteLine(text.trimStart('#').trim(), heading = true)
                text.startsWith("- ") || text.startsWith("* ") -> NoteLine(text.drop(2), heading = false)
                else -> NoteLine(text, heading = false)
            }
        }.toList()

    /** "v0.1.2" or "0.1.2-debug" → [0, 1, 2]; anything after '-' is a build flavour, not a version. */
    fun parts(version: String): List<Int> =
        version.removePrefix("v").substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }

    fun isNewer(latest: String, current: String): Boolean {
        val a = parts(latest); val b = parts(current)
        for (i in 0 until maxOf(a.size, b.size)) {
            val d = a.getOrElse(i) { 0 } - b.getOrElse(i) { 0 }
            if (d != 0) return d > 0
        }
        return false
    }
}

/**
 * Asks GitHub for the latest release. The cached answer lives in its own preferences file so a
 * restored backup cannot bring back a stale "update available".
 */
class UpdateRepository(context: Context, private val now: () -> Long = System::currentTimeMillis) {
    private val prefs = context.applicationContext.getSharedPreferences("update", Context.MODE_PRIVATE)
    private val client = OkHttpClient.Builder().callTimeout(15, TimeUnit.SECONDS).build()
    private val current = BuildConfig.VERSION_NAME

    private val _state = MutableStateFlow(cached())
    val state: StateFlow<UpdateState> = _state

    private fun cached(): UpdateState {
        val version = prefs.getString("latest_version", null) ?: return UpdateState.Unknown
        val info = ReleaseInfo(version, prefs.getString("latest_url", null) ?: return UpdateState.Unknown,
            prefs.getString("latest_apk", null), prefs.getString("latest_notes", null))
        val at = prefs.getLong("checked_at", 0L)
        return if (Releases.isNewer(info.version, current)) UpdateState.Available(info, at) else UpdateState.UpToDate(info, at)
    }

    /** Once a day is plenty for a hobby release cadence and stays far below GitHub's anonymous limit. */
    fun isDue(): Boolean = now() - prefs.getLong("checked_at", 0L) >= DAY_MS

    suspend fun check() {
        if (_state.value == UpdateState.Checking) return
        val previous = _state.value
        _state.value = UpdateState.Checking
        _state.value = runCatching { fetch() }.fold(
            onSuccess = { info ->
                val at = now()
                prefs.edit().putString("latest_version", info.version).putString("latest_url", info.url)
                    .putString("latest_apk", info.apkUrl).putString("latest_notes", info.notes)
                    .putLong("checked_at", at).apply()
                if (Releases.isNewer(info.version, current)) UpdateState.Available(info, at) else UpdateState.UpToDate(info, at)
            },
            onFailure = { e ->
                val at = when (previous) {
                    is UpdateState.UpToDate -> previous.checkedAt
                    is UpdateState.Available -> previous.checkedAt
                    else -> null
                }
                UpdateState.Failed(e.message ?: e.javaClass.simpleName, at)
            },
        )
    }

    private suspend fun fetch(): ReleaseInfo = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(Releases.LATEST_API)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "CellScope/$current")
            .build()
        client.newCall(req).execute().use { r ->
            if (!r.isSuccessful) error("HTTP ${r.code}")
            Releases.parse(r.body.string())
        }
    }

    private companion object { const val DAY_MS = 24 * 60 * 60_000L }
}
