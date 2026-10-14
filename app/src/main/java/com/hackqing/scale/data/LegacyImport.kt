package com.hackqing.scale.data

import com.hackqing.scale.body.BodyComposition
import com.hackqing.scale.model.ScaleMeasurement
import org.json.JSONArray

/**
 * Reads the legacy `measurements.json` format (short keys `fat/ver/comp`) into
 * [ScaleMeasurement]s. Pure and side-effect free so it can seed Room once and be unit-tested.
 */
object LegacyImport {

    fun parse(json: String): List<ScaleMeasurement> = try {
        val arr = JSONArray(json)
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            ScaleMeasurement(
                id = o.optLong("id"),
                timeMillis = o.optLong("time"),
                weightKg = o.optDouble("weight", 0.0).toFloat(),
                resistance = if (o.has("resistance")) o.optInt("resistance") else null,
                bodyFatPercent = if (o.has("fat")) o.optDouble("fat").toFloat() else null,
                userId = if (o.has("userId")) o.optInt("userId") else null,
                heartRate = if (o.has("heartRate")) o.optInt("heartRate") else null,
                isFinal = o.optBoolean("final", true),
                rawHex = o.optString("raw", ""),
                protoVersion = o.optInt("ver", 0),
                hcSynced = o.optBoolean("hcSynced", false),
                composition = o.optJSONObject("comp")?.let { c ->
                    BodyComposition(
                        bmi = c.optDouble("bmi", 0.0).toFloat(),
                        fatPercent = c.optDouble("fat", 0.0).toFloat(),
                        musclePercent = c.optDouble("muscle", 0.0).toFloat(),
                        waterPercent = c.optDouble("water", 0.0).toFloat(),
                        proteinPercent = c.optDouble("protein", 0.0).toFloat(),
                        boneKg = c.optDouble("bone", 0.0).toFloat(),
                        visceralFat = c.optInt("visceral", 0),
                        bmr = c.optDouble("bmr", 0.0).toFloat(),
                        bodyAge = c.optInt("bodyAge", 0),
                        leanBodyMassKg = c.optDouble("lean", 0.0).toFloat(),
                        fatMassKg = c.optDouble("fatMass", 0.0).toFloat(),
                    )
                },
            )
        }
    } catch (_: Exception) {
        emptyList()
    }
}
