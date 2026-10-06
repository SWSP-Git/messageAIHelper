plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.qqaihelper"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.qqaihelper"
        minSdk = 26
        targetSdk = 37
        versionCode = 7
        versionName = "1.2.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            // 本地模型插件仅提供 arm64-v8a 预编译库
            abiFilters += "arm64-v8a"
        }
    }

    buildTypes {
        release {
            optimization {
                enable = false
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    // 本地模型权重体积很大，禁止压缩打包（否则构建/运行都会出问题）
    androidResources {
        noCompress += listOf("mnn", "weight", "json", "txt", "bin")
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

dependencies {
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.nanohttpd:nanohttpd:2.3.1")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    // 本地模型插件（MNN 推理 + 模型资源 + 本地 OpenAI 兼容服务）
    implementation(project(":localllm"))
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}