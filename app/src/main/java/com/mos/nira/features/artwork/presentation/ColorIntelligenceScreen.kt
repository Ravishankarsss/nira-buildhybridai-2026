package com.mos.nira.features.artwork.presentation

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mos.nira.features.artwork.components.ColorDetailInspector
import com.mos.nira.features.artwork.components.ColorSwatchCard
import com.mos.nira.features.artwork.domain.ColorGroup
import com.mos.nira.features.artwork.domain.DominantColor
import com.mos.nira.core.image.ColourLabel
import com.mos.nira.core.image.ColorConverter
import com.mos.nira.features.artwork.domain.ArtworkBackground
import com.mos.nira.core.image.InputKindAssessment
import com.mos.nira.core.image.InputKind
import com.mos.nira.features.artwork.domain.ShadeEntry
import com.mos.nira.features.artwork.domain.MeasuredColor
import com.mos.nira.R
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import com.mos.nira.features.ai.ArtworkAiReadCard
import com.mos.nira.features.ai.ArtworkKey
import com.mos.nira.features.advice.PrintMethodAdviceCard

private val NavyBg = Color(0xFF0B1020)
private val CardBg = Color(0xFF171F35)
private val Violet = Color(0xFF8B7CFF)
private val Cyan = Color(0xFF55D6E8)
private val TextWhite = Color(0xFFF5F6FF)
private val TextMuted = Color(0xFF9BA6C0)
private val Amber = Color(0xFFFFC566) // "needs your answer" text

