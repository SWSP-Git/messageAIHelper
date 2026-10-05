package com.example.qqaihelper

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider

class LogViewerActivity : AppCompatActivity() {

    private lateinit var tvLogContent: TextView
    private lateinit var scrollView: ScrollView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_log_viewer)

        tvLogContent = findViewById(R.id.tvLogContent)
        scrollView = findViewById(R.id.scrollViewLog)

        // 读取日志文件内容
        loadLogContent()

        findViewById<Button>(R.id.btnClearLog).setOnClickListener {
            AppLogger.clearLog()
            tvLogContent.text = "日志已清空"
        }

        findViewById<Button>(R.id.btnShareLog).setOnClickListener {
            shareLogFile()
        }
    }

    private fun loadLogContent() {
        val logFile = AppLogger.getLogFile()
        if (logFile.exists()) {
            // 只读取最后 2000 行，防止文本过长卡顿
            val lines = logFile.readLines()
            val displayLines = if (lines.size > 2000) lines.takeLast(2000) else lines
            tvLogContent.text = displayLines.joinToString("\n")

            // 自动滚动到底部
            scrollView.post {
                scrollView.fullScroll(ScrollView.FOCUS_DOWN)
            }
        } else {
            tvLogContent.text = "暂无日志记录"
        }
    }

    private fun shareLogFile() {
        val logFile = AppLogger.getLogFile()
        if (!logFile.exists()) return

        try {
            // 使用 FileProvider 分享日志文件，避免因权限问题失败
            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", logFile)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "分享日志文件"))
        } catch (e: Exception) {
            // 如果 FileProvider 没配置，退化成纯文本分享
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, tvLogContent.text.toString())
            }
            startActivity(Intent.createChooser(intent, "分享日志文本"))
        }
    }
}