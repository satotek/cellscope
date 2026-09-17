package dev.satotek.cellscope.ui.components

import androidx.compose.foundation.layout.Column
import dev.satotek.cellscope.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.satotek.cellscope.ui.theme.Palette

/** ⓘ button that opens a bottom sheet with a title and explanatory paragraphs – keeps list rows terse. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InfoButton(title: String, vararg paragraphs: String) {
    var open by remember { mutableStateOf(false) }
    IconButton(onClick = { open = true }) { Icon(Icons.Outlined.Info, stringResource(R.string.info), tint = Palette.textDim) }
    if (open) {
        ModalBottomSheet(onDismissRequest = { open = false }, sheetState = rememberModalBottomSheetState()) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge, color = Palette.text)
                Spacer(Modifier.height(12.dp))
                paragraphs.forEach { Text(it, style = MaterialTheme.typography.bodyMedium, color = Palette.textDim, modifier = Modifier.padding(bottom = 10.dp)) }
            }
        }
    }
}
