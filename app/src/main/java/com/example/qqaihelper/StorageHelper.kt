package com.example.qqaihelper

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import androidx.core.content.ContextCompat
import java.io.File

/**
 * 存储路径管理工具。
 *
 * 核心目的：让用户的备忘录数据在 App 卸载重装后仍然保留。
 *
 * 策略（方案 D）：
 * - 优先使用外部公共目录 Download/messageAIHelper/
 *   - Android 11+ 需用户手动授予「所有文件访问权限」MANAGE_EXTERNAL_STORAGE
 *   - Android 10 及以下需 WRITE_EXTERNAL_STORAGE 运行时权限
 * - 若权限不可用，降级到 App 私有目录 filesDir（卸载会丢失）
 *
 * 首次从私有目录迁移到外部目录时，会自动把已有数据搬过去。
 *
 * ⚠️ 用户配置（API Key 等）仍然保存在私有 SharedPreferences 中，
 * 不上传到公共目录，避免敏感信息泄露。
 */
object StorageHelper {

    private const val FOLDER_NAME = "messageAIHelper"
    private const val MEMO_FILE_NAME = "memo_list.txt"

    /** 备忘录文件（根据权限自动选择路径） */
    fun getMemoFile(context: Context): File {
        if (hasExternalStorageAccess(context)) {
            val dir = getExternalBaseDir()
            if (!dir.exists()) dir.mkdirs()
            val external = File(dir, MEMO_FILE_NAME)
            // 首次使用外部目录时，若私有目录有数据，自动迁移
            migratePrivateToExternal(context, external)
            return external
        }
        return File(context.filesDir, MEMO_FILE_NAME)
    }

    /** 外部公共目录根路径 */
    fun getExternalBaseDir(): File =
        File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), FOLDER_NAME)

    /** 当前是否使用外部存储（用于 UI 提示） */
    fun isUsingExternalStorage(context: Context): Boolean = hasExternalStorageAccess(context)

    /**
     * 判断是否有外部存储的完全访问权限。
     *
     * - Android 11+：需要 MANAGE_EXTERNAL_STORAGE（用户手动在系统设置里授予）
     * - Android 10 及以下：需要 WRITE_EXTERNAL_STORAGE 运行时权限
     */
    private fun hasExternalStorageAccess(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                Environment.isExternalStorageManager()
            } catch (e: Exception) {
                false
            }
        } else {
            ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * 把私有目录里的历史数据迁移到外部目录。
     * 只在外部文件不存在、私有文件存在时执行一次。
     */
    private fun migratePrivateToExternal(context: Context, externalFile: File) {
        try {
            val privateFile = File(context.filesDir, MEMO_FILE_NAME)
            if (externalFile.exists()) return
            if (!privateFile.exists()) return

            externalFile.writeText(privateFile.readText())
            AppLogger.d("✅ 备忘录已迁移到外部目录: ${externalFile.absolutePath}")
        } catch (e: Exception) {
            AppLogger.e("备忘录迁移失败: ${e.message}")
        }
    }
}
