package com.example.qqaihelper

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * 数据导出工具。
 *
 * 导出内容：
 * - memo_list.txt：备忘录数据（从外部/私有目录读取）
 * - prefs.json：用户配置（API Key、过滤规则、Webhook 配置等）
 *
 * 输出格式：ZIP 压缩包，文件名 messageAIHelper_backup_yyyyMMdd_HHmmss.zip
 *
 * 使用 SAF (Storage Access Framework) 让用户选择保存位置，
 * 无需任何存储权限，兼容 Android 4.4+。
 *
 * ⚠️ 导出的 ZIP 中包含 API Key 等敏感信息，请妥善保管。
 */
object DataExporter {

    /**
     * 执行导出。
     *
     * @param context  上下文
     * @param targetUri 用户通过 SAF 选择的输出 URI
     * @return 成功返回 true；失败抛异常或返回 false
     */
    fun export(context: Context, targetUri: Uri): Boolean {
        return try {
            val outputStream: OutputStream = context.contentResolver.openOutputStream(targetUri)
                ?: return false
            ZipOutputStream(outputStream).use { zip ->
                // 1. 导出备忘录
                val memoFile = StorageHelper.getMemoFile(context)
                if (memoFile.exists()) {
                    zip.putNextEntry(ZipEntry("memo_list.txt"))
                    zip.write(memoFile.readBytes())
                    zip.closeEntry()
                }

                // 2. 导出 SharedPreferences 为 JSON
                val prefsJson = dumpPrefs(context)
                zip.putNextEntry(ZipEntry("prefs.json"))
                zip.write(prefsJson.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            AppLogger.d("✅ 数据导出成功")
            true
        } catch (e: Exception) {
            AppLogger.e("数据导出失败: ${e.message}")
            false
        }
    }

    /** 建议的 ZIP 文件名 */
    fun suggestFileName(): String {
        val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        return "messageAIHelper_backup_$ts.zip"
    }

    /**
     * 把 SharedPreferences("app_settings") 的内容序列化为 JSON。
     * 不依赖第三方 JSON 库，手写字符串拼接即可（结构简单）。
     */
    private fun dumpPrefs(context: Context): String {
        val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        val sb = StringBuilder()
        sb.append("{\n")
        val entries = prefs.all.entries.toList()
        entries.forEachIndexed { index, entry ->
            val key = escapeJson(entry.key)
            val value = entry.value
            sb.append("  \"")
            sb.append(key)
            sb.append("\": ")
            when (value) {
                is String -> {
                    sb.append("\"")
                    sb.append(escapeJson(value))
                    sb.append("\"")
                }
                is Boolean, is Int, is Long, is Float -> sb.append(value.toString())
                else -> {
                    sb.append("\"")
                    sb.append(escapeJson(value.toString()))
                    sb.append("\"")
                }
            }
            if (index < entries.size - 1) sb.append(",")
            sb.append("\n")
        }
        sb.append("}")
        return sb.toString()
    }

    private fun escapeJson(s: String): String {
        return s.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }
}
