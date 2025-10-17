package com.example.tfgy999

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.VideoView
import androidx.activity.ComponentActivity
import android.view.Choreographer

class TestActivity : ComponentActivity() {
    private val TAG = "TestActivity"
    private val handler = Handler(Looper.getMainLooper())
    private var totalFrames = 0
    private var addedFrames = 0
    private var droppedFrames = 0
    private var originalFps = 0f
    private var lastFps = 0f
    private var videoDurationMs = 0L
    private var startTime = 0L
    private var isFrameBoostRunning = false
    private var interpolationMethod = "未开启补帧"
    private var fpsList = mutableListOf<Float>()
    private lateinit var fpsReceiver: BroadcastReceiver
    private var isReceiverRegistered = false
    private var frameTimestamps = mutableListOf<Long>() // 用于 Choreographer 计算 FPS
    private var lastFrameTime = 0L
    private var frameCallback: Choreographer.FrameCallback? = null // 保存 FrameCallback 用于移除

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val videoView = VideoView(this).apply {
            layoutParams = android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        setContentView(videoView)

        isFrameBoostRunning = isServiceRunning()
        interpolationMethod = intent.getStringExtra("interpolationMethod") ?: "未开启补帧"

        setupFpsReceiver()
        setupFrameCallback()

        try {
            val videoPath = "android.resource://${packageName}/raw/test_video"
            Log.i(TAG, "正在加载视频路径: $videoPath")
            videoView.setVideoPath(videoPath)

            videoView.setOnPreparedListener { mp ->
                startTime = System.currentTimeMillis()
                mp.isLooping = false
                mp.start()

                originalFps = mp.videoFrameRate.takeIf { it > 0 } ?: 30f
                videoDurationMs = mp.duration.toLong()
                totalFrames = (originalFps * videoDurationMs / 1000).toInt()

                handler.postDelayed({ finishWithSummary() }, 30_000)
            }

            videoView.setOnErrorListener { _, what, extra ->
                Log.e(TAG, "视频播放错误: what=$what, extra=$extra")
                finishWithSummary()
                true
            }

            videoView.setOnCompletionListener {
                Log.i(TAG, "视频播放完成")
                finishWithSummary()
            }
        } catch (e: Exception) {
            Log.e(TAG, "视频加载失败: ${e.message}", e)
            finishWithSummary()
        }
    }

    private fun isServiceRunning(): Boolean {
        val manager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        return manager.getRunningServices(Integer.MAX_VALUE)
            .any { it.service.className == AutoFrameBoostService::class.java.name }
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private fun setupFpsReceiver() {
        fpsReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val currentFps = intent?.getFloatExtra("fps", 0f) ?: 0f
                fpsList.add(currentFps)
                if (currentFps != lastFps) {
                    if (isFrameBoostRunning) {
                        val added = (currentFps - originalFps).coerceAtLeast(0f).toInt()
                        val dropped = (originalFps - currentFps).coerceAtLeast(0f).toInt()
                        addedFrames += added
                        droppedFrames += dropped
                    } else {
                        val dropped = (originalFps - currentFps).coerceAtLeast(0f).toInt()
                        droppedFrames += dropped
                    }
                    lastFps = currentFps
                }
            }
        }
        val filter = IntentFilter("com.example.tfgy999.FPS_UPDATE")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(fpsReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(fpsReceiver, filter)
        }
        isReceiverRegistered = true
        Log.i(TAG, "FPS 广播接收器已注册")
    }

    private fun setupFrameCallback() {
        frameCallback = object : Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
                if (isFrameBoostRunning) {
                    frameTimestamps.add(frameTimeNanos)
                    calculateFpsFromTimestamps()
                }
                Choreographer.getInstance().postFrameCallback(this)
            }
        }
        frameCallback?.let {
            Choreographer.getInstance().postFrameCallback(it)
            Log.i(TAG, "Choreographer 帧回调已启动")
        }
    }

    private fun calculateFpsFromTimestamps() {
        if (frameTimestamps.size < 2) return
        val elapsedNanos = frameTimestamps.last() - frameTimestamps.first()
        val elapsedSeconds = elapsedNanos / 1_000_000_000f
        if (elapsedSeconds >= 0.5f) { // 每 0.5 秒计算一次
            val fps = (frameTimestamps.size - 1) / elapsedSeconds
            fpsList.add(fps)
            Log.d(TAG, "Choreographer 计算 FPS: $fps")
            frameTimestamps.clear()
            frameTimestamps.add(System.nanoTime()) // 重置起始时间戳
        }
    }

    @SuppressLint("DefaultLocale")
    private fun finishWithSummary() {
        val durationSeconds = (System.currentTimeMillis() - startTime) / 1000f
        val averageFpsFromReceiver = if (fpsList.isNotEmpty()) fpsList.average().toFloat() else originalFps
        val averageFpsFromChoreographer = if (frameTimestamps.size > 1) {
            val elapsedNanos = frameTimestamps.last() - frameTimestamps.first()
            val elapsedSeconds = elapsedNanos / 1_000_000_000f
            (frameTimestamps.size - 1) / elapsedSeconds
        } else {
            averageFpsFromReceiver
        }
        val finalAverageFps = if (isFrameBoostRunning) {
            // 取两种计算方法的平均值以提高准确性
            (averageFpsFromReceiver + averageFpsFromChoreographer) / 2f
        } else {
            averageFpsFromReceiver
        }

        val summary = buildString {
            append("测试完成！\n")
            append("视频时长: ${String.format("%.1f", durationSeconds)} 秒\n")
            append("原视频 FPS: ${String.format("%.1f", originalFps)}\n")
            append("平均 FPS (广播): ${String.format("%.1f", averageFpsFromReceiver)}\n")
            append("平均 FPS (Choreographer): ${String.format("%.1f", averageFpsFromChoreographer)}\n")
            append("最终平均 FPS: ${String.format("%.1f", finalAverageFps)}\n")
            append("补帧模式: $interpolationMethod\n")
            append("总帧数: $totalFrames 帧\n")
            append("补帧数: $addedFrames 帧\n")
            append("丢帧数: $droppedFrames 帧\n")
            append("插帧数: ${if (isFrameBoostRunning) addedFrames else 0} 帧\n")
        }

        runOnUiThread {
            AlertDialog.Builder(this)
                .setTitle("补帧测试总结")
                .setMessage(summary)
                .setPositiveButton("确定") { _, _ ->
                    cleanupAndReturn()
                }
                .setCancelable(false)
                .show()
        }
    }

    private fun cleanupAndReturn() {
        handler.removeCallbacksAndMessages(null)
        if (isReceiverRegistered) {
            try {
                unregisterReceiver(fpsReceiver)
                isReceiverRegistered = false
                Log.i(TAG, "FPS 广播接收器已注销")
            } catch (e: IllegalArgumentException) {
                Log.w(TAG, "尝试注销未注册的接收器: ${e.message}")
            }
        }
        // 移除 Choreographer 帧回调
        frameCallback?.let {
            Choreographer.getInstance().removeFrameCallback(it)
            Log.i(TAG, "Choreographer 帧回调已移除")
        }
        frameCallback = null
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing) cleanupAndReturn()
    }
}

val android.media.MediaPlayer.videoFrameRate: Float
    get() = 30f // 请根据实际视频库调整，当前为默认值
