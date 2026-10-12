package com.hackqing.scale.data

import android.content.Context
import com.hackqing.scale.model.ScaleMeasurement
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** 简单 JSON 文件持久化，保存测量历史。 */
class MeasurementStore(private val context: Context) {

    private val file = File(context.filesDir, "measurements.json")

    fun load(): List<ScaleMeasurement> {
        if (!file.exists()) return emptyList()
        return try {
            val arr = JSONArray(file.readText())
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
                    composition = o.optJSONObject("comp")?.let { c ->
                        com.hackqing.scale.body.BodyComposition(
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

    fun save(list: List<ScaleMeasurement>) {
        try {
            val arr = JSONArray()
            for (m in list) {
                arr.put(JSONObject().apply {
                    put("id", m.id)
                    put("time", m.timeMillis)
                    put("weight", m.weightKg.toDouble())
                    m.resistance?.let { put("resistance", it) }
                    m.bodyFatPercent?.let { put("fat", it.toDouble()) }
                    m.userId?.let { put("userId", it) }
                    m.heartRate?.let { put("heartRate", it) }
                    put("final", m.isFinal)
                    put("raw", m.rawHex)
                    put("ver", m.protoVersion)
                    m.composition?.let { c ->
                        put("comp", JSONObject().apply {
                            put("bmi", c.bmi.toDouble())
                            put("fat", c.fatPercent.toDouble())
                            put("muscle", c.musclePercent.toDouble())
                            put("water", c.waterPercent.toDouble())
                            put("protein", c.proteinPercent.toDouble())
                            put("bone", c.boneKg.toDouble())
                            put("visceral", c.visceralFat)
                            put("bmr", c.bmr.toDouble())
                            put("bodyAge", c.bodyAge)
                            put("lean", c.leanBodyMassKg.toDouble())
                            put("fatMass", c.fatMassKg.toDouble())
                        })
                    }
                })
            }
            file.writeText(arr.toString())
        } catch (_: Exception) {
        }
    }
}
