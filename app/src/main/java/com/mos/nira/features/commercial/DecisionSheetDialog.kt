package com.mos.nira.features.commercial

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mos.nira.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Choose Buyer Quote (English) or Floor Job Sheet (Tamil), see the image, and share it. */
@Composable
fun DecisionSheetDialog(data: SheetData, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var floor by remember { mutableStateOf(false) }
    var sheet by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(data, floor) {
        sheet = null
        sheet = withContext(Dispatchers.Default) { runCatching { DecisionSheet.render(context, data, floor) }.getOrNull() }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.sheet_button)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                // One above the other, so long labels (Tamil) are never squeezed.
                Column {
                    FilterChip(selected = !floor, onClick = { floor = false }, label = { Text(stringResource(R.string.sheet_buyer_toggle), fontSize = 12.sp) })
                    FilterChip(selected = floor, onClick = { floor = true }, label = { Text(stringResource(R.string.sheet_floor_toggle), fontSize = 12.sp) })
                }
                val s = sheet
                if (s == null) {
                    Text(stringResource(R.string.sheet_making), fontSize = 13.sp)
                } else {
                    Image(
                        bitmap = s.asImageBitmap(),
                        contentDescription = stringResource(R.string.sheet_button),
                        contentScale = ContentScale.FillWidth,
                        modifier = Modifier.fillMaxWidth().heightIn(max = 4000.dp).aspectRatio(s.width.toFloat() / s.height)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(enabled = sheet != null, onClick = { sheet?.let { DecisionSheet.share(context, it, floor) } }) {
                Text(stringResource(R.string.sheet_share))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.preview_cancel)) } }
    )
}
