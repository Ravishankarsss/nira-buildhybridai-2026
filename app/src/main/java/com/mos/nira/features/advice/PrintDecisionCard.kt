package com.mos.nira.features.advice

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mos.nira.R
import com.mos.nira.core.advice.AdviceLevel
import com.mos.nira.core.advice.BaseSource
import com.mos.nira.core.advice.BriefAnswers
import com.mos.nira.core.advice.BriefRules
import com.mos.nira.core.advice.DecisionCard
import com.mos.nira.core.advice.Feel
import com.mos.nira.core.advice.Fibre
import com.mos.nira.core.advice.MethodChoice
import com.mos.nira.core.advice.PlannedScreen
import com.mos.nira.core.advice.PrintDecision
import com.mos.nira.core.advice.QuantityBand
import com.mos.nira.core.advice.QuantityRules
import com.mos.nira.core.advice.TechniqueAdvisor
import com.mos.nira.core.image.ColorConverter
import com.mos.nira.core.image.GarmentPreview
import com.mos.nira.features.ai.ArtworkKey
import com.mos.nira.features.ai.activityViewModelStoreOwner
import com.mos.nira.features.artwork.data.GarmentPreviewRules
import com.mos.nira.features.artwork.domain.PartBResult
import com.mos.nira.features.artwork.domain.PartCResult
import com.mos.nira.features.artwork.domain.PartDResult

private val DCardBg = Color(0xFF171F35)
private val DInner = Color(0xFF0F1526)
private val DViolet = Color(0xFFB3A8FF)
private val DCyan = Color(0xFF55D6E8)
private val DWhite = Color(0xFFF5F6FF)
private val DMuted = Color(0xFFA8B4CE)
private val DGreen = Color(0xFF7BE0A0)
private val DAmber = Color(0xFFFFC870)

private fun hexColor(hex: String) = Color(android.graphics.Color.parseColor(hex))

/** The engine's name for its highlight-white screen (SeparationStrategyEngine.generatePartC). */
private const val HIGHLIGHT_WHITE = "Highlight White"

/** The engine's screens (Parts C and D) in the form the decision card needs. */
fun plannedScreens(partC: PartCResult?, partD: PartDResult?): List<PlannedScreen> =
    partC?.channels.orEmpty().filter { true }.let { chans ->
        // CMYK process screens: all four in CMYK, the first four colour screens in the mixed option.
        val colourChans = chans.filter { !it.isBaseWhite && it.inkName != HIGHLIGHT_WHITE }
        val processNames = when (partC?.strategyType) {
            com.mos.nira.features.artwork.domain.SeparationStrategyType.CMYK_PROCESS,
            com.mos.nira.features.artwork.domain.SeparationStrategyType.MIXED_CMYK_SPOT -> colourChans.take(4).map { it.channelIndex }.toSet()
            else -> emptySet()
        }
        chans.map { ch -> ch to (ch.channelIndex in processNames) }
    }.map { (ch, process) ->
        val ink = partD?.channels?.firstOrNull { it.channelIndex == ch.channelIndex }?.takeIf { it.inkAvailable }
        PlannedScreen(
            name = ch.inkName,
            hex = ch.inkHex,
            isBaseWhite = ch.isBaseWhite,
            mesh = ch.recommendedMesh,
            meshConfidence = ch.meshConfidence,
            gramsPerGarment = ink?.inkGramsPerGarment,
            gramsMin = ink?.inkGramsPerGarmentMin,
            gramsMax = ink?.inkGramsPerGarmentMax,
            isProcess = process,
            isHighlightWhite = ch.inkName == HIGHLIGHT_WHITE
        )
    }

