const WebSocket = require('ws');

// 测试WebSocket连接
function testWebSocketConnection() {
    console.log('开始测试WebSocket连接...');
    
    const ws = new WebSocket('ws://127.0.0.1:8888');
    
    ws.on('open', function() {
        console.log('✅ WebSocket连接成功');
        ws.send('Web客户端已连接');
    });
    
    ws.on('message', function(message) {
        console.log('📩 收到消息:', message.toString());
    });
    
    ws.on('close', function() {
        console.log('❌ WebSocket连接已关闭');
    });
    
    ws.on('error', function(error) {
        console.error('❌ WebSocket连接错误:', error.message);
        console.log('请检查：');
        console.log('1. WebSocket服务是否已启动');
        console.log('2. 8888端口是否正在本机监听');
        console.log('3. 服务器防火墙是否已配置');
    });
    
    // 5秒后关闭连接
    setTimeout(function() {
        ws.close();
        console.log('测试完成');
    }, 5000);
}

// 启动测试
testWebSocketConnection();
