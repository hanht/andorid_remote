let ws = null;
let connectScreen = document.getElementById('connect-screen');
let controlScreen = document.getElementById('control-screen');
let connectBtn = document.getElementById('connect-btn');
let disconnectBtn = document.getElementById('disconnect-btn');
let discoverBtn = document.getElementById('discover-btn');
let ipAddressInput = document.getElementById('ip-address');
let connectStatus = document.getElementById('connect-status');
let connectionStatus = document.getElementById('connection-status');
let remoteScreen = document.getElementById('remote-screen');
let homeBtn = document.getElementById('home-btn');
let backBtn = document.getElementById('back-btn');
let menuBtn = document.getElementById('menu-btn');
let deviceList = document.getElementById('device-list');
let devicesUl = document.getElementById('devices');
let isRelayConnection = false;
let lastTouchEndAt = 0;

// 连接按钮点击事件
connectBtn.addEventListener('click', connectToServer);

// 断开连接按钮点击事件
disconnectBtn.addEventListener('click', disconnectFromServer);

// 设备发现按钮点击事件
discoverBtn.addEventListener('click', discoverDevices);

// 导航按钮点击事件
homeBtn.addEventListener('click', () => sendCommand('HOME'));
backBtn.addEventListener('click', () => sendCommand('BACK'));
menuBtn.addEventListener('click', () => sendCommand('MENU'));

// 屏幕触摸事件
remoteScreen.addEventListener('touchstart', handleTouchStart, { passive: false });
remoteScreen.addEventListener('touchmove', handleTouchMove, { passive: false });
remoteScreen.addEventListener('touchend', handleTouchEnd, { passive: false });
remoteScreen.addEventListener('click', handleClick, false);

// 触摸状态
let touchState = {
    startX: 0,
    startY: 0,
    endX: 0,
    endY: 0
};

// 连接到服务器
function connectToServer() {
    let ipAddress = ipAddressInput.value.trim();
    if (!ipAddress) {
        showConnectStatus('请输入IP地址', 'error');
        return;
    }

    try {
        // 创建WebSocket连接
        let protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
        let wsUrl = '';

        isRelayConnection = ipAddress.includes('hanht.com') || ipAddress === 'relay';

        if (isRelayConnection) {
            wsUrl = `wss://www.hanht.com/ws`;
            console.log('使用中继服务器连接:', wsUrl);
        } else if (window.location.protocol === 'https:' && isPrivateIP(ipAddress)) {
            showConnectStatus('HTTPS模式下无法直接连接局域网IP，请改用中继(输入relay)或访问非安全(http)版本', 'error');
            return;
        } else {
            wsUrl = `${protocol}//${ipAddress}:8080`;
        }

        console.log('正在连接到:', wsUrl);
        ws = new WebSocket(wsUrl);
        ws.binaryType = 'blob';

        ws.onopen = () => {
            ws.send('Web客户端已连接');
            ws.send('WAKE');
            showConnectStatus('连接成功', 'success');
            connectScreen.classList.add('hidden');
            controlScreen.classList.remove('hidden');
            connectionStatus.textContent = isRelayConnection ? '已连接中继，等待设备' : '已直连设备';
        };

        ws.onmessage = (event) => {
            handleMessage(event);
        };

        ws.onerror = (error) => {
            showConnectStatus('连接失败，请检查IP地址', 'error');
            console.error('WebSocket错误:', error);
        };

        ws.onclose = () => {
            showConnectStatus('连接已断开', 'error');
            controlScreen.classList.add('hidden');
            connectScreen.classList.remove('hidden');
        };

    } catch (error) {
        showConnectStatus('连接失败，请检查IP地址', 'error');
        console.error('连接错误:', error);
    }
}

// 断开连接
function disconnectFromServer() {
    if (ws) {
        ws.close();
        ws = null;
    }
    controlScreen.classList.add('hidden');
    connectScreen.classList.remove('hidden');
}

// 处理接收到的消息
function handleMessage(event) {
    // 检查是否是二进制数据（屏幕截图）
    if (event.data instanceof Blob) {
        // 明确指定 MIME 类型，避免浏览器无法识别图片格式
        const blob = new Blob([event.data], { type: 'image/jpeg' });
        const url = URL.createObjectURL(blob);

        // 释放旧的 Blob URL，避免内存泄漏
        if (remoteScreen.src && remoteScreen.src.startsWith('blob:')) {
            URL.revokeObjectURL(remoteScreen.src);
        }
        remoteScreen.src = url;
    } else {
        // 处理文本消息
        console.log('收到消息:', event.data);
        if (event.data === 'ANDROID_CONNECTED') {
            connectionStatus.textContent = '安卓设备在线';
            if (isRelayConnection) {
                sendCommand('WAKE');
            }
        } else if (event.data === 'ANDROID_DISCONNECTED') {
            connectionStatus.textContent = '安卓设备离线';
        } else if (event.data.startsWith('ERROR:')) {
            connectionStatus.textContent = event.data.replace('ERROR:', '').trim();
        }
    }
}

