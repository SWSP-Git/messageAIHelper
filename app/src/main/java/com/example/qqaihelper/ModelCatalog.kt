package com.example.qqaihelper

/**
 * 可下载模型清单（数据源）。
 *
 * 设计目标：**可扩展**。
 * - 当前为内置（硬编码）清单；
 * - 未来可换成远程 manifest（如从 GitHub Raw 拉取 JSON），
 *   只需替换 [all] 的实现，调用方无需改动。
 *
 * ⚠️ 所有模型均托管于 **ModelScope 国内 CDN**，URL 形如：
 *   https://modelscope.cn/models/{namespace}/{repo}/resolve/master/{file}.zip
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
     * 返回全部可下载模型（按体积从小到大排序，便于用户按需选择）。
     */
    fun all(): List<ModelEntry> = builtin

    /** 按 id 查找 */
    fun find(id: String): ModelEntry? = builtin.firstOrNull { it.id == id }

    /** 人类可读体积 */
    fun humanSize(bytes: Long): String {
        val mb = bytes / 1024.0 / 1024.0
        return if (mb >= 1024) String.format("%.2f GB", mb / 1024.0) else String.format("%.1f MB", mb)
    }

    // ---- 内置清单（ModelScope 国内源） ----
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
            sizeBytes = 311_060_321L,
            sha256 = "9b3dc2058138ab52b13680b309aed2d9b5621cec60d39654ba6e326bb4bbb2b9",
        ),
        ModelEntry(
            id = "qwen3-0.6b-mnn",
            name = "Qwen3 0.6B",
            description = "Qwen3-0.6B 的 4bit 量化 MNN 模型，新一代架构，体积小、速度快。",
            url = msUrl("qwen3-0.6B-mnn", "qwen3-0.6b-mnn.zip"),
            sizeBytes = 454_471_917L,
            sha256 = "ebfae241f7af94b7c008785ac1d9d740a7491417ab3232551793dd92640617cc",
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
