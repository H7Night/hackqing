package com.hackqing.scale.ui.trends

import androidx.compose.ui.graphics.Color
import com.hackqing.scale.model.ScaleMeasurement
import java.util.Locale

/**
 * 趋势指标。[unit] 用于展示，[decimals] 控制数值精度。
 */
enum class TrendMetric(val label: String, val unit: String, val decimals: Int) {
    WEIGHT("体重", "kg", 2),
    FAT("体脂", "%", 1),
    MUSCLE("肌肉", "%", 1),
    WATER("水分", "%", 1),
    BONE("骨量", "kg", 2),
    BMR("基础代谢", "kcal", 0),
    VISCERAL("内脏脂肪", "", 0),
    BODY_AGE("体龄", "岁", 0),
    BMI("BMI", "", 1),
}

/** 每个指标的折线颜色（浅色/深色下均可辨识的定性色板）。 */
val TrendMetric.color: Color
    get() = when (this) {
        TrendMetric.WEIGHT -> Color(0xFF3B82F6)
        TrendMetric.FAT -> Color(0xFFF97316)
        TrendMetric.MUSCLE -> Color(0xFF10B981)
        TrendMetric.WATER -> Color(0xFF06B6D4)
        TrendMetric.BONE -> Color(0xFFA855F7)
        TrendMetric.BMR -> Color(0xFFEF4444)
        TrendMetric.VISCERAL -> Color(0xFFEAB308)
        TrendMetric.BODY_AGE -> Color(0xFFEC4899)
        TrendMetric.BMI -> Color(0xFF14B8A6)
    }

/** 按指标精度格式化数值（不含单位）。 */
fun TrendMetric.format(value: Float): String =
    String.format(Locale.US, "%.${decimals}f", value)

/** 带单位格式化，例："67.40 kg"、"18.1 %"、"5"。 */
fun TrendMetric.formatWithUnit(value: Float): String =
    if (unit.isEmpty()) format(value) else "${format(value)} $unit"

/** 取某条测量在指定指标下的数值；缺失（无体成分）时为 null。 */
fun metricValue(m: ScaleMeasurement, metric: TrendMetric): Float? = when (metric) {
    TrendMetric.WEIGHT -> m.weightKg
    TrendMetric.FAT -> m.composition?.fatPercent ?: m.bodyFatPercent
    TrendMetric.MUSCLE -> m.composition?.musclePercent
    TrendMetric.WATER -> m.composition?.waterPercent
    TrendMetric.BONE -> m.composition?.boneKg
    TrendMetric.BMR -> m.composition?.bmr
    TrendMetric.VISCERAL -> m.composition?.visceralFat?.toFloat()
    TrendMetric.BODY_AGE -> m.composition?.bodyAge?.toFloat()
    TrendMetric.BMI -> m.composition?.bmi
}

/**
 * 按时间升序返回 (timeMillis, value) 曲线点。
 * [rangeDays] 为 null 表示全部；否则只保留最近 [rangeDays] 天。
 */
fun trendPoints(
    history: List<ScaleMeasurement>,
    metric: TrendMetric,
    rangeDays: Int?,
    nowMillis: Long = System.currentTimeMillis(),
): List<Pair<Long, Float>> {
    val cutoff = rangeDays?.let { nowMillis - it.toLong() * 86_400_000L }
    return history
        .filter { cutoff == null || it.timeMillis >= cutoff }
        .mapNotNull { m -> metricValue(m, metric)?.let { m.timeMillis to it } }
        .sortedBy { it.first }
}