/**
 * "Ask like a master": four tap questions, then one decision card. The engine measures, the rules
 * files decide; every line shows its source and confidence. No AI is used here.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PrintDecisionCard(
    artworkKey: ArtworkKey?,
    garmentHex: String?,
    garmentChips: GarmentPreviewRules?,
    measuredFabricHex: String?,
    isPhoto: Boolean,
    fabric: String?,
    quantity: Int?,
    feel: Feel?,
    briefRules: BriefRules?,
    quantityRules: QuantityRules?,
    partB: PartBResult?,
    partC: PartCResult?,
    partD: PartDResult?,
    isComputing: Boolean,
    contrastWarnings: List<GarmentPreview.ContrastWarning>,
    onGarment: (String) -> Unit,
    onFabric: (String?) -> Unit,
    onQuantity: (Int?) -> Unit,
    onFeel: (Feel?) -> Unit,
    onLoadRules: () -> Unit,
    // Order cost and Cost-Down (factory rates on this phone; print_rules.json cost_down).
    costDownRules: com.mos.nira.core.commercial.CostDownRules? = null,
    printVsGroundRules: com.mos.nira.core.advice.PrintVsGroundRules? = null,
    costDownBefore: android.graphics.Bitmap? = null,
    costDownAfter: android.graphics.Bitmap? = null,
    onCostDownPreview: (fromHex: String?, intoHex: String?, afterGarmentHex: String?) -> Unit = { _, _, _ -> },
    onCloseCostDownPreview: () -> Unit = {},
    /** The garment preview to put on the Decision Sheet (with or without the white base). */
    sheetPreview: android.graphics.Bitmap? = null,
    choicesViewModel: PrintMethodChoicesViewModel = viewModel(viewModelStoreOwner = activityViewModelStoreOwner())
) {
    LaunchedEffect(Unit) { onLoadRules() }
    val adviceRules = rememberAdviceRules()
    val allChoices by choicesViewModel.choices.collectAsState()
    val choices = allChoices.takeIf { it.key == artworkKey } ?: PrintMethodChoices(artworkKey)
    val fibre = choices.fibre

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = DCardBg)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(stringResource(R.string.brief_title).uppercase(), color = DViolet, fontSize = 11.sp, letterSpacing = 1.5.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(stringResource(R.string.brief_hint), color = DMuted, fontSize = 12.sp, lineHeight = 17.sp)

            // 1. Garment colour
            Question(stringResource(R.string.brief_q_garment))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (isPhoto && measuredFabricHex != null) {
                    Chip(stringResource(R.string.brief_garment_photo), garmentHex.equals(measuredFabricHex, true), measuredFabricHex) { onGarment(measuredFabricHex) }
                }
                garmentChips?.chips?.forEach { (name, hex) ->
                    val label = when (name) {
                        "white" -> stringResource(R.string.preview_chip_white)
                        "navy" -> stringResource(R.string.preview_chip_navy)
                        "black" -> stringResource(R.string.preview_chip_black)
                        else -> name
                    }
                    Chip(label, garmentHex.equals(hex, true), hex) { onGarment(hex) }
                }
                if (garmentHex != null && garmentChips?.chips?.none { it.second.equals(garmentHex, true) } == true &&
                    !garmentHex.equals(measuredFabricHex, true)
                ) {
                    Chip(com.mos.nira.features.artwork.presentation.colourName(garmentHex), true, garmentHex) {}
                }
            }

            // 2. Fibre and fabric
            Question(stringResource(R.string.brief_q_fibre))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Fibre.values().forEach { f ->
                    Chip(fibreLabel(f), fibre == f) { choicesViewModel.update(artworkKey) { c -> c.copy(fibre = if (fibre == f) null else f) } }
                }
            }
            Spacer(Modifier.height(4.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                briefRules?.fabrics?.forEach { f ->
                    Chip(fabricLabel(f), fabric == f) { onFabric(if (fabric == f) null else f) }
                }
            }

            // 3. Quantity
            Question(stringResource(R.string.brief_q_quantity))
            var qtyText by remember(quantity) { mutableStateOf(quantity?.toString() ?: "") }
            OutlinedTextField(
                value = qtyText,
                onValueChange = { t ->
                    val digits = t.filter { it.isDigit() }.take(7)
                    qtyText = digits
                    onQuantity(digits.toIntOrNull()?.takeIf { it > 0 })
                },
                singleLine = true,
                placeholder = { Text(stringResource(R.string.brief_quantity_hint), fontSize = 13.sp) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.width(160.dp)
            )

            // 4. Feel
            Question(stringResource(R.string.brief_q_feel))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Feel.values().forEach { f -> Chip(feelLabel(f), feel == f) { onFeel(if (feel == f) null else f) } }
            }

            // ---------- The decision card ----------
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.card_title).uppercase(), color = DCyan, fontSize = 11.sp, letterSpacing = 1.5.sp, fontWeight = FontWeight.Bold)

            val ar = adviceRules
            if (ar == null || briefRules == null) {
                Small(stringResource(R.string.advice_loading))
                return@Column
            }
            val tone = garmentHex?.let {
                val rgb = ColorConverter.hexToRgb(it)
                val lab = ColorConverter.rgbToLab(rgb.r, rgb.g, rgb.b)
                TechniqueAdvisor.garmentTone(lab.l, lab.a, lab.b, ar.lightnessLimit, ar.chromaLimit)
            }
            val answers = BriefAnswers(garmentHex, fibre, fabric, quantity, feel)
            val screens = plannedScreens(partC, partD)
            val selected = partB?.strategies?.firstOrNull { it.strategyType == partB.selectedStrategyType }
            val card = remember(answers, tone, choices.artwork, ar, briefRules, quantityRules, screens, selected) {
                PrintDecision.decide(
                    answers = answers,
                    garmentTone = tone,
                    advice = TechniqueAdvisor.advise(ar.techniques, fibre, choices.artwork, tone),
                    briefRules = briefRules,
                    quantityRules = quantityRules,
                    separationName = selected?.name,
                    engineScreens = screens
                )
            }
            if (card.unanswered > 0) Small(stringResource(R.string.card_questions_left, card.unanswered))

            // Print vs fabric (print_vs_ground): on a dark or coloured garment, per print colour.
            val groundChecks = remember(garmentHex, screens, printVsGroundRules, tone) {
                val g = garmentHex
                val r = printVsGroundRules
                if (g == null || r == null || tone != com.mos.nira.core.advice.GarmentTone.DARK_OR_COLOURED) emptyList()
                else com.mos.nira.core.advice.PrintVsGround.check(screens.filter { it.isArtworkColour }.map { it.hex }, g, r)
            }
            if (groundChecks.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(DInner).padding(10.dp)) {
                    Text(stringResource(R.string.pvg_title), color = DMuted, fontSize = 11.sp)
                    groundChecks.forEach { c ->
                        val name = com.mos.nira.features.artwork.presentation.colourName(c.inkHex)
                        Text(
                            "• " + stringResource(
                                when (c.verdict) {
                                    com.mos.nira.core.advice.GroundVerdict.NEEDS_DISCHARGE_OR_BASE -> R.string.pvg_lighter
                                    com.mos.nira.core.advice.GroundVerdict.PRINTS_ON_TOP -> R.string.pvg_darker
                                    com.mos.nira.core.advice.GroundVerdict.CLOSE_TO_GROUND -> R.string.pvg_close
                                },
                                name
                            ),
                            color = if (c.verdict == com.mos.nira.core.advice.GroundVerdict.PRINTS_ON_TOP) DWhite else DAmber,
                            fontSize = 12.sp, lineHeight = 17.sp
                        )
                    }
                    Text(
                        stringResource(R.string.src_rules) + "  •  " + stringResource(R.string.card_confidence, printVsGroundRules!!.confidence) +
                            "  •  " + stringResource(R.string.pvg_source),
                        color = DMuted, fontSize = 10.sp
                    )
                }
            }

            // Contrast first (garment_preview)
            contrastWarnings.forEach { w ->
                Spacer(Modifier.height(6.dp))
                Text(
                    "⚠ " + stringResource(R.string.card_contrast_risk, com.mos.nira.features.artwork.presentation.colourName(w.inkHex), w.deltaL),
                    color = DAmber, fontSize = 12.sp, lineHeight = 17.sp
                )
            }

            val best = card.best
            if (best == null && (garmentHex == null || fibre == null)) {
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.card_need_basics), color = DAmber, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            } else if (best == null) {
                Line(stringResource(R.string.card_method), stringResource(R.string.card_none), stringResource(R.string.src_rules), null, false)
            } else {
                MethodLine(stringResource(R.string.card_method), best, emphasise = true)
                card.second?.let { MethodLine(stringResource(R.string.card_second), it, emphasise = false) }
                card.smallRun?.let { MethodLine(stringResource(R.string.card_small_run), it, emphasise = false) }
            }

            // Quantity line (quantity_thresholds)
            val q = card.answers.quantity
            val qText = when (card.quantityBand) {
                QuantityBand.NOT_ANSWERED -> stringResource(R.string.card_quantity_not_answered)
                QuantityBand.NOT_SET -> stringResource(R.string.card_quantity_not_set)
                QuantityBand.BUYER_ORDER -> stringResource(R.string.card_quantity_buyer, q ?: 0)
                QuantityBand.DIGITAL -> stringResource(R.string.card_quantity_digital, q ?: 0, quantityRules?.digitalBelowPieces ?: 0)
                QuantityBand.SCREEN -> stringResource(R.string.card_quantity_screen, q ?: 0, quantityRules?.screenFromPieces ?: 0)
                QuantityBand.BETWEEN -> stringResource(R.string.card_quantity_between, q ?: 0, quantityRules?.digitalBelowPieces ?: 0, quantityRules?.screenFromPieces ?: 0)
            }
            Line(stringResource(R.string.card_quantity), qText, stringResource(R.string.src_rules), quantityRules?.confidence, quantityRules?.verifiedByRavi == true)

            if (best != null) {
                if (card.baseSource == BaseSource.DIGITAL) {
                    val n = best.advice.screens
                    val screensText = if (n != null && n > 0) stringResource(R.string.card_screens_value, n, best.advice.screensDetail ?: "")
                    else stringResource(R.string.card_screens_digital)
                    Line(stringResource(R.string.card_screens), screensText, stringResource(R.string.src_rules), best.advice.confidence, best.advice.verifiedByRavi)
                    if ("discharge" in best.advice.id) {
                        Line(
                            stringResource(R.string.card_base), stringResource(R.string.card_base_discharge),
                            stringResource(R.string.src_rules), best.advice.confidence, best.advice.verifiedByRavi
                        )
                    }
                    if (best.advice.processSteps.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(R.string.card_process), color = DMuted, fontSize = 11.sp)
                        best.advice.processSteps.forEachIndexed { i, step ->
                            Text("${i + 1}. $step", color = DWhite, fontSize = 12.sp, lineHeight = 17.sp)
                        }
                    }
                } else if (isComputing && screens.isEmpty()) {
                    Small(stringResource(R.string.card_working))
                } else {
                    Line(
                        stringResource(R.string.card_separation), card.separationName ?: "—",
                        stringResource(R.string.src_engine), briefRules.separationConfidence, false
                    )
                    Line(
                        stringResource(R.string.card_screens),
                        stringResource(
                            R.string.card_screens_value, card.screens.size,
                            card.screens.map { com.mos.nira.features.artwork.presentation.inkLabel(it.name) }.joinToString(", ")
                        ),
                        stringResource(R.string.src_engine), briefRules.separationConfidence, false
                    )
                    Line(
                        stringResource(R.string.card_base),
                        when {
                            card.baseSource == BaseSource.DISCHARGE -> stringResource(R.string.card_base_discharge)
                            card.baseNeeded == true -> stringResource(R.string.card_base_yes)
                            else -> stringResource(R.string.card_base_no)
                        },
                        stringResource(if (card.baseSource == BaseSource.DISCHARGE) R.string.src_rules else R.string.src_engine),
                        if (card.baseSource == BaseSource.DISCHARGE) best.advice.confidence else briefRules.baseConfidence,
                        card.baseSource == BaseSource.DISCHARGE && best.advice.verifiedByRavi
                    )
                    // Mesh and ink per screen (screen_type_mesh_rule, ink_consumption)
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.card_mesh_ink), color = DMuted, fontSize = 11.sp)
                    card.screens.forEach { s -> ScreenRow(s) }
                    Line(
                        stringResource(R.string.card_ink),
                        card.gramsPerGarment?.let {
                            stringResource(R.string.card_ink_value, fmt(it), fmt(card.gramsMin ?: it), fmt(card.gramsMax ?: it)) +
                                (q?.let { pieces ->
                                    "\n" + stringResource(
                                        R.string.card_ink_order, pieces,
                                        fmt0(it * pieces), fmt0((card.gramsMin ?: it) * pieces), fmt0((card.gramsMax ?: it) * pieces)
                                    )
                                } ?: "")
                        } ?: stringResource(R.string.card_ink_none),
                        stringResource(R.string.src_rules), null, false
                    )
                    if (card.inksWithoutGrams.isNotEmpty()) {
                        Small(stringResource(R.string.card_ink_missing, card.inksWithoutGrams.joinToString(", ")))
                    }
                }

                // The card's facts in plain English (for the AI explanations and the share sheet).
                val fibreName = fibre?.let {
                    when (it) {
                        Fibre.COTTON_POLY_BLEND -> "cotton/polyester blend"
                        else -> it.name.lowercase()
                    }
                }
                val facts = remember(card, contrastWarnings, quantityRules, fibreName, groundChecks) {
                    com.mos.nira.core.ai.CardExplanation.facts(card, contrastWarnings, quantityRules, fibreName, groundChecks)
                }

                // Order cost and Cost-Down: deterministic, from the factory's own rates.
                var chosenOption by remember { mutableStateOf<com.mos.nira.core.commercial.CostDownOption?>(null) }
                var sheetOpen by remember { mutableStateOf(false) }
                val rates = com.mos.nira.features.commercial.rememberFactoryRates()
                val cost = remember(card, screens, rates) { com.mos.nira.core.commercial.PrintCost.forCard(card, screens, rates) }
                val options = remember(card, screens, rates, costDownRules) {
                    com.mos.nira.core.commercial.PrintCost.costDown(card, screens, rates, costDownRules)
                }
                com.mos.nira.features.commercial.OrderCostSection(
                    cost = cost,
                    options = options,
                    rates = rates,
                    cardFacts = facts,
                    costDownBefore = costDownBefore,
                    costDownAfter = costDownAfter,
                    onPreviewOption = { o ->
                        when (o.kind) {
                            com.mos.nira.core.commercial.CostDownKind.MERGE_COLOURS -> onCostDownPreview(o.fromHex, o.intoHex, null)
                            com.mos.nira.core.commercial.CostDownKind.LIGHT_GARMENT -> onCostDownPreview(null, null, garmentChips?.chips?.firstOrNull { it.first == "white" }?.second)
                            else -> Unit
                        }
                    },
                    onClosePreview = onCloseCostDownPreview,
                    onOptionSelected = { chosenOption = it },
                    extraActions = {
                        androidx.compose.material3.OutlinedButton(onClick = { sheetOpen = true }, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.sheet_button), fontSize = 12.sp, color = DCyan)
                        }
                    }
                )
                if (sheetOpen) {
                    com.mos.nira.features.commercial.DecisionSheetDialog(
                        data = com.mos.nira.features.commercial.SheetData(
                            card = card,
                            cost = cost,
                            rates = rates,
                            chosenOption = chosenOption?.takeIf { it in options },
                            contrast = contrastWarnings,
                            preview = sheetPreview
                        ),
                        onDismiss = { sheetOpen = false }
                    )
                }

                // Risks from the rules file for the chosen method
                if (best.advice.risks.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Text(stringResource(R.string.card_risks), color = DMuted, fontSize = 11.sp)
                    best.advice.risks.forEach { Text("• $it", color = DWhite, fontSize = 12.sp, lineHeight = 17.sp) }
                }

                // "Explain this card": AI explains the card's facts (text only, on-device preferred).
                com.mos.nira.features.ai.CardExplanationSection(facts, best.advice.name)
            }
            Spacer(Modifier.height(10.dp))
            Small(stringResource(R.string.card_disclaimer))
        }
    }
}

