package com.example.qqaihelper.localllm

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.ZipInputStream

/**
 * 本地模型插件管理器。
 *
 * 插件 = 一个 .zip 包，用户从文件选择器导入。解压后目录结构：
 *   filesDir/plugins/<plugin-id>/
 *     ├── plugin.json        # 清单（id/name/version/abi/engine/entry）
 *     └── model/             # 模型文件（由 entry 指向 config.json）
 *         ├── config.json
 *         ├── llm_config.json
 *         ├── llm.mnn
 *         ├── llm.mnn.json
 *         ├── llm.mnn.weight
 *         └── tokenizer.txt
 *
 * 设计目标：
 * - APK 本体不再内置任何模型权重（保持几 MB 体积）；
 * - 模型以插件形式热插拔：导入 / 删除 / 设为当前，互不影响；
 * - 未来可以放多个不同模型插件，用户在 AiOptionsActivity 里切换。
 */
object PluginManager {

    private const val TAG = "LocalLlm"

    /** 与主 App 共用同一份 SharedPreferences，键名前缀与 AiOptionsActivity 保持一致 */
    const val PREFS_NAME = "app_settings"
    const val KEY_CURRENT_PLUGIN = "local_model_plugin_id"

    const val PLUGIN_JSON = "plugin.json"
    const val DEFAULT_ENTRY = "model/config.json"

    /** 复制缓冲区 4MB，解压 800MB 权重更快 */
    private const val BUFFER = 1 shl 22

    data class PluginInfo(
        val id: String,
        val name: String,
        val version: String,
        val abi: String,
        val engine: String,
        val entry: String,
        val dir: File,
    ) {
        /** entry 指向的 config.json 绝对路径 */
        val configFile: File get() = File(dir, entry)

        /** 估算占用空间（字节） */
        val sizeBytes: Long
            get() = dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    fun pluginsRoot(context: Context): File =
        File(context.filesDir, "plugins").apply { if (!exists()) mkdirs() }

    /** 列出所有已安装插件（按名称排序） */
    fun list(context: Context): List<PluginInfo> {
        val root = pluginsRoot(context)
        return root.listFiles { f -> f.isDirectory && !f.name.startsWith(".") }
            ?.mapNotNull { readMeta(it) }
            ?.sortedBy { it.name }
            ?: emptyList()
    }

    /** 当前选中的插件（不存在或文件缺失时返回 null） */
    fun current(context: Context): PluginInfo? {
        val id = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_CURRENT_PLUGIN, null) ?: return null
        val dir = File(pluginsRoot(context), id)
        val meta = readMeta(dir) ?: return null
        return if (meta.configFile.exists()) meta else null
    }

