package dev.satotek.cellscope.ui.screens

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.BitmapFactory
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.DataObject
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.satotek.cellscope.MainViewModel
import dev.satotek.cellscope.R
import dev.satotek.cellscope.data.root.PrivAppInstaller
import dev.satotek.cellscope.data.model.PrivilegeLevel
import dev.satotek.cellscope.data.model.Snapshot
import dev.satotek.cellscope.data.snapshot.SnapshotWriter
import org.json.JSONArray
import org.json.JSONObject
import dev.satotek.cellscope.ui.components.KeyValueRow
import dev.satotek.cellscope.ui.components.Panel
import dev.satotek.cellscope.ui.theme.Mono
import dev.satotek.cellscope.ui.theme.Palette
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val FILE_AUTHORITY = "dev.satotek.cellscope.files"

@Composable
fun MoreScreen(vm: MainViewModel, state: Snapshot, onExit: () -> Unit = {}) {
    val privApp by vm.privApp.collectAsStateWithLifecycle()
    MoreScreen(state, privApp, setup = { SettingsScreen(vm, state, showTitle = false) }, onExit = onExit)
}

@Composable
fun MoreScreen(state: Snapshot, privApp: PrivAppInstaller.State?, setup: @Composable () -> Unit = {}, onExit: () -> Unit = {}) {
    var sub by rememberSaveable { mutableStateOf<String?>(null) }
    var replayPath by rememberSaveable { mutableStateOf<String?>(null) }
    when (sub) {
        "setup" -> SubScreen(stringResource(R.string.more_setup), onBack = { sub = null }, content = setup)
        "raw" -> SubScreen(stringResource(R.string.more_raw), onBack = { sub = null }) { RawScreen(state, showTitle = false) }
        "logs" -> {
            val rf = replayPath
            if (rf == null) {
                SubScreen(stringResource(R.string.more_logs), onBack = { sub = null }) {
                    LogsPane(onOpen = { replayPath = it.absolutePath })
                }
            } else {
                SubScreen(File(rf).name, onBack = { replayPath = null }) { ReplayScreen(File(rf)) }
            }
        }
        "snapshots" -> SubScreen(stringResource(R.string.more_snapshots), onBack = { sub = null }) { SnapshotsPane() }
        "about" -> SubScreen(stringResource(R.string.more_about), onBack = { sub = null }) { AboutPane(state, privApp) }
        else -> MoreHome(onOpen = { sub = it }, onExit = onExit)
    }
}

