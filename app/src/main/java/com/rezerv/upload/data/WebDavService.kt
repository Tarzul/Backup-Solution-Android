package com.rezerv.upload.data

import android.content.Context
import android.net.Uri
import com.rezerv.upload.WebDavRepository
import com.rezerv.upload.WebDavResult

/**
 * Интерфейс сервиса для работы с WebDAV сервером.
 * Использует типы данных из object WebDavRepository (FileInfo, FileMetadata и т.д.)
 * Утилитарные методы (normalizeBaseUrl, encodePath) остаются в object.
 */
interface WebDavService {

    suspend fun testConnection(
        server: String, user: String, pass: String
    ): WebDavRepository.ConnectionResult

    suspend fun listFiles(
        server: String, path: String, user: String, pass: String
    ): List<WebDavRepository.FileInfo>

    suspend fun listFilesResult(
        server: String, path: String, user: String, pass: String
    ): WebDavResult<List<WebDavRepository.FileInfo>>

    suspend fun downloadFile(
        context: Context,
        server: String,
        remotePath: String,
        fileName: String,
        user: String,
        pass: String,
        onProgress: ((Long) -> Unit)? = null
    ): WebDavRepository.DownloadResult

    suspend fun deleteFile(
        server: String, path: String, user: String, pass: String
    ): WebDavResult<Unit>

    suspend fun createFolder(
        server: String, path: String, user: String, pass: String
    ): WebDavResult<Unit>

    fun getFileMetadata(context: Context, uri: Uri): WebDavRepository.FileMetadata
}