const WebSocket = require('ws');

const url = 'wss://www.hanht.com/ws';
console.log(`Connecting to ${url}...`);

const ws = new WebSocket(url);

ws.on('open', () => {
  console.log('✅ WebSocket handshake successful!');
  ws.send('Web客户端已连接');
  setTimeout(() => ws.close(), 2000);
});

ws.on('message', (data) => {
  console.log(`📩 Received: ${data.toString()}`);
});

ws.on('error', (err) => {
  console.error(`❌ WebSocket error: ${err.message}`);
});

ws.on('close', (code, reason) => {
  console.log(`ℹ️ Connection closed: ${code} ${reason}`);
});
