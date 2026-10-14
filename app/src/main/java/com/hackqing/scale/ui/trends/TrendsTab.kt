package com.hackqing.scale.ui.trends

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hackqing.scale.model.ScaleMeasurement
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

private enum class TrendMode(val label: String) { COMBINED("整合"), INDIVIDUAL("单独") }

/** 一条折线：点集 + 颜色。 */
private data class LineSeries(val points: List<Pair<Long, Float>>, val color: Color)

@Composable
fun TrendsTab(history: List<ScaleMeasurement>) {
    var mode by remember { mutableStateOf(TrendMode.COMBINED) }
    var rangeDays by remember { mutableStateOf(30) }
    var selected by remember { mutableStateOf(setOf(TrendMetric.WEIGHT, TrendMetric.FAT)) }
    val range = if (rangeDays <= 0) null else rangeDays

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        if (history.isEmpty()) {
            Text(
                "暂无测量记录，回到「测量」扫描设备并上秤",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 24.dp),
            )
            return@Column
        }

        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            TrendMode.entries.forEachIndexed { i, m ->
                SegmentedButton(
                    selected = mode == m,
                    onClick = { mode = m },
                    shape = SegmentedButtonDefaults.itemShape(index = i, count = TrendMode.entries.size),
                ) { Text(m.label) }
            }
        }

        Spacer(Modifier.height(12.dp))
        RangeChips(rangeDays) { rangeDays = it }
        Spacer(Modifier.height(16.dp))

        when (mode) {
            TrendMode.COMBINED -> CombinedTrend(history, selected, range) { metric ->
                selected = if (metric in selected) selected - metric else selected + metric
            }
            TrendMode.INDIVIDUAL -> IndividualTrends(history, range)
        }
    }
}

@Composable
private fun RangeChips(rangeDays: Int, onSelect: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(7, 30, 90, 0).forEach { d ->
            FilterChip(
                selected = rangeDays == d,
                onClick = { onSelect(d) },
                label = { Text(if (d == 0) "全部" else "${d}天") },
            )
        }
    }
}

/** 整合：多选指标叠加在一张折线图上（各指标按自身范围缩放，用于比较走势）。 */
@Composable
private fun CombinedTrend(
    history: List<ScaleMeasurement>,
    selected: Set<TrendMetric>,
    rangeDays: Int?,
    onToggle: (TrendMetric) -> Unit,
) {
    Column {
        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TrendMetric.entries.forEach { metric ->
                FilterChip(
                    selected = metric in selected,
                    onClick = { onToggle(metric) },
                    label = { Text(metric.label) },
                    leadingIcon = {
                        Box(Modifier.size(10.dp).background(metric.color, CircleShape))
                    },
                )
            }
        }
        Spacer(Modifier.height(12.dp))

        val series = remember(history, selected, rangeDays) {
            TrendMetric.entries
                .filter { it in selected }
                .map { metric -> metric to trendPoints(history, metric, rangeDays) }
        }
        val drawable = series.filter { it.second.size >= 2 }

        when {
            selected.isEmpty() -> EmptyHint("请选择要对比的指标")
            drawable.isEmpty() -> EmptyHint("所选指标在范围内数据不足（至少 2 个时间点）")
            else -> {
                val normalize = drawable.size > 1
                FlowRow(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    drawable.forEach { (metric, pts) ->
                        LegendItem(metric, pts.last().second)
                    }
                }
                Spacer(Modifier.height(10.dp))
                CardSurface {
                    LineChart(
                        lines = drawable.map { LineSeries(it.second, it.first.color) },
                        normalize = normalize,
                        minMaxLabel = if (!normalize) minMaxLabel(drawable.first().first, drawable.first().second) else null,
                    )
                }
                if (normalize) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "多指标按各自数值范围缩放，仅用于比较走势方向",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** 单独：每个指标各占一张卡片、拥有独立坐标的折线图。 */
@Composable
private fun IndividualTrends(history: List<ScaleMeasurement>, rangeDays: Int?) {
    val items = remember(history, rangeDays) {
        TrendMetric.entries.mapNotNull { metric ->
            val pts = trendPoints(history, metric, rangeDays)
            if (pts.isEmpty()) null else metric to pts
        }
    }

    if (items.isEmpty()) {
        EmptyHint("范围内暂无可用指标数据")
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items.forEach { (metric, pts) -> MetricCard(metric, pts) }
    }
}

@Composable
private fun MetricCard(metric: TrendMetric, points: List<Pair<Long, Float>>) {
    val latest = points.last().second
    val delta = points.getOrNull(points.size - 2)?.second?.let { latest - it }

    CardSurface {
        Column(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).background(metric.color, CircleShape))
                Spacer(Modifier.width(8.dp))
                Text(metric.label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Text(
                    metric.formatWithUnit(latest),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = metric.color,
                )
            }
            if (delta != null && abs(delta) > 0.0001f) {
                Text(
                    (if (delta >= 0) "▲ +" else "▼ -") + metric.format(abs(delta)) + unitSuffix(metric),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(10.dp))
            LineChart(
                lines = listOf(LineSeries(points, metric.color)),
                normalize = false,
                minMaxLabel = minMaxLabel(metric, points),
            )
        }
    }
}

@Composable
private fun CardSurface(content: @Composable () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(Modifier.padding(14.dp)) { content() }
    }
}

@Composable
private fun LegendItem(metric: TrendMetric, value: Float) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).background(metric.color, CircleShape))
        Spacer(Modifier.width(6.dp))
        Text(
            "${metric.label} ${metric.formatWithUnit(value)}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun EmptyHint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 24.dp),
    )
}

