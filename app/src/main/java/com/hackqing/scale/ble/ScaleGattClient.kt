package com.hackqing.scale.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.hackqing.scale.model.UserProfile
import java.util.Locale
import java.util.UUID

/**
 * 体脂秤 BLE 客户端：扫描 -> 连接 -> 发现服务 -> 订阅通知 -> 握手 -> 收数据。
 * 用与好轻相同的 UUID 规则（写 0000ffe9 / 通知 0000ffe4）匹配特征。
 */
@SuppressLint("MissingPermission")
class ScaleGattClient(private val context: Context) {

    enum class State { IDLE, SCANNING, CONNECTING, DISCOVERING, READY, DISCONNECTED }

    companion object {
        private const val TAG = "hackqing-ble"
        private val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        private val MTU = 75
    }

    var onState: (State, String) -> Unit = { _, _ -> }
    var onDeviceFound: (BluetoothDevice, Int, String) -> Unit = { _, _, _ -> }
    var onEvent: (YunmaiProtocol.Event) -> Unit = {}
    var onLog: (String) -> Unit = {}
    var onMeasurement: (com.hackqing.scale.model.ScaleMeasurement) -> Unit = {}

    /** 后台自动重连总开关 */
    var autoReconnect: Boolean = false
        private set

    /** 扫描到云麦秤时是否自动连接（默认开启） */
    var autoConnectToScale: Boolean = true

    /** 后台省电扫描模式 */
    private var lowPowerScan = false

    /** 最近一次成功连接的秤地址，用于优先匹配 */
    var targetAddress: String? = null
        private set

    private val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val adapter get() = manager.adapter

    private var gatt: BluetoothGatt? = null
    private var writeChar: BluetoothGattCharacteristic? = null
    private var notifyChar: BluetoothGattCharacteristic? = null
    private var scanning = false
    private val handler = Handler(Looper.getMainLooper())

    val isConnected: Boolean get() = gatt != null
    val deviceAddress: String? get() = gatt?.device?.address

    /** 供握手写入秤的用户档案 */
    var userProfile: UserProfile = UserProfile()

    private val writeQueue = ArrayDeque<ByteArray>()
    private var writing = false

