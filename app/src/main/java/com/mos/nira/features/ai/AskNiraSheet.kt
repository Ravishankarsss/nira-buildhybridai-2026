package com.mos.nira.features.ai

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mos.nira.R
import com.mos.nira.core.ai.AskNira
import com.mos.nira.core.ai.NiraActions
import com.mos.nira.core.ai.VoiceTurn
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

private val KBg = Color(0xFF11182A)
private val KInner = Color(0xFF1A2238)
private val KCyan = Color(0xFF55D6E8)
private val KViolet = Color(0xFFB3A8FF)
private val KWhite = Color(0xFFF5F6FF)
private val KMuted = Color(0xFFA8B4CE)
private val KAmber = Color(0xFFFFC566)
private val KRed = Color(0xFFFF8A80)
private val KGreen = Color(0xFF7BE0A0)

/**
 * Ask NIRA about this job: a chat, a hands-free conversation by voice, and actions the user asks
 * for ("make navy green", "1000 pieces"). Facts are the engine's (never the image); every answer
 * shows the inference source the SDK reported and is hidden if it fails a check; actions are
 * checked by NiraActions and can be undone; the engine recalculates after any change.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AskNiraSheet(
    jobKey: String,
    facts: List<String>,
    actionContext: NiraActions.Context?,
    /** Runs checked actions and returns how to undo them. */
    onActions: (List<NiraActions.Action>) -> (() -> Unit)?,
    onDismiss: () -> Unit,
    /** Home uses its own prompts, suggestions and intro; a job uses the defaults. */
    textPrompt: String = AskNira.ASSET_PATH,
    voicePrompt: String = AskNira.VOICE_ASSET_PATH,
    suggestions: List<String>? = null,
    intro: String? = null,
    placeholder: String? = null,
    voicePrivacy: String? = null,
    viewModel: AskNiraViewModel = viewModel(viewModelStoreOwner = activityViewModelStoreOwner())
) {
    LaunchedEffect(jobKey) { viewModel.openJob(jobKey) }
    val messages by viewModel.messages.collectAsState()
    val running = messages.any { it.result == null && !it.blocked }
    var input by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()
    LaunchedEffect(messages.size, running) { if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex) }
    val send = { q: String -> if (q.isNotBlank() && !running) { viewModel.ask(q, facts, actionContext, textPrompt); input = "" } }

    // ---------- Read-aloud (Android's own text-to-speech; Ravi's option A) ----------
    val context = LocalContext.current
    val speaker = remember { NiraSpeaker(context) }
    val speakingId by speaker.speaking.collectAsState()
    var voiceNote by remember { mutableStateOf<Int?>(null) }
    val appLanguage = context.resources.configuration.locales[0].language
    fun speak(index: Int, text: String) {
        voiceNote = when (speaker.speak("a$index", text, appLanguage)) {
            NiraSpeaker.Result.SPEAKING -> null
            NiraSpeaker.Result.VOICE_MISSING -> R.string.ask_tts_missing
            NiraSpeaker.Result.NOT_READY -> R.string.ask_tts_not_ready
        }
    }

    // ---------- Conversation mode: tap once; NIRA listens, sends on a pause, answers, listens again ----------
    val scope = rememberCoroutineScope()
    var conversation by remember { mutableStateOf(false) }
    var listening by remember { mutableStateOf(false) }
    var awaitingNext by remember { mutableStateOf(false) }
    var silentTurns by remember { mutableStateOf(0) }
    var ended by remember { mutableStateOf(false) }
    val recorder = remember { mutableStateOf<MediaRecorder?>(null) }
    val pollJob = remember { mutableStateOf<Job?>(null) }
    val audioFile = remember { File(context.cacheDir, "ask_voice.m4a") }
    val pendingLabel = stringResource(R.string.ask_voice_pending)

    fun release(send: Boolean) {
        pollJob.value?.cancel(); pollJob.value = null
        val r = recorder.value ?: return
        recorder.value = null
        listening = false
        val ok = runCatching { r.stop() }.isSuccess
        r.release()
        if (send && ok && audioFile.length() > 0) viewModel.askVoice(audioFile.readBytes(), "audio/mp4", facts, pendingLabel, actionContext, voicePrompt)
        audioFile.delete()
    }

    fun endConversation() {
        conversation = false
        awaitingNext = false
        release(send = false)
        speaker.stop()
    }

    fun startListening() {
        awaitingNext = false
        speaker.stop()
        val r = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(context) else @Suppress("DEPRECATION") MediaRecorder()
        val started = runCatching {
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioSamplingRate(16000)
            r.setAudioEncodingBitRate(32000)
            r.setOutputFile(audioFile.absolutePath)
            r.prepare()
            r.start()
        }.isSuccess
        if (!started) { r.release(); endConversation(); return }
        recorder.value = r
        listening = true
        pollJob.value = scope.launch {
            var st = VoiceTurn.State()
            while (true) {
                delay(VoiceTurn.STEP_MS.toLong())
                val amp = runCatching { r.maxAmplitude }.getOrDefault(0)
                val (next, decision) = VoiceTurn.step(st, amp, AskNira.VOICE_MAX_SECONDS * 1000)
                st = next
                when (decision) {
                    VoiceTurn.Decision.SEND -> { silentTurns = 0; release(send = true); return@launch }
                    VoiceTurn.Decision.NOTHING_SAID -> {
                        release(send = false)
                        silentTurns++
                        if (silentTurns >= VoiceTurn.MAX_SILENT_TURNS) { conversation = false; ended = true } else awaitingNext = true
                        return@launch
                    }
                    VoiceTurn.Decision.KEEP_LISTENING -> Unit
                }
            }
        }
    }

    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) { conversation = true; ended = false; silentTurns = 0; startListening() }
    }

    // ---------- New answers: run checked actions, speak voice answers, then listen again ----------
    val undos = remember { mutableStateMapOf<Int, () -> Unit>() }
    val undone = remember { mutableStateMapOf<Int, Boolean>() }
    var handledUpTo by remember { mutableStateOf(messages.size) }
    LaunchedEffect(messages) {
        val i = messages.lastIndex
        val m = messages.lastOrNull() ?: return@LaunchedEffect
        if (i < handledUpTo || (m.result == null && !m.blocked)) return@LaunchedEffect
        handledUpTo = i + 1
        if (m.hold == null) {
            val actions = m.actions.mapNotNull { it.action }
            if (actions.isNotEmpty()) onActions(actions)?.let { undos[i] = it }
        }
        if (m.voice && m.hold == null && m.answer != null) speak(i, m.answer)
        if (conversation) awaitingNext = true
    }
    // Listen again once NIRA has finished thinking and speaking.
    LaunchedEffect(conversation, awaitingNext, running, speakingId, listening) {
        if (conversation && awaitingNext && !running && speakingId == null && !listening) {
            delay(500)
            if (conversation && awaitingNext && speakingId == null) startListening()
        }
    }
    DisposableEffect(Unit) { onDispose { release(send = false); speaker.shutdown() } }

    ModalBottomSheet(
        onDismissRequest = { endConversation(); onDismiss() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = KBg
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).navigationBarsPadding().imePadding()) {
            Text(stringResource(R.string.ask_title), color = KWhite, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(intro ?: stringResource(R.string.ask_intro), color = KMuted, fontSize = 12.sp, lineHeight = 16.sp)
            Spacer(Modifier.height(10.dp))

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp, max = 400.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                itemsIndexed(messages) { i, m ->
                    MessageBubble(
                        m,
                        speaking = speakingId == "a$i",
                        onSpeak = { text -> if (speakingId == "a$i") speaker.stop() else speak(i, text) },
                        onEdit = { q -> endConversation(); input = q },
                        canUndo = undos[i] != null && undone[i] != true,
                        onUndo = { undos[i]?.invoke(); undone[i] = true }
                    )
                }
            }

            if (messages.isEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // The action chip names the job's first ink, so the request is clear.
                    val firstInk = actionContext?.inkHexes?.firstOrNull()?.let { com.mos.nira.features.artwork.presentation.colourName(it).lowercase() }
                    val chips = suggestions ?: (listOf(R.string.ask_suggest_why, R.string.ask_suggest_cheaper, R.string.ask_suggest_risk, R.string.ask_suggest_check)
                        .map { stringResource(it) } + listOfNotNull(firstInk?.let { stringResource(R.string.ask_suggest_action, it) }))
                    chips.forEach { q ->
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
                    placeholder = { Text(placeholder ?: stringResource(R.string.ask_placeholder), fontSize = 13.sp) },
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

            // One tap starts a hands-free conversation; one tap ends it.
            val phase = when {
                !conversation -> stringResource(R.string.ask_talk_start, AskNira.VOICE_MAX_SECONDS)
                listening -> stringResource(R.string.ask_talk_listening)
                running -> stringResource(R.string.ask_talk_thinking)
                speakingId != null -> stringResource(R.string.ask_talk_speaking)
                else -> stringResource(R.string.ask_talk_listening)
            }
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp).heightIn(min = 60.dp)
                    .clip(RoundedCornerShape(30.dp))
                    .background(if (listening) KRed else if (conversation) KViolet else KInner)
                    .clickable {
                        when {
                            conversation -> endConversation()
                            running -> Unit
                            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED -> {
                                conversation = true; ended = false; silentTurns = 0; startListening()
                            }
                            else -> micPermission.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    }
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(phase, color = if (conversation) Color(0xFF0F1526) else KCyan, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    if (conversation) Text(stringResource(R.string.ask_talk_end), color = Color(0xFF0F1526), fontSize = 11.sp)
                }
            }
            if (ended) Text(stringResource(R.string.ask_talk_ended), color = KMuted, fontSize = 11.sp)
            voiceNote?.let { Text(stringResource(it), color = KAmber, fontSize = 11.sp, lineHeight = 15.sp) }
            Text(voicePrivacy ?: stringResource(R.string.ask_voice_privacy), color = KMuted, fontSize = 10.sp, lineHeight = 13.sp)
            Text(stringResource(R.string.ask_privacy), color = KMuted, fontSize = 10.sp, lineHeight = 13.sp, modifier = Modifier.padding(vertical = 6.dp))
        }
    }
}

