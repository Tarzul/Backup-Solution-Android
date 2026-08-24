package com.rezerv.upload

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.*

object SyncEngine {
    private const val TAG = "SyncEngine"

    data class SyncResult(
        val checked: Int,
        val uploaded: Int,
        val downloaded: Int,
        val errors: Int,
        val durationMs: Long
    )

    internal class SideResult {
        var checked = 0; var uploaded = 0; var downloaded = 0
        var bytes = 0L; var transferMs = 0L; var errors = 0
        var durationMs = 0L
        val files = mutableListOf<SyncFileDetail>()
        val folders = mutableListOf<SyncFolderDetail>()
        val errorList = mutableListOf<SyncErrorDetail>()
        internal val listingCache = HashMap<String, MutableList<Entry>>()
    }

    internal data class Entry(
        val name: String,
        val isDir: Boolean,
        val size: Long,
        val lastModified: Long = 0L
    )

    /**
     * ИСПРАВЛЕНО: suspend-функция вместо блокирующей.
     * Вызывается из coroutine scope (TaskWorker, MainViewModel).
     */
    suspend fun runTask(
        context: Context,
        task: SyncTask,
        trigger: String = "schedule",
        startTime: Long = System.currentTimeMillis(),
        onProgress: ((String) -> Unit)? = null,
        onLiveUpdate: ((String, Int, Int) -> Unit)? = null
    ): SyncResult = withContext(Dispatchers.IO) {

        val progress: (String) -> Unit = { m -> onProgress?.invoke(m) }
        val live: (String, Int, Int) -> Unit = { name, idx, total -> onLiveUpdate?.invoke(name, idx, total) }
        val (serverRaw, user, pass) = SecurePrefs.loadCredentials(context)
        val server = WebDavRepository.normalizeBaseUrl(serverRaw) ?: ""
        val res = SideResult()

        val pm = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        val wl = pm.newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK, "rezerv:sync")
        wl.acquire(4 * 60 * 60 * 1000L)

        try {
            progress("▶ Задание: ${task.name}")

            if (server.isEmpty()) {
                progress("✗ Не указан сервер")
                res.errors++
                res.errorList.add(SyncErrorDetail("Сервер", "не указан"))
            } else if (!task.isValid()) {
                progress("✗ Некорректная пара папок")
                res.errors++
                res.errorList.add(SyncErrorDetail("Задание", "некорректная пара папок"))
            } else {
                val leftLocal = !task.leftIsWebdav
                try {
                    if (leftLocal) {
                        val localUri = task.leftLocalUri
                        val webPath = task.rightWebdavPath
                        ensureRemoteFolder(server, user, pass, webPath, progress, res, "Справа")
                        val doc = doc(context, localUri, progress, res)
                        when (task.syncType) {
                            "to_right" -> uploadLocal(context, doc, server, webPath, user, pass, progress, "Справа", res, live)
                            "to_left" -> downloadTo(context, webPath, doc, server, user, pass, progress, "Слева", res, live)
                            else -> {
                                uploadLocal(context, doc, server, webPath, user, pass, progress, "Справа", res, live)
                                downloadTo(context, webPath, doc, server, user, pass, progress, "Слева", res, live)
                            }
                        }
                    } else {
                        val localUri = task.rightLocalUri
                        val webPath = task.leftWebdavPath
                        ensureRemoteFolder(server, user, pass, webPath, progress, res, "Слева")
                        val doc = doc(context, localUri, progress, res)
                        when (task.syncType) {
                            "to_right" -> downloadTo(context, webPath, doc, server, user, pass, progress, "Справа", res, live)
                            "to_left" -> uploadLocal(context, doc, server, webPath, user, pass, progress, "Слева", res, live)
                            else -> {
                                uploadLocal(context, doc, server, webPath, user, pass, progress, "Слева", res, live)
                                downloadTo(context, webPath, doc, server, user, pass, progress, "Справа", res, live)
                            }
                        }
                    }
                } catch (t: Throwable) {
                    res.errors++
                    res.errorList.add(SyncErrorDetail("Движок", "${t.javaClass.simpleName}: ${t.message}"))
                    progress("✗ ${t.javaClass.simpleName}: ${t.message}")
                    Log.e(TAG, "Sync error", t)
                }
            }

            res.durationMs = System.currentTimeMillis() - startTime
            progress("■ Завершено: загружено ${res.uploaded}, скачано ${res.downloaded}, ошибок ${res.errors}")

            HistoryManager.finalizeRecord(context, HistoryRecord(
                time = startTime, durationMs = res.durationMs, checked = res.checked,
                uploaded = res.uploaded, downloaded = res.downloaded, deleted = 0,
                errors = res.errors, status = if (res.errors == 0) "ok" else "error",
                trigger = trigger, bytesTransferred = res.bytes, transferMs = res.transferMs,
                filesJson = filesToJson(res.files),
                foldersJson = foldersToJson(res.folders),
                errorsJson = errorsToJson(res.errorList),
                taskName = task.name, taskId = task.id,
                totalFiles = res.checked
            ))

        } finally {
            if (wl.isHeld) wl.release()
        }

