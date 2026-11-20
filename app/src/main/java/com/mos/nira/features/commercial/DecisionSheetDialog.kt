package com.mos.nira.features.commercial

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mos.nira.R
import com.mos.nira.core.ai.SheetTranslation
import com.mos.nira.features.ai.SheetTranslateState
import com.mos.nira.features.ai.SheetTranslationViewModel
import com.mos.nira.features.ai.activityViewModelStoreOwner
import com.mos.nira.features.ai.aiErrorText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Choose Buyer Quote (in the buyer's language) or Floor Job Sheet (Tamil), optionally translate
 * the rules-file texts with AI (each line checked), see the image, and share it.
 */
@Composable
fun DecisionSheetDialog(
    data: SheetData,
    onDismiss: () -> Unit,
    translator: SheetTranslationViewModel = viewModel(viewModelStoreOwner = activityViewModelStoreOwner())
) {
    val context = LocalContext.current
    var floor by remember { mutableStateOf(false) }
    var language by remember { mutableStateOf("en") }
    var sheet by remember { mutableStateOf<Bitmap?>(null) }
    val lang = if (floor) "ta" else language
    val texts = remember(data.card) { SheetTranslation.texts(data.card) }
    val tState by translator.state.collectAsState()
    val key = translator.key(texts, lang)
    // Translations are used only for the language they were made for.
    val translated = (tState as? SheetTranslateState.Done)?.takeIf { it.key == key }?.result?.translated.orEmpty()

    LaunchedEffect(data, floor, language, translated) {
        sheet = null
        sheet = withContext(Dispatchers.Default) { runCatching { DecisionSheet.render(context, data, floor, lang, translated) }.getOrNull() }
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
                if (!floor) {
                    Text(stringResource(R.string.sheet_language), fontSize = 12.sp)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(DecisionSheet.LANGUAGES) { l ->
                            val loc = Locale(l)
                            FilterChip(
                                selected = language == l, onClick = { language = l },
                                label = { Text(loc.getDisplayLanguage(loc).replaceFirstChar { it.titlecase(loc) }, fontSize = 12.sp) }
                            )
                        }
                    }
                }
                // AI translation of the rules-file texts (not needed for English).
                if (lang != "en" && texts.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    val s = tState
                    val running = s is SheetTranslateState.Running && s.key == key
                    OutlinedButton(enabled = !running, onClick = { translator.translate(texts, lang) }) {
                        Text(stringResource(if (running) R.string.sheet_translating else R.string.sheet_translate_button), fontSize = 12.sp)
                    }
                    when {
                        s is SheetTranslateState.Blocked && s.key == key -> Text(stringResource(R.string.explain_blocked), fontSize = 11.sp, color = Color(0xFFFFC566))
                        s is SheetTranslateState.Done && s.key == key -> {
                            val source = when (s.ai.inferenceSource) {
                                "IN_CLOUD" -> stringResource(R.string.ai_source_in_cloud)
                                "ON_DEVICE" -> stringResource(R.string.ai_source_on_device)
                                else -> s.ai.inferenceSource
                            }
                            Text(stringResource(R.string.ai_read_source, source, s.ai.durationMs.toInt()), fontSize = 11.sp, color = Color(0xFF55D6E8))
                            val r = s.result
                            Text(
                                when {
                                    s.ai.text == null -> aiErrorText(s.ai)
                                    r == null -> stringResource(R.string.sheet_translate_failed)
                                    else -> stringResource(R.string.sheet_translate_result, r.translated.size, texts.size, r.rejected.size)
                                },
                                fontSize = 11.sp, lineHeight = 15.sp
                            )
                        }
                        else -> Text(stringResource(R.string.sheet_translate_hint), fontSize = 11.sp, lineHeight = 15.sp)
                    }
                }
                Spacer(Modifier.height(6.dp))
                val img = sheet
                if (img == null) {
                    Text(stringResource(R.string.sheet_making), fontSize = 13.sp)
                } else {
                    Image(
                        bitmap = img.asImageBitmap(),
                        contentDescription = stringResource(R.string.sheet_button),
                        contentScale = ContentScale.FillWidth,
                        modifier = Modifier.fillMaxWidth().heightIn(max = 4000.dp).aspectRatio(img.width.toFloat() / img.height)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(enabled = sheet != null, onClick = { sheet?.let { DecisionSheet.share(context, it, floor, lang) } }) {
                Text(stringResource(R.string.sheet_share))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.preview_cancel)) } }
    )
}
