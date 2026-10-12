package com.hackqing.scale.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

/** 开机后若此前开启了后台接收，则自动拉起服务。 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val prefs = context.getSharedPreferences("hackqing_prefs", Context.MODE_PRIVATE)
        if (prefs.getBoolean("bg_enabled", false)) {
            val i = Intent(context, ScaleService::class.java).setAction(ScaleService.ACTION_START)
            ContextCompat.startForegroundService(context, i)
        }
    }
}
