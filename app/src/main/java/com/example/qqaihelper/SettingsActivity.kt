package com.example.qqaihelper

import android.Manifest
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.LayoutInflater
import android.widget.Button
import android.widget.EditText
import android.widget.Switch
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

/**
 * 「更多设置」页面。
 *
 * 从 MainActivity 的「更多设置」按钮进入，包含 4 个独立的功能板块：
 * 1. 优化设置（弹窗）：电池优化白名单 + 前台服务保活 + 1 像素悬浮窗（实时保存）
 * 2. 定时开关：只在指定时间段内处理消息
 * 3. Webhook 端口配置：局域网 API 服务
 * 4. 日志查看入口
 *
 * 未保存更改保护：
 * 定时开关/Webhook 的输入若修改后未点「保存设置」直接返回，会弹窗询问是否保存。
 * 「去优化」弹窗里的两个开关是实时保存的，不纳入未保存保护范围。
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var prefs: android.content.SharedPreferences

    // ==================== 未保存快照 ====================
    private var initialScheduleEnabled = false
    private var initialScheduleStart = ""
    private var initialScheduleEnd = ""
    private var initialWebhookEnabled = false
    private var initialWebhookPort = ""
    private var initialWebhookToken = ""

    // 控件引用（快照对比时需要读取）
    private lateinit var switchSchedule: Switch
    private lateinit var etStartTime: EditText
    private lateinit var etEndTime: EditText
    private lateinit var switchWebhook: Switch
    private lateinit var etWebhookPort: EditText
    private lateinit var etWebhookToken: EditText

    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (prefs.getBoolean("pixel_window_enabled", false) && Settings.canDrawOverlays(this)) {
            PixelWindowManager.show(this)
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startKeepAliveService()
        } else {
            Toast.makeText(this, "未授予通知权限，前台服务无法显示通知", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        prefs = getSharedPreferences("app_settings", Context.MODE_PRIVATE)

        // ---------- 1. 绑定控件 ----------
        val btnBatteryOptimization = findViewById<Button>(R.id.btnBatteryOptimization)
        switchSchedule = findViewById(R.id.switchSchedule)
        etStartTime = findViewById(R.id.etStartTime)
        etEndTime = findViewById(R.id.etEndTime)
        etWebhookPort = findViewById(R.id.etWebhookPort)
        switchWebhook = findViewById(R.id.switchWebhook)
        etWebhookToken = findViewById(R.id.etWebhookToken)
        val btnSaveSettings = findViewById<Button>(R.id.btnSaveSettings)

        // ---------- 2. 加载已有配置 ----------
        switchSchedule.isChecked = prefs.getBoolean("schedule_enabled", false)
        etStartTime.setText(prefs.getString("schedule_start", ""))
        etEndTime.setText(prefs.getString("schedule_end", ""))
        etWebhookPort.setText(prefs.getString("webhook_port", ""))
        switchWebhook.isChecked = prefs.getBoolean("webhook_enabled", false)
        etWebhookToken.setText(prefs.getString("webhook_token", ""))

        // 记录初始快照
        snapshotCurrentState()

        // ---------- 3. 「去优化」按钮 ----------
        btnBatteryOptimization.setOnClickListener {
            showOptimizationDialog()
        }

        // ---------- 4. 保存所有设置 ----------
        btnSaveSettings.setOnClickListener {
            saveSettings()
            Toast.makeText(this, "✅ 设置已保存", Toast.LENGTH_SHORT).show()
            finish()
        }

        // ---------- 5. 日志查看入口 ----------
        findViewById<Button>(R.id.btnViewLogs).setOnClickListener {
            startActivity(Intent(this, LogViewerActivity::class.java))
        }

        // ---------- 6. 返回键拦截 ----------
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (hasUnsavedChanges()) {
                    showUnsavedChangesDialog()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }

    // ==================== 未保存更改保护 ====================

    private fun snapshotCurrentState() {
        initialScheduleEnabled = switchSchedule.isChecked
        initialScheduleStart = etStartTime.text.toString()
        initialScheduleEnd = etEndTime.text.toString()
        initialWebhookEnabled = switchWebhook.isChecked
        initialWebhookPort = etWebhookPort.text.toString()
        initialWebhookToken = etWebhookToken.text.toString()
    }

    private fun hasUnsavedChanges(): Boolean {
        return switchSchedule.isChecked != initialScheduleEnabled ||
                etStartTime.text.toString() != initialScheduleStart ||
                etEndTime.text.toString() != initialScheduleEnd ||
                switchWebhook.isChecked != initialWebhookEnabled ||
                etWebhookPort.text.toString() != initialWebhookPort ||
                etWebhookToken.text.toString() != initialWebhookToken
    }

    private fun showUnsavedChangesDialog() {
        AlertDialog.Builder(this)
            .setTitle("未保存的更改")
            .setMessage("当前设置已修改，是否保存后退出？")
            .setPositiveButton("保存") { _, _ ->
                saveSettings()
                finish()
            }
            .setNegativeButton("不保存") { _, _ ->
                finish()
            }
            .setNeutralButton("取消", null)
            .show()
    }

    /** 统一保存设置并刷新快照 */
    private fun saveSettings() {
        prefs.edit()
            .putBoolean("schedule_enabled", switchSchedule.isChecked)
            .putString("schedule_start", etStartTime.text.toString().trim())
            .putString("schedule_end", etEndTime.text.toString().trim())
            .putString("webhook_port", etWebhookPort.text.toString().trim())
            .putBoolean("webhook_enabled", switchWebhook.isChecked)
            .putString("webhook_token", etWebhookToken.text.toString().trim())
            .apply()
        // 保存后刷新快照，避免返回时再次提示
        snapshotCurrentState()
    }

    // ==================== 优化设置弹窗 ====================

    private fun showOptimizationDialog() {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_optimization, null)
        val btnBatteryInDialog = view.findViewById<Button>(R.id.btnBatteryOptInDialog)
        val switchKeepAlive = view.findViewById<Switch>(R.id.switchKeepAlive)
        val switchPixelWindow = view.findViewById<Switch>(R.id.switchPixelWindow)
        val btnConfirm = view.findViewById<Button>(R.id.btnDialogConfirm)
        val btnCancel = view.findViewById<Button>(R.id.btnDialogCancel)

        switchKeepAlive.isChecked = prefs.getBoolean("keep_alive_enabled", false)
        switchPixelWindow.isChecked = prefs.getBoolean("pixel_window_enabled", false)

        btnBatteryInDialog.setOnClickListener {
            jumpToBatteryOptimizationSettings()
        }

        val dialog = AlertDialog.Builder(this)
            .setView(view)
            .create()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        btnCancel.setOnClickListener { dialog.dismiss() }

        btnConfirm.setOnClickListener {
            prefs.edit()
                .putBoolean("keep_alive_enabled", switchKeepAlive.isChecked)
                .putBoolean("pixel_window_enabled", switchPixelWindow.isChecked)
                .apply()

            applyKeepAlive(switchKeepAlive.isChecked)
            applyPixelWindow(switchPixelWindow.isChecked)

            dialog.dismiss()
        }

        dialog.show()
    }

    private fun jumpToBatteryOptimizationSettings() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (!pm.isIgnoringBatteryOptimizations(packageName)) {
            val intent = Intent().apply {
                action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                data = Uri.parse("package:$packageName")
            }
            startActivity(intent)
        } else {
            Toast.makeText(this, "✅ 已处于电池优化白名单中", Toast.LENGTH_SHORT).show()
        }
    }

    private fun applyKeepAlive(enabled: Boolean) {
        if (enabled) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    return
                }
            }
            startKeepAliveService()
        } else {
            stopService(Intent(this, KeepAliveService::class.java))
            AppLogger.d("⏸️ 前台保活服务已停止")
        }
    }

    private fun startKeepAliveService() {
        val intent = Intent(this, KeepAliveService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        AppLogger.d("✅ 前台保活服务已启动")
    }

    private fun applyPixelWindow(enabled: Boolean) {
        if (enabled) {
            if (Settings.canDrawOverlays(this)) {
                PixelWindowManager.show(this)
            } else {
                Toast.makeText(this, "请授予悬浮窗权限", Toast.LENGTH_SHORT).show()
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                overlayPermissionLauncher.launch(intent)
            }
        } else {
            PixelWindowManager.hide()
        }
    }
}
