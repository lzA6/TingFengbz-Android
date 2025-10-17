package com.example.tfgy999

import android.annotation.SuppressLint
import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import timber.log.Timber

class FloatingWindowService : Service() {
    private lateinit var windowManager: WindowManager
    private lateinit var floatingView: View
    private var layoutParams: WindowManager.LayoutParams? = null
    private var initialX: Int = 0
    private var initialY: Int = 0
    private var initialTouchX: Float = 0f
    private var initialTouchY: Float = 0f
    private val mainHandler = Handler(Looper.getMainLooper())

    companion object {
        const val NOTIFICATION_ID = 2
        const val NOTIFICATION_CHANNEL_ID = "FloatingWindowChannel"
        const val FLOATING_WINDOW_UPDATE_ACTION = "com.example.tfgy999.FLOATING_WINDOW_UPDATE"
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == FLOATING_WINDOW_UPDATE_ACTION) {
                val fps = intent.getFloatExtra("fps", 0f)
                val interpolatedFrames = intent.getIntExtra("interpolatedFrames", 0)
                val status = intent.getStringExtra("status") ?: "未知"

                mainHandler.post {
                    try {
                        if (::floatingView.isInitialized) {
                            floatingView.findViewById<TextView>(R.id.fps_text)?.text = "FPS: $fps"
                            floatingView.findViewById<TextView>(R.id.interpolated_frames_text)?.text = "补帧: $interpolatedFrames"
                            floatingView.findViewById<TextView>(R.id.status_text)?.text = "状态: $status"
                        }
                    } catch (e: Exception) {
                        Timber.w(e, "更新悬浮窗UI失败")
                    }
                }
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Timber.i("FloatingWindowService onCreate")
        createNotificationChannel()
        val notification = createNotification("悬浮窗服务运行中...")

        startForegroundServiceCompat(notification)

        try {
            windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
            floatingView = LayoutInflater.from(this).inflate(R.layout.floating_window_layout, null)

            val overlayFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                WindowManager.LayoutParams.TYPE_PHONE
            }

            layoutParams = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                overlayFlag,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = 0
                y = 100
            }

            floatingView.setOnTouchListener(FloatingTouchListener())

            mainHandler.post {
                try {
                    if (::floatingView.isInitialized && floatingView.windowToken == null) {
                        windowManager.addView(floatingView, layoutParams)
                        Timber.i("悬浮窗已添加到屏幕")
                    } else {
                        Timber.w("悬浮窗View已存在或未初始化")
                    }
                } catch (e: Exception) {
                    Timber.e(e, "添加悬浮窗View失败")
                    stopSelf()
                }
            }

            LocalBroadcastManager.getInstance(this).registerReceiver(receiver, IntentFilter(FLOATING_WINDOW_UPDATE_ACTION))
            Timber.i("本地广播接收器已注册")
        } catch (e: Exception) {
            Timber.e(e, "FloatingWindowService初始化失败")
            stopForeground(true)
            stopSelf()
        }
    }

    private fun startForegroundServiceCompat(notification: Notification) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceCompat.startForeground(
                    this,
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceCompat.startForeground(
                    this,
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: SecurityException) {
            Timber.e(e, "启动前台服务失败，可能缺少权限")
            stopSelf()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "悬浮窗服务通道",
                NotificationManager.IMPORTANCE_LOW
            ).apply { setSound(null, null) }
            getSystemService(NotificationManager::class.java).createNotificationChannel(serviceChannel)
        }
    }

    private fun createNotification(contentText: String): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("听风插帧")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(pendingIntent)
            .setOnlyAlertOnce(true)
            .build()
    }

    override fun onDestroy() {
        Timber.i("FloatingWindowService onDestroy")
        try {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(receiver)
        } catch (e: IllegalArgumentException) {
            Timber.w("本地广播接收器注销失败，可能未注册")
        }
        mainHandler.post {
            try {
                if (::windowManager.isInitialized && ::floatingView.isInitialized && floatingView.isAttachedToWindow) {
                    windowManager.removeView(floatingView)
                    Timber.i("悬浮窗已移除")
                }
            } catch (e: Exception) {
                Timber.e(e, "移除悬浮窗失败")
            }
        }
        stopForeground(true)
        super.onDestroy()
    }

    private inner class FloatingTouchListener : View.OnTouchListener {
        private var isDragging = false
        private var lastAction: Int = 0

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouch(v: View, event: MotionEvent): Boolean {
            val currentX = event.rawX
            val currentY = event.rawY

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = layoutParams!!.x
                    initialY = layoutParams!!.y
                    initialTouchX = currentX
                    initialTouchY = currentY
                    lastAction = MotionEvent.ACTION_DOWN
                    isDragging = false
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = currentX - initialTouchX
                    val dy = currentY - initialTouchY
                    if (!isDragging && (Math.abs(dx) > 10 || Math.abs(dy) > 10)) {
                        isDragging = true
                    }
                    if (isDragging) {
                        layoutParams!!.x = initialX + dx.toInt()
                        layoutParams!!.y = initialY + dy.toInt()
                        try {
                            if (::windowManager.isInitialized && v.isAttachedToWindow) {
                                windowManager.updateViewLayout(v, layoutParams)
                            }
                        } catch (e: Exception) {
                            Timber.e(e, "更新悬浮窗位置失败")
                        }
                    }
                    lastAction = MotionEvent.ACTION_MOVE
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    lastAction = MotionEvent.ACTION_UP
                    isDragging = false
                    return true
                }
            }
            return false
        }
    }
}