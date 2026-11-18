package com.mos.nira.features.ai

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mos.nira.R
import com.mos.nira.core.ai.ReportExplanation
import com.mos.nira.features.artwork.domain.PartAResult
import com.mos.nira.features.artwork.domain.PartBResult

private val CardBg = Color(0xFF171F35)
private val InnerBg = Color(0xFF0F1526)
private val Violet = Color(0xFFB3A8FF)
private val Cyan = Color(0xFF55D6E8)
private val TextWhite = Color(0xFFF5F6FF)
private val TextMuted = Color(0xFFA8B4CE)
private val Amber = Color(0xFFFFC566)
private val ErrorRed = Color(0xFFFF8A80)

/**
 * "Why this recommendation": the AI explains the engine's recommendation in 2-3 sentences, with
 * the real source badge and time. The engine data stays in a card under the text. If the answer
 * has a number that is not in the engine data, the text is not shown.
 */
@Composable
fun ReportExplanationCard(
    artworkKey: ArtworkKey?,
    partA: PartAResult?,
    partB: PartBResult?,
    modifier: Modifier = Modifier,
    viewModel: ReportExplanationViewModel = viewModel()
) {
    if (partB == null || partB.strategies.none { it.isRecommended }) return
    val engineFacts = remember(partA, partB) { ReportExplanation.engineFacts(partA, partB) }
    val requestKey = "$artworkKey|${engineFacts.hashCode()}"
    LaunchedEffect(requestKey) { viewModel.explain(requestKey, engineFacts, partB) }
    val state by viewModel.state.collectAsState()

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                stringResource(R.string.explain_title).uppercase(),
                color = Violet, fontSize = 11.sp, letterSpacing = 1.5.sp, fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(stringResource(R.string.explain_hint), color = TextMuted, fontSize = 12.sp, lineHeight = 17.sp)
            Spacer(modifier = Modifier.height(10.dp))

            var facts: List<String> = engineFacts
            when (val s = state) {
                is ReportExplanationState.Running, ReportExplanationState.Idle ->
                    Text(stringResource(R.string.explain_running), color = TextMuted, fontSize = 13.sp)
                is ReportExplanationState.Blocked -> {
                    facts = s.facts
                    Text(stringResource(R.string.explain_blocked), color = TextMuted, fontSize = 13.sp, lineHeight = 18.sp)
                }
                is ReportExplanationState.Done -> {
                    facts = s.facts
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
                    when {
                        !ok -> Text(stringResource(R.string.ai_read_error, s.result.errorMessage ?: ""), color = ErrorRed, fontSize = 13.sp)
                        s.unverified.isNotEmpty() -> Text(
                            stringResource(
                                R.string.explain_unverified,
                                s.unverified.joinToString(", ") { n -> if (n % 1.0 == 0.0) n.toLong().toString() else n.toString() }
                            ),
                            color = Amber, fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Bold
                        )
                        else -> SelectionContainer {
                            Text(s.result.text!!, color = TextWhite, fontSize = 14.sp, lineHeight = 21.sp)
                        }
                    }
                }
            }

            // Engine data: always shown, under the text.
            Spacer(modifier = Modifier.height(12.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(InnerBg)
                    .padding(12.dp)
            ) {
                Text(
                    stringResource(R.string.explain_data_title).uppercase(),
                    color = Cyan, fontSize = 10.sp, letterSpacing = 1.2.sp, fontWeight = FontWeight.Bold
                )
                facts.forEach { line ->
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(line, color = TextMuted, fontSize = 12.sp, lineHeight = 16.sp)
                }
            }
        }
    }
}
