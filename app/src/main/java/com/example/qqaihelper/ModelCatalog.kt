package com.example.qqaihelper

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * 可下载模型清单（数据源）。
 *
 * 三级来源（优先级从高到低）：
 * 1. **远程 manifest**（[MANIFEST_URL]，仓库根目录的 models.json）—— 可随时更新，**无需发版**
 * 2. **本地缓存**（拉取成功后落盘，离线可用）
 * 3. **内置清单**（[builtin]）—— 兜底，保证任何情况下都有可选项
 *
 * 调用方只需用 [all] 取列表、[refresh] 触发后台刷新。
 *
 * @property id          唯一标识（用于去重 / 记录）
 * @property name        展示名称
 * @property description 一句话说明
 * @property url         下载直链
 * @property sizeBytes   文件字节数（用于进度百分比）
 * @property sha256      文件 SHA-256（小写十六进制，用于完整性校验）
 */
data class ModelEntry(
    val id: String,
    val name: String,
    val description: String,
    val url: String,
    val sizeBytes: Long,
    val sha256: String,
)

object ModelCatalog {

    private const val TAG = "ModelCatalog"

    /**
     * 远程清单候选地址（按顺序尝试，任一成功即用）。
     * 国内直连 GitHub raw 常被墙，故 ModelScope 优先、镜像兜底。
     */
    private val MANIFEST_URLS = listOf(
        // 1) ModelScope 托管（国内直连稳定；由维护者上传 models.json 到该仓库）
        "https://modelscope.cn/models/mfxq2l/messageAIHelper/resolve/master/models.json",
        // 2) GitHub raw 镜像
        "https://ghproxy.net/https://raw.githubusercontent.com/SWSP-Git/messageAIHelper/master/models.json",
        // 3) GitHub raw 直连
        "https://raw.githubusercontent.com/SWSP-Git/messageAIHelper/master/models.json",
    )

    /** 缓存有效期（6 小时），未过期则跳过网络请求 */
    private const val CACHE_TTL_MS = 6L * 60 * 60 * 1000

    private const val CACHE_FILE = "model_manifest.json"
    private const val PREFS = "app_settings"
    private const val KEY_LAST_FETCH = "manifest_last_fetch"

    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36"

    /** 当前生效清单（缓存优先，未加载则用内置） */
    @Volatile
    private var cached: List<ModelEntry>? = null

    /** 最近一次刷新失败原因（供日志排查，可为 null） */
    @Volatile
    var lastError: String? = null
        private set

    /** 返回全部可下载模型（按体积从小到大排序） */
    fun all(): List<ModelEntry> = cached ?: builtin

    /** 按 id 查找 */
    fun find(id: String): ModelEntry? = all().firstOrNull { it.id == id }

    /** 人类可读体积 */
    fun humanSize(bytes: Long): String {
        val mb = bytes / 1024.0 / 1024.0
        return if (mb >= 1024) String.format("%.2f GB", mb / 1024.0) else String.format("%.1f MB", mb)
    }

    /**
     * 尝试从远程拉取最新清单。**必须在 IO 线程调用**，不会抛异常。
     *
     * @param force 忽略缓存有效期，强制走网络
     * @return 是否最终有可用清单（有缓存或拉取成功）
     */
    fun refresh(context: Context, force: Boolean = false): Boolean {
        // 1. 首次先加载本地缓存（离线也能用）
        if (cached == null) {
            loadCache(context)?.let { cached = it }
        }

        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val last = prefs.getLong(KEY_LAST_FETCH, 0L)
        val now = System.currentTimeMillis()

        // 2. 非强制且未过期 → 直接用缓存
        if (!force && cached != null && now - last < CACHE_TTL_MS) return true

        // 3. 依次尝试候选地址
        for (url in MANIFEST_URLS) {
            try {
                val text = httpGet(url)
                val list = parseManifest(text)
                if (list.isNotEmpty()) {
                    cached = list
                    lastError = null
                    saveCache(context, text)
                    prefs.edit().putLong(KEY_LAST_FETCH, now).apply()
                    Log.i(TAG, "清单已更新，共 " + list.size + " 个模型（来源: " + url.substringBefore("/resolve") + "）")
                    return true
                }
            } catch (t: Throwable) {
                lastError = t.message
                Log.w(TAG, "清单源失败: " + t.message)
            }
        }
        return cached != null
    }

    // ==================== 内部实现 ====================

