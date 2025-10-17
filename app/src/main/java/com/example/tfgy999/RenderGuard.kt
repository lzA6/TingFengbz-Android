package com.example.tfgy999

import android.content.Context
import android.content.Intent
import android.util.Log

object RenderGuard {
    private const val MAX_FRAME_SKIP = 15 // 提高阈值至15，增加容忍度
    private var consecutiveFails = 0
    private var lastFailTime = 0L

    @Synchronized
    fun checkRenderHealth(context: Context) {
        val currentTime = System.currentTimeMillis()
        consecutiveFails++

        if (consecutiveFails > MAX_FRAME_SKIP) {
            Log.e("RenderGuard", "严重渲染故障，连续失败次数: $consecutiveFails，触发服务重启")
            triggerServiceRestart(context)
            consecutiveFails = 0
            lastFailTime = 0L
        } else {
            val timeSinceLastFail = if (lastFailTime == 0L) 0L else currentTime - lastFailTime
            Log.w("RenderGuard", "渲染失败，当前连续失败次数: $consecutiveFails，距上次失败间隔: ${timeSinceLastFail}ms")
            lastFailTime = currentTime
        }
    }

    @Synchronized
    fun resetCounter() {
        consecutiveFails = 0
        lastFailTime = 0L
        Log.i("RenderGuard", "渲染失败计数器已重置")
    }

    private fun triggerServiceRestart(context: Context) {
        try {
            val intent = Intent("com.example.tfgy999.RESTART_SERVICE")
            context.sendBroadcast(intent)
            Log.i("RenderGuard", "已发送服务重启广播")
        } catch (e: Exception) {
            Log.e("RenderGuard", "触发服务重启失败: ${e.message}", e)
        }
    }
}
