package com.example.qqaihelper

import android.util.Log
import fi.iki.elonen.NanoHTTPD

/**
 * 局域网 Webhook 服务器。
 *
 * 使用场景：
 * 除了自动监听系统通知外，用户还可以通过 HTTP 请求主动向 App 投递消息。
 * 例如：在电脑上跑自动化脚本时，只需访问：
 *   http://<手机IP>:8080/?token=xxx&msg=明天下午3点开会
 * App 就会走与「通知监听」完全相同的流程：AI 解析 → 写日历/备忘录。
 *
 * 安全设计（三层防护）：
 * 1. 仅监听局域网：不主动暴露到公网，路由器不做端口转发即可天然隔离；
 * 2. Token 校验：请求参数 token 必须与用户设置的密钥完全一致；
 * 3. 长度限制：单条消息最多 500 字，防止恶意超长文本刷爆 AI 额度。
 *
 * 关于 NanoHTTPD：
 * 这是一个轻量级的嵌入式 HTTP 服务器（约 50KB），非常适合移动端场景。
 * 我们只覆写 serve() 方法，不启动任何前端页面，纯 API 服务。
 *
 * @param port              监听端口（由用户在「更多设置」里配置，默认 8080）
 * @param validToken        用户设置的通信密钥，用于鉴权
 * @param onMessageReceived 收到合法消息后的回调，交给 QQNotificationListener 处理
 */
class WebhookServer(
    port: Int,
    private val validToken: String,
    private val onMessageReceived: (String) -> Unit
) : NanoHTTPD(port) {

    /**
     * 处理每一个进入的 HTTP 请求。
     *
     * 完整流程：
     * 1. 解析 URL 参数 token 和 msg
     * 2. 校验 token 是否匹配（不匹配直接返回 401）
     * 3. 校验 msg 长度是否超限（超限返回 400）
     * 4. 校验 msg 非空（为空返回 400，并提示正确用法）
     * 5. 一切正常 → 触发回调 + 返回 200
     */
    override fun serve(session: IHTTPSession): Response {

        // ---- 第 1 步：从 URL 参数中提取 token 和 msg ----
        // 例如 ?token=abc&msg=hello 会被解析成 parameters["token"]=["abc"], parameters["msg"]=["hello"]
        val token = session.parameters["token"]?.firstOrNull()
        val msg = session.parameters["msg"]?.firstOrNull()

        // ---- 第 2 步：鉴权 ----
        // 任何请求都必须携带正确 token，否则视为未授权访问
        if (token != validToken) {
            AppLogger.e("Webhook 拒绝访问：密钥错误或缺失")
            return newFixedLengthResponse(
                Response.Status.UNAUTHORIZED,
                "text/plain; charset=utf-8",
                "❌ 密钥错误，拒绝访问"
            )
        }

        // ---- 第 3 步：长度限制 ----
        // 防止有人构造超大文本触发 AI 调用，消耗用户付费额度
        if (msg != null && msg.length > 500) {
            return newFixedLengthResponse(
                Response.Status.BAD_REQUEST,
                "text/plain; charset=utf-8",
                "❌ 消息过长，拒绝处理"
            )
        }

        // ---- 第 4 步：内容非空校验 + 分发 ----
        return if (!msg.isNullOrBlank()) {
            // 消息合法：记录日志 + 触发回调（后续由 QQNotificationListener.processIncomingMessage 处理）
            AppLogger.d("Webhook 收到外部消息: $msg")
            onMessageReceived(msg)
            newFixedLengthResponse(
                Response.Status.OK,
                "text/plain; charset=utf-8",
                "✅ 消息已成功交给 App 处理"
            )
        } else {
            // 消息为空：返回 400 并提示正确用法，方便调用方快速纠正
            newFixedLengthResponse(
                Response.Status.BAD_REQUEST,
                "text/plain; charset=utf-8",
                "❌ 请使用 ?token=你的密钥&msg=你要发送的消息 传递参数"
            )
        }
    }
}
