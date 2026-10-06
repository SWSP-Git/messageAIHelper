package com.example.qqaihelper

import android.content.Context
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Switch
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

/**
 * AI 与日程设置页。
 *
 * 从「备忘录」页面右上角齿轮进入。
 *
 * 功能：
 * 1. 本地模型开关（UI 占位，实际推理逻辑待实现）
 * 2. 日程结合模式三选一
 *    - 不结合日程
 *    - 代码侧冲突检测
 *    - 交给 AI 分析（仅云端模型可用）
 * 3. 未来日程查询天数（1-30 天），仅第 3 档生效
 *
 * 未保存更改保护：
 * 用户修改了任意选项但没点「保存设置」直接返回时，会弹窗询问是否保存。
 */
class AiOptionsActivity : AppCompatActivity() {

    companion object {
        const val KEY_LOCAL_MODEL_ENABLED = "local_model_enabled"
        const val KEY_SCHEDULE_MODE = "schedule_mode"
        const val KEY_LOOKAHEAD_DAYS = "lookahead_days"

        const val MODE_NONE = "none"
        const val MODE_CODE_CHECK = "code_check"
        const val MODE_AI_ANALYSIS = "ai_analysis"

        const val MIN_DAYS = 1
        const val MAX_DAYS = 30
        const val DEFAULT_DAYS = 7
    }

    private lateinit var prefs: android.content.SharedPreferences

    // ==================== 未保存快照 ====================
    private var initialLocalModelEnabled = false
    private var initialScheduleMode = MODE_NONE
    private var initialLookaheadDays = DEFAULT_DAYS

    // 控件引用（快照对比时需要读取）
    private lateinit var switchLocalModel: Switch
    private lateinit var radioNone: RadioButton
    private lateinit var radioCodeCheck: RadioButton
    private lateinit var radioAiAnalysis: RadioButton
    private lateinit var etLookaheadDays: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ai_options)

        prefs = getSharedPreferences("app_settings", Context.MODE_PRIVATE)

        // ---------- 绑定控件 ----------
        switchLocalModel = findViewById(R.id.switchLocalModel)
        val radioGroup = findViewById<RadioGroup>(R.id.radioGroupScheduleMode)
        radioNone = findViewById(R.id.radioModeNone)
        radioCodeCheck = findViewById(R.id.radioModeCodeCheck)
        radioAiAnalysis = findViewById(R.id.radioModeAiAnalysis)
        etLookaheadDays = findViewById(R.id.etLookaheadDays)
        val btnSave = findViewById<Button>(R.id.btnSaveAiOptions)

        // ---------- 加载已有配置 ----------
        switchLocalModel.isChecked = prefs.getBoolean(KEY_LOCAL_MODEL_ENABLED, false)

        val savedMode = prefs.getString(KEY_SCHEDULE_MODE, MODE_NONE) ?: MODE_NONE
        when (savedMode) {
            MODE_CODE_CHECK -> radioCodeCheck.isChecked = true
            MODE_AI_ANALYSIS -> radioAiAnalysis.isChecked = true
            else -> radioNone.isChecked = true
        }

        etLookaheadDays.setText(prefs.getInt(KEY_LOOKAHEAD_DAYS, DEFAULT_DAYS).toString())

        // 记录初始快照
        snapshotCurrentState()

        // ---------- 本地模型开关联动 ----------
        switchLocalModel.setOnCheckedChangeListener { _, isChecked ->
            updateAiAnalysisAvailability(radioAiAnalysis, radioCodeCheck, isChecked)
        }
        updateAiAnalysisAvailability(radioAiAnalysis, radioCodeCheck, switchLocalModel.isChecked)

        // ---------- 保存 ----------
        btnSave.setOnClickListener {
            if (saveSettings()) {
                Toast.makeText(this, "✅ 设置已保存", Toast.LENGTH_SHORT).show()
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

    // ==================== 未保存更改保护 ====================

    private fun snapshotCurrentState() {
        initialLocalModelEnabled = switchLocalModel.isChecked
        initialScheduleMode = readCurrentMode()
        initialLookaheadDays = etLookaheadDays.text.toString().trim().toIntOrNull() ?: DEFAULT_DAYS
    }

    private fun hasUnsavedChanges(): Boolean {
        if (switchLocalModel.isChecked != initialLocalModelEnabled) return true
        if (readCurrentMode() != initialScheduleMode) return true
        val currentDays = etLookaheadDays.text.toString().trim().toIntOrNull() ?: DEFAULT_DAYS
        if (currentDays != initialLookaheadDays) return true
        return false
    }

    /** 从 UI 读取当前选择的日程结合模式 */
    private fun readCurrentMode(): String = when {
        radioCodeCheck.isChecked -> MODE_CODE_CHECK
        radioAiAnalysis.isChecked && !switchLocalModel.isChecked -> MODE_AI_ANALYSIS
        else -> MODE_NONE
    }

    private fun showUnsavedChangesDialog() {
        AlertDialog.Builder(this)
            .setTitle("未保存的更改")
            .setMessage("当前设置已修改，是否保存后退出？")
            .setPositiveButton("保存") { _, _ ->
                if (saveSettings()) {
                    finish()
                }
            }
            .setNegativeButton("不保存") { _, _ ->
                finish()
            }
            .setNeutralButton("取消", null)
            .show()
    }

    /**
     * 保存设置并刷新快照。
     * @return 是否保存成功（天数校验不通过时返回 false）
     */
    private fun saveSettings(): Boolean {
        val daysInput = etLookaheadDays.text.toString().trim()
        val days = daysInput.toIntOrNull()
        if (days == null || days < MIN_DAYS || days > MAX_DAYS) {
            Toast.makeText(this, "查询天数必须在 $MIN_DAYS-$MAX_DAYS 之间", Toast.LENGTH_SHORT).show()
            return false
        }

        val mode = readCurrentMode()

        prefs.edit()
            .putBoolean(KEY_LOCAL_MODEL_ENABLED, switchLocalModel.isChecked)
            .putString(KEY_SCHEDULE_MODE, mode)
            .putInt(KEY_LOOKAHEAD_DAYS, days)
            .apply()

        // 保存后刷新快照，避免返回时再次提示
        snapshotCurrentState()
        return true
    }

    /**
     * 联动逻辑：本地模型开启时禁用「交给 AI 分析」；
     * 若此前已选中该项，则自动回退到「代码侧冲突检测」。
     */
    private fun updateAiAnalysisAvailability(
        radioAiAnalysis: RadioButton,
        radioCodeCheck: RadioButton,
        localModelEnabled: Boolean
    ) {
        radioAiAnalysis.isEnabled = !localModelEnabled

        if (localModelEnabled && radioAiAnalysis.isChecked) {
            radioCodeCheck.isChecked = true
        }
    }
}
