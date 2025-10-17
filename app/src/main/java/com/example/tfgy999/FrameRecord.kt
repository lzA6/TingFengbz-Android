// FrameRecord.kt
package com.example.tfgy999

import androidx.room.Entity
import androidx.room.PrimaryKey

// 插帧记录实体类 → Frame record entity
@Entity(tableName = "frame_records")
data class FrameRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startTime: Long,           // 开始时间 → Start time
    val endTime: Long,             // 结束时间 → End time
    val originalFps: Int,          // 原帧率 → Original FPS
    val interpolatedFps: Int,      // 补帧后帧率 → Interpolated FPS
    val durationMinutes: Int,      // 时长（分钟）→ Duration in minutes
    val interpolatedFrames: Int    // 累计补帧数 → Total interpolated frames
)
