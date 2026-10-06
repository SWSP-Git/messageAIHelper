# localllm —— 本地模型插件

给 `messageAIHelper` 提供**离线本地大模型**能力的独立模块（Gradle library module）。

## 它做什么

- 用 MNN 引擎在手机本地运行 **Qwen2.5-1.5B-Instruct（4bit 量化）**；
- 在 `127.0.0.1` 的**预留端口**（默认 `8090`）上暴露 **OpenAI 兼容**接口
  `POST /v1/chat/completions`，主 App 无需改动协议即可在「本地 / 云端」之间切换；
- 模型权重随 APK 一起分发（`src/main/assets/models/qwen2.5-1.5b-instruct-mnn`），
  首次使用解压到应用私有目录。

## 目录结构

```
localllm/
├── build.gradle.kts              # 库模块配置（arm64-v8a，禁止压缩模型）
├── build_native.ps1              # 离线构建原生库的脚本
└── src/main/
    ├── assets/models/...         # 打包进 APK 的模型（~880MB，不入库）
    ├── cpp/
    │   ├── llm_jni.cpp           # MNN Llm 的 JNI 桥
    │   └── CMakeLists.txt
    ├── jniLibs/arm64-v8a/        # 预编译产物：libMNN.so / liblocalllm.so / libc++_shared.so
    └── java/com/example/qqaihelper/localllm/
        ├── LocalLlmEngine.kt     # JNI 桥接 + 引擎生命周期
        ├── ModelInstaller.kt     # assets -> filesDir 解压
        ├── LocalLlmServer.kt     # 本地 OpenAI 兼容服务
        └── LocalLlmManager.kt    # 对外总控 API
```

## 主 App 接入点

- `AiOptionsActivity`：模型运行位置二选一（云端 / 本地）、端口、安装/加载、状态；
- `QQNotificationListener`：按设置决定把 AI 请求发往云端还是本地服务。

## 构建原生库

```powershell
powershell -ExecutionPolicy Bypass -File .\localllm\build_native.ps1
```

需要 Android NDK（默认 `%LOCALAPPDATA%\Android\Sdk\ndk\27.2.12479018`）、`cmake`、`ninja`。
脚本会构建 `libMNN.so`（开启 `MNN_BUILD_LLM`）与 `liblocalllm.so`，并拷入 `jniLibs`。

## 移除方式

1. 删除 `:localllm` 的 `implementation(project(":localllm"))`；
2. 删除 `settings.gradle.kts` 中的 `include(":localllm")`；
3. 删除 `localllm/` 目录。

主 App 其余逻辑（云端模型）不受影响。
