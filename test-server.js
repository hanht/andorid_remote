// 简单的WebSocket服务端测试脚本
const http = require('http');

const server = http.createServer((req, res) => {
    res.statusCode = 200;
    res.setHeader('Content-Type', 'text/plain');
    res.end('WebSocket服务端测试\n');
});

server.listen(8888, '127.0.0.1', () => {
    console.log('测试服务器运行在 http://127.0.0.1:8888/');
});
