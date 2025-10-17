# JumpHeightRecorder 学习指南

> 这是一个用于熟悉本平台工作流并入门 Android CameraX 与 ML Kit 姿态识别的示例项目。
> 当前包含两个主要功能模块：跳高测试（录制视频并基于示例算法估算离地高度）与运动计数（实时识别引体向上、俯卧撑、自重深蹲）。所有估算/识别逻辑仅为演示用途，真实项目需要更精准的模型与传感器方案。

## 1. 认识仓库结构

```text
android/JumpHeightRecorder/   # Android Studio 可直接导入的项目根目录
  ├─ app/                     # 主模块
  │   ├─ src/main/java/…      # Kotlin 源码（相机预览、录制、估算逻辑）
  │   ├─ src/main/res/…       # 布局、文案、主题等资源
  │   └─ build.gradle.kts     # 模块级构建脚本
  ├─ build.gradle.kts         # 工程级构建脚本
  └─ settings.gradle.kts      # 包含的模块配置
```

主要 Kotlin 入口位于 `app/src/main/java/com/example/jumprecorder/`：

- `MainActivity`：功能选择页，跳转到跳高测试或运动计数界面。
- `JumpHeightActivity`：沿用 CameraX 录制 + 示例离地高度估算流程。
- `WorkoutCounterActivity`：结合 CameraX 预览、视频录制与 ML Kit 姿态识别进行动作分类计数。
- `WorkoutCounter` / `WorkoutPoseAnalyzer` / `WorkoutSessionViewModel`：封装动作识别、计数状态机以及 UI 状态同步逻辑，便于后续替换为更复杂的模型。

建议在 Codespaces、Dev Container 或本地环境中逐步体验以下流程：

1. `git clone` 仓库：这里指下载保存本示例的 Git 仓库。若你在 GitHub 上看到的是 `https://github.com/<你的用户名>/honor-whiter`，可以在终端运行 `git clone https://github.com/<你的用户名>/honor-whiter.git`，或直接点击网页上的 **Code ▾ → Download ZIP**。
2. 在 Android Studio 欢迎页选择 **Open**，定位到 `android/JumpHeightRecorder` 文件夹并导入（不用新建项目）。
3. 同步 Gradle 并根据提示安装缺失的 SDK / Build Tools。若同步时出现 `Plugin [id: 'com.android.application'] was not found` 错误，请确认已拉取最新的 `settings.gradle.kts`。文件中已加入 `resolutionStrategy`，会把 `com.android.application`、`com.android.library` 等插件映射到 Google Maven 的 `com.android.tools.build:gradle` 组件；若依旧提示未找到，通常是由于网络无法访问 `https://maven.google.com/`，可在联网后重新点击 **Sync Project with Gradle Files**。
4. `git status` 查看当前分支状态。
5. `git diff` 理解变更内容。
6. 通过 PR 模板提交改动。

## 2. 准备开发环境