private fun unitSuffix(metric: TrendMetric): String = if (metric.unit.isEmpty()) "" else " ${metric.unit}"

private fun minMaxLabel(metric: TrendMetric, points: List<Pair<Long, Float>>): String {
    val min = points.minOf { it.second }
    val max = points.maxOf { it.second }
    return "最小 ${metric.format(min)}${unitSuffix(metric)} · 最大 ${metric.format(max)}${unitSuffix(metric)}"
}

/**
 * 通用折线图。[normalize] 为真时每条线按自身 [min,max] 映射到 0..1（多指标对比）；
 * 否则所有线共享同一 [min,max]（单一指标，保留真实刻度）。
 */
@Composable
private fun LineChart(
    lines: List<LineSeries>,
    normalize: Boolean,
    modifier: Modifier = Modifier,
    minMaxLabel: String? = null,
) {
    val dateFmt = remember { SimpleDateFormat("MM-dd", Locale.US) }
    val all = lines.flatMap { it.points }
    val distinctTimes = all.map { it.first }.distinct().size

    if (all.size < 2 || distinctTimes < 2) {
        Box(modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
            Text(
                "数据不足（至少 2 个时间点）",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    val minT = all.minOf { it.first }
    val maxT = all.maxOf { it.first }
    val spanT = (maxT - minT).coerceAtLeast(1L)
    val globalMin = all.minOf { it.second }
    val globalMax = all.maxOf { it.second }

    Column(modifier.fillMaxWidth()) {
        Canvas(Modifier.fillMaxWidth().height(180.dp)) {
            val padV = 8.dp.toPx()
            val padH = 8.dp.toPx()
            val h = size.height - padV * 2
            val w = size.width - padH * 2

            fun xOf(t: Long) = padH + ((t - minT).toFloat() / spanT) * w
            fun yOf(v: Float, lo: Float, hi: Float): Float {
                val span = (hi - lo).takeIf { it > 0f } ?: 1f
                return padV + h - ((v - lo) / span) * h
            }

            lines.forEach { line ->
                val lo = if (normalize) line.points.minOf { it.second } else globalMin
                val hi = if (normalize) line.points.maxOf { it.second } else globalMax

                val path = Path()
                line.points.forEachIndexed { i, (t, v) ->
                    val px = xOf(t)
                    val py = yOf(v, lo, hi)
                    if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
                }

                if (lines.size == 1) {
                    val fill = Path().apply {
                        addPath(path)
                        lineTo(xOf(line.points.last().first), size.height - padV)
                        lineTo(xOf(line.points.first().first), size.height - padV)
                        close()
                    }
                    drawPath(fill, line.color.copy(alpha = 0.12f))
                }

                drawPath(
                    path,
                    line.color,
                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
                )

                val last = line.points.last()
                drawCircle(line.color, radius = 4.dp.toPx(), center = Offset(xOf(last.first), yOf(last.second, lo, hi)))
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(dateFmt.format(Date(minT)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            minMaxLabel?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(dateFmt.format(Date(maxT)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
