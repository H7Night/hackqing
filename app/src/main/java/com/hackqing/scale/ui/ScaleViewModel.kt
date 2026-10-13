package com.hackqing.scale.ui

import android.app.Application
import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hackqing.scale.data.ScaleRepository
import com.hackqing.scale.data.UiState
import com.hackqing.scale.health.HealthConnectManager
import com.hackqing.scale.model.ScaleMeasurement
import com.hackqing.scale.model.UserProfile
import com.hackqing.scale.service.ScaleService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ScaleViewModel(app: Application) : AndroidViewModel(app) {

    val ui: kotlinx.coroutines.flow.StateFlow<UiState>
        get() = ScaleRepository.state

    val healthConnect = HealthConnectManager(app)

    private val prefs = app.getSharedPreferences("hackqing_prefs", Application.MODE_PRIVATE)

    init {
        ScaleRepository.init(app)
    }

    val backgroundEnabled: Boolean get() = prefs.getBoolean("bg_enabled", false)

    fun isLikelyScale(d: com.hackqing.scale.data.DeviceItem): Boolean =
        ScaleRepository.client.isScale(d.name, d.device.address)

    // ---------- 连接 ----------
    fun startScan() = ScaleRepository.startScan()
    fun stopScan() = ScaleRepository.stopScan()
    fun connect(device: BluetoothDevice) = ScaleRepository.connect(device)
    fun disconnect() = ScaleRepository.disconnect()
    fun sendHex(hex: String) = ScaleRepository.sendHex(hex)
    fun resendUserInfo() = ScaleRepository.resendUserInfo()
    fun updateProfile(p: UserProfile) = ScaleRepository.updateProfile(getApplication(), p)
    fun clearHistory() = ScaleRepository.clearHistory()

    /**
     * 删除一条记录。若 [m] 已同步到 Health Connect 且 [alsoDeleteHc] 为真，
     * 则同时删除 Health Connect 中对应的数据；否则只删本地。
     */
    fun deleteMeasurement(m: ScaleMeasurement, alsoDeleteHc: Boolean, onResult: (String) -> Unit) {
        viewModelScope.launch {
            var note = ""
            if (alsoDeleteHc && m.hcSynced) {
                note = try {
                    when {
                        !healthConnect.isAvailable -> "（HC 不可用，未删除 HC 数据）"
                        !healthConnect.hasAllPermissions() -> "（无 HC 权限，未删除 HC 数据）"
                        else -> {
                            healthConnect.deleteFor(m)
                            "，并已删除 Health Connect 数据"
                        }
                    }
                } catch (e: Exception) {
                    "（HC 删除失败: ${e.message}）"
                }
            }
            ScaleRepository.deleteMeasurement(m.id)
            onResult("已删除$note")
        }
    }

    // ---------- 后台自动接收 ----------
    fun setBackground(enabled: Boolean) {
        prefs.edit().putBoolean("bg_enabled", enabled).apply()
        val ctx = getApplication<Application>()
        val intent = Intent(ctx, ScaleService::class.java)
            .setAction(if (enabled) ScaleService.ACTION_START else ScaleService.ACTION_STOP)
        if (enabled) {
            ContextCompat.startForegroundService(ctx, intent)
        } else {
            ScaleRepository.setAutoReconnect(false)
            ctx.startService(intent)
        }
    }

    // ---------- 导出 JSON ----------
    fun exportJson(uri: Uri, onDone: (Boolean) -> Unit) {
        val list = ScaleRepository.state.value.history
        viewModelScope.launch(Dispatchers.IO) {
            val ok = try {
                val arr = JSONArray()
                val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
                for (m in list) {
                    arr.put(JSONObject().apply {
                        put("time", fmt.format(Date(m.timeMillis)))
                        put("timestamp", m.timeMillis)
                        put("weightKg", m.weightKg.toDouble())
                        m.bodyFatPercent?.let { put("bodyFatPercent", it.toDouble()) }
                        m.resistance?.let { put("resistance", it) }
                        m.userId?.let { put("userId", it) }
                        m.heartRate?.let { put("heartRate", it) }
                        put("protoVersion", m.protoVersion)
                        put("rawHex", m.rawHex)
                        m.composition?.let { c ->
                            put("bodyComposition", JSONObject().apply {
                                put("bmi", c.bmi.toDouble())
                                put("fatPercent", c.fatPercent.toDouble())
                                put("musclePercent", c.musclePercent.toDouble())
                                put("waterPercent", c.waterPercent.toDouble())
                                put("proteinPercent", c.proteinPercent.toDouble())
                                put("boneKg", c.boneKg.toDouble())
                                put("visceralFat", c.visceralFat)
                                put("bmr", c.bmr.toDouble())
                                put("bodyAge", c.bodyAge)
                                put("leanBodyMassKg", c.leanBodyMassKg.toDouble())
                                put("fatMassKg", c.fatMassKg.toDouble())
                            })
                        }
                    })
                }
                val root = JSONObject().apply {
                    put("app", "hackqing")
                    put("exportedAt", System.currentTimeMillis())
                    put("count", list.size)
                    put("measurements", arr)
                }
                getApplication<Application>().contentResolver.openOutputStream(uri)?.use {
                    it.write(root.toString(2).toByteArray())
                }
                true
            } catch (e: Exception) {
                false
            }
            withContext(Dispatchers.Main) { onDone(ok) }
        }
    }

    // ---------- Health Connect ----------
    fun syncToHealthConnect(m: ScaleMeasurement, onResult: (String) -> Unit) {
        viewModelScope.launch {
            val msg = try {
                when {
                    !healthConnect.isAvailable -> "未安装/不支持 Health Connect"
                    !healthConnect.hasAllPermissions() -> "需要先授予 Health Connect 权限"
                    else -> {
                        val already = m.hcSynced
                        healthConnect.sync(m)
                        ScaleRepository.markHcSynced(m.id, true)
                        if (already) "已更新到 Health Connect（不重复）" else "已同步到 Health Connect"
                    }
                }
            } catch (e: Exception) {
                "同步失败: ${e.message}"
            }
            onResult(msg)
        }
    }
}
