package com.hackqing.scale.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.health.connect.client.PermissionController
import com.hackqing.scale.ble.ScaleGattClient
import com.hackqing.scale.data.UiState
import com.hackqing.scale.model.ScaleMeasurement
import com.hackqing.scale.model.UserProfile
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Bg: Color
    @Composable get() = MaterialTheme.colorScheme.background
private val CardBg: Color
    @Composable get() = MaterialTheme.colorScheme.surfaceContainer
private val Accent: Color
    @Composable get() = MaterialTheme.colorScheme.primary
private val Dim: Color
    @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant

@Composable
fun ScaleScreen(vm: ScaleViewModel) {
    val ui by vm.ui.collectAsState()
    var tab by remember { mutableStateOf(0) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // 导出 JSON
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) vm.exportJson(uri) { ok ->
            scope.launch { snackbar.showSnackbar(if (ok) "已导出 JSON" else "导出失败") }
        }
    }

    // Health Connect 权限（授权成功后自动补同步）。syncingIds 用于行内进度反馈，
    // 点击后立即进入「同步中」状态，避免出现按钮无反应的观感。
    var pendingHc by remember { mutableStateOf<ScaleMeasurement?>(null) }
    var syncingIds by remember { mutableStateOf(emptySet<Long>()) }
    val hcLauncher = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { granted ->
        val target = pendingHc
        pendingHc = null
        if (target != null) {
            if (granted.isNotEmpty()) {
                vm.syncToHealthConnect(target) { msg ->
                    syncingIds = syncingIds - target.id
                    scope.launch { snackbar.showSnackbar(msg) }
                }
            } else {
                syncingIds = syncingIds - target.id
                scope.launch { snackbar.showSnackbar("未授予 Health Connect 权限，未同步") }
            }
        }
    }

    Scaffold(
        containerColor = Bg,
        topBar = { TopBar(ui) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            PrimaryTabRow(selectedTabIndex = tab, containerColor = Bg, contentColor = Accent) {
                listOf("测量", "趋势", "历史", "调试").forEachIndexed { i, t ->
                    Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t) })
                }
            }
            when (tab) {
                0 -> MeasureTab(ui, vm, snackbar)
                1 -> com.hackqing.scale.ui.trends.TrendsTab(ui.history)
                2 -> HistoryTab(
                    ui, vm, snackbar, syncingIds,
                    onSyncHc = { m ->
                        pendingHc = m
                        syncingIds = syncingIds + m.id
                        hcLauncher.launch(vm.healthConnect.permissions)
                    },
                    onExport = { exportLauncher.launch("hackqing-weight.json") },
                )
                else -> DebugTab(ui, vm)
            }
        }
    }
}

