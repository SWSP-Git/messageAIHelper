// llm_jni.cpp
//
// 本地大模型插件的 JNI 桥。把 MNN 的 LLM 引擎（libMNN.so 中的 MNN::Transformer::Llm）
// 包装成 LocalLlmEngine.kt 里的 4 个 native 方法。
//
// 【提示词模板可配置】v1.2.3 起不再硬编码 Qwen 格式。
// 插件包的 config.json 可选提供以下字段，以适配不同模型的对话格式：
//   "prompt_template"           含系统提示的模板，占位符 {system} / {user}
//   "prompt_template_no_system" 不含系统提示的模板，占位符 {user}
//   "eos_token"                 结束标记（默认 "<|im_end|>"）
// 若未提供，回退到 Qwen2.5 的 ChatML 格式（保证旧插件兼容）。

#include <jni.h>
#include <android/log.h>

#include <fstream>
#include <memory>
#include <mutex>
#include <sstream>
#include <string>

#include "llm/llm.hpp"

#define LOG_TAG "LocalLlm"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

using MNN::Transformer::Llm;

namespace {

// ---- 默认模板（Qwen2.5 ChatML），保证旧插件兼容 ----
const char* kDefaultTplWithSystem =
    "<|im_start|>system\n{system}<|im_end|>\n"
    "<|im_start|>user\n{user}<|im_end|>\n"
    "<|im_start|>assistant\n";
const char* kDefaultTplNoSystem =
    "<|im_start|>user\n{user}<|im_end|>\n"
    "<|im_start|>assistant\n";
const char* kDefaultEos = "<|im_end|>";

struct EngineHandle {
    Llm* llm = nullptr;
    std::mutex mutex;
    std::string tplWithSystem;
    std::string tplNoSystem;
    std::string eosToken;
};

std::string toUtf8(JNIEnv* env, jstring s) {
    if (s == nullptr) return std::string();
    const char* chars = env->GetStringUTFChars(s, nullptr);
    if (chars == nullptr) return std::string();
    std::string result(chars);
    env->ReleaseStringUTFChars(s, chars);
    return result;
}

std::string readFile(const std::string& path) {
    std::ifstream ifs(path, std::ios::binary);
    if (!ifs) return std::string();
    std::ostringstream os;
    os << ifs.rdbuf();
    return os.str();
}

// 从 JSON 文本中提取字符串字段（极简实现，足够解析 config.json）
std::string jsonString(const std::string& json, const std::string& key) {
    const std::string needle = "\"" + key + "\"";
    size_t p = json.find(needle);
    if (p == std::string::npos) return std::string();
    p = json.find(':', p + needle.size());
    if (p == std::string::npos) return std::string();
    p = json.find('"', p + 1);
    if (p == std::string::npos) return std::string();
    std::string out;
    for (size_t i = p + 1; i < json.size(); ++i) {
        char c = json[i];
        if (c == '\\' && i + 1 < json.size()) {
            char n = json[i + 1];
            switch (n) {
                case 'n': out += '\n'; break;
                case 't': out += '\t'; break;
                case 'r': out += '\r'; break;
                case '"': out += '"'; break;
                case '\\': out += '\\'; break;
                case '/': out += '/'; break;
                default: out += n; break;
            }
            ++i;
        } else if (c == '"') {
            break;
        } else {
            out += c;
        }
    }
    return out;
}

std::string replaceAll(std::string s, const std::string& from, const std::string& to) {
    if (from.empty()) return s;
    size_t p = 0;
    while ((p = s.find(from, p)) != std::string::npos) {
        s.replace(p, from.size(), to);
        p += to.size();
    }
    return s;
}

std::string buildPrompt(const EngineHandle* h, const std::string& system, const std::string& user) {
    const std::string& tpl = system.empty() ? h->tplNoSystem : h->tplWithSystem;
    std::string p = replaceAll(tpl, "{system}", system);
    p = replaceAll(p, "{user}", user);
    return p;
}

std::string trimResult(std::string s, const std::string& eos) {
    if (!eos.empty()) {
        size_t pos = s.find(eos);
        if (pos != std::string::npos) s.erase(pos);
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
        handle->tplWithSystem = kDefaultTplWithSystem;
        handle->tplNoSystem = kDefaultTplNoSystem;
        handle->eosToken = kDefaultEos;

        std::string cfg = readFile(path);
        if (!cfg.empty()) {
            std::string t1 = jsonString(cfg, "prompt_template");
            std::string t2 = jsonString(cfg, "prompt_template_no_system");
            std::string eos = jsonString(cfg, "eos_token");
            if (!t1.empty()) handle->tplWithSystem = t1;
            if (!t2.empty()) handle->tplNoSystem = t2;
            if (!eos.empty()) handle->eosToken = eos;
            LOGI("nativeCreate: 模板已加载 (with_system=%d, no_system=%d, eos=%s)",
                 (int)!t1.empty(), (int)!t2.empty(), handle->eosToken.c_str());
        }
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
        const std::string prompt = buildPrompt(handle, system, user);

        const int maxTokens = (maxNewTokens <= 0) ? 512 : static_cast<int>(maxNewTokens);

        std::lock_guard<std::mutex> lock(handle->mutex);
        std::ostringstream os;
        handle->llm->reset();
        handle->llm->response(prompt, &os, handle->eosToken.c_str(), maxTokens);

        const std::string text = trimResult(os.str(), handle->eosToken);
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
