<h1 align="center">AI Git Commit Plus</h1>

<p align="center">
  <strong>JetBrains IDE 智能 Git Commit 生成插件 · 多模型支持</strong>
</p>

<div align="center">
  <img src="src/main/resources/META-INF/pluginIcon.svg" alt="AI Git Commit Plus Logo" width="120">
</div>
<br>

<div align="center">
  <a href="https://github.com/bling-yshs/AIGitCommitPlus/stargazers"><img src="https://img.shields.io/github/stars/bling-yshs/AIGitCommitPlus?logo=github&color=yellow" alt="Stars"></a>
  <a href="https://github.com/bling-yshs/AIGitCommitPlus/releases/latest"><img src="https://img.shields.io/github/v/release/bling-yshs/AIGitCommitPlus?label=Release&color=brightgreen" alt="Release"></a>
  <a href="https://github.com/bling-yshs/AIGitCommitPlus/releases"><img src="https://img.shields.io/github/downloads/bling-yshs/AIGitCommitPlus/total.svg?color=blueviolet" alt="Downloads"></a>
  <a href="https://github.com/bling-yshs/AIGitCommitPlus/blob/main/LICENSE"><img src="https://img.shields.io/github/license/bling-yshs/AIGitCommitPlus.svg?color=orange" alt="License"></a>
</div>
<br>

<!-- Plugin description -->
AI Git Commit Plus 是一个基于 IntelliJ Platform 的 Git 提交信息生成插件。它会读取当前提交窗口中选中的变更与未跟踪文件内容，构造 prompt 后发送给大语言模型，并将生成结果直接回填到 commit message 输入框。

插件支持 OpenAI Chat Completions、OpenAI Responses、Gemini、Anthropic 及兼容接口，支持流式输出、自定义 Prompt、项目根目录 `commit-prompt.txt`、模型自动发现、文件过滤和最近一次 Prompt 调试查看。
<!-- Plugin description end -->

## ✨ 功能特性

- 🤖 **多模型支持**：支持 OpenAI、OpenAI Responses、Gemini、Anthropic 及兼容协议
- 📡 **流式生成**：支持边生成边回填 commit message，减少等待感
- 🧩 **基于选中变更生成**：只读取当前提交面板里勾选的变更和未跟踪文件
- 📝 **Prompt 可定制**：内置多套 Prompt 模板，也支持项目级 `commit-prompt.txt`
- 🔎 **模型自动发现**：可从供应商接口拉取模型列表，也可手动添加自定义模型
- 🚫 **文件过滤**：可忽略生成文件、依赖锁文件、构建产物等噪音内容
- 🌍 **多语言输出**：可指定 commit message 输出语言
- 🧪 **Prompt 调试**：可在设置页查看最近一次实际发送给模型的 Prompt

## 🚀 使用步骤

### 1️⃣ 安装插件

从 GitHub Releases 下载打包产物，或本地构建 ZIP 后通过 IDE 安装。

### 2️⃣ 配置模型服务

打开 IDE 设置页，进入：

```text
Settings / Preferences -> Tools -> AI Git Commit Plus
```

然后填写以下内容：

- Provider Type
- API URL
- API Key
- Model
- Commit Message Language

### 3️⃣ 选择 Prompt 策略

插件支持两种 Prompt 模式：

- **Custom Prompt**：使用内置或你自己维护的 Prompt 模板
- **Project Prompt**：读取项目根目录下的 `commit-prompt.txt`

如果使用项目级 Prompt，文件里至少需要包含 `{diff}` 占位符；如果希望语言可切换，建议同时包含 `{language}`。

示例：

```txt
Generate conventional commit message in {language}.
Focus on what changed and why.

{diff}
```

### 4️⃣ 生成提交信息

打开 IDE 的 Git 提交窗口，勾选要提交的文件后，点击提交信息区域的插件图标：

```text
AI Git Commit Plus
```

插件会读取已选中的变更内容，调用模型生成结果，并自动写回提交信息输入框。

## 🔌 支持的协议

| Provider | 默认 URL |
|:--|:--|
| OpenAI | `https://api.openai.com/v1/chat/completions` |
| OpenAI Responses | `https://api.openai.com/v1/responses` |
| Gemini | `https://generativelanguage.googleapis.com/v1beta/models` |
| Anthropic | `https://api.anthropic.com/v1/messages` |

> [!TIP]
> 你可以填写兼容接口的基础地址或完整端点，插件会按不同协议自动补齐或规范化请求地址。

## ⚙️ 进阶能力

### 文件过滤

开启 `Enable File Filtering` 后，插件会按规则排除噪音文件，例如：

- `*.pb.go`
- `package-lock.json`
- `node_modules/**`
- `dist/**`
- `build/**`

适合避免把生成文件、依赖锁文件和构建产物一股脑喂给模型。

### 最近一次 Prompt

设置页内置 `Recent Prompt` 标签页，可直接查看上一次发送给模型的完整 Prompt，方便：

- 调试 Prompt 模板
- 定位输出不符合预期的原因
- 复制到外部模型平台复现问题

## 📂 项目结构

```text
AIGitCommitPlus/
├── src/main/kotlin/com/yshs/aicommit/
│   ├── GenerateCommitMessageAction.kt   # Git 提交窗口入口动作
│   ├── WelcomeNotification.kt           # 安装/升级欢迎通知
│   ├── config/                          # 设置页、Provider 配置、模型管理
│   ├── constant/                        # 常量与默认配置
│   ├── pojo/                            # Prompt 等数据结构
│   ├── service/                         # 模型调用与提交信息生成逻辑
│   └── util/                            # Git Diff、Prompt、HTTP、UI 工具
├── src/main/resources/META-INF/
│   ├── plugin.xml                       # 插件注册信息
│   └── pluginIcon.svg                   # 插件图标
├── build.gradle.kts                     # Gradle 构建配置
├── gradle.properties                    # 插件版本、平台版本等
└── CHANGELOG.md                         # 更新日志
```

## 🛠️ 开发环境

| 环境 | 版本要求 |
|:--|:--|
| JDK | 17 |

## 🔨 构建指南

```bash
# 构建插件 ZIP，构建产物默认位于
./gradlew buildPlugin
```

## 📄 许可证

本项目采用 [GPL v3.0](LICENSE) 开源许可证。
