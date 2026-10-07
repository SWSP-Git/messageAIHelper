package com.example.qqaihelper

/**
 * 可下载模型清单（数据源）。
 *
 * 设计目标：**可扩展**。
 * - 当前为内置（硬编码）清单，仅一个模型；
 * - 未来可换成远程 manifest（如从 GitHub Raw 拉取 JSON），
 *   只需替换 [all] 的实现，调用方无需改动。
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

    /**
     * 返回全部可下载模型。
     *
     * 未来若改为远程清单，可在此处发起网络请求（挂起函数）并解析 JSON；
     * 为保持签名稳定，调用方应通过 [all] 获取列表。
     */
    fun all(): List<ModelEntry> = builtin

    /** 按 id 查找 */
    fun find(id: String): ModelEntry? = builtin.firstOrNull { it.id == id }

    /** 人类可读体积 */
    fun humanSize(bytes: Long): String {
        val mb = bytes / 1024.0 / 1024.0
        return if (mb >= 1024) String.format("%.2f GB", mb / 1024.0) else String.format("%.1f MB", mb)
    }

    // ---- 内置清单 ----
    private val builtin = listOf(
        ModelEntry(
            id = "qwen2.5-1.5b-mnn",
            name = "Qwen2.5 1.5B Instruct (MNN 4bit)",
            description = "通用中文小模型，约 839 MB，适合离线消息解析（国内源，速度快）",
            url = "https://modelscope.cn/models/mfxq2l/qwen2.5-1.5B-mnn/resolve/master/qwen2.5-1.5b-mnn.zip",
            sizeBytes = 879_616_644L,
            sha256 = "0a1d3074acf5b6437d046b5f03cd57aeac6cad4cb16f09a0068a57ab62a2960a",
        ),
    )
}
