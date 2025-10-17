package com.example.tfgy999

import android.os.Build
import androidx.annotation.RequiresApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber

class PerformanceMonitor(private val service: AutoFrameBoostService) {
    var currentFps = 0f
        private set
    var droppedFrameCount = 0
        private set
    var minFps = Float.MAX_VALUE
        private set
    var maxFps = 0f
        private set
    private var frameCount = 0
    private var lastResetTime = 0L
    private val scope = CoroutineScope(Dispatchers.Default)
    private var isMonitoring = false

    @RequiresApi(Build.VERSION_CODES.O)
    fun startMonitoring() {
        if (isMonitoring) return
        isMonitoring = true
        scope.launch {
            while (isMonitoring) {
                val currentTime = System.nanoTime()
                updateFps(currentTime)
                delay(500)
            }
        }
    }

    fun stopMonitoring() {
        isMonitoring = false
    }

    private fun updateFps(currentTime: Long) {
        if (lastResetTime == 0L) {
            lastResetTime = currentTime
            return
        }
        val elapsedTime = (currentTime - lastResetTime) / 1_000_000_000f
        if (elapsedTime >= 0.5f) {
            currentFps = if (elapsedTime > 0) frameCount / elapsedTime else 0f
            minFps = minOf(minFps, currentFps)
            maxFps = maxOf(maxFps, currentFps)
            frameCount = 0
            lastResetTime = currentTime
            Timber.i("性能监控: 当前帧率: ${String.format("%.2f", currentFps)} FPS, 丢帧数: $droppedFrameCount")
        }
        frameCount++
    }

    fun reportDroppedFrame() {
        droppedFrameCount++
    }

    fun reset() {
        currentFps = 0f
        droppedFrameCount = 0
        minFps = Float.MAX_VALUE
        maxFps = 0f
        frameCount = 0
        lastResetTime = 0L
    }
}
