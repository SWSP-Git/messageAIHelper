---

messageAIHelper

https://img.shields.io/badge/Platform-Android-green.svg
https://img.shields.io/badge/Language-Kotlin-blue.svg
https://img.shields.io/badge/License-MIT-orange.svg

---

📖 项目简介 (Introduction)

messageAIHelper 是一款基于 Android 系统通知监听与 AI 大模型接口的自动化日程助手。

它可以实时捕获微信、QQ、钉钉等应用的系统通知消息，通过调用兼容 OpenAI 格式的 AI 接口（如硅基流动、DeepSeek 等）对消息内容进行智能解析。如果消息中包含待办事项和时间信息，App 会自动将日程静默写入您的手机系统日历中。

messageAIHelper is an automated schedule assistant based on Android system notification listening and AI Large Language Model (LLM) APIs.

It captures system notifications from apps like WeChat, QQ, DingTalk, etc., in real-time. By calling OpenAI-compatible AI APIs (such as SiliconFlow, DeepSeek), it intelligently parses the message content. If the message contains a to-do item and time information, the app will automatically write the schedule silently into your phone's system calendar.

---

✨ 核心特性 (Features)

· 📱 多应用通知监听：支持实时监听 QQ、微信、钉钉、企业微信等应用的通知消息。
· 📱 Multi-App Notification Listening: Supports real-time monitoring of notifications from QQ, WeChat, DingTalk, WeCom, etc.
· 🤖 AI 智能解析：接入兼容 OpenAI 格式的 API，自动提取发送人、摘要、待办事项及推算日程时间。
· 📅 静默写入日历：无需 Shizuku 或 MacroDroid，App 原生利用 CalendarContract 实现完全静默的日历写入。
· 🔍 自定义过滤规则：为每个被监听的应用单独设置关键词过滤（支持“与 (AND)”和“或 (OR)”逻辑）。
· 🔌 Webhook 开放端口：内置轻量级 HTTP 服务器（基于 NanoHTTPD），支持通过局域网向手机发送指令，实现外部自动化联动。
· ⏰ 定时开关控制：支持设定时间段，让 App 在指定时间范围内自动开启/暂停服务。
· 🔒 本地隐私保护：所有配置数据（API Key、过滤规则、Token）仅保存在您的手机本地，无任何云端上传。
· 📱 Multi-App Notification Listening: Supports real-time monitoring of notifications from QQ, WeChat, DingTalk, WeCom, etc.
· 🤖 AI-Powered Parsing: Connects to OpenAI-compatible APIs to automatically extract senders, summaries, to-dos, and estimate schedule times.
· 📅 Silent Calendar Writing: Uses native CalendarContract for completely silent calendar insertion without needing Shizuku or MacroDroid.
· 🔍 Custom Filter Rules: Set keyword filters for each monitored app individually (supports "AND" and "OR" logic).
· 🔌 Webhook Open Port: Built-in lightweight HTTP server (based on NanoHTTPD). Supports receiving commands via LAN for external automation.
· ⏰ Scheduled Switch Control: Set a time range for the service to automatically start/pause.
· 🔒 Local Privacy Protection: All configuration data (API Key, filter rules, Token) is stored solely on your local device. No cloud uploads.

---

🛠️ 系统要求 (Requirements)

· Android 8.0 (API 26) 及以上
· 一个兼容 OpenAI 接口的 AI 服务（如 硅基流动 SiliconFlow）
· Android 8.0 (API 26) or higher
· An OpenAI-compatible AI service (e.g., SiliconFlow)

---

🚀 使用指南 (Usage Guide)

1. 初始配置 (Initial Setup)

1. 安装 APK 并打开 App。
2. 首次启动时，根据提示授予 日历权限 和 通知使用权。
3. 在主界面填写您的 API URL、API Key 和 模型名称（例如 deepseek-ai/DeepSeek-V3），点击“保存设置”。

2. 配置监听与过滤 (Configure Monitoring & Filtering)

1. 点击 “选择要监听的应用”，勾选您需要监听的软件（如微信、QQ）。
2. 点击 “配置过滤规则”，选择应用，动态添加关键词（支持 AND/OR 逻辑），点击保存。
3. 开启 “启用消息监听服务” 的开关。

3. 更多设置 (Advanced Settings)

· 定时开关：开启后，App 只在设定的时间范围内处理消息。
· Webhook 服务：开启后，可设置端口（如 8080）和通信密钥（Token）。
· 后台保活：点击“去设置电池优化”，将 App 加入系统白名单，防止被系统杀后台。

4. Webhook 使用示例 (Webhook Example)

在电脑或局域网其他设备上，向手机发送 HTTP 请求（需替换您的手机 IP、端口、Token 和消息内容）：

```bash
http://<手机IP>:8080/?token=<您的Token>&msg=明天下午3点开会
```

成功返回：✅ 消息已成功交给 App 处理

---

🧱 技术栈 (Tech Stack)

· Language: Kotlin
· Network: OkHttp 4.x, Coroutines
· Local Server: NanoHTTPD
· Data Storage: SharedPreferences
· System API: NotificationListenerService, CalendarContract

---

⚠️ 免责声明 (Disclaimer)

1. 本软件仅供个人学习与效率提升使用，请勿用于任何非法用途。
2. 软件在本地处理数据，但 AI 解析部分需将消息文本发送至您配置的第三方 API，请确保您的 API 服务商可信。
3. 由于使用了系统通知监听，部分高度定制化的 ROM（如 MIUI、ColorOS 等）可能会杀后台，需要您手动设置电池白名单。开发者不对因系统限制导致的功能失效负责。
4. This software is for personal learning and efficiency purposes only. Do not use it for any illegal activities.
5. Data is processed locally, but the AI parsing part requires sending message text to the third-party API you configured. Please ensure your API provider is trustworthy.
6. Due to the use of system notification listening, some highly customized ROMs (e.g., MIUI, ColorOS) may kill background processes. You need to manually set a battery whitelist. The developer is not responsible for functional failures caused by system restrictions.

---

📄 开源协议 (License)

本项目采用 [GPL-3.0 License](LICENSE) 协议开源。
This project is open-sourced under the [GPL-3.0 License](LICENSE).

---
