<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# AI Git Commit Plus 更新日志

## [Unreleased]

### Changed

- 修改插件 Logo

## [1.0.3] - 2026-05-23

### Fixed

- 修复上方模型下拉框切换后，下方 Model List 选中高亮未同步更新的问题

### Changed

- 发布流程支持在 release 后自动创建维护 PR，将 `pluginVersion` 更新到下一个 patch 版本
- 发布到 JetBrains Marketplace 前会检测发布密钥，未配置完整时自动跳过商店发布流程

## [1.0.2] - 2026-05-23

### Changed

- 完善中文 README，补充插件说明、配置方式与构建指南
- 清理 GitHub Actions 工作流，移除未使用的 UI tests 流程

## [1.0.1] - 2026-03-15

### Changed

- 迁移到 IntelliJ Platform Plugin Template 结构
- 补充 GitHub Actions 的构建与发布流程
- 更新插件元数据与发版基础配置

## [1.0.0] - 2026-03-15

### Added

- 首次发布 AI Git Commit Plus
- 支持在 Git 提交窗口中基于已选变更生成 commit message
- 支持 OpenAI、OpenAI Responses、Gemini、Anthropic 四类模型协议
- 支持流式回填生成结果到提交信息输入框
- 支持自动拉取模型列表，并允许手动添加自定义模型
- 支持多语言 commit message 输出
- 支持自定义 Prompt 模板与项目级 `commit-prompt.txt`
- 支持文件过滤规则，排除常见生成文件、依赖锁文件与构建产物
- 支持查看最近一次发送给模型的 Prompt
- 支持首次安装或升级后的欢迎通知与设置引导

[Unreleased]: https://github.com/bling-yshs/AIGitCommitPlus/compare/1.0.3...HEAD
[1.0.3]: https://github.com/bling-yshs/AIGitCommitPlus/compare/1.0.2...1.0.3
[1.0.2]: https://github.com/bling-yshs/AIGitCommitPlus/compare/1.0.1...1.0.2
[1.0.1]: https://github.com/bling-yshs/AIGitCommitPlus/compare/1.0.0...1.0.1
[1.0.0]: https://github.com/bling-yshs/AIGitCommitPlus/commits/1.0.0
