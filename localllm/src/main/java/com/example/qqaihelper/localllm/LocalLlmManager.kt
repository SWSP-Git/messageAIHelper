package com.example.qqaihelper.localllm

import android.content.Context
import android.util.Log
import fi.iki.elonen.NanoHTTPD

/**
 * 本地模型插件总控（主 App 唯一需要接触的入口）。
 *
 * 职责：
 * 1. 插件查询（列出 / 当前 / 已装）；
 * 2. 引擎加载（懒加载，首次请求时载入内存）；
 * 3. 本地 HTTP 服务生命周期（在预留端口上启动/停止）。
 */
object LocalLlmManager {

    private const val TAG = "LocalLlm"

    /** 预留的本地模型服务端口（默认 8090，仅监听 127.0.0.1） */
    const val DEFAULT_PORT = 8090

    enum class State { NO_PLUGIN, UNLOADED, LOADING, READY, ERROR, NOT_SUPPORTED }

    @Volatile
    var state: State = State.NO_PLUGIN
        private set

    @Volatile
    private var server: LocalLlmServer? = null

    @Volatile
    private var loadedPluginId: String? = null

    // ---- 插件相关 ----

    fun listPlugins(context: Context): List<PluginManager.PluginInfo> = PluginManager.list(context)

    fun currentPlugin(context: Context): PluginManager.PluginInfo? = PluginManager.current(context)

    fun hasAnyPlugin(context: Context): Boolean = listPlugins(context).isNotEmpty()

    fun isServerRunning(): Boolean = server != null

    /** 是否支持本地推理（native 库加载成功 + 有当前插件） */
    fun isSupported(context: Context): Boolean =
        LocalLlmEngine.loadNativeLibs() && currentPlugin(context) != null

    // ---- 加载 / 卸载 ----

    /**
     * 确保当前插件已加载。幂等，可在任意 IO 线程调用（内部串行）。
     * 若当前插件与已加载插件不同，会先释放旧的再加载新的。
     * @return 模型是否可用
     */
    @Synchronized
    fun ensureLoaded(context: Context): Boolean {
        val plugin = currentPlugin(context)
        if (plugin == null) {
            state = State.NO_PLUGIN
            return false
        }
        if (!LocalLlmEngine.loadNativeLibs()) {
            state = State.NOT_SUPPORTED
            return false
        }
        if (LocalLlmEngine.get().isLoaded && loadedPluginId == plugin.id) {
            state = State.READY
            return true
        }
        if (LocalLlmEngine.get().isLoaded && loadedPluginId != plugin.id) {
            Log.d(TAG, "切换插件: " + loadedPluginId + " -> " + plugin.id + "，释放旧引擎")
            LocalLlmEngine.get().release()
            loadedPluginId = null
        }
        Log.d(TAG, "开始加载本地模型: " + plugin.id + " @ " + plugin.configFile.absolutePath)
        state = State.LOADING
        val start = System.currentTimeMillis()
        val ok = LocalLlmEngine.get().load(plugin.configFile.absolutePath)
        if (ok) {
            loadedPluginId = plugin.id
            state = State.READY
        } else {
            state = State.ERROR
        }
        Log.d(TAG, "本地模型加载" + (if (ok) "成功" else "失败") + "，耗时 " + (System.currentTimeMillis() - start) + "ms")
        return ok
    }

    /** 释放已加载的模型（不会停止 HTTP 服务） */
    @Synchronized
    fun unload() {
        LocalLlmEngine.get().release()
        loadedPluginId = null
        state = State.UNLOADED
    }

    // ---- HTTP 服务 ----

    /** 启动本地服务（若已在运行会先停止重建） */
    @Synchronized
    fun startServer(context: Context, port: Int): Boolean {
        if (server != null) return true
        return try {
            val modelId = currentPlugin(context)?.id ?: "local"
            val s = LocalLlmServer(port, modelId) { system, user, maxTokens ->
                if (ensureLoaded(context)) {
                    LocalLlmEngine.get().chat(system, user, maxTokens)
                } else {
                    ""
                }
            }
            s.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
            server = s
            Log.d(TAG, "本地模型服务已启动: http://127.0.0.1:" + port + "/v1/chat/completions")
            true
        } catch (t: Throwable) {
            Log.e(TAG, "本地模型服务启动失败: " + t.message, t)
            server = null
            false
        }
    }

    @Synchronized
    fun stopServer() {
        try {
            server?.stop()
        } catch (t: Throwable) {
            Log.w(TAG, "停止本地模型服务异常: " + t.message)
        }
        server = null
    }

    fun localEndpoint(port: Int): String = "http://127.0.0.1:" + port + "/v1/chat/completions"
}
