package com.remotecontrol.server

import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.gson.Gson
import java.net.InetAddress
import java.net.NetworkInterface
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var deviceInfoTextView: TextView
    private lateinit var ipAddressTextView: TextView
    private lateinit var statusTextView: TextView
    private val REQUEST_MEDIA_PROJECTION = 1
    private var servicesStarted = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        deviceInfoTextView = findViewById(R.id.deviceInfoTextView)
        ipAddressTextView = findViewById(R.id.ipAddressTextView)
        statusTextView = findViewById(R.id.statusTextView)

        // 显示设备信息
        val deviceName = android.os.Build.MODEL
        val deviceInfo = "设备: $deviceName"
        deviceInfoTextView.text = deviceInfo

        // 显示IP地址
        val ipAddress = getIPAddress(true)
        ipAddressTextView.text = "IP地址: $ipAddress"

        // 请求MediaProjection权限
        requestScreenCapturePermission()
    }

    override fun onResume() {
        super.onResume()
        if (::statusTextView.isInitialized && servicesStarted) {
            updateServiceStatus()
        }
    }

    private fun requestScreenCapturePermission() {
        val mediaProjectionManager = getSystemService(MediaProjectionManager::class.java)
        startActivityForResult(
            mediaProjectionManager.createScreenCaptureIntent(),
            REQUEST_MEDIA_PROJECTION
        )
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_MEDIA_PROJECTION) {
            if (resultCode == RESULT_OK && data != null) {
                // 启动服务
                startServices(data, resultCode)
            } else {
                statusTextView.text = "权限被拒绝，无法启动服务"
            }
        }
    }

    private fun startServices(data: Intent, resultCode: Int) {
        // 启动云端中继 WebSocket 客户端
        ContextCompat.startForegroundService(this, Intent(this, WebSocketClientService::class.java))

        // 启动局域网直连 WebSocket 服务
        startService(Intent(this, WebSocketServerService::class.java))
        
        // 启动屏幕捕获服务（传递权限数据）
        val screenCaptureIntent = Intent(this, ScreenCaptureService::class.java)
        screenCaptureIntent.putExtra("resultCode", resultCode)
        screenCaptureIntent.putExtra("data", data)
        startService(screenCaptureIntent)

        // 向服务器发送设备信息
        sendDeviceInfo()

        servicesStarted = true
        updateServiceStatus()
    }

    private fun updateServiceStatus() {
        statusTextView.text = if (RemoteControlService.isReady()) {
            "服务已启动，远程控制已启用"
        } else {
            "服务已启动，请开启无障碍控制权限后才能远程点击/滑动"
        }
    }

    private fun sendDeviceInfo() {
        val deviceName = android.os.Build.MODEL
        val ipAddress = getIPAddress(true)
        
        // 设备信息
        val deviceInfo = mapOf(
            "name" to deviceName,
            "ip" to ipAddress,
            "port" to "8080",
            "model" to android.os.Build.MODEL,
            "version" to android.os.Build.VERSION.RELEASE
        )

        // 发送到服务器
        Thread {
            try {
                val url = java.net.URL("https://www.hanht.com/remote/api/")
                val conn = url.openConnection() as java.net.HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.doOutput = true
                
                val json = Gson().toJson(deviceInfo)
                conn.outputStream.use { os ->
                    os.write(json.toByteArray())
                }
                
                val responseCode = conn.responseCode
                Log.d("MainActivity", "设备信息发送结果: $responseCode")
            } catch (e: Exception) {
                Log.e("MainActivity", "发送设备信息失败: ${e.message}")
            }
        }.start()
    }

    private fun getIPAddress(useIPv4: Boolean): String {
        try {
            val interfaces: List<NetworkInterface> = Collections.list(NetworkInterface.getNetworkInterfaces())
            for (intf in interfaces) {
                val addrs: List<InetAddress> = Collections.list(intf.inetAddresses)
                for (addr in addrs) {
                    if (!addr.isLoopbackAddress) {
                        val sAddr: String = addr.hostAddress
                        val isIPv4: Boolean = sAddr.indexOf(':') < 0
                        if (useIPv4) {
                            if (isIPv4) return sAddr
                        } else {
                            if (!isIPv4) {
                                val delim = sAddr.indexOf('%') // drop ip6 zone suffix
                                return if (delim < 0) sAddr.uppercase() else sAddr.substring(0, delim).uppercase()
                            }
                        }
                    }
                }
            }
        } catch (ex: Exception) {
            ex.printStackTrace()
        }
        return ""
    }

    fun onStopService(view: View) {
        stopService(Intent(this, WebSocketClientService::class.java))
        stopService(Intent(this, WebSocketServerService::class.java))
        stopService(Intent(this, ScreenCaptureService::class.java))
        servicesStarted = false
        statusTextView.text = "服务已停止"
    }

    fun onOpenAccessibilitySettings(view: View) {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    fun onAllowBackground(view: View) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (powerManager.isIgnoringBatteryOptimizations(packageName)) {
            statusTextView.text = "已允许后台运行"
            return
        }

        try {
            startActivity(
                Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:$packageName")
                )
            )
        } catch (e: Exception) {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
    }
}
