const http = require('http');
const WebSocket = require('ws');

const PORT = Number(process.env.PORT || 8888);
const HOST = process.env.HOST || '127.0.0.1';
const DEVICE_TTL_MS = 5 * 60 * 1000;

const clients = {
    web: new Set(),
    android: null
};
const devices = new Map();

function isOpen(ws) {
    return ws && ws.readyState === WebSocket.OPEN;
}

function sendJson(res, statusCode, payload) {
    const body = JSON.stringify(payload);
    res.writeHead(statusCode, {
        'Content-Type': 'application/json; charset=utf-8',
        'Content-Length': Buffer.byteLength(body),
        'Access-Control-Allow-Origin': '*',
        'Access-Control-Allow-Methods': 'GET,POST,OPTIONS',
        'Access-Control-Allow-Headers': 'Content-Type'
    });
    res.end(body);
}

function cleanupDevices() {
    const now = Date.now();
    for (const [ip, device] of devices) {
        if (now - device.lastSeenAt > DEVICE_TTL_MS) {
            devices.delete(ip);
        }
    }
}

function listDevices() {
    cleanupDevices();
    return Array.from(devices.values()).map((device) => ({
        name: device.name,
        ip: device.ip,
        port: device.port,
        model: device.model,
        version: device.version,
        lastSeenAt: device.lastSeenAt
    }));
}

function readRequestBody(req, limitBytes = 64 * 1024) {
    return new Promise((resolve, reject) => {
        let body = '';
        req.on('data', (chunk) => {
            body += chunk;
            if (Buffer.byteLength(body) > limitBytes) {
                reject(new Error('Request body too large'));
                req.destroy();
            }
        });
        req.on('end', () => resolve(body));
        req.on('error', reject);
    });
}

async function handleDeviceApi(req, res) {
    if (req.method === 'OPTIONS') {
        return sendJson(res, 204, {});
    }

    if (req.method === 'GET') {
        return sendJson(res, 200, { devices: listDevices() });
    }

    if (req.method === 'POST') {
        try {
            const body = await readRequestBody(req);
            const payload = JSON.parse(body || '{}');
            const ip = String(payload.ip || '').trim();

            if (!ip) {
                return sendJson(res, 400, { error: 'Missing device ip' });
            }

            const device = {
                name: String(payload.name || payload.model || ip),
                ip,
                port: String(payload.port || '8080'),
                model: String(payload.model || ''),
                version: String(payload.version || ''),
                lastSeenAt: Date.now()
            };
            devices.set(ip, device);
            console.log('设备已登记:', device);

            return sendJson(res, 200, { ok: true, device });
        } catch (error) {
            console.error('设备 API 处理失败:', error);
            return sendJson(res, 400, { error: 'Invalid device payload' });
        }
    }

    return sendJson(res, 405, { error: 'Method not allowed' });
}

const httpServer = http.createServer((req, res) => {
    const url = new URL(req.url, `http://${req.headers.host || 'localhost'}`);
    const path = url.pathname.replace(/\/+$/, '') || '/';

    if (path === '/health') {
        return sendJson(res, 200, {
            ok: true,
            android: isOpen(clients.android),
            webClients: clients.web.size,
            devices: listDevices().length
        });
    }

    if (path === '/remote/api' || path === '/api/devices') {
        handleDeviceApi(req, res);
        return;
    }

    sendJson(res, 404, { error: 'Not found' });
});

const wss = new WebSocket.Server({ noServer: true });

function sendToWebClients(data, options) {
    for (const webClient of clients.web) {
        if (isOpen(webClient)) {
            webClient.send(data, options);
        }
    }
}

function notifyWebClients(status) {
    sendToWebClients(status);
}

httpServer.on('upgrade', (req, socket, head) => {
    const url = new URL(req.url, `http://${req.headers.host || 'localhost'}`);
    const path = url.pathname.replace(/\/+$/, '') || '/';

    if (path !== '/' && path !== '/ws') {
        socket.destroy();
        return;
    }

    wss.handleUpgrade(req, socket, head, (ws) => {
        wss.emit('connection', ws, req);
    });
});

wss.on('connection', (ws, req) => {
    console.log('新的客户端连接:', req.socket.remoteAddress);

    let clientType = null;

    ws.on('message', (data, isBinary) => {
        try {
            if (!isBinary) {
                const message = data.toString();
                console.log('收到文本消息:', message);

                if (message.includes('Android客户端已连接')) {
                    clientType = 'android';
                    clients.android = ws;
                    console.log('安卓客户端已认证');
                    notifyWebClients('ANDROID_CONNECTED');
                    return;
                }

                if (message.includes('Web客户端已连接')) {
                    clientType = 'web';
                    clients.web.add(ws);
                    console.log('网页客户端已认证');
                    ws.send(isOpen(clients.android) ? 'ANDROID_CONNECTED' : 'ANDROID_DISCONNECTED');
                    return;
                }

                if (clientType === 'web') {
                    if (isOpen(clients.android)) {
                        console.log('转发控制指令到安卓设备');
                        clients.android.send(message);
                    } else {
                        ws.send('ERROR: 安卓设备未连接');
                    }
                    return;
                }

                if (clientType === 'android') {
                    console.log('转发安卓文本消息到网页端');
                    sendToWebClients(message);
                }

                return;
            }

            if (clientType === 'android') {
                sendToWebClients(data, { binary: true });
            }
        } catch (error) {
            console.error('消息处理错误:', error);
        }
    });

    ws.on('close', () => {
        console.log('客户端断开连接:', clientType);

        if (clientType === 'android' && clients.android === ws) {
            clients.android = null;
            notifyWebClients('ANDROID_DISCONNECTED');
        }

        if (clientType === 'web') {
            clients.web.delete(ws);
        }
    });

    ws.on('error', (error) => {
        console.error('WebSocket错误:', error);
    });
});

httpServer.on('error', (error) => {
    console.error('服务器错误:', error);
});

httpServer.listen(PORT, HOST, () => {
    console.log(`WebSocket/API 服务已启动，监听地址: ${HOST}:${PORT}`);
});

setInterval(() => {
    cleanupDevices();
    const status = {
        android: isOpen(clients.android) ? 'connected' : 'disconnected',
        web: clients.web.size,
        devices: devices.size
    };
    console.log('当前连接状态:', status);
}, 30000);
