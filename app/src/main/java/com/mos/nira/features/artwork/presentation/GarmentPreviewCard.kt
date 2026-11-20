package com.mos.nira.features.artwork.presentation

import androidx.compose.foundation.layout.heightIn
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mos.nira.R
import com.mos.nira.core.image.GarmentPreview
import com.mos.nira.features.artwork.data.GarmentPreviewRules

private val PCardBg = Color(0xFF171F35)
private val PInner = Color(0xFF0F1526)
private val PViolet = Color(0xFFB3A8FF)
private val PCyan = Color(0xFF55D6E8)
private val PWhite = Color(0xFFF5F6FF)
private val PMuted = Color(0xFFA8B4CE)
private val PAmber = Color(0xFFFFC566)

private fun parse(hex: String) = Color(android.graphics.Color.parseColor(hex))

/**
 * "See it on your garment": garment chips, the print drawn without and with a white base, the
 * contrast check with a one-tap swap, and (photos) "tap the garment" to correct the fabric colour.
 */
@Composable
fun GarmentPreviewCard(
    rules: GarmentPreviewRules?,
    isPhoto: Boolean,
    garmentHex: String?,
    measuredFabricHex: String?,
    photo: Bitmap?,
    withoutBase: Bitmap?,
    withBase: Bitmap?,
    warnings: List<GarmentPreview.ContrastWarning>,
    swaps: Map<String, String>,
    onGarment: (String) -> Unit,
    onSwap: (from: String, to: String?) -> Unit,
    onTapGarment: (normX: Float, normY: Float) -> Unit,
    /** False on the job page, where the brief's question 1 already has the garment chips. */
    showGarmentChips: Boolean = true
) {
    if (rules == null) return
    var customOpen by remember { mutableStateOf(false) }
    if (customOpen) {
        GarmentColourPicker(
            colours = rules.namedColours.ifEmpty { rules.chips },
            current = garmentHex,
            onPick = { onGarment(it) },
            onDismiss = { customOpen = false }
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = PCardBg)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(stringResource(R.string.preview_title).uppercase(), color = PViolet, fontSize = 11.sp, letterSpacing = 1.5.sp, fontWeight = FontWeight.Bold)
            // The long note only where the garment chips are shown (the job page keeps it short).
            if (showGarmentChips) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(stringResource(R.string.preview_hint), color = PMuted, fontSize = 12.sp, lineHeight = 17.sp)
            } else {
                Text(stringResource(R.string.preview_hint_short), color = PMuted, fontSize = 11.sp)
            }
            Spacer(modifier = Modifier.height(8.dp))

            // Garment chips from the rules file, plus a custom colour.
            if (showGarmentChips) Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                rules.chips.forEach { (name, hex) ->
                    val label = when (name) {
                        "white" -> stringResource(R.string.preview_chip_white)
                        "navy" -> stringResource(R.string.preview_chip_navy)
                        "black" -> stringResource(R.string.preview_chip_black)
                        else -> name
                    }
                    FilterChip(
                        selected = garmentHex.equals(hex, ignoreCase = true),
                        onClick = { onGarment(hex) },
                        label = { Text(label, fontSize = 12.sp) },
                        leadingIcon = { Box(Modifier.size(12.dp).clip(RoundedCornerShape(3.dp)).background(parse(hex)).border(1.dp, PMuted, RoundedCornerShape(3.dp))) }
                    )
                }
                FilterChip(
                    selected = garmentHex != null && rules.chips.none { it.second.equals(garmentHex, ignoreCase = true) },
                    onClick = { customOpen = true },
                    label = { Text(stringResource(R.string.picker_more), fontSize = 12.sp, maxLines = 1, softWrap = false) }
                )
            }

            // Photos: the measured fabric, and "tap the garment" to correct it.
            if (isPhoto && measuredFabricHex != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(stringResource(R.string.preview_fabric_measured, colourName(measuredFabricHex)), color = PMuted, fontSize = 12.sp, lineHeight = 17.sp)
                if (photo != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Image(
                        bitmap = photo.asImageBitmap(),
                        contentDescription = stringResource(R.string.preview_tap_garment),
                        contentScale = ContentScale.FillBounds,
                        modifier = Modifier
                            .fillMaxWidth(0.6f)
                            .aspectRatio(photo.width.toFloat() / photo.height)
                            .clip(RoundedCornerShape(10.dp))
                            .pointerInput(photo) {
                                detectTapGestures { o -> onTapGarment(o.x / size.width, o.y / size.height) }
                            }
                    )
                }
            }

            if (garmentHex == null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(stringResource(R.string.preview_choose_garment), color = PAmber, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }

            if (withoutBase != null && withBase != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(withoutBase to R.string.preview_without_base, withBase to R.string.preview_with_base).forEach { (bmp, caption) ->
                        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = stringResource(caption),
                                contentScale = ContentScale.Fit,
                                modifier = (if (showGarmentChips) Modifier.fillMaxWidth() else Modifier.fillMaxWidth().heightIn(max = 150.dp))
                                    .aspectRatio(bmp.width.toFloat() / bmp.height, matchHeightConstraintsFirst = !showGarmentChips)
                                    .clip(RoundedCornerShape(10.dp))
                            )
                            Text(stringResource(caption), color = PMuted, fontSize = 11.sp)
                        }
                    }
                }
            }

            // Contrast check: inks too close to the garment in lightness, with a one-tap swap.
            warnings.forEach { w ->
                Spacer(modifier = Modifier.height(8.dp))
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(PInner).padding(10.dp)) {
                    Text(
                        stringResource(R.string.preview_contrast, colourName(w.inkHex), w.deltaL, rules.contrastMinDeltaL.toInt().toString()),
                        color = PAmber, fontSize = 12.sp, lineHeight = 17.sp
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        rules.swapColours.forEach { (name, hex) ->
                            val from = swaps.entries.firstOrNull { it.value == w.inkHex }?.key ?: w.inkHex
                            OutlinedButton(onClick = { onSwap(from, hex) }) {
                                Text(
                                    when (name) {
                                        "white" -> stringResource(R.string.preview_use_white)
                                        "cream" -> stringResource(R.string.preview_use_cream)
                                        else -> name
                                    },
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
            }
            swaps.forEach { (from, to) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.preview_swapped, colourName(from), colourName(to)), color = PMuted, fontSize = 12.sp, modifier = Modifier.weight(1f))
                    TextButton(onClick = { onSwap(from, null) }) { Text(stringResource(R.string.preview_undo), color = PCyan, fontSize = 12.sp) }
                }
            }
        }
    }
}
