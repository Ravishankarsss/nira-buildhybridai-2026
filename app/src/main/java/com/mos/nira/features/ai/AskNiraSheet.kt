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
import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import java.io.File
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.TextButton
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.delay
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

    // Read-aloud of checked answers (Android's own text-to-speech; Ravi's option A).
    val ctx = LocalContext.current
    val speaker = remember { NiraSpeaker(ctx) }
    val speakingId by speaker.speaking.collectAsState()
    var voiceNote by remember { mutableStateOf<Int?>(null) }
    val appLanguage = ctx.resources.configuration.locales[0].language
    fun speak(index: Int, text: String) {
        voiceNote = when (speaker.speak("a$index", text, appLanguage)) {
            NiraSpeaker.Result.SPEAKING -> null
            NiraSpeaker.Result.VOICE_MISSING -> R.string.ask_tts_missing
            NiraSpeaker.Result.NOT_READY -> R.string.ask_tts_not_ready
        }
    }
    // A spoken question gets a spoken answer, once, and only if the answer passed the checks.
    var spokenUpTo by remember { mutableStateOf(messages.size) }
    LaunchedEffect(messages) {
        val i = messages.lastIndex
        val m = messages.lastOrNull()
        if (m != null && i >= spokenUpTo && m.result != null) {
            spokenUpTo = i + 1
            if (m.voice && m.hold == null && m.answer != null) speak(i, m.answer)
        }
    }
    DisposableEffect(Unit) { onDispose { speaker.shutdown() } }

    // Voice question: record with Android's own recorder; the AI hears it in the cloud only.
    val context = LocalContext.current
    var recording by remember { mutableStateOf(false) }
    val recorder = remember { mutableStateOf<MediaRecorder?>(null) }
    val audioFile = remember { File(context.cacheDir, "ask_voice.m4a") }
    val pendingLabel = stringResource(R.string.ask_voice_pending)
    fun stopAndAsk() {
        val r = recorder.value ?: return
        recorder.value = null
        recording = false
        val ok = runCatching { r.stop() }.isSuccess
        r.release()
        if (ok && audioFile.length() > 0) viewModel.askVoice(audioFile.readBytes(), "audio/mp4", facts, pendingLabel)
        audioFile.delete()
    }
    fun cancelRecording() {
        val r = recorder.value ?: return
        recorder.value = null
        recording = false
        runCatching { r.stop() }
        r.release()
        audioFile.delete()
    }
    var seconds by remember { mutableStateOf(0) }
    var tooShort by remember { mutableStateOf(false) }
    LaunchedEffect(recording) { seconds = 0; while (recording) { delay(1000); seconds++ } }
    fun startRecording() {
        speaker.stop()
        tooShort = false
        val r = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(context) else @Suppress("DEPRECATION") MediaRecorder()
        val started = runCatching {
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioSamplingRate(16000)
            r.setAudioEncodingBitRate(32000)
            r.setMaxDuration(AskNira.VOICE_MAX_SECONDS * 1000)
            r.setOutputFile(audioFile.absolutePath)
            r.setOnInfoListener { _, what, _ -> if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) stopAndAsk() }
            r.prepare()
            r.start()
        }.isSuccess
        if (started) { recorder.value = r; recording = true } else r.release()
    }
    // After the first "Allow", the user holds the button again to talk.
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    DisposableEffect(Unit) { onDispose { recorder.value?.let { runCatching { it.stop() }; it.release() }; audioFile.delete() } }

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
                itemsIndexed(messages) { i, m ->
                    MessageBubble(
                        m,
                        speaking = speakingId == "a$i",
                        onSpeak = { text -> if (speakingId == "a$i") speaker.stop() else speak(i, text) },
                        onEdit = { q -> input = q }
                    )
                }
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
            // Hold to talk, let go to send (like a voice message). Under 1 second is not sent.
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp).heightIn(min = 64.dp)
                    .clip(RoundedCornerShape(32.dp)).background(if (recording) KRed else KInner)
                    .pointerInput(running) {
                        detectTapGestures(onPress = {
                            if (running) return@detectTapGestures
                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                                micPermission.launch(Manifest.permission.RECORD_AUDIO)
                                return@detectTapGestures
                            }
                            val pressedAt = System.currentTimeMillis()
                            startRecording()
                            tryAwaitRelease()
                            if (System.currentTimeMillis() - pressedAt < 1000) { cancelRecording(); tooShort = true } else stopAndAsk()
                        })
                    }
            ) {
                Text(
                    if (recording) stringResource(R.string.ask_voice_stop, seconds, AskNira.VOICE_MAX_SECONDS)
                    else stringResource(R.string.ask_voice_start, AskNira.VOICE_MAX_SECONDS),
                    color = if (recording) Color(0xFF0F1526) else if (running) KMuted else KCyan,
                    fontWeight = FontWeight.Bold, fontSize = 15.sp
                )
            }
            if (tooShort) Text(stringResource(R.string.ask_voice_too_short), color = KAmber, fontSize = 11.sp)
            voiceNote?.let { Text(stringResource(it), color = KAmber, fontSize = 11.sp, lineHeight = 15.sp) }
            Text(stringResource(R.string.ask_voice_privacy), color = KMuted, fontSize = 10.sp, lineHeight = 13.sp)
            Text(stringResource(R.string.ask_privacy), color = KMuted, fontSize = 10.sp, lineHeight = 13.sp, modifier = Modifier.padding(vertical = 6.dp))
        }
    }
}

@Composable
private fun MessageBubble(m: AskMessage, speaking: Boolean, onSpeak: (String) -> Unit, onEdit: (String) -> Unit) {
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
                                TextButton(onClick = { onSpeak(m.answer) }) {
                                    Text(stringResource(if (speaking) R.string.ask_stop_speaking else R.string.ask_speak), color = KCyan, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
