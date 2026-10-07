import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

// ===== Release 签名配置 =====
// 从 local.properties 读取（该文件已 gitignore，不入库）。
// CI 环境没有该文件时 hasSigningConfig=false，构建照常进行（只是不生成签名包）。
val keystoreProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val hasSigningConfig =
    !keystoreProps.getProperty("RELEASE_STORE_FILE").isNullOrBlank() &&
    !keystoreProps.getProperty("RELEASE_STORE_PASSWORD").isNullOrBlank() &&
    !keystoreProps.getProperty("RELEASE_KEY_PASSWORD").isNullOrBlank()

android {
    namespace = "com.example.qqaihelper"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.qqaihelper"
        minSdk = 26
        targetSdk = 37
        versionCode = 9
        versionName = "1.2.2"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            // 本地模型插件仅提供 arm64-v8a 预编译库
            abiFilters += "arm64-v8a"
        }
    }

    signingConfigs {
        // 仅当 local.properties 提供了完整签名信息时才创建（CI 无此文件则跳过）
        if (hasSigningConfig) {
            create("release") {
                storeFile = file(keystoreProps.getProperty("RELEASE_STORE_FILE"))
                storePassword = keystoreProps.getProperty("RELEASE_STORE_PASSWORD")
                keyAlias = keystoreProps.getProperty("RELEASE_KEY_ALIAS")
                keyPassword = keystoreProps.getProperty("RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            if (hasSigningConfig) {
                signingConfig = signingConfigs.getByName("release")
            }
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