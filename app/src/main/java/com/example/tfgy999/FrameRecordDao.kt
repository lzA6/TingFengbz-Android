package com.example.tfgy999

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface FrameRecordDao {
    @Insert
    suspend fun insertAndGetId(record: FrameRecord): Long

    @Query("UPDATE frame_records SET endTime = :endTime, durationMinutes = :durationMinutes, interpolatedFrames = :interpolatedFrames WHERE id = :recordId")
    suspend fun updateRecordEndTime(recordId: Int, endTime: Long, durationMinutes: Int, interpolatedFrames: Int)

    @Query("SELECT * FROM frame_records ORDER BY startTime DESC")
    suspend fun getAllRecords(): List<FrameRecord>
}
