package com.mos.nira.features.ai

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mos.nira.R
import com.mos.nira.core.ai.AiActivity
import com.mos.nira.core.ai.AiActivityLog
import com.mos.nira.core.ai.AiFeature
import com.mos.nira.core.ai.AiOutcome
import com.mos.nira.core.ai.AiSettings
import java.text.SimpleDateFormat
import java.util.Date

private val ABg = Color(0xFF0B1020)
private val ACard = Color(0xFF151C30)
private val ACyan = Color(0xFF55D6E8)
private val AViolet = Color(0xFFB3A8FF)
private val AWhite = Color(0xFFF5F6FF)
private val AMuted = Color(0xFFA8B4CE)
private val AGreen = Color(0xFF7BE0A0)
private val AAmber = Color(0xFFFFC566)
private val ARed = Color(0xFFFF8A80)

/**
 * Every AI request on this phone, as the Firebase SDK reported it: on-device or cloud, how long,
 * image or voice sent, and whether NIRA's checks passed or held the answer back.
 */
@Composable
fun AiActivityScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { AiActivityLog.load(context); AiSettings.load(context) }
    val entries by AiActivityLog.entries.collectAsState()
    val confidential by AiSettings.confidentialMode.collectAsState()
    val t = AiActivityLog.totals(entries)
    val locale = context.resources.configuration.locales[0]
    val time = SimpleDateFormat("d MMM, HH:mm", locale)

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(ABg).safeDrawingPadding().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item {
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "←", color = AWhite, fontSize = 22.sp,
                    modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable(onClick = onBack).padding(horizontal = 8.dp, vertical = 2.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.act_log_title), color = AWhite, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
            Text(stringResource(R.string.act_log_intro), color = AMuted, fontSize = 12.sp, lineHeight = 16.sp, modifier = Modifier.padding(top = 4.dp))
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Total("📱", t.onDevice, stringResource(R.string.act_log_on_device), ACyan, Modifier.weight(1f))
                Total("☁️", t.cloud, stringResource(R.string.act_log_cloud), AViolet, Modifier.weight(1f))
                Total("🛡", t.held, stringResource(R.string.act_log_held), AAmber, Modifier.weight(1f))
                Total("⚠", t.failed, stringResource(R.string.act_log_failed), ARed, Modifier.weight(1f))
            }
            Text(
                stringResource(R.string.act_log_sent, t.images, t.voice) + "  ·  " +
                    stringResource(if (confidential) R.string.act_log_confidential_on else R.string.act_log_confidential_off),
                color = AMuted, fontSize = 11.sp, lineHeight = 15.sp, modifier = Modifier.padding(top = 6.dp)
            )
        }
        if (entries.isEmpty()) {
            item { Text(stringResource(R.string.act_log_empty), color = AMuted, fontSize = 13.sp, lineHeight = 18.sp) }
        }
        items(entries) { e -> Row(e, time.format(Date(e.timeMs))) }
        if (entries.isNotEmpty()) item {
            TextButton(onClick = { AiActivityLog.clear(context) }) { Text(stringResource(R.string.act_log_clear), color = AMuted, fontSize = 12.sp) }
        }
    }
}

@Composable
private fun Total(icon: String, n: Int, label: String, colour: Color, modifier: Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(14.dp)).background(ACard).padding(vertical = 10.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(icon, fontSize = 16.sp)
        Text("$n", color = colour, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(label, color = AMuted, fontSize = 10.sp, textAlign = TextAlign.Center, maxLines = 2, lineHeight = 12.sp)
    }
}

@Composable
private fun featureName(f: AiFeature): String = stringResource(
    when (f) {
        AiFeature.ASK -> R.string.feat_ask
        AiFeature.ASK_VOICE -> R.string.feat_ask_voice
        AiFeature.EXPLAIN_CARD -> R.string.feat_explain
        AiFeature.COST_DOWN -> R.string.feat_costdown
        AiFeature.ARTWORK_READ -> R.string.feat_artwork_read
        AiFeature.BRIEF_READER -> R.string.feat_brief
        AiFeature.REPORT_EXPLAIN -> R.string.feat_report
        AiFeature.DEEP_REPORT -> R.string.feat_deep
        AiFeature.SHEET_TRANSLATE -> R.string.feat_translate
        AiFeature.OTHER -> R.string.feat_other
    }
)

@Composable
private fun Row(e: AiActivity, whenText: String) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(ACard).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            when (e.source) { "ON_DEVICE" -> "📱"; "IN_CLOUD" -> "☁️"; else -> "⚠" },
            fontSize = 20.sp, modifier = Modifier.width(32.dp)
        )
        Column(Modifier.weight(1f)) {
            Text(featureName(e.feature), color = AWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            val where = when (e.source) {
                "ON_DEVICE" -> stringResource(R.string.ai_source_on_device)
                "IN_CLOUD" -> stringResource(R.string.ai_source_in_cloud)
                else -> stringResource(R.string.act_log_not_answered)
            }
            val sent = listOfNotNull(
                if (e.image) stringResource(R.string.act_log_image) else null,
                if (e.voice) stringResource(R.string.act_log_voice) else null
            )
            Text(
                (listOf(where, "${e.durationMs} ms", whenText) + sent).joinToString("  ·  "),
                color = AMuted, fontSize = 11.sp, lineHeight = 15.sp
            )
        }
        val (label, colour) = when (e.outcome) {
            AiOutcome.PASSED -> stringResource(R.string.act_log_passed) to AGreen
            AiOutcome.HELD -> stringResource(R.string.act_log_held_one) to AAmber
            AiOutcome.ERROR -> stringResource(R.string.act_log_failed_one) to ARed
        }
        Text(
            label, color = colour, fontSize = 11.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(colour.copy(alpha = 0.12f)).padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}
