package com.example.tfgy999

import android.annotation.SuppressLint
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Point
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.*
import android.util.DisplayMetrics
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

data class RecoilProfile(
    val horizontal: Float,
    val vertical: Float,
    val burstQ: Float
)

class AutoRecoilService : Service() {
    private val TAG = "AutoRecoilService"
    private val isRunning = AtomicBoolean(false)
    private lateinit var mediaProjectionManager: MediaProjectionManager
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var frameWidth = 0
    private var frameHeight = 0
    private var wakeLock: PowerManager.WakeLock? = null
    private lateinit var mainHandler: Handler
    private lateinit var captureThread: HandlerThread
    private lateinit var captureHandler: Handler

    private var isAutoRecoilEnabled = false
    private var initialFrame: Bitmap? = null
    private var stabilizationOffsetX = 0f
    private var stabilizationOffsetY = 0f
    private var velocityX = 0f
    private var velocityY = 0f
    private var anchorPoint: Point? = null // 默认准心位置
    private var isFiring = false
    private var lastMoveTime = 0L
    private var horizontalOffset = 0f
    private var verticalOffset = 0f
    private var userHorizontalInput = 0f
    private var userVerticalInput = 0f
    private var bulletCount = 0
    private val weaponRecoilMap = mapOf(
        "AKM" to RecoilProfile(0f, 50f, 1.2f),
        "M416" to RecoilProfile(10f, 40f, 1.1f),
        "SCAR-L" to RecoilProfile(8f, 30f, 1.0f),
        "M762" to RecoilProfile(12f, 26f, 1.3f),
        "Groza" to RecoilProfile(5f, 60f, 1.4f),
        "UMP45" to RecoilProfile(5f, 16f, 0.9f),
        "Vector" to RecoilProfile(3f, 14f, 0.8f),
        "Thompson" to RecoilProfile(6f, 17f, 1.0f)
    )
    private var currentWeapon = "M416"
    private val recoilReductionFactor = 0.8f
    private val stabilizationWindow = FloatArray(20) { 0f }
    private var stabilizationIndex = 0
    private val decayFactor = 0.9f
    private var lastFrameDiff = 0f // 用于检测准心抖动
    private var firingDetectionCount = 0 // 开火检测计数器

    private companion object {
        const val NOTIFICATION_ID = 2
        const val NOTIFICATION_CHANNEL_ID = "AutoRecoilChannel"
        const val RECOIL_SMOOTH_FACTOR = 0.05f
        const val CROSSHAIR_AREA_SIZE = 50 // 准心检测区域大小（像素）
        const val FIRING_THRESHOLD = 15f // 抖动阈值
        const val FIRING_CONFIRM_COUNT = 3 // 连续抖动帧数确认开火
    }

    override fun onBind(intent: Intent?): IBinder? = null

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate() {
        super.onCreate()
        mainHandler = Handler(mainLooper)
        mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        captureThread = HandlerThread("RecoilCaptureThread", Process.THREAD_PRIORITY_DEFAULT).apply { start() }
        captureHandler = Handler(captureThread.looper)
        Log.i(TAG, "服务已成功创建")
    }

    @SuppressLint("ObsoleteSdkInt")
    @RequiresApi(Build.VERSION_CODES.Q)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let {
            when (it.action) {
                "SET_WEAPON" -> {
                    setWeapon(it.getStringExtra("weapon") ?: "M416")
                    return START_NOT_STICKY
                }
                "UPDATE_FIRING_STATE" -> {
                    val firing = it.getBooleanExtra("isFiring", false)
                    val anchorX = it.getIntExtra("anchorX", 0)
                    val anchorY = it.getIntExtra("anchorY", 0)
                    val userX = it.getFloatExtra("userX", 0f)
                    val userY = it.getFloatExtra("userY", 0f)
                    setFiringState(firing, if (anchorX != 0 || anchorY != 0) Point(anchorX, anchorY) else null, userX, userY)
                    return START_NOT_STICKY
                }
                "UPDATE_RECOIL_STATE" -> {
                    isAutoRecoilEnabled = it.getBooleanExtra("enableAutoRecoil", false)
                    Log.i(TAG, "自动压枪状态已更新为: $isAutoRecoilEnabled")
                    sendRecoilStateUpdate()
                    updateNotification()
                    if (isAutoRecoilEnabled && imageReader == null) setupImageReader()
                    return START_NOT_STICKY
                }
            }
        }

