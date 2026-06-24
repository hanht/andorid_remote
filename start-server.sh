#!/bin/bash

# 启动WebSocket服务端脚本

echo "启动WebSocket服务端..."

# 检查端口是否被占用
if lsof -i :8888 > /dev/null; then
    echo "端口8888已被占用，停止占用该端口的进程..."
    lsof -ti :8888 | xargs kill -9
fi

# 进入服务目录
cd /root/remote-control

# 启动服务
echo "正在启动WebSocket服务..."
node server.js
