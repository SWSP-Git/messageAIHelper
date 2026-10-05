package com.example.qqaihelper

import android.app.AlertDialog
import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Bundle
import android.text.InputType
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var etApiUrl: EditText
    private lateinit var etApiKey: EditText
    private lateinit var etModel: EditText
    private lateinit var tvStatus: TextView
    private lateinit var prefs: android.content.SharedPreferences

    private var isPasswordVisible = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppLogger.init(applicationContext)
        setContentView(R.layout.activity_main)

        etApiUrl = findViewById(R.id.etApiUrl)
        etApiKey = findViewById(R.id.etApiKey)
        etModel = findViewById(R.id.etModel)
        tvStatus = findViewById(R.id.tvStatus)

        prefs = getSharedPreferences("app_settings", Context.MODE_PRIVATE)

        // 1. 读取配置
        etApiUrl.setText(prefs.getString("api_url", ""))
        etApiKey.setText(prefs.getString("api_key", ""))
        etModel.setText(prefs.getString("model_name", ""))

        // 2. 密码小眼睛逻辑
        findViewById<ImageButton>(R.id.btnToggleApiKey).setOnClickListener {
            isPasswordVisible = !isPasswordVisible
            if (isPasswordVisible) {
                etApiKey.inputType = InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            } else {
                etApiKey.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            }
            etApiKey.setSelection(etApiKey.text.length)
        }

        // 3. 保存配置逻辑
        findViewById<Button>(R.id.btnSave).setOnClickListener {
            val editor = prefs.edit()
            editor.putString("api_url", etApiUrl.text.toString().trim())
            editor.putString("api_key", etApiKey.text.toString().trim())
            editor.putString("model_name", etModel.text.toString().trim())
            editor.apply()
            tvStatus.text = "✅ 配置已保存！后台服务将立即使用新配置。"
        }

        // 4. 服务开关逻辑
        val switchService = findViewById<Switch>(R.id.switchService)
        switchService.isChecked = prefs.getBoolean("service_enabled", false)
        switchService.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("service_enabled", isChecked).apply()
            tvStatus.text = if (isChecked) "✅ 服务已开启" else "⏸️ 服务已暂停"
        }

        // 5. 选择监听应用逻辑
        findViewById<Button>(R.id.btnSelectApps).setOnClickListener {
            showAppSelectionDialog()
        }

        // 6. 配置过滤规则按钮
        findViewById<Button>(R.id.btnFilterRules).setOnClickListener {
            showFilterRulesDialog()
        }
        // 7. 更多设置入口
        findViewById<Button>(R.id.btnMoreSettings).setOnClickListener {
            val intent = android.content.Intent(this, SettingsActivity::class.java)
            startActivity(intent)
        }
        // 在 onCreate 的最后，调用权限检查函数
        checkAndRequestPermissions()
    } // 👈 注意！onCreate 方法在这里结束了！
    // 检查所需权限，并在缺少时依次引导用户开启
    private fun checkAndRequestPermissions() {
        // 1. 检查日历权限（标准权限，可以直接弹窗请求）
        if (androidx.core.content.ContextCompat.checkSelfPermission(
                this, android.Manifest.permission.WRITE_CALENDAR
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            // 弹出系统权限请求对话框
            androidx.core.app.ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    android.Manifest.permission.WRITE_CALENDAR,
                    android.Manifest.permission.READ_CALENDAR
                ),
                100
            )
        } else {
            // 如果日历权限已经开了，接着检查通知使用权
            checkNotificationPermission()
        }
    }

    // 检查通知使用权（特殊权限，必须引导用户去系统设置手动开）
    private fun checkNotificationPermission() {
        val pkgName = packageName
        val flat = android.provider.Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        val isEnabled = flat != null && flat.contains(pkgName)

        if (!isEnabled) {
            android.app.AlertDialog.Builder(this)
                .setTitle("需要开启通知使用权")
                .setMessage("为了自动读取微信、QQ等应用的消息，请在接下来的页面中找到「QQAIHelper」，并打开开关。")
                .setPositiveButton("去开启") { _, _ ->
                    // 跳转到系统的通知使用权设置页面
                    startActivity(android.content.Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                }
                .setNegativeButton("稍后", null)
                .show()
        }
    }

    // 当用户从设置页面返回 App 时，会触发 onResume，重新检查一遍权限
    override fun onResume() {
        super.onResume()
        // 只在权限缺失时提醒，如果都开好了就静默刷新状态
        checkNotificationPermission()
    }


    // -----------------------------------------------------------------
    // 下面这些函数，全部写在 onCreate 的大括号外面，和 onCreate 是平级的关系！
    // -----------------------------------------------------------------

    // 弹窗展示应用列表，让用户勾选
    private fun showAppSelectionDialog() {
        val pm = packageManager
        val installedApps = pm.getInstalledApplications(0)
            .filter { (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 && it.packageName != packageName }
            .sortedBy { it.loadLabel(pm).toString() }

        val savedString = prefs.getString("monitored_packages", "") ?: ""
// 如果为空，split 之后会有一个空字符串，需要过滤掉
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
                val editor = prefs.edit()
                editor.putString("monitored_packages", savedPackages.joinToString(","))
                editor.apply()
                tvStatus.text = "✅ 监听列表已更新，共选中 ${savedPackages.size} 个应用"
            }
            .setNegativeButton("取消", null)
            .show()
    }

    // 第一步：显示已监听的应用列表供选择
    private fun showFilterRulesDialog() {
        // 读取已经监听的应用包名
        val savedString = prefs.getString("monitored_packages", "com.tencent.mobileqq,com.tencent.mm") ?: ""
        val monitoredPackages = savedString.split(",").filter { it.isNotEmpty() }

        if (monitoredPackages.isEmpty()) {
            tvStatus.text = "⚠️ 请先选择要监听的应用！"
            return
        }

        // 获取包名对应的应用名称，并排序
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
                // 用户选中了某个应用，进入第二步
                val (appName, packageName) = appList[which]
                showRuleEditorDialog(appName, packageName)
            }
            .setNegativeButton("取消", null)
            .show()
    }
    // 第二步：动态构建规则编辑弹窗
    private fun showRuleEditorDialog(appName: String, packageName: String) {
        // 1. 解析已有的规则（寻找该包名当前的规则）
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

        // 2. 构建可滚动的布局容器
        val scrollView = ScrollView(this)
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 20, 40, 20)
        }
        scrollView.addView(container)

        // 用于记录用户生成的所有输入框
        val keywordEditTexts = mutableListOf<EditText>()

        // 内部函数：向容器添加一个输入框
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

        // 初始化输入框：如果有历史关键词就展示，否则先给一个空的
        if (currentKeywords.isEmpty()) {
            addKeywordInput()
        } else {
            currentKeywords.forEach { addKeywordInput(it) }
        }

        // 3. 添加“新增关键词”按钮
        val btnAdd = Button(this).apply {
            text = "➕ 添加关键词"
            setOnClickListener { addKeywordInput() } // 动态追加输入框
        }
        container.addView(btnAdd)

        // 4. 添加“与/或逻辑”开关
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

        // 5. 组装并显示弹窗
        AlertDialog.Builder(this)
            .setTitle("为 [$appName] 配置规则")
            .setView(scrollView)
            .setPositiveButton("保存") { _, _ ->
                // 收集所有非空的输入框内容，用逗号拼接
                val validKeywords = keywordEditTexts
                    .map { it.text.toString().trim() }
                    .filter { it.isNotEmpty() }

                if (validKeywords.isEmpty()) {
                    tvStatus.text = "⚠️ 保存失败，至少需要输入一个关键词"
                    return@setPositiveButton
                }

                // 构建当前规则的字符串：包名|关键词1,关键词2|AND
                val logicStr = if (switchLogic.isChecked) "AND" else "OR"
                val newRule = "$packageName|${validKeywords.joinToString(",")}|$logicStr"

                // 更新到全局的 filter_rules 中
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
} // 👈 这是整个 MainActivity 类的结束大括号