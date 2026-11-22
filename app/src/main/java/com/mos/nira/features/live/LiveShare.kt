package com.mos.nira.features.live

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.tasks.Task
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.mos.nira.R
import com.mos.nira.core.ai.AiSettings
import com.mos.nira.core.ai.CardExplanation
import com.mos.nira.core.live.LiveJob
import com.mos.nira.features.ai.activityViewModelStoreOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * "Show this job on the web": the job's text facts (the same facts and prompt "Explain this card"
 * uses; never the artwork) go to Firestore under this phone's code for [LiveJob.HOURS] hours.
 * The phone signs in anonymously so only it can change or delete its code (firestore.rules).
 * Not AI: Firestore only stores the text.
 */
class LiveShareViewModel(application: Application) : AndroidViewModel(application) {

    sealed interface State {
        data object Off : State
        data object Sending : State
        data class Sharing(val sentAt: Long) : State
        data class Failed(val message: String) : State
    }

    private val prefs = application.getSharedPreferences("live_share", Context.MODE_PRIVATE)

    /** One code per phone, kept so the person on the web types it only once. */
    val code: String = prefs.getString("code", null)?.takeIf { LiveJob.isCode(it) }
        ?: LiveJob.newCode().also { prefs.edit().putString("code", it).apply() }

    private val _on = MutableStateFlow(prefs.getBoolean("on", false))
    val on: StateFlow<Boolean> = _on.asStateFlow()

    private val _state = MutableStateFlow<State>(State.Off)
    val state: StateFlow<State> = _state.asStateFlow()

    private var pushJob: Job? = null
    private var lastSentKey: Int? = null

    fun start(title: String, methodName: String?, facts: List<String>) {
        setOn(true)
        push(title, methodName, facts, force = true)
    }

    /** Sends the job if sharing is on and it changed since the last send. */
    fun push(title: String, methodName: String?, facts: List<String>, force: Boolean = false) {
        if (!_on.value) return
        val key = listOf(title, methodName, facts).hashCode()
        if (!force && key == lastSentKey) return
        if (AiSettings.confidentialMode.value) {
            _state.value = State.Failed(getApplication<Application>().getString(R.string.live_confidential))
            return
        }
        pushJob?.cancel()
        pushJob = viewModelScope.launch {
            _state.value = State.Sending
            try {
                val app = getApplication<Application>()
                val auth = FirebaseAuth.getInstance()
                val uid = auth.currentUser?.uid ?: auth.signInAnonymously().awaitTask().user?.uid
                    ?: error("no sign-in")
                val template = withContext(Dispatchers.IO) {
                    app.assets.open(CardExplanation.ASSET_PATH).bufferedReader().use { it.readText() }
                }
                val savedAt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())
                val data = LiveJob.fields(title, methodName, savedAt, facts, CardExplanation.build(template, facts)) + mapOf(
                    "uid" to uid,
                    "updatedAt" to FieldValue.serverTimestamp(),
                    "expireAt" to Timestamp(Date(System.currentTimeMillis() + LiveJob.HOURS * 3_600_000L)),
                )
                FirebaseFirestore.getInstance().collection(LiveJob.COLLECTION).document(code).set(data).awaitTask()
                lastSentKey = key
                _state.value = State.Sharing(System.currentTimeMillis())
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _state.value = State.Failed(e.localizedMessage ?: e.javaClass.simpleName)
            }
        }
    }

    fun stop() {
        setOn(false)
        pushJob?.cancel()
        lastSentKey = null
        _state.value = State.Off
        viewModelScope.launch {
            runCatching { FirebaseFirestore.getInstance().collection(LiveJob.COLLECTION).document(code).delete().awaitTask() }
        }
    }

    private fun setOn(value: Boolean) {
        _on.value = value
        prefs.edit().putBoolean("on", value).apply()
    }
}

private suspend fun <T> Task<T>.awaitTask(): T = suspendCancellableCoroutine { c ->
    addOnCompleteListener { t ->
        if (t.isSuccessful) c.resume(t.result) else c.resumeWithException(t.exception ?: IllegalStateException("Firebase task failed"))
    }
}

/**
 * Keeps the web page up to date while sharing is on. Put it in a part of the job page that is
 * always composed, once, so every change (any tab) is sent after a short pause.
 */
@Composable
fun LiveShareAutoUpdate(title: String, methodName: String?, facts: List<String>) {
    val vm: LiveShareViewModel = viewModel(viewModelStoreOwner = activityViewModelStoreOwner())
    val on by vm.on.collectAsState()
    LaunchedEffect(on, title, methodName, facts) {
        if (on && facts.isNotEmpty()) {
            delay(1500)
            vm.push(title, methodName, facts)
        }
    }
}

/** The card on the Decision tab: start, code, status, copy link, stop. */
@Composable
fun LiveShareSection(title: String, methodName: String?, facts: List<String>, accent: Color, muted: Color, text: Color) {
    val vm: LiveShareViewModel = viewModel(viewModelStoreOwner = activityViewModelStoreOwner())
    val on by vm.on.collectAsState()
    val state by vm.state.collectAsState()
    val context = LocalContext.current
    val time = SimpleDateFormat("HH:mm", Locale.getDefault())

    Column {
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.live_title), color = accent, fontSize = 11.sp, letterSpacing = 1.5.sp, fontWeight = FontWeight.Bold)
        Text(stringResource(R.string.live_body), color = muted, fontSize = 12.sp, lineHeight = 16.sp)
        Spacer(Modifier.height(8.dp))
        if (!on) {
            OutlinedButton(onClick = { vm.start(title, methodName, facts) }, enabled = facts.isNotEmpty()) {
                Text(stringResource(R.string.live_start), color = accent, fontWeight = FontWeight.Bold)
            }
        } else {
            Text(stringResource(R.string.live_code, LiveJob.display(vm.code)), color = text, fontSize = 22.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            Text(stringResource(R.string.live_where, LiveJob.WEB_URL.removePrefix("https://")), color = muted, fontSize = 12.sp, lineHeight = 16.sp)
            Spacer(Modifier.height(4.dp))
            val status = when (val s = state) {
                LiveShareViewModel.State.Sending -> stringResource(R.string.live_sending)
                is LiveShareViewModel.State.Sharing -> stringResource(R.string.live_sent, time.format(Date(s.sentAt)))
                is LiveShareViewModel.State.Failed -> stringResource(R.string.live_error, s.message)
                LiveShareViewModel.State.Off -> stringResource(R.string.live_sending)
            }
            Text(status, color = if (state is LiveShareViewModel.State.Failed) Color(0xFFFF8A80) else accent, fontSize = 12.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = {
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("NIRA", LiveJob.webLink(vm.code)))
                    Toast.makeText(context, context.getString(R.string.live_copied), Toast.LENGTH_SHORT).show()
                }) { Text(stringResource(R.string.live_copy), color = accent) }
                if (state is LiveShareViewModel.State.Failed) TextButton(onClick = { vm.start(title, methodName, facts) }) {
                    Text(stringResource(R.string.prev_retry), color = accent)
                }
                TextButton(onClick = { vm.stop() }) { Text(stringResource(R.string.live_stop), color = muted) }
            }
        }
    }
}

