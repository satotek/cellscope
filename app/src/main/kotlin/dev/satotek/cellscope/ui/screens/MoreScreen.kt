package dev.satotek.cellscope.ui.screens

import dev.satotek.cellscope.BuildConfig
import dev.satotek.cellscope.data.update.UpdateState
import dev.satotek.cellscope.data.update.InstallState
import dev.satotek.cellscope.data.update.NoteLine
import dev.satotek.cellscope.data.update.Releases
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.Switch
import android.content.Intent
import android.net.Uri
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import android.content.pm.ApplicationInfo
import android.graphics.BitmapFactory
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape

import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.CellTower
import androidx.compose.material.icons.outlined.DataObject
import androidx.compose.material.icons.outlined.FiberManualRecord
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Speed
import androidx.annotation.StringRes
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.platform.LocalResources
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
import dev.satotek.cellscope.data.speed.SpeedResult
import dev.satotek.cellscope.data.backup.BackupArchive
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONArray
import org.json.JSONObject
import dev.satotek.cellscope.ui.components.KeyValueRow
import dev.satotek.cellscope.ui.components.Panel
import dev.satotek.cellscope.ui.components.Tag
import dev.satotek.cellscope.ui.theme.Mono
import dev.satotek.cellscope.ui.theme.Palette
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val FILE_AUTHORITY = "${BuildConfig.APPLICATION_ID}.files"

@Composable
fun MoreScreen(vm: MainViewModel, state: Snapshot, onExit: () -> Unit = {}) {
    val privApp by vm.privApp.collectAsStateWithLifecycle()
    MoreScreen(state, privApp, setup = { sec -> SettingsScreen(vm, state, showTitle = false, section = sec) }, onExit = onExit, vm = vm)
}

@Composable
fun MoreScreen(state: Snapshot, privApp: PrivAppInstaller.State?, setup: @Composable (String) -> Unit = {}, onExit: () -> Unit = {}, vm: MainViewModel? = null) {
    var sub by rememberSaveable { mutableStateOf<String?>(null) }
    var replayPath by rememberSaveable { mutableStateOf<String?>(null) }
    val emptySpeed = remember { MutableStateFlow(emptyList<SpeedResult>()) }
    val speedResults by (vm?.speedResults ?: emptySpeed).collectAsStateWithLifecycle()
    val noUpdate = remember { MutableStateFlow<UpdateState>(UpdateState.Unknown) }
    val update by (vm?.update ?: noUpdate).collectAsStateWithLifecycle()
    val autoOn = remember { MutableStateFlow(true) }
    val updateAuto by (vm?.updateAuto ?: autoOn).collectAsStateWithLifecycle()
    val idleInstall = remember { MutableStateFlow<InstallState>(InstallState.Idle) }
    val install by (vm?.install ?: idleInstall).collectAsStateWithLifecycle()
    val s = sub
    when {
        s == null -> MoreHome(update, onOpen = { sub = it }, onExit = onExit)
        s.startsWith("set:") -> {
            val sec = s.removePrefix("set:")
            SubScreen(stringResource(settingsSections.first { it.id == sec }.title), onBack = { sub = null }) { setup(sec) }
        }
        else -> when (s) {
        "visits" -> SubScreen(stringResource(R.string.more_visits), onBack = { sub = null }) { if (vm != null) VisitedCellsScreen(vm) }
        "stats" -> SubScreen(stringResource(R.string.tab_stats), onBack = { sub = null }) { StatsScreen(state) }
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
        "speed" -> SubScreen(stringResource(R.string.more_speed), onBack = { sub = null }) {
            if (vm != null) SpeedHistoryPane(vm, speedResults) else Text("—", fontFamily = Mono, color = Palette.textDim, modifier = Modifier.padding(16.dp))
        }
        "backup" -> SubScreen(stringResource(R.string.more_backup), onBack = { sub = null }) { BackupPane(vm) }
        "snapshots" -> SubScreen(stringResource(R.string.more_snapshots), onBack = { sub = null }) { SnapshotsPane() }
        "about" -> SubScreen(stringResource(R.string.more_about), onBack = { sub = null }) {
            AboutPane(state, privApp, onOpen = { sub = it }, update = update, updateAuto = updateAuto,
                onCheckUpdate = { vm?.checkForUpdates() }, onUpdateAuto = { vm?.setUpdateAuto(it) },
                install = install, canInstall = vm?.canInstallUpdates == true,
                onInstall = { vm?.installUpdate() }, onDismissInstall = { vm?.dismissInstall() })
        }
        "license" -> SubScreen(stringResource(R.string.about_license), onBack = { sub = "about" }) { AssetTextPane("LICENSE") }
        "oss" -> SubScreen(stringResource(R.string.about_oss), onBack = { sub = "about" }) { OssPane() }
        else -> MoreHome(update, onOpen = { sub = it }, onExit = onExit)
        }
    }
}

