package com.example.tfgy999

import android.content.Context
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.io.FileInputStream

object FileUtil {
    fun loadMappedFile(context: Context, fileName: String): MappedByteBuffer {
        context.assets.openFd(fileName).use { fd ->
            FileInputStream(fd.fileDescriptor).use { fis ->
                fis.channel.use { channel ->
                    return channel.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
                }
            }
        }
    }
}