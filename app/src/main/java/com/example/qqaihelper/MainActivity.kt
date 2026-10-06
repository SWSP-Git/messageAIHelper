package com.example.qqaihelper

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Bundle
import android.text.InputType
import android.widget.*
import androidx.activity.OnBackPressedCallback

/**
 * 配置中心（原主页，现在作为「设置」页使用，从 HomeActivity 的齿轮进入）。
 */
class MainActivity : BaseActivity() {

    private lateinit var etApiUrl: EditText
    private lateinit var etApiKey: EditText
    private lateinit var etModel: EditText
    private lateinit var tvStatus: TextView
    private lateinit var prefs: android.content.SharedPreferences

    private var isPasswordVisible = false

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

        etApiUrl.setText(prefs.getString("api_url", ""))
        etApiKey.setText(prefs.getString("api_key", ""))
        etModel.setText(prefs.getString("model_name", ""))

        initialApiUrl = etApiUrl.text.toString()
        initialApiKey = etApiKey.text.toString()
        initialModel = etModel.text.toString()

        findViewById<ImageButton>(R.id.btnToggleApiKey).setOnClickListener {
            isPasswordVisible = !isPasswordVisible
            if (isPasswordVisible) {
                etApiKey.inputType = InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            } else {
                etApiKey.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            }
            etApiKey.setSelection(etApiKey.text.length)
        }

        findViewById<Button>(R.id.btnSave).setOnClickListener {
            saveConfig()
            tvStatus.text = getString(R.string.config_saved_toast)
        }

        val switchService = findViewById<Switch>(R.id.switchService)
        switchService.isChecked = prefs.getBoolean("service_enabled", false)
        switchService.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("service_enabled", isChecked).apply()
            tvStatus.text = getString(
                if (isChecked) R.string.config_service_on else R.string.config_service_off
            )
        }

        findViewById<Button>(R.id.btnSelectApps).setOnClickListener {
            showAppSelectionDialog()
        }

        findViewById<Button>(R.id.btnFilterRules).setOnClickListener {
            showFilterRulesDialog()
        }

        findViewById<Button>(R.id.btnMoreSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        checkAndRequestPermissions()

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

    private fun hasUnsavedChanges(): Boolean {
        return etApiUrl.text.toString() != initialApiUrl ||
                etApiKey.text.toString() != initialApiKey ||
                etModel.text.toString() != initialModel
    }

    private fun showUnsavedChangesDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.unsaved_title)
            .setMessage(R.string.unsaved_msg_config)
            .setPositiveButton(R.string.unsaved_save) { _, _ ->
                saveConfig()
                finish()
            }
            .setNegativeButton(R.string.unsaved_discard) { _, _ ->
                finish()
            }
            .setNeutralButton(R.string.unsaved_cancel, null)
            .show()
    }

    private fun saveConfig() {
        prefs.edit()
            .putString("api_url", etApiUrl.text.toString().trim())
            .putString("api_key", etApiKey.text.toString().trim())
            .putString("model_name", etModel.text.toString().trim())
            .apply()
        initialApiUrl = etApiUrl.text.toString()
        initialApiKey = etApiKey.text.toString()
        initialModel = etModel.text.toString()
    }

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
            AlertDialog.Builder(this)
                .setTitle(R.string.perm_notification_title)
                .setMessage(R.string.perm_notification_msg)
                .setPositiveButton(R.string.perm_notification_goto) { _, _ ->
                    startActivity(
                        Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                    )
                }
                .setNegativeButton(R.string.dialog_later, null)
                .show()
        }
    }

    override fun onResume() {
        super.onResume()
        checkNotificationPermission()
    }

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
            .setTitle(R.string.dialog_select_apps_title)
            .setMultiChoiceItems(appNames, checkedItems) { _, which, isChecked ->
                if (isChecked) savedPackages.add(appPackages[which])
                else savedPackages.remove(appPackages[which])
            }
            .setPositiveButton(R.string.dialog_save) { _, _ ->
                prefs.edit().putString("monitored_packages", savedPackages.joinToString(",")).apply()
                tvStatus.text = getString(R.string.dialog_select_apps_saved, savedPackages.size)
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .show()
    }

    private fun showFilterRulesDialog() {
        val savedString = prefs.getString("monitored_packages", "") ?: ""
        val monitoredPackages = savedString.split(",").filter { it.isNotEmpty() }

        if (monitoredPackages.isEmpty()) {
            tvStatus.text = getString(R.string.dialog_filter_rules_empty)
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
            .setTitle(R.string.dialog_filter_rules_title)
            .setItems(appNames) { _, which ->
                val (appName, packageName) = appList[which]
                showRuleEditorDialog(appName, packageName)
            }
            .setNegativeButton(R.string.dialog_cancel, null)
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
                hint = getString(R.string.dialog_filter_keyword_hint)
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
            text = getString(R.string.dialog_filter_add_keyword)
            setOnClickListener { addKeywordInput() }
        }
        container.addView(btnAdd)

        val switchLogic = Switch(this).apply {
            text = getString(
                if (isAndLogic) R.string.dialog_filter_logic_and else R.string.dialog_filter_logic_or
            )
            isChecked = isAndLogic
            setOnCheckedChangeListener { _, isChecked ->
                text = getString(
                    if (isChecked) R.string.dialog_filter_logic_and else R.string.dialog_filter_logic_or
                )
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 40, 0, 0) }
        }
        container.addView(switchLogic)

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.dialog_filter_editor_title, appName))
            .setView(scrollView)
            .setPositiveButton(R.string.dialog_save) { _, _ ->
                val validKeywords = keywordEditTexts
                    .map { it.text.toString().trim() }
                    .filter { it.isNotEmpty() }

                if (validKeywords.isEmpty()) {
                    tvStatus.text = getString(R.string.dialog_filter_save_fail)
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

                tvStatus.text = getString(R.string.dialog_filter_saved, appName)
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .show()
    }
}