@Composable
private fun TopBar(ui: UiState) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(
            Modifier.fillMaxWidth()
                // 背景铺满透明状态栏区域，内容避让系统栏，避免标题被状态栏遮挡
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 14.dp),
        ) {
            Text(
                "hackqing",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "云麦/好轻 体脂秤数据接管",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.width(10.dp).height(10.dp)
                        .background(stateColor(ui.state), RoundedCornerShape(50)),
                )
                Spacer(Modifier.width(8.dp))
                Text(ui.statusText, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyMedium)
            }
            ui.connectedAddress?.let {
                Text(
                    "已连: $it",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

@Composable
private fun stateColor(s: ScaleGattClient.State): Color = when (s) {
    ScaleGattClient.State.READY -> Accent
    ScaleGattClient.State.SCANNING, ScaleGattClient.State.CONNECTING, ScaleGattClient.State.DISCOVERING -> Color(0xFFFFC857)
    else -> Dim
}

@Composable
private fun MeasureTab(ui: UiState, vm: ScaleViewModel, snackbar: SnackbarHostState) {
    var background by remember { mutableStateOf(vm.backgroundEnabled) }
    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Card(colors = CardDefaults.cardColors(containerColor = CardBg), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    ui.liveWeightKg?.let { String.format(Locale.US, "%.2f", it) } ?: "--.--",
                    color = Accent, fontSize = 64.sp, fontWeight = FontWeight.Bold,
                )
                Text("kg (实时)", color = Dim, fontSize = 13.sp)
                if (ui.measuringHeartRate) Text("心率测量中…", color = Color(0xFFFF6B6B), fontSize = 13.sp)
            }
        }

        Spacer(Modifier.height(12.dp))
        Card(colors = CardDefaults.cardColors(containerColor = CardBg), modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("后台自动接收", color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp)
                    Text("常驻后台自动重连，上秤即自动同步", color = Dim, fontSize = 11.sp)
                }
                Switch(checked = background, onCheckedChange = { on ->
                    if (on && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    background = on
                    vm.setBackground(on)
                })
            }
        }

        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (ui.state == ScaleGattClient.State.SCANNING) {
                OutlinedButton(onClick = { vm.stopScan() }, modifier = Modifier.weight(1f)) { Text("停止扫描") }
            } else {
                Button(onClick = { vm.startScan() }, modifier = Modifier.weight(1f)) { Text("扫描设备") }
            }
            if (ui.connectedAddress != null) {
                OutlinedButton(onClick = { vm.disconnect() }, modifier = Modifier.weight(1f)) { Text("断开") }
            }
        }

        Spacer(Modifier.height(12.dp))
        Text("设备", color = Dim, fontSize = 12.sp)
        Spacer(Modifier.height(6.dp))
        if (ui.devices.isEmpty()) {
            Text(
                if (ui.state == ScaleGattClient.State.SCANNING) "正在扫描附近设备…" else "未发现设备，点上方「扫描设备」开始",
                color = Dim, fontSize = 12.sp,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }
        ui.devices.forEach { d ->
            Card(colors = CardDefaults.cardColors(containerColor = CardBg), modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            d.name.ifBlank { "(无名)" } + if (vm.isLikelyScale(d)) "  · 云麦秤" else "",
                            color = if (vm.isLikelyScale(d)) Accent else MaterialTheme.colorScheme.onSurface,
                            fontSize = 15.sp,
                        )
                        Text("${d.device.address}   RSSI ${d.rssi}", color = Dim, fontSize = 11.sp)
                    }
                    Button(onClick = { vm.connect(d.device) }) { Text("连接") }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Text("用户档案（写入秤用于计算体脂）", color = Dim, fontSize = 12.sp)
        Spacer(Modifier.height(6.dp))
        ProfileEditor(ui, vm, snackbar)
    }
}

@Composable
private fun ProfileEditor(ui: UiState, vm: ScaleViewModel, snackbar: SnackbarHostState) {
    val scope = rememberCoroutineScope()
    val saved = ui.profile
    var editing by remember { mutableStateOf(false) }
    // 编辑中或已保存档案变化时，草稿重置为已保存值，未保存的改动不会外泄
    var draft by remember(saved, editing) { mutableStateOf(saved) }

    Column {
        if (editing) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumField("身高 cm", draft.heightCm.toString(), Modifier.weight(1f)) {
                    draft = draft.copy(heightCm = it.toIntOrNull() ?: draft.heightCm)
                }
                NumField("年龄", draft.age.toString(), Modifier.weight(1f)) {
                    draft = draft.copy(age = it.toIntOrNull() ?: draft.age)
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumField("性别(1男2女)", draft.sex.toString(), Modifier.weight(1f)) {
                    draft = draft.copy(sex = (it.toIntOrNull() ?: draft.sex).coerceIn(1, 2))
                }
                NumField("单位(1kg2lb3斤)", draft.unit.toString(), Modifier.weight(1f)) {
                    draft = draft.copy(unit = (it.toIntOrNull() ?: draft.unit).coerceIn(1, 3))
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { editing = false }, modifier = Modifier.weight(1f)) {
                    Text("取消")
                }
                Button(
                    onClick = {
                        vm.updateProfile(draft)
                        editing = false
                        scope.launch { snackbar.showSnackbar("已保存用户档案，历史体成分已按新档案重算") }
                    },
                    modifier = Modifier.weight(1f),
                ) { Text("保存") }
            }
        } else {
            ProfileSummaryCard(saved)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        vm.resendUserInfo()
                        scope.launch { snackbar.showSnackbar("已向秤写入当前用户档案") }
                    },
                    modifier = Modifier.weight(1f),
                ) { Text("写入秤") }
                Button(onClick = { editing = true }, modifier = Modifier.weight(1f)) {
                    Text("编辑")
                }
            }
        }
    }
}

@Composable
private fun ProfileSummaryCard(p: UserProfile) {
    Card(colors = CardDefaults.cardColors(containerColor = CardBg), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            ProfileValueRow("身高", "${p.heightCm} cm")
            ProfileValueRow("年龄", "${p.age} 岁")
            ProfileValueRow("性别", if (p.sex == 2) "女" else "男")
            ProfileValueRow("单位", when (p.unit) { 2 -> "lb"; 3 -> "斤"; else -> "kg" })
        }
    }
}

