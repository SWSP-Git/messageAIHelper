package com.example.qqaihelper

/**
 * 定时开关的时间窗口判断（纯逻辑，不依赖任何 Android API，便于 JVM 单元测试）。
 *
 * 支持跨零点区间（如 22:00–06:00）：此时只要"晚于起点"或"早于终点"即视为在窗口内。
 */
object ScheduleWindow {

    /** 默认起始时间（08:00） */
    const val DEFAULT_START = "08:00"

    /** 默认结束时间（22:00） */
    const val DEFAULT_END = "22:00"

    /**
     * 把 "HH:mm" 转为当日分钟数。
     *
     * @return 0..1439；格式非法时返回 null
     */
    fun timeToMinutes(time: String): Int? {
        val parts = time.trim().split(":")
        if (parts.size != 2) return null
        val hour = parts[0].toIntOrNull() ?: return null
        val minute = parts[1].toIntOrNull() ?: return null
        if (hour !in 0..23 || minute !in 0..59) return null
        return hour * 60 + minute
    }

    /**
     * 判断当前时间是否落在 [start, end] 窗口内。
     *
     * - 常规区间（start <= end）：now 在两者之间（含端点）
     * - 跨零点区间（start > end）：now >= start 或 now <= end
     * - 任一时间格式非法 → 返回 true（失败放行，避免误拦所有消息）
     *
     * @param now   "HH:mm" 格式的当前时间
     * @param start "HH:mm" 格式的起始时间
     * @param end   "HH:mm" 格式的结束时间
     */
    fun isWithin(now: String, start: String, end: String): Boolean {
        val nowMin = timeToMinutes(now) ?: return true
        val startMin = timeToMinutes(start) ?: return true
        val endMin = timeToMinutes(end) ?: return true

        return if (startMin <= endMin) {
            nowMin in startMin..endMin
        } else {
            nowMin >= startMin || nowMin <= endMin
        }
    }
}
