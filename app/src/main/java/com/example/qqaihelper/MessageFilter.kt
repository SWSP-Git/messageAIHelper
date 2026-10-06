package com.example.qqaihelper

/**
 * 消息过滤与来源映射（纯逻辑，不依赖任何 Android API，便于 JVM 单元测试）。
 *
 * 负责两件事：
 * 1. 通知包名 ↔ 应用显示名的双向映射；
 * 2. 按用户配置的过滤规则（AND / OR）判断消息是否应被处理。
 */
object MessageFilter {

    // ---- 应用包名 ↔ 显示名 ----
    const val PKG_QQ = "com.tencent.mobileqq"
    const val PKG_WECHAT = "com.tencent.mm"
    const val PKG_DINGTALK = "com.alibaba.android.rimet"
    const val PKG_WEWORK = "com.tencent.wework"

    /**
     * 包名 → 显示名（用于日志与备忘录来源字段）。
     * 未识别的包名返回 [UNKNOWN_APP]。
     */
    fun resolveSourceApp(packageName: String): String = when (packageName) {
        PKG_QQ -> "QQ"
        PKG_WECHAT -> "微信"
        PKG_DINGTALK -> "钉钉"
        PKG_WEWORK -> "企业微信"
        else -> UNKNOWN_APP
    }

    /** 显示名 → 包名；未识别的显示名返回 null。 */
    fun resolvePackageName(sourceApp: String): String? = when (sourceApp) {
        "QQ" -> PKG_QQ
        "微信" -> PKG_WECHAT
        "钉钉" -> PKG_DINGTALK
        "企业微信" -> PKG_WEWORK
        else -> null
    }

    const val UNKNOWN_APP = "其他应用"

    /**
     * 判断消息是否通过关键词过滤。
     *
     * 规则格式（每行一条，`包名|关键词1,关键词2|AND或OR`）：
     * ```
     * com.tencent.mobileqq|开会,会议|OR
     * com.tencent.mm|工资,到账|AND
     * ```
     *
     * 行为约定：
     * - 规则为空 → 全部通过；
     * - 来源应用无法映射到包名 → 通过（不误杀"其他应用"）；
     * - 命中首条匹配该包名的规则即返回其判定结果；
     * - 无任何规则匹配该包名 → 通过。
     *
     * @param sourceApp   消息来源显示名（如 "QQ"）
     * @param messageText 待过滤的完整消息文本
     * @param rulesString 原始规则文本（多行）
     */
    fun shouldProcess(sourceApp: String, messageText: String, rulesString: String): Boolean {
        if (rulesString.isEmpty()) return true

        val packageName = resolvePackageName(sourceApp) ?: return true

        for (line in rulesString.split("\n")) {
            val parts = line.split("|")
            if (parts.size < 3 || parts[0] != packageName) continue

            val keywords = parts[1].split(",")
            val logic = parts[2].trim().uppercase()
            val matchCount = keywords.count { messageText.contains(it.trim()) }

            return if (logic == "AND") matchCount == keywords.size else matchCount > 0
        }
        return true
    }
}

