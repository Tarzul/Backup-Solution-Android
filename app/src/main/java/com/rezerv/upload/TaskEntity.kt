package com.rezerv.upload.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters

@Entity(tableName = "tasks")
@TypeConverters(Converters::class)
data class TaskEntity(
    @PrimaryKey val id: String, // UUID
    val name: String,
    val localPath: String,
    val remotePath: String,
    val syncDirection: String, // "TO_RIGHT", "TO_LEFT", "TWO_WAY"
    val isScheduled: Boolean,
    val scheduleIntervalMinutes: Long,
    val isActive: Boolean,
    val lastRunTimestamp: Long?,
    val createdAt: Long = System.currentTimeMillis()
)