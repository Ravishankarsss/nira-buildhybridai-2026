package com.mos.nira.features.artwork.presentation

import com.mos.nira.R
import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mos.nira.features.artwork.domain.ErrorCurvePoint
import com.mos.nira.features.artwork.domain.FilmPositiveChannel
import com.mos.nira.features.artwork.domain.InkChannelConsumption
import com.mos.nira.features.artwork.domain.MeasuredColor
import com.mos.nira.features.artwork.domain.NormalizedRect
import com.mos.nira.features.artwork.domain.PartAResult
import com.mos.nira.features.artwork.domain.PartBResult
import com.mos.nira.features.artwork.domain.PartCResult
import com.mos.nira.features.artwork.domain.PartDResult
import com.mos.nira.features.artwork.domain.PrintJobInputs
import com.mos.nira.features.artwork.domain.SeparationStrategyType
import com.mos.nira.features.artwork.domain.StrategyEvaluationResult

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PrintReportScreen(
    partAResult: PartAResult?,
    partBResult: PartBResult?,
    partCResult: PartCResult?,
    partDResult: PartDResult?,
    inputs: PrintJobInputs,
    isComputingA: Boolean,
    isComputingB: Boolean,
    isComputingC: Boolean,
    isComputingD: Boolean,
    errorMessage: String?,
    exportMessage: String?,
    onUpdatePrintWidth: (Double) -> Unit,
    onUpdateGarmentHex: (String) -> Unit,
    onUpdateGarmentThreshold: (Double) -> Unit,
    onUpdateMeshTpi: (Int) -> Unit,
    onUpdateStrokes: (Int) -> Unit,
    onUpdateWastage: (Double) -> Unit,
    onUpdateOrderPieces: (Int) -> Unit,
    onToggleWhiteBase: (Boolean?) -> Unit,
    onToggleWhitePrintActsAsBase: (Boolean) -> Unit,
    onSetUserGarmentColor: (String?) -> Unit,
    onAddErasedRect: (NormalizedRect) -> Unit,
    onClearErasedRects: () -> Unit,
    onSelectStrategy: (SeparationStrategyType) -> Unit,
    onExportSeparationSheet: () -> Unit,
    onShareSeparationSheet: () -> Unit,
    onClearExportMessage: () -> Unit,
    onRecalculate: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    // The decision card comes first; the sections below are its details.
    header: @Composable () -> Unit = {},
    footer: @Composable () -> Unit = {}
) {
    var eraseModeEnabled by remember { mutableStateOf(false) }
    var widthInputText by remember(inputs.printWidthCm) { mutableStateOf(inputs.printWidthCm.toString()) }
    var orderPiecesText by remember(inputs.orderPieces) { mutableStateOf(inputs.orderPieces.toString()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "NIRA Print Report",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Technical Print Engineering Report",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onRecalculate) {
                        Icon(Icons.Default.Refresh, contentDescription = "Recalculate")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        modifier = modifier
    ) { paddingValues ->
        if (isComputingA && partAResult == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Calculating print area, mask & CIEDE2000 palette...")
                }
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { Spacer(modifier = Modifier.height(4.dp)) }

            // Error banner if any
            if (errorMessage != null) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = errorMessage,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            // Low resolution: a caution (not an error) in the user's language, with the size to ask for.
            if (partAResult?.lowResolutionWarning != null && partAResult.minPxPerCm > 0.0) {
                item {
                    val amber = Color(0xFFFFC857)
                    val needPx = kotlin.math.ceil(partAResult.minPxPerCm * partAResult.printWidthCm).toInt()
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF2E2612)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, amber.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = amber)
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(stringResource(R.string.res_warn_title), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = amber)
                                Text(
                                    stringResource(
                                        R.string.res_warn_body,
                                        partAResult.pixelsPerCm, kotlin.math.round(partAResult.pixelsPerCm * 2.54).toInt(),
                                        partAResult.printWidthCm,
                                        partAResult.minPxPerCm, kotlin.math.round(partAResult.minPxPerCm * 2.54).toInt()
                                    ),
                                    style = MaterialTheme.typography.bodySmall, color = Color(0xFFF1E6C8)
                                )
                                Text(
                                    if (inputs.inputKind == com.mos.nira.core.image.InputKind.PHOTO) stringResource(R.string.res_warn_fix_photo)
                                    else stringResource(R.string.res_warn_fix_artwork, needPx),
                                    style = MaterialTheme.typography.bodySmall, color = Color(0xFFF1E6C8)
                                )
                            }
                        }
                    }
                }
            }

            item { header() }

            // SECTION 1: PRINT MASK & RECTANGLE ERASE TOOL
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "1. Print Area & Mask",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                FilterChip(
                                    selected = eraseModeEnabled,
                                    onClick = { eraseModeEnabled = !eraseModeEnabled },
                                    label = { Text(if (eraseModeEnabled) "Erase Active" else "Erase Tool") },
                                    leadingIcon = {
                                        Icon(Icons.Default.Clear, contentDescription = null, modifier = Modifier.size(16.dp))
                                    }
                                )
                                if (inputs.erasedRectangles.isNotEmpty()) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    IconButton(
                                        onClick = onClearErasedRects,
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Default.Clear, contentDescription = "Clear Erased", tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }

                        if (eraseModeEnabled) {
                            Text(
                                text = "Drag your finger over unwanted elements (e.g. captions, hands) on the preview below to exclude them from the print mask.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
                            )
                        } else {
                            Text(
                                text = "Garment background automatically masked out. Inked print pixels shown in colour with tight print box.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
                            )
                        }

                        // Canvas View with Drag Detector for Erase Tool
                        val maskBmp = partAResult?.maskBitmap
                        if (maskBmp != null) {
                            InteractiveMaskCanvas(
                                maskBitmap = maskBmp,
                                erasedRectangles = inputs.erasedRectangles,
                                eraseEnabled = eraseModeEnabled,
                                onRectErased = { normRect ->
                                    onAddErasedRect(normRect)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                            )
                        }

                        // Quantitative Mask & Area Metrics
                        if (partAResult != null) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surface,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("Print Dimensions:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(
                                            String.format("%.1f × %.1f cm", partAResult.printWidthCm, partAResult.printHeightCm),
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("Print Bounding Box Area:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(
                                            String.format("%.1f cm²", partAResult.totalBoxAreaCm2),
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("Effective Inked Print Area:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                                        Text(
                                            String.format("%.1f cm² (%.1f%%)", partAResult.effectivePrintAreaCm2, partAResult.printMaskCoveragePercent),
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("Print Box Resolution:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(
                                            String.format("%.1f px/cm (~%d dpi)", partAResult.pixelsPerCm, (partAResult.pixelsPerCm * 2.54).toInt()),
                                            style = MaterialTheme.typography.bodySmall,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }

                                    // Measured garment profile if detected
                                    val garmentProf = partAResult.garmentProfile
                                    if (garmentProf != null) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text("Measured Fabric:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(12.dp)
                                                        .clip(CircleShape)
                                                        .background(Color(android.graphics.Color.parseColor(garmentProf.estimatedHex)))
                                                        .border(0.5.dp, Color.Gray, CircleShape)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = "${colourName(garmentProf.estimatedHex)} · L* ${String.format("%.1f", garmentProf.medianLab.l)} (spread ΔE ${String.format("%.1f", garmentProf.spreadDeltaE00)})",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontFamily = FontFamily.Monospace
                                                )
                                            }
                                        }
                                    }

                                    if (inputs.erasedRectangles.isNotEmpty()) {
                                        Text(
                                            "${inputs.erasedRectangles.size} manual exclusion zone(s) applied",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // SECTION 2: PRODUCTION INPUTS & RULE PARAMETERS
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "2. Engineering Inputs",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        // The garment is chosen once, in question 1 of the brief above.
                        val garmentNow = inputs.targetGarmentHex ?: inputs.userGarmentHex ?: partAResult?.garmentProfile?.estimatedHex
                        Text(
                            text = stringResource(R.string.report_garment_from_brief, garmentNow?.let { colourName(it) } ?: "—"),
                            style = MaterialTheme.typography.bodySmall
                        )

                        // White Underbase Controls
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("White Underbase Controls", style = MaterialTheme.typography.labelMedium)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilterChip(
                                    selected = inputs.useWhiteBaseOverride == null,
                                    onClick = { onToggleWhiteBase(null) },
                                    label = { Text("Auto (Dark Fabric)") }
                                )
                                FilterChip(
                                    selected = inputs.useWhiteBaseOverride == true,
                                    onClick = { onToggleWhiteBase(true) },
                                    label = { Text("Force Underbase") }
                                )
                                FilterChip(
                                    selected = inputs.useWhiteBaseOverride == false,
                                    onClick = { onToggleWhiteBase(false) },
                                    label = { Text("No Underbase") }
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "White Print Acts As Base (Solid White Spot)",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                FilterChip(
                                    selected = inputs.whitePrintActsAsBase,
                                    onClick = { onToggleWhitePrintActsAsBase(!inputs.whitePrintActsAsBase) },
                                    label = { Text(if (inputs.whitePrintActsAsBase) "Enabled" else "Off") }
                                )
                            }
                        }

                        // Non-white Garment Mask Threshold Setting
                        if (!inputs.garmentColorHex.equals("#FFFFFF", ignoreCase = true)) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surface,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            "Garment Mask Threshold (CIEDE2000):",
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                        Text(
                                            String.format("%.1f ΔE00", inputs.garmentThresholdDeltaE00),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    Slider(
                                        value = inputs.garmentThresholdDeltaE00.toFloat(),
                                        onValueChange = { onUpdateGarmentThreshold(it.toDouble()) },
                                        valueRange = 1.0f..15.0f,
                                        steps = 13
                                    )
                                }
                            }
                        }

                        // Print Width & Height
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedTextField(
                                value = widthInputText,
                                onValueChange = { newText ->
                                    widthInputText = newText
                                    val v = newText.toDoubleOrNull()
                                    if (v != null && v in 1.0..100.0) {
                                        onUpdatePrintWidth(v)
                                    }
                                },
                                label = { Text("Print Width (cm)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )

                            Column(modifier = Modifier.weight(1f)) {
                                Text("Height (Calculated)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    text = String.format("%.1f cm", partAResult?.printHeightCm ?: 0.0),
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // Order Quantity (Pieces)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedTextField(
                                value = orderPiecesText,
                                onValueChange = { newText ->
                                    orderPiecesText = newText
                                    val pcs = newText.toIntOrNull()
                                    if (pcs != null && pcs > 0) {
                                        onUpdateOrderPieces(pcs)
                                    }
                                },
                                label = { Text("Order Quantity (Pieces)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )

                            FlowRow(
                                modifier = Modifier.weight(1f),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                listOf(50, 100, 250, 500).forEach { pcs ->
                                    FilterChip(
                                        selected = inputs.orderPieces == pcs,
                                        onClick = {
                                            orderPiecesText = pcs.toString()
                                            onUpdateOrderPieces(pcs)
                                        },
                                        label = { Text("$pcs") }
                                    )
                                }
                            }
                        }

                        // Mesh Count Selector & Confidence Tag
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Mesh Count (tpi)", style = MaterialTheme.typography.labelMedium)
                                Spacer(modifier = Modifier.width(8.dp))
                                ConfidenceBadge("sourced")
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf(110, 137, 156, 200, 230, 305).forEach { mesh ->
                                    FilterChip(
                                        selected = inputs.meshTpi == mesh,
                                        onClick = { onUpdateMeshTpi(mesh) },
                                        label = { Text("$mesh") }
                                    )
                                }
                            }
                        }

                        // Squeegee Strokes & Wastage %
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("Strokes", style = MaterialTheme.typography.labelMedium)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    ConfidenceBadge("sourced")
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    listOf(1, 2).forEach { s ->
                                        FilterChip(
                                            selected = inputs.strokes == s,
                                            onClick = { onUpdateStrokes(s) },
                                            label = { Text("$s") }
                                        )
                                    }
                                }
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("Wastage %", style = MaterialTheme.typography.labelMedium)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    ConfidenceBadge("assumption")
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    listOf(5.0, 10.0, 15.0).forEach { w ->
                                        FilterChip(
                                            selected = inputs.wastagePercent == w,
                                            onClick = { onUpdateWastage(w) },
                                            label = { Text("${w.toInt()}%") }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // SECTION 3: MEASURED PALETTE TABLE
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "3. Measured Print Palette",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "CIEDE2000 clustering (K=16) • merged < 3.2 ΔE00 • pruned < 1.5%",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "${partAResult?.measuredPalette?.size ?: 0} Screens",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            val palette = partAResult?.measuredPalette.orEmpty()
            if (palette.isEmpty()) {
                item {
                    Text(
                        "No print colours detected in active mask.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                items(palette, key = { it.id }) { color ->
                    MeasuredColorRow(color = color)
                }
            }

            // SECTION 4: FLAT-SEPARATION ERROR CURVE
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "4. Flat-Separation Error Curve",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Text(
                            text = "Mean CIEDE2000 color fidelity error of posterised artwork across screen counts K = 4, 6, 8, 10, 12, 16.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        val curve = partAResult?.errorCurve.orEmpty()
                        if (curve.isNotEmpty()) {
                            ErrorCurveVisualizer(curve = curve)
                        }
                    }
                }
            }

            // SECTION 5: SEPARATION STRATEGIES COMPARISON TABLE (PART B)
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "5. Separation Strategies",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )

                            if (partBResult != null) {
                                Surface(
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = "${partBResult.executionTimeMs} ms (1024px)",
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }

                        // Mandatory Model Disclaimer
                        Text(
                            text = "“Model result. No dot gain or registration error included.”",
                            style = MaterialTheme.typography.bodySmall,
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )

                        // Dark Garment Global Notice
                        if (partBResult?.isDarkGarment == true) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.Warning,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Dark or coloured garment: a white base is needed under the print. Each option below shows the white screens it adds; a white print colour can also act as the base. CMYK alone is not suitable without a white base.",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }

                        if (isComputingB) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 20.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    CircularProgressIndicator(modifier = Modifier.size(28.dp))
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        "Solving Lawson-Hanson NNLS on 1024px artwork...",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        } else if (partBResult != null) {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                partBResult.strategies.forEach { strategy ->
                                    val isSelected = partBResult.selectedStrategyType == strategy.strategyType
                                    StrategyCard(
                                        strategy = strategy,
                                        isSelected = isSelected,
                                        onSelect = { onSelectStrategy(strategy.strategyType) }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // -------------------------------------------------------------
            // Section 6: Screens & Proof (Part C)
            // -------------------------------------------------------------
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "6. Screens & Proof Preview",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Chosen Strategy: ${partCResult?.strategyName ?: "Loading..."}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            if (partCResult != null) {
                                Surface(
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = "${partCResult.channels.size} Screens  |  ${partCResult.executionTimeMs} ms",
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }

                        // Mandatory Preliminary Separation Disclaimer
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Info,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "“Preliminary digital separation, not print-ready films. Model has no dot gain or registration error. Needs original artwork at about 300 dpi at print size.”",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        if (isComputingC) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    CircularProgressIndicator(modifier = Modifier.size(28.dp))
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        "Generating greyscale film positives & proof...",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        } else if (partCResult != null) {
                            // Side-by-side: Original Artwork vs Reconstructed Proof
                            Text(
                                text = "Side-by-Side Proof Comparison",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                // Original Artwork Pane
                                Card(
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                                ) {
                                    Column(modifier = Modifier.padding(8.dp)) {
                                        Text(
                                            text = "Original Artwork",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        val origBmp = partCResult.originalBitmap
                                        if (origBmp != null) {
                                            Image(
                                                bitmap = origBmp.asImageBitmap(),
                                                contentDescription = "Original Artwork",
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(130.dp)
                                                    .clip(RoundedCornerShape(6.dp)),
                                                contentScale = androidx.compose.ui.layout.ContentScale.Fit
                                            )
                                        } else {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(130.dp)
                                                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(6.dp)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text("Original unavailable", style = MaterialTheme.typography.labelSmall)
                                            }
                                        }
                                    }
                                }

                                // Reconstructed Model Proof Pane
                                Card(
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                                ) {
                                    Column(modifier = Modifier.padding(8.dp)) {
                                        Text(
                                            text = "Reconstructed Proof",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        val proofBmp = partCResult.proofBitmap
                                        if (proofBmp != null) {
                                            Image(
                                                bitmap = proofBmp.asImageBitmap(),
                                                contentDescription = "Reconstructed Model Proof",
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(130.dp)
                                                    .clip(RoundedCornerShape(6.dp)),
                                                contentScale = androidx.compose.ui.layout.ContentScale.Fit
                                            )
                                        } else {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(130.dp)
                                                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(6.dp)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text("Proof unavailable", style = MaterialTheme.typography.labelSmall)
                                            }
                                        }
                                    }
                                }
                            }

                            // Film Positives Channels Section
                            Text(
                                text = "Film Positive Screens (Black = 100% Ink, White = 0% Ink)",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )

                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                partCResult.channels.forEach { channel ->
                                    FilmPositiveCard(channel = channel)
                                }
                            }

                            // Composite Separation Sheet & Export
                            Spacer(modifier = Modifier.height(6.dp))
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "Composite Separation Sheet",
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = "All screens + proofs compiled side-by-side",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }

                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            OutlinedButton(
                                                onClick = onExportSeparationSheet,
                                                shape = RoundedCornerShape(8.dp)
                                            ) {
                                                Text("Save PNG", style = MaterialTheme.typography.labelMedium)
                                            }
                                            Button(
                                                onClick = onShareSeparationSheet,
                                                shape = RoundedCornerShape(8.dp)
                                            ) {
                                                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Share", style = MaterialTheme.typography.labelMedium)
                                            }
                                        }
                                    }

                                    if (exportMessage != null) {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(8.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = exportMessage,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Medium,
                                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                                )
                                                IconButton(
                                                    onClick = onClearExportMessage,
                                                    modifier = Modifier.size(20.dp)
                                                ) {
                                                    Icon(Icons.Default.Clear, contentDescription = "Clear", modifier = Modifier.size(14.dp))
                                                }
                                            }
                                        }
                                    }

                                    val sheetBmp = partCResult.compositeSheetBitmap
                                    if (sheetBmp != null) {
                                        Image(
                                            bitmap = sheetBmp.asImageBitmap(),
                                            contentDescription = "Composite Separation Sheet",
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(200.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp)),
                                            contentScale = androidx.compose.ui.layout.ContentScale.Fit
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // -------------------------------------------------------------
            // Section 7: INK CONSUMPTION REQUIREMENTS (Part D)
            // -------------------------------------------------------------
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "7. Ink Consumption Requirements",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Formula: Ink (g) = Coverage × Box Area × Deposit × Strokes × (1 + Wastage)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            ConfidenceBadge("sourced")
                        }

                        // Mandatory Loss Disclaimer
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Info,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "“Screen residue and set-up loss are not included.”",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }

                        if (isComputingD) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            }
                        } else if (partDResult != null) {
                            // Total Job Summary Card
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surface,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            "Total Ink / Garment:",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            String.format("%.2f g  (min %.2f, max %.2f g)", partDResult.totalGramsPerGarment, partDResult.totalGramsPerGarmentMin, partDResult.totalGramsPerGarmentMax),
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }

                                    val orderScale = partDResult.orderPieces / 100.0
                                    val orderGrams = partDResult.totalGramsPer100Pieces * orderScale
                                    val orderKg = orderGrams / 1000.0
                                    val orderMinKg = (partDResult.totalGramsPer100PiecesMin * orderScale) / 1000.0
                                    val orderMaxKg = (partDResult.totalGramsPer100PiecesMax * orderScale) / 1000.0

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            "Total Ink for ${partDResult.orderPieces} pieces:",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            String.format("%.1f g (%.3f kg)", orderGrams, orderKg),
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            "Order Range (Min – Max):",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            String.format("%.3f kg – %.3f kg", orderMinKg, orderMaxKg),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                    if (partDResult.unavailableInks.isNotEmpty()) {
                                        Text(
                                            "Not included (no ink rule in print_rules.json for its mesh): " +
                                                partDResult.unavailableInks.joinToString(", "),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }

                            // Per-channel consumption table
                            Text(
                                text = "Channel Breakdown (${partDResult.channels.size} screens)",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )

                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                partDResult.channels.forEach { ch ->
                                    InkConsumptionChannelRow(channel = ch, orderPieces = partDResult.orderPieces)
                                }
                            }
                        }
                    }
                }
            }

            item {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF2E7D32), modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "All figures above are calculated on this phone by the NIRA engine. No AI is used for the numbers.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item { footer() }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}

