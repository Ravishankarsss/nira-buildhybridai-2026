package com.mos.nira.features.advice

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mos.nira.R
import com.mos.nira.core.advice.AdviceLevel
import com.mos.nira.core.advice.AdviceReason
import com.mos.nira.core.advice.ArtworkKind
import com.mos.nira.core.advice.Fibre
import com.mos.nira.core.advice.GarmentTone
import com.mos.nira.core.advice.TechniqueAdvice
import com.mos.nira.core.advice.TechniqueAdvisor
import com.mos.nira.core.advice.TechniqueRule
import com.mos.nira.core.ai.ArtworkType
import com.mos.nira.features.ai.ArtworkAiReadState
import com.mos.nira.features.ai.ArtworkAiReadViewModel
import com.mos.nira.features.artwork.data.PrintRulesRepository
import com.mos.nira.features.artwork.domain.DominantColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val CardBg = Color(0xFF171F35)
private val InnerBg = Color(0xFF0F1526)
private val Violet = Color(0xFFB3A8FF)
private val Cyan = Color(0xFF55D6E8)
private val TextWhite = Color(0xFFF5F6FF)
private val TextMuted = Color(0xFFA8B4CE)
private val Green = Color(0xFF7BE0A0)
private val Amber = Color(0xFFFFC870)
private val Red = Color(0xFFFF8A80)

private data class RulesData(val techniques: List<TechniqueRule>, val lightnessLimit: Double, val chromaLimit: Double)