private fun fmt(v: Double) = String.format("%.1f", v)
private fun fmt0(v: Double) = String.format("%.0f", v)

@Composable
private fun MethodLine(label: String, choice: MethodChoice, emphasise: Boolean) {
    val a = choice.advice
    val level = when (a.level) {
        AdviceLevel.RECOMMENDED -> stringResource(R.string.advice_recommended)
        AdviceLevel.POSSIBLE -> stringResource(R.string.advice_possible)
        AdviceLevel.NOT_SUITABLE -> stringResource(R.string.advice_not_suitable)
    }
    val feel = choice.feelTag?.let { "  •  " + stringResource(R.string.card_feel_of, feelTagLabel(it)) } ?: ""
    Line(label, a.name + "  ($level)" + feel, stringResource(R.string.src_rules), a.confidence, a.verifiedByRavi, emphasise)
}

@Composable
private fun ScreenRow(s: PlannedScreen) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
        Box(Modifier.size(14.dp).clip(RoundedCornerShape(3.dp)).background(hexColor(s.hex)).border(1.dp, DMuted, RoundedCornerShape(3.dp)))
        Spacer(Modifier.width(8.dp))
        val grams = s.gramsPerGarment?.let { stringResource(R.string.card_grams, fmt(it)) } ?: stringResource(R.string.card_grams_none)
        Text(
            "${com.mos.nira.features.artwork.presentation.inkLabel(s.name)}: ${s.mesh}" + (s.meshConfidence?.let { " ($it)" } ?: "") + "  •  $grams",
            color = DWhite, fontSize = 12.sp, lineHeight = 17.sp
        )
    }
}

