// llm_jni.cpp
//
// 本地大模型插件的 JNI 桥。把 MNN 的 LLM 引擎（libMNN.so 中的 MNN::Transformer::Llm）
// 包装成 LocalLlmEngine.kt 里的 4 个 native 方法。
//
// 设计要点：
// 1. 不使用 MNN 的 ChatMessages 模板机制。原因：本模型（Qwen2.5 旧版导出）config 里没有
//    jinja chat_template，MNN 的 apply_chat_template 会退化成「直接拼接内容」，丢掉角色标记。
//    因此我们在 C++ 侧按 Qwen2.5 官方格式手工拼 prompt，并在 config.json 里设置
//    use_template=false，把原始 prompt 直接喂给引擎。
// 2. 所有状态封装在 EngineHandle 里，指针以 jlong 交给 Kotlin 保存。
// 3. 异常不允许穿过 JNI 边界（MNN 本身以 -fno-exceptions 编译，这里也保持防御式写法）。

#include <jni.h>
#include <android/log.h>

#include <memory>
#include <mutex>
#include <sstream>
#include <string>

#include "llm/llm.hpp"

#define LOG_TAG "LocalLlm"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

using MNN::Transformer::Llm;
using MNN::Transformer::ChatMessage;
using MNN::Transformer::ChatMessages;

namespace {

struct EngineHandle {
    Llm* llm = nullptr;
    std::mutex mutex;  // 串行化对同一 Llm 实例的访问
};

std::string toUtf8(JNIEnv* env, jstring s) {
    if (s == nullptr) return std::string();
    const char* chars = env->GetStringUTFChars(s, nullptr);
    if (chars == nullptr) return std::string();
    std::string result(chars);
    env->ReleaseStringUTFChars(s, chars);
    return result;
}

// 按 Qwen2.5 ChatML 格式拼接 prompt（system 可选）。
std::string buildQwenPrompt(const std::string& system, const std::string& user) {
    std::string p;
    if (!system.empty()) {
        p += "<|im_start|>system\n";
        p += system;
        p += "<|im_end|>\n";
    }
    p += "<|im_start|>user\n";
    p += user;
    p += "<|im_end|>\n";
    p += "<|im_start|>assistant\n";
    return p;
}

// 去掉模型可能带出的结束标记与首尾空白。
std::string trimResult(std::string s) {
    const std::string eos = "<|im_end|>";
    size_t pos = s.find(eos);
    if (pos != std::string::npos) {
        s.erase(pos);
    }
    const char* ws = " \t\r\n";
    size_t begin = s.find_first_not_of(ws);
    if (begin == std::string::npos) return std::string();
    size_t end = s.find_last_not_of(ws);
    return s.substr(begin, end - begin + 1);
}

}  // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_com_example_qqaihelper_localllm_LocalLlmEngine_nativeCreate(JNIEnv* env, jobject /*thiz*/,
                                                                jstring configPath) {
    try {
        std::string path = toUtf8(env, configPath);
        if (path.empty()) {
            LOGE("nativeCreate: config 路径为空");
            return 0;
        }
        Llm* llm = Llm::createLLM(path);
        if (llm == nullptr) {
            LOGE("nativeCreate: createLLM 返回空，path=%s", path.c_str());
            return 0;
        }
        auto* handle = new EngineHandle();
        handle->llm = llm;
        LOGI("nativeCreate: 引擎句柄已创建，config=%s", path.c_str());
        return reinterpret_cast<jlong>(handle);
    } catch (const std::exception& e) {
        LOGE("nativeCreate 异常: %s", e.what());
        return 0;
    } catch (...) {
        LOGE("nativeCreate 未知异常");
        return 0;
    }
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_example_qqaihelper_localllm_LocalLlmEngine_nativeLoad(JNIEnv* /*env*/, jobject /*thiz*/,
                                                              jlong handlePtr) {
    auto* handle = reinterpret_cast<EngineHandle*>(handlePtr);
    if (handle == nullptr || handle->llm == nullptr) return JNI_FALSE;
    try {
        const bool ok = handle->llm->load();
        LOGI("nativeLoad: %s", ok ? "成功" : "失败");
        return ok ? JNI_TRUE : JNI_FALSE;
    } catch (const std::exception& e) {
        LOGE("nativeLoad 异常: %s", e.what());
        return JNI_FALSE;
    } catch (...) {
        LOGE("nativeLoad 未知异常");
        return JNI_FALSE;
    }
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_example_qqaihelper_localllm_LocalLlmEngine_nativeChat(JNIEnv* env, jobject /*thiz*/,
                                                              jlong handlePtr, jstring systemPrompt,
                                                              jstring userMessage, jint maxNewTokens) {
    auto* handle = reinterpret_cast<EngineHandle*>(handlePtr);
    if (handle == nullptr || handle->llm == nullptr) {
        return env->NewStringUTF("");
    }
    try {
        const std::string system = toUtf8(env, systemPrompt);
        const std::string user = toUtf8(env, userMessage);
        const std::string prompt = buildQwenPrompt(system, user);

        const int maxTokens = (maxNewTokens <= 0) ? 512 : static_cast<int>(maxNewTokens);

        std::lock_guard<std::mutex> lock(handle->mutex);
        std::ostringstream os;
        // 每次请求都清空 KV / 历史，保证消息之间互不串扰。
        handle->llm->reset();
        // use_template=false（见 config.json）：传入的是已经成型 Qwen prompt，直接推理。
        handle->llm->response(prompt, &os, "<|im_end|>", maxTokens);

        const std::string text = trimResult(os.str());
        LOGI("nativeChat: 输入 %zu 字，输出 %zu 字", user.size(), text.size());
        return env->NewStringUTF(text.c_str());
    } catch (const std::exception& e) {
        LOGE("nativeChat 异常: %s", e.what());
        return env->NewStringUTF("");
    } catch (...) {
        LOGE("nativeChat 未知异常");
        return env->NewStringUTF("");
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_qqaihelper_localllm_LocalLlmEngine_nativeRelease(JNIEnv* /*env*/, jobject /*thiz*/,
                                                                 jlong handlePtr) {
    auto* handle = reinterpret_cast<EngineHandle*>(handlePtr);
    if (handle == nullptr) return;
    try {
        if (handle->llm != nullptr) {
            Llm::destroy(handle->llm);
            handle->llm = nullptr;
        }
    } catch (...) {
        LOGE("nativeRelease 异常");
    }
    delete handle;
    LOGI("nativeRelease: 引擎已释放");
}
