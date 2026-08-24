package com.rezerv.upload

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParserFactory
import java.io.FileOutputStream
import java.io.StringReader
import java.net.URLDecoder
import java.net.URLEncoder

object WebDavRepository {
    private const val TAG = "WebDavRepository"

    data class ConnectionResult(
        val success: Boolean,
        val code: Int,
        val capabilities: String = "",
        val error: String? = null
    )

    data class FileInfo(
        val name: String,
        val path: String,
        val isDirectory: Boolean,
        val size: Long,
        val lastModified: Long = 0L
    )

    data class FileMetadata(
        val name: String,
        val size: Long
    )

    // ==================== Подключение ====================

    suspend fun testConnection(server: String, user: String, pass: String): ConnectionResult =
        withContext(Dispatchers.IO) {
            try {
                val (code, info) = WebDavClient.options(server, user, pass)
                ConnectionResult(
                    success = code in 200..299,
                    code = code,
                    capabilities = info
                )
            } catch (e: Exception) {
                Log.e(TAG, "Connection test failed", e)
                ConnectionResult(false, 0, error = e.message)
            }
        }

    // ==================== Листинг (с деталями ошибок) ====================

    suspend fun listFiles(server: String, path: String, user: String, pass: String): List<FileInfo> =
        listFilesResult(server, path, user, pass).getOrDefault(emptyList())

    suspend fun listFilesResult(
        server: String, path: String, user: String, pass: String
    ): WebDavResult<List<FileInfo>> =
        withContext(Dispatchers.IO) {
            try {
                val xml = WebDavClient.propfind(server + encodePath(path), user, pass)
                WebDavResult.success(parseMultistatus(xml, path))
            } catch (e: java.io.IOException) {
                Log.e(TAG, "Network error listing files", e)
                WebDavResult.networkError(e, server + path)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to list files", e)
                WebDavResult.localError(e.message ?: "Unknown error")
            }
        }

    // ==================== Скачивание (с деталями) ====================

    sealed class DownloadResult {
        data class Success(val bytesDownloaded: Long) : DownloadResult()
        data class HttpError(val code: Int, val url: String) : DownloadResult()
        data class IoError(val message: String) : DownloadResult()
    }

