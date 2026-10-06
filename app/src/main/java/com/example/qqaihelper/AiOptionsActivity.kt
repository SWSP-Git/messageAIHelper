package com.example.qqaihelper

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import com.example.qqaihelper.localllm.LocalLlmEngine
import com.example.qqaihelper.localllm.LocalLlmManager
import com.example.qqaihelper.localllm.PluginManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * AI 与日程设置页。
 *
 * 从「备忘录」页面右上角齿轮进入。
 *
 * 功能：
 * 1. 模型运行位置二选一：云端模型 / 本地模型（离线）
 *    - 云端：沿用配置中心里的 OpenAI 兼容接口
 *    - 本地：加载 .zip 插件，在 127.0.0.1 暴露 OpenAI 兼容接口
 * 2. 本地模型插件管理：导入 zip、删除、切换当前插件、加载/重载
 * 3. 日程结合模式三选一（交给 AI 分析仅云端可用）
 * 4. 未来日程查询天数（1-30 天）
 *
 * 未保存更改保护：修改任意选项但没点「保存设置」直接返回时会弹窗询问。
 */
class AiOptionsActivity : BaseActivity() {

    companion object {
        const val KEY_LOCAL_MODEL_ENABLED = "local_model_enabled"
        const val KEY_LOCAL_MODEL_PORT = "local_model_port"
        const val KEY_SCHEDULE_MODE = "schedule_mode"
        const val KEY_LOOKAHEAD_DAYS = "lookahead_days"

        const val MODE_NONE = "none"
        const val MODE_CODE_CHECK = "code_check"
        const val MODE_AI_ANALYSIS = "ai_analysis"

        const val MIN_DAYS = 1
        const val MAX_DAYS = 30
        const val DEFAULT_DAYS = 7
        const val DEFAULT_PORT = LocalLlmManager.DEFAULT_PORT
    }

    private lateinit var prefs: android.content.SharedPreferences

    // ==================== 未保存快照 ====================
    private var initialLocalModelEnabled = false
    private var initialLocalPort = DEFAULT_PORT.toString()
    private var initialScheduleMode = MODE_NONE
    private var initialLookaheadDays = DEFAULT_DAYS

    // 控件引用
    private lateinit var radioModelCloud: RadioButton
    private lateinit var radioModelLocal: RadioButton
    private lateinit var localModelPanel: View
    private lateinit var tvLocalModelStatus: TextView
    private lateinit var etLocalModelPort: EditText
    private lateinit var pbLocalModel: ProgressBar
    private lateinit var btnInstallLocalModel: Button
    private lateinit var btnImportPlugin: Button
    private lateinit var btnAutoScan: Button
    private lateinit var llScanResult: LinearLayout
    private lateinit var llPluginList: LinearLayout
    private lateinit var tvPluginTotal: TextView
    private lateinit var tvNoPlugin: TextView
    private lateinit var radioNone: RadioButton
    private lateinit var radioCodeCheck: RadioButton
    private lateinit var radioAiAnalysis: RadioButton
    private lateinit var etLookaheadDays: EditText

    private var installing = false

    /** 自动搜寻的取消标志（点击「取消搜寻」时置为 true） */
    @Volatile
    private var scanCancelled = false

    /** 是否正在扫描（用于把按钮切换为「取消搜寻」） */
    private var scanning = false

    // ==================== 文件选择器（导入插件 zip） ====================
    private val importZipLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@registerForActivityResult
        doImportPlugin(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ai_options)

        prefs = getSharedPreferences("app_settings", Context.MODE_PRIVATE)

