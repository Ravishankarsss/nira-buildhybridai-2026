package com.mos.nira.features.ai

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mos.nira.R
import com.mos.nira.core.ai.AskNira

private val KBg = Color(0xFF11182A)
private val KInner = Color(0xFF1A2238)
private val KCyan = Color(0xFF55D6E8)
private val KViolet = Color(0xFFB3A8FF)
private val KWhite = Color(0xFFF5F6FF)
private val KMuted = Color(0xFFA8B4CE)
private val KAmber = Color(0xFFFFC566)
private val KRed = Color(0xFFFF8A80)

/**
 * Ask NIRA about this job: a chat sheet. Facts are the engine's (never the image); every answer
 * shows the inference source the Firebase SDK reported and is hidden if it fails a check.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AskNiraSheet(
    facts: List<String>,
    onDismiss: () -> Unit,
    viewModel: AskNiraViewModel = viewModel(viewModelStoreOwner = activityViewModelStoreOwner())
) {
    val jobKey = facts.hashCode().toString()
    LaunchedEffect(jobKey) { viewModel.openJob(jobKey) }
    val messages by viewModel.messages.collectAsState()
    val running = messages.any { it.result == null && !it.blocked }
    var input by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()
    LaunchedEffect(messages.size, running) { if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex) }

    val send = { q: String -> if (q.isNotBlank() && !running) { viewModel.ask(q, facts); input = "" } }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = KBg
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp).navigationBarsPadding().imePadding()
        ) {
            Text(stringResource(R.string.ask_title), color = KWhite, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.ask_intro), color = KMuted, fontSize = 12.sp, lineHeight = 16.sp)
            Spacer(Modifier.height(10.dp))

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp, max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                itemsIndexed(messages) { _, m -> MessageBubble(m) }
            }

            if (messages.isEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(R.string.ask_suggest_why, R.string.ask_suggest_cheaper, R.string.ask_suggest_risk, R.string.ask_suggest_check).forEach { id ->
                        val q = stringResource(id)
                        AssistChip(
                            onClick = { send(q) },
                            label = { Text(q, fontSize = 12.sp) },
                            colors = AssistChipDefaults.assistChipColors(containerColor = KInner, labelColor = KCyan)
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it.take(300) },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text(stringResource(R.string.ask_placeholder), fontSize = 13.sp) },
                    maxLines = 3,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { send(input) }),
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = KWhite, unfocusedTextColor = KWhite, focusedBorderColor = KCyan)
                )
                Spacer(Modifier.padding(4.dp))
                Button(
                    onClick = { send(input) },
                    enabled = input.isNotBlank() && !running,
                    colors = ButtonDefaults.buttonColors(containerColor = KViolet, contentColor = Color(0xFF0F1526))
                ) { Text(stringResource(R.string.ask_send), fontWeight = FontWeight.Bold) }
            }
            Text(stringResource(R.string.ask_privacy), color = KMuted, fontSize = 10.sp, lineHeight = 13.sp, modifier = Modifier.padding(vertical = 6.dp))
        }
    }
}

@Composable
private fun MessageBubble(m: AskMessage) {
    Column(Modifier.fillMaxWidth()) {
        // The question, on the right.
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Text(
                m.question, color = Color(0xFF0F1526), fontSize = 14.sp,
                modifier = Modifier.widthIn(max = 300.dp).clip(RoundedCornerShape(12.dp)).background(KCyan).padding(10.dp)
            )
        }
        Spacer(Modifier.height(6.dp))
        Column(Modifier.widthIn(max = 330.dp).clip(RoundedCornerShape(12.dp)).background(KInner).padding(10.dp)) {
            val r = m.result
            when {
                m.blocked -> Text(stringResource(R.string.explain_blocked), color = KMuted, fontSize = 13.sp, lineHeight = 18.sp)
                r == null -> Text(stringResource(R.string.ask_thinking), color = KMuted, fontSize = 13.sp)
                else -> {
                    val ok = r.text != null
                    val source = when (r.inferenceSource) {
                        "IN_CLOUD" -> stringResource(R.string.ai_source_in_cloud)
                        "ON_DEVICE" -> stringResource(R.string.ai_source_on_device)
                        else -> r.inferenceSource
                    }
                    Text(
                        stringResource(R.string.ai_read_source, source, r.durationMs.toInt()),
                        color = if (ok) KCyan else KRed, fontSize = 11.sp, fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(4.dp))
                    when (val hold = m.hold) {
                        is AskNira.Hold.Numbers -> Text(
                            stringResource(R.string.ask_held_numbers, hold.values.joinToString(", ") { n -> if (n % 1.0 == 0.0) n.toLong().toString() else n.toString() }),
                            color = KAmber, fontSize = 13.sp, lineHeight = 18.sp
                        )
                        AskNira.Hold.ColourCode -> Text(stringResource(R.string.ask_held_code), color = KAmber, fontSize = 13.sp, lineHeight = 18.sp)
                        null -> if (ok) SelectionContainer { Text(r.text!!, color = KWhite, fontSize = 14.sp, lineHeight = 20.sp) }
                            else Text(aiErrorText(r), color = KRed, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}
