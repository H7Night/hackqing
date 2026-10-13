package com.hackqing.scale.data

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.util.Log
import com.hackqing.scale.ble.ScaleGattClient
import com.hackqing.scale.ble.YunmaiProtocol
import com.hackqing.scale.model.ScaleMeasurement
import com.hackqing.scale.model.UserProfile
import com.yunmai.haoqing.logic.config.ClientConfigJNI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class DeviceItem(val device: BluetoothDevice, val name: String, val rssi: Int)

data class UiState(
    val state: ScaleGattClient.State = ScaleGattClient.State.IDLE,
    val statusText: String = "未连接",
    val devices: List<DeviceItem> = emptyList(),
    val connectedAddress: String? = null,
    val liveWeightKg: Float? = null,
    val measuringHeartRate: Boolean = false,
    val history: List<ScaleMeasurement> = emptyList(),
    val log: List<String> = emptyList(),
    val profile: UserProfile = UserProfile(),
    val autoReconnect: Boolean = false,
)

/**
 * 全局共享的体脂秤状态仓库：App 界面与后台前台服务共用同一实例，
 * 保证后台接收到的数据实时反映到界面，且不会重复建立连接。
 */
object ScaleRepository {

    private const val PREFS = "hackqing_prefs"
    private const val TAG = "hackqing"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    lateinit var client: ScaleGattClient
        private set
    lateinit var store: MeasurementStore
        private set

    private var initialized = false

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val deviceMap = LinkedHashMap<String, DeviceItem>()

    @SuppressLint("MissingPermission")
    fun init(context: Context) {
        if (initialized) return
        val app = context.applicationContext
        store = MeasurementStore(app)
        client = ScaleGattClient(app)
        initialized = true

        // 初始化云麦体成分算法原生库（libconfig.so 已打补丁通过其签名校验）
        try {
            ClientConfigJNI.init(app)
            Log.d(TAG, "ClientConfigJNI.init 成功")
        } catch (t: Throwable) {
            Log.w(TAG, "ClientConfigJNI.init 失败: ${t.message}")
        }

        // 算法自检：已知样本应得 BMI≈23.6、肌肉率≈55.4%（与好轻一致）
        try {
            val t = com.hackqing.scale.body.BodyCompositionCalculator.compute(
                UserProfile(heightCm = 169, age = 18, sex = 1),
                67.45f, 343, 17.3f, 33,
            )
            Log.d(TAG, "算法自检: $t")
        } catch (e: Throwable) {
            Log.w(TAG, "算法自检失败: ${e.message}")
        }

        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val profile = UserProfile(
            userId = prefs.getInt("uid", 1),
            heightCm = prefs.getInt("height", 170),
            sex = prefs.getInt("sex", 1),
            age = prefs.getInt("age", 30),
            waistCm = prefs.getInt("waist", 85),
            bustCm = prefs.getInt("bust", 90),
            basisWeightKg = prefs.getFloat("basis", 60f),
            unit = prefs.getInt("unit", 1),
            bodyType = prefs.getInt("bodyType", -1),
            heartRateSwitch = prefs.getInt("hr", 0),
        )
        client.userProfile = profile

        // 历史记录的体成分按当前档案重算（体成分是派生数据）
        val loaded = store.load()
        val enriched = loaded.map { m ->
            val c = com.hackqing.scale.body.BodyCompositionCalculator.compute(
                profile, m.weightKg, m.resistance ?: 0, m.bodyFatPercent, m.protoVersion,
            )
            if (c != null) m.copy(composition = c) else m
        }
        _state.value = _state.value.copy(
            history = enriched,
            profile = profile,
            log = listOf("hackqing 就绪"),
        )
        if (enriched != loaded) {
            scope.launch { store.save(enriched) }
        }

        client.onState = { st, msg ->
            _state.value = _state.value.copy(
                state = st,
                statusText = msg,
                connectedAddress = client.deviceAddress
                    ?: if (st == ScaleGattClient.State.DISCONNECTED) null else _state.value.connectedAddress,
            )
        }
        client.onDeviceFound = { dev, rssi, name ->
            val n = name.ifBlank {
                try { dev.name } catch (e: SecurityException) { "" } ?: ""
            }
            deviceMap[dev.address] = DeviceItem(dev, n, rssi)
            _state.value = _state.value.copy(
                devices = deviceMap.values.sortedWith(
                    compareByDescending<DeviceItem> {
                        client.isScale(it.name, it.device.address)
                    }.thenByDescending { it.rssi }
                )
            )
        }
        client.onLog = { addLog(it) }
        client.onEvent = { ev ->
            if (ev is YunmaiProtocol.Event.LiveWeight) {
                _state.value = _state.value.copy(
                    liveWeightKg = ev.weightKg,
                    measuringHeartRate = ev.measuringHeartRate,
                )
            }
        }
        client.onMeasurement = { onFinal(it) }
    }

