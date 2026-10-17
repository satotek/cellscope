package dev.satotek.cellscope.data.backup

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Whole-app archive: every log, every snapshot and the settings in one .zip that the user
 * stores wherever they like (SAF). This is the only copy that survives an uninstall or a
 * signing-key change — getExternalFilesDir() is wiped together with the app, and
 * android:allowBackup="false" means the platform will not restore it either.
 *
 * Layout:
 *   manifest.json        app/version/timestamp/counts
 *   settings.json        typed dump of the "settings" SharedPreferences
 *   logs/        cellscope-*.csv and .jsonl
 *   snapshots/   cellscope-*.png and .json
 */
object BackupArchive {
    const val MIME = "application/zip"
    private const val PREFS = "settings"

    /** dir name -> file extensions accepted on the way back in. */
    private val DIRS = mapOf(
        "logs" to setOf("csv", "jsonl"),
        "snapshots" to setOf("png", "json"),
    )

    data class Counts(val logs: Int = 0, val snapshots: Int = 0, val settings: Boolean = false, val skipped: Int = 0) {
        val empty get() = logs == 0 && snapshots == 0 && !settings
    }

    fun suggestedName(): String =
        "cellscope-" + SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date()) + ".zip"

    // ---- export ---------------------------------------------------------------------------

    fun export(context: Context, out: OutputStream): Counts {
        var logs = 0
        var snaps = 0
        ZipOutputStream(out.buffered()).use { zip ->
            DIRS.keys.forEach { d ->
                File(context.getExternalFilesDir(null), d).listFiles()
                    ?.filter { it.isFile }
                    ?.sortedBy { it.name }
                    ?.forEach { f ->
                        zip.putNextEntry(ZipEntry("$d/${f.name}").apply { time = f.lastModified() })
                        f.inputStream().use { it.copyTo(zip) }
                        zip.closeEntry()
                        // Count the primary file of each pair so the number matches what the lists show.
                        if (f.name.endsWith(".csv", true)) logs++
                        if (f.name.endsWith(".png", true)) snaps++
                    }
            }
            val counts = Counts(logs, snaps, settings = true)
            zip.entry("settings.json", settingsJson(context).toString(2))
            zip.entry("manifest.json", manifest(context, counts).toString(2))
            return counts
        }
    }

    private fun ZipOutputStream.entry(name: String, body: String) {
        putNextEntry(ZipEntry(name))
        write(body.toByteArray())
        closeEntry()
    }

    /** SharedPreferences are untyped on the way out, so each value carries its type tag. */
    private fun settingsJson(context: Context): JSONObject {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val o = JSONObject()
        p.all.forEach { (k, v) ->
            val t = when (v) {
                is String -> "s"; is Boolean -> "b"; is Int -> "i"; is Long -> "l"; is Float -> "f"
                else -> return@forEach   // string sets are not used here
            }
            o.put(k, JSONObject().put("t", t).put("v", v))
        }
        return o
    }

    private fun manifest(context: Context, c: Counts): JSONObject {
        val pi = runCatching { context.packageManager.getPackageInfo(context.packageName, 0) }.getOrNull()
        return JSONObject()
            .put("app", context.packageName)
            .put("format", 1)
            .put("versionName", pi?.versionName ?: "")
            .put("versionCode", pi?.longVersionCode ?: 0L)
            .put("created", System.currentTimeMillis())
            .put("logs", c.logs)
            .put("snapshots", c.snapshots)
            .put("secrets", false)
    }

    // ---- import ---------------------------------------------------------------------------

    /**
     * Merges an archive back in. Files are added next to the existing ones; a file that is
     * already there byte-for-byte is skipped, so re-importing the same archive is a no-op
     * rather than a duplicate.
     */
    fun import(context: Context, input: InputStream, restoreSettings: Boolean): Counts {
        var logs = 0
        var snaps = 0
        var skipped = 0
        var settings = false
        ZipInputStream(input.buffered()).use { zip ->
            while (true) {
                val e: ZipEntry = zip.nextEntry ?: break
                if (e.isDirectory) { zip.closeEntry(); continue }
                val path = e.name.replace('\\', '/')
                val dir = path.substringBeforeLast('/', "")
                // Only the base name is ever used, so "../" in an entry cannot escape (Zip Slip).
                val name = path.substringAfterLast('/')
                when {
                    path == "settings.json" -> if (restoreSettings) {
                        settings = applySettings(context, JSONObject(zip.readBytes().decodeToString()))
                    }
                    DIRS.containsKey(dir) && name.substringAfterLast('.', "").lowercase() in DIRS.getValue(dir) -> {
                        when (write(File(context.getExternalFilesDir(null), dir), name, zip.readBytes())) {
                            Written.NEW -> { if (name.endsWith(".csv", true)) logs++; if (name.endsWith(".png", true)) snaps++ }
                            Written.DUPLICATE -> if (name.endsWith(".csv", true) || name.endsWith(".png", true)) skipped++
                        }
                    }
                }
                zip.closeEntry()
            }
        }
        return Counts(logs, snaps, settings, skipped)
    }

    private enum class Written { NEW, DUPLICATE }

    private fun write(dir: File, name: String, bytes: ByteArray): Written {
        dir.mkdirs()
        var target = File(dir, name)
        var n = 1
        while (target.exists()) {
            if (target.length() == bytes.size.toLong() && target.readBytes().contentEquals(bytes)) return Written.DUPLICATE
            target = File(dir, "${name.substringBeforeLast('.')}-${n++}.${name.substringAfterLast('.')}")
        }
        target.writeBytes(bytes)
        return Written.NEW
    }

    private fun applySettings(context: Context, o: JSONObject): Boolean {
        val ed = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        o.keys().forEach { k ->
            val v = o.optJSONObject(k) ?: return@forEach
            when (v.optString("t")) {
                "s" -> ed.putString(k, v.optString("v"))
                "b" -> ed.putBoolean(k, v.optBoolean("v"))
                "i" -> ed.putInt(k, v.optInt("v"))
                "l" -> ed.putLong(k, v.optLong("v"))
                "f" -> ed.putFloat(k, v.optDouble("v").toFloat())
            }
        }
        return ed.commit()
    }
}
