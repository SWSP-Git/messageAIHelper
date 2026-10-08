# 更新日志 (Changelog)

本项目所有值得注意的变更都会记录在此文件。

All notable changes to this project are documented in this file.

格式遵循 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/)，版本号遵循 [语义化版本](https://semver.org/lang/zh-CN/)。

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project adheres to [Semantic Versioning](https://semver.org/).

---

## [未发布 / Unreleased]

### 新增 / Added
- **🌐 远程模型清单（重要）**：模型清单支持**远程动态更新**——维护者只需修改仓库根目录的 `models.json`，App 即可获取新模型，**无需发版**。三级来源（远程 → 本地缓存 → 内置兜底），多候选地址（ModelScope 优先，GitHub 镜像兜底），缓存 6 小时，离线可用。
- **🧠 模型清单扩展至 6 个**：可选 Qwen2.5 0.5B / 1.5B / 3B、Qwen3 0.6B、MiniCPM4 0.5B、DeepSeek-R1 1.5B（均托管于 ModelScope 国内源，按体积升序排列）。
- **❓ 模型选择指南**：下载对话框中新增「如何选择？」按钮，按手机内存给出推荐、逐条说明各模型特点与首次使用建议。

### 优化 / Changed
- **🔧 JNI 提示词模板可配置（去掉硬编码）**：`llm_jni.cpp` 不再写死 Qwen 格式，改为从插件 `config.json` 读取可选字段：
  - `prompt_template`（含系统提示，占位符 `{system}` / `{user}`）
  - `prompt_template_no_system`（不含系统提示，占位符 `{user}`）
  - `eos_token`（结束标记，默认 `<|im_end|>`）
  - 未提供时回退 Qwen2.5 ChatML 格式，保证旧插件完全兼容；不同对话格式的模型（如 DeepSeek 的 `<｜User｜>`）只需在插件中声明模板即可。

### 修复 / Fixed
- **🧠 修复思考型模型（DeepSeek-R1 / Qwen3）思维链溢出**：此前 `maxTokens` 默认仅 512，思维链会耗尽预算导致正式答案被截断；且输出未剥离思维链。现已（1）在 JNI 层新增 `stripThinking()` 剥离 ` thinking…` 内容；（2）`maxTokens` 默认 512 → **2048**、上限 4096 → **8192**。

### 计划中 / Planned
- 🤖 更多本地模型插件（不同尺寸 / 量化级别）

---

## [v1.2.2] - 2026-10-08

> 应用内下载模型 · 国内源提速 · 备忘交互优化

### 新增 / Added
- **⬇️ 应用内下载模型（重要）**：在「AI 与日程设置 → 本地模型」新增「下载模型」按钮，可直接下载模型插件（无需手动导入）。
  - **国内源（ModelScope）**：模型托管于 ModelScope 国内 CDN，直连速度快且稳定，支持分段下载。
  - **并发分段下载**：多线程 Range 请求并行下载（线程数可自选 1–64，默认 12），显著提速。
  - **断点续传**：支持暂停 / 恢复；中断后重进可继续，无需从头下载。
  - **SHA-256 完整性校验**：下载后自动校验，损坏则删除并提示重试。
  - **下载完成自动安装**：校验通过后自动导入为插件并设为当前。
- **📥 模型选择对话框**：下载前弹出可下载模型列表（当前含 Qwen2.5-1.5B，后续可扩展），用户自主选择，并可设置下载线程数。
- **🛡️ 下载前台服务**：下载期间前台服务保活，通知栏实时显示进度，避免退到后台被系统中断；进度区提供「后台下载」快捷入口（点击切到后台，下载继续）。

### 优化 / Changed
- **🗂️ 备忘卡片折叠态限 2 行**：摘要与智能建议在折叠态最多显示 2 行（超出省略），展开后显示完整内容，避免长文本撑高卡片。

### 修复 / Fixed
- **🔧 修复下载崩溃**：切换下载线程数时，旧任务的协程会访问已被替换的进度数组导致越界崩溃；现已用「代次隔离 + 数组参数化」修复。
- **🔧 修复分段数据错乱**：分段请求现强制要求 HTTP 206（Partial Content），避免服务器忽略 Range 时写入错位；新增分段支持探测，不支持时自动回退单线程。

---

## [v1.2.1] - 2026-10-07

> 保活设置修复 · 本地模型体验优化

### 修复 / Fixed
- **🛡️ 修复保活设置无法保存（重要）**：优化设置弹窗的底部按钮此前被 `ScrollView` 遮挡导致不可见，用户无法保存保活配置。现已改为**开关即时生效**，移除底部按钮，无需再点保存。
- **🛡️ 修复保活开关状态矛盾**：通知权限被拒时会把「前台服务保活」开关自动回滚为关闭，避免「开关为 ON 但服务未运行」。

### 新增 / Added
- **🔍 自动搜寻可取消**：扫描期间按钮变为「取消搜寻」，点击可随时中断。
- **🧠 本地模型专属提示词**：本地小模型改用更短、更直接的提示词，提升解析稳定性。

### 构建 / Build
- 支持从 `local.properties` 读取 Release 签名配置（CI 无此文件时自动跳过，不影响云端构建）。

### 文档 / Docs
- README 补充本地小模型功能说明（中英双语）。

---

## [v1.2.0] - 2026-10-07

> 本地小模型插件化 · 离线推理 · 日程双写 · 备忘交互升级

### 新增 / Added
- **🤖 本地小模型（离线推理）**：新增 `localllm` 独立模块，基于 MNN 引擎在手机本地运行小模型（如 Qwen2.5-1.5B 4bit），无需联网、不消耗 API 额度。
- **🔌 插件化模型分发**：模型以 `.zip` 插件形式导入（含 `plugin.json` 清单 + 模型权重），APK 本体保持轻量；支持安装多个插件、自由切换当前插件。
- **🔍 自动搜寻**：一键扫描手机 Download / Documents 目录，自动识别含 `plugin.json` 的插件包并列出，点击即安装。
- **🤖 模型来源二选一**：在「AI 与日程设置」中可在「云端模型」与「本地模型（离线）」之间切换；选择本地时自动启用 `127.0.0.1` 上的 OpenAI 兼容服务。
- **💡 首次提示**：首次进入配置中心时（在所有权限弹窗之后）提示用户可安装本地模型插件。
- **📊 插件管理增强**：显示已装插件总数与合计占用空间；切换插件时自动加载，无需再点「加载 / 重载」。
- **📝 日程类双写**：日程类消息在写入系统日历的同时，也会归档到备忘录（新增「分类」字段区分「日程 / 备忘」）；备忘类消息仅归档备忘录。
- **🗂️ 备忘卡片折叠/展开**：默认精简显示（重要性 / 发送人 / 时间 / 关键信息 / 智能建议 / 摘要），单击卡片展开详情（来源 / 分类 / 原文）。
- **☑️ 备忘多选管理**：长按卡片进入多选模式，支持全选 / 分享 / 删除 / 取消；删除带二次确认。

### 优化 / Changed
- 本地推理请求超时放宽：`readTimeout` 90s → **300s**、新增 `callTimeout` **310s**（首次加载权重较慢）。
- 「AI 与日程设置」页重构：模型运行位置卡片改为二选一 + 插件管理面板。
- **AI 提示词强化**：【类型】判断修正为「含任何时间线索即判为日程」（修复"明天中午11点去火车站"被误判为备忘）；【智能建议】改为必填并扩充示例，减少小模型偷懒填"无"。

### 技术 / Technical
- 新增 `localllm` Gradle library 模块（可整体删除以移除本地模型功能）。
- `abiFilters` 限定 `arm64-v8a`（本地推理仅提供该架构预编译库）。
- `androidResources.noCompress` 排除 `mnn` / `weight` / `json` / `txt` / `bin`，避免模型权重被压缩打包。
- 单元测试 **59 → 60** 个（MemoParser 新增分类字段用例）。
- 版本号：`versionCode 6 → 7`、`versionName 1.1.3 → 1.2.0`。

---

## [v1.1.3] - 2026-10-07

> 解析健壮性修复 · 发送人 · 工程可测性

### 新增 / Added
- **👤 发送人解析**：AI 从消息中提取的发送人（如「张三」）现会记录到备忘录并展示（未提取到时自动省略字段，**老数据不受影响**）。
- **🧪 单元测试体系**：新增 4 个纯 JVM 测试类，共 **59 个用例**（此前仅 1 个模板测试）：
  - `AiReplyParserTest`（16）：AI 回复字段提取、默认值回退、时间格式边界、**单行输入边界**
  - `MemoParserTest`（21）：备忘录切分、字段提取、**重要性兼容（老数据默认「中」）**、过滤逻辑、**多行「原文」完整性**
  - `MessageFilterTest`（13）：关键词过滤（AND / OR）、包名映射、格式容错
  - `ScheduleWindowTest`（8）：时间窗口判断，重点覆盖**跨零点区间**
- **⚙️ 持续集成**：新增 GitHub Actions 工作流，push / PR 时自动运行单元测试并编译 Debug APK。
- **📋 功能建议模板**：新增 `feature_request.md` Issue 模板。
- **📄 变更日志**：新增 `CHANGELOG.md`，覆盖 v1.0.0 ~ v1.1.3。

### 修复 / Fixed
- **🐛 修复备忘录「原文」显示残缺**：备忘录卡片的「原文」此前只显示消息标题，正文丢失。原因是「内容」字段保存的是「标题 + 换行 + 正文」（跨行），而解析只取了首行。已改用多行字段解析，完整展示原文。
- **🐛 修复 AI 回复字段互相污染（重要）**：当 AI 未按格式换行、把多个字段挤在同一行时（如 `【摘要】开会 【重要性】高`），解析结果会把后续字段一并吞入（摘要变成 `开会 【重要性】高`），进而**静默写入错误数据**（如把备忘误判为日程）。已修正字段结束边界，兼容单行与多行输入。

### 重构 / Refactored
- **🧩 解析逻辑抽离为纯逻辑对象**（零 Android 依赖，便于单测）：
  - `AiReplyParser`：AI 回复解析（原内嵌于 `QQNotificationListener`）
  - `MemoParser`：备忘录文本解析与重要性过滤（原内嵌于 `MemoListActivity` / `MemoAdapter`）
  - `MessageFilter`：关键词过滤与包名映射
  - `ScheduleWindow`：定时开关时间窗口判断
- 以上重构**行为完全等价**，`QQNotificationListener` 净减约 45 行。

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

[未发布 / Unreleased]: https://github.com/SWSP-Git/messageAIHelper/compare/v1.2.2...HEAD
[v1.2.2]: https://github.com/SWSP-Git/messageAIHelper/compare/v1.2.1...v1.2.2
[v1.2.1]: https://github.com/SWSP-Git/messageAIHelper/compare/v1.2.0...v1.2.1
[v1.2.0]: https://github.com/SWSP-Git/messageAIHelper/compare/v1.1.3...v1.2.0
[v1.1.3]: https://github.com/SWSP-Git/messageAIHelper/compare/v1.1.2...v1.1.3
[v1.1.2]: https://github.com/SWSP-Git/messageAIHelper/compare/v1.1.1...v1.1.2
[v1.1.1]: https://github.com/SWSP-Git/messageAIHelper/compare/v1.1.0...v1.1.1
[v1.1.0]: https://github.com/SWSP-Git/messageAIHelper/compare/v1.0.1...v1.1.0
[v1.0.1]: https://github.com/SWSP-Git/messageAIHelper/compare/v1.0.0...v1.0.1
[v1.0.0]: https://github.com/SWSP-Git/messageAIHelper/releases/tag/v1.0.0

