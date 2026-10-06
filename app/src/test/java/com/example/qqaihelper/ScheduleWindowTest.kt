package com.example.qqaihelper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [ScheduleWindow] 的单元测试（纯 JVM，无需真机）。
 *
 * 重点覆盖跨零点区间这一经典易错点。
 */
class ScheduleWindowTest {

    // ---------- timeToMinutes ----------

    @Test
    fun timeToMinutes_validInput() {
        assertEquals(0, ScheduleWindow.timeToMinutes("00:00"))
        assertEquals(480, ScheduleWindow.timeToMinutes("08:00"))
        assertEquals(1439, ScheduleWindow.timeToMinutes("23:59"))
    }

    @Test
    fun timeToMinutes_trimsWhitespace() {
        assertEquals(480, ScheduleWindow.timeToMinutes(" 08:00 "))
    }

    @Test
    fun timeToMinutes_invalidInput_returnsNull() {
        assertNull(ScheduleWindow.timeToMinutes(""))
        assertNull(ScheduleWindow.timeToMinutes("08"))
        assertNull(ScheduleWindow.timeToMinutes("08:00:00"))
        assertNull(ScheduleWindow.timeToMinutes("24:00"))
        assertNull(ScheduleWindow.timeToMinutes("08:60"))
        assertNull(ScheduleWindow.timeToMinutes("aa:bb"))
    }

    // ---------- 常规区间 ----------

    @Test
    fun isWithin_normalRange_insideAndOutside() {
        // 08:00 - 22:00
        assertTrue(ScheduleWindow.isWithin("12:00", "08:00", "22:00"))
        assertTrue(ScheduleWindow.isWithin("08:00", "08:00", "22:00")) // 端点含
        assertTrue(ScheduleWindow.isWithin("22:00", "08:00", "22:00")) // 端点含
        assertFalse(ScheduleWindow.isWithin("07:59", "08:00", "22:00"))
        assertFalse(ScheduleWindow.isWithin("22:01", "08:00", "22:00"))
    }

    @Test
    fun isWithin_sameStartAndEnd_onlyThatMinute() {
        assertTrue(ScheduleWindow.isWithin("08:00", "08:00", "08:00"))
        assertFalse(ScheduleWindow.isWithin("08:01", "08:00", "08:00"))
    }

    // ---------- 跨零点区间（重点） ----------

    @Test
    fun isWithin_crossMidnightRange_insideAndOutside() {
        // 22:00 - 06:00
        assertTrue(ScheduleWindow.isWithin("23:00", "22:00", "06:00"))
        assertTrue(ScheduleWindow.isWithin("02:00", "22:00", "06:00"))
        assertTrue(ScheduleWindow.isWithin("22:00", "22:00", "06:00")) // 端点
        assertTrue(ScheduleWindow.isWithin("06:00", "22:00", "06:00")) // 端点
        assertFalse(ScheduleWindow.isWithin("12:00", "22:00", "06:00"))
        assertFalse(ScheduleWindow.isWithin("21:59", "22:00", "06:00"))
        assertFalse(ScheduleWindow.isWithin("06:01", "22:00", "06:00"))
    }

    // ---------- 容错 ----------

    @Test
    fun isWithin_invalidTime_failsOpen() {
        // 格式非法时应放行（返回 true），避免误拦所有消息
        assertTrue(ScheduleWindow.isWithin("bad", "08:00", "22:00"))
        assertTrue(ScheduleWindow.isWithin("12:00", "bad", "22:00"))
        assertTrue(ScheduleWindow.isWithin("12:00", "08:00", "bad"))
    }

    @Test
    fun defaults_areExpectedValues() {
        assertEquals("08:00", ScheduleWindow.DEFAULT_START)
        assertEquals("22:00", ScheduleWindow.DEFAULT_END)
    }
}
