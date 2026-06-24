package com.remotecontrol.server

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import org.java_websocket.WebSocket
import org.java_websocket.handshake.ClientHandshake
import org.java_websocket.server.WebSocketServer
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.util.concurrent.CopyOnWriteArraySet

class WebSocketServerService : Service() {

    private var webSocketServer: RemoteControlWebSocketServer? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (webSocketServer != null) {
            return START_STICKY
        }

        try {
            val server = RemoteControlWebSocketServer(
                InetSocketAddress("0.0.0.0", PORT),
                applicationContext
            )
            server.isReuseAddr = true
            server.start()
            webSocketServer = server
            instance = server
            Log.d(TAG, "本地 WebSocket 服务已启动: ws://0.0.0.0:$PORT")
        } catch (e: Exception) {
            Log.e(TAG, "启动本地 WebSocket 服务失败: ${e.message}")
            e.printStackTrace()
        }

        return START_STICKY
    }

    override fun onDestroy() {
        val server = webSocketServer
        webSocketServer = null
        if (instance === server) {
            instance = null
        }

        try {
            server?.stop(1000)
        } catch (e: Exception) {
            Log.e(TAG, "停止本地 WebSocket 服务失败: ${e.message}")
        }

        Log.d(TAG, "本地 WebSocket 服务已停止")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    class RemoteControlWebSocketServer(
        address: InetSocketAddress,
        private val context: Context
    ) : WebSocketServer(address) {
        private val clients = CopyOnWriteArraySet<WebSocket>()

        override fun onOpen(conn: WebSocket?, handshake: ClientHandshake?) {
            conn ?: return
            clients.add(conn)
            DeviceWakeController.wakeScreen(context)
            Log.d(TAG, "本地网页端已连接: ${conn.remoteSocketAddress}")
            conn.send("ANDROID_CONNECTED")
            if (!RemoteControlService.isReady()) {
                conn.send("ERROR: 请先在安卓设备系统设置中启用无障碍控制服务")
            }
        }

        override fun onClose(conn: WebSocket?, code: Int, reason: String?, remote: Boolean) {
            conn ?: return
            clients.remove(conn)
            Log.d(TAG, "本地网页端已断开: code=$code, reason=$reason, remote=$remote")
        }

        override fun onMessage(conn: WebSocket?, message: String?) {
            if (message.isNullOrBlank()) return
            DeviceWakeController.wakeScreen(context)
            if (message.trim().equals("WAKE", ignoreCase = true)) {
                conn?.send("AWAKE")
                return
            }
            Log.d(TAG, "收到本地控制命令: $message")
            val ok = ControlCommandHandler.handle(message)
            if (!ok) {
                conn?.send("ERROR: 控制命令未执行，请确认无障碍控制服务已启用")
            }
        }

        override fun onMessage(conn: WebSocket?, message: ByteBuffer?) {
            // The browser control page only sends text commands.
        }

        override fun onError(conn: WebSocket?, ex: Exception?) {
            Log.e(TAG, "本地 WebSocket 错误: ${ex?.message}")
            ex?.printStackTrace()
        }

        override fun onStart() {
            Log.d(TAG, "本地 WebSocket 服务监听端口: $PORT")
        }

        fun sendScreenshot(imageData: ByteArray) {
            clients.forEach { client ->
                if (client.isOpen) {
                    try {
                        client.send(imageData)
                    } catch (e: Exception) {
                        Log.e(TAG, "发送本地截图失败: ${e.message}")
                    }
                }
            }
        }
    }

    companion object {
        private const val TAG = "WebSocketServer"
        const val PORT = 8080

        @Volatile
        private var instance: RemoteControlWebSocketServer? = null

        fun sendScreenshot(imageData: ByteArray) {
            instance?.sendScreenshot(imageData)
        }
    }
}
