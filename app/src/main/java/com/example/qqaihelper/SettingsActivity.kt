package com.example.qqaihelper

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val prefs = getSharedPreferences("app_settings", Context.MODE_PRIVATE)

        // 1. 初始化控件（请确保这里的 ID 和 XML 中完全一致）
        val btnBatteryOptimization = findViewById<Button>(R.id.btnBatteryOptimization)
        val switchSchedule = findViewById<Switch>(R.id.switchSchedule)
        val etStartTime = findViewById<EditText>(R.id.etStartTime)
        val etEndTime = findViewById<EditText>(R.id.etEndTime)
        val etWebhookPort = findViewById<EditText>(R.id.etWebhookPort)
        val switchWebhook = findViewById<Switch>(R.id.switchWebhook)
        val etWebhookToken = findViewById<EditText>(R.id.etWebhookToken)
        val btnSaveSettings = findViewById<Button>(R.id.btnSaveSettings)

        // 2. 加载已有的配置（首次安装默认为空，给用户留出提示词空间）
        switchSchedule.isChecked = prefs.getBoolean("schedule_enabled", false)
        etStartTime.setText(prefs.getString("schedule_start", ""))
        etEndTime.setText(prefs.getString("schedule_end", ""))
        etWebhookPort.setText(prefs.getString("webhook_port", ""))
        switchWebhook.isChecked = prefs.getBoolean("webhook_enabled", false)
        etWebhookToken.setText(prefs.getString("webhook_token", ""))

        // 3. “去设置电池优化”按钮逻辑
        btnBatteryOptimization.setOnClickListener {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            val packageName = packageName
            if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
                // 如果不在白名单里，就请求加入
                val intent = Intent().apply {
                    action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            } else {
                // 已经在白名单里了，给个提示
                Toast.makeText(this, "✅ 应用已经处于电池优化白名单中", Toast.LENGTH_SHORT).show()
            }
        }

        // 4. “保存设置”按钮逻辑
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
            finish()
        }
        findViewById<Button>(R.id.btnViewLogs).setOnClickListener {
            startActivity(android.content.Intent(this, LogViewerActivity::class.java))
        }
    }
}