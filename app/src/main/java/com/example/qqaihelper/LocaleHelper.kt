package com.example.qqaihelper

import android.content.Context
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import java.util.Locale

/**
 * 应用语言管理工具。
 *
 * 基于 AndroidX AppCompatDelegate.setApplicationLocales() 实现，
 * 支持 Android 13+ 的原生 per-app language，并通过 AppCompat 兼容到
 * 更低的 Android 版本（API 26+）。
 *
 * 语言选择保存在系统框架中（不是我们的 SharedPreferences），
 * 因为这是 AndroidX 官方推荐的方式，能在系统设置里同步显示。
 *
 * 用法：
 * - LocaleHelper.setLanguage(context, "zh") 切换到中文
 * - LocaleHelper.setLanguage(context, "en") 切换到英文
 * - LocaleHelper.getCurrentLanguage() 返回当前语言代码
 */
object LocaleHelper {

    const val LANG_ZH = "zh"
    const val LANG_EN = "en"
    const val LANG_SYSTEM = "system"

    /**
     * 切换应用语言。
     *
     * @param langCode "zh" 中文 / "en" 英文 / "system" 跟随系统
     */
    fun setLanguage(context: Context, langCode: String) {
        val locales = when (langCode) {
            LANG_ZH -> LocaleListCompat.forLanguageTags("zh-CN")
            LANG_EN -> LocaleListCompat.forLanguageTags("en")
            else -> LocaleListCompat.getEmptyLocaleList() // 空列表 = 跟随系统
        }
        AppCompatDelegate.setApplicationLocales(locales)
    }

    /**
     * 获取当前应用语言代码。
     * @return "zh" / "en" / "system"
     */
    fun getCurrentLanguage(): String {
        val locales = AppCompatDelegate.getApplicationLocales()
        if (locales.isEmpty) return LANG_SYSTEM
        val lang = locales.toLanguageTags()
        return when {
            lang.startsWith("zh") -> LANG_ZH
            lang.startsWith("en") -> LANG_EN
            else -> LANG_SYSTEM
        }
    }

    /**
     * 在 Application.onCreate 中调用，恢复用户上次选择的语言。
     * 建议在 App 启动时执行一次。
     */
    fun applyLanguageOnStartup(context: Context) {
        val saved = getCurrentLanguage()
        if (saved != LANG_SYSTEM) {
            setLanguage(context, saved)
        }
    }
}
