package com.rezerv.upload.data.impl

import android.content.Context
import android.net.Uri
import com.rezerv.upload.WebDavRepository
import com.rezerv.upload.WebDavResult
import com.rezerv.upload.data.WebDavService
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WebDavServiceImpl @Inject constructor() : WebDavService {

    override suspend fun testConnection(
        server: String, user: String, pass: String
    ): WebDavRepository.ConnectionResult =
        WebDavRepository.testConnection(server, user, pass)

    override suspend fun listFiles(
        server: String, path: String, user: String, pass: String
    ): List<WebDavRepository.FileInfo> =
        WebDavRepository.listFiles(server, path, user, pass)

    override suspend fun listFilesResult(
        server: String, path: String, user: String, pass: String
    ): WebDavResult<List<WebDavRepository.FileInfo>> =
        WebDavRepository.listFilesResult(server, path, user, pass)

    override suspend fun downloadFile(
        context: Context,
        server: String,
        remotePath: String,
        fileName: String,
        user: String,
        pass: String,
        onProgress: ((Long) -> Unit)?
    ): WebDavRepository.DownloadResult =
        WebDavRepository.downloadFile(context, server, remotePath, fileName, user, pass, onProgress)

    override suspend fun deleteFile(
        server: String, path: String, user: String, pass: String
    ): WebDavResult<Unit> =
        WebDavRepository.deleteFile(server, path, user, pass)

    override suspend fun createFolder(
        server: String, path: String, user: String, pass: String
    ): WebDavResult<Unit> =
        WebDavRepository.createFolder(server, path, user, pass)

    override fun getFileMetadata(context: Context, uri: Uri): WebDavRepository.FileMetadata =
        WebDavRepository.getFileMetadata(context, uri)
}