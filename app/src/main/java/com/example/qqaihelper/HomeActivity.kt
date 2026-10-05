package com.example.qqaihelper

import android.content.Intent
import android.os.Bundle
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView

/**
 * 应用主入口页（Dashboard / 仪表盘）。
 *
 * 设计意图：
 * - 与 MainActivity（旧主页）分离，这里只负责「展示功能入口卡片」和「跳转到其他页面」。
 * - 所有配置项（API Key、过滤规则等）都收敛到 MainActivity（通过右上角齿轮进入）。
 * - 卡片式布局让新用户一眼看清能做什么，而不是被一堆输入框吓到。
 *
 * 生命周期注意：
 * - AppLogger 在此初始化（而不是在 Application 里），因为这是用户打开 App 后
 *   最先到达的页面。其他页面（日志查看、备忘录等）都依赖 AppLogger 已就绪。
 */
class HomeActivity : AppCompatActivity() {

    /**
     * Activity 创建时调用：绑定布局、初始化日志、注册三个入口的点击事件。
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 加载 activity_home.xml 布局（顶部标题栏 + 若干功能卡片）
        setContentView(R.layout.activity_home)

        // 初始化全局日志工具：写入 app_log.txt，供「查看运行日志」页面读取。
        // 放在这里能保证 App 一启动日志就可用，避免其它页面首次访问时抛 IllegalStateException。
        AppLogger.init(applicationContext)

        // 入口 1：右上角齿轮图标 → 跳转到 MainActivity（现作为「设置」页使用）
        // MainActivity 里包含：API URL / Key / 模型名 / 监听应用选择 / 关键词过滤规则 等配置项
        findViewById<ImageView>(R.id.btnSettingsGear).setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
        }

        // 入口 2：备忘录卡片 → 跳转到 MemoListActivity
        // 备忘录数据由 QQNotificationListener 写入 filesDir/memo_list.txt，此页面负责读取并列表展示
        findViewById<CardView>(R.id.cardMemo).setOnClickListener {
            startActivity(Intent(this, MemoListActivity::class.java))
        }

        // 入口 3：运行日志卡片 → 跳转到 LogViewerActivity
        // 用于在没有电脑的情况下查看 App 运行状态，便于开发者/用户自助排查问题
        findViewById<CardView>(R.id.cardLog).setOnClickListener {
            startActivity(Intent(this, LogViewerActivity::class.java))
        }
    }
}
