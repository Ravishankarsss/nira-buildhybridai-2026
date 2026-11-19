package com.mos.nira.features.ai

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mos.nira.R

private val SCyan = Color(0xFF55D6E8)
private val SViolet = Color(0xFFB3A8FF)
private val SWhite = Color(0xFFF5F6FF)
private val SMuted = Color(0xFFA8B4CE)
private val SAmber = Color(0xFFFFC566)
private val SRed = Color(0xFFFF8A80)

/**
 * The "Explain this card" button and answer, under the decision card. The badge shows the
 * inference source the Firebase SDK reported; the answer is hidden if it has a number that is
 * not in the card's facts.
 */
@Composable
fun CardExplanationSection(
    facts: List<String>,
    methodName: String?,
    viewModel: CardExplanationViewModel = viewModel(viewModelStoreOwner = activityViewModelStoreOwner())
) {
    val requestKey = facts.hashCode().toString()
    LaunchedEffect(requestKey) { viewModel.resetIfChanged(requestKey) }
    val state by viewModel.state.collectAsState()

    Spacer(Modifier.height(14.dp))
    Text(stringResource(R.string.card_explain_hint), color = SMuted, fontSize = 11.sp, lineHeight = 15.sp)
    Spacer(Modifier.height(6.dp))
    val running = state is ReportExplanationState.Running
    Button(
        onClick = { viewModel.explain(requestKey, facts, methodName) },
        enabled = !running,
        colors = ButtonDefaults.buttonColors(containerColor = SViolet, contentColor = Color(0xFF0F1526))
    ) {
        Text(stringResource(if (running) R.string.explain_running else R.string.card_explain_button), fontWeight = FontWeight.Bold)
    }

    when (val s = state) {
        is ReportExplanationState.Blocked -> {
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.explain_blocked), color = SMuted, fontSize = 13.sp, lineHeight = 18.sp)
        }
        is ReportExplanationState.Done -> {
            Spacer(Modifier.height(10.dp))
            val ok = s.result.text != null
            val sourceLabel = when (s.result.inferenceSource) {
                "IN_CLOUD" -> stringResource(R.string.ai_source_in_cloud)
                "ON_DEVICE" -> stringResource(R.string.ai_source_on_device)
                else -> s.result.inferenceSource
            }
            Text(
                text = stringResource(R.string.ai_read_source, sourceLabel, s.result.durationMs.toInt()),
                color = if (ok) SCyan else SRed,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background((if (ok) SCyan else SRed).copy(alpha = 0.12f))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
            Spacer(Modifier.height(8.dp))
            Column {
                when {
                    !ok -> Text(aiErrorText(s.result), color = SRed, fontSize = 13.sp)
                    s.unverified.isNotEmpty() -> Text(
                        stringResource(
                            R.string.explain_unverified,
                            s.unverified.joinToString(", ") { n -> if (n % 1.0 == 0.0) n.toLong().toString() else n.toString() }
                        ),
                        color = SAmber, fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Bold
                    )
                    s.colourCode -> Text(
                        stringResource(R.string.card_explain_code_found),
                        color = SAmber, fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Bold
                    )
                    s.missingMethodWords.isNotEmpty() -> Text(
                        stringResource(R.string.card_explain_method_missing, s.missingMethodWords.joinToString(", ")),
                        color = SAmber, fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Bold
                    )
                    else -> SelectionContainer { Text(s.result.text!!, color = SWhite, fontSize = 14.sp, lineHeight = 21.sp) }
                }
            }
        }
        else -> Unit
    }
}
