<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# AI Git Commit Plus 更新日志

## [Unreleased]

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

[Unreleased]: https://github.com/bling-yshs/AIGitCommitPlus/compare/62a79469935b3c5cae783b8e38f0b6ac860b18eb...HEAD
[1.0.1]: https://github.com/bling-yshs/AIGitCommitPlus/commit/62a79469935b3c5cae783b8e38f0b6ac860b18eb
[1.0.0]: https://github.com/bling-yshs/AIGitCommitPlus/commit/42902275174f85b51bc25ec12690afab795710f7
