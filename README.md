# messageAIHelper

[![Platform](https://img.shields.io/badge/Platform-Android-green.svg)](https://developer.android.com)
[![Language](https://img.shields.io/badge/Language-Kotlin-blue.svg)](https://kotlinlang.org)
[![License](https://img.shields.io/badge/License-GPL--3.0-orange.svg)](LICENSE)
[![Version](https://img.shields.io/badge/Version-1.2.0-brightgreen.svg)](https://github.com/SWSP-Git/messageAIHelper/releases)
[![Android CI](https://github.com/SWSP-Git/messageAIHelper/actions/workflows/android-ci.yml/badge.svg)](https://github.com/SWSP-Git/messageAIHelper/actions/workflows/android-ci.yml)

> 🔔 捕获通知 · 🤖 AI 智能解析 · 📅 静默写日历 · 📝 智能归档备忘录 · 🌐 中英双语

---

## 📖 项目简介

**messageAIHelper** 是一款基于 Android 系统通知监听与 AI 大模型接口的**本地化自动化助手**。

它可以实时捕获微信、QQ、钉钉等应用的通知消息，通过兼容 OpenAI 格式的 AI 接口（如硅基流动、DeepSeek 等）智能解析消息内容：

- ✅ 含明确时间的消息 → **自动静默写入手机系统日历**
- ✅ 纯信息类消息（验证码、取件码、账号等）→ **自动归档到备忘录**，关键信息红字醒目展示
- ✅ AI 为每条消息生成**行动建议**，可选结合用户现有日程做**冲突检测**
- ✅ 备忘录支持**重要性分级**（高/中/低），默认只显示中高重要性的条目

**数据持久化**：备忘录默认写入外部存储（`/sdcard/Download/messageAIHelper/`），App 卸载重装不丢数据；同时支持 **ZIP 备份导出/导入**。

**国际化**：内置中英双语，切换即时生效。

所有数据在手机本地处理，只有 AI 解析环节会将消息文本发送到你自己配置的 API。开发者不收集任何用户数据。

**messageAIHelper** is a **localized automation assistant** based on Android notification listening and AI LLM APIs.

It captures notifications from WeChat, QQ, DingTalk, and parses them via OpenAI-compatible APIs:

- ✅ Messages with clear time → **silently written to system calendar**
- ✅ Pure information (codes, accounts, amounts) → **archived to Memo** with key info highlighted
- ✅ AI generates **action suggestions**, optionally checking **schedule conflicts**
- ✅ Memos support **importance levels** (High/Medium/Low), default shows only medium+ entries

**Data Persistence**: Memos stored in external storage (`/sdcard/Download/messageAIHelper/`), survives uninstall; also supports **ZIP backup export/import**.

**i18n**: Built-in Chinese/English with instant switching.

All data is processed locally. Only AI parsing sends message text to your configured API.

---

## ✨ 核心特性

### 🔔 消息监听与 AI 解析

| 功能 | 说明 |
|------|------|
| 📱 多应用通知监听 | QQ、微信、钉钉、企业微信（可自定义白名单） |
| 🔍 关键词过滤 | 每个应用独立规则，支持 **AND / OR** 逻辑 |
| ⚡ 智能防抖 | 10 秒内重复通知自动去重，节省 AI 额度 |
| 📅 静默写日历 | 原生 `CalendarContract`，无需 Shizuku / MacroDroid |
| 🔑 关键信息提取 | 验证码、取件码、金额以红字突出显示 |
| 💡 AI 智能建议 | 为每条消息生成下一步行动建议 |
| ⚠️ 日程冲突检测 | 写入前查询 ±30 分钟，有冲突则提醒 |
| ⭐ 重要性分级 | 高/中/低三档，默认过滤低重要性 |

### 📝 备忘录模块

- AI 自动识别「纯信息」类消息并归档
- 卡片式列表，关键信息红字加粗、AI 建议蓝字提示
- **重要性标签**：高红 / 中橙 / 低灰三色胶囊
- **智能过滤**：默认隐藏低重要性，一键切换显示全部
- 数据存储于外部存储，卸载重装不丢失

### 🌐 中英双语

- 完整覆盖所有 UI 文字
- 语言切换即时生效
- 支持「跟随系统」模式

### 💾 数据备份

- **外部存储持久化**：备忘录写入 Download 目录
- **ZIP 导出/导入**：一键备份所有数据（备忘录 + 配置）
- **存储位置指示**：清晰展示当前使用路径

### 🤖 三档日程结合模式

在「AI 与日程设置」页配置：

1. **不结合日程** — AI 只生成行动建议，不检查冲突（本地/云端均可用）
2. **代码侧冲突检测** — 写入前查询 ±30 分钟（本地/云端均可用）
3. **交给 AI 分析** — 把未来 N 天日程塞进 prompt，AI 自行判断（**仅云端模型可用**）

### 🔌 Webhook 开放端口

- 内置轻量级 HTTP 服务器（NanoHTTPD）
- 局域网设备可通过 HTTP 请求向手机投递消息
- Token 鉴权 + 消息长度限制

### 🛡️ 后台保活

- **前台常驻服务** — 通知栏显示处理消息计数
- **1 像素透明悬浮窗** — 提升进程优先级
- **开机自启** — 重启后自动恢复保活选项
- **电池优化白名单** — 一键跳转系统设置

### 📋 应用内日志查看器

- 无需电脑，手机上查看运行日志
- 一键分享（FileProvider 发出 .txt 附件）
- 日志自动限制 2MB 避免膨胀

### 🎨 UI / UX

- **深色主题** — 除主页外所有页面深色背景 + 白色卡片
- **未保存更改保护** — 修改后返回会询问是否保存
- **页面切换动画** — 完整划入划出，接近手机桌面翻页
- **Material Ripple** — 所有按钮的水波纹反馈
- **弹窗动画** — 缩放 + 淡入淡出

---

## 🛠️ 系统要求

- Android 8.0 (API 26) 及以上
- 兼容 OpenAI 格式的 AI 服务，推荐 [硅基流动 SiliconFlow](https://siliconflow.cn/)
- **推荐**：授予"所有文件访问权限"（Android 11+），让备忘录写入外部存储

---

## 🚀 使用指南

### 1. 初始配置

1. 安装 APK 并打开 App
2. 首次启动授予 **日历权限** 和 **通知使用权**
3. 主页 → 齿轮图标 → 「配置中心」→ 填入 API URL / Key / 模型名 → 保存

### 2. 授予存储权限（推荐）

1. 进入「更多设置」→ 找到「📁 存储位置」卡片
2. 如显示红色警告，点击「🔓 授予存储权限」
3. 在系统设置里打开「所有文件访问权限」
4. 返回 App 后看到绿色提示即为成功

### 3. 配置监听与过滤

1. 点击「📱 选择要监听的应用」勾选目标应用
2. 点击「🔍 配置过滤规则」添加关键词（AND/OR 逻辑）
3. 开启「启用消息监听服务」开关

### 4. 语言切换

「更多设置」→「🌐 语言 / Language」→ 选择中文 / English / 跟随系统

### 5. 数据备份

「更多设置」→「💾 数据备份」：
- **导出数据**：选择保存位置，生成 ZIP
- **导入数据**：选择 ZIP 文件，恢复数据

### 6. Webhook 使用示例

```bash
curl "http://<手机IP>:8080/?token=<您的Token>&msg=明天下午3点开会"
```

成功返回：`✅ 消息已成功交给 App 处理`

---

## 🧱 技术栈

| 类别 | 技术 |
|------|------|
| 语言 | Kotlin |
| 网络 | OkHttp 4.x (HTTP/1.1), Coroutines |
| 本地服务 | NanoHTTPD (Webhook) |
| 存储 | SharedPreferences + 外部文件存储 |
| 系统 API | NotificationListenerService, CalendarContract, WindowManager, Foreground Service, Storage Access Framework |
| 国际化 | AppCompatDelegate.setApplicationLocales |
| 架构 | Strategy Pattern (AI 引擎抽象), Observer Pattern (配置监听) |

---

## 📂 项目结构

```
app/src/main/java/com/example/qqaihelper/
├── BaseActivity.kt              # Activity 基类（统一页面切换动画）
├── HomeActivity.kt              # 主入口（卡片式仪表盘）
├── MainActivity.kt              # 配置中心（API、白名单、过滤）
├── SettingsActivity.kt          # 更多设置（保活、定时、Webhook、语言、备份）
├── AiOptionsActivity.kt         # AI 与日程设置（三档模式）
├── MemoListActivity.kt          # 备忘录列表（重要性过滤）
├── LogViewerActivity.kt         # 日志查看器
├── QQNotificationListener.kt    # 核心服务：监听 → AI → 写入
├── WebhookServer.kt             # 局域网 HTTP 服务
├── KeepAliveService.kt          # 前台保活服务
├── PixelWindowManager.kt        # 1 像素悬浮窗
├── BootReceiver.kt              # 开机自启
├── MemoAdapter.kt               # 备忘录适配器（重要性标签渲染）
├── LocaleHelper.kt              # 语言管理工具
├── StorageHelper.kt             # 存储路径管理（外部优先，私有降级）
├── DataExporter.kt              # 数据导出（ZIP）
├── DataImporter.kt              # 数据导入（ZIP）
└── AppLogger.kt                 # 全局日志工具
```

---

## 🔒 隐私与安全

- ✅ 用户配置（API Key、过滤规则、Token）仅保存在手机本地 SharedPreferences
- ✅ 备忘录存储在设备外部存储（Download 目录），用户可直接查看
- ✅ 日志存储在 App 私有目录
- ✅ AI 解析仅将消息文本发送到**你自己配置的 API 服务商**
- ✅ 导出的 ZIP 备份包含敏感信息，请妥善保管
- ⚠️ Webhook 默认关闭，仅局域网可访问，请设置强 Token
- ⚠️ 请勿使用不可信的 API 服务商

---

## ⚠️ 免责声明

1. 本软件仅供个人学习与效率提升使用，请勿用于任何非法用途
2. 系统通知监听方案下，部分定制 ROM（MIUI、ColorOS 等）可能杀后台，需手动设置电池白名单
3. 开发者不对因系统限制、API 服务商问题导致的功能失效负责

---

## 🤝 贡献与反馈

| 类型 | 链接 |
|------|------|
| 提交 Bug | [Issues](https://github.com/SWSP-Git/messageAIHelper/issues) |
| 代码贡献 | [贡献指南](CONTRIBUTING.md) |
| 行为规范 | [行为准则](CODE_OF_CONDUCT.md) |
| 安全漏洞 | [安全政策](SECURITY.md) |
| 更新日志 | [CHANGELOG.md](CHANGELOG.md) |
| 详细文档 | [Wiki](https://github.com/SWSP-Git/messageAIHelper/wiki) |

---

## 📄 开源协议

本项目采用 [GPL-3.0 License](LICENSE) 协议开源。

---

## 🌟 支持项目

如果这个项目对你有帮助，欢迎点个 ⭐ **Star** 支持一下！

---

---

# messageAIHelper (English)

> 🔔 Capture notifications · 🤖 AI parsing · 📅 Silent calendar · 📝 Smart memo · 🌐 Bilingual

## 📖 Introduction

**messageAIHelper** is a **localized automation assistant** based on Android notification listening and AI LLM APIs.

It captures notifications from WeChat, QQ, DingTalk, and parses them via OpenAI-compatible APIs:

- ✅ Messages with clear time → **silently written to system calendar**
- ✅ Pure information (codes, accounts, amounts) → **archived to Memo** with key info highlighted in red
- ✅ AI generates **action suggestions**, optionally checking **schedule conflicts**
- ✅ Memos support **importance levels** (High/Medium/Low), default shows only medium+ entries

**Data Persistence**: Memos stored in external storage (`/sdcard/Download/messageAIHelper/`), survives uninstall; also supports **ZIP backup export/import**.

**i18n**: Built-in Chinese/English with instant switching.

All data processed locally. Only AI parsing sends message text to your configured API.

## ✨ Features

### 🔔 Notification Monitoring & AI Parsing

| Feature | Description |
|---------|-------------|
| 📱 Multi-app monitoring | QQ, WeChat, DingTalk, WeCom (customizable) |
| 🔍 Keyword filtering | Per-app rules with **AND / OR** logic |
| ⚡ Debouncing | 10-second dedup to save AI quota |
| 📅 Silent calendar write | Native `CalendarContract`, no Shizuku/MacroDroid |
| 🔑 Key info extraction | Codes, pickup IDs, amounts highlighted in red |
| 💡 AI suggestions | Next-step action advice per message |
| ⚠️ Conflict detection | Checks ±30 min before writing |
| ⭐ Importance levels | High/Medium/Low, low entries filtered by default |

### 📝 Memo Module

- AI auto-archives "pure information" messages
- Card-based list with key info in red, AI suggestions in blue
- **Importance tags**: red/orange/gray pills for High/Medium/Low
- **Smart filter**: hides low-importance by default, one-tap to show all
- Data survives app uninstall (external storage)

### 🌐 Bilingual (Chinese / English)

- Full UI coverage
- Instant language switching
- "Follow System" option

### 💾 Data Backup

- **External storage persistence**: memos written to Download folder
- **ZIP export/import**: one-tap backup of all data
- **Storage status indicator**

### 🤖 Three Schedule Integration Modes

1. **No Integration** — AI only generates suggestions
2. **Code-Side Conflict Check** — Queries ±30 min before writing
3. **AI Analysis** — Feeds upcoming events into prompt (**cloud model only**)

### 🔌 Webhook HTTP Server

- Built-in lightweight HTTP server (NanoHTTPD)
- LAN devices can POST messages via HTTP
- Token auth + message length limit

### 🛡️ Background Keep-Alive

- **Foreground service** with processed-message counter
- **1-pixel transparent overlay** to boost process priority
- **Boot auto-start**
- **Battery optimization whitelist**

### 📋 In-App Log Viewer

- View runtime logs without PC
- One-tap sharing via FileProvider
- Auto-truncates log file at 2MB

### 🎨 UI / UX

- **Dark theme** for all pages except home
- **Unsaved changes protection**
- **Page transition animations** — full slide-in/out
- **Material Ripple** on all buttons
- **Dialog animations** — scale + fade

## 🛠️ Requirements

- Android 8.0 (API 26) or higher
- OpenAI-compatible AI service (e.g., [SiliconFlow](https://siliconflow.cn/))
- **Recommended**: Grant "All files access" (Android 11+) for external storage

## 🚀 Usage Guide

### 1. Initial Setup
1. Install APK and launch
2. Grant **Calendar** and **Notification Access** permissions
3. Home → Gear icon → Config Center → Enter API URL / Key / Model

### 2. Grant Storage Permission (Recommended)
1. Go to "More Settings" → "📁 Storage Location"
2. If red warning, tap "🔓 Grant Storage Permission"
3. Enable "All files access" in system settings
4. Return to app; green indicator means success

### 3. Configure Monitoring
1. Tap "📱 Select Apps to Monitor"
2. Tap "🔍 Configure Filter Rules" to add keywords (AND/OR)
3. Enable the monitoring service switch

### 4. Language Switch
"More Settings" → "🌐 Language / 语言" → Choose 中文 / English / Follow System

### 5. Data Backup
"More Settings" → "💾 Data Backup":
- **Export**: choose location, generates ZIP
- **Import**: pick ZIP to restore

### 6. Webhook Example

```bash
curl "http://<phone-IP>:8080/?token=<your-token>&msg=Meeting tomorrow 3pm"
```

Success: `✅ 消息已成功交给 App 处理`

## 🧱 Tech Stack

| Category | Tech |
|----------|------|
| Language | Kotlin |
| Network | OkHttp 4.x (HTTP/1.1), Coroutines |
| Local Server | NanoHTTPD |
| Storage | SharedPreferences + External file storage |
| System APIs | NotificationListenerService, CalendarContract, WindowManager, Foreground Service, SAF |
| i18n | AppCompatDelegate.setApplicationLocales |
| Patterns | Strategy (AI Engine), Observer (Config) |

## 📂 Project Structure

```
app/src/main/java/com/example/qqaihelper/
├── BaseActivity.kt              # Activity base (transition animations)
├── HomeActivity.kt              # Home dashboard
├── MainActivity.kt              # Config Center (API, whitelist, filters)
├── SettingsActivity.kt          # More Settings (keep-alive, schedule, webhook, language, backup)
├── AiOptionsActivity.kt         # AI & Schedule Settings
├── MemoListActivity.kt          # Memo list (importance filter)
├── LogViewerActivity.kt         # Log viewer
├── QQNotificationListener.kt    # Core service: listen → AI → write
├── WebhookServer.kt             # LAN HTTP server
├── KeepAliveService.kt          # Foreground keep-alive service
├── PixelWindowManager.kt        # 1-pixel overlay
├── BootReceiver.kt              # Boot auto-start
├── MemoAdapter.kt               # Memo adapter (importance rendering)
├── LocaleHelper.kt              # Language management
├── StorageHelper.kt             # Storage path manager (external-first)
├── DataExporter.kt              # Data export (ZIP)
├── DataImporter.kt              # Data import (ZIP)
└── AppLogger.kt                 # Global logger
```

## 🔒 Privacy & Security

- ✅ User configs (API Key, filters, tokens) stored locally in SharedPreferences
- ✅ Memos stored in device external storage (Download folder)
- ✅ Logs stored in app's private directory
- ✅ AI parsing only sends text to **your configured API provider**
- ✅ Exported ZIP contains sensitive info; keep it safe
- ⚠️ Webhook disabled by default, LAN-only; use strong Token
- ⚠️ Do not use untrusted API providers

## ⚠️ Disclaimer

1. For personal learning and efficiency purposes only
2. Some customized ROMs (MIUI, ColorOS) may kill background processes; manual whitelist setup required
3. Developer not responsible for failures caused by system restrictions or API provider issues

## 🤝 Contributing & Feedback

| Type | Link |
|------|------|
| Report bugs | [Issues](https://github.com/SWSP-Git/messageAIHelper/issues) |
| Code contribution | [CONTRIBUTING.md](CONTRIBUTING.md) |
| Code of conduct | [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md) |
| Security policy | [SECURITY.md](SECURITY.md) |
| Changelog | [CHANGELOG.md](CHANGELOG.md) |
| Full docs | [Wiki](https://github.com/SWSP-Git/messageAIHelper/wiki) |

## 📄 License

This project is open-sourced under the [GPL-3.0 License](LICENSE).

## 🌟 Support

If this project helps you, please give it a ⭐ **Star**!