private data class MoreRow(val id: String, @param:StringRes val title: Int, val icon: ImageVector)

/** One row per Settings group; the id after "set:" is what SettingsScreen filters on. */
private val settingsSections = listOf(
    MoreRow("measure", R.string.group_measure, Icons.Outlined.Speed),
    MoreRow("display", R.string.group_display, Icons.Outlined.Palette),
    MoreRow("radio", R.string.group_radio, Icons.Outlined.CellTower),
    MoreRow("privilege", R.string.group_privilege, Icons.Outlined.Security),
    MoreRow("record", R.string.group_record, Icons.Outlined.FiberManualRecord),
)
private val dataRows = listOf(
    MoreRow("visits", R.string.more_visits, Icons.Outlined.CellTower),
    MoreRow("stats", R.string.tab_stats, Icons.Filled.ShowChart),
    MoreRow("logs", R.string.more_logs, Icons.Outlined.Description),
    MoreRow("snapshots", R.string.more_snapshots, Icons.Outlined.PhotoLibrary),
    MoreRow("speed", R.string.more_speed, Icons.Outlined.Speed),
    MoreRow("backup", R.string.more_backup, Icons.Outlined.Backup),
)
private val miscRows = listOf(
    MoreRow("raw", R.string.more_raw, Icons.Outlined.Terminal),
    MoreRow("about", R.string.more_about, Icons.Outlined.Info),
)

@Composable
private fun MoreHome(update: UpdateState, onOpen: (String) -> Unit, onExit: () -> Unit) {
    var confirmExit by remember { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text(stringResource(R.string.tab_more), style = MaterialTheme.typography.headlineMedium, color = Palette.text) }
        if (update is UpdateState.Available) item {
            Panel(padding = 0.dp) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.update_available, update.latest.version), color = Palette.accent) },
                    supportingContent = { Text(stringResource(R.string.update_details)) },
                    leadingContent = { Icon(Icons.Outlined.SystemUpdate, null, tint = Palette.accent) },
                    trailingContent = { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = Palette.textDim) },
                    modifier = Modifier.clickable { onOpen("about") },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
            }
        }
        item { RowGroup(stringResource(R.string.more_setup), settingsSections) { onOpen("set:$it") } }
        item { RowGroup(stringResource(R.string.more_data), dataRows, onOpen) }
        item { RowGroup(null, miscRows, onOpen) }
        item {
            // Exit: the ViewModel is process-wide, so leaving via Home keeps polling; this actually stops it.
            Panel(padding = 0.dp) {
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
private fun RowGroup(title: String?, rows: List<MoreRow>, onOpen: (String) -> Unit) {
    Column {
        if (title != null) Text(title, style = MaterialTheme.typography.labelLarge, color = Palette.accent, modifier = Modifier.padding(start = 16.dp, bottom = 6.dp))
        Panel(padding = 0.dp) {
            rows.forEachIndexed { i, r ->
                ListItem(
                    headlineContent = { Text(stringResource(r.title)) },
                    leadingContent = { Icon(r.icon, null, tint = Palette.textDim) },
                    trailingContent = { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = Palette.textDim) },
                    modifier = Modifier.clickable { onOpen(r.id) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
                if (i != rows.lastIndex) HorizontalDivider(color = Palette.outline)
            }
        }
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
    val all = remember(gen) {
        File(context.getExternalFilesDir(null), "logs").listFiles()
            ?.filter { it.isFile && it.name.endsWith(".csv", ignoreCase = true) }
            ?.sortedByDescending { it.lastModified() }
            .orEmpty()
    }
    val deletes = rememberPendingDeletes(delete = ::deleteLog, onDone = { gen++ })
    val files = all.filter { it.absolutePath !in deletes.hidden }
    val fmtT = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US) }
    val csvOnly = stringResource(R.string.log_csv_only)

    // SAF picker: copies .csv / .jsonl into the logs dir (a CSV + its JSONL can be picked together).
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        val dir = File(context.getExternalFilesDir(null), "logs").apply { mkdirs() }
        uris.forEach { uri -> runCatching { importLog(context, uri, dir) } }
        if (uris.isNotEmpty()) gen++
    }
    FileListScaffold(
        files = files,
        totalBytes = files.sumOf { it.length() + jsonlOf(it).length() },
        deletes = deletes,
        deleteAllTitle = stringResource(R.string.confirm_delete_log_title),
        headerAction = {
            TextButton(onClick = { importLauncher.launch(arrayOf("text/*", "application/json", "application/octet-stream")) }) {
                Icon(Icons.Outlined.FileOpen, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.import_log))
            }
        },
    ) { f ->
        val kind = if (jsonlOf(f).isFile) "jsonl" else csvOnly
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
                onLongClick = { deletes.stage(f) },
            ),
            colors = ListItemDefaults.colors(containerColor = Palette.surface),
        )
    }
}

