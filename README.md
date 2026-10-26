# NOTX UnZip Android（实验版）

这是根据上传的 `NPK-X-main.zip` 改造的 Android 工程，目标是手机端读取《蛋仔派对》PC 版 NXPK/NPK 文件并优先提取可识别贴图。

## 当前功能

- 通过 Android 文件选择器选择单个 `.npk` 文件。
- 使用 NPK-X 的 `nxpk_mode3.py` / `mode3_tables.py` 解读 mode 3 索引。
- 使用随项目提供的 `pathdict.txt.gz` 尝试恢复资源路径。
- 只处理识别为图片的资源，默认导出 PNG，可切换 JPG。
- 对无法转换的贴图保留原始字节，导出为 ZIP 到用户指定的位置。
- GitHub Actions 工作流 `.github/workflows/android.yml` 用于在线构建 Debug APK。

## 在线构建（可用手机操作）

1. 把本工程上传到你自己的 GitHub 仓库。
2. 打开仓库的 **Actions** 页面。
3. 选择 **Build NOTX UnZip Android APK**，点击 **Run workflow**。
4. 构建结束后打开该次运行页面，在 **Artifacts** 下载 `NOTX-UnZip-debug-apk`。
5. 解压下载的 artifact，安装 `app-debug.apk`。首次安装时，Android 可能要求允许浏览器或文件管理器安装未知应用。

## 重要限制

- 这是移植工程，不是已验证可安装的成品 APK；需先由 GitHub Actions 实际构建验证。
- 当前仅面向 NPK-X 源码所针对的 PC 版 NXPK Mode 3 包，不保证兼容其他 NPK 格式或游戏更新后的索引。
- 图片转换依赖 Pillow 与 Zstandard；部分 BC/DDS/KTX 格式需要 `texture2ddecoder`，该可选原生模块目前没有纳入 Android 依赖，因此部分纹理可能保留原格式或转换失败。
- 只导出可识别贴图，不还原完整模型、特效或脚本源码；`.xn` 受保护脚本容器不执行、不反编译。
- 全量游戏资源包可能很大。应用会先复制所选 NPK 到缓存并把结果打包为 ZIP，请确保手机有足够空间。建议先用较小的 NPK 测试。
- 游戏更新后如索引密钥/密码表发生变化，需要同步更新 `nxpk_mode3.py`、`mode3_tables.py` 或 profile。
- 请仅对你有权访问的本地资源进行研究和备份。

## 构建说明

- Android Gradle Plugin 8.6.1
- Gradle 8.7
- Java 17
- Chaquopy 16.1.0
- Python packages: Pillow, zstandard, mmh3

## 源码来源和许可

解包算法与资源路径词典基于用户上传的 NPK-X 项目。请阅读 `LICENSE-NPK-X.txt`、`LICENSE-TOOLKIT.txt` 和 `THIRD_PARTY_NOTICES.md`，遵守原项目及第三方依赖许可。

## Android APK: offline KTX / ASTC to PNG

The Android app keeps the existing NPK extraction flow and adds a second flow for an already-extracted texture ZIP:

1. Choose **选择贴图 ZIP 压缩包**.
2. Tap **离线批量转换为 PNG**.
3. Tap **保存转换结果 ZIP** and choose a folder in Android's file picker.

The build workflow compiles Arm ASTC Encoder 5.7.0 in decompression-only mode for Android arm64 and packages it into the APK. Conversion runs locally on the phone and does not upload texture data. KTX1 ASTC textures are decoded with the bundled ASTC decoder; supported KTX1 DXT1/DXT5 textures use the existing Python decoder. Other files are preserved in the output ZIP, and failed KTX conversions keep the original file and appear in the summary. The APK is arm64-v8a only in this build.

To build on GitHub, push the project to the repository's `main` or `master` branch, or open **Actions → Build NOTX UnZip Android APK → Run workflow**. The APK is uploaded as the `NOTX-UnZip-debug-apk` workflow artifact.
