package com.rezerv.upload.data.local

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "file_cache",
    primaryKeys = ["serverHost", "path"],
    indices = [Index(value = ["serverHost", "parentPath"])]
)
data class FileEntity(
    val serverHost: String,       // Например: "tarzul.crazedns.ru:5083"
    val path: String,             // Полный путь: "/webdav/photos/img.jpg"
    val parentPath: String,       // Путь папки: "/webdav/photos/"
    val name: String,
    val size: Long,
    val lastModified: Long,
    val isDirectory: Boolean,
    val lastRefreshed: Long       // Timestamp последнего обновления
)