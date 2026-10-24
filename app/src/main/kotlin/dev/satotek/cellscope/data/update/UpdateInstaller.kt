package dev.satotek.cellscope.data.update

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import dev.satotek.cellscope.data.root.PrivAppInstaller
import dev.satotek.cellscope.data.root.RootShell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

sealed interface InstallState {
    data object Idle : InstallState
    data class Downloading(val fraction: Float?) : InstallState
    data object Verifying : InstallState
    data object Installing : InstallState
    /** The system installer needs the user to confirm; the UI launches [intent] while in front. */
    data class NeedsConfirmation(val intent: Intent) : InstallState
    data class Failed(val message: String) : InstallState
}

/** How the device can take an update; decided by the caller from root and priv-app state. */
enum class InstallRoute { ROOT_PRIVAPP, ROOT, PACKAGE_INSTALLER }

/**
 * Downloads a release APK, proves it is ours, and installs it. Root installs without asking;
 * otherwise the system PackageInstaller shows its own confirmation. The APK must carry our
 * package name, a higher versionCode and exactly our signing certificates, so a swapped asset
 * is never installed, least of all silently as root.
 */
class UpdateInstaller(private val context: Context) {
    private val app = context.applicationContext
    private val client = OkHttpClient.Builder().callTimeout(5, TimeUnit.MINUTES).build()
    val state: StateFlow<InstallState> get() = events

    suspend fun install(release: ReleaseInfo, route: InstallRoute) {
        if (events.value.busy) return
        val url = release.apkUrl ?: return fail("this release has no APK")
        val apk = runCatching { download(url) }.getOrElse { return fail("download: ${it.message}") }
        events.value = InstallState.Verifying
        val info = withContext(Dispatchers.IO) { verify(apk) }.getOrElse { return fail(it.message ?: "verification failed") }
        events.value = InstallState.Installing
        when (route) {
            InstallRoute.PACKAGE_INSTALLER -> runCatching { commitSession(apk) }.onFailure { fail("install: ${it.message}") }
            InstallRoute.ROOT, InstallRoute.ROOT_PRIVAPP -> rootInstall(apk, info, privApp = route == InstallRoute.ROOT_PRIVAPP)
        }
    }

    fun dismiss() { if (!events.value.busy) events.value = InstallState.Idle }

    /** The prompt is on screen; its outcome comes back through [InstallResultReceiver]. */
    fun confirmationShown() { events.value = InstallState.Installing }

    private fun fail(message: String) { events.value = InstallState.Failed(message) }

    private suspend fun download(url: String): File = withContext(Dispatchers.IO) {
        val dir = File(app.cacheDir, "update").apply { deleteRecursively(); mkdirs() }
        val out = File(dir, "update.apk")
        events.value = InstallState.Downloading(null)
        client.newCall(Request.Builder().url(url).build()).execute().use { r ->
            if (!r.isSuccessful) error("HTTP ${r.code}")
            val total = r.body.contentLength().takeIf { it > 0 }
            r.body.byteStream().use { input ->
                out.outputStream().use { output ->
                    val buf = ByteArray(64 * 1024); var done = 0L; var n: Int
                    while (input.read(buf).also { n = it } >= 0) {
                        output.write(buf, 0, n); done += n
                        events.value = InstallState.Downloading(total?.let { done.toFloat() / it })
                    }
                }
            }
        }
        out
    }

    private fun verify(apk: File): Result<PackageInfo> = runCatching {
        val pm = app.packageManager
        val flags = PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong())
        val candidate = pm.getPackageArchiveInfo(apk.path, flags) ?: error("not a readable APK")
        val self = pm.getPackageInfo(app.packageName, flags)
        check(candidate.packageName == app.packageName) {
            "package ${candidate.packageName} does not match ${app.packageName}"
        }
        check(candidate.longVersionCode > self.longVersionCode) {
            "version ${candidate.longVersionCode} is not newer than ${self.longVersionCode}"
        }
        check(certs(candidate).isNotEmpty() && certs(candidate) == certs(self)) { "signing certificate does not match" }
        candidate
    }

    private fun certs(info: PackageInfo): Set<String> {
        val signing = info.signingInfo ?: return emptySet()
        val sigs = if (signing.hasMultipleSigners()) signing.apkContentsSigners else signing.signingCertificateHistory
        val sha = MessageDigest.getInstance("SHA-256")
        return sigs.orEmpty().map { s -> sha.digest(s.toByteArray()).joinToString("") { "%02x".format(it) } }.toSet()
    }

    private fun commitSession(apk: File) {
        val installer = app.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(app.packageName)
            // Skips the prompt only when Android already counts us as this package's installer.
            setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val id = installer.createSession(params)
        installer.openSession(id).use { session ->
            session.openWrite("base.apk", 0, apk.length()).use { out ->
                apk.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }
            val callback = PendingIntent.getBroadcast(
                app, id, Intent(app, InstallResultReceiver::class.java).setPackage(app.packageName),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            )
            session.commit(callback.intentSender)
        }
    }

    /**
     * pm replaces this very process, so the install runs detached and relaunches the app itself.
     * A priv-app first gets its module rewritten from the same verified APK, so the system copy
     * matches after the next reboot without running any script that came with a download.
     */
    private suspend fun rootInstall(apk: File, info: PackageInfo, privApp: Boolean) {
        if (privApp) {
            PrivAppInstaller.install(app, info.versionName ?: "", info.longVersionCode, apk)?.let { return fail("module: $it") }
        }
        val pkg = app.packageName
        val staged = "/data/local/tmp/cellscope-update.apk"
        val log = "/data/local/tmp/cellscope-update.log"
        val launch = "$pkg/dev.satotek.cellscope.MainActivity"
        val r = RootShell.run(
            """
            cp "${apk.absolutePath}" $staged && chmod 0644 $staged || exit 1
            rm -f $log
            setsid sh -c 'pm install -r $staged > $log 2>&1 && am start -n $launch; rm -f $staged' < /dev/null > /dev/null 2>&1 &
            echo started
            """.trimIndent(), 30,
        )
        if (!r.ok || !r.out.contains("started")) return fail("root: ${r.err.ifBlank { "exit ${r.exit}" }}")
        // On success this process is killed before the loop ends; reaching the end means pm refused.
        repeat(30) {
            delay(1_000)
            val out = RootShell.run("cat $log 2>/dev/null", 5).out
            if (out.contains("Failure")) return fail(out.trim().lineSequence().last())
        }
        fail("install did not finish")
    }

    companion object {
        private val events = MutableStateFlow<InstallState>(InstallState.Idle)
        private val InstallState.busy get() = this is InstallState.Downloading || this is InstallState.Verifying || this is InstallState.Installing

        internal fun onSessionResult(intent: Intent) {
            when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
                PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                    val confirm = intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                    events.value = confirm?.let { InstallState.NeedsConfirmation(it) } ?: InstallState.Failed("no confirmation intent")
                }
                PackageInstaller.STATUS_SUCCESS -> events.value = InstallState.Idle
                else -> events.value = InstallState.Failed(
                    intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "status $status",
                )
            }
        }
    }
}

/** Receives the PackageInstaller session outcome; the activity, not this receiver, opens any prompt. */
class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = UpdateInstaller.onSessionResult(intent)
}
