const WebSocket = require('ws');

console.log('开始测试WebSocket连接...');

// 创建WebSocket连接
const ws = new WebSocket('wss://www.hanht.com/ws');

// 连接成功
ws.on('open', function() {
    console.log('✅ WebSocket连接成功');
    ws.send('Web客户端已连接');
    console.log('已发送认证消息');
});

// 收到消息
ws.on('message', function(message) {
    console.log('📩 收到消息:', message.toString());
});

// 连接关闭
ws.on('close', function() {
    console.log('❌ WebSocket连接已关闭');
});

// 连接错误
ws.on('error', function(error) {
    console.error('❌ WebSocket连接错误:', error.message);
    console.log('错误详情:', error);
    console.log('请检查：');
    console.log('1. WebSocket服务是否已启动');
    console.log('2. 网络连接是否正常');
    console.log('3. Nginx 是否已将 /ws 反向代理到本机8888端口');
});

// 5秒后关闭连接
setTimeout(function() {
    ws.close();
    console.log('测试完成');
}, 5000);
