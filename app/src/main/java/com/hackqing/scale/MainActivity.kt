package com.hackqing.scale

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.Color
import com.hackqing.scale.ui.ScaleScreen
import com.hackqing.scale.ui.ScaleViewModel

class MainActivity : ComponentActivity() {

    private val vm: ScaleViewModel by viewModels()

    private val requestPerms = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* 结果无需处理，界面会在无权限时提示扫描失败 */ }

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
    }
}
