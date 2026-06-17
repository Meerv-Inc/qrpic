package com.meerv.qrpic.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.zxing.common.BitMatrix
import com.meerv.qrpic.ecc.EccReport
import com.meerv.qrpic.ecc.LogoRegion
import com.meerv.qrpic.ecc.Verdict
import kotlin.math.roundToInt

@Composable
fun ResultsScreen(state: UiState.Result, vm: AppViewModel) {
    val scroll = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .systemBarsPadding()
            .verticalScroll(scroll)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("QRPic — Error-Correction Analysis", style = MaterialTheme.typography.titleLarge)

        SymbolCard(state.report)

        QrOverlay(
            bits = state.bits,
            photo = state.photo,
            dimension = state.dimension,
            region = state.region,
            onRegionChange = vm::updateRegion,
        )
        Text(
            "Drag the box to position it over the logo; use the size slider to match the dead space.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SizeSlider(region = state.region, dimension = state.dimension, onRegionChange = vm::updateRegion)

        ImpactCard(state.report)

        BlockTable(state.report)

        ScalingCard(
            base = state.report,
            sim = state.simReport,
            scalePercent = state.scalePercent,
            onScaleChange = vm::updateScale,
        )

        Button(onClick = vm::reset, modifier = Modifier.fillMaxWidth()) {
            Text("Scan another code")
        }
    }
}

