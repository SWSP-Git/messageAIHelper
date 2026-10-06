package com.example.qqaihelper

/**
 * 备忘录文本解析器（纯逻辑，不依赖任何 Android API，便于 JVM 单元测试）。
 *
 * 数据格式：多条备忘以**空行**分隔，每条备忘为多行 \`键: 值\` 结构：
 * \`\`\`
 * 来源: QQ
 * 时间: 2026-10-06 12:00:00
 * 重要性: 高 | 中 | 低
 * 摘要: xxx
 * 关键信息: xxx
 * 智能建议: xxx
 * 内容: xxx
 * \`\`\`
 *
 * ⚠️ 字段前缀为**固定中文**，不随 UI 语言变化（UI 标签由 strings.xml 翻译）。
 */
object MemoParser {

    // ---- 固定字段前缀 ----
    const val FIELD_SOURCE = "来源"
    const val FIELD_TIME = "时间"
    const val FIELD_IMPORTANCE = "重要性"
    const val FIELD_SUMMARY = "摘要"
    const val FIELD_KEY_INFO = "关键信息"
    const val FIELD_SUGGESTION = "智能建议"
    const val FIELD_CONTENT = "内容"

    // ---- 重要性取值 ----
    const val IMPORTANCE_HIGH = "高"
    const val IMPORTANCE_MEDIUM = "中"
    const val IMPORTANCE_LOW = "低"

    /** 多条备忘之间的分隔符（空行） */
    private const val MEMO_SEPARATOR = "\n\n"

    /**
     * 把备忘录文件内容切分为单条备忘列表。
     * 忽略空条目，保留原始文本（不做 trim，避免破坏内容格式）。
     */
    fun splitMemos(fileContent: String): List<String> =
        fileContent.split(MEMO_SEPARATOR).filter { it.isNotBlank() }

    /**
     * 从已按行切分的备忘中提取指定前缀字段的值。
     *
     * @param lines  备忘文本按 "\n" 切分后的行列表
     * @param prefix 字段前缀（不含冒号）
     * @return 冒号后的去空白文本；字段不存在时返回 null
     */
    fun extract(lines: List<String>, prefix: String): String? =
        lines.find { it.startsWith("$prefix:") }
            ?.substringAfter(":")
            ?.trim()

    /**
     * 读取一条备忘的重要性。
     *
     * 兼容策略：字段缺失或为空时返回「中」，保证老数据（v1.1.2 之前无
     * 「重要性」字段）升级后不会被误判为「低」而隐藏。
     *
     * @param lines 备忘文本按 "\n" 切分后的行列表
     */
    fun importance(lines: List<String>): String =
        extract(lines, FIELD_IMPORTANCE)?.ifBlank { null } ?: IMPORTANCE_MEDIUM

    /** 判断一条备忘是否为「低」重要性（字段缺失视为「中」，返回 false）。 */
    fun isLowImportance(lines: List<String>): Boolean =
        importance(lines) == IMPORTANCE_LOW

    /**
     * 按显示模式过滤备忘列表。
     *
     * @param memos   全部备忘（原始文本）
     * @param showAll true = 显示全部；false = 隐藏「低」重要性
     */
    fun filter(memos: List<String>, showAll: Boolean): List<String> =
        if (showAll) memos else memos.filter { !isLowImportance(it.split("\n")) }
}
