# 更新日志 (Changelog)

本项目所有值得注意的变更都会记录在此文件。

All notable changes to this project are documented in this file.

格式遵循 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/)，版本号遵循 [语义化版本](https://semver.org/lang/zh-CN/)。

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project adheres to [Semantic Versioning](https://semver.org/).

---

## [未发布 / Unreleased]

### 计划中 / Planned
- 🤖 本地小模型集成（AIEngine 抽象接口 + MediaPipe / ONNX Runtime Mobile），见 [Roadmap](https://github.com/SWSP-Git/messageAIHelper/wiki/Roadmap) 阶段三。

---

## [v1.1.2] - 2026-10-06

> 数据持久化 · 中英双语 · 动效升级

### 新增 / Added
- **🛡️ 备忘录外置存储**：默认写入 `/sdcard/Download/messageAIHelper/memo_list.txt`，卸载 / 覆盖安装不再丢失数据；首次使用时自动从私有目录迁移旧数据。
- **💾 数据备份（导出 / 导入）**：一键导出全部数据为 ZIP（备忘录 + 配置），支持从 ZIP 恢复；基于 SAF，无需任何权限。导出文件名 `messageAIHelper_backup_yyyyMMdd_HHmmss.zip`。
- **📁 存储位置指示器**：「更多设置」新增卡片，绿色=外部存储就绪，红色=正在使用私有目录，可一键跳转系统设置授权。
- **🌐 中英双语**：「更多设置」新增语言选项（中文 / English / 跟随系统），切换即时生效，基于 `AppCompatDelegate.setApplicationLocales()`。
- **⭐ 备忘录重要性标签**：高（红）/ 中（橙）/ 低（灰）三色胶囊；默认只显示中高重要性，底部按钮可切换显示全部；老数据默认视为「中」，升级不丢失。
- **✨ 动效升级**：页面完整划入划出（100% 位移，320ms）、按钮 Material Ripple 水波纹、弹窗缩放 + 淡入淡出。

### 修复 / Fixed
- **修复 HTTP/2 stream 超时**：禁用 HTTP/2（仅 HTTP/1.1），让 `readTimeout` 正常生效，大模型长响应不再被误杀。
- **修复明文 HTTP 被拦**：恢复 `usesCleartextTraffic="true"`。
- **网络超时策略优化**：`readTimeout` 60s→**90s**，新增 `callTimeout` **110s**。
- **异常日志增强**：记录异常类型 + 完整堆栈、请求 URL / 模型名 / 消息长度、HTTP 错误响应前 500 字符。

---

## [v1.1.1] - 2026-10-06

> 防误触保护 · 深度保活 · AI 日程冲突检测

### 新增 / Added
- **🛡️ 后台保活模块（全新）**：前台常驻服务 `KeepAliveService`（通知栏显示处理计数，被回收后自动重启）、1 像素透明悬浮窗 `PixelWindowManager`、开机自启 `BootReceiver`、优化设置弹窗（电池优化白名单）。
- **🤖 AI 与日程设置页（全新）**：本地模型开关（UI 占位）、三档日程结合模式（①不结合 ②代码侧冲突检测 ③交给 AI 分析）、未来日程查询天数 1–30 天可配置。
- **📝 备忘录智能字段**：`【关键信息】`红字加粗展示（验证码 / 取件码 / 账号 / 金额），`【AI建议】`蓝字提示。
- **💾 未保存更改保护**：三个设置页修改后返回会弹窗询问「保存 / 不保存 / 取消」，基于 `OnBackPressedCallback`。

### 优化 / Changed
- **🎨 深色 UI 主题**：除主页外全部页面深色背景（#252A31）+ 白色卡片 + 亮色按钮（主 #2979FF / 次 #00B0FF）。
- **配置中心 / 更多设置页重新设计**：卡片式分组、圆角输入框、开关小字说明。
- **主页背景**：纯白 #F8F9FA → 柔和浅灰 #E8EAED。
- **代码优化**：`QQNotificationListener` 中 SharedPreferences 改懒加载、正则预编译为常量、系统提示词模板化、魔法数字集中为命名常量；新增 `detectConflict()` / `queryUpcomingEvents()` 工具方法。

### 修复 / Fixed
- **系统夜间模式下文字对比度过低**：主题统一为 `Theme.Material3.Light.NoActionBar` 并加 `forceDarkAllowed="false"`。
- **AI 提示词丢失关键数据**：提示词新增「请原样照抄」指令与独立 `【关键信息】` 字段。
- **启动 Activity 使用废弃 API**：`startActivityForResult` → `registerForActivityResult`。

---

## [v1.1.0] - 2026-10-06

> 全新主页与备忘录功能

### 新增 / Added
- **🏠 全新主页**：卡片式布局仪表盘，右上角齿轮快速进入设置。
- **📝 备忘录模块**：AI 自动识别「纯信息」类消息（账号、电话、取件码等）并归档，App 内随时查看。
- **📋 应用内日志查看器**：无需连接电脑即可查看运行日志，支持一键分享。

### 优化 / Changed
- 修复所有页面内容左右贴边问题，统一留白观感。
- 「更多设置」页支持滚动，小屏幕设备可完整查看。
- 不再跟踪 `app/release/` 打包产物与 `.idea/` 本地 IDE 配置。
- 所有核心 Kotlin 代码添加详细中文注释。

### 修复 / Fixed
- **修复关键词过滤失效**：早期因应用中文名与包名不匹配，过滤规则实际从未生效，现已修复。
- **Webhook 默认关闭**：避免新用户未配置时意外暴露局域网端口。
- 消除 4 个 Kotlin nullable 编译警告。

---

## [v1.0.1] - 2026-10-05

### 新增 / Added
- **📋 应用内日志查看器**：将 Logcat 输出写入本地文件（`Context.getExternalFilesDir()`），App 内新增「日志查看」界面，支持导出 / 分享，日志超过 2MB 自动截断。

---

## [v1.0.0] - 2026-10-05

> 首次开源发布 / First Release

### 新增 / Added
- 🔔 监听 QQ / 微信 / 钉钉等应用通知。
- 🤖 接入兼容 OpenAI 格式的 AI 接口，自动解析消息与待办。
- 📅 静默写入系统日历（原生 `CalendarContract`，无需 Shizuku / MacroDroid）。
- 🔌 Webhook 开放端口（NanoHTTPD），局域网设备可投递消息。
- 🌐 内置中英双语。

---

[未发布 / Unreleased]: https://github.com/SWSP-Git/messageAIHelper/compare/v1.1.2...HEAD
[v1.1.2]: https://github.com/SWSP-Git/messageAIHelper/compare/v1.1.1...v1.1.2
[v1.1.1]: https://github.com/SWSP-Git/messageAIHelper/compare/v1.1.0...v1.1.1
[v1.1.0]: https://github.com/SWSP-Git/messageAIHelper/compare/v1.0.1...v1.1.0
[v1.0.1]: https://github.com/SWSP-Git/messageAIHelper/compare/v1.0.0...v1.0.1
[v1.0.0]: https://github.com/SWSP-Git/messageAIHelper/releases/tag/v1.0.0
