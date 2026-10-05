package com.example.qqaihelper

import android.Manifest
import android.app.Notification
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
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
import java.util.regex.Pattern

class QQNotificationListener : NotificationListenerService() {

    // ==================== 成员变量 ====================

    private var lastMessage = ""
    private var lastMessageTime = 0L
    private var webhookServer: WebhookServer? = null

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    // Webhook 配置变更监听器
    private val prefsListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "webhook_enabled" || key == "webhook_port" || key == "webhook_token") {
            AppLogger.d("检测到 Webhook 配置变更，正在重启服务...")
            restartWebhookServer()
        }
    }

    // ==================== 生命周期 ====================

    override fun onCreate() {
        super.onCreate()
        AppLogger.init(applicationContext)
        val prefs = getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        prefs.registerOnSharedPreferenceChangeListener(prefsListener)
        restartWebhookServer()
    }

    override fun onDestroy() {
        super.onDestroy()
        val prefs = getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        prefs.unregisterOnSharedPreferenceChangeListener(prefsListener)
        webhookServer?.stop()
        webhookServer = null
    }

    // ==================== Webhook 管理 ====================

    private fun restartWebhookServer() {
        webhookServer?.stop()
        webhookServer = null

        val prefs = getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        // 默认 false：用户未主动开启前不暴露端口
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

    // ==================== 通知监听 ====================

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        val packageName = sbn?.packageName ?: return

        val prefs = getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("service_enabled", true)) return
        if (!isWithinSchedule(prefs)) return

        // 白名单过滤
        val savedString = prefs.getString("monitored_packages", "") ?: ""
        val monitoredPackages = savedString.split(",").filter { it.isNotEmpty() }
        if (!monitoredPackages.contains(packageName)) return

        // 提取通知内容
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

        // 关键词过滤
        if (!shouldProcessMessage(sourceApp, fullMessage)) {
            AppLogger.d("消息未通过关键词过滤，跳过")
            return
        }

        // 防抖（10 秒内内容相同的消息只处理一次）
        val currentTime = System.currentTimeMillis()
        if (fullMessage == lastMessage && (currentTime - lastMessageTime) <= 10000) {
            AppLogger.d("重复通知，跳过 AI 请求")
            return
        }

        lastMessage = fullMessage
        lastMessageTime = currentTime
        sendToAI(fullMessage, sourceApp)
    }

    // ==================== AI 调用 ====================

    private fun sendToAI(message: String, sourceApp: String) {
        CoroutineScope(Dispatchers.IO).launch {
            val maxRetries = 3
            var attempt = 0
            var success = false

            while (attempt < maxRetries && !success) {
                attempt++
                AppLogger.d("========== 第 $attempt 次尝试发送 AI 请求 ==========")

                if (!isNetworkAvailable()) {
                    AppLogger.e("当前没有网络，跳过本次请求")
                    return@launch
                }

                try {
                    val currentTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())

                    val systemPrompt = "你是一个QQ消息助理。当前的真实时间是：$currentTime。请提取发送人、摘要和待办事项。\n" +
                            "请判断消息类型：如果包含明确时间，请标记为【类型】日程；如果是纯信息（如电话号、账号、需记住的内容），请标记为【类型】备忘。\n" +
                            "如果存在待办，必须严格以当前的真实时间为基准，推算出准确的执行时间（格式严格为 YYYY-MM-DD HH:MM）。\n" +
                            "请严格按以下格式回复：\n" +
                            "【发送人】xxx\n【类型】日程 或 备忘\n【重要性】高/中/低\n【摘要】xxx\n【待办】xxx\n【待办时间】YYYY-MM-DD HH:MM 或 无"

                    val messagesArray = JSONArray()

                    val systemMsg = JSONObject()
                    systemMsg.put("role", "system")
                    systemMsg.put("content", systemPrompt)
                    messagesArray.put(systemMsg)

                    val userMsg = JSONObject()
                    userMsg.put("role", "user")
                    userMsg.put("content", message)
                    messagesArray.put(userMsg)

                    val (url, key, model) = getApiConfig()

                    if (url.isBlank() || key.isBlank() || model.isBlank()) {
                        AppLogger.e("❌ 错误：API 配置不完整！请去 App 主界面填写 URL、Key 和模型名称。")
                        return@launch
                    }

                    val jsonBody = JSONObject()
                    jsonBody.put("model", model)
                    jsonBody.put("messages", messagesArray)
                    jsonBody.put("temperature", 0.3)

                    val mediaType = "application/json; charset=utf-8".toMediaType()
                    val requestBody = jsonBody.toString().toRequestBody(mediaType)

                    val request = Request.Builder()
                        .url(url)
                        .addHeader("Authorization", "Bearer $key")
                        .addHeader("Content-Type", "application/json")
                        .post(requestBody)
                        .build()

                    val response = client.newCall(request).execute()
                    val responseBody = response.body?.string() ?: "无响应"

                    if (response.isSuccessful) {
                        success = true
                        val jsonObject = JSONObject(responseBody)
                        val choices = jsonObject.getJSONArray("choices")
                        val aiReply = choices.getJSONObject(0).getJSONObject("message").getString("content")

                        AppLogger.d("========== AI 处理成功 ==========")
                        AppLogger.d("AI 回复内容:\n$aiReply")

                        // 解析类型（?? 处理 nullable，消除 warning）
                        val typePattern = Pattern.compile("【类型】(.*?)(?=\\n|$)")
                        val typeMatcher = typePattern.matcher(aiReply)
                        val type = if (typeMatcher.find()) (typeMatcher.group(1) ?: "").trim() else "日程"

                        // 解析摘要
                        val summaryPattern = Pattern.compile("【摘要】(.*?)(?=\\n|$)")
                        val summaryMatcher = summaryPattern.matcher(aiReply)
                        val summary = if (summaryMatcher.find()) (summaryMatcher.group(1) ?: "").trim() else "无摘要"

                        if (type == "备忘") {
                            saveMemo(sourceApp, message, summary)
                        } else {
                            parseAndWriteCalendar(aiReply, sourceApp)
                        }

                    } else {
                        AppLogger.e("AI 请求返回错误码，第 $attempt 次: ${response.code}")
                        if (attempt < maxRetries) delay(5000)
                    }
                } catch (e: Exception) {
                    AppLogger.e("第 $attempt 次网络请求发生异常: ${e.message}")
                    if (attempt < maxRetries) delay(5000)
                }
            }

            if (!success) {
                AppLogger.e("❌ 达到最大重试次数 ($maxRetries)，放弃处理该消息。")
            }
        }
    }

    // ==================== 结果处理 ====================

    // 保存备忘：追加写入文本文件
    private fun saveMemo(source: String, content: String, summary: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
                val memoText = "来源: $source\n时间: $time\n摘要: $summary\n内容: $content\n\n"
                val memoFile = File(filesDir, "memo_list.txt")
                memoFile.appendText(memoText)
                AppLogger.d("✅ 备忘录已保存: $summary")
            } catch (e: Exception) {
                AppLogger.e("保存备忘录失败: ${e.message}")
            }
        }
    }

    // 解析 AI 结果并写入系统日历
    private fun parseAndWriteCalendar(aiReply: String, sourceApp: String) {
        try {
            val todoPattern = Pattern.compile("【待办】(.*?)(?=\\n|$)")
            val timePattern = Pattern.compile("【待办时间】(\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2})")
            val todoMatcher = todoPattern.matcher(aiReply)
            val timeMatcher = timePattern.matcher(aiReply)

            if (!todoMatcher.find() || !timeMatcher.find()) {
                AppLogger.d("未提取到待办或时间格式不匹配")
                return
            }

            val todoText = (todoMatcher.group(1) ?: "").trim()
            val timeText = (timeMatcher.group(1) ?: "").trim()

            if (todoText == "无" || timeText == "无") {
                AppLogger.d("无待办，跳过写入日历")
                return
            }

            AppLogger.d("解析到待办: $todoText, 时间: $timeText，准备写入日历")

            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
                AppLogger.e("没有日历写入权限")
                return
            }

            val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            val startDate = fmt.parse(timeText) ?: run {
                AppLogger.e("时间解析失败: $timeText")
                return
            }

            val startMillis = startDate.time
            val endMillis = startMillis + 60 * 60 * 1000

            val values = ContentValues().apply {
                put(CalendarContract.Events.TITLE, todoText)
                put(CalendarContract.Events.DESCRIPTION, "来自${sourceApp}消息自动提取")
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

    // 关键词过滤（支持 AND / OR）
    private fun shouldProcessMessage(sourceApp: String, messageText: String): Boolean {
        val prefs = getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        val rulesString = prefs.getString("filter_rules", "") ?: ""
        if (rulesString.isEmpty()) return true

        // 将 sourceApp 映射回包名（filter_rules 里保存的是包名）
        val packageName = when (sourceApp) {
            "QQ" -> "com.tencent.mobileqq"
            "微信" -> "com.tencent.mm"
            "钉钉" -> "com.alibaba.android.rimet"
            "企业微信" -> "com.tencent.wework"
            else -> return true
        }

        val lines = rulesString.split("\n")
        for (line in lines) {
            val parts = line.split("|")
            if (parts.size >= 3 && parts[0] == packageName) {
                val keywords = parts[1].split(",")
                val logic = parts[2].trim().uppercase()
                var matchCount = 0
                for (kw in keywords) {
                    if (messageText.contains(kw.trim())) matchCount++
                }
                return if (logic == "AND") matchCount == keywords.size else matchCount > 0
            }
        }
        return true
    }

    // 检查是否有可用网络
    private fun isNetworkAvailable(): Boolean {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    // 检查当前是否在定时开关允许的时间段内
    private fun isWithinSchedule(prefs: android.content.SharedPreferences): Boolean {
        if (!prefs.getBoolean("schedule_enabled", false)) return true

        val startTimeStr = prefs.getString("schedule_start", "08:00") ?: "08:00"
        val endTimeStr = prefs.getString("schedule_end", "22:00") ?: "22:00"

        return try {
            val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
            val nowStr = sdf.format(Date())
            fun timeToMinutes(t: String): Int {
                val parts = t.split(":")
                return parts[0].toInt() * 60 + parts[1].toInt()
            }
            val nowMin = timeToMinutes(nowStr)
            val startMin = timeToMinutes(startTimeStr)
            val endMin = timeToMinutes(endTimeStr)
            if (startMin <= endMin) nowMin in startMin..endMin
            else nowMin >= startMin || nowMin <= endMin
        } catch (e: Exception) {
            true // 解析失败时默认放行，不阻塞用户
        }
    }

    // 包名 → 应用中文名
    private fun resolveSourceApp(packageName: String): String = when (packageName) {
        "com.tencent.mobileqq" -> "QQ"
        "com.tencent.mm" -> "微信"
        "com.alibaba.android.rimet" -> "钉钉"
        "com.tencent.wework" -> "企业微信"
        else -> "其他应用"
    }

    // 动态获取用户配置的 API 信息
    private fun getApiConfig(): Triple<String, String, String> {
        val prefs = getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        val url = prefs.getString("api_url", "") ?: ""
        val key = prefs.getString("api_key", "") ?: ""
        val model = prefs.getString("model_name", "") ?: ""
        return Triple(url, key, model)
    }
}