@Composable
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
fun ColorIntelligenceScreen(
    bitmap: Bitmap?,
    palette: List<DominantColor>,
    colorGroups: List<ColorGroup>,
    selectedColor: DominantColor?,
    garmentColor: DominantColor?,
    onColorSelected: (DominantColor) -> Unit,
    onMergeColors: (keepId: Int, mergeId: Int) -> Unit,
    onRemoveColor: (colorId: Int) -> Unit,
    onToggleLock: (colorId: Int) -> Unit,
    onAddHexColor: (String) -> Unit,
    onSetGarmentColor: (DominantColor?) -> Unit,
    onOpenShadeLibrary: () -> Unit,
    onAssignShade: (colorId: Int, shade: ShadeEntry) -> Unit,
    onNavigateToSeparation: () -> Unit,
    onNavigateToPrintReport: () -> Unit = {},
    // Print colours and garment colour measured by the Print Report engine, when calculated.
    measuredPrintColourHexes: List<String>? = null,
    measuredGarmentHex: String? = null,
    // The user's shade library, used to give the garment colour a readable name.
    garmentNameShades: List<ShadeEntry> = emptyList(),
    // The Print Report engine's measured print colours (garment removed), and whether it is working.
    measuredPrintColours: List<MeasuredColor>? = null,
    isMeasuringPrintColours: Boolean = false,
    // Photo or artwork check (print_rules.json photo_vs_artwork) and the user's answer.
    inputKindAssessment: InputKindAssessment? = null,
    inputKindConfirmed: Boolean = false,
    onAnswerInputKind: (InputKind) -> Unit = {},
    // Fold shadow check (print_rules.json shadow_vs_ink) and the user's answer for a flagged colour.
    foldShadowsRemoved: Int = 0,
    shadowTestLowResolution: Boolean = false,
    onAnswerFoldShadow: (points: List<Pair<Float, Float>>, isInk: Boolean) -> Unit = { _, _ -> },
    onAnswerAllFoldShadows: () -> Unit = {},
    // Edge blending (print_rules.json edge_blend): pixels merged, the undo switch, and the answer.
    edgeBlendPixelsMerged: Int = 0,
    keepEdgeColours: Boolean = false,
    onKeepEdgeColours: (Boolean) -> Unit = {},
    onAnswerEdgeBlend: (isBlur: Boolean) -> Unit = {},
    // Small colours (print_rules.json small_colours): speck pixels removed, and the answer for a flagged one.
    speckPixelsRemoved: Int = 0,
    onAnswerSmallColour: (point: Pair<Float, Float>, isInk: Boolean) -> Unit = { _, _ -> },
    // Table, floor or wall at the photo edge (print_rules.json photo_background) and the undo switch.
    photoEdgeExcludedPercent: Double = 0.0,
    // Photo or artwork, and the plain background of an artwork file (print_rules.json artwork_background).
    inputKind: InputKind = InputKind.ARTWORK,
    artworkBackground: ArtworkBackground? = null,
    artworkBackgroundAnswer: Boolean? = null,
    backgroundThresholdDeltaE00: Double? = null,
    onAnswerArtworkBackground: (print: Boolean) -> Unit = {},
    // "See it on your garment" (print_rules.json garment_preview).
    previewContent: @Composable () -> Unit = {},
    // Print brief and decision card (print_rules.json print_brief): one section per tab, Ask NIRA open state.
    decisionContent: @Composable (section: com.mos.nira.features.advice.CardSection, askOpen: Boolean, onAskOpen: (Boolean) -> Unit) -> Unit = { _, _, _ -> },
    // Method, screens and cost for the top bar (from the decision card).
    jobSummary: com.mos.nira.features.advice.JobSummary? = null,
    jobTitle: String = "",
    /** The print on the chosen garment for the top bar (falls back to the artwork). */
    headerBitmap: Bitmap? = null,
    /** The buyer's order quantity, shown in the top bar once answered. */
    orderQuantity: Int? = null,
    // The artwork's resolution is below the rules' limit for fine detail (a "NIRA noticed" note).
    lowResolution: Boolean = false,
    includePhotoEdgeArea: Boolean = false,
    onIncludePhotoEdgeArea: (Boolean) -> Unit = {},
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    // The artwork background, when left out (not ink), is hidden from the colour list and families.
    val hiddenBackgroundLab = artworkBackground?.takeIf { !it.printed }?.let {
        val rgb = ColorConverter.hexToRgb(it.hex)
        ColorConverter.rgbToLab(rgb.r, rgb.g, rgb.b)
    }
    fun isHiddenBackground(c: DominantColor): Boolean {
        val lab = hiddenBackgroundLab ?: return false
        val limit = backgroundThresholdDeltaE00 ?: return false
        return ColorConverter.deltaE00(ColorConverter.Lab(c.labL, c.labA, c.labB), lab) <= limit
    }
    var detailsOpen by rememberSaveable { mutableStateOf(false) }
    var tab by rememberSaveable { mutableStateOf(JobTab.BRIEF) }
    var askOpen by remember { mutableStateOf(false) }
    var showAddDialog by remember { mutableStateOf(false) }
    var showMergeDialog by remember { mutableStateOf(false) }
    var mergeTargetId by remember { mutableStateOf<Int?>(null) }
    var hexInput by remember { mutableStateOf("#") }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text(stringResource(R.string.add_colour_title), color = TextWhite, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(stringResource(R.string.add_colour_hint), color = TextMuted, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = hexInput,
                        onValueChange = { hexInput = it.uppercase() },
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedTextColor = TextWhite,
                            unfocusedTextColor = TextWhite,
                            focusedContainerColor = Color(0xFF0F1526),
                            unfocusedContainerColor = Color(0xFF0F1526),
                            focusedIndicatorColor = Cyan
                        )
                    )
                    // The name, so nobody has to read a colour code.
                    if (Regex("^#[0-9A-Fa-f]{6}$").matches(hexInput.trim())) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(stringResource(R.string.add_colour_name, colourName(hexInput.trim())), color = Cyan, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onAddHexColor(hexInput)
                        showAddDialog = false
                        hexInput = "#"
                    }
                ) {
                    Text(stringResource(R.string.add_colour_confirm), color = Cyan, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text("Cancel", color = TextWhite)
                }
            },
            containerColor = CardBg,
            shape = RoundedCornerShape(16.dp)
        )
    }

    if (showMergeDialog && selectedColor != null) {
        AlertDialog(
            onDismissRequest = { showMergeDialog = false },
            title = { Text(stringResource(R.string.merge_title, colourName(selectedColor.hex)), color = TextWhite, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.merge_hint), color = TextMuted, fontSize = 12.sp)
                    palette.filter { it.id != selectedColor.id }.forEach { target ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (mergeTargetId == target.id) Color(0xFF232D48) else Color(0xFF0F1526))
                                .clickable { mergeTargetId = target.id }
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .clip(CircleShape)
                                    .background(Color(target.r, target.g, target.b))
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(colourName(target.hex), color = TextWhite, fontSize = 13.sp)
                            Spacer(modifier = Modifier.weight(1f))
                            Text(target.formattedPercentage, color = Cyan, fontSize = 12.sp)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (mergeTargetId != null) {
                            onMergeColors(mergeTargetId!!, selectedColor.id)
                            showMergeDialog = false
                            mergeTargetId = null
                        }
                    },
                    enabled = mergeTargetId != null
                ) {
                    Text("Confirm Merge", color = Cyan, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showMergeDialog = false }) {
                    Text("Cancel", color = TextWhite)
                }
            },
            containerColor = CardBg,
            shape = RoundedCornerShape(16.dp)
        )
    }

    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    Box(modifier = modifier.fillMaxSize().background(NavyBg)) {
    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(10.dp))
            JobSummaryBar(
                bitmap = headerBitmap ?: bitmap,
                title = jobTitle,
                summary = jobSummary,
                // Quantity first: the long colour note may be cut at the end of the line.
                colourLine = (orderQuantity?.let { stringResource(R.string.sheet_quantity, it) + " · " } ?: "") + (when {
                    measuredPrintColours != null -> {
                        val flagged = measuredPrintColours.count { it.possibleFoldShadow || it.possibleEdgeBlend || it.possibleSmallColour }
                        val sure = measuredPrintColours.size - flagged
                        pluralStringResource(R.plurals.measured_print_colours, sure, sure) +
                            (if (flagged > 0) " " + stringResource(R.string.colour_possible_shadow_extra, flagged) else "")
                    }
                    isMeasuringPrintColours -> stringResource(R.string.colour_measuring)
                    else -> stringResource(R.string.colour_clusters_title, palette.size)
                }),
                onBack = onBack
            )
            // NIRA noticed: the page's own questions first, then what the decision card noticed.
            val screenNotes = buildList {
                if (artworkBackground != null && artworkBackgroundAnswer == null) add(com.mos.nira.features.advice.JobNote(com.mos.nira.features.advice.NoteKind.BACKGROUND))
                if (inputKindAssessment != null && inputKindAssessment.askUser && !inputKindConfirmed) add(com.mos.nira.features.advice.JobNote(com.mos.nira.features.advice.NoteKind.INPUT_KIND))
                if (lowResolution) add(com.mos.nira.features.advice.JobNote(com.mos.nira.features.advice.NoteKind.LOW_RES))
                val flagged = measuredPrintColours?.count { it.possibleFoldShadow || it.possibleEdgeBlend || it.possibleSmallColour } ?: 0
                if (flagged > 0) add(com.mos.nira.features.advice.JobNote(com.mos.nira.features.advice.NoteKind.COLOUR_CHECKS, count = flagged))
            }
            val allNotes = (screenNotes + jobSummary?.notes.orEmpty()).distinct()
            if (allNotes.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                NoticedStrip(allNotes) { tab = it }
            }
        }

        // The tabs stay at the top while the tab scrolls.
        stickyHeader {
            JobTabs(selected = tab, onSelect = { tab = it })
        }

        // "This looks like background, not ink. Print it or drop it?" (artwork files).
        if (tab == JobTab.BRIEF && artworkBackground != null) {
            item {
                ArtworkBackgroundCard(artworkBackground, artworkBackgroundAnswer, onAnswerArtworkBackground)
            }
        }

        // "This looks like a photo. Treat it as a photo?" when the signals disagree with the mode.
        if (tab == JobTab.BRIEF && inputKindAssessment != null && inputKindAssessment.askUser && !inputKindConfirmed) {
            item {
                InputKindQuestionCard(inputKindAssessment, onAnswerInputKind)
            }
        }

        // Fold shadow notes (photos of light garments).
        if (tab == JobTab.ENGINEERING && (foldShadowsRemoved > 0 || shadowTestLowResolution)) {
            item {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
                    if (foldShadowsRemoved > 0) {
                        Text(
                            pluralStringResource(R.plurals.fold_shadows_removed, foldShadowsRemoved, foldShadowsRemoved),
                            color = TextMuted, fontSize = 12.sp, lineHeight = 17.sp
                        )
                    }
                    if (shadowTestLowResolution) {
                        Text(stringResource(R.string.colour_shadow_low_res), color = Amber, fontSize = 12.sp, lineHeight = 17.sp)
                    }
                }
            }
        }

        // Photo edge area left out of the print, and the switch to include it.
        if (tab == JobTab.ENGINEERING && (photoEdgeExcludedPercent > 0.0 || includePhotoEdgeArea)) {
            item {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
                    if (photoEdgeExcludedPercent > 0.0) {
                        Text(
                            stringResource(R.string.photo_edge_excluded, String.format("%.1f%%", photoEdgeExcludedPercent)),
                            color = TextMuted, fontSize = 12.sp, lineHeight = 17.sp
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(R.string.photo_edge_include_switch),
                            color = TextWhite, fontSize = 13.sp, modifier = Modifier.weight(1f)
                        )
                        Switch(checked = includePhotoEdgeArea, onCheckedChange = onIncludePhotoEdgeArea)
                    }
                }
            }
        }

        // Specks removed as colours.
        if (tab == JobTab.ENGINEERING && speckPixelsRemoved > 0) {
            item {
                Text(
                    pluralStringResource(R.plurals.speck_pixels_removed, speckPixelsRemoved, speckPixelsRemoved),
                    color = TextMuted, fontSize = 12.sp, lineHeight = 17.sp,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
        }

        // Edge blending report and the undo switch.
        if (tab == JobTab.ENGINEERING && (edgeBlendPixelsMerged > 0 || keepEdgeColours)) {
            item {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
                    if (edgeBlendPixelsMerged > 0) {
                        Text(
                            pluralStringResource(R.plurals.edge_blend_merged, edgeBlendPixelsMerged, edgeBlendPixelsMerged),
                            color = TextMuted, fontSize = 12.sp, lineHeight = 17.sp
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(R.string.edge_blend_keep_switch),
                            color = TextWhite, fontSize = 13.sp, modifier = Modifier.weight(1f)
                        )
                        Switch(checked = keepEdgeColours, onCheckedChange = onKeepEdgeColours)
                    }
                }
            }
        }

        // Measured print colours: the ones used for screens and ink.
        if (tab == JobTab.ENGINEERING && !measuredPrintColours.isNullOrEmpty()) {
            item {
                MeasuredPrintColoursCard(measuredPrintColours, onAnswerFoldShadow, onAnswerAllFoldShadows, onAnswerEdgeBlend, onAnswerSmallColour)
            }
        }

        // "See it on your garment": drawn on the phone with the rules' ink model.
        if (tab == JobTab.BRIEF) item { previewContent() }

        // "Ask like a master": four tap questions, then one decision card.
        // Always composed (the Engineering tab draws nothing) so Ask NIRA, the sheet and the top bar work on every tab.
        item { decisionContent(tab.section, askOpen) { askOpen = it } }

        // AI read of the artwork (Firebase AI Logic). Engine numbers are passed in as facts.
        if (tab == JobTab.DECISION) item {
            ArtworkAiReadCard(
                bitmap = bitmap,
                printColourHexes = palette.filter { !it.isGarmentBase }.map { it.hex },
                garmentHex = garmentColor?.hex,
                factColourHexes = measuredPrintColourHexes,
                factGarmentHex = measuredGarmentHex
            )
        }

        // The method list and the AI read's artwork type stay in step even when Details is closed.
        item {
            com.mos.nira.features.advice.SyncAdviceChoices(
                garmentColor = garmentColor,
                artworkKey = ArtworkKey.of(bitmap, palette.filter { !it.isGarmentBase }.map { it.hex }),
                garmentFromMeasureAllowed = inputKind == InputKind.PHOTO
            )
        }

        // Engineering: the full report, the films, and the shade library.
        if (tab == JobTab.ENGINEERING) item {
            EngineeringLinks(onReport = onNavigateToPrintReport, onSeparation = onNavigateToSeparation, onShades = onOpenShadeLibrary)
        }

        // Details: every method, colour editing and colour families. Closed by default.
        if (tab == JobTab.ENGINEERING) item {
            Card(
                modifier = Modifier.fillMaxWidth().clickable { detailsOpen = !detailsOpen },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = CardBg)
            ) {
                Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.details_title).uppercase(), color = Violet, fontSize = 11.sp, letterSpacing = 1.5.sp, fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.details_hint), color = TextMuted, fontSize = 12.sp, lineHeight = 16.sp)
                    }
                    Text(stringResource(if (detailsOpen) R.string.details_hide else R.string.details_show), color = Cyan, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        if (tab == JobTab.ENGINEERING && detailsOpen) {
            // Print method advice from print_techniques.json (rules, not AI).
            item {
                PrintMethodAdviceCard(
                    garmentColor = garmentColor,
                    // An artwork file's border is paper, not fabric: the user chooses the garment.
                    garmentFromMeasureAllowed = inputKind == InputKind.PHOTO,
                    artworkKey = ArtworkKey.of(bitmap, palette.filter { !it.isGarmentBase }.map { it.hex })
                )
            }

            // Active Selected Color Technical Inspector + Editing Toolbar + Top 3 Shade Matches
            if (selectedColor != null && !isHiddenBackground(selectedColor)) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        ColorDetailInspector(
                            color = selectedColor,
                            onAssignShade = { shade -> onAssignShade(selectedColor.id, shade) }
                        )

                        // Stage 1 interactive editing controls: Merge, Remove, Lock, Set as Garment Base
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { showMergeDialog = true },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Merge...", fontSize = 11.sp, color = TextWhite)
                            }

                            OutlinedButton(
                                onClick = { onToggleLock(selectedColor.id) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text(if (selectedColor.isLocked) "Unlock" else "Lock", fontSize = 11.sp, color = Cyan)
                            }

                            OutlinedButton(
                                onClick = { onSetGarmentColor(selectedColor) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Base Fabric", fontSize = 11.sp, color = Violet)
                            }

                            OutlinedButton(
                                onClick = { onRemoveColor(selectedColor.id) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Remove", fontSize = 11.sp, color = Color(0xFFFF6E6E))
                            }
                        }
                    }
                }
            }

            // Color Swatches Palette Header with "Add Required Colour" CTA
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.colour_clusters_title, palette.size),
                            color = Violet,
                            fontSize = 11.sp,
                            letterSpacing = 1.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = stringResource(R.string.colour_clusters_hint),
                            color = TextMuted,
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )
                    }

                    TextButton(onClick = { showAddDialog = true }) {
                        Text(stringResource(R.string.add_colour_button), color = Cyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Palette Swatches List (an artwork background that was left out is not listed)
            items(palette.filter { !isHiddenBackground(it) }) { color ->
                ColorSwatchCard(
                    color = color,
                    isSelected = selectedColor?.id == color.id,
                    onClick = { onColorSelected(color) }
                )
            }

            // Perceptual Color Families / Grouping Section
            // Families with only one shade add nothing: hidden. The left-out background is not a family.
            val shownGroups = colorGroups
                .map { g -> g.copy(members = g.members.filter { !isHiddenBackground(it) }) }
                .filter { it.members.size > 1 }
            if (shownGroups.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "PERCEPTUAL COLOR FAMILIES (CIEDE2000 < 5.0)",
                        color = Violet,
                        fontSize = 11.sp,
                        letterSpacing = 1.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                items(shownGroups) { group ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = CardBg)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = stringResource(R.string.colour_family, group.groupId + 1, colourName(group.primaryColor.hex)),
                                    color = TextWhite,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    text = String.format("%.1f%% coverage", group.totalPercentage),
                                    color = Cyan,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                group.members.forEach { m ->
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(CircleShape)
                                            .background(Color(m.r, m.g, m.b))
                                            .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape)
                                            .clickable { onColorSelected(m) }
                                    )
                                }
                                Text(
                                    text = "${group.members.size} shades",
                                    color = TextMuted,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(96.dp))
        }
    }
        // Ask NIRA from every tab, once the card has a method to talk about.
        if (jobSummary?.method != null) {
            // Full label at the top of the page; a small round button once the user scrolls.
            val expanded = listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset < 40
            val label = stringResource(R.string.ask_fab)
            androidx.compose.material3.ExtendedFloatingActionButton(
                text = { Text(label.removePrefix("✦").trim(), fontWeight = FontWeight.Bold) },
                icon = { Text("✦", fontSize = 18.sp, fontWeight = FontWeight.Bold) },
                expanded = expanded,
                onClick = { askOpen = true },
                containerColor = Violet,
                contentColor = Color(0xFF0F1526),
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
            )
        }
    }
}

