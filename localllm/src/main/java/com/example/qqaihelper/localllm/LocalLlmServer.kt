package com.example.qqaihelper.localllm

import android.util.Log
import fi.iki.elonen.NanoHTTPD
import org.json.JSONArray
import org.json.JSONObject

/**
 * 本地模型服务（插件对外接口）。
 *
 * 在 127.0.0.1 的一个「预留端口」上暴露 OpenAI 兼容的接口，
 * 这样主 App 的 AI 调用代码完全不用改协议：云端换成本地只是把 base URL 指过来即可。
 *
 * 支持的接口：
 *   POST /v1/chat/completions   —— 与 OpenAI /chat/completions 一致的最小实现
 *   GET  /v1/models             —— 返回当前本地模型信息
 *
 * 安全：仅监听回环地址 127.0.0.1，不对外网/局域网暴露。
 */
class LocalLlmServer(
    port: Int,
    private val modelId: String = "local",
    private val chatFn: (system: String, user: String, maxTokens: Int) -> String
) : NanoHTTPD("127.0.0.1", port) {

    companion object {
        private const val TAG = "LocalLlm"
        private const val DEFAULT_MAX_TOKENS = 512
        private const val MAX_BODY_CHARS = 64 * 1024
    }

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri ?: "/"
        Log.d(TAG, "本地服务收到请求: " + session.method + " " + uri)
        return try {
            when {
                session.method == Method.POST && uri.endsWith("/chat/completions") -> handleChatCompletion(session)
                session.method == Method.GET && uri.endsWith("/models") -> handleModels()
                else -> json(Response.Status.NOT_FOUND, error("未知接口: " + uri))
            }
        } catch (t: Throwable) {
            Log.e(TAG, "本地服务处理异常: " + t.message, t)
            json(Response.Status.INTERNAL_ERROR, error(t.message ?: "internal error"))
        }
    }

    private fun handleChatCompletion(session: IHTTPSession): Response {
        val bodyMap = HashMap<String, String>()
        session.parseBody(bodyMap)
        val body = bodyMap["postData"] ?: ""
        if (body.isBlank()) return json(Response.Status.BAD_REQUEST, error("请求体为空"))
        if (body.length > MAX_BODY_CHARS) return json(Response.Status.BAD_REQUEST, error("请求体过大"))

        val root = JSONObject(body)
        val messages = root.optJSONArray("messages")
            ?: return json(Response.Status.BAD_REQUEST, error("缺少 messages 字段"))

        val maxTokens = root.optInt("max_tokens", DEFAULT_MAX_TOKENS)
            .let { if (it in 1..4096) it else DEFAULT_MAX_TOKENS }

        var system = ""
        var lastUser = ""
        for (i in 0 until messages.length()) {
            val m = messages.optJSONObject(i) ?: continue
            val role = m.optString("role", "")
            val content = m.optString("content", "")
            when (role) {
                "system" -> system = content
                "user" -> lastUser = content
            }
        }
        if (lastUser.isBlank()) return json(Response.Status.BAD_REQUEST, error("缺少 user 消息"))

        val started = System.currentTimeMillis()
        val answer = chatFn(system, lastUser, maxTokens)
        val costMs = System.currentTimeMillis() - started

        if (answer.isEmpty()) {
            return json(
                Response.Status.INTERNAL_ERROR,
                error("本地模型未就绪或推理失败（请确认插件已安装并设为当前）")
            )
        }

        val result = JSONObject().apply {
            put("id", "chatcmpl-local-" + System.currentTimeMillis())
            put("object", "chat.completion")
            put("created", System.currentTimeMillis() / 1000)
            put("model", modelId)
            put("choices", JSONArray().apply {
                put(JSONObject().apply {
                    put("index", 0)
                    put("message", JSONObject().apply {
                        put("role", "assistant")
                        put("content", answer)
                    })
                    put("finish_reason", "stop")
                })
            })
            put("usage", JSONObject().apply {
                put("prompt_tokens", 0)
                put("completion_tokens", 0)
                put("total_tokens", 0)
                put("cost_ms", costMs)
            })
        }
        Log.d(TAG, "本地推理完成，耗时 " + costMs + "ms，输出 " + answer.length + " 字")
        return json(Response.Status.OK, result.toString())
    }

    private fun handleModels(): Response {
        val result = JSONObject().apply {
            put("object", "list")
            put("data", JSONArray().apply {
                put(JSONObject().apply {
                    put("id", modelId)
                    put("object", "model")
                    put("owned_by", "local-mnn")
                })
            })
        }
        return json(Response.Status.OK, result.toString())
    }

    private fun error(message: String): String =
        JSONObject().apply {
            put("error", JSONObject().apply {
                put("message", message)
                put("type", "local_llm_error")
            })
        }.toString()

    private fun json(status: Response.Status, body: String): Response =
        newFixedLengthResponse(status, "application/json; charset=utf-8", body)
}