        SyncResult(res.checked, res.uploaded, res.downloaded, res.errors, res.durationMs)
    }

    // ==================== Upload (suspend) ====================

    private suspend fun uploadLocal(
        context: Context, dir: DocumentFile?, server: String, webPath: String,
        user: String, pass: String, progress: (String) -> Unit, side: String,
        res: SideResult, live: (String, Int, Int) -> Unit
    ) {
        if (dir == null) return
        live("📋 Список: ${webPath.substringAfterLast('/')}/", 0, 0)

        val remote = listRemote(server, user, pass, webPath, progress, res) ?: return
        val remoteFiles = remote.filter { !it.isDir }.associate { it.name to it }

        val allFiles = dir.listFiles().filter {
            it.isFile && !it.isDirectory && !isJunkFile(it.name ?: "") && it.length() > 0L
        }
        val totalToUpload = allFiles.size
        var fileIndex = 0

        for (f in dir.listFiles()) {
            val name = f.name ?: continue
            if (isJunkFile(name) || f.isDirectory) {
                if (f.isDirectory && !isJunkFolder(name)) {
                    val sub = webPath.trimEnd('/') + "/" + name
                    ensureRemoteFolder(server, user, pass, sub, progress, res, side)
                    uploadLocal(context, f, server, sub, user, pass, progress, side, res, live)
                }
                continue
            }
            if (!f.isFile || f.length() <= 0L) continue

            res.checked++
            fileIndex++
            live(name, fileIndex, totalToUpload)

            val rFile = remoteFiles[name]
            if (rFile != null && rFile.size == f.length()) {
                val localM = f.lastModified()
                val remoteM = rFile.lastModified
                if (remoteM <= 0L || localM <= 0L) continue
                if (remoteM >= localM - 1500L) continue
            }

            progress("   ⬆ $name")
            val t0 = System.currentTimeMillis()
            try {
                val input = context.contentResolver.openInputStream(f.uri)
                if (input == null) {
                    progress("   ✗ $name: не удалось открыть")
                    res.errors++
                    res.errorList.add(SyncErrorDetail(name, "не удалось открыть"))
                } else {
                    input.use {
                        // ИСПРАВЛЕНО: suspend-вызов
                        val code = WebDavClient.put(
                            server + WebDavRepository.encodePath(webPath.trimEnd('/') + "/" + name),
                            user, pass, it
                        )
                        if (code in 200..299) {
                            res.uploaded++
                            res.bytes += f.length()
                            res.files.add(SyncFileDetail(name, f.length(), System.currentTimeMillis() - t0, side))
                            progress("   ✓ $name")
                        } else {
                            val reason = httpErrorReason(code)
                            progress("   ✗ $name: $reason")
                            res.errors++
                            res.errorList.add(SyncErrorDetail(name, reason))
                        }
                    }
                }
            } catch (e: Exception) {
                progress("   ✗ $name: ${e.message}")
                res.errors++
                res.errorList.add(SyncErrorDetail(name, e.message ?: "исключение"))
                Log.e(TAG, "Upload error: $name", e)
            }
            res.transferMs += System.currentTimeMillis() - t0
        }
    }

    // ==================== Download (suspend) ====================

    private suspend fun downloadTo(
        context: Context, webPath: String, dir: DocumentFile?, server: String,
        user: String, pass: String, progress: (String) -> Unit, side: String,
        res: SideResult, live: (String, Int, Int) -> Unit
    ) {
        if (dir == null) return
        live("📋 Список (скачивание): ${webPath.substringAfterLast('/')}/", 0, 0)

        val entries = listRemote(server, user, pass, webPath, progress, res) ?: return
        val fileEntries = entries.filter { !it.isDir }
        val totalToDownload = fileEntries.size
        var fileIndex = 0

        val localChildren = dir.listFiles()
        val localMap = HashMap<String, DocumentFile>(localChildren.size)
        for (c in localChildren) {
            val n = c.name
            if (!c.isDirectory && n != null) localMap[n] = c
        }

        for (e in entries) {
            val remote = webPath.trimEnd('/') + "/" + e.name
            if (isJunkFile(e.name) || isJunkFolder(e.name)) continue

            if (e.isDir) {
                val sub = localChildren.firstOrNull { it.isDirectory && it.name == e.name }
                    ?: dir.createDirectory(e.name) ?: continue
                downloadTo(context, remote, sub, server, user, pass, progress, side, res, live)
                continue
            }

            res.checked++
            var existing = localMap[e.name]
            if (existing != null && existing.length() == e.size) continue
            if (existing == null) existing = dir.findFile(e.name)?.takeIf { it.isFile }
            if (existing != null && existing.length() == e.size) continue

            fileIndex++
            live(e.name, fileIndex, totalToDownload)
            progress("   ⬇ ${e.name}")
            val t0 = System.currentTimeMillis()

            var resp: okhttp3.Response? = null
            try {
                // ИСПРАВЛЕНО: suspend-вызов
                resp = WebDavClient.get(server + WebDavRepository.encodePath(remote), user, pass)
                if (resp.code in 200..299) {
                    val target = existing ?: dir.createFile(getMimeType(e.name), e.name)
                    if (target == null) {
                        progress("   ✗ ${e.name}: не удалось создать файл")
                        res.errors++
                        res.errorList.add(SyncErrorDetail(e.name, "не удалось создать файл"))
                    } else {
                        var size = 0L
                        context.contentResolver.openOutputStream(target.uri, "rwt")?.use { out ->
                            resp.body?.byteStream()?.use { input ->
                                val buf = ByteArray(64 * 1024)
                                var r: Int
                                while (input.read(buf).also { r = it } != -1) {
                                    out.write(buf, 0, r)
                                    size += r
                                }
                            }
                        }
                        res.downloaded++
                        res.bytes += size
                        res.files.add(SyncFileDetail(e.name, size, System.currentTimeMillis() - t0, side))
                        progress("   ✓ ${e.name}")
                    }
                } else {
                    val reason = httpErrorReason(resp.code)
                    progress("   ✗ ${e.name}: $reason")
                    res.errors++
                    res.errorList.add(SyncErrorDetail(e.name, reason))
                }
            } catch (e2: Exception) {
                progress("   ✗ ${e.name}: ${e2.message}")
                res.errors++
                res.errorList.add(SyncErrorDetail(e.name, e2.message ?: "исключение"))
                Log.e(TAG, "Download error: ${e.name}", e2)
            } finally {
                try { resp?.close() } catch (_: Exception) {}
            }
            res.transferMs += System.currentTimeMillis() - t0
        }
    }

    // ==================== Утилиты ====================

    private suspend fun listRemote(
        server: String, user: String, pass: String, webPath: String,
        progress: (String) -> Unit, res: SideResult
    ): List<Entry>? {
        res.listingCache[webPath]?.let { return it }
        return try {
            val xml = WebDavClient.propfind(server + WebDavRepository.encodePath(webPath), user, pass)
            val list = parseEntries(xml, webPath)
            res.listingCache[webPath] = list.toMutableList()
            list
        } catch (e: Exception) {
            progress("✗ Ошибка PROPFIND: ${e.message}")
            res.errors++
            res.errorList.add(SyncErrorDetail("PROPFIND $webPath", e.message ?: "исключение"))
            Log.e(TAG, "Propfind error", e)
            null
        }
    }

    private suspend fun ensureRemoteFolder(
        server: String, user: String, pass: String, folderPath: String,
        progress: (String) -> Unit, res: SideResult, side: String
    ) {
        try {
            val code = WebDavClient.mkcol(server + WebDavRepository.encodePath(folderPath), user, pass)
            when {
                code in 200..299 -> {
                    res.folders.add(SyncFolderDetail(folderPath, side))
                    progress("   📁 Создана папка: $folderPath")
                }
                code == 405 -> {} // уже существует
                else -> {
                    progress("   ✗ Ошибка создания папки $folderPath (HTTP $code)")
                    res.errors++
                    res.errorList.add(SyncErrorDetail(folderPath, httpErrorReason(code)))
                }
            }
        } catch (e: Exception) {
            progress("   ✗ Ошибка создания папки: ${e.message}")
            res.errors++
            res.errorList.add(SyncErrorDetail(folderPath, e.message ?: "исключение"))
        }
    }

    private fun doc(context: Context, uri: String, progress: (String) -> Unit, res: SideResult): DocumentFile? {
        return try {
            DocumentFile.fromTreeUri(context, Uri.parse(uri))
        } catch (e: Exception) {
            progress("✗ Не удалось открыть папку: $uri")
            res.errors++
            res.errorList.add(SyncErrorDetail("Локальная папка", "не удалось открыть: $uri"))
            null
        }
    }

    private fun httpErrorReason(code: Int): String = when (code) {
        401 -> "ошибка авторизации"
        403 -> "нет прав"
        404 -> "путь не найден"
        413 -> "слишком большой"
        507 -> "сервер переполнен"
        in 500..599 -> "ошибка сервера (HTTP $code)"
        else -> "HTTP $code"
    }

    private fun isJunkFile(name: String): Boolean {
        val lower = name.lowercase()
        return lower.startsWith(".thumbnails") || lower == "thumbs.db" ||
               lower == ".ds_store" || lower == "desktop.ini" ||
               lower.startsWith("._") || lower == ".nomedia"
    }

    private fun isJunkFolder(name: String): Boolean {
        val lower = name.lowercase()
        return lower == "cache" || lower == "thumbnails" || lower == ".thumbnails" ||
               lower == ".cache" || lower == ".trashes" || lower == ".spotlight-v100" ||
               lower == ".fseventsd"
    }

    private fun getMimeType(fileName: String): String {
        val l = fileName.lowercase()
        return when {
            l.endsWith(".jpg") || l.endsWith(".jpeg") -> "image/jpeg"
            l.endsWith(".png") -> "image/png"
            l.endsWith(".gif") -> "image/gif"
            l.endsWith(".webp") -> "image/webp"
            l.endsWith(".mp4") -> "video/mp4"
            l.endsWith(".pdf") -> "application/pdf"
            l.endsWith(".txt") -> "text/plain"
            else -> "application/octet-stream"
        }
    }

    private fun parseEntries(xml: String, requestPath: String): List<Entry> {
        // Тот же парсинг что в WebDavRepository.parseMultistatus,
        // но возвращает Entry с lastModified
        val items = mutableListOf<Entry>()
        try {
            val factory = org.xmlpull.v1.XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = true
            val parser = factory.newPullParser()
            parser.setInput(java.io.StringReader(xml))
            var eventType = parser.eventType
            var href = ""; var size = 0L; var lastMod = 0L
            var isDir = false; var displayName = ""

            while (eventType != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                if (eventType == org.xmlpull.v1.XmlPullParser.START_TAG) {
                    when (parser.name) {
                        "href" -> href = parser.nextText()
                        "getcontentlength" -> size = parser.nextText().toLongOrNull() ?: 0L
                        "getlastmodified" -> {
                            lastMod = try {
                                val sdf = java.text.SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", java.util.Locale.US)
                                sdf.parse(parser.nextText())?.time ?: 0L
                            } catch (e: Exception) { 0L }
                        }
                        "collection" -> isDir = true
                        "displayname" -> displayName = parser.nextText()
                    }
                } else if (eventType == org.xmlpull.v1.XmlPullParser.END_TAG && parser.name == "response") {
                    val decoded = try { java.net.URLDecoder.decode(href, "UTF-8") } catch (e: Exception) { href }
                    val name = if (displayName.isNotEmpty()) displayName
                    else decoded.trimEnd('/').substringAfterLast('/')

                    val itemPath = if (isDir && !decoded.endsWith("/")) "$decoded/" else decoded
                    val reqNorm = if (requestPath.endsWith("/")) requestPath else "$requestPath/"
                    val itemNorm = if (itemPath.endsWith("/")) itemPath else "$itemPath/"

                    if (name.isNotEmpty() && !(isDir && itemNorm == reqNorm)) {
                        items.add(Entry(name, isDir, size, lastMod))
                    }
                    href = ""; size = 0L; lastMod = 0L; isDir = false; displayName = ""
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            Log.e(TAG, "parseEntries error", e)
        }
        return items
    }

    private fun filesToJson(list: List<SyncFileDetail>): String {
        val arr = org.json.JSONArray()
        for (f in list) arr.put(org.json.JSONObject().apply {
            put("n", f.name); put("s", f.size); put("m", f.ms); put("d", f.side)
        })
        return arr.toString()
    }

    private fun foldersToJson(list: List<SyncFolderDetail>): String {
        val arr = org.json.JSONArray()
        for (f in list) arr.put(org.json.JSONObject().apply {
            put("p", f.path); put("d", f.side)
        })
        return arr.toString()
    }

    private fun errorsToJson(list: List<SyncErrorDetail>): String {
        val arr = org.json.JSONArray()
        for (e in list) arr.put(org.json.JSONObject().apply {
            put("n", e.name); put("r", e.reason)
        })
        return arr.toString()
    }
}