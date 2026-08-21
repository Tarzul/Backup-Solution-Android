package com.rezerv.upload

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile

object SyncEngine {
    private const val TAG = "SyncEngine"

    data class SyncResult(val checked: Int, val uploaded: Int, val downloaded: Int, val errors: Int, val durationMs: Long)

    class SideResult {
        var checked = 0; var uploaded = 0; var downloaded = 0
        var bytes = 0L; var transferMs = 0L; var errors = 0
        val files = mutableListOf<SyncFileDetail>()
        val folders = mutableListOf<SyncFolderDetail>()
    }

    private data class Entry(val name: String, val isDir: Boolean, val size: Long)

    // ==================== Запуск задания ====================
    fun runTask(
        context: Context, task: SyncTask,
        trigger: String = "schedule",
        startTime: Long = System.currentTimeMillis(),
        onProgress: ((String) -> Unit)? = null,
        onLiveUpdate: ((String, Int, Int) -> Unit)? = null
    ): SyncResult {
        val progress: (String) -> Unit = { m -> onProgress?.invoke(m) }
        val live: (String, Int, Int) -> Unit = { name, idx, total -> onLiveUpdate?.invoke(name, idx, total) }
        val (serverRaw, user, pass) = SecurePrefs.loadCredentials(context)
        val server = WebDavRepository.normalizeBaseUrl(serverRaw) ?: ""
        val res = SideResult()
        progress("▶ Задание: ${task.name}")

        if (server.isEmpty()) { progress("✗ Не указан сервер"); res.errors++ }
        else if (!task.isValid()) { progress("✗ Некорректная пара папок"); res.errors++ }
        else {
            val leftLocal = !task.leftIsWebdav
            try {
                if (leftLocal) {
                    val localUri = task.leftLocalUri; val webPath = task.rightWebdavPath
                    ensureRemoteFolder(server, user, pass, webPath, progress, res, "Справа")
                    when (task.syncType) {
                        "to_right" -> uploadLocal(context, doc(context, localUri, progress, res), server, webPath, user, pass, progress, "Справа", res, live)
                        "to_left" -> downloadTo(context, webPath, doc(context, localUri, progress, res), server, user, pass, progress, "Слева", res, live)
                        else -> {
                            val d = doc(context, localUri, progress, res)
                            uploadLocal(context, d, server, webPath, user, pass, progress, "Справа", res, live)
                            downloadTo(context, webPath, d, server, user, pass, progress, "Слева", res, live)
                        }
                    }
                } else {
                    val localUri = task.rightLocalUri; val webPath = task.leftWebdavPath
                    ensureRemoteFolder(server, user, pass, webPath, progress, res, "Слева")
                    when (task.syncType) {
                        "to_right" -> downloadTo(context, webPath, doc(context, localUri, progress, res), server, user, pass, progress, "Справа", res, live)
                        "to_left" -> uploadLocal(context, doc(context, localUri, progress, res), server, webPath, user, pass, progress, "Слева", res, live)
                        else -> {
                            val d = doc(context, localUri, progress, res)
                            uploadLocal(context, d, server, webPath, user, pass, progress, "Слева", res, live)
                            downloadTo(context, webPath, d, server, user, pass, progress, "Справа", res, live)
                        }
                    }
                }
            } catch (t: Throwable) {
                res.errors++
                progress("✗ ${t.javaClass.simpleName}: ${t.message}")
                Log.e(TAG, "Sync error", t)
            }
        }

        val durationMs = System.currentTimeMillis() - startTime
        progress("■ Завершено: загружено ${res.uploaded}, скачано ${res.downloaded}, ошибок ${res.errors}")
        
        // НОВОЕ: заменяем addRecord на finalizeRecord — обновляет live-запись по time
        HistoryManager.finalizeRecord(context, HistoryRecord(
            time = startTime, durationMs = durationMs, checked = res.checked,
            uploaded = res.uploaded, downloaded = res.downloaded, deleted = 0,
            errors = res.errors, status = if (res.errors == 0) "ok" else "error",
            trigger = trigger, bytesTransferred = res.bytes, transferMs = res.transferMs,
            filesJson = filesToJson(res.files), foldersJson = foldersToJson(res.folders),
            taskName = task.name,
            taskId = task.id,
            totalFiles = res.checked
        ))
        
        return SyncResult(res.checked, res.uploaded, res.downloaded, res.errors, durationMs)
    }

