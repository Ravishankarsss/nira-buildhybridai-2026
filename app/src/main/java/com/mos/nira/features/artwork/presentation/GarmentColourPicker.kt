package com.mos.nira.features.artwork.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mos.nira.R

/** The name of a garment colour from print_rules.json garment_preview.named_colours. */
@Composable
fun garmentColourName(key: String, hex: String): String = when (key) {
    "white" -> stringResource(R.string.colour_white)
    "off_white" -> stringResource(R.string.garment_off_white)
    "cream" -> stringResource(R.string.colour_cream)
    "light_grey" -> stringResource(R.string.colour_light_grey)
    "heather_grey" -> stringResource(R.string.garment_heather_grey)
    "charcoal" -> stringResource(R.string.garment_charcoal)
    "black" -> stringResource(R.string.colour_black)
    "navy" -> stringResource(R.string.colour_navy)
    "royal_blue" -> stringResource(R.string.garment_royal_blue)
    "sky_blue" -> stringResource(R.string.garment_sky_blue)
    "turquoise" -> stringResource(R.string.garment_turquoise)
    "bottle_green" -> stringResource(R.string.garment_bottle_green)
    "olive" -> stringResource(R.string.colour_olive)
    "khaki" -> stringResource(R.string.garment_khaki)
    "beige" -> stringResource(R.string.garment_beige)
    "mustard" -> stringResource(R.string.garment_mustard)
    "yellow" -> stringResource(R.string.colour_yellow)
    "orange" -> stringResource(R.string.colour_orange)
    "red" -> stringResource(R.string.colour_red)
    "maroon" -> stringResource(R.string.garment_maroon)
    "pink" -> stringResource(R.string.colour_pink)
    "lavender" -> stringResource(R.string.garment_lavender)
    "purple" -> stringResource(R.string.colour_purple)
    "brown" -> stringResource(R.string.colour_brown)
    else -> colourName(hex)
}

/**
 * "More colours": common garment colours by name. The colour code is only under "Advanced". The
 * swatches are approximate; for an exact colour the user photographs the garment and taps it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GarmentColourPicker(
    colours: List<Pair<String, String>>,
    current: String?,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var advanced by remember { mutableStateOf(false) }
    var hexText by remember { mutableStateOf(current ?: "#") }
    val validHex = Regex("^#[0-9A-Fa-f]{6}$").matches(hexText.trim())
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.picker_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(stringResource(R.string.picker_note), fontSize = 12.sp, lineHeight = 16.sp)
                Spacer(Modifier.height(10.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    colours.forEach { (key, hex) ->
                        val selected = current.equals(hex, ignoreCase = true)
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .width(76.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (selected) Color(0x3355D6E8) else Color.Transparent)
                                .clickable { onPick(hex); onDismiss() }
                                .padding(vertical = 6.dp)
                        ) {
                            Box(
                                Modifier.size(34.dp).clip(CircleShape)
                                    .background(Color(android.graphics.Color.parseColor(hex)))
                                    .border(1.dp, Color(0xFF8A93A8), CircleShape)
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(garmentColourName(key, hex), fontSize = 11.sp, lineHeight = 13.sp, textAlign = TextAlign.Center, maxLines = 2)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = { advanced = !advanced }) { Text(stringResource(R.string.picker_advanced), fontSize = 12.sp) }
                if (advanced) {
                    OutlinedTextField(value = hexText, onValueChange = { hexText = it.take(7) }, singleLine = true, label = { Text("#RRGGBB") })
                    TextButton(enabled = validHex, onClick = { onPick(hexText.trim().uppercase()); onDismiss() }) {
                        Text(stringResource(R.string.preview_ok))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.preview_cancel)) } }
    )
}