    // ---------------- 扫描 ----------------

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val name = try {
                result.scanRecord?.deviceName ?: result.device.name ?: ""
            } catch (e: SecurityException) {
                ""
            }
            onDeviceFound(result.device, result.rssi, name)
            if (autoConnectToScale && gatt == null && isScale(name, result.device.address)) {
                onLog("发现云麦秤 ${name.ifBlank { result.device.address }}，自动连接")
                connect(result.device)
            }
        }

        override fun onScanFailed(errorCode: Int) {
            scanning = false
            onState(State.IDLE, "扫描失败: $errorCode")
        }
    }

    /** 是否为本项目要接管的云麦/好轻体脂秤。 */
    fun isScale(name: String?, address: String?): Boolean {
        if (address != null && address.equals(targetAddress, true)) return true
        if (name != null && name.contains("yunmai", true)) return true
        // 云麦 OUI
        if (address != null && address.uppercase(Locale.ROOT).startsWith("80:EA:CA")) return true
        return false
    }

    // ---------------- 自动重连 ----------------

    private val SCAN_WINDOW_MS = 15_000L
    private val SCAN_GAP_MS = 5_000L

    private val reconnectLoop = object : Runnable {
        override fun run() {
            if (!autoReconnect) return
            if (gatt != null) return
            startScan()
            handler.postDelayed({
                if (autoReconnect && gatt == null) stopScanQuiet()
            }, SCAN_WINDOW_MS)
            handler.postDelayed(this, SCAN_WINDOW_MS + SCAN_GAP_MS)
        }
    }

    private fun stopScanQuiet() {
        if (!scanning) return
        try {
            adapter?.bluetoothLeScanner?.stopScan(scanCallback)
        } catch (_: Exception) {
        }
        scanning = false
    }

    private fun scheduleReconnect(delayMs: Long) {
        if (!autoReconnect) return
        handler.removeCallbacks(reconnectLoop)
        handler.postDelayed(reconnectLoop, delayMs)
    }

    /**
     * 开启/关闭后台自动重连。开启后会周期性扫描，发现云麦秤即自动连接；
     * 断开连接后自动重试，实现"早上上秤即自动收到数据"。
     */
    fun setAutoReconnect(enabled: Boolean, lowPower: Boolean = false) {
        autoReconnect = enabled
        lowPowerScan = lowPower
        handler.removeCallbacks(reconnectLoop)
        if (enabled) {
            scheduleReconnect(0)
        } else {
            stopScanQuiet()
        }
    }

    fun startScan() {
        val a = adapter ?: run { onState(State.IDLE, "蓝牙不可用"); return }
        if (!a.isEnabled) { onState(State.IDLE, "蓝牙未开启"); return }
        if (scanning) return
        val scanner = a.bluetoothLeScanner ?: run { onState(State.IDLE, "扫描器不可用"); return }
        val settings = ScanSettings.Builder()
            .setScanMode(
                if (lowPowerScan) ScanSettings.SCAN_MODE_BALANCED
                else ScanSettings.SCAN_MODE_LOW_LATENCY
            )
            .build()
        scanner.startScan(null, settings, scanCallback)
        scanning = true
        onState(State.SCANNING, "正在扫描…")
    }

    fun stopScan() {
        if (!scanning) return
        try {
            adapter?.bluetoothLeScanner?.stopScan(scanCallback)
        } catch (_: Exception) {
        }
        scanning = false
        if (gatt == null) onState(State.IDLE, "已停止扫描")
    }

    // ---------------- 连接 ----------------

    fun connect(device: BluetoothDevice) {
        stopScan()
        onState(State.CONNECTING, "正在连接 ${device.address} …")
        closeGatt()
        gatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        } else {
            device.connectGatt(context, false, gattCallback)
        }
    }

    fun disconnect() {
        closeGatt()
        onState(State.DISCONNECTED, "已断开")
    }

    private fun closeGatt() {
        try {
            gatt?.disconnect()
            gatt?.close()
        } catch (_: Exception) {
        }
        gatt = null
        writeChar = null
        notifyChar = null
        writeQueue.clear()
        writing = false
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_CONNECTED) {
                targetAddress = g.device.address
                onState(State.DISCOVERING, "已连接，发现服务中…")
                g.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                onLog("连接断开: status=$status")
                onState(State.DISCONNECTED, "连接断开 (status=$status)")
                if (autoReconnect) {
                    onLog("自动重连：${SCAN_WINDOW_MS / 1000} 秒后重新扫描")
                    scheduleReconnect(3_000L)
                }
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                onState(State.IDLE, "服务发现失败: $status")
                return
            }
            logServices(g.services)
            pickCharacteristics(g.services)
            if (writeChar == null || notifyChar == null) {
                onState(State.IDLE, "未找到秤特征 (需 ffe9 写 / ffe4 通知)")
                return
            }
            onLog("写特征=${writeChar!!.uuid}  通知特征=${notifyChar!!.uuid}")
            enableNotify(g, notifyChar!!)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                g.requestMtu(MTU)
            }
            onState(State.READY, "已就绪，可开始测量")
            // 进入测量状态：握手（延迟后按好轻实测顺序发送）
            handler.postDelayed({ postHandshake() }, 500)
        }

        override fun onMtuChanged(g: BluetoothGatt, mtu: Int, status: Int) {
            onLog("MTU=$mtu status=$status")
        }

        override fun onCharacteristicChanged(
            g: BluetoothGatt,
            c: BluetoothGattCharacteristic,
            value: ByteArray,
        ) {
            handleNotify(value)
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic) {
            @Suppress("DEPRECATION")
            handleNotify(c.value)
        }

        override fun onCharacteristicWrite(
            g: BluetoothGatt,
            c: BluetoothGattCharacteristic,
            status: Int,
        ) {
            onLog("写入完成 status=$status  ${YunmaiProtocol.bytesToHex(c.value)}")
            writing = false
            drainQueue()
        }

        override fun onDescriptorWrite(
            g: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int,
        ) {
            onLog("订阅完成 status=$status")
        }
    }

    private fun logServices(services: List<BluetoothGattService>) {
        for (s in services) {
            onLog("服务 ${s.uuid}")
            for (c in s.characteristics) {
                onLog("   特征 ${c.uuid}  props=0x%02X".format(c.properties))
            }
        }
    }

    private fun pickCharacteristics(services: List<BluetoothGattService>) {
        for (s in services) {
            for (c in s.characteristics) {
                val u = c.uuid.toString().lowercase(Locale.ROOT)
                if (u.contains(YunmaiProtocol.WRITE_CHAR_HINT)) writeChar = c
                if (u.contains(YunmaiProtocol.NOTIFY_CHAR_HINT)) notifyChar = c
            }
        }
    }

    private fun enableNotify(g: BluetoothGatt, c: BluetoothGattCharacteristic) {
        g.setCharacteristicNotification(c, true)
        val d = c.getDescriptor(CCCD_UUID) ?: run {
            onLog("无 CCCD 描述符，尝试直接订阅")
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            g.writeDescriptor(d, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
        } else {
            @Suppress("DEPRECATION")
            d.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            @Suppress("DEPRECATION")
            g.writeDescriptor(d)
        }
    }

    private fun handleNotify(value: ByteArray) {
        if (value.isEmpty()) return
        val hex = YunmaiProtocol.bytesToHex(value)
        val event = YunmaiProtocol.parse(value)
        onLog("收 ${YunmaiProtocol.cmdName(cmdOf(hex))}  $hex")
        onEvent(event)
        when (event) {
            is YunmaiProtocol.Event.Final -> {
                onMeasurement(event.measurement)
                // 好轻在收到最终结果后回复 0D041410 (cmd 0x14)
                send(YunmaiProtocol.ackFinish())
            }
            else -> Unit
        }
    }

    private fun cmdOf(hex: String): Int =
        if (hex.length >= 8) Integer.parseInt(hex.substring(6, 8), 16) else -1

    // ---------------- 发送 ----------------

    /**
     * 连接后握手：严格复刻好轻实测日志的 7 帧序列（ScaleCheck.log "Sender send ok!!hex:"）：
     *   0D05130016 / 用户信息 / 0D041713 / 时间同步 / 0D044246 / 0D05F000F5 / 0D04181C
     */
    private fun postHandshake() {
        onLog("开始握手（复刻好轻序列）")
        val frames = listOf(
            YunmaiProtocol.cmd13(),
            YunmaiProtocol.userInfo(YunmaiProtocol.USER_ADD_OR_QUERY, userProfile),
            YunmaiProtocol.queryTime(),
            YunmaiProtocol.timeSync(),
            YunmaiProtocol.serialQuery(),
            YunmaiProtocol.firmwareQuery(),
            YunmaiProtocol.cmd18(),
        )
        frames.forEachIndexed { i, frame ->
            handler.postDelayed({ send(frame) }, i * 250L)
        }
    }

    fun send(bytes: ByteArray) {
        writeQueue.addLast(bytes)
        drainQueue()
    }

    fun sendHex(hex: String) {
        send(YunmaiProtocol.hexToBytes(hex))
    }

    fun resendUserInfo() {
        send(YunmaiProtocol.userInfo(YunmaiProtocol.USER_ADD_OR_UPDATE, userProfile))
    }

    private fun drainQueue() {
        if (writing) return
        val g = gatt ?: return
        val c = writeChar ?: return
        val data = writeQueue.removeFirstOrNull() ?: return
        writing = true
        c.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            g.writeCharacteristic(c, data, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
        } else {
            @Suppress("DEPRECATION")
            c.value = data
            @Suppress("DEPRECATION")
            g.writeCharacteristic(c)
        }
    }

    fun rawLog(s: String) { Log.d(TAG, s) }
}
