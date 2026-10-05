package com.example.qqaihelper

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class QQNotificationListener : NotificationListenerService() {


    // 记录上一条处理过的消息，防止重复通知疯狂触发 AI
    private var lastMessage = ""
    private var lastMessageTime = 0L

    // 增加超时时间（连接、读取、写入各60秒）
    private val client = OkHttpClient.Builder()
        .connectTimeout(60, java.util.concurrent.TimeUnit.SECONDS) // 连接超时60秒
        .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)    // 读取超时60秒
        .writeTimeout(60, java.util.concurrent.TimeUnit.SECONDS)   // 写入超时60秒
        .retryOnConnectionFailure(true) // 允许底层自动重连
        .build()

    private var webhookServer: WebhookServer? = null

    // 监听用户是否在设置里更改了 Webhook 状态
    private val prefsListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "webhook_enabled" || key == "webhook_port" || key == "webhook_token") {
            Log.d("QQ_AI_HELPER", "检测到 Webhook 配置变更，正在重启服务...")
            restartWebhookServer()
        }
    }

    override fun onCreate() {
        super.onCreate()
        // 注册配置变更监听器
        val prefs = getSharedPreferences("app_settings", android.content.Context.MODE_PRIVATE)
        prefs.registerOnSharedPreferenceChangeListener(prefsListener)
        // 首次启动时，尝试启动 Webhook
        restartWebhookServer()
    }

    override fun onDestroy() {
        super.onDestroy()
        // 注销监听器，防止内存泄漏
        val prefs = getSharedPreferences("app_settings", android.content.Context.MODE_PRIVATE)
        prefs.unregisterOnSharedPreferenceChangeListener(prefsListener)
        // 关闭服务器
        webhookServer?.stop()
        webhookServer = null
    }

    // 统一管理 Webhook 的启停逻辑
    private fun restartWebhookServer() {
        // 先停止旧服务
        webhookServer?.stop()
        webhookServer = null

        val prefs = getSharedPreferences("app_settings", android.content.Context.MODE_PRIVATE)
        val isEnabled = prefs.getBoolean("webhook_enabled", true)

        if (!isEnabled) {
            Log.d("QQ_AI_HELPER", "⏸️ Webhook 服务已手动关闭")
            return
        }

        val port = prefs.getString("webhook_port", "8080")?.toIntOrNull() ?: 8080
        val token = prefs.getString("webhook_token", "my_secret_123") ?: "my_secret_123"

        try {
            webhookServer = WebhookServer(port, token) { msg ->
                processIncomingMessage("外部插件", "外部消息", msg)
            }
            webhookServer?.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
            Log.d("QQ_AI_HELPER", "✅ Webhook 服务器已启动，监听端口: $port")
        } catch (e: Exception) {
            Log.e("QQ_AI_HELPER", "❌ Webhook 启动失败: ${e.message}")
        }
    }

    // 公共的消息处理函数（供通知监听和 Webhook 共同调用）
    private fun processIncomingMessage(sourceApp: String, title: String, text: String) {
        val prefs = getSharedPreferences("app_settings", android.content.Context.MODE_PRIVATE)
        val fullMessage = "$title\n$text"

        // 关键词过滤
        if (!shouldProcessMessage(sourceApp, fullMessage)) return

        // 防抖逻辑（防止重复通知疯狂触发 AI）
        val currentTime = System.currentTimeMillis()
        if (fullMessage != lastMessage || (currentTime - lastMessageTime) > 10000) {
            lastMessage = fullMessage
            lastMessageTime = currentTime
            sendToAI(fullMessage, sourceApp)
        } else {
            Log.d("QQ_AI_HELPER", "重复通知，跳过 AI 请求")
        }
    }

    // 动态获取用户配置的 API 信息
    private fun getApiConfig(): Triple<String, String, String> {
        val prefs = getSharedPreferences("app_settings", android.content.Context.MODE_PRIVATE)
        val url = prefs.getString("api_url", "") ?: ""
        val key = prefs.getString("api_key", "") ?: ""
        val model = prefs.getString("model_name", "") ?: ""
        return Triple(url, key, model)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)

        // 1. 读取服务开关
        val prefs = getSharedPreferences("app_settings", android.content.Context.MODE_PRIVATE)
        if (!prefs.getBoolean("service_enabled", true)) {
            return
        }
        // 检查定时开关逻辑
        if (prefs.getBoolean("schedule_enabled", false)) {
            val startTimeStr = prefs.getString("schedule_start", "08:00") ?: "08:00"
            val endTimeStr = prefs.getString("schedule_end", "22:00") ?: "22:00"

            val sdf = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
            val nowStr = sdf.format(java.util.Date())

            // 把时间转成分钟数，方便比较
            fun timeToMinutes(time: String): Int {
                val parts = time.split(":")
                return parts[0].toInt() * 60 + parts[1].toInt()
            }

            try {
                val nowMin = timeToMinutes(nowStr)
                val startMin = timeToMinutes(startTimeStr)
                val endMin = timeToMinutes(endTimeStr)

                // 判断是否在时间段内（处理跨天的情况，比如 22:00 到 08:00）
                val isWithinTime = if (startMin <= endMin) {
                    nowMin in startMin..endMin
                } else {
                    nowMin >= startMin || nowMin <= endMin
                }

                if (!isWithinTime) {
                    // 不在设定的时间段内，直接忽略消息
                    // Log.d("QQ_AI_HELPER", "当前不在设定时间段内，忽略消息")
                    return
                }
            } catch (e: Exception) {
                // 时间格式输错时忽略该错误，保证程序不崩溃
            }
        }

        // 2. 获取包名和监听白名单
        val packageName = sbn?.packageName ?: return
        val savedString = prefs.getString("monitored_packages", "") ?: ""
        val monitoredPackages = savedString.split(",").filter { it.isNotEmpty() }

        // 3. 如果是被监听的应用，开始处理
        if (monitoredPackages.contains(packageName)) {
            val extras = sbn.notification.extras
            val title = extras.getString(Notification.EXTRA_TITLE) ?: "无标题"
            val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: "无内容"

            // 4. 判断来源
            val sourceApp = when (packageName) {
                "com.tencent.mobileqq" -> "QQ"
                "com.tencent.mm" -> "微信"
                "com.alibaba.android.rimet" -> "钉钉"
                "com.tencent.wework" -> "企业微信"
                else -> "其他应用"
            }

            Log.d("QQ_AI_HELPER", "========== 捕获到消息 ==========")
            Log.d("QQ_AI_HELPER", "标题: $title")
            Log.d("QQ_AI_HELPER", "正文: $text")

            val fullMessage = "$title\n$text"

            // 5. 执行关键词过滤（如果未通过则直接返回）
            if (!shouldProcessMessage(packageName, fullMessage)) {
                Log.d("QQ_AI_HELPER", "消息 [$fullMessage] 未通过关键词过滤，跳过")
                return
            }

            // 6. 执行防抖逻辑（避免重复通知）
            val currentTime = System.currentTimeMillis()
            if (fullMessage != lastMessage || (currentTime - lastMessageTime) > 10000) {
                lastMessage = fullMessage
                lastMessageTime = currentTime
                sendToAI(fullMessage, sourceApp)
            } else {
                Log.d("QQ_AI_HELPER", "重复通知，跳过 AI 请求")
            }
        }
    }

    private fun sendToAI(message: String, sourceApp: String) {
        CoroutineScope(Dispatchers.IO).launch {
            val maxRetries = 3 // 最大重试次数
            var attempt = 0
            var success = false

            while (attempt < maxRetries && !success) {
                attempt++
                Log.d("QQ_AI_HELPER", "========== 第 $attempt 次尝试发送 AI 请求 ==========")

                if (!isNetworkAvailable()) {
                    Log.e("QQ_AI_HELPER", "当前没有网络，跳过请求")
                    return@launch
                }

                try {
                    val currentTime = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
                    val systemPrompt = "你是一个QQ消息助理。当前的真实时间是：$currentTime。请提取发送人、摘要和待办事项。如果存在待办，必须严格以当前的真实时间为基准，推算出准确的执行时间（格式严格为 YYYY-MM-DD HH:MM）。\n请按以下格式回复：\n【发送人】xxx\n【重要性】高/中/低\n【摘要】xxx\n【待办】xxx\n【待办时间】YYYY-MM-DD HH:MM 或 无"
                    val messagesArray = org.json.JSONArray()

                    val systemMsg = JSONObject()
                    systemMsg.put("role", "system")
                    systemMsg.put("content", systemPrompt)
                    messagesArray.put(systemMsg)

                    val userMsg = JSONObject()
                    userMsg.put("role", "user")
                    userMsg.put("content", message)
                    messagesArray.put(userMsg)

                    // 获取动态配置
                    val (url, key, model) = getApiConfig()

                    // 防御性检查：如果 API 配置为空，直接记录日志并终止
                    if (url.isBlank() || key.isBlank() || model.isBlank()) {
                        Log.e("QQ_AI_HELPER", "❌ 错误：API 配置不完整！请去 App 主界面填写 URL、Key 和模型名称。")
                        return@launch
                    }
                    //组装json数据
                    val jsonBody = JSONObject()
                    jsonBody.put("model", model)
                    jsonBody.put("messages", messagesArray)
                    jsonBody.put("temperature", 0.3)

                    val mediaType = "application/json; charset=utf-8".toMediaType()
                    val requestBody = jsonBody.toString().toRequestBody(mediaType)
                    //构建网络请求
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

                        Log.d("QQ_AI_HELPER", "========== AI 处理成功 ==========")
                        Log.d("QQ_AI_HELPER", "AI 回复内容:\n$aiReply")

                        parseAndWriteCalendar(aiReply, sourceApp)

                    } else {
                        Log.e("QQ_AI_HELPER", "AI 请求返回错误码，第 $attempt 次: ${response.code}")
                        if (attempt < maxRetries) delay(5000) // 等待 5 秒后重试
                    }
                } catch (e: Exception) {
                    Log.e("QQ_AI_HELPER", "第 $attempt 次网络请求发生异常: ${e.message}")
                    if (attempt < maxRetries) delay(5000) // 等待 5 秒后重试
                }
            }

            if (!success) {
                Log.e("QQ_AI_HELPER", "❌ 达到最大重试次数 ($maxRetries)，放弃处理该消息。")
            }
        }
    }
    // 解析 AI 结果并写入系统日历
    private fun parseAndWriteCalendar(aiReply: String, sourceApp: String) {
        try {
            // 用正则表达式提取“【待办】”和“【待办时间】”
            val todoPattern = java.util.regex.Pattern.compile("【待办】(.*?)(?=\\n|$)")
            val timePattern = java.util.regex.Pattern.compile("【待办时间】(\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2})")

            val todoMatcher = todoPattern.matcher(aiReply)
            val timeMatcher = timePattern.matcher(aiReply)

            if (todoMatcher.find() && timeMatcher.find()) {
                val todoText = todoMatcher.group(1).trim()
                val timeText = timeMatcher.group(1).trim()

                if (todoText != "无" && timeText != "无") {
                    Log.d("QQ_AI_HELPER", "解析到待办: $todoText, 时间: $timeText，准备写入日历")

                    // 检查是否有日历读写权限
                    if (androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.WRITE_CALENDAR) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                        Log.e("QQ_AI_HELPER", "没有日历写入权限，请去系统设置里手动授予！")
                        return
                    }

                    // 解析时间
                    val format = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
                    val startDate = format.parse(timeText)

                    if (startDate != null) {
                        val startMillis = startDate.time
                        val endMillis = startMillis + 60 * 60 * 1000 // 默认持续1小时

                        // 构造日历事件
                        val values = android.content.ContentValues().apply {
                            put(android.provider.CalendarContract.Events.TITLE, todoText)
                            put(android.provider.CalendarContract.Events.DESCRIPTION, "来自${sourceApp}消息自动提取")
                            put(android.provider.CalendarContract.Events.DTSTART, startMillis)
                            put(android.provider.CalendarContract.Events.DTEND, endMillis)
                            put(android.provider.CalendarContract.Events.CALENDAR_ID, 1) // ⚠️ 通常本机日历ID是1
                            put(android.provider.CalendarContract.Events.EVENT_TIMEZONE, java.util.TimeZone.getDefault().id)
                            put(android.provider.CalendarContract.Events.HAS_ALARM, 1)
                        }

                        val uri = contentResolver.insert(android.provider.CalendarContract.Events.CONTENT_URI, values)
                        if (uri != null) {
                            Log.d("QQ_AI_HELPER", "✅ 成功写入日历！事件ID: $uri")
                        } else {
                            Log.e("QQ_AI_HELPER", "❌ 写入日历失败，返回为空")
                        }
                    }
                } else {
                    Log.d("QQ_AI_HELPER", "AI 判定当前消息无待办，跳过写入日历")
                }
            } else {
                Log.d("QQ_AI_HELPER", "未提取到待办或时间格式不匹配，跳过写入")
            }
        } catch (e: Exception) {
            Log.e("QQ_AI_HELPER", "解析或写入日历时发生异常: ${e.message}")
        }
    }
    // 检查手机当前是否有可用网络
    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
    private fun shouldProcessMessage(packageName: String, messageText: String): Boolean {
        val prefs = getSharedPreferences("app_settings", android.content.Context.MODE_PRIVATE)
        val rulesString = prefs.getString("filter_rules", "") ?: ""
        if (rulesString.isEmpty()) return true

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
}