    private fun addLog(line: String) {
        val ts = java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.US)
            .format(java.util.Date())
        _state.value = _state.value.copy(log = (_state.value.log + "[$ts] $line").takeLast(400))
        Log.d(TAG, line)
    }

    private fun onFinal(m: ScaleMeasurement) {
        val profile = _state.value.profile
        val comp = com.hackqing.scale.body.BodyCompositionCalculator.compute(
            profile, m.weightKg, m.resistance ?: 0, m.bodyFatPercent, m.protoVersion,
        )
        val enriched = if (comp != null) m.copy(composition = comp) else m
        val list = (_state.value.history + enriched).sortedByDescending { it.timeMillis }
        _state.value = _state.value.copy(history = list, liveWeightKg = m.weightKg)
        scope.launch { store.save(list) }
        if (comp != null) {
            addLog(
                "体成分 BMI=%.1f 体脂=%.1f%% 肌肉=%.1f%% 水分=%.1f%% 蛋白=%.1f%% 骨量=%.2fkg 内脏=%d BMR=%.0f 体年龄=%d"
                    .format(comp.bmi, comp.fatPercent, comp.musclePercent, comp.waterPercent,
                        comp.proteinPercent, comp.boneKg, comp.visceralFat, comp.bmr, comp.bodyAge)
            )
        } else {
            addLog("已保存测量: ${m.weightKg}kg (体成分算法不可用或资料不足)")
        }
    }

    // ---------- 对外操作 ----------

    fun startScan() {
        deviceMap.clear()
        _state.value = _state.value.copy(devices = emptyList())
        client.startScan()
    }

    fun stopScan() = client.stopScan()

    fun connect(device: BluetoothDevice) {
        client.userProfile = _state.value.profile
        client.connect(device)
    }

    fun disconnect() {
        client.setAutoReconnect(false)
        _state.value = _state.value.copy(autoReconnect = false)
        client.disconnect()
    }

    fun setAutoReconnect(enabled: Boolean, lowPower: Boolean = false) {
        client.userProfile = _state.value.profile
        client.setAutoReconnect(enabled, lowPower)
        _state.value = _state.value.copy(autoReconnect = enabled)
        addLog(if (enabled) "已开启后台自动重连" else "已关闭后台自动重连")
    }

    fun sendHex(hex: String) {
        if (hex.isBlank()) return
        addLog("发送(手动) $hex")
        client.sendHex(hex)
    }

    fun resendUserInfo() {
        client.userProfile = _state.value.profile
        client.resendUserInfo()
    }

    fun updateProfile(context: Context, p: UserProfile) {
        _state.value = _state.value.copy(profile = p)
        client.userProfile = p
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().apply {
            putInt("uid", p.userId); putInt("height", p.heightCm); putInt("sex", p.sex)
            putInt("age", p.age); putInt("waist", p.waistCm); putInt("bust", p.bustCm)
            putFloat("basis", p.basisWeightKg); putInt("unit", p.unit)
            putInt("bodyType", p.bodyType); putInt("hr", p.heartRateSwitch)
        }.apply()

        // 档案变更后按新档案重算所有历史体成分
        val recomputed = _state.value.history.map { m ->
            val c = com.hackqing.scale.body.BodyCompositionCalculator.compute(
                p, m.weightKg, m.resistance ?: 0, m.bodyFatPercent, m.protoVersion,
            )
            if (c != null) m.copy(composition = c) else m
        }
        _state.value = _state.value.copy(history = recomputed)
        scope.launch { store.save(recomputed) }
        addLog("已更新用户档案并重算历史体成分: 身高${p.heightCm} 年龄${p.age} 性别${p.sex}")
    }

    fun deleteMeasurement(id: Long) {
        val list = _state.value.history.filterNot { it.id == id }
        _state.value = _state.value.copy(history = list)
        scope.launch { store.save(list) }
        addLog("已删除记录 #$id")
    }

    /** 标记某条测量是否已同步到 Health Connect（用于去重与删除联动）。 */
    fun markHcSynced(id: Long, synced: Boolean) {
        val list = _state.value.history.map { if (it.id == id) it.copy(hcSynced = synced) else it }
        _state.value = _state.value.copy(history = list)
        scope.launch { store.save(list) }
    }

    fun clearHistory() {
        _state.value = _state.value.copy(history = emptyList())
        scope.launch { store.save(emptyList()) }
    }
}
