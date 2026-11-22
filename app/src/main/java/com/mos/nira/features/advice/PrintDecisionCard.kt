package com.mos.nira.features.advice

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
    /** The job's inks as the garment preview has them (measured palette), for Ask NIRA actions. */
    previewInkHexes: List<String> = emptyList(),
    /** Preview-only ink swaps now in place (ink hex -> shown hex). */
    inkSwaps: Map<String, String> = emptyMap(),
    /** Shows an ink in another colour in the preview; null puts it back. */
    onPreviewInk: (fromHex: String, toHex: String?) -> Unit = { _, _ -> },
    /** Which part to draw (the job page's tabs). Everything is still worked out every time. */
    section: CardSection = CardSection.ALL,
    /** Ask NIRA is opened from outside (the floating button on every tab). */
    askOpen: Boolean = false,
    onAskOpenChange: (Boolean) -> Unit = {},
    /** Method, screens and cost for the job page's top bar. */
    onSummary: (JobSummary) -> Unit = {},
    /** The fibre saved with this job (restored when the job is opened again). */
    savedFibre: Fibre? = null,
    /** Every fibre change, so the job can save it. */
    onFibre: (Fibre?) -> Unit = {},
    choicesViewModel: PrintMethodChoicesViewModel = viewModel(viewModelStoreOwner = activityViewModelStoreOwner())
) {
    LaunchedEffect(Unit) { onLoadRules() }
    val adviceRules = rememberAdviceRules()
    val allChoices by choicesViewModel.choices.collectAsState()
    val choices = allChoices.takeIf { it.key == artworkKey } ?: PrintMethodChoices(artworkKey)
    val fibre = choices.fibre
    // Restore the saved fibre once for this artwork, then report changes so the job keeps them.
    var fibreRestored by remember(artworkKey) { mutableStateOf(savedFibre == null) }
    LaunchedEffect(artworkKey, savedFibre) {
        if (!fibreRestored) {
            if (choices.fibre == null && savedFibre != null) choicesViewModel.update(artworkKey) { c -> c.copy(fibre = savedFibre) }
            fibreRestored = true
        }
    }
    LaunchedEffect(fibre, fibreRestored) { if (fibreRestored) onFibre(fibre) }
    fun show(part: CardSection) = section == CardSection.ALL || section == part

    DecisionShell(visible = section != CardSection.NONE) {
        if (show(CardSection.BRIEF)) {
            Text(stringResource(R.string.brief_title).uppercase(), color = DViolet, fontSize = 11.sp, letterSpacing = 1.5.sp, fontWeight = FontWeight.Bold)
            if (section == CardSection.ALL) {
                Spacer(Modifier.height(4.dp))
                Text(stringResource(R.string.brief_hint), color = DMuted, fontSize = 12.sp, lineHeight = 17.sp)
            }

            // Buyer Brief Reader: AI proposes the answers from the buyer's email or page; the user ticks them.
            var readerOpen by remember { mutableStateOf(false) }
            Spacer(Modifier.height(8.dp))
            androidx.compose.material3.OutlinedButton(onClick = { readerOpen = true }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.reader_button), color = DCyan, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
            val readerColours = garmentChips?.namedColours.orEmpty().ifEmpty { garmentChips?.chips.orEmpty() }
            if (readerOpen && briefRules != null) {
                BriefReaderDialog(
                    colours = readerColours,
                    fabrics = briefRules.fabrics,
                    onApply = { fields ->
                        fields.forEach { f ->
                            when (f.kind) {
                                com.mos.nira.core.ai.BriefReader.Kind.GARMENT_COLOUR ->
                                    readerColours.firstOrNull { it.first == f.value }?.let { onGarment(it.second) }
                                com.mos.nira.core.ai.BriefReader.Kind.FIBRE ->
                                    com.mos.nira.core.ai.BriefReader.fibre(f.value)?.let { fb -> choicesViewModel.update(artworkKey) { c -> c.copy(fibre = fb) } }
                                com.mos.nira.core.ai.BriefReader.Kind.FABRIC -> onFabric(f.value)
                                com.mos.nira.core.ai.BriefReader.Kind.QUANTITY -> f.value.toIntOrNull()?.let { onQuantity(it) }
                                com.mos.nira.core.ai.BriefReader.Kind.FEEL -> com.mos.nira.core.ai.BriefReader.feel(f.value)?.let { onFeel(it) }
                            }
                        }
                    },
                    onDismiss = { readerOpen = false }
                )
            }

            // 1. Garment colour
            var pickerOpen by remember { mutableStateOf(false) }
            Question(stringResource(R.string.brief_q_garment))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                    val named = garmentChips.namedColours.firstOrNull { it.second.equals(garmentHex, true) }
                    Chip(
                        named?.let { com.mos.nira.features.artwork.presentation.garmentColourName(it.first, it.second) }
                            ?: com.mos.nira.features.artwork.presentation.colourName(garmentHex),
                        true, garmentHex
                    ) { pickerOpen = true }
                }
                Chip(stringResource(R.string.picker_more), false) { pickerOpen = true }
            }
            if (pickerOpen && garmentChips != null) {
                com.mos.nira.features.artwork.presentation.GarmentColourPicker(
                    colours = garmentChips.namedColours.ifEmpty { garmentChips.chips },
                    current = garmentHex,
                    onPick = { onGarment(it) },
                    onDismiss = { pickerOpen = false }
                )
            }

            // 2. Fibre and fabric
            Question(stringResource(R.string.brief_q_fibre))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Fibre.values().forEach { f ->
                    Chip(fibreLabel(f), fibre == f) { choicesViewModel.update(artworkKey) { c -> c.copy(fibre = if (fibre == f) null else f) } }
                }
            }
            Spacer(Modifier.height(4.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Feel.values().forEach { f -> Chip(feelLabel(f), feel == f) { onFeel(if (feel == f) null else f) } }
            }
        }

            // ---------- The decision card ----------
            if (show(CardSection.DECISION)) {
                if (section == CardSection.ALL) Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.card_title).uppercase(), color = DCyan, fontSize = 11.sp, letterSpacing = 1.5.sp, fontWeight = FontWeight.Bold)
            }

            val ar = adviceRules
            if (ar == null || briefRules == null) {
                if (section != CardSection.NONE) Small(stringResource(R.string.advice_loading))
                return@DecisionShell
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
            if (show(CardSection.DECISION) && card.unanswered > 0) Small(stringResource(R.string.card_questions_left, card.unanswered))

            // Print vs fabric (print_vs_ground): on a dark or coloured garment, per print colour.
            val groundChecks = remember(garmentHex, screens, printVsGroundRules, tone) {
                val g = garmentHex
                val r = printVsGroundRules
                if (g == null || r == null || tone != com.mos.nira.core.advice.GarmentTone.DARK_OR_COLOURED) emptyList()
                else com.mos.nira.core.advice.PrintVsGround.check(screens.filter { it.isArtworkColour }.map { it.hex }, g, r)
            }
            if (show(CardSection.DECISION) && groundChecks.isNotEmpty()) {
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
            if (show(CardSection.DECISION)) contrastWarnings.forEach { w ->
                Spacer(Modifier.height(6.dp))
                Text(
                    "⚠ " + stringResource(R.string.card_contrast_risk, com.mos.nira.features.artwork.presentation.colourName(w.inkHex), w.deltaL),
                    color = DAmber, fontSize = 12.sp, lineHeight = 17.sp
                )
            }

            val best = card.best
            if (best == null) {
                val left = card.unanswered
                LaunchedEffect(left) {
                    onSummary(JobSummary(null, null, null, "", false, left, false, notes = if (left > 0) listOf(JobNote(NoteKind.UNANSWERED, count = left)) else emptyList()))
                }
            }
            if (!show(CardSection.DECISION)) Unit
            else if (best == null && (garmentHex == null || fibre == null)) {
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.card_need_basics), color = DAmber, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            } else if (best == null) {
                Line(stringResource(R.string.card_method), stringResource(R.string.card_none), stringResource(R.string.src_rules), null, false)
            } else {
                MethodLine(stringResource(R.string.card_method), best, emphasise = true)
                val others = listOfNotNull(card.second?.let { R.string.card_second to it }, card.smallRun?.let { R.string.card_small_run to it })
                if (others.isNotEmpty()) {
                    var othersOpen by remember { mutableStateOf(false) }
                    androidx.compose.material3.TextButton(onClick = { othersOpen = !othersOpen }, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                        Text(
                            stringResource(if (othersOpen) R.string.card_others_hide else R.string.card_others_show, others.size),
                            color = DCyan, fontSize = 12.sp
                        )
                    }
                    if (othersOpen) others.forEach { (label, choice) -> MethodLine(stringResource(label), choice, emphasise = false) }
                }
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
            if (show(CardSection.DECISION)) Line(stringResource(R.string.card_quantity), qText, stringResource(R.string.src_rules), quantityRules?.confidence, quantityRules?.verifiedByRavi == true)

            if (best != null) {
                if (!show(CardSection.DECISION)) Unit
                else if (card.baseSource == BaseSource.DIGITAL) {
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
                        var stepsOpen by remember { mutableStateOf(false) }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            stringResource(R.string.card_process) + "  (" + best.advice.processSteps.size + ")  " + if (stepsOpen) "▴" else "▾",
                            color = DCyan, fontSize = 12.sp,
                            modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { stepsOpen = !stepsOpen }.padding(vertical = 4.dp)
                        )
                        if (stepsOpen) best.advice.processSteps.forEachIndexed { i, step ->
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
                var sheetLanguage by remember { mutableStateOf("en") }
                val rates = com.mos.nira.features.commercial.rememberFactoryRates()
                val cost = remember(card, screens, rates) { com.mos.nira.core.commercial.PrintCost.forCard(card, screens, rates) }
                val options = remember(card, screens, rates, costDownRules) {
                    com.mos.nira.core.commercial.PrintCost.costDown(card, screens, rates, costDownRules)
                }
                // The top bar: method, screens and cost per piece.
                val summaryScreens = if (card.baseSource == BaseSource.DIGITAL) best.advice.screens ?: 0 else card.screens.size
                // What the card noticed, from its own checks (no new values).
                val bestSaving = options.maxOfOrNull { it.savingPerPc }?.takeIf { it > 0 }
                val notes = buildList {
                    contrastWarnings.forEach { add(JobNote(NoteKind.CONTRAST, colourHex = it.inkHex)) }
                    groundChecks.filter { it.verdict == com.mos.nira.core.advice.GroundVerdict.NEEDS_DISCHARGE_OR_BASE && "discharge" !in best.advice.id }
                        .forEach { add(JobNote(NoteKind.NEEDS_BASE, colourHex = it.inkHex)) }
                    if (!best.advice.verifiedByRavi) add(JobNote(NoteKind.NOT_VERIFIED))
                    bestSaving?.let { add(JobNote(NoteKind.SAVING, amount = it, currency = rates.currency)) }
                    if (card.unanswered > 0) add(JobNote(NoteKind.UNANSWERED, count = card.unanswered))
                }
                LaunchedEffect(best.advice.name, summaryScreens, cost?.perPc, rates.currency, card.unanswered, best.advice.verifiedByRavi, rates.isSample, notes) {
                    onSummary(JobSummary(best.advice.name, summaryScreens, cost?.perPc, rates.currency, best.advice.verifiedByRavi, card.unanswered, rates.isSample, card.baseSource == BaseSource.DIGITAL, notes))
                }
                if (show(CardSection.COST)) com.mos.nira.features.commercial.OrderCostSection(
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
                    // The artwork's own colours (from the engine's screens; a digital card has none of its own).
                    jobLabel = card.screens.ifEmpty { screens }.filter { it.isArtworkColour }
                        .map { com.mos.nira.features.artwork.presentation.colourName(it.hex) }.distinct()
                        .joinToString(", ").ifBlank { best.advice.name },
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
                        onDismiss = { sheetOpen = false; sheetLanguage = "en" },
                        initialLanguage = sheetLanguage
                    )
                }

                // Risks from the rules file for the chosen method
                if (show(CardSection.DECISION) && best.advice.risks.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Text(stringResource(R.string.card_risks), color = DMuted, fontSize = 11.sp)
                    best.advice.risks.forEach { Text("• $it", color = DWhite, fontSize = 12.sp, lineHeight = 17.sp) }
                }

                // "Explain this card": AI explains the card's facts (text only, on-device preferred).
                if (show(CardSection.DECISION)) com.mos.nira.features.ai.CardExplanationSection(
                    facts, best.advice.name,
                    hintText = if (section == CardSection.ALL) null else stringResource(R.string.card_explain_hint_short)
                )

                // Ask NIRA: questions about this job, answered from the same facts plus the order cost.
                // On the job page it opens from the floating button; in ALL mode it keeps its own button.
                if (section == CardSection.ALL) {
                    Spacer(Modifier.height(8.dp))
                    androidx.compose.material3.OutlinedButton(onClick = { onAskOpenChange(true) }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.ask_button), color = DCyan, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    }
                }
                if (askOpen) {
                    val askFacts = remember(facts, cost, options, rates) {
                        if (cost?.perPc != null && cost.total != null) facts + com.mos.nira.features.commercial.tradeOffFacts(cost, options, rates) else facts
                    }
                    val namedColours = garmentChips?.namedColours.orEmpty().ifEmpty { garmentChips?.chips.orEmpty() }
                    val actionContext = remember(previewInkHexes, namedColours, briefRules) {
                        com.mos.nira.core.ai.NiraActions.Context(
                            inkHexes = previewInkHexes,
                            colours = namedColours,
                            fabrics = briefRules.fabrics,
                            languages = com.mos.nira.features.commercial.DecisionSheet.LANGUAGES
                        )
                    }
                    com.mos.nira.features.ai.AskNiraSheet(
                        jobKey = artworkKey?.toString() ?: "job",
                        facts = askFacts,
                        actionContext = actionContext,
                        onActions = { actions ->
                            // Run each checked action and remember how to put it back.
                            val undo = mutableListOf<() -> Unit>()
                            actions.forEach { a ->
                                when (a) {
                                    is com.mos.nira.core.ai.NiraActions.Action.PreviewInk -> {
                                        // Every shade with this colour name changes, so the whole colour changes in the preview.
                                        a.allHexes.forEach { hex ->
                                            val before = inkSwaps[hex]
                                            onPreviewInk(hex, a.toHex)
                                            undo += { onPreviewInk(hex, before) }
                                        }
                                    }
                                    is com.mos.nira.core.ai.NiraActions.Action.SetGarment -> {
                                        val before = garmentHex
                                        onGarment(a.hex)
                                        if (before != null) undo += { onGarment(before) }
                                    }
                                    is com.mos.nira.core.ai.NiraActions.Action.SetQuantity -> {
                                        val before = quantity
                                        onQuantity(a.pieces)
                                        undo += { onQuantity(before) }
                                    }
                                    is com.mos.nira.core.ai.NiraActions.Action.SetFibre -> {
                                        val before = fibre
                                        com.mos.nira.core.ai.BriefReader.fibre(a.fibre)?.let { fb -> choicesViewModel.update(artworkKey) { c -> c.copy(fibre = fb) } }
                                        undo += { choicesViewModel.update(artworkKey) { c -> c.copy(fibre = before) } }
                                    }
                                    is com.mos.nira.core.ai.NiraActions.Action.SetFabric -> {
                                        val before = fabric
                                        onFabric(a.fabric)
                                        undo += { onFabric(before) }
                                    }
                                    is com.mos.nira.core.ai.NiraActions.Action.SetFeel -> {
                                        val before = feel
                                        com.mos.nira.core.ai.BriefReader.feel(a.feel)?.let { onFeel(it) }
                                        undo += { onFeel(before) }
                                    }
                                    is com.mos.nira.core.ai.NiraActions.Action.OpenSheet -> {
                                        sheetLanguage = a.language
                                        sheetOpen = true
                                    }
                                    // Home actions are not offered on a job.
                                    else -> Unit
                                }
                            }
                            val run: () -> Unit = { undo.asReversed().forEach { it() } }
                            run
                        },
                        onDismiss = { onAskOpenChange(false) }
                    )
                }
            }
            if (show(CardSection.DECISION)) {
                Spacer(Modifier.height(10.dp))
                Small(stringResource(R.string.card_disclaimer))
            }
    }
}

