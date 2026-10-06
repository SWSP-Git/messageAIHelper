plugins {
    // 复用根工程已声明的 AGP 版本（与 :app 同版本），此处不再指定版本。
    id("com.android.library")
}

/**
 * 本地模型「插件」模块。
 *
 * 设计原则：与主 App 完全解耦，只通过少量公开 API（LocalLlmManager）对外暴露能力。
 * 删除本模块 + settings.gradle.kts 里的 include(":localllm") 即可干净移除本地模型功能。
 *
 * 原生库为预编译产物（libMNN.so / liblocalllm.so，arm64-v8a），
 * 放在 src/main/jniLibs，由 CMake 工具链离线构建后拷入，避免 AGP 与 NDK 版本强耦合。
 */
android {
    namespace = "com.example.qqaihelper.localllm"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        minSdk = 26
        ndk {
            // 本地模型推理仅在 arm64-v8a 上提供预编译库
            abiFilters += "arm64-v8a"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    // 模型权重体积很大，必须禁止压缩，否则打包/运行都会出问题
    androidResources {
        noCompress += listOf("mnn", "weight", "json", "txt", "bin")
    }
}

dependencies {
    implementation("org.nanohttpd:nanohttpd:2.3.1")
}