@Composable
fun InkConsumptionChannelRow(channel: InkChannelConsumption, orderPieces: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(Color(android.graphics.Color.parseColor(channel.inkHex)))
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(3.dp))
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Screen ${channel.channelIndex}: ${inkLabel(channel.inkName)}",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        text = (if (channel.meshTpiMax != channel.meshTpi) "${channel.meshTpi}–${channel.meshTpiMax} tpi" else "${channel.meshTpi} tpi") +
                            (if (channel.inkAvailable) " • ${channel.depositGPerM2Default.toInt()} g/m²" else " • no ink rule"),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Active Coverage: ${String.format("%.1f%%", channel.boxCoveragePercent)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Strokes: ${channel.strokes} • Wastage: ${channel.wastagePercent.toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Per Garment:",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 10.sp
                    )
                    Text(
                        text = if (channel.inkAvailable) String.format("%.2f g", channel.inkGramsPerGarment) else "not available",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    val scale = orderPieces / 100.0
                    val chOrderGrams = channel.inkGramsPer100Pieces * scale
                    val chOrderKg = chOrderGrams / 1000.0

                    Text(
                        text = "For $orderPieces Pieces:",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 10.sp
                    )
                    Text(
                        text = if (channel.inkAvailable) String.format("%.1f g (%.3f kg)", chOrderGrams, chOrderKg) else "not available",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Text(
                text = if (channel.inkAvailable) "Deposit range: ${channel.depositGPerM2Min.toInt()}–${channel.depositGPerM2Max.toInt()} g/m² (${channel.depositConfidence})"
                    else "Ink: not available. print_rules.json has no ink deposit for this mesh.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 9.sp
            )
        }
    }
}

