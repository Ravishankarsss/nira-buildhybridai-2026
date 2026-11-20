package com.mos.nira.features.advice

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mos.nira.R
import com.mos.nira.core.ai.BriefReader
import com.mos.nira.features.ai.BriefReadState
import com.mos.nira.features.ai.BriefReaderViewModel
import com.mos.nira.features.ai.activityViewModelStoreOwner
import com.mos.nira.features.ai.aiErrorText
import com.mos.nira.features.artwork.presentation.garmentColourName

private val RCyan = Color(0xFF55D6E8)
private val RAmber = Color(0xFFFFC566)
private val RMuted = Color(0xFF8A93A8)

/**
 * "Read the buyer's brief": paste the order email or choose a photo of the tech-pack page. The AI
 * proposes answers; code checks them (BriefReader); the user ticks which ones fill the brief.
 */
@Composable
fun BriefReaderDialog(
    colours: List<Pair<String, String>>,
    fabrics: List<String>,
    onApply: (List<BriefReader.Field>) -> Unit,
    onDismiss: () -> Unit,
    viewModel: BriefReaderViewModel = viewModel(viewModelStoreOwner = activityViewModelStoreOwner())
) {
    LaunchedEffect(Unit) { viewModel.reset() }
    val state by viewModel.state.collectAsState()
    var usePhoto by rememberSaveable { mutableStateOf(false) }
    var text by rememberSaveable { mutableStateOf("") }
    var imageUri by remember { mutableStateOf<Uri?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> if (uri != null) imageUri = uri }
    val keys = colours.map { it.first }
    val chosen = remember { mutableStateListOf<BriefReader.Kind>() }

    val done = state as? BriefReadState.Done
    val usable = done?.reading?.usable.orEmpty()
    LaunchedEffect(done) { chosen.clear(); chosen.addAll(usable.map { it.kind }) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.reader_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when (val s = state) {
                    is BriefReadState.Done -> {
                        val source = when (s.result.inferenceSource) {
                            "IN_CLOUD" -> stringResource(R.string.ai_source_in_cloud)
                            "ON_DEVICE" -> stringResource(R.string.ai_source_on_device)
                            else -> s.result.inferenceSource
                        }
                        Text(stringResource(R.string.ai_read_source, source, s.result.durationMs.toInt()), color = RCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        val reading = s.reading
                        when {
                            s.result.text == null -> Text(aiErrorText(s.result), color = RAmber, fontSize = 13.sp)
                            reading == null || reading.fields.isEmpty() -> Text(stringResource(R.string.reader_nothing), color = RAmber, fontSize = 13.sp)
                            else -> {
                                Text(stringResource(if (usePhoto) R.string.reader_check_photo else R.string.reader_check_text), fontSize = 12.sp, lineHeight = 16.sp)
                                reading.fields.forEach { f -> FieldRow(f, colours, f.kind in chosen) { on -> if (on) chosen.add(f.kind) else chosen.remove(f.kind) } }
                            }
                        }
                    }
                    BriefReadState.Running -> Text(stringResource(R.string.reader_running), fontSize = 14.sp)
                    BriefReadState.Blocked -> Text(stringResource(R.string.reader_blocked), color = RAmber, fontSize = 13.sp, lineHeight = 18.sp)
                    BriefReadState.Idle -> {
                        Text(stringResource(R.string.reader_intro), fontSize = 12.sp, lineHeight = 16.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(selected = !usePhoto, onClick = { usePhoto = false }, label = { Text(stringResource(R.string.reader_tab_text)) })
                            FilterChip(selected = usePhoto, onClick = { usePhoto = true }, label = { Text(stringResource(R.string.reader_tab_photo)) })
                        }
                        if (!usePhoto) {
                            OutlinedTextField(
                                value = text, onValueChange = { text = it.take(BriefReader.MAX_TEXT_CHARS) },
                                modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp),
                                placeholder = { Text(stringResource(R.string.reader_text_hint), fontSize = 12.sp) }
                            )
                        } else {
                            OutlinedButton(onClick = { picker.launch("image/*") }) {
                                Text(stringResource(if (imageUri == null) R.string.reader_choose_photo else R.string.reader_photo_chosen))
                            }
                            Text(stringResource(R.string.reader_photo_privacy), color = RMuted, fontSize = 11.sp, lineHeight = 15.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {
            when (state) {
                is BriefReadState.Done -> TextButton(
                    enabled = chosen.isNotEmpty(),
                    onClick = { onApply(usable.filter { it.kind in chosen }); onDismiss() }
                ) { Text(stringResource(R.string.reader_apply, chosen.size)) }
                BriefReadState.Idle -> TextButton(
                    enabled = if (usePhoto) imageUri != null else text.isNotBlank(),
                    onClick = { viewModel.read(if (usePhoto) null else text, if (usePhoto) imageUri else null, keys, fabrics) }
                ) { Text(stringResource(R.string.reader_read)) }
                else -> Unit
            }
        },
        dismissButton = {
            Row {
                if (state is BriefReadState.Done || state is BriefReadState.Blocked) {
                    TextButton(onClick = { viewModel.reset() }) { Text(stringResource(R.string.reader_again)) }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.preview_cancel)) }
            }
        }
    )
}

@Composable
private fun FieldRow(f: BriefReader.Field, colours: List<Pair<String, String>>, checked: Boolean, onCheck: (Boolean) -> Unit) {
    val question = stringResource(
        when (f.kind) {
            BriefReader.Kind.GARMENT_COLOUR -> R.string.brief_q_garment
            BriefReader.Kind.FIBRE -> R.string.reader_q_fibre
            BriefReader.Kind.FABRIC -> R.string.reader_q_fabric
            BriefReader.Kind.QUANTITY -> R.string.brief_q_quantity
            BriefReader.Kind.FEEL -> R.string.brief_q_feel
        }
    )
    val value = when (f.kind) {
        BriefReader.Kind.GARMENT_COLOUR -> colours.firstOrNull { it.first == f.value }?.let { garmentColourName(it.first, it.second) } ?: f.value
        BriefReader.Kind.FIBRE -> BriefReader.fibre(f.value)?.let { fibreLabel(it) } ?: f.value
        BriefReader.Kind.FABRIC -> fabricLabel(f.value)
        BriefReader.Kind.QUANTITY -> f.value
        BriefReader.Kind.FEEL -> BriefReader.feel(f.value)?.let { feelLabel(it) } ?: f.value
    }
    val ok = f.dropped == null
    Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth().clickable(enabled = ok) { onCheck(!checked) }) {
        Checkbox(checked = ok && checked, onCheckedChange = if (ok) onCheck else null, enabled = ok)
        Spacer(Modifier.width(4.dp))
        Column(Modifier.weight(1f)) {
            Text("${question.trimStart('1', '2', '3', '4', '.', ' ')}: $value", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = if (ok) Color.Unspecified else RMuted)
            f.evidence?.let { Text("“$it”", fontSize = 11.sp, color = RMuted, lineHeight = 14.sp) }
            if (!ok) Text(
                stringResource(
                    when (f.dropped) {
                        BriefReader.Drop.NOT_ALLOWED -> R.string.reader_drop_allowed
                        BriefReader.Drop.NOT_IN_TEXT -> R.string.reader_drop_text
                        else -> R.string.reader_drop_number
                    }
                ),
                fontSize = 11.sp, color = RAmber
            )
            Spacer(Modifier.height(2.dp))
        }
    }
}