@Composable
private fun MoreHome(onOpen: (String) -> Unit, onExit: () -> Unit) {
    var confirmExit by remember { mutableStateOf(false) }
    val rows = listOf(
        Triple("setup", R.string.more_setup, Icons.Outlined.Settings),
        Triple("raw", R.string.more_raw, Icons.Outlined.Terminal),
        Triple("logs", R.string.more_logs, Icons.Outlined.Description),
        Triple("snapshots", R.string.more_snapshots, Icons.Outlined.PhotoLibrary),
        Triple("about", R.string.more_about, Icons.Outlined.Info),
    )
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
        item { Text(stringResource(R.string.tab_more), style = MaterialTheme.typography.headlineMedium, color = Palette.text, modifier = Modifier.padding(bottom = 12.dp)) }
        item {
            Panel(padding = 0.dp) {
                rows.forEachIndexed { i, (id, title, icon) ->
                    ListItem(
                        headlineContent = { Text(stringResource(title)) },
                        leadingContent = { Icon(icon, null, tint = Palette.textDim) },
                        trailingContent = { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = Palette.textDim) },
                        modifier = Modifier.clickable { onOpen(id) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                    if (i != rows.lastIndex) HorizontalDivider(color = Palette.outline)
                }
            }
        }
        item {
            // Exit: the ViewModel is process-wide, so leaving via Home keeps polling; this actually stops it.
            Panel(padding = 0.dp, modifier = Modifier.padding(top = 12.dp)) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.exit_app), color = Palette.poor) },
                    leadingContent = { Icon(Icons.AutoMirrored.Outlined.Logout, null, tint = Palette.poor) },
                    modifier = Modifier.clickable { confirmExit = true },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
            }
        }
    }
    if (confirmExit) {
        AlertDialog(
            onDismissRequest = { confirmExit = false },
            title = { Text(stringResource(R.string.exit_app)) },
            text = { Text(stringResource(R.string.exit_body)) },
            confirmButton = { Button(onClick = { confirmExit = false; onExit() }, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.exit_app)) } },
            dismissButton = { TextButton(onClick = { confirmExit = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun SubScreen(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back), tint = Palette.text) }
            Text(title, style = MaterialTheme.typography.titleLarge, color = Palette.text)
        }
        Box(Modifier.weight(1f)) { content() }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LogsPane(onOpen: (File) -> Unit) {
    val context = LocalContext.current
    var gen by remember { mutableIntStateOf(0) }
    val files = remember(gen) {
        File(context.getExternalFilesDir(null), "logs").listFiles()
            ?.filter { it.isFile && it.name.endsWith(".csv", ignoreCase = true) }
            ?.sortedByDescending { it.lastModified() }
            .orEmpty()
    }
    var pending by remember { mutableStateOf<File?>(null) }
    val fmtT = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US) }
    val csvOnly = stringResource(R.string.log_csv_only)

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
        if (files.isEmpty()) {
            item { Text("—", fontFamily = Mono, color = Palette.textDim) }
        } else {
            item {
                Panel(padding = 0.dp) {
                    files.forEachIndexed { i, f ->
                        val jsonl = File(f.parentFile, f.nameWithoutExtension + ".jsonl")
                        val kind = if (jsonl.isFile) "jsonl" else csvOnly
                        ListItem(
                            headlineContent = { Text(f.name, fontFamily = Mono, fontSize = 13.sp) },
                            supportingContent = {
                                Text(
                                    "${fmtSize(f.length())} · ${fmtT.format(Date(f.lastModified()))} · $kind",
                                    fontFamily = Mono, fontSize = 12.sp, color = Palette.textDim,
                                )
                            },
                            trailingContent = {
                                IconButton(onClick = { shareCsv(context, f) }) {
                                    Icon(Icons.Outlined.Share, stringResource(R.string.share), tint = Palette.textDim)
                                }
                            },
                            modifier = Modifier.combinedClickable(
                                onClick = { onOpen(f) },
                                onLongClick = { pending = f },
                            ),
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        )
                        if (i != files.lastIndex) HorizontalDivider(color = Palette.outline)
                    }
                }
            }
        }
    }
    pending?.let { f ->
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text(stringResource(R.string.confirm_delete_log_title)) },
            text = { Text(stringResource(R.string.confirm_delete_log_body, f.name), fontFamily = Mono) },
            confirmButton = {
                Button(
                    onClick = {
                        File(f.parentFile, f.nameWithoutExtension + ".jsonl").delete()
                        f.delete()
                        pending = null
                        gen++
                    },
                    shapes = ButtonDefaults.shapes(),
                ) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = { TextButton(onClick = { pending = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SnapshotsPane() {
    val context = LocalContext.current
    var gen by remember { mutableIntStateOf(0) }
    val files = remember(gen) { SnapshotWriter.listPng(context) }
    var pending by remember { mutableStateOf<File?>(null) }
    val fmtT = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
        if (files.isEmpty()) {
            item { Text("—", fontFamily = Mono, color = Palette.textDim) }
        } else {
            item {
                Panel(padding = 0.dp) {
                    files.forEachIndexed { i, f ->
                        key(f.absolutePath) {
                        val meta = remember(f) { snapshotMeta(f, fmtT) }
                        val thumb = remember(f) { decodeThumb(f) }
                        ListItem(
                            headlineContent = { Text(meta.time, fontFamily = Mono, fontSize = 13.sp) },
                            supportingContent = {
                                Text(meta.summary, fontFamily = Mono, fontSize = 12.sp, color = Palette.textDim)
                            },
                            leadingContent = {
                                if (thumb != null) {
                                    Image(
                                        thumb.asImageBitmap(),
                                        null,
                                        Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)),
                                        contentScale = ContentScale.Crop,
                                    )
                                }
                            },
                            trailingContent = {
                                IconButton(onClick = { SnapshotWriter.shareJson(context, f) }) {
                                    Icon(Icons.Outlined.DataObject, "JSON", tint = Palette.textDim)
                                }
                            },
                            modifier = Modifier.combinedClickable(
                                onClick = { SnapshotWriter.share(context, f) },
                                onLongClick = { pending = f },
                            ),
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        )
                        if (i != files.lastIndex) HorizontalDivider(color = Palette.outline)
                        }
                    }
                }
            }
        }
    }
    pending?.let { f ->
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text(stringResource(R.string.confirm_delete_snapshot_title)) },
            text = { Text(stringResource(R.string.confirm_delete_log_body, f.name), fontFamily = Mono) },
            confirmButton = {
                Button(
                    onClick = { SnapshotWriter.delete(f); pending = null; gen++ },
                    shapes = ButtonDefaults.shapes(),
                ) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = { TextButton(onClick = { pending = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

private data class SnapshotMeta(val time: String, val summary: String)

private fun snapshotMeta(png: File, fmtT: SimpleDateFormat): SnapshotMeta {
    val jsonFile = SnapshotWriter.jsonFile(png)
    val json = runCatching { JSONObject(jsonFile.readText()) }.getOrNull()
    val takenAt = json?.optLong("takenAt", png.lastModified())?.takeIf { it > 0 } ?: png.lastModified()
    val serving = json?.optJSONObject("serving")
    val summary = if (serving == null) png.name else {
        val rat = serving.optString("rat", "—")
        val pci = if (serving.has("pci")) serving.optInt("pci").toString() else "—"
        val bands = serving.optJSONArray("bands") ?: JSONArray()
        val band = bandLabel(rat, bands)
        "$rat $band · PCI $pci"
    }
    return SnapshotMeta(fmtT.format(Date(takenAt)), summary)
}

private fun bandLabel(rat: String, bands: JSONArray): String {
    if (bands.length() == 0) return "—"
    val list = (0 until bands.length()).map { bands.optInt(it) }
    return when (rat) {
        "NR" -> list.joinToString("/") { "n$it" }
        "LTE" -> list.joinToString("/") { "B$it" }
        else -> list.joinToString("/")
    }
}

private fun decodeThumb(file: File, maxPx: Int = 128): android.graphics.Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, bounds)
    var sample = 1
    val largest = maxOf(bounds.outWidth, bounds.outHeight).coerceAtLeast(1)
    while (largest / sample > maxPx * 2) sample *= 2
    return BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
}

@Composable
private fun AboutPane(state: Snapshot, privApp: PrivAppInstaller.State?) {
    val context = LocalContext.current
    val version = remember {
        runCatching {
            val pm = context.packageManager
            val pkg = context.packageName
            if (Build.VERSION.SDK_INT >= 33) pm.getPackageInfo(pkg, android.content.pm.PackageManager.PackageInfoFlags.of(0)).versionName
            else @Suppress("DEPRECATION") pm.getPackageInfo(pkg, 0).versionName
        }.getOrNull() ?: "—"
    }
    val debug = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    val ksu = when (privApp) {
        PrivAppInstaller.State.ACTIVE -> stringResource(R.string.privapp_active)
        PrivAppInstaller.State.PENDING_REBOOT -> stringResource(R.string.privapp_pending_reboot)
        PrivAppInstaller.State.PENDING_REMOVAL -> stringResource(R.string.privapp_pending_removal)
        PrivAppInstaller.State.NOT_INSTALLED -> stringResource(R.string.privapp_inactive)
        null -> if (state.privilege == PrivilegeLevel.PRIV_APP) stringResource(R.string.privapp_active) else stringResource(R.string.privapp_inactive)
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
        item {
            Panel {
                KeyValueRow(stringResource(R.string.about_version), version)
                KeyValueRow(stringResource(R.string.about_privilege), stringResource(state.privilege.labelRes))
                KeyValueRow(
                    stringResource(R.string.about_permissions),
                    stringResource(if (state.permissionsGranted) R.string.status_perm_ok else R.string.status_perm_missing),
                )
                KeyValueRow(stringResource(R.string.about_ksu), ksu)
                KeyValueRow(stringResource(R.string.about_build), stringResource(if (debug) R.string.build_debug else R.string.build_release))
            }
        }
    }
}

private fun shareCsv(context: android.content.Context, file: File) {
    val uri = FileProvider.getUriForFile(context, FILE_AUTHORITY, file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/csv"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, context.getString(R.string.share)))
}

private fun fmtSize(n: Long): String = when {
    n < 1000 -> "$n B"
    n < 1_000_000 -> String.format(Locale.US, "%.1f KB", n / 1000.0)
    else -> String.format(Locale.US, "%.1f MB", n / 1_000_000.0)
}