/** The job page's tabs; each shows one part of the job. */
enum class JobTab(val section: com.mos.nira.features.advice.CardSection) {
    BRIEF(com.mos.nira.features.advice.CardSection.BRIEF),
    DECISION(com.mos.nira.features.advice.CardSection.DECISION),
    COST(com.mos.nira.features.advice.CardSection.COST),
    ENGINEERING(com.mos.nira.features.advice.CardSection.NONE)
}

/** The Print Report engine's measured print colours, with coverage and nearest shade. */
@Composable
private fun MeasuredPrintColoursCard(
    colours: List<MeasuredColor>,
    onAnswerFoldShadow: (points: List<Pair<Float, Float>>, isInk: Boolean) -> Unit,
    onAnswerAllFoldShadows: () -> Unit,
    onAnswerEdgeBlend: (isBlur: Boolean) -> Unit,
    onAnswerSmallColour: (point: Pair<Float, Float>, isInk: Boolean) -> Unit
) {
    var asking by remember { mutableStateOf<MeasuredColor?>(null) }
    asking?.takeIf { it.possibleSmallColour && !it.possibleFoldShadow && !it.possibleEdgeBlend }?.let { c ->
        val point = c.smallColourPoint
        AlertDialog(
            onDismissRequest = { asking = null },
            title = { Text(stringResource(R.string.small_colour_title)) },
            text = { Text(colourName(c.hex) + "  •  " + c.formattedCoverage + "\n\n" + stringResource(R.string.small_colour_body)) },
            confirmButton = {
                TextButton(onClick = { if (point != null) onAnswerSmallColour(point, true); asking = null }) {
                    Text(stringResource(R.string.small_colour_yes))
                }
            },
            dismissButton = {
                TextButton(onClick = { if (point != null) onAnswerSmallColour(point, false); asking = null }) {
                    Text(stringResource(R.string.small_colour_no))
                }
            }
        )
    }
    asking?.takeIf { it.possibleEdgeBlend && !it.possibleFoldShadow }?.let { c ->
        AlertDialog(
            onDismissRequest = { asking = null },
            title = { Text(stringResource(R.string.edge_blend_confirm_title)) },
            text = { Text(colourName(c.hex) + "\n\n" + stringResource(R.string.edge_blend_confirm_body)) },
            confirmButton = {
                TextButton(onClick = { onAnswerEdgeBlend(true); asking = null }) {
                    Text(stringResource(R.string.edge_blend_confirm_merge))
                }
            },
            dismissButton = {
                TextButton(onClick = { onAnswerEdgeBlend(false); asking = null }) {
                    Text(stringResource(R.string.edge_blend_confirm_keep))
                }
            }
        )
    }
    asking?.takeIf { it.possibleFoldShadow }?.let { c ->
        AlertDialog(
            onDismissRequest = { asking = null },
            title = { Text(stringResource(R.string.colour_confirm_title)) },
            text = {
                Column {
                    Text(colourName(c.hex) + "\n\n" + stringResource(R.string.colour_confirm_body))
                    // One answer for every flagged area in the photo, however the colours group.
                    if (colours.count { it.possibleFoldShadow } > 1 || c.uncertainRegionPoints.size > 1) {
                        Spacer(modifier = Modifier.height(8.dp))
                        TextButton(onClick = { onAnswerAllFoldShadows(); asking = null }) {
                            Text(stringResource(R.string.colour_confirm_all_shadows))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { onAnswerFoldShadow(c.uncertainRegionPoints, true); asking = null }) {
                    Text(stringResource(R.string.colour_confirm_ink))
                }
            },
            dismissButton = {
                TextButton(onClick = { onAnswerFoldShadow(c.uncertainRegionPoints, false); asking = null }) {
                    Text(stringResource(R.string.colour_confirm_shadow))
                }
            }
        )
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.colour_measured_title),
                color = Violet,
                fontSize = 11.sp,
                letterSpacing = 1.5.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(stringResource(R.string.colour_measured_hint), color = TextMuted, fontSize = 12.sp, lineHeight = 17.sp)
            colours.forEach { c ->
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = if (c.possibleFoldShadow || c.possibleEdgeBlend || c.possibleSmallColour) Modifier.clickable { asking = c } else Modifier
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(c.rgb.r, c.rgb.g, c.rgb.b))
                            .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(colourName(c.hex), color = TextWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(c.hex, color = TextMuted, fontSize = 11.sp)
                        }
                        Text(
                            text = stringResource(R.string.colour_coverage, c.formattedCoverage) +
                                (c.nearestShade?.let { "  •  ${it.code} ${it.name}" } ?: ""),
                            color = TextMuted,
                            fontSize = 12.sp
                        )
                        if (c.possibleSmallColour && !c.possibleEdgeBlend && !c.possibleFoldShadow) {
                            Text(
                                stringResource(R.string.small_colour_flag, c.formattedCoverage),
                                color = Amber,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        if (c.possibleEdgeBlend && !c.possibleFoldShadow) {
                            Text(
                                stringResource(R.string.edge_blend_flag_label),
                                color = Amber,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        if (c.possibleFoldShadow) {
                            Text(
                                stringResource(R.string.colour_possible_shadow_label),
                                color = Amber,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Asks the user to confirm photo or artwork when the file signals disagree with their choice. */
@Composable
private fun InputKindQuestionCard(assessment: InputKindAssessment, onAnswer: (InputKind) -> Unit) {
    val suggestPhoto = assessment.chosen == InputKind.ARTWORK
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                stringResource(if (suggestPhoto) R.string.input_kind_ask_photo else R.string.input_kind_ask_artwork),
                color = TextWhite, fontSize = 15.sp, fontWeight = FontWeight.Bold, lineHeight = 20.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(stringResource(R.string.input_kind_hint), color = TextMuted, fontSize = 12.sp, lineHeight = 17.sp)
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onAnswer(if (suggestPhoto) InputKind.PHOTO else InputKind.ARTWORK) }) {
                    Text(stringResource(if (suggestPhoto) R.string.input_kind_yes_photo else R.string.input_kind_yes_artwork))
                }
                OutlinedButton(onClick = { onAnswer(assessment.chosen) }) {
                    Text(stringResource(if (suggestPhoto) R.string.input_kind_keep_artwork else R.string.input_kind_keep_photo))
                }
            }
        }
    }
}

/** "This looks like background, not ink": print it or drop it (print_rules.json artwork_background). */
@Composable
private fun ArtworkBackgroundCard(bg: ArtworkBackground, answer: Boolean?, onAnswer: (print: Boolean) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(android.graphics.Color.parseColor(bg.hex)))
                    .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(10.dp))
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                if (answer == null) {
                    Text(stringResource(R.string.artbg_title), color = TextWhite, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        stringResource(
                            R.string.artbg_body, colourName(bg.hex),
                            String.format("%.0f%%", bg.imageCoveragePercent), String.format("%.0f%%", bg.borderSharePercent)
                        ),
                        color = TextMuted, fontSize = 12.sp, lineHeight = 17.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { onAnswer(false) }) { Text(stringResource(R.string.artbg_drop)) }
                        OutlinedButton(onClick = { onAnswer(true) }) { Text(stringResource(R.string.artbg_print)) }
                    }
                } else {
                    Text(
                        stringResource(if (bg.printed) R.string.artbg_printed_note else R.string.artbg_dropped_note, colourName(bg.hex)),
                        color = TextMuted, fontSize = 12.sp, lineHeight = 17.sp
                    )
                    TextButton(onClick = { onAnswer(!bg.printed) }) {
                        Text(stringResource(if (bg.printed) R.string.artbg_drop_instead else R.string.artbg_print_instead), color = Cyan, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}


/** Top of the job page: back, the artwork, its name, and what NIRA decided (method, screens, cost). */
@Composable
private fun JobSummaryBar(
    bitmap: Bitmap?,
    title: String,
    summary: com.mos.nira.features.advice.JobSummary?,
    colourLine: String,
    onBack: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg)
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "←", color = TextWhite, fontSize = 22.sp,
                    modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable(onClick = onBack).padding(horizontal = 8.dp, vertical = 2.dp)
                )
                Spacer(Modifier.width(8.dp))
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                    )
                    Spacer(Modifier.width(12.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(title.ifBlank { stringResource(R.string.job_untitled) }, color = TextWhite, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    Text(colourLine, color = TextMuted, fontSize = 12.sp, maxLines = 1)
                }
            }
            Spacer(Modifier.height(6.dp))
            val m = summary?.method
            if (m == null) {
                Text(
                    if (summary != null && summary.unanswered > 0) pluralStringResource(R.plurals.job_answer_left, summary.unanswered, summary.unanswered)
                    else stringResource(R.string.job_answer_brief),
                    color = Amber, fontSize = 13.sp, fontWeight = FontWeight.Bold
                )
            } else {
                Text(m, color = Cyan, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                val parts = listOfNotNull(
                    summary.screens?.let { pluralStringResource(R.plurals.job_screens, it, it) },
                    summary.perPc?.let { stringResource(R.string.cost_per_pc, com.mos.nira.core.commercial.Money.format(summary.currency, it, 2)) + if (summary.sampleRates) " *" else "" },
                    if (summary.verifiedByRavi) stringResource(R.string.job_verified) else null
                )
                Text(parts.joinToString("  ·  "), color = TextWhite, fontSize = 13.sp)
            }
        }
    }
}

/** Brief · Decision · Cost · Engineering. */
@Composable
private fun JobTabs(selected: JobTab, onSelect: (JobTab) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().background(NavyBg).padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        JobTab.values().forEach { t ->
            val on = t == selected
            Text(
                stringResource(
                    when (t) {
                        JobTab.BRIEF -> R.string.tab_brief
                        JobTab.DECISION -> R.string.tab_decision
                        JobTab.COST -> R.string.tab_cost
                        JobTab.ENGINEERING -> R.string.tab_engineering
                    }
                ),
                color = if (on) Color(0xFF0F1526) else TextWhite,
                fontSize = 12.sp,
                fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.weight(1f).clip(RoundedCornerShape(20.dp))
                    .background(if (on) Cyan else CardBg)
                    .clickable { onSelect(t) }
                    .padding(vertical = 10.dp, horizontal = 4.dp)
            )
        }
    }
}

