package com.example.qqaihelper

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

/**
 * 「更多设置」页面。
 *
 * 从 MainActivity 的「更多设置」按钮进入，包含 4 个独立的功能板块：
 * 1. 电池优化白名单引导（防止 App 被系统杀后台）
 * 2. 定时开关（只在指定时间段内处理消息，节省 AI 额度）
 * 3. Webhook 端口配置（局域网 API 服务）
 * 4. 日志查看入口
 *
 * 所有配置项在点击「保存设置」时统一写入 SharedPreferences。
 * 注意：开关拨动后不会立即生效，必须点保存才会写入。
 */
class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val prefs = getSharedPreferences("app_settings", Context.MODE_PRIVATE)

        // ---------- 1. 绑定控件（ID 必须与 activity_settings.xml 一致） ----------
        val btnBatteryOptimization = findViewById<Button>(R.id.btnBatteryOptimization)
        val switchSchedule = findViewById<Switch>(R.id.switchSchedule)
        val etStartTime = findViewById<EditText>(R.id.etStartTime)
        val etEndTime = findViewById<EditText>(R.id.etEndTime)
        val etWebhookPort = findViewById<EditText>(R.id.etWebhookPort)
        val switchWebhook = findViewById<Switch>(R.id.switchWebhook)
        val etWebhookToken = findViewById<EditText>(R.id.etWebhookToken)
        val btnSaveSettings = findViewById<Button>(R.id.btnSaveSettings)

        // ---------- 2. 加载已有配置 ----------
        // 首次安装时全部为空 / 默认关闭，避免默认开启服务或暴露端口
        switchSchedule.isChecked = prefs.getBoolean("schedule_enabled", false)
        etStartTime.setText(prefs.getString("schedule_start", ""))
        etEndTime.setText(prefs.getString("schedule_end", ""))
        etWebhookPort.setText(prefs.getString("webhook_port", ""))
        switchWebhook.isChecked = prefs.getBoolean("webhook_enabled", false)
        etWebhookToken.setText(prefs.getString("webhook_token", ""))

        // ---------- 3. 引导用户将 App 加入电池优化白名单 ----------
        // 国产 ROM（小米、华为、努比亚等）对后台限制很严格，
        // 不在白名单里的话通知监听服务会被系统定期杀死，导致漏消息
        btnBatteryOptimization.setOnClickListener {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            val packageName = packageName
            if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
                // 请求加入白名单（会跳转到系统弹窗）
                val intent = Intent().apply {
                    action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            } else {
                Toast.makeText(this, "✅ 应用已经处于电池优化白名单中", Toast.LENGTH_SHORT).show()
            }
        }

        // ---------- 4. 保存所有设置 ----------
        btnSaveSettings.setOnClickListener {
            val editor = prefs.edit()
            editor.putBoolean("schedule_enabled", switchSchedule.isChecked)
            editor.putString("schedule_start", etStartTime.text.toString().trim())
            editor.putString("schedule_end", etEndTime.text.toString().trim())
            editor.putString("webhook_port", etWebhookPort.text.toString().trim())
            editor.putBoolean("webhook_enabled", switchWebhook.isChecked)
            editor.putString("webhook_token", etWebhookToken.text.toString().trim())
            editor.apply()

            Toast.makeText(this, "✅ 设置已保存", Toast.LENGTH_SHORT).show()
            // 保存后自动返回上一页（MainActivity）
            finish()
        }

        // ---------- 5. 日志查看入口 ----------
        findViewById<Button>(R.id.btnViewLogs).setOnClickListener {
            startActivity(Intent(this, LogViewerActivity::class.java))
        }
    }
}