        try {
            createNotificationChannel()
            val notificationIntent = Intent(this, MainActivity::class.java)
            val pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE)
            val notificationBuilder = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
                .setContentTitle("自动压枪服务运行中")
                .setContentText(if (isAutoRecoilEnabled) "自动压枪已启用" else "自动压枪未启用")
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentIntent(pendingIntent)
                .setOngoing(true)

            val notification = notificationBuilder.build()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }

            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AutoRecoil:WakeLock").apply {
                acquire()
            }

            isRunning.set(true)
            isAutoRecoilEnabled = intent?.getBooleanExtra("enableAutoRecoil", false) ?: false
            val resultCode = intent?.getIntExtra("resultCode", Activity.RESULT_CANCELED) ?: Activity.RESULT_CANCELED
            val data = intent?.getParcelableExtra<Intent>("data")

            if (resultCode != Activity.RESULT_OK || data == null) {
                Log.e(TAG, "服务启动失败：无效的 resultCode 或 data")
                stopSelfSafely()
                return START_NOT_STICKY
            }

            mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, data)
            if (mediaProjection == null) {
                Log.e(TAG, "服务启动失败：无法获取 MediaProjection")
                stopSelfSafely()
                return START_NOT_STICKY
            }
            mediaProjection?.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() {
                    stopSelfSafely()
                }
            }, null)

            val displayMetrics = resources.displayMetrics
            frameWidth = displayMetrics.widthPixels
            frameHeight = displayMetrics.heightPixels
            anchorPoint = Point(frameWidth / 2, frameHeight / 2) // 默认准心为中心

            setupImageReader()
            virtualDisplay = createVirtualDisplay()
            if (virtualDisplay == null) {
                Log.e(TAG, "VirtualDisplay 创建失败")
                stopSelfSafely()
                return START_NOT_STICKY
            }

            Log.i(TAG, "服务已成功启动，自动压枪: $isAutoRecoilEnabled，准心位置: $anchorPoint")
            sendRecoilStateUpdate()
        } catch (e: Exception) {
            Log.e(TAG, "服务启动失败: ${e.message}", e)
            stopSelfSafely()
        }
        return START_STICKY
    }

    private fun sendRecoilStateUpdate() {
        val intent = Intent("com.example.tfgy999.RECOIL_STATE_UPDATE").apply {
            putExtra("enableAutoRecoil", isAutoRecoilEnabled)
        }
        sendBroadcast(intent)
    }

    private fun updateNotification() {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE)
        val notificationBuilder = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("自动压枪服务运行中")
            .setContentText(if (isAutoRecoilEnabled) "自动压枪已启用" else "自动压枪未启用")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(pendingIntent)
            .setOngoing(true)

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notificationBuilder.build())
    }

    private fun stopSelfSafely() {
        isRunning.set(false)
        mainHandler.post { stopSelf() }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun setupImageReader() {
        if (!isRunning.get() || !isAutoRecoilEnabled) return
        try {
            imageReader?.close()
            imageReader = ImageReader.newInstance(
                frameWidth, frameHeight, PixelFormat.RGBA_8888, 2
            ).apply {
                setOnImageAvailableListener({ reader ->
                    if (!isRunning.get() || !isAutoRecoilEnabled) return@setOnImageAvailableListener
                    captureHandler.post {
                        try {
                            val image = reader.acquireLatestImage() ?: return@post
                            val buffer = image.planes[0].buffer
                            val bitmap = Bitmap.createBitmap(frameWidth, frameHeight, Bitmap.Config.ARGB_8888)
                            buffer.rewind()
                            bitmap.copyPixelsFromBuffer(buffer)
                            processFrameForRecoil(bitmap)
                            bitmap.recycle()
                            image.close()
                        } catch (e: Exception) {
                            Log.e(TAG, "帧处理发生错误: ${e.message}", e)
                        }
                    }
                }, captureHandler)
            }
            Log.i(TAG, "ImageReader 已成功设置: width=$frameWidth, height=$frameHeight")
        } catch (e: Exception) {
            Log.e(TAG, "ImageReader 设置失败: ${e.message}", e)
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createVirtualDisplay(): VirtualDisplay? {
        return mainHandler.postAndWait(1, TimeUnit.SECONDS) {
            if (mediaProjection == null || imageReader == null) {
                Log.e(TAG, "MediaProjection 或 ImageReader 未初始化")
                return@postAndWait null
            }
            try {
                val vd = mediaProjection?.createVirtualDisplay(
                    "AutoRecoil",
                    frameWidth, frameHeight,
                    resources.displayMetrics.densityDpi,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC or DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    imageReader!!.surface, null, mainHandler
                )
                Log.i(TAG, "VirtualDisplay 已成功创建，分辨率: ${frameWidth}x${frameHeight}")
                vd
            } catch (e: SecurityException) {
                Log.e(TAG, "VirtualDisplay 创建失败，权限不足: ${e.message}", e)
                null
            } catch (e: Exception) {
                Log.e(TAG, "VirtualDisplay 创建失败: ${e.message}", e)
                null
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun processFrameForRecoil(bitmap: Bitmap) {
        val displayMetrics = resources.displayMetrics
        val realSize = Point(displayMetrics.widthPixels, displayMetrics.heightPixels)
        val centerX = realSize.x / 2f
        val centerY = realSize.y / 2f

        // 检测准心抖动以判断开火状态
        val frameDiff = detectCrosshairShake(bitmap)
        if (frameDiff > FIRING_THRESHOLD) {
            firingDetectionCount++
            if (firingDetectionCount >= FIRING_CONFIRM_COUNT && !isFiring) {
                setFiringState(true, Point(centerX.toInt(), centerY.toInt()))
                Log.i(TAG, "检测到准心高频抖动，确认开火，抖动值: $frameDiff")
            }
        } else {
            firingDetectionCount = 0
            if (isFiring) {
                setFiringState(false, null)
                Log.i(TAG, "准心抖动停止，结束开火，抖动值: $frameDiff")
            }
        }

        if (isFiring && anchorPoint != null) {
            val currentTime = System.currentTimeMillis()
            val timeDelta = if (lastMoveTime == 0L) 16L else (currentTime - lastMoveTime).coerceAtMost(100L)
            lastMoveTime = currentTime

            val anchorX = anchorPoint!!.x.toFloat()
            val anchorY = anchorPoint!!.y.toFloat()
            val currentX = centerX + horizontalOffset
            val currentY = centerY + verticalOffset

            val deltaX = currentX - anchorX
            val deltaY = currentY - anchorY

            val profile = weaponRecoilMap[currentWeapon] ?: RecoilProfile(0f, 5f, 1.0f)
            val maxBullets = 5
            val recoilY = if (bulletCount < maxBullets) {
                profile.vertical * (1f - (bulletCount.toFloat() / maxBullets) * 0.1f)
            } else {
                profile.vertical * 0.05f
            } * recoilReductionFactor
            bulletCount++

            val recoilCorrectionX = profile.horizontal * timeDelta / 1000f * recoilReductionFactor
            val recoilCorrectionY = recoilY * timeDelta / 1000f

            val autoCorrectionX = -deltaX * RECOIL_SMOOTH_FACTOR - recoilCorrectionX
            val autoCorrectionY = -deltaY * RECOIL_SMOOTH_FACTOR - recoilCorrectionY

            val userCorrectionX = userHorizontalInput * timeDelta / 1000f
            val userCorrectionY = userVerticalInput * timeDelta / 1000f

            if (initialFrame != null) {
                val diffX = calculateFrameDifference(bitmap, initialFrame!!, "horizontal")
                val diffY = calculateFrameDifference(bitmap, initialFrame!!, "vertical")
                velocityX = applyMovingAverage(diffX * 10.0f, true) * decayFactor
                velocityY = applyMovingAverage(diffY * 10.0f, false) * decayFactor
                stabilizationOffsetX = (stabilizationOffsetX + velocityX) * decayFactor
                stabilizationOffsetY = (stabilizationOffsetY + velocityY) * decayFactor
            }

            horizontalOffset += (autoCorrectionX + stabilizationOffsetX + userCorrectionX) * RECOIL_SMOOTH_FACTOR
            verticalOffset += (autoCorrectionY + stabilizationOffsetY + userCorrectionY) * RECOIL_SMOOTH_FACTOR

            horizontalOffset = horizontalOffset.coerceIn(-realSize.x / 2f, realSize.x / 2f)
            verticalOffset = verticalOffset.coerceIn(-realSize.y / 2f, realSize.y / 2f)

            val originalX = anchorX + userHorizontalInput
            val originalY = anchorY + userVerticalInput
            val adjustedX = anchorX + horizontalOffset
            val adjustedY = anchorY + verticalOffset

            Log.i(
                TAG, """
                自动压枪处理详情:
                - 当前武器: $currentWeapon
                - 用户输入: (X=%.2f, Y=%.2f)
                - 锚点坐标: (X=%.2f, Y=%.2f)
                - 自动修正: (X=%.2f, Y=%.2f)
                - 防抖偏移: (X=%.2f, Y=%.2f)
                - 用户修正: (X=%.2f, Y=%.2f)
                - 最终偏移: (X=%.2f, Y=%.2f)
                - 坐标调整: 从 (X=%.2f, Y=%.2f) 到 (X=%.2f, Y=%.2f)
                - 压枪状态: 生效
                """.trimIndent().format(
                    userHorizontalInput, userVerticalInput,
                    anchorX, anchorY,
                    autoCorrectionX, autoCorrectionY,
                    stabilizationOffsetX, stabilizationOffsetY,
                    userCorrectionX, userCorrectionY,
                    horizontalOffset, verticalOffset,
                    originalX, originalY,
                    adjustedX, adjustedY
                )
            )

            injectMove(horizontalOffset, verticalOffset)
        } else {
            horizontalOffset = 0f
            verticalOffset = 0f
            bulletCount = 0
            Log.i(
                TAG, """
                自动压枪处理详情:
                - 当前武器: $currentWeapon
                - 用户输入: (X=%.2f, Y=%.2f)
                - 锚点坐标: $anchorPoint
                - 压枪状态: 未生效 (isFiring=$isFiring)
                """.trimIndent().format(
                    userHorizontalInput, userVerticalInput
                )
            )
        }
    }

    private fun detectCrosshairShake(bitmap: Bitmap): Float {
        if (initialFrame == null) {
            initialFrame = Bitmap.createBitmap(bitmap)
            return 0f
        }

        val centerX = frameWidth / 2
        val centerY = frameHeight / 2
        val halfSize = CROSSHAIR_AREA_SIZE / 2
        var totalDiff = 0f
        val samplePoints = 10

        for (i in 0 until samplePoints) {
            for (j in 0 until samplePoints) {
                val x = centerX - halfSize + (i * CROSSHAIR_AREA_SIZE / samplePoints)
                val y = centerY - halfSize + (j * CROSSHAIR_AREA_SIZE / samplePoints)
                if (x >= 0 && x < frameWidth && y >= 0 && y < frameHeight) {
                    val currentPixel = bitmap.getPixel(x, y)
                    val initialPixel = initialFrame!!.getPixel(x, y)
                    totalDiff += kotlin.math.abs(
                        (Color.red(currentPixel) + Color.green(currentPixel) + Color.blue(currentPixel)) / 3f -
                                (Color.red(initialPixel) + Color.green(initialPixel) + Color.blue(initialPixel)) / 3f
                    )
                }
            }
        }

        val avgDiff = totalDiff / (samplePoints * samplePoints)
        val diffChange = kotlin.math.abs(avgDiff - lastFrameDiff)
        lastFrameDiff = avgDiff
        initialFrame = Bitmap.createBitmap(bitmap) // 更新初始帧以检测连续变化
        return diffChange
    }

    private fun applyMovingAverage(diff: Float, isHorizontal: Boolean): Float {
        stabilizationWindow[stabilizationIndex] = diff
        stabilizationIndex = (stabilizationIndex + 1) % stabilizationWindow.size
        val average = stabilizationWindow.average().toFloat()
        return if (isHorizontal) average * 0.8f else average
    }

    private fun calculateFrameDifference(currentFrame: Bitmap, initialFrame: Bitmap, direction: String): Float {
        val width = currentFrame.width
        val height = currentFrame.height
        var totalDiff = 0f
        val samplePoints = 100
        for (i in 0 until samplePoints) {
            val x = width / samplePoints * i
            val y = height / samplePoints * i
            val currentPixel = currentFrame.getPixel(x, if (direction == "horizontal") height / 2 else y)
            val initialPixel = initialFrame.getPixel(x, if (direction == "horizontal") height / 2 else y)
            totalDiff += ((Color.red(currentPixel) + Color.green(currentPixel) + Color.blue(currentPixel)) / 3f -
                    (Color.red(initialPixel) + Color.green(initialPixel) + Color.blue(initialPixel)) / 3f)
        }
        return totalDiff / samplePoints
    }

    private fun injectMove(deltaX: Float, deltaY: Float) {
        val accessibilityService = TouchAccessibilityService.instance
        if (accessibilityService != null) {
            val displayMetrics = resources.displayMetrics
            val screenWidth = displayMetrics.widthPixels.toFloat()
            val screenHeight = displayMetrics.heightPixels.toFloat()

            val anchorX = anchorPoint?.x?.toFloat() ?: (screenWidth / 2f)
            val anchorY = anchorPoint?.y?.toFloat() ?: (screenHeight / 2f)

            var x = anchorX + deltaX
            var y = anchorY + deltaY

            x = x.coerceIn(0f, screenWidth)
            y = y.coerceIn(0f, screenHeight)

            accessibilityService.simulateTouch(x, y, 16)
            Log.i(TAG, "触控注入完成: 从 (X=%.2f, Y=%.2f) 调整到 (X=%.2f, Y=%.2f)".format(anchorX, anchorY, x, y))
        } else {
            Log.e(TAG, "无障碍服务未初始化，无法执行触控操作")
        }
    }

    fun setFiringState(firing: Boolean, point: Point?, userX: Float = 0f, userY: Float = 0f) {
        isFiring = firing
        anchorPoint = point ?: Point(frameWidth / 2, frameHeight / 2) // 默认准心为中心
        userHorizontalInput = userX
        userVerticalInput = userY
        if (!firing) {
            horizontalOffset = 0f
            verticalOffset = 0f
            stabilizationOffsetX = 0f
            stabilizationOffsetY = 0f
            velocityX = 0f
            velocityY = 0f
            bulletCount = 0
            lastMoveTime = 0L
            stabilizationWindow.fill(0f)
            stabilizationIndex = 0
            initialFrame?.recycle()
            initialFrame = null
            firingDetectionCount = 0
        } else if (initialFrame == null) {
            imageReader?.acquireLatestImage()?.let { image ->
                initialFrame?.recycle()
                initialFrame = Bitmap.createBitmap(image.width, image.height, Bitmap.Config.ARGB_8888)
                initialFrame?.copyPixelsFromBuffer(image.planes[0].buffer)
                image.close()
            }
        }
        Log.i(
            TAG, """
            开火状态更新:
            - 开火: $isFiring
            - 锚点: $anchorPoint
            - 用户输入坐标: (X=%.2f, Y=%.2f)
            """.trimIndent().format(userX, userY)
        )
    }

    fun setWeapon(weapon: String) {
        currentWeapon = weapon.takeIf { weaponRecoilMap.containsKey(it) } ?: "M416"
        Log.i(TAG, "当前枪械已设置为: $currentWeapon")
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning.set(false)
        virtualDisplay?.release()
        imageReader?.close()
        mediaProjection?.stop()
        wakeLock?.release()
        captureThread.quitSafely()
        initialFrame?.recycle()
        Log.i(TAG, "服务已销毁")
    }

    private inline fun <reified T> Handler.postAndWait(timeout: Long, unit: TimeUnit, crossinline block: () -> T): T? {
        if (Looper.myLooper() == looper) return block()
        val latch = java.util.concurrent.CountDownLatch(1)
        var result: T? = null
        var exception: Exception? = null
        post {
            try {
                result = block()
            } catch (e: Exception) {
                exception = e
            } finally {
                latch.countDown()
            }
        }
        latch.await(timeout, unit)
        exception?.let { throw it }
        return result
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "自动压枪服务",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { setSound(null, null) }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }
}