@Composable
fun StrategyCard(
    strategy: StrategyEvaluationResult,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect() },
        shape = RoundedCornerShape(12.dp),
        border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Header Row: Radio + Title + Recommendation Badge + Screen Count
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    RadioButton(
                        selected = isSelected,
                        onClick = onSelect,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = strategy.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    if (strategy.isRecommended) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0xFF2E7D32)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Star, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
                                Spacer(modifier = Modifier.width(2.dp))
                                Text(
                                    text = "RECOMMENDED",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Text(
                        text = "${strategy.screenCount} Screens",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }

            // Recommendation Reason if available
            if (strategy.recommendationReason != null) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xFFE8F5E9),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = strategy.recommendationReason,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF2E7D32),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Transition Note if detected
            if (strategy.transitionNote != null) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xFFE3F2FD),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = Color(0xFF1565C0), modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = strategy.transitionNote,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF1565C0),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // Inks Swatches Row
            if (strategy.inkHexes.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    strategy.inkHexes.forEachIndexed { idx, hex ->
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .clip(CircleShape)
                                .background(Color(android.graphics.Color.parseColor(hex)))
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                        )
                    }
                    Text(
                        text = strategy.inkNames.joinToString(", "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Quality Figures Table (Three metrics required by Part B)
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Metric 1: Mean ΔE00
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Mean Error", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
                        Text(
                            text = String.format("ΔE %.2f", strategy.meanDeltaE00),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = when {
                                strategy.meanDeltaE00 < 2.5 -> Color(0xFF2E7D32)
                                strategy.meanDeltaE00 < 4.5 -> Color(0xFF1565C0)
                                else -> Color(0xFFE65100)
                            }
                        )
                    }

                    // Metric 2: 95th Percentile ΔE00
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("95th %ile", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
                        Text(
                            text = String.format("ΔE %.2f", strategy.percentile95DeltaE00),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Metric 3: % of Pixels Within 3.0 ΔE00
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Within 3.0 ΔE", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
                        Text(
                            text = String.format("%.1f%%", strategy.percentWithinDeltaE3),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = if (strategy.percentWithinDeltaE3 > 80.0) Color(0xFF2E7D32) else Color(0xFFE65100)
                        )
                    }
                }
            }

            // Dark Garment Warning if applicable
            if (strategy.darkGarmentWarning != null) {
                Text(
                    text = strategy.darkGarmentWarning,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Text(
                text = strategy.notes,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 10.sp
            )
        }
    }
}

