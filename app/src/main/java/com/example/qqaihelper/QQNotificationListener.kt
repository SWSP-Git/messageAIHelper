package com.example.qqaihelper

import android.Manifest
import android.app.Notification
import android.content.ContentValues
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.provider.CalendarContract
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.content.ContextCompat
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * 核心服务：监听系统通知 → 关键词过滤 → 调用 AI 解析 → 写入日历或备忘录。
 *
 * 三档日程结合模式（由 AiOptionsActivity 配置）：
 * 1. MODE_NONE         不结合日程：AI 只生成智能建议
 * 2. MODE_CODE_CHECK   代码侧冲突检测：AI 生成建议后，代码查 ±30min 冲突追加警告
 * 3. MODE_AI_ANALYSIS  交给 AI 分析：把用户未来 N 天日程塞进 prompt，AI 自行判断冲突（仅云端）
 */
class QQNotificationListener : NotificationListenerService() {

    companion object {
        // ---- 系统提示词模板 ----
        private val SYSTEM_PROMPT_TEMPLATE = """
            你是一个QQ消息助理。当前的真实时间是：{TIME}。请提取发送人、摘要和待办事项。
            请判断消息类型：如果包含明确时间，请标记为【类型】日程；如果是纯信息（如电话号、账号、需记住的内容），请标记为【类型】备忘。
            如果存在待办，必须严格以当前的真实时间为基准，推算出准确的执行时间（格式严格为 YYYY-MM-DD HH:MM）。
            对于【关键信息】字段：如果消息包含验证码、取件码、电话号、账号、金额、地址等必须原样保留的数据，请原样照抄，不要改写或省略。
            对于【智能建议】字段：用一句话提醒用户下一步可以做什么（例如"记得今天下班前去取件"、"建议提前 10 分钟到会议室"），没有建议时填"无"。
            请严格按以下格式回复：
            【发送人】xxx
            【类型】日程 或 备忘
            【重要性】高/中/低
            【摘要】一句话概括
            【关键信息】xxx（无则填"无"）
            【智能建议】xxx（无则填"无"）
            【待办】xxx
            【待办时间】YYYY-MM-DD HH:MM 或 无
        """.trimIndent()

        // ---- 常用常量 ----
        private const val PREFS_NAME = "app_settings"
        private const val TIME_FORMAT_DATETIME = "yyyy-MM-dd HH:mm:ss"
        private const val TIME_FORMAT_MINUTE = "yyyy-MM-dd HH:mm"
        private const val DEBOUNCE_WINDOW_MS = 10_000L
        private const val AI_MAX_RETRIES = 3
        private const val AI_RETRY_DELAY_MS = 5_000L
        private const val MEMO_FILE_NAME = "memo_list.txt"

        // ---- 日程冲突检测时间窗口（±30 分钟） ----
        private const val CONFLICT_WINDOW_MS = 30 * 60 * 1000L
    }

    private val prefs: SharedPreferences by lazy {
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private var lastMessage = ""
    private var lastMessageTime = 0L
    private var webhookServer: WebhookServer? = null

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(90, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .callTimeout(110, java.util.concurrent.TimeUnit.SECONDS)
        .protocols(listOf(okhttp3.Protocol.HTTP_1_1))
        .retryOnConnectionFailure(true)
        .build()

    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "webhook_enabled" || key == "webhook_port" || key == "webhook_token") {
            AppLogger.d("检测到 Webhook 配置变更，正在重启服务...")
            restartWebhookServer()
        }
    }

    // ==================== 生命周期 ====================

    override fun onCreate() {
        super.onCreate()
        AppLogger.init(applicationContext)
        prefs.registerOnSharedPreferenceChangeListener(prefsListener)
        restartWebhookServer()
    }

    override fun onDestroy() {
        super.onDestroy()
        prefs.unregisterOnSharedPreferenceChangeListener(prefsListener)
        webhookServer?.stop()
        webhookServer = null
    }

    // ==================== Webhook 管理 ====================

