package com.example.tfgy999

import android.graphics.Bitmap
import android.graphics.Color

fun List<Bitmap>.averageBitmap(): Bitmap? {
    if (isEmpty()) return null
    val width = this[0].width
    val height = this[0].height
    val averagedBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val pixelCount = width * height
    val pixelBuffer = IntArray(pixelCount)

    for (i in 0 until pixelCount) {
        var totalRed = 0
        var totalGreen = 0
        var totalBlue = 0
        for (bitmap in this) {
            val pixel = bitmap.getPixel(i % width, i / width)
            totalRed += Color.red(pixel)
            totalGreen += Color.green(pixel)
            totalBlue += Color.blue(pixel)
        }
        val avgRed = totalRed / size
        val avgGreen = totalGreen / size
        val avgBlue = totalBlue / size
        pixelBuffer[i] = Color.rgb(avgRed, avgGreen, avgBlue)
    }
    averagedBitmap.setPixels(pixelBuffer, 0, width, 0, 0, width, height)
    return averagedBitmap
}
