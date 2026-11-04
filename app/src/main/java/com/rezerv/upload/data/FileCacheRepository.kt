package com.rezerv.upload.data

import android.util.Log
import com.rezerv.upload.WebDavRepository
import com.rezerv.upload.data.local.FileDao
import com.rezerv.upload.data.local.FileEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FileCacheRepository @Inject constructor(
    private val fileDao: FileDao,
    private val webDavService: WebDavService
) {
    private val TAG = "FileCacheRepo"

    fun getFilesFlow(serverHost: String, parentPath: String): Flow<List<WebDavRepository.FileInfo>> {
        return fileDao.getFilesInFolder(serverHost, parentPath)
            .map { entities -> entities.map { it.toFileInfo() } }
    }

    suspend fun refreshFromServer(
        serverHost: String, serverUrl: String, parentPath: String, user: String, pass: String
    ): Boolean {
        return try {
            val serverFiles = webDavService.listFiles(serverUrl, parentPath, user, pass)
            val cachedFiles = fileDao.getFilesInFolderSync(serverHost, parentPath)

            if (cachedFiles.isNotEmpty() && serverFiles.size == cachedFiles.size) {
                val serverSum = serverFiles.sumOf { it.size + it.lastModified }
                val cacheSum = cachedFiles.sumOf { it.size + it.lastModified }
                if (serverSum == cacheSum) {
                    return false
                }
            }

            val now = System.currentTimeMillis()
            val entities = serverFiles.map { f ->
                FileEntity(
                    serverHost = serverHost, path = f.path, parentPath = parentPath,
                    name = f.name, size = f.size, lastModified = f.lastModified,
                    isDirectory = f.isDirectory, lastRefreshed = now
                )
            }

            fileDao.deleteFilesInFolder(serverHost, parentPath)
            fileDao.upsertFiles(entities)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Cache refresh failed for $parentPath", e)
            false
        }
    }

    suspend fun clearCache(serverHost: String) = fileDao.clearServerCache(serverHost)
}

// ✅ ИСПРАВЛЕНО: internal вместо private, чтобы функция была видна в BrowserViewModel
internal fun FileEntity.toFileInfo(): WebDavRepository.FileInfo {
    return WebDavRepository.FileInfo(
        name = name,
        path = path,
        size = size,
        lastModified = lastModified,
        isDirectory = isDirectory
    )
}