    private fun restartWebhookServer() {
        webhookServer?.stop()
        webhookServer = null

        if (!prefs.getBoolean("webhook_enabled", false)) {
            AppLogger.d("⏸️ Webhook 服务未启用")
            return
        }

        val port = prefs.getString("webhook_port", "8080")?.toIntOrNull() ?: 8080
        val token = prefs.getString("webhook_token", "my_secret_123") ?: "my_secret_123"

        try {
            webhookServer = WebhookServer(port, token) { msg ->
                processIncomingMessage("外部插件", "外部消息", msg)
            }
            webhookServer?.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
            AppLogger.d("✅ Webhook 服务器已启动，监听端口: $port")
        } catch (e: Exception) {
            AppLogger.e("❌ Webhook 启动失败: ${e.message}")
        }
    }

    // ==================== 通知监听入口 ====================

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        val packageName = sbn?.packageName ?: return

        if (!prefs.getBoolean("service_enabled", true)) return
        if (!isWithinSchedule()) return

        val monitoredPackages = prefs.getString("monitored_packages", "")
            ?.split(",")?.filter { it.isNotEmpty() } ?: emptyList()
        if (!monitoredPackages.contains(packageName)) return

        val extras = sbn.notification.extras
        val title = extras.getString(Notification.EXTRA_TITLE) ?: "无标题"
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: "无内容"
        val sourceApp = resolveSourceApp(packageName)

        AppLogger.d("========== 捕获到消息 ==========")
        AppLogger.d("来源: $sourceApp | 标题: $title | 正文: $text")

