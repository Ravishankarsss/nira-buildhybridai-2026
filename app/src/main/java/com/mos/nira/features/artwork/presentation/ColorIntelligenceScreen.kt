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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var showMergeDialog by remember { mutableStateOf(false) }
    var mergeTargetId by remember { mutableStateOf<Int?>(null) }
    var hexInput by remember { mutableStateOf("#") }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Add Spot Colour", color = TextWhite, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Enter custom 6-digit HEX color code (e.g. #FF5500):", color = TextMuted, fontSize = 12.sp)
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
                    Text("Add & Reassign", color = Cyan, fontWeight = FontWeight.Bold)
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
            title = { Text("Merge ${selectedColor.hex} Into...", color = TextWhite, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Select which colour to absorb this shade into:", color = TextMuted, fontSize = 12.sp)
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
                            Text(target.hex, color = TextWhite, fontFamily = FontFamily.Monospace, fontSize = 13.sp)
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

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(NavyBg)
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(10.dp))

            // Navigation bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onBack,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("← Preview", color = TextWhite)
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(
                        onClick = onOpenShadeLibrary,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Shades", color = Cyan, fontSize = 11.sp)
                    }

                    OutlinedButton(
                        onClick = onNavigateToPrintReport,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Report", color = Cyan, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }

                    Button(
                        onClick = onNavigateToSeparation,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Violet,
                            contentColor = TextWhite
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Separation →", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Mini Artwork Thumbnail Card
            if (bitmap != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = CardBg)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "Analyzed Artwork",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(64.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                        )

                        Spacer(modifier = Modifier.width(14.dp))

                        Column {
                            Text(
                                text = "CIEDE2000 PALETTE & SHADE MATCHING",
                                color = Cyan,
                                fontSize = 11.sp,
                                letterSpacing = 1.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            // Headline count = the colours the Print Report uses (garment removed).
                            Text(
                                text = when {
                                    measuredPrintColours != null -> {
                                        // Colours that may be fold shadows are shown as "+N if ink".
                                        val flagged = measuredPrintColours.count { it.possibleFoldShadow }
                                        val sure = measuredPrintColours.size - flagged
                                        pluralStringResource(R.plurals.measured_print_colours, sure, sure) +
                                            (if (flagged > 0) " " + stringResource(R.string.colour_possible_shadow_extra, flagged) else "")
                                    }
                                    isMeasuringPrintColours -> stringResource(R.string.colour_measuring)
                                    else -> stringResource(R.string.colour_clusters_title, palette.size)
                                },
                                color = TextWhite,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            (measuredGarmentHex ?: garmentColor?.hex)?.let { hex ->
                                Text(
                                    text = stringResource(
                                        R.string.colour_garment_excluded,
                                        ColourLabel.withNearestShadeName(hex, garmentNameShades)
                                    ),
                                    color = TextMuted,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        // "This looks like a photo. Treat it as a photo?" when the signals disagree with the mode.
        if (inputKindAssessment != null && inputKindAssessment.askUser && !inputKindConfirmed) {
            item {
                InputKindQuestionCard(inputKindAssessment, onAnswerInputKind)
            }
        }

        // Fold shadow notes (photos of light garments).
        if (foldShadowsRemoved > 0 || shadowTestLowResolution) {
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

        // Measured print colours: the ones used for screens and ink.
        if (!measuredPrintColours.isNullOrEmpty()) {
            item {
                MeasuredPrintColoursCard(measuredPrintColours, onAnswerFoldShadow)
            }
        }

        // AI read of the artwork (Firebase AI Logic). Engine numbers are passed in as facts.
        item {
            ArtworkAiReadCard(
                bitmap = bitmap,
                printColourHexes = palette.filter { !it.isGarmentBase }.map { it.hex },
                garmentHex = garmentColor?.hex,
                factColourHexes = measuredPrintColourHexes,
                factGarmentHex = measuredGarmentHex
            )
        }

        // Print method advice from print_techniques.json (rules, not AI).
        item {
            PrintMethodAdviceCard(
                garmentColor = garmentColor,
                artworkKey = ArtworkKey.of(bitmap, palette.filter { !it.isGarmentBase }.map { it.hex })
            )
        }

        // Active Selected Color Technical Inspector + Editing Toolbar + Top 3 Shade Matches
        if (selectedColor != null) {
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
                    Text("+ Add Custom HEX", color = Cyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Palette Swatches List
        items(palette) { color ->
            ColorSwatchCard(
                color = color,
                isSelected = selectedColor?.id == color.id,
                onClick = { onColorSelected(color) }
            )
        }

        // Perceptual Color Families / Grouping Section
        if (colorGroups.isNotEmpty()) {
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

            items(colorGroups) { group ->
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
                                text = "Family #${group.groupId + 1}: ${group.primaryColor.hex}",
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

        item {
            Spacer(modifier = Modifier.height(30.dp))
        }
    }
}

/** The Print Report engine's measured print colours, with coverage and nearest shade. */
@Composable
private fun MeasuredPrintColoursCard(
    colours: List<MeasuredColor>,
    onAnswerFoldShadow: (points: List<Pair<Float, Float>>, isInk: Boolean) -> Unit
) {
    var asking by remember { mutableStateOf<MeasuredColor?>(null) }
    asking?.let { c ->
        AlertDialog(
            onDismissRequest = { asking = null },
            title = { Text(stringResource(R.string.colour_confirm_title)) },
            text = { Text(c.hex + "\n\n" + stringResource(R.string.colour_confirm_body)) },
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
                    modifier = if (c.possibleFoldShadow) Modifier.clickable { asking = c } else Modifier
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
                        Text(c.hex, color = TextWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text(
                            text = stringResource(R.string.colour_coverage, c.formattedCoverage) +
                                (c.nearestShade?.let { "  •  ${it.code} ${it.name}" } ?: ""),
                            color = TextMuted,
                            fontSize = 12.sp
                        )
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
