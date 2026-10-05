package com.example.qqaihelper

import android.app.AlertDialog
import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Bundle
import android.text.InputType
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

/**
 * 配置中心（原主页，现在作为「设置」页使用，从 HomeActivity 的齿轮进入）。
 *
 * 职责：
 * 1. 配置 AI 接口（API URL / API Key / 模型名称）
 * 2. 全局服务开关
 * 3. 选择要监听的通知来源应用（白名单）
 * 4. 为每个应用单独配置关键词过滤规则（支持 AND / OR 逻辑）
 * 5. 首次进入时引导用户开启「日历权限」和「通知使用权」
 *
 * 存储方案：全部通过 SharedPreferences（"app_settings"）持久化。
 * 其它模块（如 QQNotificationListener）通过同一份 prefs 读取配置。
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 绑定控件
        etApiUrl = findViewById(R.id.etApiUrl)
        etApiKey = findViewById(R.id.etApiKey)
        etModel = findViewById(R.id.etModel)
        tvStatus = findViewById(R.id.tvStatus)

        // 打开统一的 SharedPreferences 文件
        prefs = getSharedPreferences("app_settings", Context.MODE_PRIVATE)

        // ---------- 1. 加载已有配置 ----------
        // 首次安装时全部为空，让用户看到 XML 里的 hint 提示词
        etApiUrl.setText(prefs.getString("api_url", ""))
        etApiKey.setText(prefs.getString("api_key", ""))
        etModel.setText(prefs.getString("model_name", ""))

        // ---------- 2. API Key 小眼睛：切换明文 / 密文 ----------
        findViewById<ImageButton>(R.id.btnToggleApiKey).setOnClickListener {
            isPasswordVisible = !isPasswordVisible
            if (isPasswordVisible) {
                // 明文显示
                etApiKey.inputType = InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            } else {
                // 密文显示
                etApiKey.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            }
            // 切换类型后光标会跳到开头，这里手动把光标移到末尾，方便继续输入
            etApiKey.setSelection(etApiKey.text.length)
        }

        // ---------- 3. 保存 API 配置 ----------
        findViewById<Button>(R.id.btnSave).setOnClickListener {
            val editor = prefs.edit()
            editor.putString("api_url", etApiUrl.text.toString().trim())
            editor.putString("api_key", etApiKey.text.toString().trim())
            editor.putString("model_name", etModel.text.toString().trim())
            editor.apply()
            tvStatus.text = "✅ 配置已保存！后台服务将立即使用新配置。"
        }

        // ---------- 4. 全局服务开关 ----------
        // 关闭后 QQNotificationListener 会直接忽略所有通知，不消耗 AI 额度
        val switchService = findViewById<Switch>(R.id.switchService)
        switchService.isChecked = prefs.getBoolean("service_enabled", false)
        switchService.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("service_enabled", isChecked).apply()
            tvStatus.text = if (isChecked) "✅ 服务已开启" else "⏸️ 服务已暂停"
        }

        // ---------- 5. 选择监听应用（弹窗多选） ----------
        findViewById<Button>(R.id.btnSelectApps).setOnClickListener {
            showAppSelectionDialog()
        }

        // ---------- 6. 配置关键词过滤规则 ----------
        findViewById<Button>(R.id.btnFilterRules).setOnClickListener {
            showFilterRulesDialog()
        }

        // ---------- 7. 进入「更多设置」页面 ----------
        // 包含：定时开关 / Webhook 端口 / 电池优化白名单 / 查看日志 等
        findViewById<Button>(R.id.btnMoreSettings).setOnClickListener {
            val intent = android.content.Intent(this, SettingsActivity::class.java)
            startActivity(intent)
        }

        // ---------- 8. 首次进入时检查权限 ----------
        checkAndRequestPermissions()
    }

    // ==================== 权限引导 ====================

    /**
     * 按顺序检查必要权限：
     * 1. 日历读写权限（标准权限，可直接弹窗请求）
     * 2. 通知使用权（系统特殊权限，必须跳转到系统设置页让用户手动开启）
     */
    private fun checkAndRequestPermissions() {
        if (androidx.core.content.ContextCompat.checkSelfPermission(
                this, android.Manifest.permission.WRITE_CALENDAR
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            // 弹出系统权限请求对话框（Android 原生）
            androidx.core.app.ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    android.Manifest.permission.WRITE_CALENDAR,
                    android.Manifest.permission.READ_CALENDAR
                ),
                100
            )
        } else {
            // 日历权限已就绪，继续检查通知使用权
            checkNotificationPermission()
        }
    }

    /**
     * 检查「通知使用权」是否已授予。
     *
     * 判断方法：读取系统设置里的 enabled_notification_listeners 字符串，
     * 里面包含我们应用包名即表示已授权。
     *
     * 若未授权，弹窗引导用户跳转到系统设置手动开启。
     */
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

    /**
     * 用户从系统设置返回时重新检查一次通知使用权。
     * 如果权限已经开好，则静默通过，不再打扰用户。
     */
    override fun onResume() {
        super.onResume()
        checkNotificationPermission()
    }

    // ==================== 应用白名单 ====================

    /**
     * 弹出应用多选列表，让用户勾选需要监听的通知来源应用。
     *
     * 过滤规则：
     * - 排除系统应用（FLAG_SYSTEM），避免用户误选导致无意义消耗 AI
     * - 排除自己（packageName），防止递归处理
     *
     * 保存格式："com.tencent.mobileqq,com.tencent.mm,..."（逗号分隔的包名列表）
     */
    private fun showAppSelectionDialog() {
        val pm = packageManager
        val installedApps = pm.getInstalledApplications(0)
            .filter { (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 && it.packageName != packageName }
            .sortedBy { it.loadLabel(pm).toString() }

        // 读取历史选择（首次为空，即全不勾选）
        val savedString = prefs.getString("monitored_packages", "") ?: ""
        // 空字符串 split 后会得到 [""]，需要过滤掉
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

    // ==================== 关键词过滤规则（两步向导） ====================

    /**
     * 步骤 1：列出当前被监听的 App，让用户选择要为哪一个配置过滤规则。
     */
    private fun showFilterRulesDialog() {
        // 只处理已被监听的应用，避免为无用应用配置规则
        val savedString = prefs.getString("monitored_packages", "") ?: ""
        val monitoredPackages = savedString.split(",").filter { it.isNotEmpty() }

        if (monitoredPackages.isEmpty()) {
            tvStatus.text = "⚠️ 请先选择要监听的应用！"
            return
        }

        // 包名 → 应用名（若该应用已被卸载则跳过）
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

    /**
     * 步骤 2：为选定的应用动态构建规则编辑器。
     *
     * UI 结构：
     * - 一组「关键词输入框」（可动态增删，目前是逐个追加）
     * - 一个「➕ 添加关键词」按钮
     * - 一个「AND / OR」逻辑开关
     *
     * 数据存储格式（每行一条规则）：
     *   包名|关键词1,关键词2,关键词3|AND
     *   包名2|关键词A,关键词B|OR
     */
    private fun showRuleEditorDialog(appName: String, packageName: String) {
        // 解析历史规则
        val rulesString = prefs.getString("filter_rules", "") ?: ""
        var currentKeywords = listOf<String>()
        var isAndLogic = false // 默认 OR 逻辑（符合大多数使用场景）

        rulesString.split("\n").forEach { line ->
            val parts = line.split("|")
            if (parts.size >= 3 && parts[0] == packageName) {
                currentKeywords = parts[1].split(",").filter { it.isNotBlank() }
                isAndLogic = parts[2].trim().uppercase() == "AND"
            }
        }

        // 构建可滚动的容器（输入框多时可以滚动）
        val scrollView = ScrollView(this)
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 20, 40, 20)
        }
        scrollView.addView(container)

        // 用列表保存所有动态创建的输入框，方便保存时统一读取
        val keywordEditTexts = mutableListOf<EditText>()

        // 局部函数：向容器追加一个新的关键词输入框
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

        // 首次打开时至少给一个空输入框
        if (currentKeywords.isEmpty()) {
            addKeywordInput()
        } else {
            currentKeywords.forEach { addKeywordInput(it) }
        }

        // 动态追加按钮
        val btnAdd = Button(this).apply {
            text = "➕ 添加关键词"
            setOnClickListener { addKeywordInput() }
        }
        container.addView(btnAdd)

        // AND / OR 逻辑开关
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

        // 组装并显示弹窗
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

                // 单条规则："包名|关键词1,关键词2|AND"
                val logicStr = if (switchLogic.isChecked) "AND" else "OR"
                val newRule = "$packageName|${validKeywords.joinToString(",")}|$logicStr"

                // 更新到全局 filter_rules：已存在则替换，否则追加
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