private fun jsonlOf(csv: File) = File(csv.parentFile, csv.nameWithoutExtension + ".jsonl")

/** Copies one picked document into [dir], keeping its display name; only CellScope's own extensions are accepted. */
private fun importLog(context: android.content.Context, uri: Uri, dir: File) {
    val name = context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
        if (c.moveToFirst()) c.getString(0) else null
    } ?: uri.lastPathSegment?.substringAfterLast('/') ?: return
    if (!name.endsWith(".csv", true) && !name.endsWith(".jsonl", true)) return
    var target = File(dir, name)
    var n = 1
    while (target.exists()) { target = File(dir, "${name.substringBeforeLast('.')}-${n++}.${name.substringAfterLast('.')}") }
    context.contentResolver.openInputStream(uri)?.use { inp -> target.outputStream().use { inp.copyTo(it) } }
}
private fun deleteLog(csv: File) { jsonlOf(csv).delete(); csv.delete() }

/**
 * Export / restore of everything the app owns, through SAF so the archive lands outside the
 * app sandbox — the one place uninstalling does not reach.
 */
@Composable
private fun BackupPane(vm: MainViewModel?) {
    val resources = LocalResources.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var restoreSettings by rememberSaveable { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    fun fail(t: Throwable) = resources.getString(R.string.backup_failed, t.message ?: t.javaClass.simpleName)

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(BackupArchive.MIME)) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true; message = null
        scope.launch {
            val r = withContext(Dispatchers.IO) {
                runCatching {
                    val out = context.contentResolver.openOutputStream(uri) ?: error("openOutputStream")
                    out.use { BackupArchive.export(context, it) }
                }
            }
            busy = false
            message = r.fold({ resources.getString(R.string.backup_exported, it.logs, it.snapshots) + resources.getString(R.string.backup_visits, it.visits) }, ::fail)
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true; message = null
        val withSettings = restoreSettings
        scope.launch {
            val r = withContext(Dispatchers.IO) {
                runCatching {
                    val inp = context.contentResolver.openInputStream(uri) ?: error("openInputStream")
                    inp.use { BackupArchive.import(context, it, withSettings) }
                }
            }
            busy = false
            r.onSuccess { if (it.settings) vm?.reloadPrefs() else vm?.reloadVisitedCells() }
            message = r.fold({ c ->
                resources.getString(R.string.backup_imported, c.logs, c.snapshots) + resources.getString(R.string.backup_visits, c.visits) +
                    if (c.skipped > 0) " · " + resources.getString(R.string.backup_skipped, c.skipped) else ""
            }, ::fail)
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Panel {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.backup_desc), fontSize = 13.sp, color = Palette.textDim)
            }
        }
        Button(
            onClick = { exportLauncher.launch(BackupArchive.suggestedName()) },
            enabled = !busy,
            shapes = ButtonDefaults.shapes(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Outlined.Backup, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.backup_export))
        }
        Panel(padding = 0.dp) {
            ListItem(
                headlineContent = { Text(stringResource(R.string.backup_restore_settings)) },
                trailingContent = { Checkbox(checked = restoreSettings, onCheckedChange = { restoreSettings = it }) },
                modifier = Modifier.clickable { restoreSettings = !restoreSettings },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )
        }
        OutlinedButton(
            onClick = { importLauncher.launch(arrayOf(BackupArchive.MIME, "application/octet-stream")) },
            enabled = !busy,
            shapes = ButtonDefaults.shapes(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Outlined.FileOpen, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.backup_import))
        }
        val note = if (busy) stringResource(R.string.backup_working) else message
        if (note != null) Text(note, fontFamily = Mono, fontSize = 12.sp, color = Palette.textDim)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SpeedHistoryPane(vm: MainViewModel, results: List<SpeedResult>) {
    val context = LocalContext.current
    val fmtT = remember { SimpleDateFormat("HH:mm:ss", Locale.US) }
    val visible = remember(results) { results }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${visible.size}", fontFamily = Mono, fontSize = 12.sp, color = Palette.textDim)
                if (visible.isNotEmpty()) {
                    TextButton(onClick = {
                        val dir = File(context.getExternalFilesDir(null), "logs").apply { mkdirs() }
                        shareCsv(context, vm.writeSpeedCsv(dir))
                    }) { Text(stringResource(R.string.speed_share_csv)) }
                }
            }
        }
        if (visible.isEmpty()) {
            item { Text("—", fontFamily = Mono, color = Palette.textDim) }
        } else {
            item {
                Panel(padding = 0.dp) {
                    visible.forEachIndexed { i, r ->
                        key(r.t) {
                            SwipeToDelete(onDelete = { vm.deleteSpeedResult(r) }) {
                                ListItem(
                                    headlineContent = {
                                        Text(
                                            "${fmtT.format(Date(r.t))}  ▼ ${String.format(Locale.US, "%.1f", r.dlMbps)}  ▲ ${String.format(Locale.US, "%.1f", r.ulMbps)}",
                                            fontFamily = Mono, fontSize = 13.sp,
                                        )
                                    },
                                    supportingContent = {
                                        Text(
                                            "RTT ${r.minRttMs?.let { String.format(Locale.US, "%.0f", it) } ?: "—"} ms · ${r.rat} ${r.band} PCI ${r.pci ?: "—"} · ${r.rsrp ?: "—"} dBm",
                                            fontFamily = Mono, fontSize = 12.sp, color = Palette.textDim,
                                        )
                                    },
                                    colors = ListItemDefaults.colors(containerColor = Palette.surface),
                                )
                            }
                            if (i != visible.lastIndex) HorizontalDivider(color = Palette.outline)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SnapshotsPane() {
    val resources = LocalResources.current
    val context = LocalContext.current
    var gen by remember { mutableIntStateOf(0) }
    val all = remember(gen) { SnapshotWriter.listPng(context) }
    val deletes = rememberPendingDeletes(delete = SnapshotWriter::delete, onDone = { gen++ })
    val files = all.filter { it.absolutePath !in deletes.hidden }
    val fmtT = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US) }

    FileListScaffold(
        files = files,
        totalBytes = files.sumOf { it.length() + SnapshotWriter.jsonFile(it).length() },
        deletes = deletes,
        deleteAllTitle = stringResource(R.string.confirm_delete_snapshot_title),
    ) { f ->
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
                Row {
                    IconButton(onClick = {
                        val message = runCatching { SnapshotWriter.copyImage(context, f) }.fold(
                            { resources.getString(R.string.snapshot_copied) },
                            { resources.getString(R.string.snapshot_copy_failed) },
                        )
                        android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(Icons.Outlined.ContentCopy, stringResource(R.string.snapshot_copy_image), tint = Palette.textDim)
                    }
                    IconButton(onClick = { SnapshotWriter.shareJson(context, f) }) {
                        Icon(Icons.Outlined.DataObject, "JSON", tint = Palette.textDim)
                    }
                }
            },
            modifier = Modifier.combinedClickable(
                onClick = { SnapshotWriter.share(context, f) },
                onLongClick = { deletes.stage(f) },
            ),
            colors = ListItemDefaults.colors(containerColor = Palette.surface),
        )
    }
}

/**
 * Gmail-style deletion: a swiped row leaves the list immediately, a snackbar offers undo, and the
 * file is only unlinked once the snackbar is gone (or the pane is left with deletes still staged).
 */
private class PendingDeletes(
    private val scope: CoroutineScope,
    val snackbar: SnackbarHostState,
    private val delete: (File) -> Unit,
    private val onDone: () -> Unit,
    private val deletedMsg: String,
    private val undoLabel: String,
) {
    val hidden = mutableStateListOf<String>()

    fun stage(f: File) {
        if (f.absolutePath in hidden) return
        hidden += f.absolutePath
        scope.launch {
            val r = snackbar.showSnackbar(deletedMsg, undoLabel, duration = SnackbarDuration.Short)
            if (r == SnackbarResult.ActionPerformed) {
                hidden -= f.absolutePath
            } else if (f.absolutePath in hidden) {
                commit(f)
            }
        }
    }

    fun commit(f: File) { delete(f); hidden -= f.absolutePath; onDone() }

    fun deleteAll(files: List<File>) {
        snackbar.currentSnackbarData?.dismiss()
        files.forEach { delete(it) }
        hidden.clear()
        onDone()
    }

    fun flush() { hidden.map(::File).forEach(delete); hidden.clear() }
}

@Composable
private fun rememberPendingDeletes(delete: (File) -> Unit, onDone: () -> Unit): PendingDeletes {
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val deletedMsg = stringResource(R.string.deleted)
    val undoLabel = stringResource(R.string.undo)
    val pd = remember { PendingDeletes(scope, snackbar, delete, onDone, deletedMsg, undoLabel) }
    // Leaving the pane cancels the snackbar coroutine, so staged deletes must be carried out here.
    DisposableEffect(pd) { onDispose { pd.flush() } }
    return pd
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun FileListScaffold(
    files: List<File>,
    totalBytes: Long,
    deletes: PendingDeletes,
    deleteAllTitle: String,
    headerAction: @Composable () -> Unit = {},
    row: @Composable (File) -> Unit,
) {
    var confirmAll by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
            item {
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("${files.size} · ${fmtSize(totalBytes)}", fontFamily = Mono, fontSize = 12.sp, color = Palette.textDim)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        headerAction()
                        if (files.isNotEmpty()) {
                            TextButton(onClick = { confirmAll = true }) {
                                Text(stringResource(R.string.delete_all), color = Palette.poor)
                            }
                        }
                    }
                }
            }
            if (files.isEmpty()) {
                item { Text("—", fontFamily = Mono, color = Palette.textDim) }
            } else {
                item {
                    Panel(padding = 0.dp) {
                        files.forEachIndexed { i, f ->
                            key(f.absolutePath) {
                                SwipeToDelete(onDelete = { deletes.stage(f) }) { row(f) }
                                if (i != files.lastIndex) HorizontalDivider(color = Palette.outline)
                            }
                        }
                    }
                }
            }
        }
        SnackbarHost(deletes.snackbar, Modifier.align(Alignment.BottomCenter))
    }
    if (confirmAll) {
        AlertDialog(
            onDismissRequest = { confirmAll = false },
            title = { Text(deleteAllTitle) },
            text = { Text("${files.size} · ${fmtSize(totalBytes)}", fontFamily = Mono) },
            confirmButton = {
                Button(
                    onClick = { confirmAll = false; deletes.deleteAll(files) },
                    colors = ButtonDefaults.buttonColors(containerColor = Palette.poor),
                    shapes = ButtonDefaults.shapes(),
                ) { Text(stringResource(R.string.delete_all)) }
            },
            dismissButton = { TextButton(onClick = { confirmAll = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

/** End-to-start swipe reveals a red bin; the row itself is opaque so it covers the background while at rest. */
@Composable
private fun SwipeToDelete(onDelete: () -> Unit, content: @Composable () -> Unit) {
    val state = rememberSwipeToDismissBoxState(positionalThreshold = { it * 0.45f })
    SwipeToDismissBox(
        state = state,
        enableDismissFromStartToEnd = false,
        onDismiss = { if (it == SwipeToDismissBoxValue.EndToStart) onDelete() },
        backgroundContent = {
            Box(
                Modifier.fillMaxSize().background(Palette.poor).padding(end = 24.dp),
                contentAlignment = Alignment.CenterEnd,
            ) { Icon(Icons.Outlined.Delete, stringResource(R.string.delete), tint = Color.White) }
        },
    ) { content() }
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

/** Publication facts in one place; strings.xml stays for translatable labels only. */
object AppInfo {
    const val AUTHOR = "satotek"
    const val AUTHOR_URL = "https://satotek.dev"
    const val SOURCE_URL = "https://github.com/satotek/cellscope"
    const val LICENSE = "MIT"
    const val COPYRIGHT = "© 2026 satotek"
}

@Composable
private fun AboutPane(
    state: Snapshot, privApp: PrivAppInstaller.State?, onOpen: (String) -> Unit,
    update: UpdateState = UpdateState.Unknown, updateAuto: Boolean = true,
    onCheckUpdate: () -> Unit = {}, onUpdateAuto: (Boolean) -> Unit = {},
    install: InstallState = InstallState.Idle, canInstall: Boolean = false,
    onInstall: () -> Unit = {}, onDismissInstall: () -> Unit = {},
) {
    val context = LocalContext.current
    val pkgInfo = remember {
        runCatching {
            val pm = context.packageManager
            pm.getPackageInfo(context.packageName, android.content.pm.PackageManager.PackageInfoFlags.of(0))
        }.getOrNull()
    }
    val version = pkgInfo?.versionName ?: "—"
    val code = pkgInfo?.longVersionCode ?: 0L
    val icon = remember { runCatching { context.packageManager.getApplicationIcon(context.packageName).toBitmap(192, 192).asImageBitmap() }.getOrNull() }
    val debug = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    val ksu = when (privApp) {
        PrivAppInstaller.State.ACTIVE -> stringResource(R.string.privapp_active)
        PrivAppInstaller.State.PENDING_REBOOT -> stringResource(R.string.privapp_pending_reboot)
        PrivAppInstaller.State.PENDING_REMOVAL -> stringResource(R.string.privapp_pending_removal)
        PrivAppInstaller.State.NOT_INSTALLED -> stringResource(R.string.privapp_inactive)
        null -> if (state.privilege == PrivilegeLevel.PRIV_APP) stringResource(R.string.privapp_active) else stringResource(R.string.privapp_inactive)
    }
    fun open(url: String) = runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) Image(icon, null, Modifier.size(64.dp).clip(RoundedCornerShape(16.dp)))
                Spacer(Modifier.width(16.dp))
                Column {
                    Text("CellScope", style = MaterialTheme.typography.headlineSmall, color = Palette.text)
                    Text(
                        "$version ($code) · ${stringResource(if (debug) R.string.build_debug else R.string.build_release)}",
                        fontFamily = Mono, fontSize = 13.sp, color = Palette.textDim,
                    )
                    Text(AppInfo.COPYRIGHT, style = MaterialTheme.typography.bodySmall, color = Palette.textDim)
                }
            }
        }
        item {
            Panel {
                KeyValueRow(stringResource(R.string.about_privilege), stringResource(state.privilege.labelRes))
                KeyValueRow(
                    stringResource(R.string.about_permissions),
                    stringResource(if (state.permissionsGranted) R.string.status_perm_ok else R.string.status_perm_missing),
                )
                KeyValueRow(stringResource(R.string.about_ksu), ksu)
                KeyValueRow("Android", "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                KeyValueRow(stringResource(R.string.about_device), "${Build.MANUFACTURER} ${Build.MODEL}")
            }
        }
        item {
            UpdatePanel(update, updateAuto, privApp == PrivAppInstaller.State.ACTIVE, onCheckUpdate, onUpdateAuto, ::open,
                install, canInstall, silent = state.root.available == true, onInstall, onDismissInstall)
        }
        item {
            Panel(padding = 0.dp) {
                LinkRow(stringResource(R.string.about_author), AppInfo.AUTHOR, external = true) { open(AppInfo.AUTHOR_URL) }
                HorizontalDivider(color = Palette.outline)
                LinkRow(stringResource(R.string.about_source), AppInfo.SOURCE_URL.removePrefix("https://"), external = true) { open(AppInfo.SOURCE_URL) }
                HorizontalDivider(color = Palette.outline)
                LinkRow(stringResource(R.string.about_license), AppInfo.LICENSE) { onOpen("license") }
                HorizontalDivider(color = Palette.outline)
                LinkRow(stringResource(R.string.about_oss), null) { onOpen("oss") }
            }
        }
    }
}

@Composable
private fun UpdatePanel(
    update: UpdateState, auto: Boolean, privApp: Boolean,
    onCheck: () -> Unit, onAuto: (Boolean) -> Unit, open: (String) -> Unit,
    install: InstallState, canInstall: Boolean, silent: Boolean,
    onInstall: () -> Unit, onDismissInstall: () -> Unit,
) {
    val time = remember { SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault()) }
    var confirm by remember { mutableStateOf(false) }
    val installing = install is InstallState.Downloading || install == InstallState.Verifying ||
        install == InstallState.Installing || install is InstallState.NeedsConfirmation
    val checkedAt = when (update) {
        is UpdateState.UpToDate -> update.checkedAt
        is UpdateState.Available -> update.checkedAt
        is UpdateState.Failed -> update.checkedAt
        else -> null
    }
    Panel(title = stringResource(R.string.update_title)) {
        Text(
            when (update) {
                UpdateState.Unknown -> stringResource(R.string.update_unknown)
                UpdateState.Checking -> stringResource(R.string.update_checking)
                is UpdateState.UpToDate -> stringResource(R.string.update_up_to_date, update.latest.version)
                is UpdateState.Available -> stringResource(R.string.update_available, update.latest.version)
                is UpdateState.Failed -> stringResource(R.string.update_failed, update.message)
            },
            style = MaterialTheme.typography.bodyLarge,
            color = when (update) {
                is UpdateState.Available -> Palette.accent
                is UpdateState.Failed -> Palette.poor
                else -> Palette.text
            },
        )
        checkedAt?.takeIf { it > 0 }?.let {
            Text(stringResource(R.string.update_checked_at, time.format(Date(it))), fontSize = 12.sp, color = Palette.textDim)
        }
        val notes = (update as? UpdateState.Available)?.latest?.notes?.let { Releases.noteLines(it) }.orEmpty()
        if (notes.isNotEmpty()) {
            var expanded by remember { mutableStateOf(false) }
            Column(Modifier.padding(top = 8.dp)) {
                Text(stringResource(R.string.update_notes), style = MaterialTheme.typography.labelMedium, color = Palette.textDim)
                ReleaseNotes(if (expanded) notes else notes.take(NOTES_PREVIEW))
                if (notes.size > NOTES_PREVIEW) {
                    TextButton(onClick = { expanded = !expanded }, contentPadding = PaddingValues(0.dp)) {
                        Text(stringResource(if (expanded) R.string.update_notes_less else R.string.update_notes_more))
                    }
                }
            }
        }
        if (update is UpdateState.Available && !canInstall) {
            Text(stringResource(R.string.update_debug_hint), fontSize = 12.sp, color = Palette.textDim, modifier = Modifier.padding(top = 4.dp))
        }
        when (install) {
            is InstallState.Downloading -> {
                Text(stringResource(R.string.update_downloading), fontSize = 12.sp, color = Palette.text, modifier = Modifier.padding(top = 6.dp))
                val f = install.fraction
                if (f == null) androidx.compose.material3.LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 4.dp))
                else androidx.compose.material3.LinearProgressIndicator(progress = { f }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
            }
            InstallState.Verifying -> Text(stringResource(R.string.update_verifying), fontSize = 12.sp, color = Palette.text, modifier = Modifier.padding(top = 6.dp))
            InstallState.Installing, is InstallState.NeedsConfirmation ->
                Text(stringResource(if (silent) R.string.update_installing_root else R.string.update_installing), fontSize = 12.sp, color = Palette.text, modifier = Modifier.padding(top = 6.dp))
            is InstallState.Failed -> Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.update_install_failed, install.message), fontSize = 12.sp, color = Palette.poor, modifier = Modifier.weight(1f))
                TextButton(onClick = onDismissInstall) { Text(stringResource(R.string.close)) }
            }
            InstallState.Idle -> {}
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (update is UpdateState.Available && canInstall && update.latest.apkUrl != null) {
                Button(onClick = { confirm = true }, enabled = !installing) { Text(stringResource(R.string.update_install)) }
            }
            OutlinedButton(onClick = onCheck, enabled = update != UpdateState.Checking && !installing) { Text(stringResource(R.string.update_check)) }
        }
        if (update is UpdateState.Available) {
            TextButton(onClick = { open(update.latest.url) }, contentPadding = PaddingValues(0.dp)) { Text(stringResource(R.string.update_open)) }
        }
        if (confirm && update is UpdateState.Available) AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text(stringResource(R.string.update_install_title, update.latest.version)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(stringResource(when {
                        silent && privApp -> R.string.update_install_body_privapp
                        silent -> R.string.update_install_body_root
                        else -> R.string.update_install_body
                    }))
                    if (notes.isNotEmpty()) {
                        Text(stringResource(R.string.update_notes), style = MaterialTheme.typography.labelMedium,
                            color = Palette.textDim, modifier = Modifier.padding(top = 12.dp))
                        ReleaseNotes(notes)
                    }
                }
            },
            confirmButton = { Button(onClick = { confirm = false; onInstall() }) { Text(stringResource(R.string.update_install)) } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text(stringResource(R.string.cancel)) } },
        )
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.update_auto), style = MaterialTheme.typography.bodyMedium, color = Palette.text)
                Text(stringResource(R.string.update_auto_desc), fontSize = 12.sp, color = Palette.textDim)
            }
            Switch(checked = auto, onCheckedChange = onAuto)
        }
    }
}

