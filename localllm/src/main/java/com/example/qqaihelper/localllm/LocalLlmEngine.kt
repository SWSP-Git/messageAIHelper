package com.example.qqaihelper.localllm

import android.util.Log

/**
 * 本地大模型引擎（JNI 桥接层）。
 *
 * 底层是 MNN 的 LLM 引擎（libMNN.so + 本模块的 liblocalllm.so），
 * 对上层只暴露 3 个动作：load / chat / release。
 *
 * 线程模型：MNN 的 Llm 实例不是线程安全的，所有原生调用都用同一把锁串行化。
 * 因此调用方必须在后台线程（IO 线程）里使用 chat()。
 */
class LocalLlmEngine {

    @Volatile
    private var handle: Long = 0L

    /** 模型是否已成功加载进内存 */
    @Volatile
    var isLoaded: Boolean = false
        private set

    private val lock = Any()

    companion object {
        const val TAG = "LocalLlm"

        @Volatile
        private var instance: LocalLlmEngine? = null

        @Volatile
        private var libsLoaded = false

        fun get(): LocalLlmEngine = instance ?: synchronized(this) {
            instance ?: LocalLlmEngine().also { instance = it }
        }

        /** 加载 native 库，失败时返回 false（例如非 arm64 设备）。 */
        @Synchronized
        fun loadNativeLibs(): Boolean {
            if (libsLoaded) return true
            return try {
                System.loadLibrary("MNN")
                System.loadLibrary("localllm")
                libsLoaded = true
                true
            } catch (t: Throwable) {
                Log.e(TAG, "加载本地推理引擎失败: ${t.message}", t)
                false
            }
        }
    }

    /**
     * 从 config.json 路径加载模型。
     * @return 是否加载成功
     */
    fun load(configPath: String): Boolean {
        if (!loadNativeLibs()) return false
        synchronized(lock) {
            if (isLoaded) return true
            val h = nativeCreate(configPath)
            if (h == 0L) {
                Log.e(TAG, "createLLM 失败，配置路径: $configPath")
                return false
            }
            val ok = nativeLoad(h)
            if (ok) {
                handle = h
                isLoaded = true
            } else {
                nativeRelease(h)
                Log.e(TAG, "模型 load() 返回失败")
            }
            return ok
        }
    }

    /**
     * 发起一次对话补全（阻塞，必须在后台线程调用）。
     * @return 模型输出的纯文本；未加载或出错时返回空串
     */
    fun chat(systemPrompt: String, userMessage: String, maxNewTokens: Int): String {
        synchronized(lock) {
            if (!isLoaded || handle == 0L) return ""
            return nativeChat(handle, systemPrompt, userMessage, maxNewTokens)
        }
    }

    fun release() {
        synchronized(lock) {
            if (handle != 0L) {
                nativeRelease(handle)
                handle = 0L
            }
            isLoaded = false
        }
    }

    private external fun nativeCreate(configPath: String): Long
    private external fun nativeLoad(handle: Long): Boolean
    private external fun nativeChat(handle: Long, systemPrompt: String, userMessage: String, maxNewTokens: Int): String
    private external fun nativeRelease(handle: Long)
}