1. 安装 [Android Studio Giraffe 或更高版本](https://developer.android.google.cn/studio)；安装时勾选 Android SDK 34 与对应的构建工具。
2. 运行一次 `sdkmanager --licenses` 接受许可。
3. 如果没有 `gradlew` 包装器，可在 Android Studio 中执行 **Tools ▸ Gradle ▸ Wrapper Task** 或在命令行运行：
   ```bash
   gradle wrapper --gradle-version 8.2
   ```
   之后即可通过 `./gradlew assembleDebug` 构建。

> 💡 建议在 Android Studio 中直接通过 *File ▸ Open…* 选择 `android/JumpHeightRecorder` 目录导入。

## 3. 第一次运行快速检查清单

1. **确认 Gradle 同步成功**：右上角状态栏出现“Gradle sync finished”提示。
2. **切换到 app 配置**：Run/Debug Configurations 下拉中选择 `app`。
3. **准备运行设备**：
   - 使用 **Device Manager** 创建 Pixel 系列的 API 34 模拟器，或
   - 开启真机的开发者模式并打开 USB 调试，使用数据线连接电脑。
4. **授予必要权限**：首次运行时系统会弹出相机与麦克风权限弹窗，点击允许。
5. **查看 logcat**：如果应用没有显示画面，可打开 Logcat 检查 CameraX 初始化日志。

## 4. 应用内主要流程

1. **选择模块**：主界面提供“跳高测试”和“运动计数”两个入口，可根据训练目标自由切换。
2. **跳高测试**：
   - 输入人物真实身高（厘米）。
   - 点击“估算”得到像素换算参考值。
   - 点击“开始录制”触发 CameraX 视频录制；再次点击停止。
   - 停止录制后，`JumpAnalyzer` 会读取视频做示例性估算，并在界面上显示结果。
3. **运动计数**：
   - 点击“开始记录”后会同时启动视频录制与 ML Kit 姿态识别，请确保全身位于取景框中。
   - 系统会尝试自动识别当前动作类型（引体向上、俯卧撑、自重深蹲），并根据“起始点 → 动作中段 → 回到起始点”的节奏累计次数。
   - 停止录制后，会生成带次数汇总的统计文本，可直接通过分享按钮发送至 DeepSeek 等云端分析服务。

## 5. 关键代码导读

- [`MainActivity`](app/src/main/java/com/example/jumprecorder/MainActivity.kt)：功能选择页，负责跳转到具体模块。
- 跳高测试链路：
  - [`JumpHeightActivity`](app/src/main/java/com/example/jumprecorder/JumpHeightActivity.kt)：使用 **ViewBinding** 和 **CameraX** 完成相机预览、权限申请和视频录制，录制结束后调用 `JumpMeasurementViewModel.onVideoReady()`。
  - [`JumpMeasurementViewModel`](app/src/main/java/com/example/jumprecorder/JumpMeasurementViewModel.kt)：保留人物身高与像素换算参数，在后台线程调用 `JumpAnalyzer` 并将结果推送到界面。
  - [`JumpAnalyzer`](app/src/main/java/com/example/jumprecorder/JumpAnalyzer.kt)：使用 `MediaMetadataRetriever` 抽取若干视频帧，查找最亮的像素行作为脚尖高度示例；将像素高度除以换算比例得到估算值。
- 运动计数链路：
  - [`WorkoutCounterActivity`](app/src/main/java/com/example/jumprecorder/WorkoutCounterActivity.kt)：绑定 CameraX 预览、视频录制与 `ImageAnalysis`，同时启动姿态识别与会话状态管理。
  - [`WorkoutPoseAnalyzer`](app/src/main/java/com/example/jumprecorder/WorkoutPoseAnalyzer.kt)：基于 ML Kit Accurate Pose Detector 解析人体关键点并回调 `WorkoutCounter`。
    - [`WorkoutCounter`](app/src/main/java/com/example/jumprecorder/WorkoutCounter.kt)：实现动作分类与节奏状态机，根据关节角度/高度变化计算引体向上、俯卧撑、自重深蹲的完成次数。
    - [`PoseFeatureExtractor`](app/src/main/java/com/example/jumprecorder/PoseFeatureExtractor.kt)：统一封装关节角度与相对高度等特征计算，确保实时计数与离线训练脚本一致。
    - [`PoseClassifier`](app/src/main/java/com/example/jumprecorder/PoseClassifier.kt)：加载 `app/src/main/assets/workout_classifier.json` 中的原型向量，为实时姿态输出类别概率，辅助 `WorkoutCounter` 判定动作类型。
    - [`WorkoutSessionViewModel`](app/src/main/java/com/example/jumprecorder/WorkoutSessionViewModel.kt)：维护实时计数、当前动作类型、统计摘要以及分享状态，驱动界面展示。

## 6. 自定义动作分类模型训练与替换

运动计数模块默认加载 `app/src/main/assets/workout_classifier.json` 中的原型分类器。你可以通过仓库根目录的 `ml/` 脚本快速复现或替换：

1. **采集或整理数据**：
   - 如果需要从视频/图像构建数据集，可编辑 `ml/data_sources/sample_workout_sources.json`（或自定义 manifest），然后运行：
     ```bash
     python ml/download_workout_media.py --manifest ml/data_sources/my_sources.json --video-dir ml/raw_media --extract-frames --frame-dir ml/extracted_frames
     ```
     该脚本支持 HTTP 直链与 YouTube 下载，并可按需裁剪片段、导出 JPEG 帧。务必确认素材拥有可用于训练的授权。
   - 使用 ML Kit 姿态识别保存 `[{"label": "PUSH_UP", "landmarks": [...]}, …]` 结构的 JSON，或者直接复用 `ml/data/sample_pose_dataset.csv` 进行练习。
2. **提取特征**：
   ```bash
   python ml/extract_pose_features.py --input raw_pose_samples.json --output ml/data/custom_dataset.csv
   ```
   若已手动整理好角度/高度特征，可跳过此步骤，直接使用 CSV 作为训练输入。
3. **训练并导出分类器**：
   ```bash
   python ml/train_pose_classifier.py --input ml/data/custom_dataset.csv --output android/JumpHeightRecorder/app/src/main/assets/workout_classifier.json
   ```
   脚本会计算特征的均值、标准差与每个类别的中心向量，供 `PoseClassifier` 运行时加载。
4. **同步项目**：在 Android Studio 中点击 *Sync Project with Gradle Files*，新的 JSON 会被打包进 APK。若模型文件缺失或解析失败，`WorkoutCounterActivity` 会弹出提示并自动回退至内置阈值逻辑。

> ✅ 提示：如需保留多个模型版本，可在 `assets` 目录中使用不同文件名，并在 `WorkoutCounterActivity` 的 `CLASSIFIER_ASSET` 常量中切换。

> ⚖️ **隐私&版权提示**：在采集包含人物的训练素材之前，请征得被摄者同意，并遵守素材平台的服务条款。对于来自公共数据集的资源，请遵循其许可证要求并在需要时添加署名。

## 7. 下一步练习建议

| 目标 | 建议任务 |
| --- | --- |
| 熟悉平台协作流程 | 新建分支，修改 UI 文案或主题颜色，跑通 `./gradlew lint`，提交 PR。 |
| 优化跳跃识别 | 集成 MediaPipe Pose，并在 `JumpAnalyzer` 内根据脚踝关键点的纵向位移计算。 |
| 数据可视化 | 将视频帧中提取到的位移曲线绘制到 `PreviewView` 的叠加层。 |
| 扩展运动计数 | 训练或接入自定义动作分类模型，利用 `WorkoutCounter` 保持计数状态机，增补其他动作（例如波比跳、箭步蹲）。 |
| 科学校准 | 编写标定流程：通过测量相机到地面的距离、镜头 FOV 等参数修正 `pixelPerCentimeter`。 |

## 8. 常见问题 FAQ

- **为什么估算结果不准确？** 当前算法仅用于教学展示。需要结合人体关键点检测或外部传感器才能得到可靠数据。
- **如何导出视频文件？** 录制文件默认保存在应用的外部媒体目录（`/Android/media/com.example.jumprecorder/`），可通过文件管理器或 `adb pull` 导出。
- **如果没有真实设备怎么办？** 可以使用支持 CameraX 的 Android 模拟器（API 30 及以上）。不过模拟器的传感器数据有限，建议尽快在真机上调试。
- **运动计数为什么刚开始识别缓慢？** 首次进入该模块时，Google ML Kit 会在后台下载姿态识别模型，需要保持网络畅通（能够访问 `https://maven.google.com/` 及 Google Play 服务依赖）。下载完成后即可离线推理。

祝学习顺利！
