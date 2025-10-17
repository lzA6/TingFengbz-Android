package com.example.tfgy999

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [FrameRecord::class, FrameData::class], version = 2)
abstract class AppDatabase : RoomDatabase() {
    abstract fun frameRecordDao(): FrameRecordDao
    abstract fun frameDataDao(): FrameDataDao

    companion object {
        // 迁移脚本从版本1到版本2
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // 删除旧表（如果存在）
                database.execSQL("DROP TABLE IF EXISTS frame_data")

                // 创建新表，确保与实体类一致
                database.execSQL("""
                    CREATE TABLE frame_data (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        recordId INTEGER NOT NULL,
                        timestamp INTEGER NOT NULL,
                        originalFps REAL NOT NULL,
                        interpolatedFps REAL NOT NULL,
                        FOREIGN KEY(recordId) REFERENCES frame_records(id) ON DELETE CASCADE
                    )
                """.trimIndent())
            }
        }
    }
}
