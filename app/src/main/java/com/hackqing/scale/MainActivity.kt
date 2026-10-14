package com.hackqing.scale

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.Color
import com.hackqing.scale.ui.ScaleScreen
import com.hackqing.scale.ui.ScaleViewModel
import com.hackqing.scale.service.ScaleService
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val vm: ScaleViewModel by viewModels()

    private val requestPerms = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* 结果无需处理，界面会在无权限时提示扫描失败 */ ensureBackground() }

    private val requiredPermissions: Array<String>
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
            )
        } else {
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
            )
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFF37D6A3),
                    background = Color(0xFF101418),
                    surface = Color(0xFF1B2129),
                    onBackground = Color.White,
                    onSurface = Color.White,
                )
            ) {
                LaunchedEffect(Unit) {
                    requestPerms.launch(requiredPermissions)
                }
                ScaleScreen(vm)
            }
        }
        // 若后台开关为开且已授权，则拉起前台服务（修复：此前仅手动切换开关才会启动）
        ensureBackground()
    }

    /** 具备蓝牙权限且后台开关为开时，拉起后台自动接收服务。 */
    private fun ensureBackground() {
        val granted = requiredPermissions.all {
            checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED
        }
        if (!granted) return
        val enabled = getSharedPreferences("hackqing_prefs", MODE_PRIVATE)
            .getBoolean("bg_enabled", false)
        if (!enabled) return
        ContextCompat.startForegroundService(
            this,
            Intent(this, ScaleService::class.java).setAction(ScaleService.ACTION_START),
        )
    }
}
