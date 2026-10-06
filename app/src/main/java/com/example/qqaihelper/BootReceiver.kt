package com.example.qqaihelper

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat

/**
 * 开机自启广播接收器。
 *
 * 系统开机完成后，会广播 Intent.ACTION_BOOT_COMPLETED。
 * 我们在这里恢复用户上次开启的保活选项：
 * 1. 前台服务（如果 keep_alive_enabled = true）
 * 2. 1 像素悬浮窗（如果 pixel_window_enabled = true 且已有悬浮窗权限）
 *
 * ⚠️ 需要 RECEIVE_BOOT_COMPLETED 权限，并在 AndroidManifest.xml 注册。
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        AppLogger.init(context)
        val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)

        // 1. 恢复前台服务
        if (prefs.getBoolean("keep_alive_enabled", false)) {
            try {
                val serviceIntent = Intent(context, KeepAliveService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    ContextCompat.startForegroundService(context, serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
                AppLogger.d("✅ 开机自启：前台服务已恢复")
            } catch (e: Exception) {
                AppLogger.e("开机自启前台服务失败: ${e.message}")
            }
        }

        // 2. 恢复 1 像素悬浮窗（需权限已授予）
        if (prefs.getBoolean("pixel_window_enabled", false) &&
            Settings.canDrawOverlays(context)) {
            PixelWindowManager.show(context)
        }
    }
}