        processIncomingMessage(sourceApp, title, text)
    }

    // ==================== 消息处理主流程 ====================

    private fun processIncomingMessage(sourceApp: String, title: String, text: String) {
        val fullMessage = "$title\n$text"

        if (!shouldProcessMessage(sourceApp, fullMessage)) {
            AppLogger.d("消息未通过关键词过滤，跳过")
            return
        }

        val currentTime = System.currentTimeMillis()
        if (fullMessage == lastMessage && (currentTime - lastMessageTime) <= DEBOUNCE_WINDOW_MS) {
            AppLogger.d("重复通知，跳过 AI 请求")
            return
        }

        lastMessage = fullMessage
        lastMessageTime = currentTime

        val count = prefs.getInt("processed_message_count", 0) + 1
        prefs.edit().putInt("processed_message_count", count).apply()

        sendToAI(fullMessage, sourceApp)
    }

    // ==================== AI 调用 ====================

    private fun sendToAI(message: String, sourceApp: String) {
        CoroutineScope(Dispatchers.IO).launch {
            var attempt = 0
            var success = false

            while (attempt < AI_MAX_RETRIES && !success) {
                attempt++
                AppLogger.d("========== 第 $attempt 次尝试发送 AI 请求 ==========")

                if (!isNetworkAvailable()) {
                    AppLogger.e("当前没有网络，跳过本次请求")
                    return@launch
                }

                try {
                    val (url, key, model) = getApiConfig()
                    if (url.isBlank() || key.isBlank() || model.isBlank()) {
                        AppLogger.e("❌ 错误：API 配置不完整！请去 App 主界面填写 URL、Key 和模型名称。")
                        return@launch
                    }

                    val currentTime = formatNow(TIME_FORMAT_DATETIME)
                    val systemPrompt = buildSystemPrompt(currentTime)

                    val messagesArray = JSONArray().apply {
                        put(JSONObject().apply {
                            put("role", "system")
                            put("content", systemPrompt)
                        })
                        put(JSONObject().apply {
                            put("role", "user")
                            put("content", message)
                        })
                    }

                    val jsonBody = JSONObject().apply {
                        put("model", model)
                        put("messages", messagesArray)
                        put("temperature", 0.3)
                    }

                    val mediaType = "application/json; charset=utf-8".toMediaType()
                    val requestBody = jsonBody.toString().toRequestBody(mediaType)

                    val request = Request.Builder()
                        .url(url)
                        .addHeader("Authorization", "Bearer $key")
                        .addHeader("Content-Type", "application/json")
                        .post(requestBody)
                        .build()

                    AppLogger.d("请求 URL: $url")
                    AppLogger.d("请求模型: $model")
                    AppLogger.d("消息长度: ${message.length} 字符")

                    val response = client.newCall(request).execute()
                    val responseBody = response.body?.string() ?: "无响应"

                    if (response.isSuccessful) {
                        success = true
                        handleAiResponse(responseBody, sourceApp, message)
                    } else {
                        AppLogger.e("AI 请求返回错误码，第 $attempt 次: ${response.code}")
                        AppLogger.e("响应内容: ${responseBody.take(500)}")
                        if (attempt < AI_MAX_RETRIES) delay(AI_RETRY_DELAY_MS)
                    }
                } catch (e: Exception) {
                    // 打印异常类型 + 完整堆栈，便于定位问题
                    AppLogger.e("第 $attempt 次网络请求发生异常: ${e.javaClass.simpleName}: ${e.message}")
                    AppLogger.e("异常堆栈: " + e.stackTraceToString())
                    if (attempt < AI_MAX_RETRIES) delay(AI_RETRY_DELAY_MS)
                }
            }

            if (!success) {
                AppLogger.e("❌ 达到最大重试次数 ($AI_MAX_RETRIES)，放弃处理该消息。")
            }
        }
    }

    /**
     * 按当前配置的三档模式构建系统提示词。
     *
     * - MODE_NONE / MODE_CODE_CHECK：基础模板
     * - MODE_AI_ANALYSIS：基础模板 + 用户未来 N 天的日程列表
     */
    private fun buildSystemPrompt(currentTime: String): String {
        val basePrompt = SYSTEM_PROMPT_TEMPLATE.replace("{TIME}", currentTime)

        val mode = prefs.getString(AiOptionsActivity.KEY_SCHEDULE_MODE, AiOptionsActivity.MODE_NONE)
        if (mode != AiOptionsActivity.MODE_AI_ANALYSIS) {
            return basePrompt
        }

        // MODE_AI_ANALYSIS：查询用户未来 N 天日程并追加到 prompt
        val days = prefs.getInt(AiOptionsActivity.KEY_LOOKAHEAD_DAYS, AiOptionsActivity.DEFAULT_DAYS)
        val events = queryUpcomingEvents(days)

        if (events.isEmpty()) {
            AppLogger.d("AI 分析模式：未来 $days 天无日程，跳过注入")
            return basePrompt
        }

        AppLogger.d("AI 分析模式：注入 $events.size 条未来日程")

        val scheduleText = events.joinToString("\n") { "  - $it" }
        return """
            $basePrompt

            ---
            以下是用户未来 $days 天的既有日程，请在生成【智能建议】时结合它们判断是否存在时间冲突。若发现冲突，请在建议中明确提示（例如"⚠️ 与已有日程「XXX」时间接近，建议调整"）：
            $scheduleText
        """.trimIndent()
    }

    /**
     * 处理 AI 返回结果：解析字段 → 分发到日历或备忘录。
     */
    private fun handleAiResponse(responseBody: String, sourceApp: String, originalMessage: String) {
        val aiReply = JSONObject(responseBody)
            .getJSONArray("choices")
            .getJSONObject(0)
            .getJSONObject("message")
            .getString("content")

        AppLogger.d("========== AI 处理成功 ==========")
        AppLogger.d("AI 回复内容:\n$aiReply")

        val parsed = AiReplyParser.parse(aiReply)

        if (parsed.isMemo) {
            saveMemo(sourceApp, parsed.sender, originalMessage, parsed.summary, parsed.keyInfo, parsed.suggestion, parsed.importance)
        } else {
            // 日程类型：先尝试解析时间；若开启代码侧冲突检测，追加警告
            var suggestion = parsed.suggestion
            val todoTime = parsed.todoTime
            if (todoTime != null && todoTime != "无") {
                val mode = prefs.getString(AiOptionsActivity.KEY_SCHEDULE_MODE, AiOptionsActivity.MODE_NONE)
                if (mode == AiOptionsActivity.MODE_CODE_CHECK) {
                    val conflict = detectConflict(todoTime)
                    if (conflict != null) {
                        suggestion = if (suggestion == "无") {
                            "⚠️ 与已有日程「$conflict」时间接近，建议确认"
                        } else {
                            "$suggestion ⚠️ 与「$conflict」时间接近"
                        }
                        AppLogger.d("代码侧冲突检测：发现冲突「$conflict」")
                    }
                }
            }
            parseAndWriteCalendar(aiReply, sourceApp, suggestion)
        }
    }

    // ==================== 冲突检测 ====================

    /**
     * 在系统日历中查询待办时间 ±30 分钟内是否有既有事件。
     *
     * @param timeText 待办时间，格式 "yyyy-MM-dd HH:mm"
     * @return 冲突事件标题；无冲突时返回 null
     */
    private fun detectConflict(timeText: String): String? {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALENDAR)
            != PackageManager.PERMISSION_GRANTED) {
            return null
        }

        val startDate = try {
            SimpleDateFormat(TIME_FORMAT_MINUTE, Locale.getDefault()).parse(timeText)
        } catch (e: Exception) {
            null
        } ?: return null

        val windowStart = startDate.time - CONFLICT_WINDOW_MS
        val windowEnd = startDate.time + CONFLICT_WINDOW_MS

        return try {
            val projection = arrayOf(CalendarContract.Events.TITLE)
            val selection = "(${CalendarContract.Events.DTEND} > ? AND ${CalendarContract.Events.DTSTART} < ?)"
            val args = arrayOf(windowStart.toString(), windowEnd.toString())
            val cursor: Cursor? = contentResolver.query(
                CalendarContract.Events.CONTENT_URI, projection, selection, args, null
            )
            val conflictTitle = cursor?.use {
                if (it.moveToFirst()) it.getString(0) else null
            }
            conflictTitle
        } catch (e: Exception) {
            AppLogger.e("冲突检测查询失败: ${e.message}")
            null
        }
    }

    /**
     * 查询用户未来 N 天的日程（标题 + 开始时间），用于 MODE_AI_ANALYSIS 模式注入 prompt。
     *
     * @param days 未来天数
     * @return 形如 "2026-10-07 14:30  客户评审" 的字符串列表
     */
    private fun queryUpcomingEvents(days: Int): List<String> {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALENDAR)
            != PackageManager.PERMISSION_GRANTED) {
            return emptyList()
        }

        val now = System.currentTimeMillis()
        val end = now + days * 24L * 60 * 60 * 1000

        return try {
            val projection = arrayOf(
                CalendarContract.Events.TITLE,
                CalendarContract.Events.DTSTART
            )
            val selection = "(${CalendarContract.Events.DTSTART} >= ? AND ${CalendarContract.Events.DTSTART} <= ?)"
            val args = arrayOf(now.toString(), end.toString())
            val cursor = contentResolver.query(
                CalendarContract.Events.CONTENT_URI, projection, selection, args,
                "${CalendarContract.Events.DTSTART} ASC"
            )

            val result = mutableListOf<String>()
            cursor?.use {
                val titleIdx = it.getColumnIndex(CalendarContract.Events.TITLE)
                val startIdx = it.getColumnIndex(CalendarContract.Events.DTSTART)
                val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                while (it.moveToNext()) {
                    val title = it.getString(titleIdx) ?: "无标题"
                    val start = it.getLong(startIdx)
                    result.add("${sdf.format(Date(start))}  $title")
                }
            }
            result.take(50) // 上限 50 条，避免 prompt 过长
        } catch (e: Exception) {
            AppLogger.e("查询未来日程失败: ${e.message}")
            emptyList()
        }
    }

    // ==================== 结果处理 ====================

    /**
     * 保存备忘录：追加写入文本文件。
     *
     * @param source     来源应用（如 "QQ"）
     * @param sender     AI 提取的发送人（如 "张三"）；为 null 时不写入该字段
     * @param content    原始消息文本
     * @param summary    摘要
     * @param keyInfo    关键信息
     * @param suggestion 智能建议
     * @param importance 重要性
     */
    private fun saveMemo(
        source: String,
        sender: String?,
        content: String,
        summary: String,
        keyInfo: String,
        suggestion: String,
        importance: String
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val time = formatNow(TIME_FORMAT_DATETIME)
                val sb = StringBuilder()
                sb.append("来源: $source\n")
                // 发送人：AI 未提取到时省略该字段，保证与老数据格式兼容
                if (!sender.isNullOrBlank() && sender != "无") {
                    sb.append("发送人: $sender\n")
                }
                sb.append("时间: $time\n")
                sb.append("重要性: $importance\n")
                sb.append("摘要: $summary\n")
                if (keyInfo.isNotBlank() && keyInfo != "无") {
                    sb.append("关键信息: $keyInfo\n")
                }
                if (suggestion.isNotBlank() && suggestion != "无") {
                    sb.append("智能建议: $suggestion\n")
                }
                sb.append("内容: $content\n\n")

                val memoFile = StorageHelper.getMemoFile(applicationContext)
                memoFile.appendText(sb.toString())
                AppLogger.d("✅ 备忘录已保存: ${memoFile.absolutePath}")
            } catch (e: Exception) {
                AppLogger.e("保存备忘录失败: ${e.message}")
            }
        }
    }

    /** 解析 AI 结果并写入系统日历 */
    private fun parseAndWriteCalendar(aiReply: String, sourceApp: String, suggestion: String) {
        try {
            val parsed = AiReplyParser.parse(aiReply)
            val todoText = parsed.todoText
            val timeText = parsed.todoTime

            if (todoText == null || timeText == null) {
                AppLogger.d("未提取到待办或时间格式不匹配")
                return
            }
            if (todoText == "无" || timeText == "无") {
                AppLogger.d("无待办，跳过写入日历")
                return
            }

            AppLogger.d("解析到待办: $todoText, 时间: $timeText，准备写入日历")

            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_CALENDAR)
                != PackageManager.PERMISSION_GRANTED) {
                AppLogger.e("没有日历写入权限")
                return
            }

            val startDate = SimpleDateFormat(TIME_FORMAT_MINUTE, Locale.getDefault()).parse(timeText)
            if (startDate == null) {
                AppLogger.e("时间解析失败: $timeText")
                return
            }

            val startMillis = startDate.time
            val endMillis = startMillis + 60 * 60 * 1000

            // 冲突警告 + 智能建议一起写进日历事件的描述
            val descBuilder = StringBuilder("来自${sourceApp}消息自动提取")
            if (suggestion.isNotBlank() && suggestion != "无") {
                descBuilder.append("\n\n【AI建议】$suggestion")
            }

            val values = ContentValues().apply {
                put(CalendarContract.Events.TITLE, todoText)
                put(CalendarContract.Events.DESCRIPTION, descBuilder.toString())
                put(CalendarContract.Events.DTSTART, startMillis)
                put(CalendarContract.Events.DTEND, endMillis)
                put(CalendarContract.Events.CALENDAR_ID, 1)
                put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
                put(CalendarContract.Events.HAS_ALARM, 1)
            }

            val uri = contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
            if (uri != null) {
                AppLogger.d("✅ 成功写入日历！事件ID: $uri")
            } else {
                AppLogger.e("❌ 写入日历失败")
            }
        } catch (e: Exception) {
            AppLogger.e("解析或写入日历时发生异常: ${e.message}")
        }
    }

    // ==================== 辅助函数 ====================

    /** 关键词过滤（支持 AND / OR）；规则解析委托给纯逻辑 MessageFilter */
    private fun shouldProcessMessage(sourceApp: String, messageText: String): Boolean {
        val rulesString = prefs.getString("filter_rules", "") ?: ""
        return MessageFilter.shouldProcess(sourceApp, messageText, rulesString)
    }

    private fun isNetworkAvailable(): Boolean {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun isWithinSchedule(): Boolean {
        if (!prefs.getBoolean("schedule_enabled", false)) return true

        val startTimeStr = prefs.getString("schedule_start", ScheduleWindow.DEFAULT_START) ?: ScheduleWindow.DEFAULT_START
        val endTimeStr = prefs.getString("schedule_end", ScheduleWindow.DEFAULT_END) ?: ScheduleWindow.DEFAULT_END

        return ScheduleWindow.isWithin(formatNow("HH:mm"), startTimeStr, endTimeStr)
    }

    private fun resolveSourceApp(packageName: String): String =
        MessageFilter.resolveSourceApp(packageName)

    private fun getApiConfig(): Triple<String, String, String> {
        val url = prefs.getString("api_url", "") ?: ""
        val key = prefs.getString("api_key", "") ?: ""
        val model = prefs.getString("model_name", "") ?: ""
        return Triple(url, key, model)
    }

    private fun formatNow(pattern: String): String =
        SimpleDateFormat(pattern, Locale.getDefault()).format(Date())
}