// 发送命令
function sendCommand(command) {
    if (ws && ws.readyState === WebSocket.OPEN) {
        ws.send(command);
    }
}

function mapToRemoteScreen(clientX, clientY, clampToScreen = false) {
    if (!remoteScreen.naturalWidth || !remoteScreen.naturalHeight) {
        return null;
    }

    const box = remoteScreen.getBoundingClientRect();
    const imageAspect = remoteScreen.naturalWidth / remoteScreen.naturalHeight;
    const boxAspect = box.width / box.height;
    let width;
    let height;
    let left;
    let top;

    if (boxAspect > imageAspect) {
        height = box.height;
        width = height * imageAspect;
        left = box.left + (box.width - width) / 2;
        top = box.top;
    } else {
        width = box.width;
        height = width / imageAspect;
        left = box.left;
        top = box.top + (box.height - height) / 2;
    }

    const right = left + width;
    const bottom = top + height;
    const isOutside = clientX < left || clientX > right || clientY < top || clientY > bottom;
    if (isOutside && !clampToScreen) {
        return null;
    }

    const boundedX = Math.min(Math.max(clientX, left), right);
    const boundedY = Math.min(Math.max(clientY, top), bottom);

    return {
        x: Math.min(remoteScreen.naturalWidth - 1, Math.floor((boundedX - left) * remoteScreen.naturalWidth / width)),
        y: Math.min(remoteScreen.naturalHeight - 1, Math.floor((boundedY - top) * remoteScreen.naturalHeight / height))
    };
}

// 处理触摸开始
function handleTouchStart(e) {
    e.preventDefault();
    let touch = e.touches[0];
    touchState.startX = touch.clientX;
    touchState.startY = touch.clientY;
    touchState.endX = touch.clientX;
    touchState.endY = touch.clientY;
}

// 处理触摸移动
function handleTouchMove(e) {
    e.preventDefault();
    let touch = e.touches[0];
    touchState.endX = touch.clientX;
    touchState.endY = touch.clientY;
}

// 处理触摸结束
function handleTouchEnd(e) {
    e.preventDefault();
    lastTouchEndAt = Date.now();

    if (e.changedTouches && e.changedTouches.length > 0) {
        touchState.endX = e.changedTouches[0].clientX;
        touchState.endY = e.changedTouches[0].clientY;
    }

    const start = mapToRemoteScreen(touchState.startX, touchState.startY);
    const end = mapToRemoteScreen(touchState.endX, touchState.endY, true);
    if (!start || !end) {
        return;
    }

    // 计算移动距离
    let distance = Math.sqrt(
        Math.pow(end.x - start.x, 2) + Math.pow(end.y - start.y, 2)
    );

    if (distance < 10) {
        // 点击事件
        sendCommand(`CLICK ${start.x} ${start.y}`);
    } else {
        // 滑动事件
        sendCommand(`SWIPE ${start.x} ${start.y} ${end.x} ${end.y}`);
    }
}

// 处理点击事件
function handleClick(e) {
    if (Date.now() - lastTouchEndAt < 500) {
        return;
    }

    const point = mapToRemoteScreen(e.clientX, e.clientY);
    if (!point) {
        return;
    }

    // 发送点击命令
    sendCommand(`CLICK ${point.x} ${point.y}`);
}

// 显示连接状态
function showConnectStatus(message, type) {
    connectStatus.textContent = message;
    connectStatus.className = `status ${type}`;
}

// 设备自动发现功能
function keepLatestDevices(devices) {
    const latestByDevice = new Map();

    devices.forEach(device => {
        const modelAndName = `${device.model || ''}:${device.name || ''}`;
        const identity = device.deviceId || (modelAndName === ':' ? device.ip : modelAndName);
        const timestamp = Date.parse(device.lastSeen || device.lastSeenAt || '') || 0;
        const existing = latestByDevice.get(identity);
        const existingTimestamp = existing
            ? Date.parse(existing.lastSeen || existing.lastSeenAt || '') || 0
            : -1;

        if (!existing || timestamp >= existingTimestamp) {
            latestByDevice.set(identity, device);
        }
    });

    return Array.from(latestByDevice.values()).sort((left, right) => {
        const leftTimestamp = Date.parse(left.lastSeen || left.lastSeenAt || '') || 0;
        const rightTimestamp = Date.parse(right.lastSeen || right.lastSeenAt || '') || 0;
        return rightTimestamp - leftTimestamp;
    });
}

