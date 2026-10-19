package dev.satotek.cellscope.data.settings

import android.content.Context
import dev.satotek.cellscope.data.speed.SpeedResult
import dev.satotek.cellscope.data.speed.SpeedResultStore
import org.json.JSONArray

/** Retains the existing keys so upgrades and backup restoration share the same format. */
class SettingsStore(context: Context) : SpeedResultStore {
    private val prefs = context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var pollIntervalMs: Long
        get() = prefs.getLong("poll_ms", 1000L)
        set(value) { prefs.edit().putLong("poll_ms", value).apply() }

    var pingHost: String
        get() = prefs.getString("ping_host", "1.1.1.1") ?: "1.1.1.1"
        set(value) { prefs.edit().putString("ping_host", value).apply() }

    var pingEnabled: Boolean
        get() = prefs.getBoolean("ping_on", true)
        set(value) { prefs.edit().putBoolean("ping_on", value).apply() }

    var speedServer: String
        get() = prefs.getString("speed_server", "") ?: ""
        set(value) { prefs.edit().putString("speed_server", value).apply() }

    var themeMode: String
        get() = prefs.getString("theme", "system") ?: "system"
        set(value) { prefs.edit().putString("theme", value).apply() }

    var pipAuto: Boolean
        get() = prefs.getBoolean("pip_auto", true)
        set(value) { prefs.edit().putBoolean("pip_auto", value).apply() }

    var overlayEnabled: Boolean
        get() = prefs.getBoolean("overlay", false)
        set(value) { prefs.edit().putBoolean("overlay", value).apply() }

    var gnbBits: Int
        get() = prefs.getInt("gnb_bits", 24)
        set(value) { prefs.edit().putInt("gnb_bits", value).apply() }

    override fun load(): List<SpeedResult> {
        val raw = prefs.getString("speed_results", null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { SpeedResult.fromJson(arr.getJSONObject(it)) }
        }.getOrDefault(emptyList())
    }

    override fun save(results: List<SpeedResult>) {
        val arr = JSONArray()
        results.forEach { arr.put(it.toJson()) }
        prefs.edit().putString("speed_results", arr.toString()).apply()
    }
}
