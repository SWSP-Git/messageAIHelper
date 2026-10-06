package com.example.qqaihelper

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.FileProvider

/**
 * 应用内日志查看器。
 */
class LogViewerActivity : BaseActivity() {

    private lateinit var tvLogContent: TextView
    private lateinit var scrollView: ScrollView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_log_viewer)

        tvLogContent = findViewById(R.id.tvLogContent)
        scrollView = findViewById(R.id.scrollViewLog)

        loadLogContent()

        findViewById<Button>(R.id.btnClearLog).setOnClickListener {
            AppLogger.clearLog()
            tvLogContent.text = getString(R.string.log_cleared)
        }

        findViewById<Button>(R.id.btnShareLog).setOnClickListener {
            shareLogFile()
        }
    }

    private fun loadLogContent() {
        val logFile = AppLogger.getLogFile()
        if (logFile.exists()) {
            tvLogContent.text = logFile.readLines().takeLast(2000).joinToString("\n")
            scrollView.post {
                scrollView.fullScroll(ScrollView.FOCUS_DOWN)
            }
        } else {
            tvLogContent.text = getString(R.string.log_empty)
        }
    }

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
            startActivity(Intent.createChooser(intent, getString(R.string.log_share_file_title)))
        } catch (e: Exception) {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, tvLogContent.text.toString())
            }
            startActivity(Intent.createChooser(intent, getString(R.string.log_share_text_title)))
        }
    }
}