function discoverDevices() {
    showConnectStatus('正在从服务器获取设备列表...', 'info');
    deviceList.classList.remove('hidden');
    devicesUl.innerHTML = '';

    // 从服务器获取设备列表
    fetch('https://www.hanht.com/remote/api/')
        .then(response => response.json())
        .then(data => {
            const latestDevices = keepLatestDevices(data.devices || []);
            if (latestDevices.length > 0) {
                latestDevices.forEach(device => {
                    addDeviceToList(device, 'relay');
                });
                showConnectStatus(`发现 ${latestDevices.length} 个设备`, 'success');
            } else {
                // 如果是HTTPS环境，跳过局域网扫描以避免Mixed Content错误
                if (window.location.protocol === 'https:') {
                    showConnectStatus('未发现云端设备，HTTPS模式下无法扫描局域网', 'error');
                } else {
                    scanLocalNetwork();
                }
            }
        })
        .catch(error => {
            console.error('从服务器获取设备列表失败:', error);
            // 如果是HTTPS环境，跳过局域网扫描
            if (window.location.protocol === 'https:') {
                showConnectStatus('连接发现服务失败', 'error');
            } else {
                scanLocalNetwork();
            }
        });
}

// 扫描本地网络
function scanLocalNetwork() {
    showConnectStatus('正在扫描局域网设备...', 'info');

    // 获取本地IP地址前缀
    const localIp = getLocalIP();
    if (!localIp) {
        showConnectStatus('无法获取本地网络信息', 'error');
        return;
    }

    const ipPrefix = localIp.substring(0, localIp.lastIndexOf('.')) + '.';
    const discoveredDevices = [];
    const ipsToScan = [];

    // 扫描IP范围 (1-254)
    for (let i = 1; i <= 254; i++) {
        const ip = ipPrefix + i;
        if (ip !== localIp) {
            ipsToScan.push(ip);
        }
    }

    let scanCount = 0;
    const maxScans = ipsToScan.length;

    ipsToScan.forEach((ip) => {

        // 尝试连接
        try {
            const testWs = new WebSocket(`ws://${ip}:8080`);

            testWs.onopen = () => {
                // 连接成功，可能是我们的设备
                const device = { ip: ip, name: `设备 (${ip})` };
                discoveredDevices.push(device);
                addDeviceToList(device);
                testWs.close();
            };

            testWs.onerror = () => {
                // 连接失败，不是我们的设备
                testWs.close();
            };

            testWs.onclose = () => {
                scanCount++;
                if (scanCount >= maxScans) {
                    if (discoveredDevices.length > 0) {
                        showConnectStatus(`发现 ${discoveredDevices.length} 个设备`, 'success');
                    } else {
                        showConnectStatus('未发现设备，请确保安卓设备已启动服务', 'error');
                    }
                }
            };

            // 设置超时
            setTimeout(() => {
                testWs.close();
            }, 1000);

        } catch (e) {
            scanCount++;
        }
    });
}

// 添加设备到列表
function addDeviceToList(device, connectionMode = 'direct') {
    const li = document.createElement('li');
    const deviceName = device.name || device.ip;
    const deviceIp = device.ip;
    li.textContent = `${deviceName} (${deviceIp})`;
    li.addEventListener('click', () => {
        // 选择设备
        document.querySelectorAll('#devices li').forEach(item => {
            item.classList.remove('selected');
        });
        li.classList.add('selected');
        ipAddressInput.value = connectionMode === 'relay' ? 'relay' : deviceIp;
        showConnectStatus(
            connectionMode === 'relay'
                ? `已选择 ${deviceName}，将通过中继连接`
                : `已选择 ${deviceName}，将直连 ${deviceIp}`,
            'info'
        );
    });
    devicesUl.appendChild(li);
}

// 获取本地IP地址
function getLocalIP() {
    try {
        const RTCPeerConnection = window.RTCPeerConnection || window.mozRTCPeerConnection || window.webkitRTCPeerConnection;

        if (!RTCPeerConnection) {
            return null;
        }

        const pc = new RTCPeerConnection({ iceServers: [] });

        pc.createDataChannel('');
        pc.createOffer().then(offer => {
            pc.setLocalDescription(offer);
        });

        pc.onicecandidate = (event) => {
            if (!event || !event.candidate) return;

            const ipRegex = /([0-9]{1,3}\.[0-9]{1,3}\.[0-9]{1,3}\.[0-9]{1,3})/;
            const match = event.candidate.candidate.match(ipRegex);

            if (match) {
                pc.close();
                return match[1];
            }
        };

        // 超时处理
        setTimeout(() => {
            pc.close();
        }, 1000);

    } catch (e) {
        console.error('获取本地IP失败:', e);
    }

    // 默认返回一个常见的局域网前缀
    return '192.168.1.1';
}

// 检查是否为私有/局域网 IP
function isPrivateIP(ip) {
    return /^(192\.168\.|169\.254\.|10\.|172\.(1[6-9]|2[0-9]|3[0-1])\.)/.test(ip) || ip === 'localhost' || ip === '127.0.0.1';
}

// 页面关闭时断开连接
window.onbeforeunload = () => {
    if (ws) {
        ws.close();
    }
};
