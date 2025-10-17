package com.example.tfgy999

import android.opengl.GLES20
import android.os.Handler
import android.os.HandlerThread
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentLinkedQueue

class TextureLoader(private val mainHandler: Handler) {
    private val queue = ConcurrentLinkedQueue<Runnable>()
    private val workerThread = HandlerThread("TextureLoader").apply { start() }
    private val workerHandler = Handler(workerThread.looper)

    // 异步加载纹理
    fun loadAsync(buffer: ByteBuffer, width: Int, height: Int) {
        queue.add {
            val texId = createTexture(buffer, width, height)
            mainHandler.post { updateFrameBuffer(texId) }
        }
        workerHandler.post { processQueue() }
    }

    private fun processQueue() {
        while (queue.isNotEmpty()) {
            queue.poll()?.run()
        }
    }

    private fun createTexture(buffer: ByteBuffer, width: Int, height: Int): Int {
        val textureIds = IntArray(1)
        GLES20.glGenTextures(1, textureIds, 0)
        val id = textureIds[0]
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, id)
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, width, height, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buffer)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        return id
    }

    private fun updateFrameBuffer(texId: Int) {
        // 主线程更新帧缓冲逻辑，可根据需求扩展
    }

    fun stop() {
        workerThread.quitSafely()
    }
}
