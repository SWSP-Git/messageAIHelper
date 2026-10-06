package com.example.qqaihelper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [MemoParser] 的单元测试（纯 JVM，无需真机）。
 *
 * 重点覆盖「数据零丢失」与「低重要性默认隐藏」两条核心约束，
 * 尤其是老数据（无「重要性」字段）的兼容行为。
 */
class MemoParserTest {

    // ---------- splitMemos ----------

    @Test
    fun splitMemos_twoMemos_separatedByBlankLine() {
        val content = "来源: QQ\n摘要: 第一条\n\n来源: 微信\n摘要: 第二条\n"

        val memos = MemoParser.splitMemos(content)

        assertEquals(2, memos.size)
        assertTrue(memos[0].contains("第一条"))
        assertTrue(memos[1].contains("第二条"))
    }

    @Test
    fun splitMemos_emptyContent_returnsEmptyList() {
        assertTrue(MemoParser.splitMemos("").isEmpty())
    }

    @Test
    fun splitMemos_onlyBlankLines_returnsEmptyList() {
        assertTrue(MemoParser.splitMemos("\n\n\n\n").isEmpty())
    }

    @Test
    fun splitMemos_trailingBlankLines_ignored() {
        val content = "来源: QQ\n摘要: 唯一一条\n\n\n"

        val memos = MemoParser.splitMemos(content)

        assertEquals(1, memos.size)
    }

    // ---------- extract ----------

    @Test
    fun extract_existingField_returnsTrimmedValue() {
        val lines = listOf("来源: QQ", "摘要:   前后有空格   ")

        assertEquals("QQ", MemoParser.extract(lines, "来源"))
        assertEquals("前后有空格", MemoParser.extract(lines, "摘要"))
    }

    @Test
    fun extract_missingField_returnsNull() {
        val lines = listOf("来源: QQ")

        assertNull(MemoParser.extract(lines, "重要性"))
    }

    @Test
    fun extract_prefixMustMatchWholeField() {
        // “来源” 不应误匹配 “来源App”
        val lines = listOf("来源App: xxx")

        assertNull(MemoParser.extract(lines, "来源"))
    }

    // ---------- importance（兼容性重点） ----------

    @Test
    fun importance_explicitValues_parsed() {
        assertEquals("高", MemoParser.importance(listOf("重要性: 高")))
        assertEquals("中", MemoParser.importance(listOf("重要性: 中")))
        assertEquals("低", MemoParser.importance(listOf("重要性: 低")))
    }

    @Test
    fun importance_missingField_defaultsToMedium() {
        // 老数据（v1.1.2 之前）没有重要性字段，必须默认为「中」
        val lines = listOf("来源: QQ", "摘要: 老数据")

        assertEquals("中", MemoParser.importance(lines))
    }

    @Test
    fun importance_blankValue_defaultsToMedium() {
        val lines = listOf("重要性:    ")

        assertEquals("中", MemoParser.importance(lines))
    }

    // ---------- isLowImportance ----------

    @Test
    fun isLowImportance_onlyLowReturnsTrue() {
        assertTrue(MemoParser.isLowImportance(listOf("重要性: 低")))
        assertFalse(MemoParser.isLowImportance(listOf("重要性: 高")))
        assertFalse(MemoParser.isLowImportance(listOf("重要性: 中")))
    }

    @Test
    fun isLowImportance_missingField_returnsFalse() {
        // 老数据不得被判为低
        assertFalse(MemoParser.isLowImportance(listOf("来源: QQ")))
    }

    // ---------- filter ----------

    @Test
    fun filter_showAll_keepsEverything() {
        val memos = listOf(
            "重要性: 高",
            "重要性: 中",
            "重要性: 低",
            "来源: QQ"
        )

        val result = MemoParser.filter(memos, showAll = true)

        assertEquals(4, result.size)
    }

    @Test
    fun filter_defaultMode_hidesOnlyLow() {
        val memos = listOf(
            "重要性: 高",
            "重要性: 中",
            "重要性: 低",
            "来源: QQ"
        )

        val result = MemoParser.filter(memos, showAll = false)

        assertEquals(3, result.size)
        assertFalse(result.any { it.contains("低") })
    }

    @Test
    fun filter_allLow_hidesAll() {
        val memos = listOf("重要性: 低", "重要性: 低")

        assertTrue(MemoParser.filter(memos, showAll = false).isEmpty())
    }

    @Test
    fun filter_legacyDataWithoutImportance_allVisible() {
        // 关键兼容场景：全部为老数据时，默认模式下应全部可见
        val memos = listOf(
            "来源: QQ\n摘要: 老数据A",
            "来源: 微信\n摘要: 老数据B"
        )

        val result = MemoParser.filter(memos, showAll = false)

        assertEquals(2, result.size)
    }
}