@Composable
fun InteractiveMaskCanvas(
    maskBitmap: Bitmap,
    erasedRectangles: List<NormalizedRect>,
    eraseEnabled: Boolean,
    onRectErased: (NormalizedRect) -> Unit,
    modifier: Modifier = Modifier
) {
    var dragStart by remember { mutableStateOf<Offset?>(null) }
    var dragCurrent by remember { mutableStateOf<Offset?>(null) }

    BoxWithConstraints(modifier = modifier) {
        val density = androidx.compose.ui.platform.LocalDensity.current
        val boxWidthPx = this.constraints.maxWidth.toFloat()
        val bmpAspect = maskBitmap.height.toFloat() / maskBitmap.width.toFloat()
        val boxHeightPx = boxWidthPx * bmpAspect

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(with(density) { boxHeightPx.toDp() })
                .pointerInput(eraseEnabled) {
                    if (eraseEnabled) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                dragStart = offset
                                dragCurrent = offset
                            },
                            onDrag = { change, _ ->
                                dragCurrent = change.position
                            },
                            onDragEnd = {
                                val start = dragStart
                                val current = dragCurrent
                                if (start != null && current != null) {
                                    val left = minOf(start.x, current.x) / boxWidthPx
                                    val right = maxOf(start.x, current.x) / boxWidthPx
                                    val top = minOf(start.y, current.y) / boxHeightPx
                                    val bottom = maxOf(start.y, current.y) / boxHeightPx

                                    if ((right - left) > 0.02f && (bottom - top) > 0.02f) {
                                        onRectErased(NormalizedRect(left, top, right, bottom))
                                    }
                                }
                                dragStart = null
                                dragCurrent = null
                            },
                            onDragCancel = {
                                dragStart = null
                                dragCurrent = null
                            }
                        )
                    }
                }
        ) {
            Image(
                bitmap = maskBitmap.asImageBitmap(),
                contentDescription = "Mask Preview",
                modifier = Modifier.fillMaxSize()
            )

            // Render existing user erased rectangles
            erasedRectangles.forEach { rect ->
                Box(
                    modifier = Modifier
                        .padding(
                            start = with(density) { (rect.minX * boxWidthPx).toDp() },
                            top = with(density) { (rect.minY * boxHeightPx).toDp() }
                        )
                        .size(
                            width = with(density) { ((rect.maxX - rect.minX) * boxWidthPx).toDp() },
                            height = with(density) { ((rect.maxY - rect.minY) * boxHeightPx).toDp() }
                        )
                        .border(BorderStroke(1.5.dp, Color.Red), RoundedCornerShape(2.dp))
                        .background(Color.Red.copy(alpha = 0.25f), RoundedCornerShape(2.dp))
                )
            }

            // Draw active drag rectangle in progress
            val start = dragStart
            val current = dragCurrent
            if (start != null && current != null) {
                val left = minOf(start.x, current.x)
                val top = minOf(start.y, current.y)
                val width = kotlin.math.abs(current.x - start.x)
                val height = kotlin.math.abs(current.y - start.y)

                Box(
                    modifier = Modifier
                        .padding(
                            start = with(density) { left.toDp() },
                            top = with(density) { top.toDp() }
                        )
                        .size(
                            width = with(density) { width.toDp() },
                            height = with(density) { height.toDp() }
                        )
                        .border(BorderStroke(2.dp, Color.Red), RoundedCornerShape(4.dp))
                        .background(Color.Red.copy(alpha = 0.35f), RoundedCornerShape(4.dp))
                )
            }
        }
    }
}

