package com.example.qqaihelper

import android.util.Log
import fi.iki.elonen.NanoHTTPD

class WebhookServer(port: Int, private val validToken: String, private val onMessageReceived: (String) -> Unit) : NanoHTTPD(port) {

    override fun serve(session: IHTTPSession): Response {
        // 1. 获取请求中的密钥和消息
        val token = session.parameters["token"]?.firstOrNull()
        val msg = session.parameters["msg"]?.firstOrNull()

        // 2. 核心安全验证：如果密钥不对，直接拒绝！
        if (token != validToken) {
            AppLogger.e("Webhook 拒绝访问：密钥错误或缺失")
            return newFixedLengthResponse(Response.Status.UNAUTHORIZED, "text/plain; charset=utf-8", "❌ 密钥错误，拒绝访问")
        }

        // 3. 消息长度限制（防止恶意发送超长文本刷额度，限制为 500 字）
        if (msg != null && msg.length > 500) {
            return newFixedLengthResponse(Response.Status.BAD_REQUEST, "text/plain; charset=utf-8", "❌ 消息过长，拒绝处理")
        }

        return if (!msg.isNullOrBlank()) {
            AppLogger.d("Webhook 收到外部消息: $msg")
            onMessageReceived(msg)
            newFixedLengthResponse(Response.Status.OK, "text/plain; charset=utf-8", "✅ 消息已成功交给 App 处理")
        } else {
            newFixedLengthResponse(Response.Status.BAD_REQUEST, "text/plain; charset=utf-8", "❌ 请使用 ?token=你的密钥&msg=你要发送的消息 传递参数")
        }
    }
}