    private fun doc(context: Context, uri: String, progress: (String) -> Unit, res: SideResult): DocumentFile? {
        val d = try { DocumentFile.fromTreeUri(context, Uri.parse(uri)) } catch (e: Exception) { null }
        if (d == null) { progress("✗ Ошибка: папка недоступна (URI невалиден)"); res.errors++ }
        return d
    }

    // ==================== Локально → WebDAV (рекурсивно) ====================
    private fun uploadLocal(
        context: Context, dir: DocumentFile?, server: String, webPath: String,
        user: String, pass: String, progress: (String) -> Unit, side: String, res: SideResult,
        live: (String, Int, Int) -> Unit   // НОВОЕ
    ) {
        if (dir == null) return
        // НОВОЕ: показываем в live-карточке, что идёт листинг папки
        live("📋 Список (загрузка): ${webPath.substringAfterLast('/')}/", 0, 0)
        val remote = listRemote(server, user, pass, webPath, progress, res) ?: return
        val remoteFiles = remote.filter { !it.isDir }.associate { it.name to it.size }
        
        // НОВОЕ: считаем общее количество файлов ДО цикла (для прогресса)
        val allFiles = dir.listFiles().filter {
            it.isFile && !it.isDirectory && !isJunkFile(it.name ?: "") && (it.length() > 0L)
        }
        val totalToUpload = allFiles.size
        var fileIndex = 0
        
        for (f in dir.listFiles()) {
            val name = f.name ?: continue
            
            // ИСПРАВЛЕНО: пропускаем мусорные файлы DCIM
            if (isJunkFile(name)) continue
            
            if (f.isDirectory) {
                // ИСПРАВЛЕНО: пропускаем мусорные папки (cache, thumbnails, trashes...)
                if (isJunkFolder(name)) continue
                val sub = webPath.trimEnd('/') + "/" + name
                ensureRemoteFolder(server, user, pass, sub, progress, res, side)
                // НОВОЕ: передаём live в рекурсивный вызов
                uploadLocal(context, f, server, sub, user, pass, progress, side, res, live)
                continue
            }
            if (!f.isFile) continue
            
            // ИСПРАВЛЕНО: пропускаем файлы нулевого размера
            if (f.length() <= 0L) continue
            
            res.checked++
            fileIndex++
            // НОВОЕ: уведомляем UI о текущем файле
            live(name, fileIndex, totalToUpload)
            
            val rSize = remoteFiles[name]
            if (rSize != null && rSize == f.length()) continue
            progress("   ⬆ $name")
            val t0 = System.currentTimeMillis()
            try {
                val input = context.contentResolver.openInputStream(f.uri)
                if (input == null) { progress("   ✗ $name: не удалось открыть"); res.errors++ }
                else input.use {
                    val code = WebDavClient.put(server + WebDavRepository.encodePath(webPath.trimEnd('/') + "/" + name), user, pass, it)
                    if (code in 200..299) {
                        res.uploaded++; res.bytes += f.length()
                        res.files.add(SyncFileDetail(name, f.length(), System.currentTimeMillis() - t0, side))
                        progress("   ✓ $name")
                    } else {
                        val reason = when (code) {
                            403 -> "нет прав"; 413 -> "слишком большой"; 507 -> "сервер переполнен"
                            404 -> "путь не найден"; 500, 502, 503 -> "ошибка сервера"
                            else -> "HTTP $code"
                        }
                        progress("   ✗ $name: $reason")
                        res.errors++
                    }
                }
            } catch (e: Exception) { progress("   ✗ $name: ${e.message}"); res.errors++; Log.e(TAG, "Upload error: $name", e) }
            res.transferMs += System.currentTimeMillis() - t0
        }
    }

