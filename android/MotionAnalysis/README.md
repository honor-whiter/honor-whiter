# MotionAnalysis

MotionAnalysis 是一个面向运动动作对比的安卓示例项目，提供以下能力：

- 模板视频与模仿者视频导入入口。
- 本地关键点提取与动作轨迹差异分析的架构骨架。
- 表格与折线图形式展示关键指标。
- 支持将本地生成的数据序列上传至 DeepSeek 云端进行进一步分析（需要配置 API Key）。

当前项目采用 Jetpack Compose 编写 UI，分析流程由 `MotionAnalysisRepository` 串联本地关键点提取、动作对齐、差异度量与云端交互逻辑。由于尚未集成真实的关键点检测模型，`LocalPoseAnalyzer` 使用伪造数据以便演示流程，后续可替换为 MediaPipe、ML Kit 或自研算法实现。

## 云端分析配置

在 `MainActivity` 中通过 `DeepSeekClient` 创建云端分析实例，默认 API Key 提供器返回 `null`。将 `apiKeyProvider` 替换为实际读取秘钥的实现即可启用云端能力。接口定义参考 [DeepSeek API 文档](https://api-docs.deepseek.com/zh-cn/)。

## 模块拓展

项目主界面预留了后续模块接入的入口，后续可在 `MotionAnalysisScreen` 中添加跳跃高度评估、动作计数等功能模块入口，实现一个统一的运动分析工具集。
