package com.rezerv.upload.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey val id: String,
    val name: String,
    val leftLocalUri: String,
    val rightWebdavPath: String,
    val leftIsWebdav: Boolean,
    val syncType: String,
    val scheduleEnabled: Boolean,
    val scheduleIntervalMinutes: Long,
    val useWifi: Boolean,
    val useMobile: Boolean,
    val onlyCharging: Boolean,
    val notifyOnSuccess: Boolean,
    val notifyOnError: Boolean,
    val lastRun: Long?,
    val lastStatus: String?,
    val createdAt: Long = System.currentTimeMillis()
)