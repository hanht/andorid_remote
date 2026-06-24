package com.remotecontrol.server

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent

class RemoteControlService : AccessibilityService() {

    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.d(TAG, "无障碍远程控制服务已连接")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // No event inspection is needed; the service is used only to dispatch user-approved gestures.
    }

    override fun onInterrupt() {
        Log.d(TAG, "无障碍远程控制服务被中断")
    }

    override fun onDestroy() {
        if (instance === this) {
            instance = null
        }
        Log.d(TAG, "无障碍远程控制服务已停止")
        super.onDestroy()
    }

    private fun dispatchGestureOnMain(gesture: GestureDescription): Boolean {
        mainHandler.post {
            dispatchGesture(gesture, null, null)
        }
        return true
    }

    private fun click(x: Int, y: Int): Boolean {
        val path = Path().apply {
            moveTo(x.toFloat(), y.toFloat())
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, CLICK_DURATION_MS))
            .build()
        return dispatchGestureOnMain(gesture)
    }

    private fun swipe(x1: Int, y1: Int, x2: Int, y2: Int): Boolean {
        val path = Path().apply {
            moveTo(x1.toFloat(), y1.toFloat())
            lineTo(x2.toFloat(), y2.toFloat())
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, SWIPE_DURATION_MS))
            .build()
        return dispatchGestureOnMain(gesture)
    }

    companion object {
        private const val TAG = "RemoteControl"
        private const val CLICK_DURATION_MS = 80L
        private const val SWIPE_DURATION_MS = 350L

        @Volatile
        private var instance: RemoteControlService? = null

        fun isReady(): Boolean = instance != null

        fun simulateClick(x: Int, y: Int): Boolean {
            val service = instance ?: return false
            Log.d(TAG, "执行点击: $x, $y")
            return service.click(x, y)
        }

        fun simulateTouch(x: Int, y: Int): Boolean = simulateClick(x, y)

        fun simulateSwipe(x1: Int, y1: Int, x2: Int, y2: Int): Boolean {
            val service = instance ?: return false
            Log.d(TAG, "执行滑动: $x1,$y1 -> $x2,$y2")
            return service.swipe(x1, y1, x2, y2)
        }

        fun performBack(): Boolean {
            val service = instance ?: return false
            Log.d(TAG, "执行返回")
            return service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
        }

        fun performHome(): Boolean {
            val service = instance ?: return false
            Log.d(TAG, "执行 Home")
            return service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
        }

        fun performRecents(): Boolean {
            val service = instance ?: return false
            Log.d(TAG, "执行最近任务")
            return service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS)
        }
    }
}
