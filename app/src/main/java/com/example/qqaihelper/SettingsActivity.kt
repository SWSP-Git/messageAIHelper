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
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

/**
 * 「更多设置」页面。
 *
 * 包含以下功能板块：
 * 1. 优化设置（弹窗）：电池优化白名单 + 前台服务保活 + 1 像素悬浮窗
 * 2. 定时开关
 * 3. Webhook 端口配置
 * 4. 语言切换
 * 5. 存储位置管理 + 数据备份（导出/导入 ZIP）
 * 6. 日志查看入口
 *
 * 未保存更改保护：
 * 定时开关/Webhook 的输入若修改后未保存直接返回，会弹窗询问是否保存。
 */
class SettingsActivity : BaseActivity() {

    private lateinit var prefs: android.content.SharedPreferences

    // ==================== 未保存快照 ====================
    private var initialScheduleEnabled = false
    private var initialScheduleStart = ""
    private var initialScheduleEnd = ""
    private var initialWebhookEnabled = false
    private var initialWebhookPort = ""
    private var initialWebhookToken = ""

    // 控件引用
    private lateinit var switchSchedule: Switch
    private lateinit var etStartTime: EditText
    private lateinit var etEndTime: EditText
    private lateinit var switchWebhook: Switch
    private lateinit var etWebhookPort: EditText
    private lateinit var etWebhookToken: EditText
    private lateinit var tvStorageStatus: TextView
    private lateinit var btnGrantStorage: Button

