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
import com.mos.nira.features.artwork.domain.ShadeEntry
import com.mos.nira.features.ai.ArtworkAiReadCard
import com.mos.nira.features.ai.ArtworkKey
import com.mos.nira.features.advice.PrintMethodAdviceCard

private val NavyBg = Color(0xFF0B1020)
private val CardBg = Color(0xFF171F35)
private val Violet = Color(0xFF8B7CFF)
private val Cyan = Color(0xFF55D6E8)
private val TextWhite = Color(0xFFF5F6FF)
private val TextMuted = Color(0xFF9BA6C0)

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
                            Text(
                                text = "${palette.size} Print Colours Auto-Detected",
                                color = TextWhite,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            if (garmentColor != null) {
                                Text(
                                    text = "Base garment (${garmentColor.hex}) excluded from print screens",
                                    color = TextMuted,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
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
                Text(
                    text = "PRINT COLOUR PALETTE",
                    color = Violet,
                    fontSize = 11.sp,
                    letterSpacing = 1.5.sp,
                    fontWeight = FontWeight.Bold
                )

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
