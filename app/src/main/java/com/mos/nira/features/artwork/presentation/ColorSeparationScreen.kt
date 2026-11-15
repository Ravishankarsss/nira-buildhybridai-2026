package com.mos.nira.features.artwork.presentation

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.mos.nira.core.image.ColorSeparator

private val NavyBg = Color(0xFF0B1020)
private val CardBg = Color(0xFF171F35)
private val Violet = Color(0xFF8B7CFF)
private val Cyan = Color(0xFF55D6E8)
private val TextWhite = Color(0xFFF5F6FF)
private val TextMuted = Color(0xFF9BA6C0)

@Composable
fun ColorSeparationScreen(
    separationResult: ColorSeparator.SeparationResult?,
    selectedLayerIndex: Int,
    onSelectLayer: (Int) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(NavyBg)
            .padding(horizontal = 20.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(modifier = Modifier.height(10.dp))

        // Navigation header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = onBack,
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("← Color Intelligence", color = TextWhite)
            }

            Text(
                text = "PRELIMINARY SEPARATIONS",
                color = Cyan,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Disclaimer Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF141A2D))
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "TECHNICAL NOTE",
                    color = Violet,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "This preview represents a digital cluster separation for preliminary design verification. It is not a production-ready halftone screen or mechanical print plate.",
                    color = TextMuted,
                    fontSize = 11.sp,
                    lineHeight = 16.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Layer Canvas Preview
        val activeBitmap = if (selectedLayerIndex == -1) {
            separationResult?.compositeBitmap
        } else {
            separationResult?.layers?.getOrNull(selectedLayerIndex)?.tintedBitmap
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(320.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF070B16)),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF232D48))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(20.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (activeBitmap != null) {
                    Image(
                        bitmap = activeBitmap.asImageBitmap(),
                        contentDescription = "Separation Layer",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Text("No separation data available", color = TextMuted)
                }

                // Layer label overlay
                val layerLabel = if (selectedLayerIndex == -1) {
                    "Combined Composite Preview"
                } else {
                    val layer = separationResult?.layers?.getOrNull(selectedLayerIndex)
                    "Layer ${selectedLayerIndex + 1}: ${layer?.dominantColor?.hex ?: ""} (${String.format("%.1f%%", layer?.pixelCoverage ?: 0f)})"
                }

                Text(
                    text = layerLabel,
                    color = TextWhite,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(12.dp)
                        .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Layer Selection Controls
        Text(
            text = "SELECT SEPARATION LAYER",
            color = Violet,
            fontSize = 11.sp,
            letterSpacing = 1.5.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(12.dp))

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            // Combined composite pill
            item {
                LayerSelectorChip(
                    title = "All Layers",
                    subtitle = "Composite",
                    isSelected = selectedLayerIndex == -1,
                    swatchColor = null,
                    onClick = { onSelectLayer(-1) }
                )
            }

            // Individual color layer pills
            if (separationResult != null) {
                itemsIndexed(separationResult.layers) { index, layer ->
                    val color = Color(layer.dominantColor.r, layer.dominantColor.g, layer.dominantColor.b)
                    LayerSelectorChip(
                        title = "Layer ${index + 1}",
                        subtitle = layer.dominantColor.hex,
                        isSelected = selectedLayerIndex == index,
                        swatchColor = color,
                        onClick = { onSelectLayer(index) }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Active Layer Specs
        if (selectedLayerIndex >= 0 && separationResult != null) {
            val layer = separationResult.layers.getOrNull(selectedLayerIndex)
            if (layer != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = CardBg)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Text(
                            text = "LAYER ${selectedLayerIndex + 1} SPECIFICATIONS",
                            color = Cyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Target Ink / Color", color = TextMuted, fontSize = 12.sp)
                            Text(layer.dominantColor.hex, color = TextWhite, fontSize = 12.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Color Values", color = TextMuted, fontSize = 12.sp)
                            Text(layer.dominantColor.formattedRgb, color = TextWhite, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Estimated Lab", color = TextMuted, fontSize = 12.sp)
                            Text(layer.dominantColor.formattedLab, color = TextWhite, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Surface Coverage", color = TextMuted, fontSize = 12.sp)
                            Text(String.format("%.1f%%", layer.pixelCoverage), color = Cyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(30.dp))
    }
}

@Composable
fun LayerSelectorChip(
    title: String,
    subtitle: String,
    isSelected: Boolean,
    swatchColor: Color?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) Color(0xFF263254) else CardBg
        ),
        border = if (isSelected) androidx.compose.foundation.BorderStroke(1.5.dp, Violet) else null
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (swatchColor != null) {
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(swatchColor)
                        .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape)
                )
                Spacer(modifier = Modifier.width(10.dp))
            }

            Column {
                Text(
                    text = title,
                    color = if (isSelected) Cyan else TextWhite,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = subtitle,
                    color = TextMuted,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}
