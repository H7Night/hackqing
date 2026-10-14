package com.hackqing.scale.ui.trends

import com.hackqing.scale.body.BodyComposition
import com.hackqing.scale.model.ScaleMeasurement
import org.junit.Assert.assertEquals
import org.junit.Test

class TrendDataTest {

    private fun m(
        id: Long,
        time: Long,
        weight: Float,
        fat: Float? = null,
        comp: BodyComposition? = null,
    ) = ScaleMeasurement(id = id, timeMillis = time, weightKg = weight, bodyFatPercent = fat, composition = comp)

    @Test
    fun `weight points are sorted by time`() {
        val history = listOf(m(2, 2000, 70f), m(1, 1000, 71f))
        val points = trendPoints(history, TrendMetric.WEIGHT, rangeDays = null, nowMillis = 10_000)
        assertEquals(listOf(1000L to 71f, 2000L to 70f), points)
    }

    @Test
    fun `range filters older points`() {
        val day = 86_400_000L
        val history = listOf(m(1, 0L, 60f), m(2, 9 * day, 61f))
        val points = trendPoints(history, TrendMetric.WEIGHT, rangeDays = 7, nowMillis = 9 * day + 1)
        assertEquals(listOf(9 * day to 61f), points)
    }

    @Test
    fun `metric picks the right field`() {
        val comp = BodyComposition(
            bmi = 23.6f, fatPercent = 17.3f, musclePercent = 55.4f, waterPercent = 60f,
            proteinPercent = 18f, boneKg = 2.8f, visceralFat = 5, bmr = 1575f,
            bodyAge = 17, leanBodyMassKg = 55.2f, fatMassKg = 12.2f,
        )
        val history = listOf(m(1, 1000, 70f, fat = 18f, comp = comp))
        assertEquals(17.3f, metricValue(history[0], TrendMetric.FAT))
        assertEquals(2.8f, metricValue(history[0], TrendMetric.BONE))
        assertEquals(5f, metricValue(history[0], TrendMetric.VISCERAL))
        assertEquals(70f, metricValue(history[0], TrendMetric.WEIGHT))
    }
}