/** The full engineering pages: report, films and separation, shade library. */
@Composable
private fun EngineeringLinks(onReport: () -> Unit, onSeparation: () -> Unit, onShades: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg)
    ) {
        Column(Modifier.padding(6.dp)) {
            listOf(
                Triple(R.string.eng_report, R.string.eng_report_hint, onReport),
                Triple(R.string.eng_films, R.string.eng_films_hint, onSeparation),
                Triple(R.string.eng_shades, R.string.eng_shades_hint, onShades)
            ).forEach { (title, hint, go) ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = go).padding(12.dp)
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(title), color = TextWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text(stringResource(hint), color = TextMuted, fontSize = 11.sp, lineHeight = 15.sp)
                    }
                    Text("›", color = Cyan, fontSize = 22.sp)
                }
            }
        }
    }
}


/** Which tab fixes a note. */
private fun tabFor(k: com.mos.nira.features.advice.NoteKind): JobTab = when (k) {
    com.mos.nira.features.advice.NoteKind.BACKGROUND, com.mos.nira.features.advice.NoteKind.INPUT_KIND,
    com.mos.nira.features.advice.NoteKind.UNANSWERED, com.mos.nira.features.advice.NoteKind.CONTRAST -> JobTab.BRIEF
    com.mos.nira.features.advice.NoteKind.NEEDS_BASE, com.mos.nira.features.advice.NoteKind.NOT_VERIFIED -> JobTab.DECISION
    com.mos.nira.features.advice.NoteKind.SAVING -> JobTab.COST
    com.mos.nira.features.advice.NoteKind.LOW_RES, com.mos.nira.features.advice.NoteKind.COLOUR_CHECKS -> JobTab.ENGINEERING
}

