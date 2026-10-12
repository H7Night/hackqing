package com.hackqing.scale.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.BodyFatRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.units.Mass
import androidx.health.connect.client.units.Percentage
import com.hackqing.scale.model.ScaleMeasurement
import java.time.Instant

/**
 * Health Connect 单项同步。
 *
 * 写入项目：体重（WeightRecord）；若测量含体脂率则一并写入 BodyFatRecord。
 * 目前为手动触发（每条记录点“同步”）。
 */
class HealthConnectManager(private val context: Context) {

    fun sdkStatus(): Int = HealthConnectClient.getSdkStatus(context)

    val isAvailable: Boolean
        get() = sdkStatus() == HealthConnectClient.SDK_AVAILABLE

    /** 需要申请的权限集合。 */
    val permissions: Set<String> = setOf(
        HealthPermission.getWritePermission(WeightRecord::class),
        HealthPermission.getWritePermission(BodyFatRecord::class),
    )

    suspend fun hasAllPermissions(): Boolean {
        if (!isAvailable) return false
        return try {
            val granted = HealthConnectClient.getOrCreate(context)
                .permissionController.getGrantedPermissions()
            granted.containsAll(permissions)
        } catch (_: Exception) {
            false
        }
    }

    /** 单项同步：写入一条测量。 */
    suspend fun sync(m: ScaleMeasurement) {
        val client = HealthConnectClient.getOrCreate(context)
        val meta = Metadata.manualEntry()
        val time = Instant.ofEpochMilli(m.timeMillis)
        val offset = java.time.ZoneId.systemDefault().rules.getOffset(time)
        val records = mutableListOf<Record>()
        records += WeightRecord(
            time = time,
            zoneOffset = offset,
            weight = Mass.kilograms(m.weightKg.toDouble()),
            metadata = meta,
        )
        m.bodyFatPercent?.let {
            records += BodyFatRecord(
                time = time,
                zoneOffset = offset,
                percentage = Percentage(it.toDouble()),
                metadata = meta,
            )
        }
        client.insertRecords(records)
    }
}