@Composable
private fun actionText(a: NiraActions.Action): String = when (a) {
    is NiraActions.Action.PreviewInk -> stringResource(R.string.act_preview_ink, colourWord(a.fromHex), colourKeyWord(a.toKey, a.toHex))
    is NiraActions.Action.SetGarment -> stringResource(R.string.act_garment, colourKeyWord(a.key, a.hex))
    is NiraActions.Action.SetQuantity -> stringResource(R.string.act_quantity, a.pieces)
    is NiraActions.Action.SetFibre -> stringResource(R.string.act_fibre, com.mos.nira.core.ai.BriefReader.fibre(a.fibre)?.let { com.mos.nira.features.advice.fibreLabel(it) } ?: a.fibre)
    is NiraActions.Action.SetFabric -> stringResource(R.string.act_fabric, com.mos.nira.features.advice.fabricLabel(a.fabric))
    is NiraActions.Action.SetFeel -> stringResource(R.string.act_feel, com.mos.nira.core.ai.BriefReader.feel(a.feel)?.let { com.mos.nira.features.advice.feelLabel(it) } ?: a.feel)
    is NiraActions.Action.OpenSheet -> stringResource(R.string.act_sheet, java.util.Locale(a.language).let { it.getDisplayLanguage(it) })
    is NiraActions.Action.OpenJob -> stringResource(R.string.act_open_job, a.name)
    is NiraActions.Action.OpenLibrary -> stringResource(R.string.act_open_library, a.query)
    is NiraActions.Action.NewJob -> stringResource(if (a.photo) R.string.act_new_photo else R.string.act_new_artwork)
    NiraActions.Action.OpenAiActivity -> stringResource(R.string.act_open_activity)
    NiraActions.Action.OpenSettings -> stringResource(R.string.act_open_settings)
}