        // ---------- 绑定控件 ----------
        val radioGroupSource = findViewById<RadioGroup>(R.id.radioGroupModelSource)
        radioModelCloud = findViewById(R.id.radioModelCloud)
        radioModelLocal = findViewById(R.id.radioModelLocal)
        localModelPanel = findViewById(R.id.localModelPanel)
        tvLocalModelStatus = findViewById(R.id.tvLocalModelStatus)
        etLocalModelPort = findViewById(R.id.etLocalModelPort)
        pbLocalModel = findViewById(R.id.pbLocalModel)
        btnInstallLocalModel = findViewById(R.id.btnInstallLocalModel)
        btnImportPlugin = findViewById(R.id.btnImportPlugin)
        btnAutoScan = findViewById(R.id.btnAutoScan)
        llScanResult = findViewById(R.id.llScanResult)
        llPluginList = findViewById(R.id.llPluginList)
        tvPluginTotal = findViewById(R.id.tvPluginTotal)
        tvNoPlugin = findViewById(R.id.tvNoPlugin)

        val radioGroup = findViewById<RadioGroup>(R.id.radioGroupScheduleMode)
        radioNone = findViewById(R.id.radioModeNone)
        radioCodeCheck = findViewById(R.id.radioModeCodeCheck)
        radioAiAnalysis = findViewById(R.id.radioModeAiAnalysis)
        etLookaheadDays = findViewById(R.id.etLookaheadDays)
        val btnSave = findViewById<Button>(R.id.btnSaveAiOptions)

        // ---------- 加载已有配置 ----------
        val localEnabled = prefs.getBoolean(KEY_LOCAL_MODEL_ENABLED, false)
        radioModelLocal.isChecked = localEnabled
        radioModelCloud.isChecked = !localEnabled
        etLocalModelPort.setText(prefs.getString(KEY_LOCAL_MODEL_PORT, DEFAULT_PORT.toString()))

        val savedMode = prefs.getString(KEY_SCHEDULE_MODE, MODE_NONE) ?: MODE_NONE
        when (savedMode) {
            MODE_CODE_CHECK -> radioCodeCheck.isChecked = true
            MODE_AI_ANALYSIS -> radioAiAnalysis.isChecked = true
            else -> radioNone.isChecked = true
        }
        etLookaheadDays.setText(prefs.getInt(KEY_LOOKAHEAD_DAYS, DEFAULT_DAYS).toString())

        snapshotCurrentState()

        // ---------- 模型来源联动 ----------
        radioGroupSource.setOnCheckedChangeListener { _, _ ->
            updateLocalPanel()
            updateAiAnalysisAvailability()
        }
        updateLocalPanel()
        updateAiAnalysisAvailability()

        // ---------- 导入插件 / 加载模型 ----------
        btnImportPlugin.setOnClickListener {
            importZipLauncher.launch(arrayOf("application/zip", "application/octet-stream", "*/*"))
        }
        btnInstallLocalModel.setOnClickListener { loadOrReloadLocalModel() }
        btnAutoScan.setOnClickListener {
            if (installing && !btnInstallLocalModel.isEnabled && scanning) {
                // 扫描进行中：此按钮变为「取消搜寻」
                scanCancelled = true
            } else {
                doAutoScan()
            }
        }

        // ---------- 保存 ----------
        btnSave.setOnClickListener {
            if (saveSettings()) {
                Toast.makeText(this, getString(R.string.ai_saved_toast), Toast.LENGTH_SHORT).show()
                finish()
            }
        }

        // ---------- 返回键拦截 ----------
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

    override fun onResume() {
        super.onResume()
        refreshLocalStatus()
        rebuildPluginList()
    }

    // ==================== 模型来源 / 本地状态 ====================

    private fun localSelected(): Boolean = radioModelLocal.isChecked

    private fun updateLocalPanel() {
        val show = localSelected()
        localModelPanel.visibility = if (show) View.VISIBLE else View.GONE
        if (show) {
            refreshLocalStatus()
            rebuildPluginList()
        }
    }

    private fun refreshLocalStatus() {
        if (!localSelected()) return
        val ctx = applicationContext
        val current = LocalLlmManager.currentPlugin(ctx)
        val loaded = LocalLlmEngine.get().isLoaded
        val text = when {
            loaded && current != null -> getString(R.string.ai_status_loaded, current.name)
            current != null -> getString(R.string.ai_status_selected, current.name)
            LocalLlmManager.hasAnyPlugin(ctx) -> getString(R.string.ai_status_installed)
            else -> getString(R.string.ai_status_none)
        }
        tvLocalModelStatus.text = text
    }

