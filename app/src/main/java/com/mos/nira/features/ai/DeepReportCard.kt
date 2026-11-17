package com.mos.nira.features.ai

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mos.nira.R
import com.mos.nira.core.ai.ReportLanguage

private val CardBg = Color(0xFF171F35)
private val InnerBg = Color(0xFF0F1526)
private val Violet = Color(0xFFB3A8FF)
private val Cyan = Color(0xFF55D6E8)
private val TextWhite = Color(0xFFF5F6FF)
private val TextMuted = Color(0xFFA8B4CE)
private val ErrorRed = Color(0xFFFF8A80)

/**
 * "Deep report" card at the end of the Print Report. buildFacts is called only when the user
 * taps the button, so the facts always match what is on screen at that moment.
 */
@Composable
fun DeepReportCard(
    artworkKey: ArtworkKey?,
    buildFacts: () -> String,
    modifier: Modifier = Modifier,
    viewModel: DeepReportViewModel = viewModel()
) {
    val rawState by viewModel.state.collectAsState()
    val state = rawState.let { s -> if (s is DeepReportState.Done && s.key != artworkKey) DeepReportState.Idle else s }
    var language by remember { mutableStateOf(ReportLanguage.ENGLISH) }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.deep_report_title).uppercase(),
                color = Violet,
                fontSize = 11.sp,
                letterSpacing = 1.5.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(stringResource(R.string.deep_report_hint), color = TextMuted, fontSize = 12.sp, lineHeight = 17.sp)

            Spacer(modifier = Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ReportLanguage.values().forEach { lang ->
                    FilterChip(
                        selected = language == lang,
                        onClick = { language = lang },
                        label = {
                            Text(
                                stringResource(if (lang == ReportLanguage.ENGLISH) R.string.lang_en else R.string.lang_ta),
                                fontSize = 12.sp
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = InnerBg,
                            labelColor = TextMuted,
                            selectedContainerColor = Cyan.copy(alpha = 0.2f),
                            selectedLabelColor = Cyan
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            val running = state == DeepReportState.Running
            Button(
                onClick = { viewModel.write(artworkKey, buildFacts(), language) },
                enabled = !running,
                colors = ButtonDefaults.buttonColors(containerColor = Violet, contentColor = Color(0xFF0B1020)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    stringResource(if (running) R.string.deep_report_running else R.string.deep_report_button),
                    fontWeight = FontWeight.Bold
                )
            }

            when (val s = state) {
                DeepReportState.BlockedByConfidentialMode -> {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(stringResource(R.string.deep_report_blocked), color = TextMuted, fontSize = 13.sp, lineHeight = 18.sp)
                }
                is DeepReportState.Done -> {
                    Spacer(modifier = Modifier.height(12.dp))
                    val ok = s.result.text != null
                    val sourceLabel = when (s.result.inferenceSource) {
                        "IN_CLOUD" -> stringResource(R.string.ai_source_in_cloud)
                        "ON_DEVICE" -> stringResource(R.string.ai_source_on_device)
                        else -> s.result.inferenceSource
                    }
                    Text(
                        text = stringResource(R.string.ai_read_source, sourceLabel, s.result.durationMs.toInt()),
                        color = if (ok) Cyan else ErrorRed,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background((if (ok) Cyan else ErrorRed).copy(alpha = 0.12f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    if (ok) {
                        SelectionContainer {
                            Text(s.result.text!!, color = TextWhite, fontSize = 14.sp, lineHeight = 21.sp)
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(stringResource(R.string.deep_report_disclaimer), color = TextMuted, fontSize = 12.sp, lineHeight = 17.sp)
                    } else {
                        Text(
                            stringResource(R.string.ai_read_error, s.result.errorMessage ?: ""),
                            color = ErrorRed,
                            fontSize = 13.sp
                        )
                    }
                }
                else -> Unit
            }
        }
    }
}
