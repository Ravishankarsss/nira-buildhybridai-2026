package com.mos.nira.features.ai

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mos.nira.R
import com.mos.nira.core.ai.ArtworkRead
import com.mos.nira.core.ai.ArtworkType
import com.mos.nira.core.ai.HybridInferenceResult

private val CardBg = Color(0xFF171F35)
private val Violet = Color(0xFFB3A8FF)
private val Cyan = Color(0xFF55D6E8)
private val TextWhite = Color(0xFFF5F6FF)
private val TextMuted = Color(0xFFA8B4CE)
private val ErrorRed = Color(0xFFFF8A80)
private val DeviceGreen = Color(0xFF7BE0A0)

/**
 * "AI read of the artwork" card. Shows the real inference source and duration on every answer.
 */
@Composable
fun ArtworkAiReadCard(
    bitmap: Bitmap?,
    printColourHexes: List<String>,
    garmentHex: String?,
    modifier: Modifier = Modifier,
    viewModel: ArtworkAiReadViewModel = viewModel(viewModelStoreOwner = activityViewModelStoreOwner())
) {
    val rawState by viewModel.state.collectAsState()
    // The view model is shared, so a finished read from another artwork must not be shown here.
    val key = ArtworkKey.of(bitmap, printColourHexes)
    val state = rawState.let { s ->
        if (s is ArtworkAiReadState.Done && s.key != key) ArtworkAiReadState.Idle else s
    }

    if (state == ArtworkAiReadState.AskImageUpload) {
        AlertDialog(
            onDismissRequest = { viewModel.cancelImageUpload() },
            containerColor = CardBg,
            title = { Text(stringResource(R.string.ai_upload_title), color = TextWhite) },
            text = { Text(stringResource(R.string.ai_upload_message), color = TextMuted) },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmImageUpload() }) {
                    Text(stringResource(R.string.ai_upload_send), color = Violet)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.cancelImageUpload() }) {
                    Text(stringResource(R.string.ai_upload_cancel), color = Cyan)
                }
            }
        )
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.ai_read_title).uppercase(),
                color = Violet,
                fontSize = 11.sp,
                letterSpacing = 1.5.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = stringResource(R.string.ai_read_hint), color = TextMuted, fontSize = 12.sp)
            Spacer(modifier = Modifier.height(10.dp))

            val running = state == ArtworkAiReadState.Running
            Button(
                onClick = { viewModel.read(bitmap, printColourHexes, garmentHex) },
                enabled = !running,
                colors = ButtonDefaults.buttonColors(containerColor = Violet, contentColor = Color(0xFF0B1020)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    stringResource(if (running) R.string.ai_read_running else R.string.ai_read_button),
                    fontWeight = FontWeight.Bold
                )
            }

            when (val s = state) {
                ArtworkAiReadState.BlockedByConfidentialMode -> Message(stringResource(R.string.ai_read_blocked))
                ArtworkAiReadState.NoImage -> Message(stringResource(R.string.ai_read_no_image))
                is ArtworkAiReadState.Done -> ResultView(s.result, s.read)
                else -> Unit
            }
        }
    }
}

@Composable
private fun Message(text: String, color: Color = TextMuted) {
    Spacer(modifier = Modifier.height(10.dp))
    Text(text = text, color = color, fontSize = 13.sp, lineHeight = 18.sp)
}

@Composable
private fun ResultView(result: HybridInferenceResult, read: ArtworkRead?) {
    Spacer(modifier = Modifier.height(12.dp))

    if (result.text == null) {
        // Failed request: show the real source word (ERROR / UNAVAILABLE) and the error.
        SourceBadge(result)
        Message(stringResource(R.string.ai_read_error, result.errorMessage ?: ""), ErrorRed)
        return
    }

    SourceBadge(result)

    if (read == null) {
        Message(stringResource(R.string.ai_read_unreadable))
        Message(result.text, TextWhite)
    } else {
        Field(
            stringResource(R.string.ai_read_type),
            if (read.artworkType == ArtworkType.UNKNOWN) stringResource(R.string.ai_type_unknown) else read.artworkType.name
        )
        if (read.motifs.isNotEmpty()) Field(stringResource(R.string.ai_read_motifs), read.motifs.joinToString(", "))
        if (read.effects.isNotEmpty()) Field(stringResource(R.string.ai_read_effects), read.effects.joinToString(", "))
        if (read.risks.isNotEmpty()) {
            Field(
                stringResource(R.string.ai_read_risks),
                read.risks.joinToString("\n") { r ->
                    val label = r.type.name.replace('_', ' ')
                    if (r.note.isEmpty()) "• $label" else "• $label: ${r.note}"
                }
            )
        }
        if (read.evidence.isNotEmpty()) Field(stringResource(R.string.ai_read_evidence), read.evidence)
    }

    Message(stringResource(R.string.ai_read_disclaimer))
}

@Composable
private fun SourceBadge(result: HybridInferenceResult) {
    val (label, color) = when (result.inferenceSource) {
        "ON_DEVICE" -> stringResource(R.string.ai_source_on_device) to DeviceGreen
        "IN_CLOUD" -> stringResource(R.string.ai_source_in_cloud) to Cyan
        else -> result.inferenceSource to ErrorRed
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = stringResource(R.string.ai_read_source, label, result.durationMs.toInt()),
            color = color,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(color.copy(alpha = 0.12f))
                .padding(horizontal = 8.dp, vertical = 4.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
    }
}

@Composable
private fun Field(label: String, value: String) {
    Spacer(modifier = Modifier.height(10.dp))
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(text = label, color = TextMuted, fontSize = 12.sp)
        Text(text = value, color = TextWhite, fontSize = 14.sp, lineHeight = 20.sp)
    }
}
