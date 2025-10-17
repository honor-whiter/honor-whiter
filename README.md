# 👋 欢迎来到 Jump Height Recorder 学习仓库

本仓库包含一个用于练习 Android CameraX、ML Kit 姿态识别与垂直起跳高度估算的入门示例。完整项目位于 [`android/JumpHeightRecorder`](android/JumpHeightRecorder/README.md)。

## 如何在本地使用 Android Studio 打开项目

1. **下载仓库源码**
   - 这里的“仓库”指的就是当前这个包含 Android 示例项目的 Git 仓库（例如发布在 GitHub 或 Gitee 上的地址）。
   - 如果你使用浏览器，可在仓库页面点击 **Code ▾ → Download ZIP** 下载后解压。
   - 如果你已经安装 [Git](https://git-scm.com/)，可在终端执行以下命令克隆：
     ```bash
     git clone https://github.com/<你的用户名>/honor-whiter.git
     ```
     将 `<你的用户名>` 替换为托管此仓库的账号；若该项目是别人分享给你的，也可以直接使用对方给出的仓库地址。
   - 命令完成后，进入 `honor-whiter` 文件夹，确认其中包含 `android/JumpHeightRecorder` 目录。
2. **启动 Android Studio**
   - 无需“新建项目”，在欢迎界面选择 **“Open”/“打开”**。
   - 定位到刚才克隆的仓库根目录，选择其中的 `android/JumpHeightRecorder` 文件夹并确认。
3. **等待同步与依赖下载**
   - 第一次打开会自动执行 Gradle 同步，过程中会提示安装缺失的 SDK 组件，按照向导完成即可。
   - 如果提示“Plugin [id: 'com.android.application'…] was not found”，请确认已经同步最新的仓库代码。`settings.gradle.kts` 现已包含对 `com.android.application`、`com.android.library` 等插件的显式解析策略，会直接从 Google Maven 下载对应的 Android Gradle Plugin；若仍报错，多半是因为当前网络无法访问 `https://maven.google.com/`，可切换代理或 VPN 后再点击 **Sync Project with Gradle Files**。
   - 如果提示“Gradle wrapper not found”，可在 IDE 下方的 Terminal 中运行：
     ```bash
     gradle wrapper --gradle-version 8.2
     ```
     然后再次点击 **Sync Project with Gradle Files**。
4. **连接设备或创建模拟器**
   - 通过 USB 连接一台已开启开发者模式的 Android 手机并授权调试，或在 **Device Manager** 中创建 API 34（Android 14）以上的模拟器。
5. **运行应用**
   - 在工具栏选择目标设备后点击绿色的 ▶️ Run 按钮。
   - 首次运行会弹出相机与麦克风权限，允许后即可看到主界面，可根据需要进入“跳高测试”或“运动计数”。
   - 跳高测试模块沿用 CameraX + 示例估算逻辑；运动计数模块会额外下载 Google ML Kit 姿态识别模型（需能够访问 `https://maven.google.com/` 与 Google Play 服务依赖），并实时统计引体向上、俯卧撑、自重深蹲的次数。

更多关于功能、代码结构和进阶练习的说明，请继续查阅 [Android 项目内的学习指南](android/JumpHeightRecorder/README.md)。

## 动作识别数据采集与训练脚本

仓库根目录新增了一个 `ml/` 文件夹，提供可本地运行的 Python 脚本，帮助你采集素材、整理姿态特征并训练/替换运动计数模块所使用的分类模型：

- `ml/download_workout_media.py`：根据 manifest 批量下载你有权限使用的训练素材（支持 HTTP 直链与 YouTube，通过 `--extract-frames` 可直接导出 JPEG 帧）。
- `ml/extract_pose_features.py`：将通过 ML Kit 采集到的关键点序列（JSON）转换为关节角度、相对高度等特征 CSV。
- `ml/train_pose_classifier.py`：读取特征 CSV，计算每个动作类别的均值向量并导出到 `android/JumpHeightRecorder/app/src/main/assets/workout_classifier.json`。
- `ml/data/sample_pose_dataset.csv`：提供 3 类基础动作（俯卧撑、自重深蹲、引体向上）的示例特征，可直接练习训练流程。
- `ml/data_sources/sample_workout_sources.json`：示例 manifest，展示如何为不同动作列出下载源与裁剪时间段。请根据素材版权情况替换为你自己的配置。

快速体验：

```bash
# 1. （可选）根据 manifest 下载/整理训练素材（需要提前确认版权、隐私条款）
python ml/download_workout_media.py --manifest ml/data_sources/my_sources.json --video-dir ml/raw_media --extract-frames --frame-dir ml/extracted_frames

# 2. 将采集到的姿态关键点转换成特征 CSV
python ml/extract_pose_features.py --input my_raw_poses.json --output ml/data/my_dataset.csv

# 3. 使用特征 CSV 训练并导出分类器（默认输出到 app/assets/）
python ml/train_pose_classifier.py --input ml/data/my_dataset.csv --output android/JumpHeightRecorder/app/src/main/assets/workout_classifier.json

# 4. 在 Android Studio 中点击 Sync Project with Gradle Files，重新构建即可加载新模型
```

> ⚖️ **合法合规提醒**：脚本不会绕过任何网站的版权或访问限制。请仅下载自己拍摄的素材，或遵循授权协议（例如 Creative Commons、开源数据集）获取公开数据，并在需要时对人物进行脱敏处理。

导出的 JSON 会被 `WorkoutCounter` 在运行时加载；当文件缺失或解析失败时，应用会退回到内置的角度阈值逻辑继续计数。
