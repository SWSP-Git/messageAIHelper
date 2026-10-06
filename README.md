# messageAIHelper

[![Platform](https://img.shields.io/badge/Platform-Android-green.svg)](https://developer.android.com)
[![Language](https://img.shields.io/badge/Language-Kotlin-blue.svg)](https://kotlinlang.org)
[![License](https://img.shields.io/badge/License-GPL--3.0-orange.svg)](LICENSE)
[![Version](https://img.shields.io/badge/Version-1.1.1-brightgreen.svg)](https://github.com/SWSP-Git/messageAIHelper/releases)

---

## 📖 项目简介 (Introduction)

**messageAIHelper** 是一款基于 Android 系统通知监听与 AI 大模型接口的**本地化自动化助手**。

它可以实时捕获微信、QQ、钉钉等应用的通知消息，通过兼容 OpenAI 格式的 AI 接口（如硅基流动、DeepSeek 等）智能解析消息内容：

- 含明确时间的消息 → **自动静默写入手机系统日历**
- 纯信息类消息（验证码、取件码、账号等）→ **自动归档到备忘录**，关键信息以红字醒目展示
- AI 还会为每条消息生成**行动建议**，并可选结合用户现有日程做**冲突检测**

所有数据在手机本地处理，只有 AI 解析环节会将消息文本发送到你自己配置的 API。开发者不收集任何用户数据。

**messageAIHelper** is a **localized automation assistant** based on Android system notification listening and AI LLM APIs.

It captures notifications from apps like WeChat, QQ, and DingTalk, and parses them intelligently via OpenAI-compatible AI APIs (e.g., SiliconFlow, DeepSeek):

- Messages with clear time → **automatically written to the system calendar silently**
- Pure information (verification codes, pickup codes, account numbers) → **archived to Memo**, with key info highlighted in red
- AI also generates **action suggestions**, optionally checking **schedule conflicts** against existing calendar

All data is processed locally. Only the AI parsing step sends message text to your configured API.

---

## ✨ 核心特性 (Features)

### 🔔 消息监听与 AI 解析

- **多应用通知监听**：支持 QQ、微信、钉钉、企业微信等（可自定义白名单）
- **关键词过滤**：为每个应用单独配置关键词规则，支持 AND / OR 逻辑
- **智能防抖**：10 秒内重复通知自动去重，避免浪费 AI 额度
- **静默写入日历**：基于原生 `CalendarContract`，无需 Shizuku / MacroDroid
- **关键信息提取**：验证码、取件码、金额等以红字醒目展示
- **AI 智能建议**：为每条消息生成下一步行动建议
- **日程冲突检测**：写入前查询 ±30 分钟内既有日程，有冲突则提醒

### 📝 备忘录模块

- AI 自动识别「纯信息」类消息并归档
- 卡片式列表，关键信息红字加粗、AI 建议蓝字提示
- 数据存储于 App 私有目录，无需联网即可查看

### 🤖 三档日程结合模式

在「AI 与日程设置」页可配置：

1. **不结合日程**：AI 只生成行动建议，不检查冲突（本地/云端均可用）
2. **代码侧冲突检测**：写入日程前查询 ±30 分钟，有冲突则追加警告（本地/云端均可用）
3. **交给 AI 分析**：把用户未来 N 天日程塞进 prompt，由 AI 自行判断冲突（**仅云端模型可用**）

### 🔌 Webhook 开放端口

- 内置轻量级 HTTP 服务器（基于 NanoHTTPD）
- 局域网设备可通过 HTTP 请求向手机投递消息
- 支持 Token 鉴权 + 消息长度限制

### 🛡️ 后台保活

- **前台常驻服务**：通知栏显示处理消息计数，点击返回 App
- **1 像素透明悬浮窗**：让进程优先级提升，降低被杀概率
- **开机自启**：手机重启后自动恢复保活选项
- **电池优化白名单**：一键跳转系统设置

### 📋 应用内日志查看器

- 无需连接电脑，手机上即可查看运行日志
- 支持一键分享（通过 FileProvider 发出 .txt 附件）
- 日志文件自动限制 2MB，避免膨胀

### 🎨 UI / UX

- **深色主题**：除主页外所有页面采用深色背景 + 白色卡片
- **未保存更改保护**：设置页修改后返回会询问是否保存，防止误操作
- **卡片式布局**：功能分组清晰，视觉一致

---

### 🌐 English Version

### 🔔 Notification Monitoring & AI Parsing

- **Multi-app monitoring**: QQ, WeChat, DingTalk, WeCom (customizable whitelist)
- **Keyword filtering**: Per-app rules with AND / OR logic
- **Debouncing**: 10-second dedup to save AI quota
- **Silent calendar writing**: Native `CalendarContract`, no Shizuku/MacroDroid needed
- **Key info extraction**: Verification codes, pickup codes, amounts highlighted in red
- **AI suggestions**: Generates next-step action advice
- **Conflict detection**: Checks ±30 min for scheduling conflicts

### 📝 Memo Module

- AI automatically archives "pure information" messages
- Card-based list, key info in bold red, AI suggestions in blue
- Data stored in app's private directory, offline accessible

### 🤖 Three Schedule Integration Modes

Configurable in "AI & Schedule Settings":

1. **No integration**: AI only generates suggestions
2. **Code-side conflict check**: Queries ±30 min before writing
3. **AI analysis**: Feeds upcoming events into prompt (**cloud model only**)

### 🔌 Webhook HTTP Server

- Built-in lightweight HTTP server (NanoHTTPD)
- LAN devices can POST messages via HTTP
- Token auth + message length limit

### 🛡️ Background Keep-Alive

- **Foreground service**: Shows processed-message counter in notification
- **1-pixel transparent overlay**: Increases process priority
- **Boot auto-start**: Restores keep-alive options after reboot
- **Battery optimization whitelist**: One-tap jump to system settings

### 📋 In-App Log Viewer

- View runtime logs on phone without PC
- One-tap sharing via FileProvider
- Auto-truncates log file at 2MB

### 🎨 UI / UX

- **Dark theme**: All pages except home use dark background + white cards
- **Unsaved changes protection**: Prompts to save when leaving edited settings
- **Card-based layout**: Clear grouping and consistent visuals

---

## 🛠️ 系统要求 (Requirements)

- Android 8.0 (API 26) 及以上 / or higher
- 一个兼容 OpenAI 接口的 AI 服务，推荐 [硅基流动 SiliconFlow](https://siliconflow.cn/) / An OpenAI-compatible AI service (e.g., SiliconFlow)

---

## 🚀 使用指南 (Usage Guide)

### 1. 初始配置 (Initial Setup)

1. 安装 APK 并打开 App / Install APK and launch
2. 首次启动时，根据提示授予 **日历权限** 和 **通知使用权** / Grant Calendar & Notification Access
3. 从主页点击齿轮图标进入「配置中心」，填入 **API URL**、**API Key**、**模型名称** / Enter API config via gear icon on Home page

### 2. 配置监听与过滤 (Configure Monitoring)

1. 点击「📱 选择要监听的应用」勾选目标应用 / Select apps to monitor
2. 点击「🔍 配置过滤规则」添加关键词（AND/OR 逻辑）/ Configure keyword rules
3. 开启「启用消息监听服务」开关 / Enable the service switch

### 3. AI 与日程设置 (AI & Schedule Settings)

从「备忘录」页面右上角齿轮进入 / Enter via gear icon in Memo page:

- **本地模型开关**（UI 占位，功能开发中）
- **三档日程结合模式**（不结合 / 代码侧检测 / AI 分析）
- **未来日程查询天数**（1-30 天）

### 4. 更多设置 (Advanced Settings)

从配置中心底部进入 / Enter from bottom of Config Center:

- **后台保活**：「去优化」可设置电池白名单 + 前台服务 + 悬浮窗
- **定时开关**：设定处理时段（如 08:00 - 22:00）
- **Webhook 服务**：配置端口 + Token
- **查看运行日志**：应用内日志查看器

### 5. Webhook 使用示例 (Webhook Example)

在电脑或局域网其他设备上发送 HTTP 请求 / Send HTTP request from LAN:

```bash
http://<手机IP>:8080/?token=<您的Token>&msg=明天下午3点开会
```

成功返回 / Success response: `✅ 消息已成功交给 App 处理`

---

## 🧱 技术栈 (Tech Stack)

| 类别 | 技术 |
|------|------|
| **语言** | Kotlin |
| **网络** | OkHttp 4.x, Coroutines |
| **本地服务** | NanoHTTPD (Webhook) |
| **存储** | SharedPreferences (配置), 文件存储 (备忘/日志) |
| **系统 API** | NotificationListenerService, CalendarContract, WindowManager, Foreground Service |
| **架构** | Strategy Pattern (AI 引擎抽象), Observer Pattern (配置变更监听) |

---

## 📂 项目结构 (Project Structure)

```
app/src/main/java/com/example/qqaihelper/
├── HomeActivity.kt              # 主入口（卡片式仪表盘）
├── MainActivity.kt              # 配置中心（API、白名单、过滤规则）
├── SettingsActivity.kt          # 更多设置（保活、定时、Webhook）
├── AiOptionsActivity.kt         # AI 与日程设置（三档模式）
├── MemoListActivity.kt          # 备忘录列表
├── LogViewerActivity.kt         # 日志查看器
├── QQNotificationListener.kt    # 核心服务：监听 → AI → 写入
├── WebhookServer.kt             # 局域网 HTTP 服务
├── KeepAliveService.kt          # 前台保活服务
├── PixelWindowManager.kt        # 1 像素悬浮窗
├── BootReceiver.kt              # 开机自启
├── MemoAdapter.kt               # 备忘录列表适配器
└── AppLogger.kt                 # 全局日志工具
```

---

## 🔒 隐私与安全 (Privacy & Security)

- ✅ 所有配置数据（API Key、过滤规则、Token）仅保存在手机本地
- ✅ 备忘、日志存储在 App 私有目录，其他应用无法访问
- ✅ AI 解析仅将消息文本发送到**你自己配置的 API 服务商**
- ⚠️ Webhook 服务默认关闭，且仅在局域网内可访问，请设置强 Token
- ⚠️ 请勿使用不可信的 API 服务商

- ✅ All configs (API Key, filter rules, Token) stored locally
- ✅ Memos and logs stored in app's private directory
- ✅ AI parsing only sends text to **your configured API provider**
- ⚠️ Webhook disabled by default; only accessible from LAN; use strong Token
- ⚠️ Do not use untrusted API providers

---

## ⚠️ 免责声明 (Disclaimer)

1. 本软件仅供个人学习与效率提升使用，请勿用于任何非法用途
2. 由于使用系统通知监听，部分高度定制化的 ROM（如 MIUI、ColorOS）可能杀后台，需手动设置电池白名单
3. 开发者不对因系统限制、API 服务商问题导致的功能失效负责
4. This software is for personal learning and efficiency purposes only
5. Due to system notification listening, some customized ROMs may kill background processes; manual whitelist setup may be required
6. Developer is not responsible for failures caused by system restrictions or API provider issues

---

## 🤝 贡献与反馈 (Contributing & Feedback)

- 提交 Bug：[Issues](https://github.com/SWSP-Git/messageAIHelper/issues)
- 代码贡献：[贡献指南](CONTRIBUTING.md)
- 行为规范：[行为准则](CODE_OF_CONDUCT.md)
- 安全漏洞：[安全政策](SECURITY.md)
- 详细文档：[Wiki](https://github.com/SWSP-Git/messageAIHelper/wiki)

---

## 📄 开源协议 (License)

本项目采用 [GPL-3.0 License](LICENSE) 协议开源。

This project is open-sourced under the [GPL-3.0 License](LICENSE).

---

## 🌟 支持项目 (Support)

如果这个项目对你有帮助，欢迎点个 ⭐ Star 支持一下！

If this project helps you, feel free to give it a ⭐ Star!
