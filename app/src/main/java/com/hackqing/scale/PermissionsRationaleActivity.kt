package com.hackqing.scale

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Health Connect 权限说明页（隐私政策）。
 * 系统在 Health Connect 授权页点击「隐私政策」时跳转到此页。
 */
class PermissionsRationaleActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF101418)) {
                    Column(Modifier.fillMaxSize().padding(24.dp)) {
                        Text("hackqing 隐私说明", color = Color(0xFF37D6A3), fontSize = 22.sp)
                        Text(
                            "\n本应用仅在您的设备本地读取体脂秤数据，并仅在您主动点击「同步」时，" +
                                "把体重与体脂率写入 Health Connect。\n\n" +
                                "本应用不联网、不上传任何健康数据，所有测量记录保存在本机。\n\n" +
                                "数据用途：记录体重与体脂变化趋势，供您本人查看。",
                            color = Color.White, fontSize = 14.sp,
                        )
                    }
                }
            }
        }
    }
}
