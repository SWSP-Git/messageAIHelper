package com.example.qqaihelper

import android.content.Context
import android.net.Uri
import org.json.JSONObject
import java.io.File
import java.util.zip.ZipInputStream

/**
 * 数据导入工具。
 *
 * 从 ZIP 中读取：
 * - memo_list.txt：备忘录数据（恢复到 StorageHelper 指定的位置）
 * - prefs.json：用户配置（写入 SharedPreferences）
 *
 * 使用 SAF 让用户选择 ZIP 文件，无需权限。
 *
 * 导入策略：
 * - 备忘录：追加到现有文件末尾（不覆盖），避免误删用户数据
 * - 用户配置：逐条覆盖（同名 key 直接替换）
 */
object DataImporter {

    /**
     * 执行导入。
     *
     * @param context  上下文
     * @param sourceUri 用户通过 SAF 选择的 ZIP 文件 URI
     * @return 成功返回 true；失败返回 false
     */
    fun import(context: Context, sourceUri: Uri): Boolean {
        return try {
            val inputStream = context.contentResolver.openInputStream(sourceUri)
                ?: return false

            var memoContent: String? = null
            var prefsContent: String? = null

            ZipInputStream(inputStream).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    when (entry.name) {
                        "memo_list.txt" -> memoContent = zip.readBytes().toString(Charsets.UTF_8)
                        "prefs.json" -> prefsContent = zip.readBytes().toString(Charsets.UTF_8)
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }

            // 1. 恢复备忘录（追加，不覆盖）
            if (!memoContent.isNullOrEmpty()) {
                val memoFile = StorageHelper.getMemoFile(context)
                // 确保末尾有空行
                val text = if (memoContent.endsWith("\n\n")) memoContent else memoContent + "\n\n"
                memoFile.appendText(text)
                AppLogger.d("✅ 备忘录已导入: ${memoFile.absolutePath}")
            }

            // 2. 恢复用户配置
            if (!prefsContent.isNullOrEmpty()) {
                val json = JSONObject(prefsContent)
                val editor = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE).edit()
                val keys = json.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val value = json.get(key)
                    when (value) {
                        is Boolean -> editor.putBoolean(key, value)
                        is Int -> editor.putInt(key, value)
                        is Long -> editor.putLong(key, value)
                        is Double -> editor.putFloat(key, value.toFloat())
                        else -> editor.putString(key, value.toString())
                    }
                }
                editor.apply()
                AppLogger.d("✅ 用户配置已导入")
            }

            true
        } catch (e: Exception) {
            AppLogger.e("数据导入失败: ${e.message}")
            false
        }
    }
}
