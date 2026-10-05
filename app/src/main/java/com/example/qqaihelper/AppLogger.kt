package com.example.qqaihelper

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 全局日志工具（单例）。
 *
 * 背景：
 * Android 的 Log.d / Log.e 只在连接电脑、用 Logcat 才能看到。
 * 对于「纯手机端调试」的场景非常不便。因此这里做了一层「双写」：
 *   1. 依然调用 Log.d/Log.e，方便开发者插着数据线看 Logcat；
 *   2. 同步追加写入 filesDir/app_log.txt，供 App 内置的「日志查看」页面读取。
 *
 * 为什么用 object 而不是 class：
 * - 日志是全局共享的，整个 App 只需要一个实例；
 * - object 在 Kotlin 里天然是线程安全的单例，无需手动加锁创建。
 *
 * 线程安全：
 * - writeToFile 和 clearLog 都加了 @Synchronized，避免多协程并发写同一个文件时
 *   出现字符错乱。
 */
object AppLogger {

    /** Logcat 中显示的 TAG，方便过滤 */
    private const val TAG = "QQ_AI_HELPER"

    /** 日志文件名（位于 App 私有目录 filesDir 下） */
    private const val LOG_FILE_NAME = "app_log.txt"

    /** 日志文件大小上限：2MB。超过后自动清空重写，避免无限膨胀占满存储 */
    private const val MAX_LOG_SIZE = 1024 * 1024 * 2

    /** 日志文件句柄，延迟初始化（需要 Context 才能拿到 filesDir） */
    private lateinit var logFile: File

    /**
     * 初始化日志文件路径。
     *
     * ⚠️ 必须在使用任何日志方法之前调用一次，否则 writeToFile 会静默跳过。
     * 建议调用位置：
     * - HomeActivity.onCreate（App 主入口，覆盖绝大多数场景）
     * - QQNotificationListener.onCreate（后台服务被系统拉起时，保证日志依然可用）
     *
     * 用 isInitialized 判断避免重复初始化（幂等）。
     */
    fun init(context: Context) {
        if (!::logFile.isInitialized) {
            logFile = File(context.filesDir, LOG_FILE_NAME)
        }
    }

    /**
     * 记录普通调试日志（对应 Log.d）。
     * 写入格式：[MM-dd HH:mm:ss] [D] 消息内容
     */
    fun d(message: String) {
        Log.d(TAG, message)
        writeToFile("D", message)
    }

    /**
     * 记录错误日志（对应 Log.e）。
     *
     * @param message  错误描述
     * @param throwable 可选异常对象，会把堆栈一起写入文件（便于事后排查）
     */
    fun e(message: String, throwable: Throwable? = null) {
        Log.e(TAG, message, throwable)
        writeToFile("E", "$message ${throwable?.stackTraceToString() ?: ""}")
    }

    /**
     * 实际的写文件逻辑。
     *
     * 为什么用同步写入而不是协程：
     * - 日志本就是小量、低频的操作，同步写入不会明显阻塞主线程；
     * - 同步能保证「用户点击清空 → 立即写新日志 → 立即能看到」的时序一致，
     *   避免异步协程在 clearLog 之后才落盘导致的「日志消失」问题。
     *
     * @param level   "D" 或 "E"
     * @param message 日志正文
     */
    @Synchronized
    private fun writeToFile(level: String, message: String) {
        if (!::logFile.isInitialized) return

        try {
            // 文件超限时清空重写（用 writeText("") 而不是 delete()，
            // 因为 delete 之后 File 句柄仍在，appendText 会重新创建，但容易与
            // 正在读取 UI 的线程产生「文件不存在」的竞态）
            if (logFile.exists() && logFile.length() > MAX_LOG_SIZE) {
                logFile.writeText("")
            }
            val time = SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
            val logLine = "$time [$level] $message\n"
            logFile.appendText(logLine) // 追加写入，同步落盘
        } catch (e: Exception) {
            // 写日志本身失败时，只能退回到 Logcat，避免死循环
            Log.e(TAG, "写入日志文件失败", e)
        }
    }

    /**
     * 获取日志文件句柄，供「日志查看」页面读取内容。
     * @throws IllegalStateException 若尚未调用 init()，抛出异常提醒开发者
     */
    fun getLogFile(): File {
        if (!::logFile.isInitialized) {
            throw IllegalStateException("AppLogger 尚未初始化！请先调用 AppLogger.init(context)")
        }
        return logFile
    }

    /**
     * 清空日志内容。
     * 用 writeText("") 而非 delete()：避免与读取端产生文件不存在的竞态。
     */
    @Synchronized
    fun clearLog() {
        if (::logFile.isInitialized && logFile.exists()) {
            logFile.writeText("")
        }
    }
}
