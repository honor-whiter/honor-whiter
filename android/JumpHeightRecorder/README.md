# JumpHeightRecorder 学习指南

> 这是一个用于熟悉本平台工作流并入门 Android CameraX 视频录制的示例项目。
> 代码和文档都以“通过已知身高估算垂直起跳离地高度”为主题。所有估算算法仅为演示用途，真实项目需要更精准的传感器或骨骼识别方案。

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

建议在 Codespaces、Dev Container 或本地环境中逐步体验以下流程：

1. `git clone` 仓库。
2. 在 Android Studio 欢迎页选择 **Open**，定位到 `android/JumpHeightRecorder` 文件夹并导入（不用新建项目）。
3. 同步 Gradle 并根据提示安装缺失的 SDK / Build Tools。
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

## 4. 录制与估算流程

在应用内：
   - 输入人物真实身高（厘米）。
   - 点击“估算”得到像素换算参考值。
   - 点击“开始录制”触发 CameraX 视频录制；再次点击停止。
   - 停止录制后，`JumpAnalyzer` 会读取视频做示例性估算，并在界面上显示结果。

## 5. 关键代码导读

- [`MainActivity`](app/src/main/java/com/example/jumprecorder/MainActivity.kt)：
  - 使用 **ViewBinding** 和 **CameraX** 完成相机预览、权限申请和视频录制。
  - 录制结束后调用 `JumpMeasurementViewModel.onVideoReady()`。
- [`JumpMeasurementViewModel`](app/src/main/java/com/example/jumprecorder/JumpMeasurementViewModel.kt)：
  - 保留人物身高与像素换算参数。
  - 在后台线程调用 `JumpAnalyzer` 并将结果推送到界面。
- [`JumpAnalyzer`](app/src/main/java/com/example/jumprecorder/JumpAnalyzer.kt)：
  - 使用 `MediaMetadataRetriever` 抽取若干视频帧，查找最亮的像素行作为脚尖高度示例。
  - 将像素高度除以换算比例得到估算值。
  - 提醒用户替换为真正的骨骼检测算法（如 MediaPipe Pose、OpenCV + AprilTag 标定等）。

## 6. 下一步练习建议

| 目标 | 建议任务 |
| --- | --- |
| 熟悉平台协作流程 | 新建分支，修改 UI 文案或主题颜色，跑通 `./gradlew lint`，提交 PR。 |
| 优化跳跃识别 | 集成 MediaPipe Pose，并在 `JumpAnalyzer` 内根据脚踝关键点的纵向位移计算。 |
| 数据可视化 | 将视频帧中提取到的位移曲线绘制到 `PreviewView` 的叠加层。 |
| 科学校准 | 编写标定流程：通过测量相机到地面的距离、镜头 FOV 等参数修正 `pixelPerCentimeter`。 |

## 7. 常见问题 FAQ

- **为什么估算结果不准确？** 当前算法仅用于教学展示。需要结合人体关键点检测或外部传感器才能得到可靠数据。
- **如何导出视频文件？** 录制文件默认保存在应用的外部媒体目录（`/Android/media/com.example.jumprecorder/`），可通过文件管理器或 `adb pull` 导出。
- **如果没有真实设备怎么办？** 可以使用支持 CameraX 的 Android 模拟器（API 30 及以上）。不过模拟器的传感器数据有限，建议尽快在真机上调试。

祝学习顺利！
