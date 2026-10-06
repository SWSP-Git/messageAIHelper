package com.example.qqaihelper

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider

/**
 * 应用内日志查看器。
 *
 * 背景：
 * 传统 Android 调试需要插数据线 + 打开 Logcat，对普通用户不友好，
 * 也不方便开发者脱离电脑排查问题。本页面直接从 AppLogger 写入的
 * 本地日志文件读取内容并展示，让「手机端调试」成为可能。
 *
 * 功能：
 * - 显示日志内容（自动滚动到最新）
 * - 清空日志
 * - 分享日志（通过 FileProvider 发出 .txt 附件）
 *
 * 日志文件位置：filesDir/app_log.txt（由 AppLogger 管理）
 */
class LogViewerActivity : AppCompatActivity() {

    private lateinit var tvLogContent: TextView
    private lateinit var scrollView: ScrollView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_log_viewer)

        tvLogContent = findViewById(R.id.tvLogContent)
        scrollView = findViewById(R.id.scrollViewLog)

        // 进入页面时立即加载一次日志
        loadLogContent()

        // 清空按钮：调用 AppLogger.clearLog() 后用文字提示替代，避免空白页面
        findViewById<Button>(R.id.btnClearLog).setOnClickListener {
            AppLogger.clearLog()
            tvLogContent.text = "日志已清空"
        }

        // 分享按钮：把日志文件作为附件发给任何人/应用
        findViewById<Button>(R.id.btnShareLog).setOnClickListener {
            shareLogFile()
        }
    }

    /**
     * 读取日志文件并展示。
     *
     * 性能考量：
     * - 只取最后 2000 行，避免日志过多时字符串拼接导致 UI 卡顿
     * - 展示后自动滚动到底部，让用户第一时间看到最新日志
     */
    private fun loadLogContent() {
        val logFile = AppLogger.getLogFile()
        if (logFile.exists()) {
            // takeLast 对短数组也安全：长度不足时直接返回全部
            tvLogContent.text = logFile.readLines().takeLast(2000).joinToString("\n")

            // 自动滚到底部（post 是为了等 TextView 布局完成后再滚动）
            scrollView.post {
                scrollView.fullScroll(ScrollView.FOCUS_DOWN)
            }
        } else {
            tvLogContent.text = "暂无日志记录"
        }
    }

    /**
     * 分享日志文件。
     *
     * 优先方案：用 FileProvider 生成 content:// URI 分享原始 .txt 文件。
     *   - 走 FileProvider 是因为 Android 7.0+ 禁止直接分享 file:// URI
     *
     * 降级方案：如果 FileProvider 未配置或分享失败，退化成把日志文本
     *   直接作为 EXTRA_TEXT 发送（丢失附件形式，但至少能发出内容）。
     */
    private fun shareLogFile() {
        val logFile = AppLogger.getLogFile()
        if (!logFile.exists()) return

        try {
            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", logFile)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "分享日志文件"))
        } catch (e: Exception) {
            // FileProvider 未配置 / 权限异常时走文本分享
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, tvLogContent.text.toString())
            }
            startActivity(Intent.createChooser(intent, "分享日志文本"))
        }
    }
}