@Composable
private fun noteText(n: com.mos.nira.features.advice.JobNote): String = when (n.kind) {
    com.mos.nira.features.advice.NoteKind.BACKGROUND -> stringResource(R.string.note_background)
    com.mos.nira.features.advice.NoteKind.INPUT_KIND -> stringResource(R.string.note_input_kind)
    com.mos.nira.features.advice.NoteKind.LOW_RES -> stringResource(R.string.note_low_res)
    com.mos.nira.features.advice.NoteKind.COLOUR_CHECKS -> pluralStringResource(R.plurals.note_colour_checks, n.count, n.count)
    com.mos.nira.features.advice.NoteKind.UNANSWERED -> pluralStringResource(R.plurals.job_answer_left, n.count, n.count)
    com.mos.nira.features.advice.NoteKind.CONTRAST -> stringResource(R.string.note_contrast, colourName(n.colourHex ?: "#000000"))
    com.mos.nira.features.advice.NoteKind.NEEDS_BASE -> stringResource(R.string.note_needs_base, colourName(n.colourHex ?: "#000000"))
    com.mos.nira.features.advice.NoteKind.NOT_VERIFIED -> stringResource(R.string.note_not_verified)
    com.mos.nira.features.advice.NoteKind.SAVING -> stringResource(R.string.note_saving, com.mos.nira.core.commercial.Money.format(n.currency, n.amount ?: 0.0, 2))
}

/** One line when closed ("NIRA noticed 3 things: ..."); every note, each opening its tab, when open. */
@Composable
private fun NoticedStrip(notes: List<com.mos.nira.features.advice.JobNote>, onOpenTab: (JobTab) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0xFF2E2612))
            .border(1.dp, Amber.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
    ) {
        Row(
            Modifier.fillMaxWidth().clickable { open = !open }.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("💡", fontSize = 15.sp)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(pluralStringResource(R.plurals.noticed_title, notes.size, notes.size), color = Amber, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                if (!open) Text(noteText(notes.first()), color = TextWhite, fontSize = 12.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            }
            Text(if (open) "▲" else "▼", color = Amber, fontSize = 12.sp)
        }
        if (open) {
            notes.forEach { n ->
                Row(
                    Modifier.fillMaxWidth().clickable { onOpenTab(tabFor(n.kind)) }.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("•", color = Amber, fontSize = 13.sp)
                    Spacer(Modifier.width(8.dp))
                    Text(noteText(n), color = TextWhite, fontSize = 12.sp, lineHeight = 16.sp, modifier = Modifier.weight(1f))
                    Text("›", color = Cyan, fontSize = 18.sp)
                }
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}
