package dev.satotek.cellscope.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.satotek.cellscope.R
import dev.satotek.cellscope.data.ai.AiClient
import dev.satotek.cellscope.data.ai.AiPrefs
import dev.satotek.cellscope.data.ai.AiShare
import dev.satotek.cellscope.data.ai.MeasurementDigest
import dev.satotek.cellscope.data.model.CellEvent
import dev.satotek.cellscope.data.model.SignalSample
import dev.satotek.cellscope.ui.theme.Mono
import dev.satotek.cellscope.ui.theme.Palette
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun AiDigestButton(samples: List<SignalSample>, events: List<CellEvent>, range: LongRange) {
    var open by remember { mutableStateOf(false) }
    IconButton(onClick = { open = true }) {
        Icon(Icons.Outlined.AutoAwesome, stringResource(R.string.ai_ask), tint = Palette.text)
    }
    if (open) {
        AiDigestSheet(samples, events, range, onDismiss = { open = false })
    }
}

private sealed class AiPhase {
    data object Preview : AiPhase()
    data object Loading : AiPhase()
    data class Answer(val text: String) : AiPhase()
    data class Error(val text: String) : AiPhase()
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AiDigestSheet(
    samples: List<SignalSample>,
    events: List<CellEvent>,
    range: LongRange,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val locale = remember { Locale.getDefault() }
    val digest = remember(samples, events, range, locale) {
        MeasurementDigest.build(context, samples, events, range, locale)
    }
    val hasKey = remember { AiPrefs.hasKey(context) }
    var phase by remember { mutableStateOf<AiPhase>(AiPhase.Preview) }
    val scope = rememberCoroutineScope()
    val shown = when (val p = phase) {
        is AiPhase.Answer -> p.text
        is AiPhase.Error -> p.text
        else -> digest
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
            Text(stringResource(R.string.ai_digest_title), style = MaterialTheme.typography.titleLarge, color = Palette.text)
            Spacer(Modifier.height(12.dp))
            when (phase) {
                AiPhase.Loading -> Box(Modifier.fillMaxWidth().heightIn(min = 120.dp), contentAlignment = Alignment.Center) {
                    LoadingIndicator()
                }
                is AiPhase.Error -> Text(
                    shown,
                    fontFamily = Mono,
                    fontSize = 12.sp,
                    color = Palette.poor,
                    modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
                )
                else -> Text(
                    shown,
                    fontFamily = Mono,
                    fontSize = 12.sp,
                    color = Palette.text,
                    modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
                )
            }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = { AiShare.share(context, shown) },
                    shapes = ButtonDefaults.shapes(),
                    enabled = phase !is AiPhase.Loading,
                ) { Text(stringResource(R.string.share)) }
                FilledTonalButton(
                    onClick = { AiShare.copy(context, shown) },
                    shapes = ButtonDefaults.shapes(),
                    enabled = phase !is AiPhase.Loading,
                ) { Text(stringResource(R.string.copy)) }
                if (hasKey) {
                    Button(
                        onClick = {
                            phase = AiPhase.Loading
                            scope.launch {
                                val result = AiClient(context).ask(digest)
                                phase = result.fold(
                                    onSuccess = { AiPhase.Answer(it) },
                                    onFailure = { AiPhase.Error(it.message ?: "failed") },
                                )
                            }
                        },
                        shapes = ButtonDefaults.shapes(),
                        enabled = phase !is AiPhase.Loading,
                    ) { Text(stringResource(R.string.ai_ask)) }
                }
            }
        }
    }
}
