package nl.lrvdlinden.liftr.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import nl.lrvdlinden.liftr.core.L10n
import nl.lrvdlinden.liftr.core.LiftrFormat
import nl.lrvdlinden.liftr.core.tr
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.max

data class InsightPoint(val date: Long, val value: Double)

enum class InsightRange(val months: Long?) {
    Month(1), ThreeMonths(3), SixMonths(6), Year(12), All(null);

    val label: String
        get() = when (this) {
            Month -> tr("1M", "1M")
            ThreeMonths -> tr("3M", "3M")
            SixMonths -> tr("6M", "6M")
            Year -> tr("1Y", "1J")
            All -> tr("All", "Alles")
        }

    val start: Long?
        get() = months?.let { LocalDate.now().minusMonths(it).atStartOfDay(LiftrFormat.zone).toInstant().toEpochMilli() }
}

/**
 * Insights-style chart drawn on a Canvas (InsightChart.swift): big value on top, drag a finger over the
 * graph to read any point, pick a period, and min / average / max underneath.
 */
@Composable
fun InsightChart(
    title: String,
    unit: String,
    points: List<InsightPoint>,
    bars: Boolean = false,
    decimals: Int = 1,
    defaultRange: InsightRange = InsightRange.ThreeMonths,
    zeroBased: Boolean = false,
) {
    var range by rememberSaveable(title) { mutableStateOf(defaultRange) }
    var selected by remember(title, range) { mutableStateOf<Int?>(null) }
    val haptics = LocalHapticFeedback.current
    val sorted = points.sortedBy { it.date }
    val data = range.start?.let { s -> sorted.filter { it.date >= s } } ?: sorted
    fun format(v: Double) = LiftrFormat.decimal(v, decimals)

    Column(Modifier.fillMaxWidth().liftrCard(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Header
        val shown = selected?.let { data.getOrNull(it) } ?: data.lastOrNull()
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title.uppercase(), color = LiftrColors.muted, fontSize = 12.sp, fontWeight = FontWeight.Black)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(shown?.let { format(it.value) } ?: "–", style = LiftrType.display(36.sp))
                Text(" $unit", color = LiftrColors.muted, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 4.dp))
                Box(Modifier.weight(1f))
                val change = (data.lastOrNull()?.value ?: 0.0) - (data.firstOrNull()?.value ?: 0.0)
                if (selected == null && data.size > 1 && !bars && change != 0.0) {
                    Text((if (change > 0) "↗ +" else "↘ −") + format(abs(change)), color = LiftrColors.accent, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
            }
            Text(shown?.let { LiftrFormat.day(it.date) } ?: " ", color = if (selected == null) LiftrColors.muted else LiftrColors.accent,
                fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }

        if (data.isEmpty()) {
            Box(Modifier.fillMaxWidth().heightIn(min = 180.dp), contentAlignment = Alignment.Center) {
                Text(tr("No data in this period.", "Geen gegevens in deze periode."), color = LiftrColors.muted, fontSize = 15.sp)
            }
        } else {
            val values = data.map { it.value }
            val low = values.min()
            val high = values.max()
            val pad = max((high - low) * 0.15, if (high == 0.0) 1.0 else high * 0.02)
            val minY = if (bars || zeroBased) 0.0 else low - pad
            val maxY = high + pad
            val minX = data.first().date
            val maxX = data.last().date
            val labelFormat = DateTimeFormatter.ofPattern(if (range == InsightRange.Year || range == InsightRange.All) "MMM" else "d MMM", L10n.locale)
            val axisPaint = remember {
                android.graphics.Paint().apply { color = 0xFF9E9EA8.toInt(); isAntiAlias = true }
            }
            Canvas(
                Modifier.fillMaxWidth().height(210.dp)
                    .pointerInput(data, bars) {
                        fun pick(x: Float) {
                            val chartW = size.width - 40.dp.toPx()
                            val idx = nearestIndex(data, x, chartW, minX, maxX, bars)
                            if (idx != selected) {
                                selected = idx
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                        }
                        detectDragGestures(
                            onDragStart = { pick(it.x) },
                            onDragEnd = { selected = null },
                            onDragCancel = { selected = null },
                        ) { change, _ -> pick(change.position.x) }
                    }
                    .pointerInput(data, bars) {
                        detectTapGestures(onPress = {
                            val chartW = size.width - 40.dp.toPx()
                            selected = nearestIndex(data, it.x, chartW, minX, maxX, bars)
                            tryAwaitRelease()
                            selected = null
                        })
                    },
            ) {
                val axisW = 40.dp.toPx()
                val labelH = 18.dp.toPx()
                val w = size.width - axisW
                val h = size.height - labelH
                fun x(i: Int, date: Long): Float = if (bars) {
                    val slot = w / data.size
                    slot * i + slot / 2
                } else if (maxX == minX) w / 2 else ((date - minX).toDouble() / (maxX - minX) * w).toFloat()
                fun y(v: Double): Float = (h - (v - minY) / (maxY - minY) * h).toFloat()

                // Grid and y labels
                axisPaint.textSize = 11.sp.toPx()
                for (k in 0..3) {
                    val v = minY + (maxY - minY) * k / 3
                    val yy = y(v)
                    drawLine(Color.White.copy(alpha = 0.08f), Offset(0f, yy), Offset(w, yy), 1f)
                    drawContext.canvas.nativeCanvas.drawText(LiftrFormat.decimal(v, if (maxY - minY < 10) 1 else 0), w + 6.dp.toPx(), yy + 4.dp.toPx(), axisPaint)
                }
                // x labels (start, middle, end)
                val idxs = listOf(0, data.size / 2, data.size - 1).distinct()
                for (i in idxs) {
                    val label = labelFormat.format(LiftrFormat.zoned(data[i].date)).trimEnd('.')
                    val tw = axisPaint.measureText(label)
                    val xx = (x(i, data[i].date) - tw / 2).coerceIn(0f, w - tw)
                    drawContext.canvas.nativeCanvas.drawText(label, xx, size.height - 3.dp.toPx(), axisPaint)
                }

                if (bars) {
                    val slot = w / data.size
                    val bw = max(2f, slot * 0.7f)
                    data.forEachIndexed { i, p ->
                        val top = y(p.value)
                        val on = selected == null || selected == i
                        drawRoundRect(
                            brush = if (on) Brush.verticalGradient(listOf(LiftrColors.accent, LiftrColors.accentDeep)) else Brush.verticalGradient(listOf(LiftrColors.accent.copy(alpha = 0.35f), LiftrColors.accent.copy(alpha = 0.35f))),
                            topLeft = Offset(x(i, p.date) - bw / 2, top), size = Size(bw, max(0f, h - top)),
                            cornerRadius = CornerRadius(minOf(4.dp.toPx(), bw / 2)),
                        )
                    }
                } else {
                    val pts = data.mapIndexed { i, p -> Offset(x(i, p.date), y(p.value)) }
                    val line = Path()
                    pts.forEachIndexed { i, o ->
                        if (i == 0) line.moveTo(o.x, o.y) else {
                            val prev = pts[i - 1]
                            val cx = (prev.x + o.x) / 2
                            line.cubicTo(cx, prev.y, cx, o.y, o.x, o.y)
                        }
                    }
                    val area = Path().apply {
                        addPath(line)
                        lineTo(pts.last().x, h)
                        lineTo(pts.first().x, h)
                        close()
                    }
                    drawPath(area, Brush.verticalGradient(listOf(LiftrColors.accent.copy(alpha = 0.45f), LiftrColors.accent.copy(alpha = 0.02f)), 0f, h))
                    drawPath(line, LiftrColors.accent, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                    val sel = selected
                    if (sel == null) {
                        drawCircle(LiftrColors.accent, 4.dp.toPx(), pts.last())
                    } else {
                        val o = pts[sel]
                        drawLine(Color.White.copy(alpha = 0.35f), Offset(o.x, 0f), Offset(o.x, h), 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)))
                        drawCircle(Color.White, 6.dp.toPx(), o)
                        drawCircle(LiftrColors.accent, 4.dp.toPx(), o)
                    }
                }
                val sel = selected
                if (bars && sel != null) {
                    val xx = x(sel, data[sel].date)
                    drawLine(Color.White.copy(alpha = 0.35f), Offset(xx, 0f), Offset(xx, h), 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)))
                }
            }
        }

        // Range picker
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (r in InsightRange.entries) {
                val on = r == range
                Text(r.label, Modifier.weight(1f).clip(RoundedCornerShape(50))
                    .background(if (on) LiftrColors.accent.copy(alpha = 0.2f) else LiftrColors.cardRaised)
                    .clickable { range = r }.padding(vertical = 7.dp),
                    color = if (on) LiftrColors.accent else LiftrColors.muted, fontSize = 12.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
            }
        }

        if (data.isNotEmpty()) {
            val values = data.map { it.value }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Stat(tr("Min", "Min"), "${format(values.min())} $unit", Modifier.weight(1f))
                Box(Modifier.width(1.dp).height(28.dp).background(LiftrColors.line))
                Stat(tr("Average", "Gemiddeld"), "${format(values.average())} $unit", Modifier.weight(1f))
                Box(Modifier.width(1.dp).height(28.dp).background(LiftrColors.line))
                Stat(tr("Max", "Max"), "${format(values.max())} $unit", Modifier.weight(1f))
            }
        }
    }
}

private fun nearestIndex(data: List<InsightPoint>, x: Float, w: Float, minX: Long, maxX: Long, bars: Boolean): Int? {
    if (data.isEmpty()) return null
    if (bars) {
        val slot = w / data.size
        return (x / slot).toInt().coerceIn(0, data.size - 1)
    }
    val t = if (maxX == minX) minX else minX + ((x / w).coerceIn(0f, 1f) * (maxX - minX)).toLong()
    return data.indices.minByOrNull { abs(data[it].date - t) }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label.uppercase(), color = LiftrColors.muted, fontSize = 10.sp, fontWeight = FontWeight.Black)
        Text(value, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}
