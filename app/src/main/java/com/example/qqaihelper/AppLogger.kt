package com.example.qqaihelper

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AppLogger {
    private const val TAG = "QQ_AI_HELPER"
    private const val LOG_FILE_NAME = "app_log.txt"
    private const val MAX_LOG_SIZE = 1024 * 1024 * 2 // 限制日志文件为 2MB

    private lateinit var logFile: File

    // 初始化，必须在 Application 或 MainActivity 中调用一次
    fun init(context: Context) {
        if (!::logFile.isInitialized) {
            logFile = File(context.filesDir, LOG_FILE_NAME)
        }
    }

    // 输出普通日志
    fun d(message: String) {
        Log.d(TAG, message)
        writeToFile("D", message)
    }

    // 输出错误日志
    fun e(message: String, throwable: Throwable? = null) {
        Log.e(TAG, message, throwable)
        writeToFile("E", "$message ${throwable?.stackTraceToString() ?: ""}")
    }

    // 写入文件的实际逻辑（改为同步写入，保证立刻可见）
    @Synchronized
    private fun writeToFile(level: String, message: String) {
        if (!::logFile.isInitialized) return

        try {
            // 如果日志文件太大，就清空重来（直接清空内容，不删文件）
            if (logFile.exists() && logFile.length() > MAX_LOG_SIZE) {
                logFile.writeText("")
            }
            val time = SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
            val logLine = "$time [$level] $message\n"
            logFile.appendText(logLine) // 同步追加，保证立刻落盘
        } catch (e: Exception) {
            Log.e(TAG, "写入日志文件失败", e)
        }
    }

    // 获取日志文件供 UI 读取
    fun getLogFile(): File {
        if (!::logFile.isInitialized) {
            throw IllegalStateException("AppLogger 尚未初始化！")
        }
        return logFile
    }

    // 清空日志（用清空内容代替删除文件，避免文件读取状态冲突）
    @Synchronized
    fun clearLog() {
        if (::logFile.isInitialized && logFile.exists()) {
            logFile.writeText("")
        }
    }
}