@Composable
fun MeasuredColorRow(color: MeasuredColor) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Color Swatch
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(color.rgb.r, color.rgb.g, color.rgb.b))
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
            )

            Spacer(modifier = Modifier.width(12.dp))

            // Color Metrics & Lab
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = colourName(color.hex),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = color.formattedCoverage,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Text(
                    text = "${color.hex} • ${color.formattedLab} • ${color.formattedArea}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Nearest Shade Match if present
                if (color.nearestShade != null && color.nearestShadeDeltaE00 != null) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Match: ${color.nearestShade.name} (${color.nearestShade.code})",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = if (color.nearestShadeDeltaE00 < 2.0) Color(0xFFE8F5E9) else Color(0xFFFFF3E0)
                        ) {
                            Text(
                                text = String.format("ΔE %.1f", color.nearestShadeDeltaE00),
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (color.nearestShadeDeltaE00 < 2.0) Color(0xFF2E7D32) else Color(0xFFE65100),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ErrorCurveVisualizer(curve: List<ErrorCurvePoint>) {
    val maxError = curve.maxOfOrNull { it.meanDeltaE00 }?.coerceAtLeast(10.0) ?: 10.0

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        curve.forEach { point ->
            val fraction = (point.meanDeltaE00 / maxError).toFloat().coerceIn(0.05f, 1f)
            val barColor = when {
                point.meanDeltaE00 < 2.5 -> Color(0xFF4CAF50) // Excellent fidelity
                point.meanDeltaE00 < 4.5 -> Color(0xFF8BC34A) // Good fidelity
                point.meanDeltaE00 < 7.0 -> Color(0xFFFFB300) // Moderate fidelity
                else -> Color(0xFFF44336) // High posterisation distortion
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "K = ${point.k}",
                    modifier = Modifier.width(52.dp),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(18.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.surface)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraction)
                            .height(18.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(barColor)
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = String.format("%.2f ΔE", point.meanDeltaE00),
                    modifier = Modifier.width(64.dp),
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
fun ConfidenceBadge(confidence: String) {
    val (bgColor, textColor) = when (confidence.lowercase()) {
        "sourced" -> Pair(Color(0xFFE8F5E9), Color(0xFF2E7D32))
        "interpolated" -> Pair(Color(0xFFE3F2FD), Color(0xFF1565C0))
        else -> Pair(Color(0xFFFFF3E0), Color(0xFFE65100))
    }

    Surface(
        shape = RoundedCornerShape(4.dp),
        color = bgColor
    ) {
        Text(
            text = confidence,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
            style = MaterialTheme.typography.labelSmall,
            color = textColor,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun FilmPositiveCard(channel: FilmPositiveChannel) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Film Positive Thumbnail (Greyscale: Black = Ink, White = Substrate)
            val filmBmp = channel.filmPositiveBitmap
            if (filmBmp != null) {
                Image(
                    bitmap = filmBmp.asImageBitmap(),
                    contentDescription = "Film Positive ${inkLabel(channel.inkName)}",
                    modifier = Modifier
                        .size(68.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(6.dp)),
                    contentScale = androidx.compose.ui.layout.ContentScale.Fit
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .background(Color.White, RoundedCornerShape(6.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(6.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Film", style = MaterialTheme.typography.labelSmall)
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Channel Details & Coverage
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(Color(android.graphics.Color.parseColor(channel.inkHex)))
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(3.dp))
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Screen ${channel.channelIndex}: ${inkLabel(channel.inkName)}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = colourName(channel.inkHex),
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "•",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Mesh: ${channel.recommendedMesh}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Active Coverage: ${channel.formattedCoverage}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "(Box: ${channel.formattedBoxCoverage})",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
