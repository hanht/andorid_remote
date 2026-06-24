# WebSocket服务端部署说明

## 环境要求

- Node.js 12.0 或更高版本
- npm 6.0 或更高版本
- 公网服务器（47.95.36.42）
- Nginx（配置 `/ws` 和 `/remote/api/` 反向代理到本机 `127.0.0.1:8888`）

## 部署步骤

### 1. 上传文件

将以下文件上传到服务器的适当目录（例如 `/home/ubuntu/remote-control`）：

- `server.js` - WebSocket服务端代码
- `package.json` - 依赖配置文件

### 2. 安装依赖

```bash
# 进入部署目录
cd /home/ubuntu/remote-control

# 安装依赖
pnpm install
# 如果服务器未安装 pnpm，也可以使用 npm install
```

### 3. 启动服务

```bash
# 启动 WebSocket/API 服务
npm start

# 或者使用PM2进行后台运行
# 安装PM2
npm install -g pm2

# 启动服务
npm run start:pm2
```

### 4. 配置防火墙

Node.js 服务只监听本机 `127.0.0.1:8888`，公网入口由 Nginx 的 HTTPS `/ws` 和 `/remote/api/` 反向代理提供。通常不需要对公网开放 8888 端口。

```bash
# 示例 Nginx 关键配置
location /ws {
    proxy_pass http://127.0.0.1:8888;
    proxy_http_version 1.1;
    proxy_set_header Upgrade $http_upgrade;
    proxy_set_header Connection "upgrade";
    proxy_set_header Host $host;
}

# 需要放在 /remote/ 静态目录规则之前
location /remote/api/ {
    proxy_pass http://127.0.0.1:8888/remote/api/;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
}
```

### 5. 验证服务运行状态

```bash
# 检查服务是否运行
ps aux | grep node

# 检查8888端口是否在本机监听
netstat -tuln | grep 8888

# 检查 HTTP API
curl http://127.0.0.1:8888/health

# 查看服务日志
npm run logs
```

## 测试连接

### 本地测试

在服务器上运行测试脚本：

```bash
node test-websocket.js
```

### 远程测试

1. 在浏览器中打开 `https://www.hanht.com/remote/`
2. 打开浏览器开发者工具（F12）
3. 查看控制台日志，检查WebSocket连接状态
4. 查看网络标签页，确认WebSocket连接是否成功

## 常见问题排查

### 1. 连接失败

- **检查服务是否运行**：`ps aux | grep node`
- **检查端口是否监听**：`netstat -tuln | grep 8888`
- **检查防火墙设置**：`ufw status`
- **检查服务器IP和端口**：确保连接地址正确

### 2. WebSocket错误

- **混合内容错误**：确保使用 `wss://` 而非 `ws://`
- **CORS错误**：确保服务端支持跨域连接
- **证书错误**：如果使用HTTPS，确保SSL证书有效

### 3. 服务启动失败

- **端口被占用**：检查是否有其他服务占用8888端口
- **权限不足**：确保有足够权限绑定端口
- **依赖缺失**：确保已运行 `npm install`

## 服务管理

### 启动服务

```bash
npm start
```

### 停止服务

```bash
# 直接停止
Ctrl+C

# 或者使用PM2
npm run stop
```

### 重启服务

```bash
npm run restart
```

### 查看日志

```bash
npm run logs
```

## 技术支持

如果遇到问题，请检查以下几点：

1. 服务端是否正常运行
2. 端口是否已开放
3. 连接地址是否正确
4. 网络连接是否通畅

如需进一步帮助，请提供详细的错误信息和服务器环境信息。
