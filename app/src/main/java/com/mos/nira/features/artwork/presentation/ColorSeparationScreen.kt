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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mos.nira.R
import com.mos.nira.features.artwork.domain.PartCResult

private val NavyBg = Color(0xFF0B1020)
private val CardBg = Color(0xFF171F35)
private val Violet = Color(0xFF8B7CFF)
private val Cyan = Color(0xFF55D6E8)
private val TextWhite = Color(0xFFF5F6FF)
private val TextMuted = Color(0xFF9BA6C0)

/**
 * The screens NIRA plans for this print: the Print Report engine's screens (Part C), the same ones
 * the decision card lists. The left-out background never appears here. Model proof, not films.
 */
@Composable
fun ColorSeparationScreen(
    partC: PartCResult?,
    isComputing: Boolean,
    selectedLayerIndex: Int,
    onSelectLayer: (Int) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val channels = partC?.channels.orEmpty()
    val index = selectedLayerIndex.takeIf { it in channels.indices } ?: -1
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(NavyBg)
            .padding(horizontal = 20.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(onClick = onBack, shape = RoundedCornerShape(10.dp)) {
                Text(stringResource(R.string.sep_back), color = TextWhite)
            }
            Text(stringResource(R.string.sep_title).uppercase(), color = Cyan, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        }
        Spacer(modifier = Modifier.height(16.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF141A2D))
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                partC?.strategyName?.let {
                    Text(it, color = TextWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                }
                Text(stringResource(R.string.sep_note), color = TextMuted, fontSize = 11.sp, lineHeight = 16.sp)
            }
        }
        Spacer(modifier = Modifier.height(16.dp))

        if (partC == null || channels.isEmpty()) {
            Text(stringResource(if (isComputing) R.string.card_working else R.string.sep_none), color = TextMuted, fontSize = 13.sp)
            return@Column
        }

        val active: Bitmap? = if (index == -1) partC.proofBitmap else channels[index].filmPositiveBitmap
        Card(
            modifier = Modifier.fillMaxWidth().height(320.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = if (index == -1) Color(0xFF070B16) else Color.White),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF232D48))
        ) {
            Box(modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(20.dp)), contentAlignment = Alignment.Center) {
                if (active != null) {
                    Image(
                        bitmap = active.asImageBitmap(),
                        contentDescription = stringResource(R.string.sep_title),
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                val label = if (index == -1) stringResource(R.string.sep_proof) else inkLabel(channels[index].inkName)
                Text(
                    text = label,
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
        Text(stringResource(R.string.sep_pick).uppercase(), color = Violet, fontSize = 11.sp, letterSpacing = 1.5.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(12.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            item {
                LayerSelectorChip(
                    title = stringResource(R.string.sep_all),
                    subtitle = stringResource(R.string.sep_proof),
                    isSelected = index == -1,
                    swatchColor = null,
                    onClick = { onSelectLayer(-1) }
                )
            }
            itemsIndexed(channels) { i, ch ->
                LayerSelectorChip(
                    title = inkLabel(ch.inkName),
                    subtitle = colourName(ch.inkHex),
                    isSelected = index == i,
                    swatchColor = Color(ch.inkRgb.r, ch.inkRgb.g, ch.inkRgb.b),
                    onClick = { onSelectLayer(i) }
                )
            }
        }
        Spacer(modifier = Modifier.height(20.dp))
        if (index >= 0) {
            val ch = channels[index]
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = CardBg)
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Text(inkLabel(ch.inkName), color = Cyan, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(10.dp))
                    SpecRow(stringResource(R.string.sep_colour), colourName(ch.inkHex))
                    SpecRow(stringResource(R.string.sep_mesh), ch.recommendedMesh + (ch.meshConfidence?.let { " ($it)" } ?: ""))
                    SpecRow(stringResource(R.string.sep_coverage), ch.formattedCoverage)
                }
            }
        }
        Spacer(modifier = Modifier.height(30.dp))
    }
}

@Composable
private fun SpecRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = TextMuted, fontSize = 12.sp)
        Text(value, color = TextWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
                Text(text = title, color = if (isSelected) Cyan else TextWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text(text = subtitle, color = TextMuted, fontSize = 10.sp)
            }
        }
    }
}
