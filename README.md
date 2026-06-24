# Android Remote Control

通过浏览器远程查看并控制 Android 手机。项目支持两种连接方式：

- **公网中继**：Android 手机和控制端不需要在同一网络，通过 WSS 中继连接。
- **局域网直连**：浏览器直接连接 Android 手机的 `8080` 端口。

## 能做什么

- 在浏览器中实时查看 Android 屏幕
- 点击、滑动、返回、Home 和最近任务
- 自动发现最近在线的设备
- 手机熄屏后保持中继在线，网页连接时自动唤醒
- 中继断线后自动重连
- 支持电脑、iPhone、iPad 和其他带现代浏览器的设备作为控制端

## 项目结构

```text
android-remote/
├── RemoteControlServer/       # Android 被控端
│   └── app/
├── web-client/                # 浏览器控制端
│   └── api/                   # 设备登记接口（PHP 持久化版本）
├── server.js                  # Node.js WebSocket 中继服务
├── package.json               # 中继服务依赖和启动命令
├── DEPLOYMENT.md              # 服务端部署补充说明
└── test-*.js                  # WebSocket 测试脚本
```

通信流程：

```text
浏览器 ── WSS ──> Node.js 中继 <── WSS ── Android
   │                                      │
   └── 控制命令                    JPEG 屏幕帧 ──┘
```

局域网模式下，浏览器也可以直接连接 `ws://手机IP:8080`，不经过中继服务器。

## 环境要求

### Android 构建环境

- JDK 17 或更高版本
- Android SDK Platform 33
- Android Platform Tools（包含 `adb`）
- Android 8.0（API 26）或更高版本的测试设备

### 中继服务器

- Node.js 18 或更高版本
- npm 或 pnpm
- Nginx（公网 HTTPS/WSS 部署时需要）
- PHP（使用 `web-client/api/` 保存设备发现记录时需要）

## 快速开始

### 1. 构建 Android 应用

在 `RemoteControlServer/local.properties` 中配置 Android SDK：

```properties
sdk.dir=/你的/Android/sdk/路径
```

构建 Debug APK：

```bash
cd RemoteControlServer
./gradlew :app:assembleDebug
```

生成的 APK 位于：

```text
RemoteControlServer/app/build/outputs/apk/debug/app-debug.apk
```

连接手机后安装：

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### 2. 配置 Android 手机

首次打开应用后，依次完成：

1. 允许屏幕捕获。
2. 点击“开启无障碍控制权限”，启用“远程控制输入服务”。
3. 点击“允许后台运行”，关闭系统对本应用的电池优化。
4. 在华为手机的“应用启动管理”中关闭自动管理，并允许自启动、关联启动和后台活动。

只要通知栏显示“远程控制中继已连接”，手机端就已经在线。

### 3. 从浏览器连接

1. 打开 `https://你的域名/remote/`。
2. 点击“自动发现设备”。
3. 选择设备后点击“连接”。
4. 等待状态变为“安卓设备在线”。

也可以在输入框直接输入 `relay`，再点击“连接”。

> HTTPS 页面不能直接连接局域网的 `ws://` 地址。使用线上 HTTPS 页面时，请选择 `relay`；需要局域网直连时，请从 HTTP 页面打开控制端。

## 本地运行网页

在项目根目录执行：

```bash
cd web-client
python3 -m http.server 8000
```

浏览器访问 `http://localhost:8000`。局域网直连时，输入 Android 应用显示的 IP 地址。

## 启动中继服务

安装依赖：

```bash
pnpm install
```

启动服务：

```bash
HOST=127.0.0.1 PORT=8888 pnpm start
```

健康检查：

```bash
curl http://127.0.0.1:8888/health
```

正常响应示例：

```json
{"ok":true,"android":true,"webClients":1,"devices":1}
```

生产环境建议使用 PM2 或 systemd 管理 Node.js 进程，并由 Nginx 提供 HTTPS/WSS：

```nginx
location /ws {
    proxy_pass http://127.0.0.1:8888;
    proxy_http_version 1.1;
    proxy_set_header Upgrade $http_upgrade;
    proxy_set_header Connection "upgrade";
    proxy_set_header Host $host;
}
```

将 `web-client/` 部署到网站的 `/remote/` 目录。设备发现接口有两种方式：

- 使用 `web-client/api/index.php`：设备记录保存在服务器文件中，并按设备保留最新 IP。
- 将 `/remote/api/` 反向代理到 Node.js：使用 `server.js` 的内存设备列表，服务重启后记录清空。

完整部署说明见 [DEPLOYMENT.md](DEPLOYMENT.md)。

## 休眠与锁屏

Android 中继客户端使用前台服务、CPU/Wi-Fi 锁、心跳检测和自动重连，手机熄屏后仍可接收连接。网页连接成功后会发送 `WAKE` 命令点亮手机。

需要注意：

- 启用 PIN、密码或安全键盘后，锁屏输入页面可能显示为黑屏。
- 这是 Android 的安全保护，远程控制不能绕过安全锁屏。
- 遇到这种情况需要在手机上本地解锁，或在可信环境中改用无密码锁屏/系统提供的智能解锁。
- 华为系统还可能单独限制后台应用，请务必配置“应用启动管理”。

## 常见问题

### 自动发现显示旧 IP

设备接口会按设备名称和型号合并记录，只返回最后在线的 IP。若仍显示旧数据，请刷新页面并重新启动 Android 应用。

### 网页显示“安卓设备离线”

检查以下项目：

1. Android 通知栏是否显示“远程控制中继已连接”。
2. 手机能否访问 `https://你的域名`。
3. 中继服务健康检查中的 `android` 是否为 `true`。
4. 华为“应用启动管理”和电池优化是否已经放行。

### 能看到画面，但无法点击

确认系统无障碍设置中的“远程控制输入服务”处于开启状态。

### 屏幕一直黑色

重新打开 Android 应用并允许屏幕捕获。若当前停留在密码或安全键盘页面，请先本地解锁。

### HTTPS 页面不能连接手机 IP

这是浏览器的混合内容限制。线上页面请输入 `relay`；局域网直连请使用 HTTP 页面。

## 验证代码

Android 构建与单元测试：

```bash
cd RemoteControlServer
./gradlew :app:assembleDebug :app:testDebugUnitTest
```

Node.js 语法检查：

```bash
node --check server.js
node --check web-client/script.js
```

## 安全说明

当前版本面向个人或受信任环境，**尚未实现账号登录、设备配对码或连接鉴权**。WSS 可以加密传输，但不能代替访问控制。

请不要把未加鉴权的中继服务开放给不受信任的用户，也不要用于控制未经授权的设备。正式部署前建议增加用户认证、设备配对、速率限制和操作审计。
