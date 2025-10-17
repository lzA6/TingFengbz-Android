package com.example.tfgy999

import android.content.Context
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import java.nio.ByteBuffer
import java.nio.ByteOrder

class RecoilPredictor(context: Context) {
    private val interpreter: Interpreter

    init {
        val model = FileUtil.loadMappedFile(context, "recoil_lstm.tflite")
        interpreter = Interpreter(model)
    }

    fun predictNextOffset(currentPattern: FloatArray): FloatArray {
        val input = ByteBuffer.allocateDirect(4 * currentPattern.size).order(ByteOrder.nativeOrder()).apply {
            currentPattern.forEach { putFloat(it) }
            rewind()
        }
        val output = Array(1) { FloatArray(2) }
        interpreter.run(input, output)
        return output[0]
    }
}