/** The job page's tabs draw one part of the card each; NONE draws nothing but keeps the dialogs. */
enum class CardSection { ALL, BRIEF, DECISION, COST, NONE }

/** What the job page's top bar shows. Numbers are the engine's and the factory rates'. */
data class JobSummary(
    val method: String?,
    val screens: Int?,
    val perPc: Double?,
    val currency: String,
    val verifiedByRavi: Boolean,
    val unanswered: Int,
    val sampleRates: Boolean,
    /** True when the method is digital-based (digital, or discharge + digital): no spot-screen separation. */
    val digitalBase: Boolean = false,
    /** What the card noticed (contrast, base needed, not floor-verified, a saving). */
    val notes: List<JobNote> = emptyList()
)

/** One "NIRA noticed" note. [colourHex] or [amount] fill the text; [tab] is where to fix it. */
data class JobNote(val kind: NoteKind, val colourHex: String? = null, val amount: Double? = null, val currency: String = "", val count: Int = 0)

enum class NoteKind { LOW_RES, BACKGROUND, INPUT_KIND, COLOUR_CHECKS, UNANSWERED, CONTRAST, NEEDS_BASE, NOT_VERIFIED, SAVING }

/**
 * Top of the Print Report: the job's method from the decision card, and what the screen
 * separations below mean for it, so the two never seem to disagree.
 */
