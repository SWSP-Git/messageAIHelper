package com.example.qqaihelper

import android.content.Intent
import android.os.Bundle
import android.widget.ImageView
import androidx.cardview.widget.CardView

/**
 * 应用主入口页（Dashboard）。
 *
 * 生命周期注意：
 * - AppLogger 在此初始化（用户打开 App 后最先到达的页面）
 * - 应用语言也在此恢复（从 AppCompatDelegate 中读取用户上次的选择）
 */
class HomeActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        // 恢复用户上次选择的语言（如果有）
        LocaleHelper.applyLanguageOnStartup(this)

        // 初始化全局日志工具
        AppLogger.init(applicationContext)

        findViewById<ImageView>(R.id.btnSettingsGear).setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
        }

        findViewById<CardView>(R.id.cardMemo).setOnClickListener {
            startActivity(Intent(this, MemoListActivity::class.java))
        }

        findViewById<CardView>(R.id.cardLog).setOnClickListener {
            startActivity(Intent(this, LogViewerActivity::class.java))
        }
    }
}
