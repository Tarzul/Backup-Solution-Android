package com.rezerv.upload

import java.io.IOException

/**
 * Типизированные результаты WebDAV-операций.
 * Заменяет Boolean-возвраты для детальной диагностики.
 */
sealed class WebDavResult<out T> {

    data class Success<T>(val data: T) : WebDavResult<T>()

    data class HttpError(
        val code: Int,
        val message: String,
        val url: String = ""
    ) : WebDavResult<Nothing>()

    data class NetworkError(
        val exception: IOException,
        val url: String = ""
    ) : WebDavResult<Nothing>()

    data class LocalError(
        val message: String
    ) : WebDavResult<Nothing>()

    // ==================== Утилиты ====================

    val isSuccess: Boolean get() = this is Success
    val isError: Boolean get() = this !is Success

    fun getOrNull(): T? = when (this) {
        is Success -> data
        else -> null
    }

    fun getOrDefault(default: @UnsafeVariance T): T = when (this) {
        is Success -> data
        else -> default
    }

    fun getOrThrow(): T = when (this) {
        is Success -> data
        is HttpError -> throw IOException("HTTP $code: $message ($url)")
        is NetworkError -> throw exception
        is LocalError -> throw IllegalStateException(message)
    }

    fun errorMessage(): String = when (this) {
        is Success -> ""
        is HttpError -> when (code) {
            401 -> "Ошибка авторизации (неверный логин/пароль)"
            403 -> "Нет прав доступа"
            404 -> "Файл или папка не найдены"
            413 -> "Файл слишком большой"
            507 -> "Сервер переполнен"
            in 500..599 -> "Ошибка сервера (HTTP $code)"
            else -> "HTTP $code: $message"
        }
        is NetworkError -> "Сетевая ошибка: ${exception.message}"
        is LocalError -> message
    }

    /** Трансформация данных при успехе */
    fun <R> map(transform: (T) -> R): WebDavResult<R> = when (this) {
        is Success -> Success(transform(data))
        is HttpError -> this
        is NetworkError -> this
        is LocalError -> this
    }

    companion object {
        fun <T> success(data: T): WebDavResult<T> = Success(data)
        fun httpError(code: Int, message: String = "", url: String = ""): WebDavResult<Nothing> =
            HttpError(code, message, url)
        fun networkError(e: IOException, url: String = ""): WebDavResult<Nothing> =
            NetworkError(e, url)
        fun localError(message: String): WebDavResult<Nothing> = LocalError(message)

        /** Обёртка для операций, возвращающих HTTP-код */
        fun fromHttpCode(code: Int, url: String = ""): WebDavResult<Int> = when {
            code in 200..299 -> Success(code)
            else -> HttpError(code, "HTTP $code", url)
        }
    }
}