    suspend fun downloadFile(
        context: Context,
        server: String,
        remotePath: String,
        fileName: String,
        user: String,
        pass: String,
        onProgress: ((Long) -> Unit)? = null
    ): DownloadResult = withContext(Dispatchers.IO) {
        try {
            val url = server + encodePath(remotePath)
            val resp = WebDavClient.get(url, user, pass)

            if (resp.code !in 200..299) {
                resp.close()
                return@withContext DownloadResult.HttpError(resp.code, url)
            }

            var downloaded = 0L
            val outputResult = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val cv = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val uri = context.contentResolver.insert(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv
                )
                if (uri == null) {
                    resp.close()
                    return@withContext DownloadResult.IoError("Не удалось создать файл в Downloads")
                }
                context.contentResolver.openOutputStream(uri)
            } else {
                @Suppress("DEPRECATION")
                val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                dir.mkdirs()
                FileOutputStream(java.io.File(dir, fileName))
            }

            if (outputResult == null) {
                resp.close()
                return@withContext DownloadResult.IoError("Не удалось открыть поток записи")
            }

            outputResult.use { output ->
                resp.body?.byteStream()?.use { input ->
                    val buf = ByteArray(64 * 1024)
                    var r: Int
                    while (input.read(buf).also { r = it } != -1) {
                        output.write(buf, 0, r)
                        downloaded += r
                        onProgress?.invoke(downloaded)
                    }
                }
            }
            resp.close()
            DownloadResult.Success(downloaded)

        } catch (e: java.io.IOException) {
            Log.e(TAG, "Download IO error", e)
            DownloadResult.IoError(e.message ?: "IO error")
        } catch (e: Exception) {
            Log.e(TAG, "Download failed", e)
            DownloadResult.IoError(e.message ?: "Unknown error")
        }
    }

    // ==================== Удаление (с деталями) ====================

    suspend fun deleteFile(server: String, path: String, user: String, pass: String): WebDavResult<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val url = server + encodePath(path)
                val code = WebDavClient.delete(url, user, pass)
                if (code in 200..299) WebDavResult.success(Unit)
                else WebDavResult.httpError(code, url = url)
            } catch (e: java.io.IOException) {
                WebDavResult.networkError(e)
            } catch (e: Exception) {
                WebDavResult.localError(e.message ?: "Unknown")
            }
        }

    // ==================== Создание папки (с деталями) ====================

    suspend fun createFolder(server: String, path: String, user: String, pass: String): WebDavResult<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val url = server + encodePath(path)
                val code = WebDavClient.mkcol(url, user, pass)
                when {
                    code in 200..299 -> WebDavResult.success(Unit)
                    code == 405 -> WebDavResult.success(Unit)  // уже существует
                    else -> WebDavResult.httpError(code, url = url)
                }
            } catch (e: java.io.IOException) {
                WebDavResult.networkError(e)
            } catch (e: Exception) {
                WebDavResult.localError(e.message ?: "Unknown")
            }
        }

    // ==================== Утилиты (без изменений) ====================

    fun getFileMetadata(context: Context, uri: Uri): FileMetadata {
        var name = "file.bin"
        var size = -1L
        context.contentResolver.query(uri, null, null, null, null)?.use { c ->
            val n = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val s = c.getColumnIndex(OpenableColumns.SIZE)
            if (c.moveToFirst()) {
                if (n >= 0) name = c.getString(n) ?: name
                if (s >= 0) size = c.getLong(s)
            }
        }
        if (size < 0) {
            size = try {
                context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L
            } catch (e: Exception) { -1L }
        }
        return FileMetadata(name, size)
    }

    fun normalizeBaseUrl(raw: String): String? {
        var url = raw.trim().trimEnd('/')
        if (url.isEmpty()) return null
        if (!url.startsWith("http://", true) && !url.startsWith("https://", true)) {
            url = "http://$url"
        }
        return try {
            val u = java.net.URL(url)
            val port = if (u.port != -1) ":${u.port}" else ""
            "${u.protocol}://${u.host}$port"
        } catch (e: Exception) { null }
    }

    fun getServerPath(raw: String): String {
        var url = raw.trim().trimEnd('/')
        if (url.isEmpty()) return "/"
        if (!url.startsWith("http://", true) && !url.startsWith("https://", true)) {
            url = "http://$url"
        }
        return try {
            val p = java.net.URL(url).path
            if (p.isNullOrEmpty()) "/" else if (p.endsWith("/")) p else "$p/"
        } catch (e: Exception) { "/" }
    }

    fun encodePath(p: String): String = p.split("/").joinToString("/") {
        if (it.isEmpty()) "" else URLEncoder.encode(it, "UTF-8").replace("+", "%20")
    }

    private fun parseMultistatus(xml: String, requestPath: String): List<FileInfo> {
        val items = mutableListOf<FileInfo>()
        try {
            val factory = XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = true
            val parser = factory.newPullParser()
            parser.setInput(StringReader(xml))
            var eventType = parser.eventType
            var currentHref = ""
            var currentSize = 0L
            var lastModified = 0L
            var isCollection = false
            var displayName = ""

            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG) {
                    when (parser.name) {
                        "href" -> currentHref = parser.nextText()
                        "getcontentlength" -> currentSize = parser.nextText().toLongOrNull() ?: 0L
                        "getlastmodified" -> {
                            lastModified = try {
                                val sdf = java.text.SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", java.util.Locale.US)
                                sdf.parse(parser.nextText())?.time ?: 0L
                            } catch (e: Exception) { 0L }
                        }
                        "collection" -> isCollection = true
                        "displayname" -> displayName = parser.nextText()
                    }
                } else if (eventType == XmlPullParser.END_TAG) {
                    if (parser.name == "response") {
                        val normalizedHref = if (currentHref.startsWith("http")) {
                            try { java.net.URI(currentHref).path ?: currentHref }
                            catch (e: Exception) { currentHref }
                        } else currentHref

                        val decodedHref = try {
                            URLDecoder.decode(normalizedHref, "UTF-8")
                        } catch (e: Exception) { normalizedHref }

                        val name = if (displayName.isNotEmpty()) displayName
                        else {
                            val trimmed = decodedHref.trimEnd('/')
                            if (trimmed.isNotEmpty()) {
                                val lastSlash = trimmed.lastIndexOf('/')
                                if (lastSlash >= 0 && lastSlash < trimmed.length - 1)
                                    trimmed.substring(lastSlash + 1) else trimmed
                            } else ""
                        }

                        if (name.isNotEmpty()) {
                            val itemPath = if (isCollection) {
                                if (decodedHref.endsWith("/")) decodedHref else "$decodedHref/"
                            } else decodedHref

                            val requestedPathNorm = if (requestPath.endsWith("/")) requestPath else "$requestPath/"
                            val itemPathNorm = if (itemPath.endsWith("/")) itemPath else "$itemPath/"
                            if (!(isCollection && itemPathNorm == requestedPathNorm)) {
                                items.add(FileInfo(name, itemPath, isCollection, currentSize, lastModified))
                            }
                        }
                        currentHref = ""; currentSize = 0L; lastModified = 0L
                        isCollection = false; displayName = ""
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse XML", e)
        }
        return items
    }
}