/** One card line: label, value, source, and confidence / floor check. */
@Composable
private fun Line(label: String, value: String, source: String, confidence: String?, verified: Boolean, emphasise: Boolean = false) {
    Spacer(Modifier.height(8.dp))
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(DInner).padding(10.dp)) {
        Text(label, color = DMuted, fontSize = 11.sp)
        Text(value, color = DWhite, fontSize = if (emphasise) 15.sp else 13.sp, fontWeight = if (emphasise) FontWeight.Bold else FontWeight.Normal, lineHeight = 18.sp)
        val conf = confidence?.let { stringResource(R.string.card_confidence, it) } ?: stringResource(R.string.advice_confidence_none)
        Text(
            "$source  •  $conf  •  " + stringResource(if (verified) R.string.card_verified else R.string.card_not_verified),
            color = if (verified) DGreen else DMuted, fontSize = 10.sp
        )
    }
}

@Composable
private fun Question(text: String) {
    Spacer(Modifier.height(12.dp))
    Text(text, color = DWhite, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(4.dp))
}

@Composable
private fun Small(text: String) {
    Spacer(Modifier.height(4.dp))
    Text(text, color = DMuted, fontSize = 11.sp, lineHeight = 15.sp)
}

@Composable
private fun Chip(text: String, selected: Boolean, swatchHex: String? = null, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(text, fontSize = 12.sp, maxLines = 1) },
        leadingIcon = swatchHex?.let { hex ->
            { Box(Modifier.size(12.dp).clip(RoundedCornerShape(3.dp)).background(hexColor(hex)).border(1.dp, DMuted, RoundedCornerShape(3.dp))) }
        },
        colors = FilterChipDefaults.filterChipColors(
            containerColor = DInner,
            labelColor = DMuted,
            selectedContainerColor = DCyan.copy(alpha = 0.2f),
            selectedLabelColor = DCyan
        )
    )
}

@Composable
private fun fabricLabel(id: String): String = when (id) {
    "single_jersey" -> stringResource(R.string.fabric_single_jersey)
    "pique" -> stringResource(R.string.fabric_pique)
    "fleece" -> stringResource(R.string.fabric_fleece)
    "interlock" -> stringResource(R.string.fabric_interlock)
    "other" -> stringResource(R.string.fabric_other)
    else -> id
}

@Composable
private fun feelLabel(f: Feel): String = stringResource(
    when (f) {
        Feel.SOFT -> R.string.feel_soft
        Feel.RAISED -> R.string.feel_raised
        Feel.ANY -> R.string.feel_any
    }
)

@Composable
private fun feelTagLabel(tag: String): String = when (tag) {
    "soft" -> stringResource(R.string.feel_soft)
    "raised" -> stringResource(R.string.feel_raised)
    else -> tag
}
