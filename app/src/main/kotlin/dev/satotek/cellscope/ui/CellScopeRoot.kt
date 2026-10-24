package dev.satotek.cellscope.ui

import androidx.compose.foundation.layout.Box
import dev.satotek.cellscope.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.launch
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.satotek.cellscope.MainViewModel
import dev.satotek.cellscope.SnapshotState
import dev.satotek.cellscope.data.snapshot.SnapshotWriter
import dev.satotek.cellscope.ui.screens.CellsScreen
import dev.satotek.cellscope.ui.screens.MoreScreen
import dev.satotek.cellscope.ui.screens.OverviewScreen
import dev.satotek.cellscope.ui.screens.PipHud
import dev.satotek.cellscope.ui.screens.SignalScreen
import dev.satotek.cellscope.ui.screens.CommunicationScreen
import dev.satotek.cellscope.ui.snapshot.SnapshotCaptureHost
import dev.satotek.cellscope.ui.theme.Palette

private enum class Tab(@param:StringRes val labelRes: Int, val icon: ImageVector) {
    OVERVIEW(R.string.tab_home, Icons.Filled.CellTower),
    CELLS(R.string.tab_cells, Icons.Filled.ViewList),
    SIGNAL(R.string.tab_signal, Icons.Filled.ShowChart),
    COMMUNICATION(R.string.tab_communication, Icons.Outlined.Speed),
    MORE(R.string.tab_more, Icons.Outlined.MoreHoriz),
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CellScopeRoot(
    vm: MainViewModel,
    onRequestPermissions: () -> Unit,
    permanentlyDenied: Boolean = false,
    onOpenSettings: () -> Unit = {},
    inPip: Boolean = false,
    onEnterPip: () -> Unit = {},
    onExit: () -> Unit = {},
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val recording by vm.recordingFlow.collectAsStateWithLifecycle()
    val snapshotState by vm.snapshotState.collectAsStateWithLifecycle()
    val snapshotRender by vm.snapshotRender.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    var switchHintShown by rememberSaveable { mutableStateOf(false) }
    val copyLabel = stringResource(R.string.snapshot_copy_image)

    val selectSub: (Int) -> Unit = { subId ->
        if (subId != state.sim?.subId) {
            vm.setSelectedSub(subId)
            val label = state.sims.firstOrNull { it.subId == subId }?.label.orEmpty()
            val message = resources.getString(if (switchHintShown) R.string.sim_switched else R.string.sim_switched_first, label)
            switchHintShown = true
            scope.launch {
                snackbarHostState.currentSnackbarData?.dismiss()
                snackbarHostState.showSnackbar(message)
            }
        }
    }

    // Probe root once at startup; harmless if su is absent.
    LaunchedEffect(Unit) { vm.probeRoot() }
    LaunchedEffect(snapshotState) {
        when (val st = snapshotState) {
            is SnapshotState.Done -> {
                val result = snackbarHostState.showSnackbar(st.file.name, copyLabel, withDismissAction = true, duration = SnackbarDuration.Long)
                if (result == SnackbarResult.ActionPerformed) {
                    val message = runCatching { SnapshotWriter.copyImage(context, st.file) }.fold(
                        { resources.getString(R.string.snapshot_copied) },
                        { resources.getString(R.string.snapshot_copy_failed) },
                    )
                    android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()
                }
                vm.ackSnapshotState()
            }
            is SnapshotState.Error -> {
                snackbarHostState.showSnackbar(st.msg)
                vm.ackSnapshotState()
            }
            else -> {}
        }
    }

    if (inPip) {
        val page by vm.hudPage.collectAsStateWithLifecycle()
        PipHud(state, page)
        return
    }

    val update by vm.update.collectAsStateWithLifecycle()
    // The installer's confirmation must come from a visible activity, so it is opened here rather
    // than from the broadcast that delivers it.
    val install by vm.install.collectAsStateWithLifecycle()
    val rootContext = androidx.compose.ui.platform.LocalContext.current
    androidx.compose.runtime.LaunchedEffect(install) {
        val pending = install as? dev.satotek.cellscope.data.update.InstallState.NeedsConfirmation ?: return@LaunchedEffect
        runCatching { rootContext.startActivity(pending.intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
        vm.installConfirmationShown()
    }
    val updateAvailable = update is dev.satotek.cellscope.data.update.UpdateState.Available
    Scaffold(
        containerColor = Palette.bg,
        floatingActionButton = {
            if (state.permissionsGranted && Tab.entries[tab] in listOf(Tab.OVERVIEW, Tab.CELLS, Tab.SIGNAL)) {
                dev.satotek.cellscope.ui.components.SimSelector(state.sims, state.sim?.subId, selectSub)
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            ShortNavigationBar {
                Tab.entries.forEachIndexed { i, t ->
                    val label = stringResource(t.labelRes)
                    ShortNavigationBarItem(selected = tab == i, onClick = { tab = i }, icon = {
                        // A new release is announced on More, so the tab carries the dot that leads there.
                        if (t == Tab.MORE && updateAvailable) {
                            androidx.compose.material3.BadgedBox(badge = { androidx.compose.material3.Badge() }) { Icon(t.icon, label) }
                        } else Icon(t.icon, label)
                    }, label = { Text(label) })
                }
            }
        }
    ) { pad ->
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().padding(pad)) {
                if (!state.permissionsGranted) {
                    Column(Modifier.align(Alignment.Center).padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(R.string.perm_title), style = MaterialTheme.typography.titleLarge, color = Palette.text, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(12.dp))
                        Text(
                            stringResource(R.string.perm_body),
                            style = MaterialTheme.typography.bodyMedium, color = Palette.textDim, textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(20.dp))
                        if (permanentlyDenied) {
                            Text(stringResource(R.string.perm_denied_forever), style = MaterialTheme.typography.bodySmall, color = Palette.poor, textAlign = TextAlign.Center)
                            Spacer(Modifier.height(12.dp))
                            Button(onClick = onOpenSettings, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.perm_open_settings)) }
                        } else {
                            Button(onClick = onRequestPermissions, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.perm_grant)) }
                        }
                    }
                } else when (Tab.entries[tab]) {
                    Tab.OVERVIEW -> OverviewScreen(
                        state,
                        capturing = snapshotState is SnapshotState.Capturing,
                        onShowAllCells = { tab = Tab.CELLS.ordinal },
                        onSelectSub = selectSub,
                        onEnterPip = onEnterPip,
                        onTakeSnapshot = { vm.takeSnapshot() },
                        recording = recording,
                        onToggleRecording = { vm.toggleRecording() },
                    )
                    Tab.CELLS -> CellsScreen(state)
                    Tab.SIGNAL -> SignalScreen(state)
                    Tab.COMMUNICATION -> CommunicationScreen(vm, state)
                    Tab.MORE -> MoreScreen(vm, state, onExit = onExit)
                }
            }
            snapshotRender?.let { req ->
                val mapImg = remember(req.map) { req.map?.asImageBitmap() }
                SnapshotCaptureHost(
                    snapshot = req.snapshot,
                    map = mapImg,
                    takenAt = req.takenAt,
                    onCaptured = { vm.onSnapshotCaptured(it.asAndroidBitmap()) },
                    onFailed = { vm.onSnapshotFailed(it) },
                )
            }
        }
    }
}