@Composable
private fun colourWord(hex: String) = com.mos.nira.features.artwork.presentation.colourName(hex)

@Composable
private fun colourKeyWord(key: String, hex: String) = com.mos.nira.features.artwork.presentation.garmentColourName(key, hex)

@Composable
private fun rejectText(r: NiraActions.Reject): String = stringResource(
    when (r) {
        NiraActions.Reject.UNKNOWN_INK -> R.string.act_no_ink
        NiraActions.Reject.UNKNOWN_COLOUR -> R.string.act_no_colour
        NiraActions.Reject.QUANTITY_NOT_SAID -> R.string.act_no_quantity
        NiraActions.Reject.BAD_VALUE, NiraActions.Reject.UNKNOWN_TYPE -> R.string.act_not_allowed
    }
)

@Composable
private fun MessageBubble(
    m: AskMessage,
    speaking: Boolean,
    onSpeak: (String) -> Unit,
    onEdit: (String) -> Unit,
    canUndo: Boolean,
    onUndo: () -> Unit
) {
    Column(Modifier.fillMaxWidth()) {
        // The question, on the right.
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Text(
                (if (m.voice) "🎤 " else "") + m.question, color = Color(0xFF0F1526), fontSize = 14.sp,
                modifier = Modifier.widthIn(max = 300.dp).clip(RoundedCornerShape(12.dp)).background(KCyan).padding(10.dp)
            )
        }
        if (m.voice && m.result != null && m.question.isNotBlank()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { onEdit(m.question) }) { Text(stringResource(R.string.ask_edit_heard), color = KMuted, fontSize = 11.sp) }
            }
        }
        Spacer(Modifier.height(6.dp))
        Column(Modifier.widthIn(max = 330.dp).clip(RoundedCornerShape(12.dp)).background(KInner).padding(10.dp)) {
            val r = m.result
            when {
                m.blocked -> Text(stringResource(if (m.voice) R.string.ask_voice_blocked else R.string.explain_blocked), color = KMuted, fontSize = 13.sp, lineHeight = 18.sp)
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
                        null -> when {
                            !ok -> Text(aiErrorText(r), color = KRed, fontSize = 13.sp)
                            m.answer == null -> Text(stringResource(R.string.ask_voice_unclear), color = KAmber, fontSize = 13.sp, lineHeight = 18.sp)
                            else -> {
                                SelectionContainer { Text(m.answer, color = KWhite, fontSize = 14.sp, lineHeight = 20.sp) }
                                // What NIRA did in the app (checked), and what it could not do.
                                m.actions.forEach { p ->
                                    val a = p.action
                                    if (a != null) Text("✓ " + actionText(a), color = KGreen, fontSize = 12.sp, lineHeight = 16.sp)
                                    else p.reject?.let { Text("✗ " + rejectText(it), color = KAmber, fontSize = 12.sp, lineHeight = 16.sp) }
                                }
                                Row {
                                    TextButton(onClick = { onSpeak(m.answer) }) {
                                        Text(stringResource(if (speaking) R.string.ask_stop_speaking else R.string.ask_speak), color = KCyan, fontSize = 12.sp)
                                    }
                                    if (canUndo) TextButton(onClick = onUndo) { Text(stringResource(R.string.act_undo), color = KAmber, fontSize = 12.sp) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
