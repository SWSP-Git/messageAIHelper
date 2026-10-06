package com.example.qqaihelper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [AiReplyParser] 的单元测试（纯 JVM，无需真机）。
 *
 * 覆盖 AI 回复解析的关键路径与边界情况：
 * - 完整回复的字段提取
 * - 缺失字段的默认值（与历史行为保持一致）
 * - 待办 / 待办时间的匹配与不匹配
 * - 备忘类型的判定
 * - 空输入与格式异常
 */
class AiReplyParserTest {

    @Test
    fun parse_fullReply_extractsAllFields() {
        val reply = """
            【发送人】张三
            【类型】日程
            【重要性】高
            【摘要】明天下午三点开会
            【关键信息】无
            【智能建议】建议提前10分钟到会议室
            【待办】与客户开会
            【待办时间】2026-10-07 15:00
        """.trimIndent()

        val parsed = AiReplyParser.parse(reply)

        assertEquals("日程", parsed.type)
        assertEquals("高", parsed.importance)
        assertEquals("明天下午三点开会", parsed.summary)
        assertEquals("无", parsed.keyInfo)
        assertEquals("建议提前10分钟到会议室", parsed.suggestion)
        assertEquals("与客户开会", parsed.todoText)
        assertEquals("2026-10-07 15:00", parsed.todoTime)
        assertFalse(parsed.isMemo)
    }

    @Test
    fun parse_memoType_isRecognizedAsMemo() {
        val reply = """
            【类型】备忘
            【重要性】中
            【摘要】取件码通知
            【关键信息】5621
        """.trimIndent()

        val parsed = AiReplyParser.parse(reply)

        assertTrue(parsed.isMemo)
        assertEquals("备忘", parsed.type)
        assertEquals("5621", parsed.keyInfo)
        assertNull(parsed.todoText)
        assertNull(parsed.todoTime)
    }

    @Test
    fun parse_missingFields_fallBackToDefaults() {
        val parsed = AiReplyParser.parse("这是一条没有任何标记的文本")

        assertEquals("日程", parsed.type)
        assertEquals("无摘要", parsed.summary)
        assertEquals("无", parsed.keyInfo)
        assertEquals("中", parsed.importance)
        assertEquals("无", parsed.suggestion)
        assertNull(parsed.todoText)
        assertNull(parsed.todoTime)
        assertFalse(parsed.isMemo)
    }

    @Test
    fun parse_emptyInput_returnsDefaults() {
        val parsed = AiReplyParser.parse("")

        assertEquals("日程", parsed.type)
        assertEquals("无摘要", parsed.summary)
        assertEquals("中", parsed.importance)
        assertNull(parsed.todoText)
        assertNull(parsed.todoTime)
    }

    @Test
    fun parse_todoTimeWithoutTodo_extractsTimeOnly() {
        val reply = """
            【类型】日程
            【待办时间】2026-10-07 15:00
        """.trimIndent()

        val parsed = AiReplyParser.parse(reply)

        assertNull(parsed.todoText)
        assertEquals("2026-10-07 15:00", parsed.todoTime)
    }

    @Test
    fun parse_malformedTime_returnsNullTodoTime() {
        // 月份未补零、缺前导零，均不符合 yyyy-MM-dd HH:mm
        val reply = """
            【待办】开会
            【待办时间】2026-1-5 9:00
        """.trimIndent()

        val parsed = AiReplyParser.parse(reply)

        assertEquals("开会", parsed.todoText)
        assertNull(parsed.todoTime)
    }

    @Test
    fun parse_todoTimeWithChineseWordWu_returnsLiteralValue() {
        // AI 按约定填“无”时，应原样返回字符串“无”，由调用方判断
        val reply = """
            【待办】无
            【待办时间】无
        """.trimIndent()

        val parsed = AiReplyParser.parse(reply)

        assertEquals("无", parsed.todoText)
        assertNull(parsed.todoTime)
    }

    @Test
    fun parse_extraWhitespace_isTrimmed() {
        val reply = "【摘要】   前后有空格   \n【重要性】  高  "

        val parsed = AiReplyParser.parse(reply)

        assertEquals("前后有空格", parsed.summary)
        assertEquals("高", parsed.importance)
    }

    @Test
    fun parse_keyInfo_preservesOriginalData() {
        val reply = """
            【类型】备忘
            【关键信息】取件码 5621，金额 ¥128.00
        """.trimIndent()

        val parsed = AiReplyParser.parse(reply)

        assertEquals("取件码 5621，金额 ¥128.00", parsed.keyInfo)
    }

    @Test
    fun parse_fieldOrderDoesNotMatter() {
        val reply = """
            【待办时间】2026-12-31 23:59
            【待办】跨年倒计时
            【类型】日程
        """.trimIndent()

        val parsed = AiReplyParser.parse(reply)

        assertEquals("跨年倒计时", parsed.todoText)
        assertEquals("2026-12-31 23:59", parsed.todoTime)
        assertEquals("日程", parsed.type)
    }
}
