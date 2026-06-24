package com.remotecontrol.server

import android.util.Log

object ControlCommandHandler {
    private const val TAG = "ControlCommand"

    fun handle(command: String): Boolean {
        val parts = command.trim().split(Regex("\\s+"))
        if (parts.isEmpty()) return true

        return when (parts[0].uppercase()) {
            "WEB客户端已连接".uppercase(),
            "WEB_CONNECTED",
            "ANDROID_CONNECTED",
            "ANDROID_DISCONNECTED" -> true

            "MOVE",
            "CLICK" -> {
                val x = parts.getOrNull(1)?.toIntOrNull()
                val y = parts.getOrNull(2)?.toIntOrNull()
                if (x == null || y == null) {
                    Log.w(TAG, "点击命令格式错误: $command")
                    false
                } else {
                    RemoteControlService.simulateClick(x, y)
                }
            }

            "SWIPE" -> {
                val x1 = parts.getOrNull(1)?.toIntOrNull()
                val y1 = parts.getOrNull(2)?.toIntOrNull()
                val x2 = parts.getOrNull(3)?.toIntOrNull()
                val y2 = parts.getOrNull(4)?.toIntOrNull()
                if (x1 == null || y1 == null || x2 == null || y2 == null) {
                    Log.w(TAG, "滑动命令格式错误: $command")
                    false
                } else {
                    RemoteControlService.simulateSwipe(x1, y1, x2, y2)
                }
            }

            "BACK" -> RemoteControlService.performBack()
            "HOME" -> RemoteControlService.performHome()
            "MENU",
            "RECENTS" -> RemoteControlService.performRecents()

            else -> {
                Log.w(TAG, "未知控制命令: $command")
                false
            }
        }
    }
}
