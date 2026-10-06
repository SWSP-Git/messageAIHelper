package com.example.qqaihelper

import java.util.regex.Pattern

/**
 * AI 回复解析器（纯逻辑，不依赖任何 Android API，便于 JVM 单元测试）。
 *
 * 负责把 AI 返回的多行文本解析为结构化字段。AI 约定的回复格式如下：
 * ```
 * 【发送人】xxx
 * 【类型】日程 或 备忘
 * 【重要性】高/中/低
 * 【摘要】一句话概括
 * 【关键信息】xxx（无则填"无"）
 * 【智能建议】xxx（无则填"无"）
 * 【待办】xxx
 * 【待办时间】YYYY-MM-DD HH:MM 或 无
 * ```
 *
 * 缺失字段采用与历史行为一致的默认值。
 */
object AiReplyParser {

    // ---- 预编译正则（避免每次解析都重新编译） ----
    //
    // 字段结束边界 (?=\n|【|$)：遇到「换行」「下一个【字段】标记」或「字符串结尾」即停止。
    // 之所以额外用「【」作边界，是为了兼容 AI 未按格式换行、把多个字段挤在同一行的情况
    // （例如 "【摘要】开会 【重要性】高"）。若只用 (?=\n|$)，单行输入下 .*? 会一路吃到
    // 行尾，导致字段互相污染（摘要捕获到 "开会 【重要性】高"）。
    private val SENDER_PATTERN = Pattern.compile("【发送人】(.*?)(?=\\n|【|$)")
    private val TYPE_PATTERN = Pattern.compile("【类型】(.*?)(?=\\n|【|$)")
    private val SUMMARY_PATTERN = Pattern.compile("【摘要】(.*?)(?=\\n|【|$)")
    private val KEY_INFO_PATTERN = Pattern.compile("【关键信息】(.*?)(?=\\n|【|$)")
    private val IMPORTANCE_PATTERN = Pattern.compile("【重要性】(.*?)(?=\\n|【|$)")
    private val SUGGESTION_PATTERN = Pattern.compile("【智能建议】(.*?)(?=\\n|【|$)")
    private val TODO_PATTERN = Pattern.compile("【待办】(.*?)(?=\\n|【|$)")
    private val TODO_TIME_PATTERN = Pattern.compile("【待办时间】(\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2})")

    /**
     * AI 回复的结构化解析结果。
     *
     * @property sender     发送人（AI 从消息标题/内容中提取；未匹配到为 null）
     * @property type       消息类型，"日程" 或 "备忘"（缺省为 "日程"）
     * @property summary    摘要（缺省为 "无摘要"）
     * @property keyInfo    关键信息（缺省为 "无"）
     * @property importance 重要性：高/中/低（缺省为 "中"）
     * @property suggestion 智能建议（缺省为 "无"）
     * @property todoText   待办文本，未匹配到为 null
     * @property todoTime   待办时间（yyyy-MM-dd HH:mm），未匹配到为 null
     */
    data class ParsedReply(
        val sender: String?,
        val type: String,
        val summary: String,
        val keyInfo: String,
        val importance: String,
        val suggestion: String,
        val todoText: String?,
        val todoTime: String?
    ) {
        /** 是否为备忘类型 */
        val isMemo: Boolean get() = type == "备忘"

        /** 发送人是否有效（非空且不为占位符"无"） */
        val hasSender: Boolean get() = !sender.isNullOrBlank() && sender != "无"
    }

    /**
     * 解析 AI 回复文本。
     *
     * @param aiReply AI 返回的原始文本
     * @return 结构化解析结果
     */
    fun parse(aiReply: String): ParsedReply {
        return ParsedReply(
            sender = matchFirst(SENDER_PATTERN, aiReply),
            type = matchFirst(TYPE_PATTERN, aiReply) ?: "日程",
            summary = matchFirst(SUMMARY_PATTERN, aiReply) ?: "无摘要",
            keyInfo = matchFirst(KEY_INFO_PATTERN, aiReply) ?: "无",
            importance = matchFirst(IMPORTANCE_PATTERN, aiReply) ?: "中",
            suggestion = matchFirst(SUGGESTION_PATTERN, aiReply) ?: "无",
            todoText = matchFirst(TODO_PATTERN, aiReply),
            todoTime = matchFirst(TODO_TIME_PATTERN, aiReply)
        )
    }

    /** 返回第一个匹配组的去空白文本；未匹配到返回 null。 */
    private fun matchFirst(pattern: Pattern, text: String): String? {
        val matcher = pattern.matcher(text)
        return if (matcher.find()) (matcher.group(1) ?: "").trim() else null
    }
}
