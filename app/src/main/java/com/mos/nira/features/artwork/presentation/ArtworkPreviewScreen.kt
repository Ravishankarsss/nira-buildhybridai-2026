package com.mos.nira.features.artwork.presentation

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mos.nira.features.artwork.domain.ArtworkMetadata
import com.mos.nira.features.artwork.domain.DominantColor

private val NavyBg = Color(0xFF0B1020)
private val CardBg = Color(0xFF171F35)
private val Violet = Color(0xFF8B7CFF)
private val Cyan = Color(0xFF55D6E8)
private val TextWhite = Color(0xFFF5F6FF)
private val TextMuted = Color(0xFF9BA6C0)

@Composable
fun ArtworkPreviewScreen(
    bitmap: Bitmap?,
    metadata: ArtworkMetadata?,
    maxScreens: Int,
    garmentColor: DominantColor?,
    analysisState: AnalysisState,
    onMaxScreensChange: (Int) -> Unit,
    onSelectGarmentHex: (String?) -> Unit,
    onReplaceImage: (Uri) -> Unit,
    onStartAnalysis: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) onReplaceImage(uri)
    }

    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val transformState = rememberTransformableState { zoomChange, offsetChange, _ ->
        scale = (scale * zoomChange).coerceIn(1f, 5f)
        offset += offsetChange
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(NavyBg)
            .padding(horizontal = 20.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(modifier = Modifier.height(10.dp))

        // Top Navigation Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = onBack,
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("← Back", color = TextWhite)
            }

            OutlinedButton(
                onClick = { pickerLauncher.launch("image/*") },
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Replace Image", color = Cyan)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Interactive Zoomable Canvas Preview
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(340.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF070B16)),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF232D48))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(20.dp))
                    .transformable(state = transformState),
                contentAlignment = Alignment.Center
            ) {
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Artwork Preview",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer(
                                scaleX = scale,
                                scaleY = scale,
                                translationX = offset.x,
                                translationY = offset.y
                            )
                    )
                } else {
                    CircularProgressIndicator(color = Cyan)
                }

                Text(
                    text = if (scale > 1.05f) "Pinch to zoom (${String.format("%.1fx", scale)}) • Tap to reset" else "Pinch to zoom & pan",
                    color = TextWhite.copy(alpha = 0.6f),
                    fontSize = 11.sp,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(12.dp)
                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Technical Image Inspection Card
        if (metadata != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = CardBg)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "IMAGE SPECIFICATIONS",
                        color = Violet,
                        fontSize = 11.sp,
                        letterSpacing = 1.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Filename", color = TextMuted, fontSize = 12.sp)
                        Text(metadata.fileName, color = TextWhite, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Dimensions", color = TextMuted, fontSize = 12.sp)
                        Text(metadata.resolutionFormatted, color = Cyan, fontSize = 12.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("File Size", color = TextMuted, fontSize = 12.sp)
                        Text(metadata.fileSizeFormatted, color = TextWhite, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Print Engineering Constraints: Garment Color & Max Screens
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = CardBg)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = "PRINT CONSTRAINTS & GARMENT BASE",
                    color = Violet,
                    fontSize = 11.sp,
                    letterSpacing = 1.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Max screens slider
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(androidx.compose.ui.res.stringResource(com.mos.nira.R.string.machine_screens_label), color = TextMuted, fontSize = 12.sp)
                    Text(androidx.compose.ui.res.stringResource(com.mos.nira.R.string.machine_screens_value, maxScreens), color = Cyan, fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                }
                Text(androidx.compose.ui.res.stringResource(com.mos.nira.R.string.machine_screens_hint), color = TextMuted, fontSize = 11.sp, lineHeight = 15.sp)

                Slider(
                    value = maxScreens.toFloat(),
                    onValueChange = { onMaxScreensChange(it.toInt()) },
                    valueRange = 2f..16f,
                    steps = 13,
                    colors = SliderDefaults.colors(
                        thumbColor = Cyan,
                        activeTrackColor = Violet,
                        inactiveTrackColor = Color(0xFF2B3350)
                    )
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Garment base swatch presets
                Text("Garment Base Color (Excluded from Ink Layers):", color = TextMuted, fontSize = 12.sp)
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val presets = listOf(
                        "None" to null,
                        "White" to "#FFFFFF",
                        "Black" to "#000000",
                        "Navy" to "#0B1020",
                        "Heather" to "#B0B0B0"
                    )

                    presets.forEach { (name, hex) ->
                        val isSelected = (garmentColor?.hex?.equals(hex, ignoreCase = true) == true) || (hex == null && garmentColor == null)
                        OutlinedButton(
                            onClick = { onSelectGarmentHex(hex) },
                            shape = RoundedCornerShape(8.dp),
                            border = if (isSelected) androidx.compose.foundation.BorderStroke(1.5.dp, Cyan) else null,
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (isSelected) Color(0xFF232D48) else Color.Transparent
                            )
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (hex != null) {
                                    Box(
                                        modifier = Modifier
                                            .size(12.dp)
                                            .clip(CircleShape)
                                            .background(Color(android.graphics.Color.parseColor(hex)))
                                            .border(1.dp, Color.White.copy(alpha = 0.3f), CircleShape)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                }
                                Text(name, color = if (isSelected) Cyan else TextWhite, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Action CTA with progress display
        when (analysisState) {
            is AnalysisState.Loading -> {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(color = Cyan)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = analysisState.message,
                        color = TextMuted,
                        fontSize = 12.sp
                    )
                }
            }
            is AnalysisState.Error -> {
                Text(
                    text = analysisState.message,
                    color = Color(0xFFFF5252),
                    fontSize = 13.sp,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                Button(
                    onClick = onStartAnalysis,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Violet),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("Retry Analysis", fontWeight = FontWeight.Bold, color = TextWhite)
                }
            }
            else -> {
                Button(
                    onClick = onStartAnalysis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Violet,
                        contentColor = TextWhite
                    ),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("Auto-Detect Print Colours (CIEDE2000)  →", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(30.dp))
    }
}