@Composable
fun JobMethodNote(summary: JobSummary?, onEditBrief: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = DCardBg)
    ) {
        Column(Modifier.padding(14.dp)) {
            val m = summary?.method
            if (m == null) {
                Text(stringResource(R.string.report_no_method), color = DAmber, fontSize = 13.sp, lineHeight = 18.sp)
            } else {
                Text(stringResource(R.string.report_job_method, m), color = DCyan, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text(
                    stringResource(if (summary.digitalBase) R.string.report_note_digital else R.string.report_note_screen),
                    color = DWhite, fontSize = 12.sp, lineHeight = 17.sp
                )
            }
            androidx.compose.material3.TextButton(onClick = onEditBrief) {
                Text(stringResource(R.string.report_edit_brief), color = DCyan, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun DecisionShell(visible: Boolean, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    if (visible) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = DCardBg)
        ) { Column(modifier = Modifier.padding(16.dp), content = content) }
    } else {
        Column(content = content)
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
    Spacer(Modifier.height(6.dp))
    androidx.compose.material3.HorizontalDivider(color = DInner)
    Column(Modifier.fillMaxWidth().padding(top = 6.dp)) {
        // Label, then where it comes from, how sure it is and the floor check, all on one small line.
        val conf = confidence?.let { stringResource(R.string.card_confidence, it) } ?: stringResource(R.string.advice_confidence_none)
        Text(
            "$label  ·  $source  ·  $conf  ·  " + stringResource(if (verified) R.string.card_verified else R.string.card_not_verified),
            color = if (verified) DGreen else DMuted, fontSize = 10.sp, lineHeight = 13.sp
        )
        Text(value, color = DWhite, fontSize = if (emphasise) 15.sp else 13.sp, fontWeight = if (emphasise) FontWeight.Bold else FontWeight.Normal, lineHeight = 18.sp)
    }
}

@Composable
private fun Question(text: String) {
    Spacer(Modifier.height(8.dp))
    Text(text, color = DWhite, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(2.dp))
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
internal fun fabricLabel(id: String): String = when (id) {
    "single_jersey" -> stringResource(R.string.fabric_single_jersey)
    "pique" -> stringResource(R.string.fabric_pique)
    "fleece" -> stringResource(R.string.fabric_fleece)
    "interlock" -> stringResource(R.string.fabric_interlock)
    "other" -> stringResource(R.string.fabric_other)
    else -> id
}

@Composable
internal fun feelLabel(f: Feel): String = stringResource(
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
