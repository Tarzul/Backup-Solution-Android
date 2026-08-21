package com.rezerv.upload

import android.util.Base64

/** Единая точка сборки URL и заголовка авторизации для Coil. */
object WebDavImages {

    /** base без пути + закодированный абсолютный путь (без двойных /webdav/, пробелы и кириллица закодированы). */
    fun url(server: String, path: String): String {
        val base = WebDavRepository.normalizeBaseUrl(server) ?: return ""
        return base + WebDavRepository.encodePath(path)
    }

    fun basicHeader(user: String, pass: String): String =
        "Basic " + Base64.encodeToString("$user:$pass".toByteArray(), Base64.NO_WRAP)

    /** Ключи кэша: зависят от пути и размера файла — обновлённый файл не возьмётся из старого кэша. */
    fun cacheKey(prefix: String, path: String, size: Long): String = "$prefix:$path:$size"
}