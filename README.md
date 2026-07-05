# FileShare — Android 局域网文件共享服务器

将 [FileBrowser](https://github.com/filebrowser/filebrowser) 交叉编译后嵌入 Android APK，
在手机上直接运行完整的文件管理服务器。

> **无需 root，无需外部依赖，开箱即用。**

---

## ✨ 功能

- 📁 **文件管理** — 上传、下载、预览、分享、重命名、删除
- 🌐 **局域网访问** — 浏览器打开 `http://手机IP:8080` 即可使用
- 📱 **QR 码** — 扫码快速连接，无需输入地址
- 🔒 **账号认证** — 默认 admin/admin，支持自定义
- 📋 **实时日志** — 服务器运行状态一目了然
- 🗂️ **自定义目录** — 选择或输入要共享的文件夹路径

## 📲 使用方式

### 下载 APK

从 [Releases](https://github.com/your-username/FileShareApp/releases) 页面下载最新 APK，
或通过 GitHub Actions 构建：

1. Fork 本仓库
2. 进入 Actions → Build FileShare APK → Run workflow
3. 等待构建完成，下载 Artifact

### 安装运行

1. 安装 APK 到 Android 手机（Android 8.0+）
2. 打开 App，点击 **启动服务器**
3. 允许通知权限（用于前台服务常驻）
4. 如果共享外部存储目录，授予文件管理权限
5. 同一局域网内，浏览器访问 App 显示的 URL 地址

> **默认账号**：`admin` / `admin`（可在 App 右上角设置中修改）

---

## 🛠 技术架构

```
┌─────────────────────────────────────┐
│          Jetpack Compose UI          │
│  (Material 3, ZXing QR Code, SAF)   │
├─────────────────────────────────────┤
│      Foreground Service (常驻)       │
│  管理 FileBrowser 进程生命周期       │
├─────────────────────────────────────┤
│   FileBrowser (Go ARM64 二进制)      │
│   完整的 HTTP 文件管理服务器          │
└─────────────────────────────────────┘
```

### 关键技术点

| 模块 | 技术 |
|------|------|
| **UI** | Kotlin + Jetpack Compose + Material 3 |
| **进程管理** | Foreground Service + ProcessBuilder |
| **QR 码** | ZXing (`com.google.zxing:core`) |
| **目录选择** | Storage Access Framework (SAF) |
| **服务器** | FileBrowser (Go, 交叉编译 ARM64) |
| **构建** | Gradle Kotlin DSL + GitHub Actions |

### 项目结构

```
FileShareApp/
├── app/
│   ├── src/main/
│   │   ├── assets/                  # → filebrowser-arm64 (CI 生成)
│   │   ├── java/com/example/fileshare/
│   │   │   ├── MainActivity.kt      # 入口 Activity
│   │   │   ├── service/
│   │   │   │   └── FileServerService.kt  # 前台服务核心
│   │   │   ├── util/
│   │   │   │   ├── BinaryManager.kt # 二进制管理 & 进程控制
│   │   │   │   └── NetworkUtil.kt   # 局域网 IP 获取
│   │   │   └── ui/
│   │   │       ├── MainScreen.kt    # 主界面
│   │   │       ├── QrCodeView.kt    # QR 码组件
│   │   │       └── theme/           # Material 3 主题
│   │   └── res/                     # 资源文件
│   └── build.gradle.kts
├── build.gradle.kts
├── settings.gradle.kts
└── .github/workflows/build.yml      # CI 流水线
```

---

## 🔧 本地构建

### 前置条件

- Android Studio Hedgehog (2023.1+) 或更高版本
- JDK 17
- Gradle 8.4
- Go 1.22+

### 构建步骤

```bash
# 1. 克隆本仓库
git clone https://github.com/your-username/FileShareApp.git
cd FileShareApp

# 2. 交叉编译 FileBrowser
git clone https://github.com/filebrowser/filebrowser.git
cd filebrowser
CGO_ENABLED=0 GOOS=linux GOARCH=arm64 go build -ldflags="-s -w" -o filebrowser-arm64 .
cd ..

# 3. 复制二进制到 assets
mkdir -p app/src/main/assets
cp filebrowser/filebrowser-arm64 app/src/main/assets/

# 4. 构建 APK
./gradlew assembleDebug

# APK 生成在: app/build/outputs/apk/debug/app-debug.apk
```

### 在 Android Studio 中打开

直接用 Android Studio 打开 `FileShareApp/` 目录，
等待 Gradle 同步完成后即可构建运行。

> ⚠️ 首次运行前需确保 `app/src/main/assets/filebrowser-arm64` 存在，
> 否则 App 会因找不到二进制文件而崩溃。请先执行交叉编译步骤。

---

## 🤖 CI/CD (GitHub Actions)

提交到 `main` 分支后，GitHub Actions 会自动：

1. 拉取 FileBrowser 源码并交叉编译
2. 将产物嵌入 Android 项目 assets
3. 构建并签名 APK
4. 上传 APK 为 Artifact

### 配置签名（可选）

如果要生成签名的 Release APK，需要在 GitHub 仓库设置以下 Secrets：

| Secret | 说明 |
|--------|------|
| `KEYSTORE_PATH` | Keystore 文件的 **Base64 编码**内容 |
| `KEY_ALIAS` | 密钥别名 |
| `KEY_PASSWORD` | 密钥密码 |
| `STORE_PASSWORD` | Keystore 密码 |

生成 Base64 编码：
```bash
# Linux
base64 -w0 your-release-key.jks

# macOS
base64 -i your-release-key.jks -o - | pbcopy
```

> 如果未配置 Secrets，CI 会使用 Android 默认的 Debug 签名。

---

## ⚙️ FileBrowser 启动参数

```bash
./filebrowser-arm64 \
  -a 0.0.0.0 \          # 监听所有网络接口
  -p 8080 \             # 端口
  -r /sdcard/FileShare \ # 共享根目录
  -d /data/.../filebrowser.db  # 数据库路径
  --username admin \     # 管理员账号
  --password admin       # 管理员密码
```

## 📄 许可

本项目基于 MIT 许可开源。
FileBrowser 使用 [Apache License 2.0](https://github.com/filebrowser/filebrowser/blob/master/LICENSE)。

---

## 🙏 致谢

- [FileBrowser](https://github.com/filebrowser/filebrowser) — 优秀的开源文件管理服务器
- [ZXing](https://github.com/zxing/zxing) — 二维码生成库
- [Jetpack Compose](https://developer.android.com/jetpack/compose) — 现代 Android UI 工具包