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

/**
 * AI 与日程设置页。
 *
 * 从「备忘录」页面右上角齿轮进入。
 *
 * 未保存更改保护：
 * 用户修改了任意选项但没点「保存设置」直接返回时，会弹窗询问是否保存。
 */
class AiOptionsActivity : BaseActivity() {

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

    private var initialLocalModelEnabled = false
    private var initialScheduleMode = MODE_NONE
    private var initialLookaheadDays = DEFAULT_DAYS

    private lateinit var switchLocalModel: Switch
    private lateinit var radioNone: RadioButton
    private lateinit var radioCodeCheck: RadioButton
    private lateinit var radioAiAnalysis: RadioButton
    private lateinit var etLookaheadDays: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ai_options)

        prefs = getSharedPreferences("app_settings", Context.MODE_PRIVATE)

        switchLocalModel = findViewById(R.id.switchLocalModel)
        val radioGroup = findViewById<RadioGroup>(R.id.radioGroupScheduleMode)
        radioNone = findViewById(R.id.radioModeNone)
        radioCodeCheck = findViewById(R.id.radioModeCodeCheck)
        radioAiAnalysis = findViewById(R.id.radioModeAiAnalysis)
        etLookaheadDays = findViewById(R.id.etLookaheadDays)
        val btnSave = findViewById<Button>(R.id.btnSaveAiOptions)

        switchLocalModel.isChecked = prefs.getBoolean(KEY_LOCAL_MODEL_ENABLED, false)

        val savedMode = prefs.getString(KEY_SCHEDULE_MODE, MODE_NONE) ?: MODE_NONE
        when (savedMode) {
            MODE_CODE_CHECK -> radioCodeCheck.isChecked = true
            MODE_AI_ANALYSIS -> radioAiAnalysis.isChecked = true
            else -> radioNone.isChecked = true
        }

        etLookaheadDays.setText(prefs.getInt(KEY_LOOKAHEAD_DAYS, DEFAULT_DAYS).toString())

        snapshotCurrentState()

        switchLocalModel.setOnCheckedChangeListener { _, isChecked ->
            updateAiAnalysisAvailability(radioAiAnalysis, radioCodeCheck, isChecked)
        }
        updateAiAnalysisAvailability(radioAiAnalysis, radioCodeCheck, switchLocalModel.isChecked)

        btnSave.setOnClickListener {
            if (saveSettings()) {
                Toast.makeText(this, getString(R.string.ai_saved_toast), Toast.LENGTH_SHORT).show()
                finish()
            }
        }

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

    private fun readCurrentMode(): String = when {
        radioCodeCheck.isChecked -> MODE_CODE_CHECK
        radioAiAnalysis.isChecked && !switchLocalModel.isChecked -> MODE_AI_ANALYSIS
        else -> MODE_NONE
    }

    private fun showUnsavedChangesDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.unsaved_title)
            .setMessage(R.string.unsaved_msg_settings)
            .setPositiveButton(R.string.unsaved_save) { _, _ ->
                if (saveSettings()) {
                    finish()
                }
            }
            .setNegativeButton(R.string.unsaved_discard) { _, _ ->
                finish()
            }
            .setNeutralButton(R.string.unsaved_cancel, null)
            .show()
    }

    private fun saveSettings(): Boolean {
        val daysInput = etLookaheadDays.text.toString().trim()
        val days = daysInput.toIntOrNull()
        if (days == null || days < MIN_DAYS || days > MAX_DAYS) {
            Toast.makeText(this, getString(R.string.ai_days_invalid, MIN_DAYS, MAX_DAYS), Toast.LENGTH_SHORT).show()
            return false
        }

        val mode = readCurrentMode()

        prefs.edit()
            .putBoolean(KEY_LOCAL_MODEL_ENABLED, switchLocalModel.isChecked)
            .putString(KEY_SCHEDULE_MODE, mode)
            .putInt(KEY_LOOKAHEAD_DAYS, days)
            .apply()

        snapshotCurrentState()
        return true
    }

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
