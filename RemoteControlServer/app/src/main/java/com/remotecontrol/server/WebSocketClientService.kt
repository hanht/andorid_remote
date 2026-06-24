package com.remotecontrol.server

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake
import java.net.URI
import kotlin.math.min

class WebSocketClientService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private val reconnectRunnable = Runnable { connectToRelay() }
    private var webSocketClient: RemoteControlWebSocketClient? = null
    private var reconnectDelayMs = INITIAL_RECONNECT_DELAY_MS
    private var destroyed = false
    private var cpuWakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification("正在连接中继服务器"))
        acquireConnectivityLocks()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        destroyed = false
        connectToRelay()
        return START_STICKY
    }

    private fun connectToRelay() {
        if (destroyed) return

        val existingClient = webSocketClient
        if (existingClient != null && !existingClient.isClosed && !existingClient.isClosing) {
            return
        }

        handler.removeCallbacks(reconnectRunnable)
        try {
            val uri = URI(RELAY_URL)
            val client = RemoteControlWebSocketClient(uri)
            client.setConnectionLostTimeout(CONNECTION_LOST_TIMEOUT_SECONDS)
            webSocketClient = client
            instance = client
            client.connect()
            updateNotification("正在连接中继服务器")
            Log.d(TAG, "正在连接到: $uri")
        } catch (e: Exception) {
            Log.e(TAG, "启动 WebSocket 连接失败: ${e.message}")
            scheduleReconnect()
        }
    }

    private fun scheduleReconnect() {
        if (destroyed) return

        handler.removeCallbacks(reconnectRunnable)
        handler.postDelayed(reconnectRunnable, reconnectDelayMs)
        Log.d(TAG, "将在 ${reconnectDelayMs / 1000} 秒后重新连接")
        reconnectDelayMs = min(reconnectDelayMs * 2, MAX_RECONNECT_DELAY_MS)
        updateNotification("连接已断开，正在自动重连")
    }

    private fun handleConnected(client: RemoteControlWebSocketClient) {
        if (webSocketClient !== client) return
        reconnectDelayMs = INITIAL_RECONNECT_DELAY_MS
        instance = client
        updateNotification("远程控制中继已连接")
    }

    private fun handleDisconnected(client: RemoteControlWebSocketClient) {
        if (webSocketClient !== client) return
        webSocketClient = null
        if (instance === client) {
            instance = null
        }
        scheduleReconnect()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "远程连接服务",
                NotificationManager.IMPORTANCE_LOW
            )
            channel.description = "保持远程控制中继在线"
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(status: String) = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
        .setContentTitle("远程控制服务")
        .setContentText(status)
        .setSmallIcon(android.R.drawable.stat_sys_upload)
        .setOngoing(true)
        .setContentIntent(
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        )
        .build()

    private fun updateNotification(status: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(status))
    }

    @Suppress("DEPRECATION")
    private fun acquireConnectivityLocks() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        cpuWakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "$packageName:RelayConnection"
        ).apply {
            setReferenceCounted(false)
            acquire()
        }

        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        wifiLock = wifiManager.createWifiLock(
            WifiManager.WIFI_MODE_FULL_HIGH_PERF,
            "$packageName:RelayWifi"
        ).apply {
            setReferenceCounted(false)
            acquire()
        }
    }

    override fun onDestroy() {
        destroyed = true
        handler.removeCallbacks(reconnectRunnable)
        webSocketClient?.close()
        webSocketClient = null
        instance = null

        if (cpuWakeLock?.isHeld == true) cpuWakeLock?.release()
        if (wifiLock?.isHeld == true) wifiLock?.release()
        cpuWakeLock = null
        wifiLock = null

        stopForeground(true)
        Log.d(TAG, "服务已停止")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private inner class RemoteControlWebSocketClient(serverUri: URI) : WebSocketClient(serverUri) {

        override fun onOpen(handshakedata: ServerHandshake?) {
            Log.d(TAG, "已连接到服务器")
            handleConnected(this)
            send("Android客户端已连接")
            if (!RemoteControlService.isReady()) {
                send("ERROR: 请先在安卓设备系统设置中启用无障碍控制服务")
            }
        }

        override fun onMessage(message: String?) {
            Log.d(TAG, "收到消息: $message")
            message?.let {
                DeviceWakeController.wakeScreen(this@WebSocketClientService)
                if (it.trim().equals("WAKE", ignoreCase = true)) {
                    if (isOpen) send("AWAKE")
                    return
                }

                val ok = ControlCommandHandler.handle(it)
                if (!ok && isOpen) {
                    send("ERROR: 控制命令未执行，请确认无障碍控制服务已启用")
                }
            }
        }

        override fun onClose(code: Int, reason: String?, remote: Boolean) {
            Log.d(TAG, "已断开连接: code=$code, reason=$reason, remote=$remote")
            handleDisconnected(this)
        }

        override fun onError(ex: Exception?) {
            Log.e(TAG, "WebSocket 错误: ${ex?.message}")
            if (!isOpen) {
                handleDisconnected(this)
            }
        }

        fun sendScreenshot(imageData: ByteArray) {
            if (!isOpen) return
            try {
                send(imageData)
            } catch (e: Exception) {
                Log.e(TAG, "发送截图失败: ${e.message}")
            }
        }
    }

    companion object {
        private const val TAG = "WebSocketClient"
        private const val RELAY_URL = "wss://www.hanht.com/ws"
        private const val NOTIFICATION_CHANNEL_ID = "RelayConnection"
        private const val NOTIFICATION_ID = 124
        private const val CONNECTION_LOST_TIMEOUT_SECONDS = 20
        private const val INITIAL_RECONNECT_DELAY_MS = 2_000L
        private const val MAX_RECONNECT_DELAY_MS = 60_000L

        @Volatile
        private var instance: RemoteControlWebSocketClient? = null

        fun sendScreenshot(imageData: ByteArray) {
            instance?.sendScreenshot(imageData)
        }

        fun isConnected(): Boolean = instance?.isOpen == true
    }
}