@Composable
private fun ProfileValueRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, color = Dim, fontSize = 12.sp, modifier = Modifier.width(64.dp))
        Text(value, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp)
    }
}

@Composable
private fun NumField(label: String, value: String, modifier: Modifier, onChange: (String) -> Unit) {
    var text by remember(value) { mutableStateOf(value) }
    OutlinedTextField(
        value = text,
        onValueChange = { text = it; onChange(it) },
        label = { Text(label, fontSize = 11.sp) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
    )
}

@Composable
private fun HistoryTab(
    ui: UiState,
    vm: ScaleViewModel,
    snackbar: SnackbarHostState,
    syncingIds: Set<Long>,
    onSyncHc: (ScaleMeasurement) -> Unit,
    onExport: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var pendingDelete by remember { mutableStateOf<ScaleMeasurement?>(null) }
    var detail by remember { mutableStateOf<ScaleMeasurement?>(null) }
    val fmt = remember { SimpleDateFormat("MM-dd HH:mm", Locale.US) }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("共 ${ui.history.size} 条 · 点击查看详情", color = Dim, fontSize = 12.sp, modifier = Modifier.weight(1f))
            IconButton(onClick = onExport) {
                Icon(Icons.Filled.FileDownload, contentDescription = "导出JSON", tint = Accent)
            }
            OutlinedButton(onClick = { vm.clearHistory() }) { Text("清空") }
        }
        Spacer(Modifier.height(8.dp))

        LazyColumn(Modifier.fillMaxSize()) {
            if (ui.history.isEmpty()) {
                item {
                    Text(
                        "暂无测量记录，回到「测量」扫描设备并上秤",
                        color = Dim, fontSize = 13.sp,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                    )
                }
            }
            items(ui.history, key = { it.id }) { m ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = CardBg),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { detail = m },
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(String.format(Locale.US, "%.2f", m.weightKg), color = Accent, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.width(6.dp))
                            Text("kg", color = Dim, fontSize = 12.sp)
                            Spacer(Modifier.weight(1f))
                            Text(
                                fmt.format(Date(m.timeMillis)),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp,
                            )
                        }
                        val extra = buildString {
                            m.composition?.let {
                                append(String.format(Locale.US, "BMI %.1f  ", it.bmi))
                                append(String.format(Locale.US, "体脂 %.1f%%  ", it.fatPercent))
                                append(String.format(Locale.US, "肌肉 %.1f%%  ", it.musclePercent))
                            } ?: run {
                                m.bodyFatPercent?.let { append(String.format(Locale.US, "体脂 %.1f%%  ", it)) }
                            }
                            m.resistance?.let { append("阻抗 $it  ") }
                            m.userId?.let { append("用户#$it") }
                        }
                        if (extra.isNotBlank()) Text(extra, color = Dim, fontSize = 11.sp)
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            val syncing = m.id in syncingIds
                            TextButton(
                                enabled = !syncing,
                                onClick = {
                                    if (!vm.healthConnect.isAvailable) {
                                        scope.launch { snackbar.showSnackbar("未安装/不支持 Health Connect") }
                                    } else {
                                        onSyncHc(m)
                                    }
                                },
                            ) {
                                if (syncing) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(14.dp),
                                        strokeWidth = 2.dp,
                                        color = Accent,
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text("同步中…", fontSize = 12.sp)
                                } else {
                                    Icon(Icons.Filled.FavoriteBorder, contentDescription = null, tint = Accent)
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        if (m.hcSynced) "已同步到 Health Connect" else "同步到 Health Connect",
                                        fontSize = 12.sp,
                                    )
                                }
                            }
                            Spacer(Modifier.weight(1f))
                            IconButton(onClick = { pendingDelete = m }) {
                                Icon(
                                    Icons.Filled.Delete,
                                    contentDescription = "删除",
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // 详情
    detail?.let { m ->
        AlertDialog(
            onDismissRequest = { detail = null },
            title = { Text("记录详情") },
            text = { MeasurementDetail(m) },
            confirmButton = { TextButton(onClick = { detail = null }) { Text("关闭") } },
        )
    }

    // 删除确认（已同步的记录询问是否一并删除 Health Connect 数据）
    pendingDelete?.let { m ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除记录") },
            text = {
                Column {
                    Text(
                        "${fmt.format(Date(m.timeMillis))} · ${String.format(Locale.US, "%.2f", m.weightKg)} kg",
                        color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (m.hcSynced) {
                            "该记录已同步到 Health Connect。是否同时删除 Health Connect 中的数据？"
                        } else {
                            "此操作不可撤销。"
                        },
                        color = Dim, fontSize = 13.sp,
                    )
                }
            },
            confirmButton = {
                if (m.hcSynced) {
                    Row {
                        TextButton(onClick = {
                            vm.deleteMeasurement(m, alsoDeleteHc = false) { msg ->
                                scope.launch { snackbar.showSnackbar(msg) }
                            }
                            pendingDelete = null
                        }) { Text("仅删本地") }
                        Spacer(Modifier.width(4.dp))
                        TextButton(onClick = {
                            vm.deleteMeasurement(m, alsoDeleteHc = true) { msg ->
                                scope.launch { snackbar.showSnackbar(msg) }
                            }
                            pendingDelete = null
                        }) { Text("同时删 HC", color = MaterialTheme.colorScheme.error) }
                    }
                } else {
                    TextButton(onClick = {
                        vm.deleteMeasurement(m, alsoDeleteHc = false) { msg ->
                            scope.launch { snackbar.showSnackbar(msg) }
                        }
                        pendingDelete = null
                    }) { Text("删除", color = MaterialTheme.colorScheme.error) }
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun MeasurementDetail(m: ScaleMeasurement) {
    val fmtFull = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US) }
    Column {
        DetailRow("时间", fmtFull.format(Date(m.timeMillis)))
        DetailRow("体重", String.format(Locale.US, "%.2f kg", m.weightKg))
        m.bodyFatPercent?.let { DetailRow("体脂率", String.format(Locale.US, "%.1f %%", it)) }
        m.resistance?.let { DetailRow("阻抗", it.toString()) }
        m.heartRate?.let { DetailRow("心率", "$it bpm") }
        m.userId?.let { DetailRow("秤用户 ID", it.toString()) }
        m.composition?.let { c ->
            Spacer(Modifier.height(4.dp))
            Text("体成分", color = Accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            DetailRow("BMI", String.format(Locale.US, "%.1f", c.bmi))
            DetailRow("体脂率", String.format(Locale.US, "%.1f %%", c.fatPercent))
            DetailRow("肌肉率", String.format(Locale.US, "%.1f %%", c.musclePercent))
            DetailRow("水分率", String.format(Locale.US, "%.1f %%", c.waterPercent))
            DetailRow("蛋白质率", String.format(Locale.US, "%.1f %%", c.proteinPercent))
            DetailRow("骨量", String.format(Locale.US, "%.2f kg", c.boneKg))
            DetailRow("内脏脂肪", c.visceralFat.toString())
            DetailRow("基础代谢", String.format(Locale.US, "%.0f kcal", c.bmr))
            DetailRow("体年龄", "${c.bodyAge} 岁")
            DetailRow("去脂体重", String.format(Locale.US, "%.2f kg", c.leanBodyMassKg))
            DetailRow("脂肪重量", String.format(Locale.US, "%.2f kg", c.fatMassKg))
        }
        DetailRow("协议版本", "0x" + Integer.toHexString(m.protoVersion).uppercase(Locale.US) + " (${m.protoVersion})")
        DetailRow("原始帧", m.rawHex)
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, color = Dim, fontSize = 12.sp, modifier = Modifier.width(88.dp))
        Text(value, color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun DebugTab(ui: UiState, vm: ScaleViewModel) {
    var hex by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = hex, onValueChange = { hex = it },
                label = { Text("手动发送 HEX", fontSize = 11.sp) },
                singleLine = true, modifier = Modifier.weight(1f),
            )
            Button(onClick = { vm.sendHex(hex) }) { Text("发送") }
        }
        Spacer(Modifier.height(8.dp))
        Text("原始日志", color = Dim, fontSize = 12.sp)
        Spacer(Modifier.height(4.dp))
        Box(Modifier.fillMaxSize().background(Color(0xFF0A0D11), RoundedCornerShape(8.dp))) {
            LazyColumn(Modifier.fillMaxSize().padding(8.dp)) {
                items(ui.log) { line ->
                    Text(
                        line,
                        color = if (line.contains("收 ")) Accent else Color(0xFFB9C3D0),
                        fontSize = 10.sp, fontFamily = FontFamily.Monospace,
                    )
                }
            }
        }
    }
}