    /** 优化设置弹窗中的「前台服务保活」开关（供权限回调回滚用，弹窗关闭后置空） */
    private var dialogKeepAliveSwitch: Switch? = null

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
            Toast.makeText(this, getString(R.string.opt_permission_notification_toast), Toast.LENGTH_SHORT).show()
            // 权限被拒：回滚保活开关，避免「开关为 ON 但服务未运行」的矛盾状态
            prefs.edit().putBoolean("keep_alive_enabled", false).apply()
            dialogKeepAliveSwitch?.setOnCheckedChangeListener(null)
            dialogKeepAliveSwitch?.isChecked = false
            dialogKeepAliveSwitch?.setOnCheckedChangeListener { _, checked ->
                prefs.edit().putBoolean("keep_alive_enabled", checked).apply()
                applyKeepAlive(checked)
            }
        }
    }

    /** 请求"所有文件访问权限"（Android 11+）或 WRITE_EXTERNAL_STORAGE（旧版） */
    private val storagePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        updateStorageStatus()
    }

    private val legacyStoragePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            Toast.makeText(this, getString(R.string.storage_granted_toast), Toast.LENGTH_SHORT).show()
        }
        updateStorageStatus()
    }

    /** 导出数据的文件选择器 */
    private val exportLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri: Uri? ->
        if (uri == null) return@registerForActivityResult
        val ok = DataExporter.export(this, uri)
        Toast.makeText(
            this,
            getString(if (ok) R.string.backup_export_success else R.string.backup_export_fail),
            Toast.LENGTH_SHORT
        ).show()
    }

    /** 导入数据的文件选择器 */
    private val importLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@registerForActivityResult
        val ok = DataImporter.import(this, uri)
        Toast.makeText(
            this,
            getString(if (ok) R.string.backup_import_success else R.string.backup_import_fail),
            Toast.LENGTH_LONG
        ).show()
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
        tvStorageStatus = findViewById(R.id.tvStorageStatus)
        btnGrantStorage = findViewById(R.id.btnGrantStorage)

        // ---------- 2. 加载已有配置 ----------
        switchSchedule.isChecked = prefs.getBoolean("schedule_enabled", false)
        etStartTime.setText(prefs.getString("schedule_start", ""))
        etEndTime.setText(prefs.getString("schedule_end", ""))
        etWebhookPort.setText(prefs.getString("webhook_port", ""))
        switchWebhook.isChecked = prefs.getBoolean("webhook_enabled", false)
        etWebhookToken.setText(prefs.getString("webhook_token", ""))

        snapshotCurrentState()

        // ---------- 3. 「去优化」按钮 ----------
        btnBatteryOptimization.setOnClickListener {
            showOptimizationDialog()
        }

        // ---------- 4. 语言切换 ----------
        setupLanguageRadio()

        // ---------- 5. 存储位置管理 ----------
        updateStorageStatus()
        btnGrantStorage.setOnClickListener {
            requestStoragePermission()
        }

        // ---------- 6. 导出 / 导入 ----------
        findViewById<Button>(R.id.btnExportData).setOnClickListener {
            exportLauncher.launch(DataExporter.suggestFileName())
        }
        findViewById<Button>(R.id.btnImportData).setOnClickListener {
            importLauncher.launch(arrayOf("application/zip", "application/octet-stream", "*/*"))
        }

        // ---------- 7. 保存所有设置 ----------
        btnSaveSettings.setOnClickListener {
            saveSettings()
            Toast.makeText(this, getString(R.string.settings_saved_toast), Toast.LENGTH_SHORT).show()
            finish()
        }

        // ---------- 8. 日志查看入口 ----------
        findViewById<Button>(R.id.btnViewLogs).setOnClickListener {
            startActivity(Intent(this, LogViewerActivity::class.java))
        }

        // ---------- 9. 返回键拦截 ----------
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

    // ==================== 存储位置管理 ====================

    /** 刷新存储位置的 UI 状态 */
    private fun updateStorageStatus() {
        val usingExternal = StorageHelper.isUsingExternalStorage(this)
        if (usingExternal) {
            tvStorageStatus.text = getString(R.string.storage_external_ok)
            tvStorageStatus.setTextColor(Color.parseColor("#00C853"))
            btnGrantStorage.visibility = Button.GONE
        } else {
            tvStorageStatus.text = getString(R.string.storage_private_warn)
            tvStorageStatus.setTextColor(Color.parseColor("#D32F2F"))
            btnGrantStorage.visibility = Button.VISIBLE
        }
    }

    /**
     * 申请存储权限。
     *
     * Android 11+：跳转到"所有文件访问权限"系统设置页
     * Android 10 及以下：弹出 WRITE_EXTERNAL_STORAGE 运行时权限对话框
     */
    private fun requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.parse("package:$packageName")
                }
                storagePermissionLauncher.launch(intent)
            } catch (e: Exception) {
                // 某些 ROM 不支持 app-specific 的入口，降级到全局入口
                val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                storagePermissionLauncher.launch(intent)
            }
        } else {
            legacyStoragePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    }

    // ==================== 语言切换 ====================

    private fun setupLanguageRadio() {
        val radioGroup = findViewById<RadioGroup>(R.id.radioGroupLanguage)
        val radioZh = findViewById<RadioButton>(R.id.radioLangZh)
        val radioEn = findViewById<RadioButton>(R.id.radioLangEn)
        val radioSystem = findViewById<RadioButton>(R.id.radioLangSystem)

        when (LocaleHelper.getCurrentLanguage()) {
            LocaleHelper.LANG_ZH -> radioZh.isChecked = true
            LocaleHelper.LANG_EN -> radioEn.isChecked = true
            else -> radioSystem.isChecked = true
        }

        radioGroup.setOnCheckedChangeListener { _, checkedId ->
            val lang = when (checkedId) {
                R.id.radioLangZh -> LocaleHelper.LANG_ZH
                R.id.radioLangEn -> LocaleHelper.LANG_EN
                else -> LocaleHelper.LANG_SYSTEM
            }
            if (lang != LocaleHelper.getCurrentLanguage()) {
                LocaleHelper.setLanguage(this, lang)
            }
        }
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
            .setTitle(R.string.unsaved_title)
            .setMessage(R.string.unsaved_msg_settings)
            .setPositiveButton(R.string.unsaved_save) { _, _ ->
                saveSettings()
                finish()
            }
            .setNegativeButton(R.string.unsaved_discard) { _, _ ->
                finish()
            }
            .setNeutralButton(R.string.unsaved_cancel, null)
            .show()
    }

    private fun saveSettings() {
        prefs.edit()
            .putBoolean("schedule_enabled", switchSchedule.isChecked)
            .putString("schedule_start", etStartTime.text.toString().trim())
            .putString("schedule_end", etEndTime.text.toString().trim())
            .putString("webhook_port", etWebhookPort.text.toString().trim())
            .putBoolean("webhook_enabled", switchWebhook.isChecked)
            .putString("webhook_token", etWebhookToken.text.toString().trim())
            .apply()
        snapshotCurrentState()
    }

    // ==================== 优化设置弹窗 ====================

    private fun showOptimizationDialog() {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_optimization, null)
        val btnBatteryInDialog = view.findViewById<Button>(R.id.btnBatteryOptInDialog)
        val switchKeepAlive = view.findViewById<Switch>(R.id.switchKeepAlive)
        val switchPixelWindow = view.findViewById<Switch>(R.id.switchPixelWindow)

        // 记录引用，供通知权限被拒时回滚开关状态
        dialogKeepAliveSwitch = switchKeepAlive

        switchKeepAlive.isChecked = prefs.getBoolean("keep_alive_enabled", false)
        switchPixelWindow.isChecked = prefs.getBoolean("pixel_window_enabled", false)

        btnBatteryInDialog.setOnClickListener {
            jumpToBatteryOptimizationSettings()
        }

        // 开关即时生效：切换即写入并应用，无需「保存」按钮
        switchKeepAlive.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean("keep_alive_enabled", checked).apply()
            applyKeepAlive(checked)
        }
        switchPixelWindow.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean("pixel_window_enabled", checked).apply()
            applyPixelWindow(checked)
        }

        val dialog = AlertDialog.Builder(this)
            .setView(view)
            .create()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.setOnDismissListener { dialogKeepAliveSwitch = null }
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
            Toast.makeText(this, getString(R.string.opt_battery_already), Toast.LENGTH_SHORT).show()
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
                Toast.makeText(this, getString(R.string.opt_permission_overlay_toast), Toast.LENGTH_SHORT).show()
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
