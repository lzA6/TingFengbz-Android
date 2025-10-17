package com.example.tfgy999

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

// 定义frame_data表实体，确保与迁移脚本一致
@Entity(
    tableName = "frame_data",
    foreignKeys = [ForeignKey(
        entity = FrameRecord::class,           // 外键引用的实体类 → Referenced entity class
        parentColumns = ["id"],                // 父表列 → Parent table column
        childColumns = ["recordId"],           // 子表列 → Child table column
        onDelete = ForeignKey.CASCADE          // 删除时级联操作 → Cascade on delete
    )]
)
data class FrameData(
    @PrimaryKey(autoGenerate = true) val id: Long,    // 主键，自增 → Primary key, auto-increment
    val recordId: Long,                               // 记录ID，外键 → Record ID, foreign key
    val timestamp: Long,                              // 时间戳 → Timestamp
    val originalFps: Float,                           // 原始帧率 → Original FPS
    val interpolatedFps: Float                        // 插值后的帧率 → Interpolated FPS
)
