package com.example.qqaihelper

import android.app.AlertDialog
import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Bundle
import android.text.InputType
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity

/**
 * 配置中心（原主页，现在作为「设置」页使用，从 HomeActivity 的齿轮进入）。
 *
 * 职责：
 * 1. 配置 AI 接口（API URL / API Key / 模型名称）
 * 2. 全局服务开关（实时保存）
 * 3. 选择要监听的通知来源应用（白名单）
 * 4. 为每个应用单独配置关键词过滤规则（支持 AND / OR 逻辑）
 * 5. 首次进入时引导用户开启「日历权限」和「通知使用权」
 *
 * 未保存更改保护：
 * 用户修改了 API 配置但没点「保存设置」直接返回时，会弹窗询问是否保存。
 * 全局服务开关是实时保存的，不纳入未保存保护范围。
 */
class MainActivity : AppCompatActivity() {

    // ==================== 控件引用 ====================
    private lateinit var etApiUrl: EditText
    private lateinit var etApiKey: EditText
    private lateinit var etModel: EditText
    private lateinit var tvStatus: TextView
    private lateinit var prefs: android.content.SharedPreferences

    /** API Key 输入框当前是否明文显示（由小眼睛按钮切换） */
    private var isPasswordVisible = false

    // ==================== 未保存快照 ====================
    private lateinit var initialApiUrl: String
    private lateinit var initialApiKey: String
    private lateinit var initialModel: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        etApiUrl = findViewById(R.id.etApiUrl)
        etApiKey = findViewById(R.id.etApiKey)
        etModel = findViewById(R.id.etModel)
        tvStatus = findViewById(R.id.tvStatus)

        prefs = getSharedPreferences("app_settings", Context.MODE_PRIVATE)

        // ---------- 1. 加载已有配置 ----------
        etApiUrl.setText(prefs.getString("api_url", ""))
        etApiKey.setText(prefs.getString("api_key", ""))
        etModel.setText(prefs.getString("model_name", ""))

        // 记录初始快照（用于返回时判断是否有未保存更改）
        initialApiUrl = etApiUrl.text.toString()
        initialApiKey = etApiKey.text.toString()
        initialModel = etModel.text.toString()

        // ---------- 2. API Key 小眼睛 ----------
        findViewById<ImageButton>(R.id.btnToggleApiKey).setOnClickListener {
            isPasswordVisible = !isPasswordVisible
            if (isPasswordVisible) {
                etApiKey.inputType = InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            } else {
                etApiKey.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            }
            etApiKey.setSelection(etApiKey.text.length)
        }

        // ---------- 3. 保存 API 配置 ----------
        findViewById<Button>(R.id.btnSave).setOnClickListener {
            saveConfig()
            tvStatus.text = "✅ 配置已保存！后台服务将立即使用新配置。"
        }

