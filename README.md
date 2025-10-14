# 👋 欢迎来到 Jump Height Recorder 学习仓库

本仓库包含一个用于练习 Android CameraX 视频录制与垂直起跳高度估算的入门示例。完整项目位于 [`android/JumpHeightRecorder`](android/JumpHeightRecorder/README.md)。

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
   - 如果提示“Plugin [id: 'com.android.application'…] was not found”，请确认已经同步最新的仓库代码（`settings.gradle.kts` 中已经包含 `google()` 与 `mavenCentral()` 仓库配置），并确保电脑可以访问 Google Maven。再次点击 **Sync Project with Gradle Files** 即可。
   - 如果提示“Gradle wrapper not found”，可在 IDE 下方的 Terminal 中运行：
     ```bash
     gradle wrapper --gradle-version 8.2
     ```
     然后再次点击 **Sync Project with Gradle Files**。
4. **连接设备或创建模拟器**
   - 通过 USB 连接一台已开启开发者模式的 Android 手机并授权调试，或在 **Device Manager** 中创建 API 34（Android 14）以上的模拟器。
5. **运行应用**
   - 在工具栏选择目标设备后点击绿色的 ▶️ Run 按钮。
   - 首次运行会弹出相机与麦克风权限，允许后即可看到预览、录制以及示例估算界面。

更多关于功能、代码结构和进阶练习的说明，请继续查阅 [Android 项目内的学习指南](android/JumpHeightRecorder/README.md)。
