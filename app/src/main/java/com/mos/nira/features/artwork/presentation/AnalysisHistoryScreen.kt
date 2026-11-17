package com.mos.nira.features.artwork.presentation

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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.mos.nira.features.artwork.domain.ArtworkAnalysisRecord
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val NavyBg = Color(0xFF0B1020)
private val CardBg = Color(0xFF171F35)
private val Violet = Color(0xFF8B7CFF)
private val Cyan = Color(0xFF55D6E8)
private val TextWhite = Color(0xFFF5F6FF)
private val TextMuted = Color(0xFF9BA6C0)

@Composable
fun AnalysisHistoryScreen(
    savedAnalyses: List<ArtworkAnalysisRecord>,
    onOpenRecord: (ArtworkAnalysisRecord) -> Unit,
    onDeleteRecord: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var recordToDelete by remember { mutableStateOf<ArtworkAnalysisRecord?>(null) }

    if (recordToDelete != null) {
        val record = recordToDelete!!
        AlertDialog(
            onDismissRequest = { recordToDelete = null },
            title = {
                Text(text = "Delete Analysis", color = TextWhite, fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    text = "Are you sure you want to delete the analysis for \"${record.fileName}\"? This will remove stored palettes and cached masks.",
                    color = TextMuted,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteRecord(record.id)
                        recordToDelete = null
                    }
                ) {
                    Text("Delete", color = Color(0xFFFF5252), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { recordToDelete = null }) {
                    Text("Cancel", color = TextWhite)
                }
            },
            containerColor = CardBg,
            shape = RoundedCornerShape(18.dp)
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(NavyBg)
            .padding(horizontal = 20.dp)
    ) {
        Spacer(modifier = Modifier.height(10.dp))

        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = onBack,
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("← Studio", color = TextWhite)
            }

            Text(
                text = "SAVED ANALYSES (${savedAnalyses.size})",
                color = Cyan,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (savedAnalyses.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("◈", color = TextMuted, fontSize = 40.sp)
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "No saved analyses found",
                        color = TextWhite,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Analyses will be saved locally when you process artwork.",
                        color = TextMuted,
                        fontSize = 12.sp
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(savedAnalyses, key = { it.id }) { record ->
                    HistoryItemCard(
                        record = record,
                        onClick = { onOpenRecord(record) },
                        onDelete = { recordToDelete = record }
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(20.dp))
                }
            }
        }
    }
}

@Composable
fun HistoryItemCard(
    record: ArtworkAnalysisRecord,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dateStr = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date(record.timestamp))

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
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
                verticalAlignment = Alignment.CenterVertically
            ) {
                val thumbModel = record.thumbnailPath?.let { File(it) } ?: record.imageUri
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF0F1526))
                        .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = thumbModel,
                        contentDescription = record.fileName,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = record.fileName,
                        color = TextWhite,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = "${record.width} × ${record.height} px  •  ${record.printColourCount} colors",
                        color = Cyan,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = dateStr,
                        color = TextMuted,
                        fontSize = 11.sp
                    )
                }

                TextButton(onClick = onDelete) {
                    Text("Delete", color = Color(0xFFFF6E6E), fontSize = 12.sp)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Palette Swatch Row
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                record.palette.take(8).forEach { c ->
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(Color(c.r, c.g, c.b))
                    )
                }
                if (record.palette.size > 8) {
                    Text(
                        text = "+${record.palette.size - 8}",
                        color = TextMuted,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
            }
        }
    }
}