    /** Фильтр мусорных файлов, которые не нужно синхронизировать */
    private fun isJunkFile(name: String): Boolean {
        val lower = name.lowercase()
        return lower.startsWith(".") ||
                lower == "cache" ||
                lower.startsWith("cache (") ||
                lower.endsWith(".tmp") ||
                lower.endsWith(".crdownload") ||
                lower.startsWith("~") ||
                lower.contains(".pending") ||
                lower == "thumbdata" ||
                lower.startsWith("thumbdata3_") ||
                lower.startsWith("thumbdata4_")
    }  

    /** Фильтр мусорных папок, которые не нужно синхронизировать */
    private fun isJunkFolder(name: String): Boolean {
        val lower = name.lowercase()
        return lower.startsWith(".") ||
                lower == "cache" ||
                lower == "caches" ||
                lower == "thumbnails" ||
                lower == "trashes" ||
                lower == "lost.dir" ||
                lower == "temp" ||
                lower == "tmp" ||
                lower == "android"   // Android/data, Android/obb — данные приложений
    }      

    // ==================== WebDAV → локально (рекурсивно) ====================
    private fun downloadTo(
        context: Context, webPath: String, dir: DocumentFile?, server: String,
        user: String, pass: String, progress: (String) -> Unit, side: String, res: SideResult,
        live: (String, Int, Int) -> Unit   // НОВОЕ
    ) {
        if (dir == null) return
        live("📋 Список (скачивание): ${webPath.substringAfterLast('/')}/", 0, 0)
        val entries = listRemote(server, user, pass, webPath, progress, res) ?: return
        
        // НОВОЕ: считаем общее количество файлов ДО цикла
        val fileEntries = entries.filter { !it.isDir }
        val totalToDownload = fileEntries.size
        var fileIndex = 0
        val localChildren = dir.listFiles()
        
        for (e in entries) {
            val remote = webPath.trimEnd('/') + "/" + e.name
            
            // ИСПРАВЛЕНО: мусор с сервера не скачиваем и не заходим в мусорные папки
            if (isJunkFile(e.name) || isJunkFolder(e.name)) continue
            
            if (e.isDir) {
                val sub = localChildren.firstOrNull { it.isDirectory && it.name == e.name }
                    ?: dir.createDirectory(e.name) ?: continue
                // НОВОЕ: передаём live в рекурсивный вызов
                downloadTo(context, remote, sub, server, user, pass, progress, side, res, live)
                continue
            }
            res.checked++
            val existing = dir.findFile(e.name)?.takeIf { it.isFile }
                ?: localChildren.firstOrNull { !it.isDirectory && it.name == e.name }
            if (existing != null && existing.length() == e.size) continue   // не изменился
            
            fileIndex++
            // НОВОЕ: уведомляем UI о текущем файле
            live(e.name, fileIndex, totalToDownload)
            
            progress("   ⬇ ${e.name}")
            val t0 = System.currentTimeMillis()
            var resp: okhttp3.Response? = null
            try {
                resp = WebDavClient.get(server + WebDavRepository.encodePath(remote), user, pass)
                if (resp.code in 200..299) {
                    val target = existing ?: dir.createFile(getMimeType(e.name), e.name)
                    if (target == null) { progress("   ✗ ${e.name}: не удалось создать файл"); res.errors++ }
                    else {
                        var size = 0L
                        context.contentResolver.openOutputStream(target.uri, "rwt")?.use { out ->
                            resp.body?.byteStream()?.use { input ->
                                val buf = ByteArray(64 * 1024); var r: Int
                                while (input.read(buf).also { r = it } != -1) { out.write(buf, 0, r); size += r }
                            }
                        }
                        res.downloaded++; res.bytes += size
                        res.files.add(SyncFileDetail(e.name, size, System.currentTimeMillis() - t0, side))
                        progress("   ✓ ${e.name}")
                    }
                } else { progress("   ✗ ${e.name} (HTTP ${resp.code})"); res.errors++ }
            } catch (e2: Exception) {
                progress("   ✗ ${e.name}: ${e2.message}")
                res.errors++
                Log.e(TAG, "Download error: ${e.name}", e2)
            } finally {
                try { resp?.close() } catch (_: Exception) {}
            }
            res.transferMs += System.currentTimeMillis() - t0
        }
    }

