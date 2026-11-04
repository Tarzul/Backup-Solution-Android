package com.rezerv.upload.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface FileDao {
    @Query("""
        SELECT * FROM file_cache 
        WHERE serverHost = :serverHost AND parentPath = :parentPath 
        ORDER BY isDirectory DESC, name COLLATE NOCASE ASC
    """)
    fun getFilesInFolder(serverHost: String, parentPath: String): Flow<List<FileEntity>>

    @Query("SELECT * FROM file_cache WHERE serverHost = :serverHost AND parentPath = :parentPath")
    suspend fun getFilesInFolderSync(serverHost: String, parentPath: String): List<FileEntity>

    @Upsert
    suspend fun upsertFiles(files: List<FileEntity>)

    @Query("DELETE FROM file_cache WHERE serverHost = :serverHost AND parentPath = :parentPath")
    suspend fun deleteFilesInFolder(serverHost: String, parentPath: String)

    @Query("DELETE FROM file_cache WHERE serverHost = :serverHost")
    suspend fun clearServerCache(serverHost: String)
}