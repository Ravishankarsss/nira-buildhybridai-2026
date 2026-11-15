package com.mos.nira.features.artwork.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mos.nira.features.artwork.domain.DominantColor
import com.mos.nira.features.artwork.domain.ShadeEntry
import com.mos.nira.features.artwork.domain.ShadeMatch

private val CardBg = Color(0xFF171F35)
private val BorderViolet = Color(0xFF8B7CFF)
private val TextWhite = Color(0xFFF5F6FF)
private val TextMuted = Color(0xFF9BA6C0)
private val AccentCyan = Color(0xFF55D6E8)

@Composable
fun ColorSwatchCard(
    color: DominantColor,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val composeColor = Color(color.r, color.g, color.b)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        border = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, BorderViolet) else null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(composeColor)
                    .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape)
            )

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = color.hex,
                        color = TextWhite,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = color.formattedPercentage,
                        color = AccentCyan,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(modifier = Modifier.height(3.dp))

                Text(
                    text = color.formattedRgb,
                    color = TextMuted,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace
                )

                if (color.assignedShade != null) {
                    Text(
                        text = "Matched Shade: [${color.assignedShade.type}] ${color.assignedShade.code} (${color.assignedShade.name})",
                        color = AccentCyan,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                } else {
                    Text(
                        text = color.formattedLab,
                        color = TextMuted.copy(alpha = 0.8f),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

@Composable
fun ColorDetailInspector(
    color: DominantColor,
    onAssignShade: (ShadeEntry) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val composeColor = Color(color.r, color.g, color.b)

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(composeColor)
                        .border(1.dp, Color.White.copy(alpha = 2.5f), RoundedCornerShape(14.dp))
                )

                Spacer(modifier = Modifier.width(16.dp))

                Column {
                    Text(
                        text = color.hex,
                        color = TextWhite,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "Coverage: ${color.formattedPercentage} (${color.pixelCount} px)",
                        color = AccentCyan,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Technical Data Grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                TechnicalDataBox(
                    title = "sRGB CHANNELS",
                    value = "R: ${color.r}\nG: ${color.g}\nB: ${color.b}",
                    modifier = Modifier.weight(1f)
                )

                TechnicalDataBox(
                    title = "CIE L*a*b* (EST.)",
                    value = "L*: ${String.format("%.1f", color.labL)}\na*: ${String.format("%.1f", color.labA)}\nb*: ${String.format("%.1f", color.labB)}",
                    modifier = Modifier.weight(1f)
                )
            }

            // Top 3 Nearest Shades Section
            if (color.nearestShades.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "NEAREST LIBRARY SHADE MATCHES (CIEDE2000)",
                    color = BorderViolet,
                    fontSize = 10.sp,
                    letterSpacing = 1.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    color.nearestShades.take(3).forEach { match ->
                        ShadeMatchRow(
                            match = match,
                            isAssigned = color.assignedShade?.code == match.shade.code,
                            onAssign = { onAssignShade(match.shade) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "Note: L*a*b* values and CIEDE2000 matches are mathematical estimates derived from digital RGB input under D65 illuminant, not direct physical spectrophotometer reflectance readings.",
                color = TextMuted.copy(alpha = 0.7f),
                fontSize = 10.sp,
                lineHeight = 14.sp
            )
        }
    }
}

@Composable
fun ShadeMatchRow(
    match: ShadeMatch,
    isAssigned: Boolean,
    onAssign: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (isAssigned) Color(0xFF232D48) else Color(0xFF0F1526))
            .border(
                1.dp,
                if (isAssigned) AccentCyan else Color.White.copy(alpha = 0.05f),
                RoundedCornerShape(10.dp)
            )
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "[${match.shade.type}] ${match.shade.code}",
                    color = TextWhite,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = match.shade.name,
                    color = TextMuted,
                    fontSize = 11.sp
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "${String.format("%.2f", match.deltaE00)} ΔE00 • ${match.grade} (${match.shade.libraryName})",
                color = AccentCyan,
                fontSize = 10.sp
            )
        }

        OutlinedButton(
            onClick = onAssign,
            shape = RoundedCornerShape(8.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 4.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = if (isAssigned) BorderViolet else Color.Transparent
            )
        ) {
            Text(if (isAssigned) "Assigned ✓" else "Assign", fontSize = 10.sp, color = TextWhite)
        }
    }
}

@Composable
private fun TechnicalDataBox(
    title: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(Color(0xFF0F1526), RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        Column {
            Text(
                text = title,
                color = BorderViolet,
                fontSize = 10.sp,
                letterSpacing = 1.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = value,
                color = TextWhite,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                lineHeight = 17.sp
            )
        }
    }
}