    // ==================== Утилиты ====================
    private fun listRemote(server: String, user: String, pass: String, webPath: String,
                           progress: (String) -> Unit, res: SideResult): List<Entry>? {
        return try {
            val xml = WebDavClient.propfind(server + WebDavRepository.encodePath(webPath), user, pass)
            parseEntries(xml, webPath)
        } catch (e: Exception) {
            progress("✗ Ошибка PROPFIND: ${e.message}"); res.errors++
            Log.e(TAG, "Propfind error", e); null
        }
    }

    private fun ensureRemoteFolder(server: String, user: String, pass: String, folderPath: String,
                                   progress: (String) -> Unit, res: SideResult, side: String) {
        try {
            val code = WebDavClient.mkcol(server + WebDavRepository.encodePath(folderPath), user, pass)
            when {
                code in 200..299 -> { res.folders.add(SyncFolderDetail(folderPath, side)); progress("   📁 Создана папка: $folderPath") }
                code == 405 -> {} // уже существует
                else -> { progress("   ✗ Ошибка создания папки $folderPath (HTTP $code)"); res.errors++ }
            }
        } catch (e: Exception) { progress("   ✗ Ошибка создания папки: ${e.message}"); res.errors++ }
    }

    private fun parseEntries(xml: String, requestPath: String): List<Entry> {
        val list = mutableListOf<Entry>()
        try {
            val factory = javax.xml.parsers.DocumentBuilderFactory.newInstance()
            factory.isNamespaceAware = true
            val doc = factory.newDocumentBuilder().parse(org.xml.sax.InputSource(java.io.StringReader(xml)))
            val responses = doc.getElementsByTagNameNS("DAV:", "response")
            val reqNorm = requestPath.trimEnd('/')
            for (i in 0 until responses.length) {
                val node = responses.item(i) ?: continue
                var isDir = false; var name = ""; var size = 0L; var href = ""
                fun walkProp(n: org.w3c.dom.Node) {
                    for (j in 0 until n.childNodes.length) {
                        val c = n.childNodes.item(j) ?: continue
                        when (c.localName ?: c.nodeName ?: "") {
                            "href" -> href = c.textContent.orEmpty()
                            "collection" -> isDir = true
                            "displayname" -> name = c.textContent.orEmpty()
                            "getcontentlength" -> size = c.textContent.orEmpty().toLongOrNull() ?: 0L
                            "propstat", "prop", "response" -> walkProp(c)
                        }
                    }
                }
                walkProp(node)
                val decodedHref = try { java.net.URLDecoder.decode(href, "UTF-8") } catch (e: Exception) { href }
                if (decodedHref.trimEnd('/') == reqNorm) continue          // сама папка
                if (name.isEmpty()) {
                    val t = decodedHref.trimEnd('/')
                    name = t.substringAfterLast('/')
                }
                if (name.isNotEmpty()) list.add(Entry(name, isDir, size))
            }
        } catch (e: Exception) { Log.e(TAG, "parseEntries error", e) }
        return list
    }

    private fun filesToJson(list: List<SyncFileDetail>): String {
        val arr = org.json.JSONArray()
        for (f in list) arr.put(org.json.JSONObject().apply { put("n", f.name); put("s", f.size); put("m", f.ms); put("d", f.side) })
        return arr.toString()
    }
    private fun foldersToJson(list: List<SyncFolderDetail>): String {
        val arr = org.json.JSONArray()
        for (f in list) arr.put(org.json.JSONObject().apply { put("p", f.path); put("d", f.side) })
        return arr.toString()
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
}