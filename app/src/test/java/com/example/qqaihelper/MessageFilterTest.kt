package com.example.qqaihelper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [MessageFilter] 的单元测试（纯 JVM，无需真机）。
 *
 * 重点覆盖 v1.1.0 曾出现过的"关键词过滤完全失效"历史 bug 场景。
 */
class MessageFilterTest {

    // ---------- 包名 ↔ 显示名映射 ----------

    @Test
    fun resolveSourceApp_knownPackages_mapped() {
        assertEquals("QQ", MessageFilter.resolveSourceApp("com.tencent.mobileqq"))
        assertEquals("微信", MessageFilter.resolveSourceApp("com.tencent.mm"))
        assertEquals("钉钉", MessageFilter.resolveSourceApp("com.alibaba.android.rimet"))
        assertEquals("企业微信", MessageFilter.resolveSourceApp("com.tencent.wework"))
    }

    @Test
    fun resolveSourceApp_unknownPackage_returnsOther() {
        assertEquals("其他应用", MessageFilter.resolveSourceApp("com.unknown.app"))
    }

    @Test
    fun resolvePackageName_isInverseOfResolveSourceApp() {
        assertEquals("com.tencent.mobileqq", MessageFilter.resolvePackageName("QQ"))
        assertEquals("com.tencent.mm", MessageFilter.resolvePackageName("微信"))
        assertNull(MessageFilter.resolvePackageName("其他应用"))
    }

    // ---------- 过滤：空规则 ----------

    @Test
    fun shouldProcess_emptyRules_alwaysTrue() {
        assertTrue(MessageFilter.shouldProcess("QQ", "任意内容", ""))
    }

    @Test
    fun shouldProcess_unknownSourceApp_alwaysTrue() {
        // “其他应用”无法映射到包名，不应被误杀
        val rules = "com.tencent.mobileqq|开会|OR"
        assertTrue(MessageFilter.shouldProcess("其他应用", "随便什么内容", rules))
    }

    // ---------- 过滤：OR 逻辑 ----------

    @Test
    fun shouldProcess_orLogic_matchesAnyKeyword() {
        val rules = "com.tencent.mobileqq|开会,会议|OR"

        assertTrue(MessageFilter.shouldProcess("QQ", "明天开会", rules))
        assertTrue(MessageFilter.shouldProcess("QQ", "有个会议要参加", rules))
        assertFalse(MessageFilter.shouldProcess("QQ", "今天天气不错", rules))
    }

    // ---------- 过滤：AND 逻辑 ----------

    @Test
    fun shouldProcess_andLogic_requiresAllKeywords() {
        val rules = "com.tencent.mm|工资,到账|AND"

        assertTrue(MessageFilter.shouldProcess("微信", "您的工资已到账", rules))
        assertFalse(MessageFilter.shouldProcess("微信", "工资发放通知", rules))
        assertFalse(MessageFilter.shouldProcess("微信", "已到账 100 元", rules))
    }

    // ---------- 过滤：多应用规则隔离 ----------

    @Test
    fun shouldProcess_rulesArePerPackage() {
        val rules = """
            com.tencent.mobileqq|开会|OR
            com.tencent.mm|到账|OR
        """.trimIndent()

        assertTrue(MessageFilter.shouldProcess("QQ", "明天开会", rules))
        // 微信的消息不应被 QQ 的规则匹配
        assertFalse(MessageFilter.shouldProcess("微信", "明天开会", rules))
        assertTrue(MessageFilter.shouldProcess("微信", "已到账", rules))
    }

    @Test
    fun shouldProcess_noRuleForPackage_passesThrough() {
        val rules = "com.tencent.mobileqq|开会|OR"

        // 钉钉没有配置规则 → 放行
        assertTrue(MessageFilter.shouldProcess("钉钉", "任意内容", rules))
    }

    // ---------- 过滤：格式容错 ----------

    @Test
    fun shouldProcess_malformedLines_areIgnored() {
        val rules = """
            这是一行没有分隔符的垃圾数据
            com.tencent.mobileqq|开会|OR
        """.trimIndent()

        assertTrue(MessageFilter.shouldProcess("QQ", "明天开会", rules))
        assertFalse(MessageFilter.shouldProcess("QQ", "无关消息", rules))
    }

    @Test
    fun shouldProcess_logicIsCaseInsensitive() {
        val rules = "com.tencent.mobileqq|开会|or"

        assertTrue(MessageFilter.shouldProcess("QQ", "明天开会", rules))
    }

    @Test
    fun shouldProcess_keywordsAreTrimmed() {
        val rules = "com.tencent.mobileqq| 开会 , 会议 |OR"

        assertTrue(MessageFilter.shouldProcess("QQ", "明天开会", rules))
    }

    @Test
    fun shouldProcess_emptyMessage_withOrRule_returnsFalse() {
        val rules = "com.tencent.mobileqq|开会|OR"

        assertFalse(MessageFilter.shouldProcess("QQ", "", rules))
    }
}