        // ---------- 4. 全局服务开关（实时保存，不纳入未保存保护） ----------
        val switchService = findViewById<Switch>(R.id.switchService)
        switchService.isChecked = prefs.getBoolean("service_enabled", false)
        switchService.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("service_enabled", isChecked).apply()
            tvStatus.text = if (isChecked) "✅ 服务已开启" else "⏸️ 服务已暂停"
        }

        // ---------- 5. 选择监听应用 ----------
        findViewById<Button>(R.id.btnSelectApps).setOnClickListener {
            showAppSelectionDialog()
        }

        // ---------- 6. 配置过滤规则 ----------
        findViewById<Button>(R.id.btnFilterRules).setOnClickListener {
            showFilterRulesDialog()
        }

        // ---------- 7. 进入「更多设置」 ----------
        findViewById<Button>(R.id.btnMoreSettings).setOnClickListener {
            startActivity(android.content.Intent(this, SettingsActivity::class.java))
        }

        // ---------- 8. 检查权限 ----------
        checkAndRequestPermissions()

        // ---------- 9. 注册返回键拦截：有未保存改动时先询问 ----------
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

    /** 判断三个输入框是否有未保存的修改 */
    private fun hasUnsavedChanges(): Boolean {
        return etApiUrl.text.toString() != initialApiUrl ||
                etApiKey.text.toString() != initialApiKey ||
                etModel.text.toString() != initialModel
    }

    /** 弹出「是否保存」对话框 */
    private fun showUnsavedChangesDialog() {
        AlertDialog.Builder(this)
            .setTitle("未保存的更改")
            .setMessage("当前 API 配置已修改，是否保存后退出？")
            .setPositiveButton("保存") { _, _ ->
                saveConfig()
                finish()
            }
            .setNegativeButton("不保存") { _, _ ->
                finish()
            }
            .setNeutralButton("取消", null)
            .show()
    }

    /** 统一保存 API 配置并刷新快照 */
    private fun saveConfig() {
        prefs.edit()
            .putString("api_url", etApiUrl.text.toString().trim())
            .putString("api_key", etApiKey.text.toString().trim())
            .putString("model_name", etModel.text.toString().trim())
            .apply()
        // 保存后刷新快照，避免返回时再次提示
        initialApiUrl = etApiUrl.text.toString()
        initialApiKey = etApiKey.text.toString()
        initialModel = etModel.text.toString()
    }

    // ==================== 权限引导 ====================

    private fun checkAndRequestPermissions() {
        if (androidx.core.content.ContextCompat.checkSelfPermission(
                this, android.Manifest.permission.WRITE_CALENDAR
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            androidx.core.app.ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    android.Manifest.permission.WRITE_CALENDAR,
                    android.Manifest.permission.READ_CALENDAR
                ),
                100
            )
        } else {
            checkNotificationPermission()
        }
    }

    private fun checkNotificationPermission() {
        val pkgName = packageName
        val flat = android.provider.Settings.Secure.getString(
            contentResolver, "enabled_notification_listeners"
        )
        val isEnabled = flat != null && flat.contains(pkgName)

        if (!isEnabled) {
            android.app.AlertDialog.Builder(this)
                .setTitle("需要开启通知使用权")
                .setMessage("为了自动读取微信、QQ等应用的消息，请在接下来的页面中找到「messageAIHelper」，并打开开关。")
                .setPositiveButton("去开启") { _, _ ->
                    startActivity(
                        android.content.Intent(
                            android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS
                        )
                    )
                }
                .setNegativeButton("稍后", null)
                .show()
        }
    }

    override fun onResume() {
        super.onResume()
        checkNotificationPermission()
    }

    // ==================== 应用白名单 ====================

    private fun showAppSelectionDialog() {
        val pm = packageManager
        val installedApps = pm.getInstalledApplications(0)
            .filter { (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 && it.packageName != packageName }
            .sortedBy { it.loadLabel(pm).toString() }

        val savedString = prefs.getString("monitored_packages", "") ?: ""
        val savedPackages = savedString.split(",").filter { it.isNotEmpty() }.toMutableSet()

        val appNames = installedApps.map { it.loadLabel(pm).toString() }.toTypedArray()
        val appPackages = installedApps.map { it.packageName }.toTypedArray()
        val checkedItems = installedApps.map { savedPackages.contains(it.packageName) }.toBooleanArray()

        AlertDialog.Builder(this)
            .setTitle("选择要监听的应用")
            .setMultiChoiceItems(appNames, checkedItems) { _, which, isChecked ->
                if (isChecked) savedPackages.add(appPackages[which])
                else savedPackages.remove(appPackages[which])
            }
            .setPositiveButton("保存") { _, _ ->
                prefs.edit().putString("monitored_packages", savedPackages.joinToString(",")).apply()
                tvStatus.text = "✅ 监听列表已更新，共选中 ${savedPackages.size} 个应用"
            }
            .setNegativeButton("取消", null)
            .show()
    }

    // ==================== 关键词过滤规则 ====================

    private fun showFilterRulesDialog() {
        val savedString = prefs.getString("monitored_packages", "") ?: ""
        val monitoredPackages = savedString.split(",").filter { it.isNotEmpty() }

        if (monitoredPackages.isEmpty()) {
            tvStatus.text = "⚠️ 请先选择要监听的应用！"
            return
        }

        val pm = packageManager
        val appList = monitoredPackages.mapNotNull { pkg ->
            try {
                val appInfo = pm.getApplicationInfo(pkg, 0)
                val appName = pm.getApplicationLabel(appInfo).toString()
                appName to pkg
            } catch (e: Exception) {
                null
            }
        }.sortedBy { it.first }

        val appNames = appList.map { it.first }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("选择要配置规则的应用")
            .setItems(appNames) { _, which ->
                val (appName, packageName) = appList[which]
                showRuleEditorDialog(appName, packageName)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showRuleEditorDialog(appName: String, packageName: String) {
        val rulesString = prefs.getString("filter_rules", "") ?: ""
        var currentKeywords = listOf<String>()
        var isAndLogic = false

        rulesString.split("\n").forEach { line ->
            val parts = line.split("|")
            if (parts.size >= 3 && parts[0] == packageName) {
                currentKeywords = parts[1].split(",").filter { it.isNotBlank() }
                isAndLogic = parts[2].trim().uppercase() == "AND"
            }
        }

        val scrollView = ScrollView(this)
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 20, 40, 20)
        }
        scrollView.addView(container)

        val keywordEditTexts = mutableListOf<EditText>()

        fun addKeywordInput(keyword: String = "") {
            val et = EditText(this).apply {
                hint = "请输入关键词"
                setText(keyword)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, 10, 0, 10) }
            }
            container.addView(et)
            keywordEditTexts.add(et)
        }

        if (currentKeywords.isEmpty()) {
            addKeywordInput()
        } else {
            currentKeywords.forEach { addKeywordInput(it) }
        }

        val btnAdd = Button(this).apply {
            text = "➕ 添加关键词"
            setOnClickListener { addKeywordInput() }
        }
        container.addView(btnAdd)

        val switchLogic = Switch(this).apply {
            text = if (isAndLogic) "逻辑：必须包含所有关键词 (AND)" else "逻辑：包含任一关键词即可 (OR)"
            isChecked = isAndLogic
            setOnCheckedChangeListener { _, isChecked ->
                text = if (isChecked) "逻辑：必须包含所有关键词 (AND)" else "逻辑：包含任一关键词即可 (OR)"
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 40, 0, 0) }
        }
        container.addView(switchLogic)

        AlertDialog.Builder(this)
            .setTitle("为 [$appName] 配置规则")
            .setView(scrollView)
            .setPositiveButton("保存") { _, _ ->
                val validKeywords = keywordEditTexts
                    .map { it.text.toString().trim() }
                    .filter { it.isNotEmpty() }

                if (validKeywords.isEmpty()) {
                    tvStatus.text = "⚠️ 保存失败，至少需要输入一个关键词"
                    return@setPositiveButton
                }

                val logicStr = if (switchLogic.isChecked) "AND" else "OR"
                val newRule = "$packageName|${validKeywords.joinToString(",")}|$logicStr"

                val lines = rulesString.split("\n").toMutableList()
                var found = false
                for (i in lines.indices) {
                    if (lines[i].startsWith("$packageName|")) {
                        lines[i] = newRule
                        found = true
                        break
                    }
                }
                if (!found) lines.add(newRule)

                val finalRules = lines.filter { it.isNotBlank() }.joinToString("\n")
                prefs.edit().putString("filter_rules", finalRules).apply()

                tvStatus.text = "✅ [$appName] 的过滤规则已保存"
            }
            .setNegativeButton("取消", null)
            .show()
    }
}
