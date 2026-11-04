package com.rezerv.upload.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        TaskEntity::class,
        FileEntity::class  // ✅ ДОБАВЛЕНО
    ],
    version = 2,           // ✅ УВЕЛИЧЕНО с 1 до 2
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun fileDao(): FileDao  // ✅ ДОБАВЛЕНО
}