    private fun httpGet(urlStr: String): String {
        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15000
            readTimeout = 20000
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("Accept", "application/json")
        }
        try {
            val code = conn.responseCode
            if (code != 200) throw IllegalStateException("HTTP " + code)
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    /** 解析清单 JSON：{ "models": [ {id,name,description,url,sizeBytes,sha256}, ... ] } */
    private fun parseManifest(text: String): List<ModelEntry> {
        val root = JSONObject(text)
        val arr: JSONArray = root.optJSONArray("models") ?: return emptyList()
        val out = ArrayList<ModelEntry>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val id = o.optString("id").trim()
            val url = o.optString("url").trim()
            val sha = o.optString("sha256").trim()
            if (id.isEmpty() || url.isEmpty()) continue
            out.add(
                ModelEntry(
                    id = id,
                    name = o.optString("name", id).ifBlank { id },
                    description = o.optString("description", ""),
                    url = url,
                    sizeBytes = o.optLong("sizeBytes", 0L),
                    sha256 = sha,
                )
            )
        }
        return out.sortedBy { it.sizeBytes }
    }

    private fun cacheFile(context: Context): File = File(context.filesDir, CACHE_FILE)

    private fun loadCache(context: Context): List<ModelEntry>? {
        return try {
            val f = cacheFile(context)
            if (!f.exists()) return null
            parseManifest(f.readText()).takeIf { it.isNotEmpty() }
        } catch (t: Throwable) {
            Log.w(TAG, "读取清单缓存失败: " + t.message)
            null
        }
    }

    private fun saveCache(context: Context, text: String) {
        try {
            cacheFile(context).writeText(text)
        } catch (t: Throwable) {
            Log.w(TAG, "写入清单缓存失败: " + t.message)
        }
    }

    // ---- 内置清单（远程/缓存均不可用时的兜底；ModelScope 国内源） ----
    private const val NS = "mfxq2l"

    private fun msUrl(repo: String, file: String) =
        "https://modelscope.cn/models/" + NS + "/" + repo + "/resolve/master/" + file

    private val builtin = listOf(
        ModelEntry(
            id = "qwen2.5-0.5b-mnn",
            name = "Qwen2.5 0.5B（轻量·推荐）",
            description = "体积小、速度快，解析准确度尚可，适合低配手机或追求响应速度的场景。",
            url = msUrl("qwen2.5-0.5B-mnn", "qwen2.5-0.5b-mnn.zip"),
            sizeBytes = 284_537_739L,
            sha256 = "3723fcfc3cd5379e494d7836e1d38efde472a484857eb1377070de6a4baedad4",
        ),
        ModelEntry(
            id = "minicpm4-0.5b-mnn",
            name = "MiniCPM4 0.5B",
            description = "MiniCPM4-0.5B 的 MNN 模型，轻量快速，适合低配手机。",
            url = msUrl("minicpm4-0.5B-mnn", "minicpm4-0.5b-mnn.zip"),
            sizeBytes = 311_060_063L,
            sha256 = "8dd289f7ac0c8ecd3f4e0b5be5d8e857b599c4da66c7b53a14a758f2af0098ae",
        ),
        ModelEntry(
            id = "qwen3-0.6b-mnn",
            name = "Qwen3 0.6B",
            description = "Qwen3-0.6B 的 4bit 量化 MNN 模型，新一代架构，体积小、速度快。",
            url = msUrl("qwen3-0.6B-mnn", "qwen3-0.6b-mnn.zip"),
            sizeBytes = 454_471_659L,
            sha256 = "e7cda3952cc42b3da8be629b4692282b8d6b4fb6033435230891b386399e1123",
        ),
        ModelEntry(
            id = "qwen2.5-1.5b-mnn",
            name = "Qwen2.5 1.5B（均衡·推荐）",
            description = "通用中文小模型，解析准确度与体积较均衡，适合大多数场景。",
            url = msUrl("qwen2.5-1.5B-mnn", "qwen2.5-1.5b-mnn.zip"),
            sizeBytes = 879_616_644L,
            sha256 = "0a1d3074acf5b6437d046b5f03cd57aeac6cad4cb16f09a0068a57ab62a2960a",
        ),
        ModelEntry(
            id = "deepseek-r1-1.5b-mnn",
            name = "DeepSeek-R1 1.5B",
            description = "DeepSeek-R1-Distill-Qwen-1.5B 的 MNN 模型，具备推理链能力。",
            url = msUrl("deepseek-r1-1.5B-mnn", "deepseek-r1-1.5b-mnn.zip"),
            sizeBytes = 1_020_641_698L,
            sha256 = "e101b56a184442058c0b6191eeba1eac066dd3bec12819ce7e9668b30b2df3cc",
        ),
        ModelEntry(
            id = "qwen2.5-3b-mnn",
            name = "Qwen2.5 3B（高质量）",
            description = "Qwen2.5-3B-Instruct 的 MNN 模型，能力更强，适合对解析质量要求高的场景（需较大内存）。",
            url = msUrl("qwen2.5-3B-mnn", "qwen2.5-3b-mnn.zip"),
            sizeBytes = 1_747_152_618L,
            sha256 = "3f821322ec5531f8d632bbf737f1deada53b3fd97bc82122cbbc86ba38df6447",
        ),
    )
}