@Composable
private fun SymbolCard(report: EccReport) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            KeyValue("QR version", "${report.version}  (${report.dimension}×${report.dimension} modules)")
            KeyValue("ECC level", "${report.ecLevel}  (~${report.nominalRecoveryPercent}% nominal recovery)")
            KeyValue("Total codewords", "${report.totalCodewords}  (${report.totalEcCodewords} error-correction)")
            val text = report.decodedText
            if (text != null) {
                Text("Decoded text", style = MaterialTheme.typography.labelMedium)
                Text(
                    text,
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                )
            } else {
                Text(
                    "Payload did not decode from this capture (logo and/or image quality). ECC analysis " +
                        "below still works because it is read from the QR structure.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun ImpactCard(report: EccReport) {
    val (label, color) = when (report.verdict) {
        Verdict.DECODES_WITH_MARGIN -> "Decodes with margin" to Color(0xFF2E7D32)
        Verdict.DECODES_FRAGILE -> "Decodes, but fragile" to Color(0xFFF9A825)
        Verdict.UNRECOVERABLE -> "Unrecoverable — logo too large" to Color(0xFFC62828)
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Logo impact", style = MaterialTheme.typography.titleMedium)
            Box(
                Modifier
                    .background(color, MaterialTheme.shapes.small)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text(label, color = Color.White, fontWeight = FontWeight.Bold)
            }
            KeyValue("Modules under logo", "${report.coveredModules} (${report.coveredDataModules} data/ECC)")
            KeyValue("Codewords corrupted", "${report.corruptedCodewords} of ${report.totalCodewords}")
            KeyValueStacked(
                "ECC budget consumed",
                "${(report.budgetConsumedFraction * 100).roundToInt()}% of error-correction codewords",
            )
            KeyValueStacked(
                "Headroom left (unknown errors)",
                "${report.minResidualErrorCapacity} codewords / worst block, " +
                    "${report.totalResidualErrorCapacity} total",
            )
            Text(
                explain(report),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun BlockTable(report: EccReport) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Per-block breakdown", style = MaterialTheme.typography.titleMedium)
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                MonoCell("Blk", 0.12f, bold = true)
                MonoCell("EC", 0.16f, bold = true)
                MonoCell("Erase", 0.22f, bold = true)
                MonoCell("Resid", 0.22f, bold = true)
                MonoCell("Status", 0.28f, bold = true)
            }
            report.blocks.forEach { b ->
                val status = when {
                    !b.recoverableAsErasures -> "FAIL"
                    !b.recoverableAsErrors || b.residualErrorCapacity == 0 -> "tight"
                    else -> "ok"
                }
                Row(Modifier.fillMaxWidth()) {
                    MonoCell("${b.index}", 0.12f)
                    MonoCell("${b.ecCodewords}", 0.16f)
                    MonoCell("${b.erasures}", 0.22f)
                    MonoCell("${b.residualErrorCapacity}", 0.22f)
                    MonoCell(status, 0.28f)
                }
            }
            Text(
                "EC = error-correction codewords in the block; Erase = codewords the logo corrupts " +
                    "(known positions → erasures); Resid = floor((EC − Erase)/2) headroom for further " +
                    "unknown errors.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

@Composable
private fun ScalingCard(
    base: EccReport,
    sim: EccReport,
    scalePercent: Int,
    onScaleChange: (Int) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("What if the logo shrinks or expands?", style = MaterialTheme.typography.titleMedium)
            val areaPct = run {
                val f = 1f + scalePercent / 100f
                (f * f * 100f).roundToInt()
            }
            Text(
                "Logo size: ${if (scalePercent >= 0) "+" else ""}$scalePercent% per side  (~$areaPct% of original area)",
                style = MaterialTheme.typography.bodyMedium,
            )
            Slider(
                value = scalePercent.toFloat(),
                onValueChange = { onScaleChange(it.roundToInt()) },
                valueRange = -50f..100f,
                steps = 14,
            )
            KeyValue(
                "Codewords corrupted",
                "${sim.corruptedCodewords}  (was ${base.corruptedCodewords})",
            )
            KeyValue(
                "ECC budget consumed",
                "${(sim.budgetConsumedFraction * 100).roundToInt()}%  (was ${(base.budgetConsumedFraction * 100).roundToInt()}%)",
            )
            KeyValue(
                "Headroom (worst block)",
                "${sim.minResidualErrorCapacity} codewords  (was ${base.minResidualErrorCapacity})",
            )
            HeadroomChart(base = base, scalePercent = scalePercent, sim = sim)
            Text(
                scalingExplanation(base, sim, scalePercent),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun HeadroomChart(base: EccReport, scalePercent: Int, sim: EccReport) {
    val maxHeadroom = (base.totalEcCodewords / (2 * base.blocks.size.coerceAtLeast(1)))
        .coerceAtLeast(sim.minResidualErrorCapacity)
        .coerceAtLeast(base.minResidualErrorCapacity)
        .coerceAtLeast(1)
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(3f)
            .padding(vertical = 8.dp),
    ) {
        val axis = Color.Gray
        drawLine(axis, Offset(0f, size.height), Offset(size.width, size.height), 2f)
        // Current operating point as a bar relative to max headroom.
        val barWidth = size.width * 0.18f
        val cx = size.width * ((scalePercent + 50) / 150f)
        val h = size.height * (sim.minResidualErrorCapacity.toFloat() / maxHeadroom)
        val barColor = when (sim.verdict) {
            Verdict.DECODES_WITH_MARGIN -> Color(0xFF2E7D32)
            Verdict.DECODES_FRAGILE -> Color(0xFFF9A825)
            Verdict.UNRECOVERABLE -> Color(0xFFC62828)
        }
        drawRect(
            color = barColor,
            topLeft = Offset(cx - barWidth / 2, size.height - h),
            size = Size(barWidth, h),
        )
    }
    Text(
        "Bar = remaining headroom (worst block) at the current logo size. Green = margin, " +
            "amber = fragile, red = unrecoverable.",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun SizeSlider(region: LogoRegion, dimension: Int, onRegionChange: (LogoRegion) -> Unit) {
    val sideModules = region.halfWidth * 2f
    Column {
        Text(
            "Logo size: ${sideModules.roundToInt()} × ${(region.halfHeight * 2f).roundToInt()} modules",
            style = MaterialTheme.typography.bodySmall,
        )
        Slider(
            value = region.halfWidth.coerceIn(0.5f, dimension / 2f),
            onValueChange = { half ->
                onRegionChange(region.copy(halfWidth = half, halfHeight = half))
            },
            valueRange = 0.5f..(dimension / 2f),
        )
    }
}

@Composable
private fun QrOverlay(
    bits: BitMatrix,
    photo: Bitmap?,
    dimension: Int,
    region: LogoRegion,
    onRegionChange: (LogoRegion) -> Unit,
) {
    val matrixImage: ImageBitmap = remember(bits) { bits.toImageBitmap() }
    val photoImage: ImageBitmap? = remember(photo) { photo?.asImageBitmap() }
    var showPhoto by remember(photoImage) { mutableStateOf(photoImage != null) }
    val currentRegion = rememberUpdatedState(region)

    Column {
        if (photoImage != null) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Background:", style = MaterialTheme.typography.labelMedium)
                FilterChip(selected = showPhoto, onClick = { showPhoto = true }, label = { Text("Captured photo") })
                FilterChip(selected = !showPhoto, onClick = { showPhoto = false }, label = { Text("QR matrix") })
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .background(Color.White),
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            val cell = size.width.toFloat() / dimension
                            val r = currentRegion.value
                            val nx = (r.centerX + dragAmount.x / cell).coerceIn(0f, dimension.toFloat())
                            val ny = (r.centerY + dragAmount.y / cell).coerceIn(0f, dimension.toFloat())
                            onRegionChange(r.copy(centerX = nx, centerY = ny))
                        }
                    },
            ) {
                val dst = IntSize(size.width.roundToInt(), size.height.roundToInt())
                if (showPhoto && photoImage != null) {
                    // The deskewed photo already maps module 0..dimension onto its full extent.
                    drawImage(
                        image = photoImage,
                        dstSize = dst,
                        dstOffset = IntOffset(0, 0),
                        filterQuality = FilterQuality.Low,
                    )
                } else {
                    // QR matrix scaled up with crisp (nearest-neighbour) edges.
                    drawImage(
                        image = matrixImage,
                        dstSize = dst,
                        dstOffset = IntOffset(0, 0),
                        filterQuality = FilterQuality.None,
                    )
                }
                drawRegion(region, dimension)
            }
        }
    }
}

private fun DrawScope.drawRegion(region: LogoRegion, dimension: Int) {
    val cell = size.width / dimension
    val left = (region.centerX - region.halfWidth) * cell
    val top = (region.centerY - region.halfHeight) * cell
    val w = region.halfWidth * 2f * cell
    val h = region.halfHeight * 2f * cell
    drawRect(
        color = Color(0x55FF1744),
        topLeft = Offset(left, top),
        size = Size(w, h),
    )
    drawRect(
        color = Color(0xFFFF1744),
        topLeft = Offset(left, top),
        size = Size(w, h),
        style = Stroke(width = 3f),
    )
}

@Composable
private fun KeyValue(key: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(key, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

/** Label with a trailing colon, value on its own line below — used for the headline calculations. */
@Composable
private fun KeyValueStacked(key: String, value: String) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            "$key:",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.MonoCell(text: String, weight: Float, bold: Boolean = false) {
    Text(
        text,
        modifier = Modifier.weight(weight),
        fontFamily = FontFamily.Monospace,
        fontSize = 13.sp,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
    )
}

private fun explain(report: EccReport): String {
    val worst = report.worstBlock ?: return ""
    return when (report.verdict) {
        Verdict.DECODES_WITH_MARGIN ->
            "The logo consumes ${report.corruptedCodewords} codewords. The tightest block still has " +
                "${worst.ecCodewords - worst.erasures} of its ${worst.ecCodewords} ECC codewords free, " +
                "leaving room for ~${report.minResidualErrorCapacity} more codewords of real-world damage."
        Verdict.DECODES_FRAGILE ->
            "The logo eats almost all of the error-correction budget. The worst block has only " +
                "${worst.ecCodewords - worst.erasures} ECC codewords left — little or no margin for dirt, " +
                "glare or print defects. Shrink the logo or raise the ECC level."
        Verdict.UNRECOVERABLE ->
            "The logo corrupts more codewords than at least one block can correct even with known " +
                "positions. This symbol will not reliably decode — shrink the logo or use a higher ECC level."
    }
}

private fun scalingExplanation(base: EccReport, sim: EccReport, scalePercent: Int): String {
    val deltaCw = sim.corruptedCodewords - base.corruptedCodewords
    val direction = when {
        scalePercent > 0 -> "Expanding"
        scalePercent < 0 -> "Shrinking"
        else -> "Keeping"
    }
    val effect = when {
        deltaCw > 0 -> "corrupts $deltaCw more codeword(s), leaving ${sim.minResidualErrorCapacity} " +
            "codewords of headroom in the worst block (was ${base.minResidualErrorCapacity})."
        deltaCw < 0 -> "frees ${-deltaCw} codeword(s), raising headroom to ${sim.minResidualErrorCapacity} " +
            "codewords in the worst block (was ${base.minResidualErrorCapacity})."
        else -> "does not change which codewords are corrupted."
    }
    val verdictNote = when (sim.verdict) {
        Verdict.UNRECOVERABLE -> " At this size the code becomes unrecoverable."
        Verdict.DECODES_FRAGILE -> " At this size the code is fragile (almost no error-correction margin)."
        Verdict.DECODES_WITH_MARGIN -> " At this size the code still decodes with margin."
    }
    return "$direction the logo by $scalePercent% $effect$verdictNote"
}

private fun BitMatrix.toImageBitmap(): ImageBitmap {
    val w = width
    val h = height
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val black = 0xFF000000.toInt()
    val white = 0xFFFFFFFF.toInt()
    val rowPixels = IntArray(w)
    for (y in 0 until h) {
        for (x in 0 until w) {
            rowPixels[x] = if (get(x, y)) black else white
        }
        bmp.setPixels(rowPixels, 0, w, 0, y, w, 1)
    }
    return bmp.asImageBitmap()
}
