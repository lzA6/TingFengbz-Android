package com.example.tfgy999

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface FrameDataDao {
    @Insert
    suspend fun insertAll(frameDataList: List<FrameData>)

    @Query("SELECT * FROM frame_data WHERE recordId = :recordId ORDER BY timestamp ASC")
    suspend fun getFrameDataByRecordId(recordId: Int): List<FrameData>
}