private const val NOTES_PREVIEW = 6

@Composable
private fun ReleaseNotes(lines: List<NoteLine>) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        lines.forEach { line ->
            if (line.heading) Text(line.text, style = MaterialTheme.typography.titleSmall, color = Palette.text, modifier = Modifier.padding(top = 4.dp))
            else Text("• ${line.text}", fontSize = 13.sp, color = Palette.text)
        }
    }
}

@Composable
private fun LinkRow(title: String, value: String?, external: Boolean = false, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = value?.let { { Text(it, fontFamily = Mono, fontSize = 12.sp, color = Palette.textDim) } },
        trailingContent = {
            Icon(if (external) Icons.AutoMirrored.Outlined.OpenInNew else Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = Palette.textDim)
        },
        modifier = Modifier.clickable(onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

/** Plain-text asset rendered as-is inside a card; the file is the legal record, so no markdown styling. */
@Composable
private fun AssetTextPane(name: String) {
    val context = LocalContext.current
    val text = remember(name) { runCatching { context.assets.open(name).bufferedReader().readText() }.getOrElse { "—" } }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Panel { Text(text, fontFamily = Mono, fontSize = 12.sp, color = Palette.text) }
    }
}

private data class Notice(val name: String, val author: String, val license: String, val url: String)

/** Keep in sync with THIRD_PARTY.md at the repo root — that file is the record, this list is the UI. */
private val ossNotices = listOf(
    Notice("Kotlin", "JetBrains s.r.o.", "Apache-2.0", "https://github.com/JetBrains/kotlin"),
    Notice("AndroidX core · activity · lifecycle", "The Android Open Source Project", "Apache-2.0", "https://android.googlesource.com/platform/frameworks/support"),
    Notice("Jetpack Compose · Material 3", "The Android Open Source Project", "Apache-2.0", "https://developer.android.com/jetpack/compose"),
    Notice("Material Symbols", "Google LLC", "Apache-2.0", "https://github.com/google/material-design-icons"),
    Notice("OkHttp / Okio", "Square, Inc.", "Apache-2.0", "https://github.com/square/okhttp"),
)

@Composable
private fun OssPane() {
    val context = LocalContext.current
    fun open(url: String) = runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Column {
                Text(stringResource(R.string.oss_map), style = MaterialTheme.typography.labelLarge, color = Palette.accent, modifier = Modifier.padding(start = 16.dp, bottom = 6.dp))
                Panel(padding = 0.dp) {
                    ListItem(
                        headlineContent = { Text("OpenStreetMap") },
                        supportingContent = { Text("© OpenStreetMap contributors · ODbL 1.0", fontFamily = Mono, fontSize = 12.sp, color = Palette.textDim) },
                        trailingContent = { Icon(Icons.AutoMirrored.Outlined.OpenInNew, null, tint = Palette.textDim) },
                        modifier = Modifier.clickable { open("https://www.openstreetmap.org/copyright") },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                    HorizontalDivider(color = Palette.outline)
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.oss_tile_policy)) },
                        supportingContent = { Text("operations.osmfoundation.org/policies/tiles", fontFamily = Mono, fontSize = 12.sp, color = Palette.textDim) },
                        trailingContent = { Icon(Icons.AutoMirrored.Outlined.OpenInNew, null, tint = Palette.textDim) },
                        modifier = Modifier.clickable { open("https://operations.osmfoundation.org/policies/tiles/") },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                }
            }
        }
        item {
            Column {
                Text(stringResource(R.string.speed_test), style = MaterialTheme.typography.labelLarge, color = Palette.accent, modifier = Modifier.padding(start = 16.dp, bottom = 6.dp))
                Panel(padding = 0.dp) {
                    ListItem(
                        headlineContent = { Text("Measurement Lab (NDT7)") },
                        supportingContent = { Text("measurementlab.net", fontFamily = Mono, fontSize = 12.sp, color = Palette.textDim) },
                        trailingContent = { Icon(Icons.AutoMirrored.Outlined.OpenInNew, null, tint = Palette.textDim) },
                        modifier = Modifier.clickable { open("https://www.measurementlab.net/") },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                }
            }
        }
        item {
            Column {
                Text(stringResource(R.string.oss_libraries), style = MaterialTheme.typography.labelLarge, color = Palette.accent, modifier = Modifier.padding(start = 16.dp, bottom = 6.dp))
                Panel(padding = 0.dp) {
                    ossNotices.forEachIndexed { i, n ->
                        ListItem(
                            headlineContent = { Text(n.name) },
                            supportingContent = { Text(n.author, style = MaterialTheme.typography.bodySmall, color = Palette.textDim) },
                            trailingContent = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Tag(n.license)
                                    Spacer(Modifier.width(8.dp))
                                    Icon(Icons.AutoMirrored.Outlined.OpenInNew, null, tint = Palette.textDim)
                                }
                            },
                            modifier = Modifier.clickable { open(n.url) },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        )
                        if (i != ossNotices.lastIndex) HorizontalDivider(color = Palette.outline)
                    }
                }
            }
        }
        item {
            Panel(padding = 0.dp) {
                ListItem(
                    headlineContent = { Text("Apache License 2.0") },
                    supportingContent = { Text("apache.org/licenses/LICENSE-2.0", fontFamily = Mono, fontSize = 12.sp, color = Palette.textDim) },
                    trailingContent = { Icon(Icons.AutoMirrored.Outlined.OpenInNew, null, tint = Palette.textDim) },
                    modifier = Modifier.clickable { open("https://www.apache.org/licenses/LICENSE-2.0") },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
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
