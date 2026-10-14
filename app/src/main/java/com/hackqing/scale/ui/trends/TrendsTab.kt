package com.hackqing.scale.ui.trends

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.hackqing.scale.model.ScaleMeasurement

@Composable
fun TrendsTab(history: List<ScaleMeasurement>) {
    var metric by remember { mutableStateOf(TrendMetric.WEIGHT) }
    var range by remember { mutableStateOf(30) }
    val rangeDays = if (range <= 0) null else range
    val points = remember(history, metric, range) { trendPoints(history, metric, rangeDays) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TrendMetric.entries.forEach { m ->
                FilterChip(
                    selected = m == metric,
                    onClick = { metric = m },
                    label = { Text(m.label) },
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(7, 30, 90, 0).forEach { d ->
                FilterChip(
                    selected = range == d,
                    onClick = { range = d },
                    label = { Text(if (d == 0) "全部" else "${d}天") },
                )
            }
        }
        Spacer(Modifier.height(20.dp))
        if (points.size < 2) {
            Text(
                "数据不足（至少 2 条「${metric.label}」记录）",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Column
        }
        TrendChart(points, Modifier.fillMaxWidth().height(220.dp))
        Spacer(Modifier.height(16.dp))
        val values = points.map { it.second }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Stat("最小", values.min())
            Stat("平均", values.average().toFloat())
            Stat("最大", values.max())
        }
    }
}

@Composable
private fun Stat(label: String, value: Float) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            "%.1f".format(value),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun TrendChart(points: List<Pair<Long, Float>>, modifier: Modifier = Modifier) {
    val lineColor = MaterialTheme.colorScheme.primary
    val fillColor = lineColor.copy(alpha = 0.18f)
    Canvas(modifier) {
        val minV = points.minOf { it.second }
        val maxV = points.maxOf { it.second }
        val spanV = (maxV - minV).takeIf { it > 0f } ?: 1f
        val minT = points.first().first
        val maxT = points.last().first
        val spanT = (maxT - minT).takeIf { it > 0L } ?: 1L
        fun x(t: Long) = ((t - minT).toFloat() / spanT) * size.width
        fun y(v: Float) = size.height - ((v - minV) / spanV) * size.height

        val path = Path()
        points.forEachIndexed { i, (t, v) ->
            if (i == 0) path.moveTo(x(t), y(v)) else path.lineTo(x(t), y(v))
        }
        val fill = Path().apply {
            addPath(path)
            lineTo(x(maxT), size.height)
            lineTo(x(minT), size.height)
            close()
        }
        drawPath(fill, fillColor)
        drawPath(path, lineColor, style = Stroke(width = 4f))
    }
}