    // ==================== 插件列表 ====================

    private fun rebuildPluginList() {
        llPluginList.removeAllViews()
        val ctx = applicationContext
        val plugins = LocalLlmManager.listPlugins(ctx)
        val currentId = LocalLlmManager.currentPlugin(ctx)?.id
        tvNoPlugin.visibility = if (plugins.isEmpty()) View.VISIBLE else View.GONE

        // 总占用汇总
        if (plugins.isEmpty()) {
            tvPluginTotal.visibility = View.GONE
        } else {
            val totalBytes = plugins.sumOf { it.sizeBytes }
            tvPluginTotal.text = getString(
                R.string.ai_plugin_total, plugins.size, PluginManager.humanSize(totalBytes)
            )
            tvPluginTotal.visibility = View.VISIBLE
        }

        for (p in plugins) {
            val isCurrent = p.id == currentId
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, 14, 0, 14)
            }
            val title = TextView(this).apply {
                text = (if (isCurrent) "✅ " else "○ ") + p.name
                textSize = 14f
                setTextColor(if (isCurrent) 0xFF1976D2.toInt() else 0xFF212121.toInt())
                setTypeface(null, android.graphics.Typeface.BOLD)
            }
            val sub = TextView(this).apply {
                text = "id=" + p.id + " · v" + p.version + " · " + p.abi + " · " + PluginManager.humanSize(p.sizeBytes)
                textSize = 11f
                setTextColor(0xFF757575.toInt())
            }
            val actions = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 6, 0, 0)
            }
            val btnUse = Button(this).apply {
                text = if (isCurrent) getString(R.string.ai_plugin_used) else getString(R.string.ai_plugin_use)
                textSize = 12f
                isAllCaps = false
                isEnabled = !isCurrent
                setOnClickListener {
                    // 切换当前插件并立即加载（省去再点「加载 / 重载」）
                    switchAndLoadPlugin(p)
                }
            }
            val btnDel = Button(this).apply {
                text = getString(R.string.ai_plugin_delete)
                textSize = 12f
                isAllCaps = false
                setOnClickListener { confirmDeletePlugin(p) }
            }
            actions.addView(btnUse)
            actions.addView(btnDel)
            row.addView(title)
            row.addView(sub)
            row.addView(actions)
            llPluginList.addView(row)
        }
    }

    private fun confirmDeletePlugin(p: PluginManager.PluginInfo) {
        AlertDialog.Builder(this)
            .setTitle(R.string.ai_plugin_delete_title)
            .setMessage(getString(R.string.ai_plugin_delete_msg, p.name, PluginManager.humanSize(p.sizeBytes)))
            .setPositiveButton(R.string.ai_plugin_delete) { _, _ ->
                PluginManager.delete(applicationContext, p.id)
                LocalLlmManager.unload()
                refreshLocalStatus()
                rebuildPluginList()
            }
            .setNegativeButton(R.string.ai_plugin_cancel, null)
            .show()
    }

    // ==================== 导入插件 ====================

    private fun doImportPlugin(uri: Uri) {
        if (installing) return
        installing = true
        btnImportPlugin.isEnabled = false
        btnInstallLocalModel.isEnabled = false
        pbLocalModel.visibility = View.VISIBLE
        pbLocalModel.isIndeterminate = false
        pbLocalModel.progress = 0

        val ctx = applicationContext
        val zipSize = queryFileSize(uri)

        CoroutineScope(Dispatchers.IO).launch {
            withContext(Dispatchers.Main) {
                tvLocalModelStatus.text = getString(R.string.ai_plugin_importing)
            }
            val plugin = try {
                ctx.contentResolver.openInputStream(uri)?.use { input ->
                    PluginManager.installFromZip(ctx, input, zipSize) { name, done, total ->
                        runOnUiThread {
                            tvLocalModelStatus.text = getString(R.string.ai_plugin_extracting, name)
                            if (total > 0) {
                                val pct = (done * 100 / total).toInt().coerceIn(0, 100)
                                pbLocalModel.progress = pct
                            }
                        }
                    }
                }
            } catch (t: Throwable) {
                null
            }

            if (plugin != null) {
                PluginManager.setCurrent(ctx, plugin.id)
                LocalLlmManager.unload()
            }

            withContext(Dispatchers.Main) {
                pbLocalModel.visibility = View.GONE
                pbLocalModel.progress = 0
                installing = false
                btnImportPlugin.isEnabled = true
                btnInstallLocalModel.isEnabled = true
                if (plugin != null) {
                    Toast.makeText(this@AiOptionsActivity, getString(R.string.ai_plugin_imported, plugin.name), Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@AiOptionsActivity, getString(R.string.ai_plugin_import_failed), Toast.LENGTH_LONG).show()
                }
                refreshLocalStatus()
                rebuildPluginList()
            }
        }
    }

    // ==================== 自动搜寻 ====================

    /** 扫描手机常见目录，列出可用插件包，点击即安装；扫描中按钮变为「取消搜寻」 */
    private fun doAutoScan() {
        if (installing) return
        installing = true
        scanning = true
        scanCancelled = false
        btnAutoScan.isEnabled = true
        btnAutoScan.text = getString(R.string.ai_scan_cancel)
        btnImportPlugin.isEnabled = false
        btnInstallLocalModel.isEnabled = false
        llScanResult.removeAllViews()
        pbLocalModel.visibility = View.VISIBLE
        pbLocalModel.isIndeterminate = true
        tvLocalModelStatus.text = getString(R.string.ai_scan_scanning)

        CoroutineScope(Dispatchers.IO).launch {
            val found = try {
                PluginManager.scanForPlugins { scanCancelled }
            } catch (t: Throwable) {
                emptyList()
            }
            val cancelled = scanCancelled
            withContext(Dispatchers.Main) {
                scanning = false
                pbLocalModel.isIndeterminate = false
                pbLocalModel.visibility = View.GONE
                installing = false
                btnAutoScan.isEnabled = true
                btnAutoScan.text = getString(R.string.ai_scan_button)
                btnImportPlugin.isEnabled = true
                btnInstallLocalModel.isEnabled = true
                if (cancelled) {
                    tvLocalModelStatus.text = getString(R.string.ai_scan_cancelled)
                } else {
                    renderScanResult(found)
                }
            }
        }
    }

    /** 渲染扫描结果列表；点击某项即触发安装 */
    private fun renderScanResult(found: List<PluginManager.FoundPlugin>) {
        llScanResult.removeAllViews()
        if (found.isEmpty()) {
            tvLocalModelStatus.text = getString(R.string.ai_scan_none)
            return
        }
        tvLocalModelStatus.text = getString(R.string.ai_scan_found, found.size)

        val installedIds = LocalLlmManager.listPlugins(applicationContext).map { it.id }.toSet()

        for (f in found) {
            val already = f.id in installedIds
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, 12, 0, 12)
            }
            val title = TextView(this).apply {
                text = (if (already) "✅ " else "📦 ") + f.name + (if (already) getString(R.string.ai_scan_installed_tag) else "")
                textSize = 14f
                setTextColor(0xFF212121.toInt())
                setTypeface(null, android.graphics.Typeface.BOLD)
            }
            val sub = TextView(this).apply {
                text = f.zipFile.name + " · v" + f.version + " · " + f.abi + " · " + PluginManager.humanSize(f.sizeBytes)
                textSize = 11f
                setTextColor(0xFF757575.toInt())
            }
            row.addView(title)
            row.addView(sub)
            if (!already) {
                row.setOnClickListener { installFoundPlugin(f) }
            }
            llScanResult.addView(row)
        }
    }

    /** 安装扫描到的插件包（后台解压 + 进度显示） */
    private fun installFoundPlugin(f: PluginManager.FoundPlugin) {
        if (installing) return
        installing = true
        btnAutoScan.isEnabled = false
        btnImportPlugin.isEnabled = false
        btnInstallLocalModel.isEnabled = false
        pbLocalModel.visibility = View.VISIBLE
        pbLocalModel.isIndeterminate = false
        pbLocalModel.progress = 0

        val ctx = applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            withContext(Dispatchers.Main) {
                tvLocalModelStatus.text = getString(R.string.ai_scan_installing, f.name)
            }
            val plugin = PluginManager.installFromZipFile(ctx, f.zipFile) { name, done, total ->
                runOnUiThread {
                    tvLocalModelStatus.text = getString(R.string.ai_plugin_extracting, name)
                    if (total > 0) {
                        pbLocalModel.progress = (done * 100 / total).toInt().coerceIn(0, 100)
                    }
                }
            }
            if (plugin != null) {
                PluginManager.setCurrent(ctx, plugin.id)
                LocalLlmManager.unload()
            }
            withContext(Dispatchers.Main) {
                pbLocalModel.visibility = View.GONE
                pbLocalModel.progress = 0
                installing = false
                btnAutoScan.isEnabled = true
                btnImportPlugin.isEnabled = true
                btnInstallLocalModel.isEnabled = true
                if (plugin != null) {
                    Toast.makeText(this@AiOptionsActivity, getString(R.string.ai_plugin_imported, plugin.name), Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@AiOptionsActivity, getString(R.string.ai_plugin_import_failed), Toast.LENGTH_LONG).show()
                }
                llScanResult.removeAllViews()
                refreshLocalStatus()
                rebuildPluginList()
            }
        }
    }

    /** 查询 SAF uri 的文件大小（不可用时返回 -1） */
    private fun queryFileSize(uri: Uri): Long {
        return try {
            contentResolver.query(uri, null, null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val idx = c.getColumnIndex(OpenableColumns.SIZE)
                    if (idx >= 0 && !c.isNull(idx)) c.getLong(idx) else -1L
                } else -1L
            } ?: -1L
        } catch (t: Throwable) {
            -1L
        }
    }

    /** 切换当前插件并立即加载进内存（后台执行，带进度提示） */
    private fun switchAndLoadPlugin(p: PluginManager.PluginInfo) {
        if (installing) return
        installing = true
        btnAutoScan.isEnabled = false
        btnImportPlugin.isEnabled = false
        btnInstallLocalModel.isEnabled = false
        pbLocalModel.visibility = View.VISIBLE
        pbLocalModel.isIndeterminate = true
        tvLocalModelStatus.text = getString(R.string.ai_plugin_switching)

        val ctx = applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            // 切到新插件（unload 会释放旧引擎），再加载
            PluginManager.setCurrent(ctx, p.id)
            LocalLlmManager.unload()
            val ok = LocalLlmManager.ensureLoaded(ctx)

            withContext(Dispatchers.Main) {
                pbLocalModel.isIndeterminate = false
                pbLocalModel.visibility = View.GONE
                installing = false
                btnAutoScan.isEnabled = true
                btnImportPlugin.isEnabled = true
                btnInstallLocalModel.isEnabled = true
                if (!ok) {
                    Toast.makeText(this@AiOptionsActivity, getString(R.string.ai_plugin_loaded_fail), Toast.LENGTH_LONG).show()
                }
                refreshLocalStatus()
                rebuildPluginList()
            }
        }
    }

    // ==================== 加载 / 重载 ====================

    private fun loadOrReloadLocalModel() {
        if (installing) return
        val ctx = applicationContext
        val current = LocalLlmManager.currentPlugin(ctx)
        if (current == null) {
            Toast.makeText(this, getString(R.string.ai_plugin_need_select), Toast.LENGTH_SHORT).show()
            return
        }
        installing = true
        btnInstallLocalModel.isEnabled = false
        btnImportPlugin.isEnabled = false
        pbLocalModel.visibility = View.VISIBLE
        pbLocalModel.isIndeterminate = true

        CoroutineScope(Dispatchers.IO).launch {
            val ok = LocalLlmManager.ensureLoaded(ctx)
            withContext(Dispatchers.Main) {
                pbLocalModel.isIndeterminate = false
                pbLocalModel.visibility = View.GONE
                installing = false
                btnInstallLocalModel.isEnabled = true
                btnImportPlugin.isEnabled = true
                tvLocalModelStatus.text = getString(
                    if (ok) R.string.ai_plugin_loaded_ok else R.string.ai_plugin_loaded_fail
                )
            }
        }
    }

    // ==================== 未保存更改保护 ====================

    private fun snapshotCurrentState() {
        initialLocalModelEnabled = localSelected()
        initialLocalPort = etLocalModelPort.text.toString()
        initialScheduleMode = readCurrentMode()
        initialLookaheadDays = etLookaheadDays.text.toString().trim().toIntOrNull() ?: DEFAULT_DAYS
    }

    private fun hasUnsavedChanges(): Boolean {
        if (localSelected() != initialLocalModelEnabled) return true
        if (etLocalModelPort.text.toString() != initialLocalPort) return true
        if (readCurrentMode() != initialScheduleMode) return true
        val currentDays = etLookaheadDays.text.toString().trim().toIntOrNull() ?: DEFAULT_DAYS
        if (currentDays != initialLookaheadDays) return true
        return false
    }

    /** 从 UI 读取当前选择的日程结合模式 */
    private fun readCurrentMode(): String = when {
        radioCodeCheck.isChecked -> MODE_CODE_CHECK
        radioAiAnalysis.isChecked && !localSelected() -> MODE_AI_ANALYSIS
        else -> MODE_NONE
    }

    private fun showUnsavedChangesDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.unsaved_title)
            .setMessage(R.string.unsaved_msg_settings)
            .setPositiveButton(R.string.unsaved_save) { _, _ ->
                if (saveSettings()) finish()
            }
            .setNegativeButton(R.string.unsaved_discard) { _, _ -> finish() }
            .setNeutralButton(R.string.unsaved_cancel, null)
            .show()
    }

    /**
     * 保存设置并刷新快照。
     * @return 是否保存成功（天数/端口校验不通过时返回 false）
     */
    private fun saveSettings(): Boolean {
        val daysInput = etLookaheadDays.text.toString().trim()
        val days = daysInput.toIntOrNull()
        if (days == null || days < MIN_DAYS || days > MAX_DAYS) {
            Toast.makeText(this, getString(R.string.ai_days_invalid, MIN_DAYS, MAX_DAYS), Toast.LENGTH_SHORT).show()
            return false
        }

        val portInput = etLocalModelPort.text.toString().trim()
        val port = portInput.toIntOrNull() ?: DEFAULT_PORT
        if (port !in 1024..65535) {
            Toast.makeText(this, getString(R.string.ai_port_invalid), Toast.LENGTH_SHORT).show()
            return false
        }

        val localEnabled = localSelected()
        if (localEnabled && LocalLlmManager.currentPlugin(this) == null) {
            Toast.makeText(this, getString(R.string.ai_save_local_warn), Toast.LENGTH_LONG).show()
        }

        val mode = readCurrentMode()
        prefs.edit()
            .putBoolean(KEY_LOCAL_MODEL_ENABLED, localEnabled)
            .putString(KEY_LOCAL_MODEL_PORT, port.toString())
            .putString(KEY_SCHEDULE_MODE, mode)
            .putInt(KEY_LOOKAHEAD_DAYS, days)
            .apply()

        snapshotCurrentState()
        return true
    }

    /**
     * 联动逻辑：选择本地模型时禁用「交给 AI 分析」；
     * 若此前已选中该项，则自动回退到「代码侧冲突检测」。
     */
    private fun updateAiAnalysisAvailability() {
        val local = localSelected()
        radioAiAnalysis.isEnabled = !local
        if (local && radioAiAnalysis.isChecked) {
            radioCodeCheck.isChecked = true
        }
    }
}
