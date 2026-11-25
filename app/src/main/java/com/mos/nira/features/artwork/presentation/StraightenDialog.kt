package com.mos.nira.features.artwork.presentation

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.mos.nira.R
import com.mos.nira.core.image.PhotoStraighten

/**
 * "Straighten (4 corners)": the user drags four points onto the corners of the garment or the print
 * area; [onStraighten] gets them in the picture's own pixels (top-left, top-right, bottom-right, bottom-left).
 */
@Composable
fun StraightenDialog(bitmap: Bitmap, onStraighten: (FloatArray) -> Unit, onDismiss: () -> Unit) {
    // Corners in picture pixels.
    var corners by remember(bitmap) { mutableStateOf(PhotoStraighten.startCorners(bitmap.width, bitmap.height)) }
    var active by remember { mutableIntStateOf(-1) }
    val usable = PhotoStraighten.isUsable(corners)
    val cyan = Color(0xFF55D6E8)

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            modifier = Modifier.fillMaxSize().background(Color(0xFF0B1020)).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(stringResource(R.string.str_title), color = Color(0xFFF5F6FF), fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(stringResource(R.string.str_hint), color = Color(0xFF9BA6C0), fontSize = 12.sp, lineHeight = 16.sp)
            Spacer(Modifier.height(12.dp))
            BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                val density = LocalDensity.current
                val maxW = constraints.maxWidth.toFloat(); val maxH = constraints.maxHeight.toFloat()
                val scale = minOf(maxW / bitmap.width, maxH / bitmap.height)
                val shownW = bitmap.width * scale; val shownH = bitmap.height * scale
                val touch = with(density) { 40.dp.toPx() }
                Box(
                    modifier = Modifier
                        .size(with(density) { shownW.toDp() }, with(density) { shownH.toDp() })
                        .pointerInput(bitmap, scale) {
                            detectDragGestures(
                                onDragStart = { o -> active = PhotoStraighten.nearestCorner(corners.map { it * scale }.toFloatArray(), o.x, o.y, touch) },
                                onDrag = { change, amount ->
                                    val i = active
                                    if (i >= 0) {
                                        change.consume()
                                        corners = corners.copyOf().also {
                                            it[2 * i] = (it[2 * i] + amount.x / scale).coerceIn(0f, bitmap.width - 1f)
                                            it[2 * i + 1] = (it[2 * i + 1] + amount.y / scale).coerceIn(0f, bitmap.height - 1f)
                                        }
                                    }
                                },
                                onDragEnd = { active = -1 },
                                onDragCancel = { active = -1 }
                            )
                        }
                ) {
                    Image(bitmap = bitmap.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize())
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val p = List(4) { Offset(corners[2 * it] * scale, corners[2 * it + 1] * scale) }
                        val path = Path().apply { moveTo(p[0].x, p[0].y); for (i in 1..3) lineTo(p[i].x, p[i].y); close() }
                        val line = if (usable) cyan else Color(0xFFFF6E6E)
                        drawPath(path, line.copy(alpha = 0.18f))
                        drawPath(path, line, style = Stroke(width = 2.5.dp.toPx()))
                        p.forEachIndexed { i, o ->
                            drawCircle(Color.Black.copy(alpha = 0.5f), radius = 13.dp.toPx(), center = o)
                            drawCircle(if (i == active) Color.White else line, radius = 9.dp.toPx(), center = o)
                        }
                    }
                }
            }
            if (!usable) {
                Spacer(Modifier.height(6.dp))
                Text(stringResource(R.string.str_bad), color = Color(0xFFFF8A80), fontSize = 12.sp)
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) {
                    Text(stringResource(R.string.common_cancel), color = Color(0xFFF5F6FF))
                }
                Button(
                    onClick = { onStraighten(corners.copyOf()); onDismiss() },
                    enabled = usable,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B7CFF))
                ) { Text(stringResource(R.string.str_apply), fontWeight = FontWeight.Bold) }
            }
        }
    }
}