/**
 * Print method advice from print_techniques.json. The user chooses the fabric; artwork type is
 * set from the AI read when there is one, and the garment from the measured colour. Every
 * choice can be changed by the user.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PrintMethodAdviceCard(
    garmentColor: DominantColor?,
    modifier: Modifier = Modifier,
    aiReadViewModel: ArtworkAiReadViewModel = viewModel()
) {
    val context = LocalContext.current
    val rules by produceState<RulesData?>(initialValue = null) {
        value = withContext(Dispatchers.IO) {
            val json = context.assets.open("print_techniques.json").bufferedReader().use { it.readText() }
            val decision = PrintRulesRepository(context).getSeparationDecisionRules()
            RulesData(TechniqueAdvisor.parseRules(json), decision.darkGarmentThresholdL, decision.garmentChromaThreshold)
        }
    }

    var fibreName by rememberSaveable { mutableStringOrNull() }
    var artworkName by rememberSaveable { mutableStringOrNull() }
    var artworkFromAi by rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    var garmentName by rememberSaveable { mutableStringOrNull() }
    var garmentFromMeasure by rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }

    // Artwork type from the AI read, unless the user already chose one.
    val aiState by aiReadViewModel.state.collectAsState()
    LaunchedEffect(aiState) {
        val read = (aiState as? ArtworkAiReadState.Done)?.read ?: return@LaunchedEffect
        if (artworkName == null || artworkFromAi) {
            when (read.artworkType) {
                ArtworkType.FLAT -> { artworkName = ArtworkKind.FLAT.name; artworkFromAi = true }
                ArtworkType.TONAL -> { artworkName = ArtworkKind.TONAL.name; artworkFromAi = true }
                ArtworkType.UNKNOWN -> Unit
            }
        }
    }

    // Garment tone from the measured garment colour, unless the user already chose one.
    LaunchedEffect(rules, garmentColor) {
        val r = rules ?: return@LaunchedEffect
        val g = garmentColor ?: return@LaunchedEffect
        if (garmentName == null || garmentFromMeasure) {
            garmentName = TechniqueAdvisor.garmentTone(g.labL, g.labA, g.labB, r.lightnessLimit, r.chromaLimit).name
            garmentFromMeasure = true
        }
    }

    val fibre = fibreName?.let { Fibre.valueOf(it) }
    val artwork = artworkName?.let { ArtworkKind.valueOf(it) }
    val garment = garmentName?.let { GarmentTone.valueOf(it) }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.advice_title).uppercase(),
                color = Violet,
                fontSize = 11.sp,
                letterSpacing = 1.5.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(stringResource(R.string.advice_hint), color = TextMuted, fontSize = 12.sp)

            Label(stringResource(R.string.advice_fabric))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Fibre.values().forEach { f ->
                    Chip(fibreLabel(f), selected = fibre == f) { fibreName = if (fibre == f) null else f.name }
                }
            }

            Label(stringResource(R.string.advice_artwork))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip(stringResource(R.string.advice_artwork_flat), artwork == ArtworkKind.FLAT) {
                    artworkName = ArtworkKind.FLAT.name; artworkFromAi = false
                }
                Chip(stringResource(R.string.advice_artwork_tonal), artwork == ArtworkKind.TONAL) {
                    artworkName = ArtworkKind.TONAL.name; artworkFromAi = false
                }
            }
            if (artworkFromAi) Small(stringResource(R.string.advice_from_ai))

            Label(stringResource(R.string.advice_garment))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip(stringResource(R.string.advice_garment_light), garment == GarmentTone.LIGHT) {
                    garmentName = GarmentTone.LIGHT.name; garmentFromMeasure = false
                }
                Chip(stringResource(R.string.advice_garment_dark), garment == GarmentTone.DARK_OR_COLOURED) {
                    garmentName = GarmentTone.DARK_OR_COLOURED.name; garmentFromMeasure = false
                }
            }
            if (garmentFromMeasure) Small(stringResource(R.string.advice_from_measured))

            val r = rules
            if (r == null) {
                Small(stringResource(R.string.advice_loading))
            } else {
                val advice = remember(r, fibre, artwork, garment) { TechniqueAdvisor.advise(r.techniques, fibre, artwork, garment) }
                AdviceLevel.values().forEach { level ->
                    val items = advice.filter { it.level == level }
                    if (items.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = levelLabel(level),
                            color = levelColor(level),
                            fontSize = 11.sp,
                            letterSpacing = 1.2.sp,
                            fontWeight = FontWeight.Bold
                        )
                        items.forEach { TechniqueItem(it) }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Small(stringResource(R.string.advice_disclaimer))
        }
    }
}

private fun mutableStringOrNull() = androidx.compose.runtime.mutableStateOf<String?>(null)

@Composable
private fun TechniqueItem(advice: TechniqueAdvice) {
    Spacer(modifier = Modifier.height(8.dp))
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(InnerBg)
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = advice.name,
                color = TextWhite,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            if (!advice.verifiedByRavi) {
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.advice_not_verified),
                    color = Amber,
                    fontSize = 10.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Amber.copy(alpha = 0.12f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
        if (advice.whatItIs.isNotBlank() && advice.level != AdviceLevel.NOT_SUITABLE) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(advice.whatItIs, color = TextMuted, fontSize = 12.sp, lineHeight = 17.sp)
        }

        // For a ruled-out method only the reasons that rule it out are shown.
        val shown = if (advice.level == AdviceLevel.NOT_SUITABLE) {
            advice.reasons.filter { it.severity == com.mos.nira.core.advice.Severity.BLOCK }
        } else advice.reasons
        Spacer(modifier = Modifier.height(6.dp))
        shown.forEach { reason ->
            Text(
                text = "• " + reasonText(reason),
                color = when (reason.severity) {
                    com.mos.nira.core.advice.Severity.OK -> TextWhite
                    com.mos.nira.core.advice.Severity.BLOCK -> Red
                    else -> Amber
                },
                fontSize = 12.sp,
                lineHeight = 17.sp
            )
        }

        if (advice.level != AdviceLevel.NOT_SUITABLE && advice.risks.isNotEmpty()) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(stringResource(R.string.advice_risks), color = TextMuted, fontSize = 11.sp)
            advice.risks.forEach { Text("• $it", color = TextMuted, fontSize = 12.sp, lineHeight = 17.sp) }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = advice.confidence?.let { stringResource(R.string.advice_confidence, it) }
                ?: stringResource(R.string.advice_confidence_none),
            color = TextMuted,
            fontSize = 11.sp
        )
    }
}

@Composable
private fun reasonText(reason: AdviceReason): String = when (reason) {
    is AdviceReason.FibreSuitable -> stringResource(R.string.reason_fibre_suitable, reason.entry)
    is AdviceReason.FibreNotSuitable -> stringResource(R.string.reason_fibre_not_suitable, reason.entry)
    is AdviceReason.FibreCaution -> stringResource(R.string.reason_fibre_caution, reason.note)
    is AdviceReason.FibreNotListed -> stringResource(R.string.reason_fibre_not_listed, fibreLabel(reason.fibre))
    AdviceReason.FibreNoData -> stringResource(R.string.reason_fibre_no_data)
    AdviceReason.FibreNotChosen -> stringResource(R.string.reason_fibre_not_chosen)
    AdviceReason.ArtworkSolidOnly -> stringResource(R.string.reason_artwork_solid_only)
    AdviceReason.ArtworkSolidPreferred -> stringResource(R.string.reason_artwork_solid_preferred)
    AdviceReason.ArtworkFits -> stringResource(R.string.reason_artwork_fits)
    AdviceReason.ArtworkNotChosen -> stringResource(R.string.reason_artwork_not_chosen)
    AdviceReason.GarmentLightOnly -> stringResource(R.string.reason_garment_light_only)
    AdviceReason.GarmentDarkOnly -> stringResource(R.string.reason_garment_dark_only)
    is AdviceReason.GarmentCaution -> stringResource(R.string.reason_garment_caution, reason.note)
    is AdviceReason.GarmentNote -> stringResource(R.string.reason_garment_note, reason.note)
    AdviceReason.GarmentNotChosen -> stringResource(R.string.reason_garment_not_chosen)
}

@Composable
private fun fibreLabel(f: Fibre): String = stringResource(
    when (f) {
        Fibre.COTTON -> R.string.fibre_cotton
        Fibre.POLYESTER -> R.string.fibre_polyester
        Fibre.COTTON_POLY_BLEND -> R.string.fibre_blend
        Fibre.LINEN -> R.string.fibre_linen
        Fibre.NYLON -> R.string.fibre_nylon
    }
)

@Composable
private fun levelLabel(level: AdviceLevel): String = stringResource(
    when (level) {
        AdviceLevel.RECOMMENDED -> R.string.advice_recommended
        AdviceLevel.POSSIBLE -> R.string.advice_possible
        AdviceLevel.NOT_SUITABLE -> R.string.advice_not_suitable
    }
)

private fun levelColor(level: AdviceLevel) = when (level) {
    AdviceLevel.RECOMMENDED -> Green
    AdviceLevel.POSSIBLE -> Amber
    AdviceLevel.NOT_SUITABLE -> Red
}

@Composable
private fun Label(text: String) {
    Spacer(modifier = Modifier.height(12.dp))
    Text(text, color = TextMuted, fontSize = 12.sp)
    Spacer(modifier = Modifier.height(4.dp))
}

@Composable
private fun Small(text: String) {
    Spacer(modifier = Modifier.height(4.dp))
    Text(text, color = TextMuted, fontSize = 11.sp, lineHeight = 15.sp)
}

@Composable
private fun Chip(text: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(text, fontSize = 12.sp) },
        colors = FilterChipDefaults.filterChipColors(
            containerColor = InnerBg,
            labelColor = TextMuted,
            selectedContainerColor = Cyan.copy(alpha = 0.2f),
            selectedLabelColor = Cyan
        )
    )
}
