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

    @Query("""
        SELECT * FROM file_cache 
        WHERE serverHost = :serverHost AND parentPath = :parentPath
    """)
    suspend fun getFilesInFolderSync(serverHost: String, parentPath: String): List<FileEntity>

    // ✅ НОВОЕ: Пагинация
    @Query("""
        SELECT * FROM file_cache 
        WHERE serverHost = :serverHost AND parentPath = :parentPath 
        ORDER BY isDirectory DESC, name COLLATE NOCASE ASC
        LIMIT :limit OFFSET :offset
    """)
    suspend fun getFilesPaged(serverHost: String, parentPath: String, limit: Int, offset: Int): List<FileEntity>

    // ✅ НОВОЕ: Счетчик для пагинации
    @Query("""
        SELECT COUNT(*) FROM file_cache 
        WHERE serverHost = :serverHost AND parentPath = :parentPath
    """)
    suspend fun getCount(serverHost: String, parentPath: String): Int

    @Upsert
    suspend fun upsertFiles(files: List<FileEntity>)

    @Query("DELETE FROM file_cache WHERE serverHost = :serverHost AND parentPath = :parentPath")
    suspend fun deleteFilesInFolder(serverHost: String, parentPath: String)

    @Query("DELETE FROM file_cache WHERE serverHost = :serverHost")
    suspend fun clearServerCache(serverHost: String)
}