    fun setCurrent(context: Context, id: String?) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_CURRENT_PLUGIN, id)
            .apply()
    }

    /** 解析插件目录中的 plugin.json */
    fun readMeta(dir: File): PluginInfo? {
        val pj = File(dir, PLUGIN_JSON)
        if (!pj.exists()) return null
        return try {
            val o = JSONObject(pj.readText())
            PluginInfo(
                id = o.optString("id", dir.name).ifBlank { dir.name },
                name = o.optString("name", dir.name).ifBlank { dir.name },
                version = o.optString("version", "?").ifBlank { "?" },
                abi = o.optString("abi", "arm64-v8a").ifBlank { "arm64-v8a" },
                engine = o.optString("engine", "").ifBlank { "" },
                entry = o.optString("entry", DEFAULT_ENTRY).ifBlank { DEFAULT_ENTRY },
                dir = dir,
            )
        } catch (t: Throwable) {
            Log.e(TAG, "解析 plugin.json 失败: ${dir.name} / ${t.message}")
            null
        }
    }

    /**
     * 从 zip 流安装插件（耗时，IO 线程调用）。
     *
     * @param zipInput  来自 ContentResolver.openInputStream(uri)
     * @param zipSizeBytes 原始 zip 大小（-1 未知），仅用于进度百分比
     * @param onProgress (当前条目名, 已写入字节, 总字节或 -1)
     * @return 安装好的插件；失败返回 null
     */
    fun installFromZip(
        context: Context,
        zipInput: InputStream,
        zipSizeBytes: Long,
        onProgress: (String, Long, Long) -> Unit = { _, _, _ -> },
    ): PluginInfo? {
        val root = pluginsRoot(context)
        val tmp = File(root, ".tmp_${System.currentTimeMillis()}")
        tmp.deleteRecursively()
        if (!tmp.mkdirs()) {
            Log.e(TAG, "无法创建临时目录: ${tmp.absolutePath}")
            return null
        }

        return try {
            var written = 0L
            var entryCount = 0

            ZipInputStream(zipInput).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    entryCount++
                    val name = entry.name
                    val out = File(tmp, name)

                    // 防路径穿越
                    if (!out.canonicalPath.startsWith(tmp.canonicalPath + File.separator)
                        && out.canonicalPath != tmp.canonicalPath) {
                        Log.w(TAG, "跳过可疑 zip 条目: $name")
                        zis.closeEntry()
                        entry = zis.nextEntry
                        continue
                    }

                    if (entry.isDirectory) {
                        out.mkdirs()
                    } else {
                        out.parentFile?.mkdirs()
                        FileOutputStream(out).use { fos ->
                            val buf = ByteArray(BUFFER)
                            while (true) {
                                val n = zis.read(buf)
                                if (n <= 0) break
                                fos.write(buf, 0, n)
                                written += n
                                onProgress(name, written, zipSizeBytes)
                            }
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }

            if (entryCount == 0) {
                Log.e(TAG, "zip 为空")
                tmp.deleteRecursively()
                return null
            }

            // 校验 plugin.json
            val meta = readMeta(tmp)
            if (meta == null) {
                Log.e(TAG, "zip 中缺少或损坏 plugin.json")
                tmp.deleteRecursively()
                return null
            }
            if (!meta.configFile.exists()) {
                Log.e(TAG, "entry 指向的文件不存在: ${meta.entry}")
                tmp.deleteRecursively()
                return null
            }

            // 移动（重命名优先，跨卷回退到复制）
            val dest = File(root, meta.id)
            if (dest.exists()) dest.deleteRecursively()
            if (!tmp.renameTo(dest)) {
                tmp.copyRecursively(dest, overwrite = true)
                tmp.deleteRecursively()
            }

            val finalMeta = readMeta(dest) ?: run {
                dest.deleteRecursively()
                null
            } ?: return null

            onProgress("完成", written, zipSizeBytes)
            Log.d(TAG, "插件安装成功: ${finalMeta.id} (${finalMeta.name})")
            finalMeta
        } catch (t: Throwable) {
            Log.e(TAG, "安装插件失败: ${t.message}", t)
            tmp.deleteRecursively()
            null
        }
    }

    // ==================== 自动搜寻 ====================

    /** 扫描结果：手机里找到的可用插件包（尚未安装） */
    data class FoundPlugin(
        val zipFile: File,
        val id: String,
        val name: String,
        val version: String,
        val abi: String,
    ) {
        val sizeBytes: Long get() = zipFile.length()
    }

    /**
     * 扫描手机常见目录，查找可作为插件安装的 .zip 包。
     *
     * 判定标准：zip 内含**有效的 plugin.json**（即插件的"特有标签"）。
     * 仅读取 zip 中央目录与 plugin.json，不解压整个包，因此速度快。
     *
     * 扫描范围：Download / Documents（递归，跳过 Android 子目录）+ 内部存储根目录（仅顶层）。
     *
     * @param maxDepth 递归深度上限，避免过深遍历
     * @param isCancelled 每次处理文件前调用；返回 true 时中断扫描（用于「取消搜寻」）
     * @return 找到的候选插件列表（按名称排序）
     */
    fun scanForPlugins(maxDepth: Int = 4, isCancelled: () -> Boolean = { false }): List<FoundPlugin> {
        val root = android.os.Environment.getExternalStorageDirectory() ?: return emptyList()
        val result = mutableListOf<FoundPlugin>()
        val seen = mutableSetOf<String>()

        fun handleZip(file: File): Boolean {
            if (isCancelled()) return false
            if (!seen.add(file.absolutePath)) return true
            readPluginMetaFromZip(file)?.let { result.add(it) }
            return true
        }

        // Download / Documents：递归扫描，跳过 Android/data、Android/obb
        outer@ for (name in listOf("Download", "Documents")) {
            val dir = File(root, name)
            if (!dir.isDirectory) continue
            val files = dir.walkTopDown()
                .maxDepth(maxDepth)
                .onEnter { it.name != "Android" }
                .filter { it.isFile && it.extension.equals("zip", ignoreCase = true) }
            for (f in files) {
                if (!handleZip(f)) break@outer
            }
        }

        // 内部存储根目录：仅顶层文件
        if (!isCancelled()) {
            root.listFiles { f -> f.isFile && f.extension.equals("zip", ignoreCase = true) }
                ?.forEach { f -> if (isCancelled()) return@forEach else handleZip(f) }
        }

        return result.sortedBy { it.name }
    }

    /** 仅读 zip 中央目录与 plugin.json 判断是否为有效插件包；无效返回 null */
    private fun readPluginMetaFromZip(zip: File): FoundPlugin? {
        return try {
            java.util.zip.ZipFile(zip).use { zf ->
                val entry = zf.getEntry(PLUGIN_JSON) ?: return null
                val text = zf.getInputStream(entry).bufferedReader().use { it.readText() }
                val o = JSONObject(text)
                val id = o.optString("id", "").ifBlank { return null }
                FoundPlugin(
                    zipFile = zip,
                    id = id,
                    name = o.optString("name", id).ifBlank { id },
                    version = o.optString("version", "?").ifBlank { "?" },
                    abi = o.optString("abi", "arm64-v8a").ifBlank { "arm64-v8a" },
                )
            }
        } catch (t: Throwable) {
            Log.w(TAG, "读取插件包失败: " + zip.name + " / " + t.message)
            null
        }
    }

    /** 从本地文件安装插件（便捷封装，内部复用 installFromZip） */
    fun installFromZipFile(
        context: Context,
        zip: File,
        onProgress: (String, Long, Long) -> Unit = { _, _, _ -> },
    ): PluginInfo? {
        if (!zip.isFile) return null
        return try {
            zip.inputStream().use { input ->
                installFromZip(context, input, zip.length(), onProgress)
            }
        } catch (t: Throwable) {
            Log.e(TAG, "从文件安装插件失败: " + t.message, t)
            null
        }
    }

    /** 删除插件（若为当前插件会同时清空 current） */
    fun delete(context: Context, id: String): Boolean {
        val dir = File(pluginsRoot(context), id)
        val ok = dir.deleteRecursively()
        if (context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_CURRENT_PLUGIN, null) == id) {
            setCurrent(context, null)
        }
        return ok
    }

    /** 人类可读的体积 */
    fun humanSize(bytes: Long): String {
        val mb = bytes / 1024.0 / 1024.0
        return if (mb >= 1024) String.format("%.2f GB", mb / 1024.0)
        else String.format("%.1f MB", mb)
    }
}
