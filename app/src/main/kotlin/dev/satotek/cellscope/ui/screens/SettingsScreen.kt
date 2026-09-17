package dev.satotek.cellscope.ui.screens

import androidx.compose.foundation.layout.Arrangement
import dev.satotek.cellscope.R
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.LoadingIndicator
import dev.satotek.cellscope.data.telephony.RatLock
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.TextButton
import dev.satotek.cellscope.data.root.PrivAppInstaller
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import android.app.LocaleManager
import android.content.Intent
import android.os.LocaleList
import android.net.Uri
import android.provider.Settings
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.satotek.cellscope.MainViewModel
import dev.satotek.cellscope.data.ai.AiPrefs
import dev.satotek.cellscope.data.ai.AiProvider
import dev.satotek.cellscope.data.model.PrivilegeLevel
import dev.satotek.cellscope.data.model.Snapshot
import dev.satotek.cellscope.ui.components.InfoButton
import dev.satotek.cellscope.ui.components.Panel
import dev.satotek.cellscope.ui.components.Tag
import dev.satotek.cellscope.ui.theme.Mono
import dev.satotek.cellscope.ui.theme.Palette

/**
 * Settings in the Pixel "grouped list" idiom: one card per group, one terse row per setting,
 * explanations tucked behind ⓘ so the page reads as a list of switches rather than prose.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsScreen(vm: MainViewModel, s: Snapshot, showTitle: Boolean = true) {
    val interval by vm.pollIntervalMs.collectAsStateWithLifecycle()
    val host by vm.pingHost.collectAsStateWithLifecycle()
    val gnbBits by vm.gnbBits.collectAsStateWithLifecycle()
    val ratLock by vm.ratLock.collectAsStateWithLifecycle()
    val ratLockRaw by vm.ratLockRaw.collectAsStateWithLifecycle()
    val ratLockError by vm.ratLockError.collectAsStateWithLifecycle()
    val wifiOn by vm.wifiOn.collectAsStateWithLifecycle()
    val airplaneOn by vm.airplaneOn.collectAsStateWithLifecycle()
    val reconnecting by vm.reconnecting.collectAsStateWithLifecycle()
    val ratModes = listOf(
        RatLock.Mode.AUTO to R.string.rat_auto, RatLock.Mode.NR_LTE to R.string.rat_nr_lte, RatLock.Mode.NR_ONLY to R.string.rat_nr,
        RatLock.Mode.LTE_ONLY to R.string.rat_lte, RatLock.Mode.LTE_WCDMA to R.string.rat_lte_wcdma, RatLock.Mode.WCDMA_ONLY to R.string.rat_wcdma,
    )
    LaunchedEffect(s.privilege, s.root.available) { vm.refreshRatLock(); vm.refreshConnectivity() }
    val gnbChoices = listOf(22, 24, 26, 28, 32)
    val pingOn by vm.pingEnabled.collectAsStateWithLifecycle()
    val pipAuto by vm.pipAuto.collectAsStateWithLifecycle()
    val overlayOn by vm.overlayEnabled.collectAsStateWithLifecycle()
    val recording by vm.recordingFlow.collectAsStateWithLifecycle()
    val privApp by vm.privApp.collectAsStateWithLifecycle()
    val privAppError by vm.privAppError.collectAsStateWithLifecycle()
    var confirm by remember { mutableStateOf<String?>(null) }   // "install" | "remove" | "reboot"

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (showTitle) item { Text(stringResource(R.string.more_setup), style = MaterialTheme.typography.headlineMedium, color = Palette.text) }

        // ---- status strip ------------------------------------------------------------------
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Tag(stringResource(s.privilege.labelRes), when (s.privilege) { PrivilegeLevel.PRIV_APP -> Palette.nr; PrivilegeLevel.ROOT -> Palette.lte; else -> Palette.textDim })
                Tag(if (s.permissionsGranted) stringResource(R.string.status_perm_ok) else stringResource(R.string.status_perm_missing), if (s.permissionsGranted) Palette.nr else Palette.poor)
                Tag(when (s.root.available) { true -> "su"; false -> "su ✗"; null -> "su ?" }, if (s.root.available == true) Palette.nr else Palette.textDim)
                s.location?.let { Text(String.format("%.4f, %.4f", it.first, it.second), fontFamily = Mono, fontSize = 11.sp, color = Palette.textDim) }
            }
        }

        // ---- measurement ---------------------------------------------------------------------
        item {
            Group(stringResource(R.string.group_measure)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.poll_interval), style = MaterialTheme.typography.bodyLarge, color = Palette.text)
                        Slider(value = interval.toFloat(), onValueChange = { vm.setPollInterval(it.toLong()) }, valueRange = 500f..5000f, steps = 8)
                    }
                    Spacer(Modifier.width(12.dp))
                    Text("${interval} ms", fontFamily = Mono, color = Palette.text)
                    InfoButton(stringResource(R.string.poll_interval), stringResource(R.string.poll_interval_p1), stringResource(R.string.poll_interval_p2))
                }
                HorizontalDivider(color = Palette.outline)
                ListItem(
                    headlineContent = { Text(stringResource(R.string.ping)) },
                    supportingContent = {
                        OutlinedTextField(value = host, onValueChange = { vm.setPingHost(it) }, singleLine = true, textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                            modifier = Modifier.fillMaxWidth().padding(top = 6.dp), enabled = pingOn)
                    },
                    trailingContent = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            InfoButton(stringResource(R.string.ping), stringResource(R.string.ping_p1), stringResource(R.string.ping_p2))
                            Switch(checked = pingOn, onCheckedChange = { vm.setPingEnabled(it) })
                        }
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
                HorizontalDivider(color = Palette.outline)
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.gnb_bits), style = MaterialTheme.typography.bodyLarge, color = Palette.text, modifier = Modifier.weight(1f))
                        Text("$gnbBits bit", fontFamily = Mono, color = Palette.text)
                        InfoButton(stringResource(R.string.gnb_bits), stringResource(R.string.gnb_bits_p1), stringResource(R.string.gnb_bits_p2))
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
                        gnbChoices.forEachIndexed { i, b ->
                            ToggleButton(
                                checked = gnbBits == b, onCheckedChange = { vm.setGnbBits(b) }, modifier = Modifier.weight(1f),
                                shapes = when (i) {
                                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                                    gnbChoices.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                                },
                            ) { Text("$b", fontFamily = Mono, fontSize = 12.sp) }
                        }
                    }
                }
            }
        }

        item {
            val context = LocalContext.current
            Group(stringResource(R.string.group_display)) {
                val themeMode by vm.themeMode.collectAsStateWithLifecycle()
                SegmentRow(
                    title = stringResource(R.string.theme),
                    choices = listOf("system" to stringResource(R.string.theme_system), "light" to stringResource(R.string.theme_light), "dark" to stringResource(R.string.theme_dark)),
                    selected = themeMode,
                    onSelect = { vm.setThemeMode(it) },
                )
                HorizontalDivider(color = Palette.outline)
                // Per-app locale (API 33+): setting it recreates the activity, so no state of our own.
                val localeManager = context.getSystemService(LocaleManager::class.java)
                val currentLang = localeManager.applicationLocales.let { if (it.isEmpty) "" else it[0].language }
                SegmentRow(
                    title = stringResource(R.string.language),
                    choices = listOf("" to stringResource(R.string.lang_system), "ja" to "日本語", "en" to "English"),
                    selected = currentLang,
                    onSelect = { localeManager.applicationLocales = if (it.isEmpty()) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(it) },
                )
                HorizontalDivider(color = Palette.outline)
                ListItem(
                    headlineContent = { Text(stringResource(R.string.pip_auto)) },
                    trailingContent = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            InfoButton(stringResource(R.string.pip_auto), stringResource(R.string.pip_auto_p1), stringResource(R.string.pip_auto_p2))
                            Switch(checked = pipAuto && !overlayOn, onCheckedChange = { vm.setPipAuto(it) }, enabled = !overlayOn)
                        }
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
                HorizontalDivider(color = Palette.outline)
                ListItem(
                    headlineContent = { Text(stringResource(R.string.overlay)) },
                    trailingContent = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            InfoButton(stringResource(R.string.overlay), stringResource(R.string.overlay_p1), stringResource(R.string.overlay_p2))
                            Switch(
                                checked = overlayOn,
                                onCheckedChange = { on ->
                                    if (on && !Settings.canDrawOverlays(context)) {
                                        vm.setOverlayEnabled(true)
                                        context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}")))
                                    } else vm.setOverlayEnabled(on)
                                },
                            )
                        }
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
            }
        }

        // ---- radio: RAT lock (priv-app) + connectivity toggles (root) ------------------------------
        item {
            val isPriv = s.privilege == PrivilegeLevel.PRIV_APP
            val hasRoot = s.root.available == true
            Group(stringResource(R.string.group_radio)) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.rat_lock), style = MaterialTheme.typography.bodyLarge, color = Palette.text)
                            val sub = when {
                                !isPriv -> stringResource(R.string.rat_lock_needs_privapp)
                                ratLockError != null -> ratLockError!!
                                ratLock == null && ratLockRaw != null -> stringResource(R.string.rat_custom) + " · 0x" + java.lang.Long.toHexString(ratLockRaw!!)
                                else -> null
                            }
                            sub?.let { Text(it, color = if (ratLockError != null) Palette.poor else Palette.textDim, fontFamily = Mono, fontSize = 11.sp) }
                        }
                        InfoButton(stringResource(R.string.rat_lock), stringResource(R.string.rat_lock_p1), stringResource(R.string.rat_lock_p2))
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
                        ratModes.forEachIndexed { i, (mode, label) ->
                            ToggleButton(
                                checked = ratLock == mode, onCheckedChange = { vm.setRatLock(mode) }, enabled = isPriv, modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 2.dp, vertical = 8.dp),
                                shapes = when (i) {
                                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                                    ratModes.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                                },
                            ) { Text(stringResource(label), fontSize = 11.sp, maxLines = 1, softWrap = false) }
                        }
                    }
                }
                HorizontalDivider(color = Palette.outline)
                ListItem(
                    headlineContent = { Text(stringResource(R.string.wifi)) },
                    supportingContent = if (!hasRoot) ({ Text(stringResource(R.string.needs_root), color = Palette.textDim, fontSize = 12.sp) }) else null,
                    trailingContent = { Switch(checked = wifiOn, onCheckedChange = { vm.setWifi(it) }, enabled = hasRoot) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
                HorizontalDivider(color = Palette.outline)
                ListItem(
                    headlineContent = { Text(stringResource(R.string.airplane)) },
                    trailingContent = { Switch(checked = airplaneOn, onCheckedChange = { vm.setAirplane(it) }, enabled = hasRoot) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
                HorizontalDivider(color = Palette.outline)
                ListItem(
                    headlineContent = { Text(stringResource(R.string.reconnect)) },
                    trailingContent = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            InfoButton(stringResource(R.string.reconnect), stringResource(R.string.reconnect_p1))
                            if (reconnecting) LoadingIndicator(Modifier.height(32.dp))
                            else FilledTonalButton(onClick = { vm.reconnect() }, shapes = ButtonDefaults.shapes(), contentPadding = ButtonDefaults.ExtraSmallContentPadding, enabled = hasRoot) { Text(stringResource(R.string.reconnect)) }
                        }
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
            }
        }

        // ---- privilege ---------------------------------------------------------------------------
        item {
            Group(stringResource(R.string.group_privilege)) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.root_mode)) },
                    
                    trailingContent = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            InfoButton(stringResource(R.string.root_mode), stringResource(R.string.root_mode_p1), stringResource(R.string.root_mode_p2), stringResource(R.string.root_mode_p3))
                            Switch(checked = s.root.enabled, onCheckedChange = { vm.setRootEnabled(it) }, enabled = s.root.available == true)
                        }
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
                HorizontalDivider(color = Palette.outline)
                ListItem(
                    headlineContent = { Text(stringResource(R.string.privapp_mode)) },
                    supportingContent = {
                        Column {
                            Text(when (privApp) {
                                PrivAppInstaller.State.ACTIVE -> stringResource(R.string.privapp_active)
                                PrivAppInstaller.State.PENDING_REBOOT -> stringResource(R.string.privapp_pending_reboot)
                                PrivAppInstaller.State.PENDING_REMOVAL -> stringResource(R.string.privapp_pending_removal)
                                PrivAppInstaller.State.NOT_INSTALLED -> stringResource(R.string.privapp_inactive)
                                null -> if (s.privilege == PrivilegeLevel.PRIV_APP) stringResource(R.string.privapp_active) else stringResource(R.string.privapp_inactive)
                            }, color = if (privApp == PrivAppInstaller.State.ACTIVE) Palette.nr else Palette.textDim)
                            privAppError?.let { Text(it, color = Palette.poor, fontFamily = Mono, fontSize = 11.sp) }
                        }
                    },
                    trailingContent = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            InfoButton(stringResource(R.string.privapp_mode), stringResource(R.string.privapp_mode_p1), stringResource(R.string.privapp_mode_p2), stringResource(R.string.privapp_mode_p3))
                            when (privApp) {
                                PrivAppInstaller.State.NOT_INSTALLED ->
                                    FilledTonalButton(onClick = { confirm = "install" }, shapes = ButtonDefaults.shapes(), contentPadding = ButtonDefaults.ExtraSmallContentPadding, enabled = s.root.available == true) { Text(stringResource(R.string.privapp_enable)) }
                                PrivAppInstaller.State.PENDING_REBOOT -> {
                                    FilledTonalButton(onClick = { confirm = "install" }, shapes = ButtonDefaults.shapes(), contentPadding = ButtonDefaults.ExtraSmallContentPadding) { Text(stringResource(R.string.privapp_regenerate)) }
                                    Spacer(Modifier.width(6.dp))
                                    Button(onClick = { confirm = "reboot" }, shapes = ButtonDefaults.shapes(), contentPadding = ButtonDefaults.ExtraSmallContentPadding) { Text(stringResource(R.string.privapp_reboot)) }
                                }
                                PrivAppInstaller.State.PENDING_REMOVAL ->
                                    Button(onClick = { confirm = "reboot" }, shapes = ButtonDefaults.shapes(), contentPadding = ButtonDefaults.ExtraSmallContentPadding) { Text(stringResource(R.string.privapp_reboot)) }
                                PrivAppInstaller.State.ACTIVE -> {
                                    FilledTonalButton(onClick = { confirm = "install" }, shapes = ButtonDefaults.shapes(), contentPadding = ButtonDefaults.ExtraSmallContentPadding) { Text(stringResource(R.string.privapp_regenerate)) }
                                    Spacer(Modifier.width(6.dp))
                                    FilledTonalButton(onClick = { confirm = "remove" }, shapes = ButtonDefaults.shapes(), contentPadding = ButtonDefaults.ExtraSmallContentPadding) { Text(stringResource(R.string.privapp_remove)) }
                                }
                                null -> {}
                            }
                        }
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
                HorizontalDivider(color = Palette.outline)
                ListItem(
                    headlineContent = { Text(stringResource(R.string.check_su)) },
                    trailingContent = {
                        FilledTonalButton(onClick = { vm.probeRoot() }, shapes = ButtonDefaults.shapes(), contentPadding = ButtonDefaults.ExtraSmallContentPadding) {
                            Icon(Icons.Outlined.Refresh, null, Modifier.height(16.dp))
                        }
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
            }
        }

        // ---- AI ------------------------------------------------------------------------------------
        item {
            val context = LocalContext.current
            var ai by remember { mutableStateOf(AiPrefs.load(context)) }
            val providers = listOf(
                AiProvider.OPENAI to R.string.ai_openai,
                AiProvider.GEMINI to R.string.ai_gemini,
                AiProvider.ANTHROPIC to R.string.ai_anthropic,
            )
            Group(stringResource(R.string.group_ai)) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
                    Text(stringResource(R.string.ai_provider), style = MaterialTheme.typography.bodyLarge, color = Palette.text)
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
                        providers.forEachIndexed { i, (p, label) ->
                            ToggleButton(
                                checked = ai.provider == p,
                                onCheckedChange = {
                                    val next = if (ai.model.isBlank() || ai.model == ai.provider.defaultModel) {
                                        ai.copy(provider = p, model = "")
                                    } else ai.copy(provider = p)
                                    ai = next
                                    AiPrefs.save(context, next)
                                },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 2.dp, vertical = 8.dp),
                                shapes = when (i) {
                                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                                    providers.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                                },
                            ) { Text(stringResource(label), fontSize = 11.sp, maxLines = 1, softWrap = false) }
                        }
                    }
                }
                HorizontalDivider(color = Palette.outline)
                ListItem(
                    headlineContent = { Text(stringResource(R.string.ai_api_key)) },
                    supportingContent = {
                        OutlinedTextField(
                            value = ai.apiKey,
                            onValueChange = { v -> val next = ai.copy(apiKey = v); ai = next; AiPrefs.save(context, next) },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                        )
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
                HorizontalDivider(color = Palette.outline)
                ListItem(
                    headlineContent = { Text(stringResource(R.string.ai_model)) },
                    supportingContent = {
                        OutlinedTextField(
                            value = ai.model,
                            onValueChange = { v -> val next = ai.copy(model = v); ai = next; AiPrefs.save(context, next) },
                            singleLine = true,
                            placeholder = { Text(ai.provider.defaultModel, fontFamily = Mono, fontSize = 13.sp, color = Palette.textDim) },
                            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                        )
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
            }
        }

        // ---- recording -----------------------------------------------------------------------------
        item {
            Group(stringResource(R.string.group_record)) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.csv_log)) },
                    supportingContent = { Text(if (recording) vm.logFile?.name ?: stringResource(R.string.csv_recording) else stringResource(R.string.csv_stopped), color = if (recording) Palette.nr else Palette.textDim, fontFamily = Mono, fontSize = 12.sp) },
                    trailingContent = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            InfoButton(stringResource(R.string.csv_log), stringResource(R.string.csv_log_p1), stringResource(R.string.csv_log_p2))
                            Switch(checked = recording, onCheckedChange = { vm.toggleRecording() })
                        }
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
                HorizontalDivider(color = Palette.outline)
                ListItem(
                    headlineContent = { Text(stringResource(R.string.clear_history)) },
                    supportingContent = { Text("${s.history.size} samples · ${s.events.size} events", color = Palette.textDim, fontFamily = Mono, fontSize = 12.sp) },
                    trailingContent = { FilledTonalButton(onClick = { confirm = "clear" }, shapes = ButtonDefaults.shapes(), contentPadding = ButtonDefaults.ExtraSmallContentPadding) { Text(stringResource(R.string.clear)) } },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
            }
        }
    }
    confirm?.let { k ->
        ConfirmDialog(k, onDismiss = { confirm = null }) {
            when (k) { "install" -> vm.installPrivApp(); "remove" -> vm.uninstallPrivApp(); "clear" -> vm.clearHistory(); else -> vm.rebootDevice() }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ConfirmDialog(kind: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    val (title, body, action) = when (kind) {
        "install" -> Triple(stringResource(R.string.confirm_install_title), stringResource(R.string.confirm_install_body), stringResource(R.string.privapp_enable))
        "remove" -> Triple(stringResource(R.string.confirm_remove_title), stringResource(R.string.confirm_remove_body), stringResource(R.string.privapp_remove))
        "clear" -> Triple(stringResource(R.string.clear_history), stringResource(R.string.confirm_clear_body), stringResource(R.string.clear))
        else -> Triple(stringResource(R.string.privapp_reboot), stringResource(R.string.confirm_reboot_body), stringResource(R.string.privapp_reboot))
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) }, text = { Text(body) },
        confirmButton = { Button(onClick = { onConfirm(); onDismiss() }, shapes = ButtonDefaults.shapes()) { Text(action) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** Title on the left, a connected toggle group on the right — same look as the gNB-bits picker. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SegmentRow(title: String, choices: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge, color = Palette.text)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
            choices.forEachIndexed { i, (id, label) ->
                ToggleButton(
                    checked = selected == id, onCheckedChange = { if (selected != id) onSelect(id) }, modifier = Modifier.weight(1f),
                    shapes = when (i) {
                        0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                        choices.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                        else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                    },
                ) { Text(label, fontSize = 12.sp, maxLines = 1) }
            }
        }
    }
}

@Composable
private fun Group(title: String, content: @Composable () -> Unit) {
    Column {
        Text(title, style = MaterialTheme.typography.labelLarge, color = Palette.accent, modifier = Modifier.padding(start = 16.dp, bottom = 6.dp))
        Panel(padding = 0.dp) { content() }
    }
}
