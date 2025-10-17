package com.example.tfgy999

import android.graphics.Bitmap
import java.nio.ByteBuffer

object BitmapUtils {
    fun bufferToBitmap(buffer: ByteBuffer, width: Int, height: Int): Bitmap? {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        buffer.rewind()
        bitmap.copyPixelsFromBuffer(buffer)
        return bitmap
    }

    fun bitmapToBuffer(bitmap: Bitmap, buffer: ByteBuffer) {
        buffer.rewind()
        bitmap.copyPixelsToBuffer(buffer)
        buffer.rewind()
    }
}
