package com.hackqing.scale.ui.trends

import com.hackqing.scale.model.ScaleMeasurement

enum class TrendMetric(val label: String) {
    WEIGHT("体重"),
    FAT("体脂"),
    MUSCLE("肌肉"),
    WATER("水分"),
    BONE("骨量"),
    BMR("基础代谢"),
    VISCERAL("内脏脂肪"),
    BODY_AGE("体龄"),
    BMI("BMI"),
}

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
