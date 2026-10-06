package com.example.qqaihelper

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

/**
 * 前台保活服务。
 *
 * 目的：
 * Android 从 8.0 开始对后台服务限制越来越严格，普通后台服务几分钟内就会被系统杀掉。
 * 只有「前台服务」（通知栏有一条常驻通知）才能长期存活。
 *
 * 本服务不做任何业务逻辑，唯一职责就是：
 * - 启动一个前台通知，让系统认为 App 正在为用户工作
 * - 通知上展示「最近处理消息数」，让用户直观感受服务在工作
 * - 点击通知可回到 App 主页
 *
 * 通知更新机制：
 * QQNotificationListener 每次成功处理消息，会给 SharedPreferences 里的
 * processed_message_count +1；本服务通过注册 OnSharedPreferenceChangeListener
 * 感知变化，实时刷新通知内容，无需 IPC。
 */
class KeepAliveService : Service() {

    companion object {
        private const val CHANNEL_ID = "keep_alive_channel"
        private const val NOTIFICATION_ID = 1001
        private const val PREFS_NAME = "app_settings"
        private const val KEY_PROCESSED_COUNT = "processed_message_count"
    }

    private lateinit var prefs: SharedPreferences

    /** 监听处理计数变化，实时刷新通知 */
    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == KEY_PROCESSED_COUNT) {
            updateNotification()
        }
    }

    override fun onCreate() {
        super.onCreate()
        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        createNotificationChannel()
        prefs.registerOnSharedPreferenceChangeListener(prefsListener)
        // 启动前台服务（必须有通知，否则系统会崩溃）
        startForeground(NOTIFICATION_ID, buildNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // START_STICKY：被系统杀掉后自动重启
        updateNotification()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        prefs.unregisterOnSharedPreferenceChangeListener(prefsListener)
        // 移除前台通知
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    /**
     * Android 8.0+ 必须先注册通知渠道，否则通知无法显示。
     * 用 IMPORTANCE_LOW 让通知不出声、不振动、不弹横幅。
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "后台保活",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "保持消息监听服务运行，防止被系统回收"
                setShowBadge(false)
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        // 点击通知返回 App 主页
        val intent = Intent(this, HomeActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val count = prefs.getInt(KEY_PROCESSED_COUNT, 0)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("messageAIHelper 运行中")
            .setContentText("已处理消息: $count 条")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun updateNotification() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, buildNotification())
    }
}
