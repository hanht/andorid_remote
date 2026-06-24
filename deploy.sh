#!/bin/bash

# 部署WebSocket服务端脚本

echo "开始部署WebSocket服务端..."

# 检查并安装Node.js和npm
if ! command -v node &> /dev/null; then
    echo "正在安装Node.js..."
    curl -fsSL https://rpm.nodesource.com/setup_16.x | bash -
    yum install -y nodejs
    echo "Node.js安装完成"
else
    echo "Node.js已安装"
fi

# 检查npm
if ! command -v npm &> /dev/null; then
    echo "npm未安装，请手动安装"
    exit 1
else
    echo "npm已安装"
fi

# 进入部署目录
cd /root/remote-control

# 安装依赖
echo "正在安装依赖..."
npm install

# 检查安装结果
if [ $? -eq 0 ]; then
    echo "依赖安装成功"
else
    echo "依赖安装失败"
    exit 1
fi

# 启动服务
echo "正在启动WebSocket服务..."
npm start

echo "部署完成"
