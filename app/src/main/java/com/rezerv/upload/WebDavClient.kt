package com.rezerv.upload

import android.util.Log
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object WebDavClient {
    private const val TAG = "WebDavClient"

    // ==================== Единый клиент (singleton) ====================
    val httpClient: OkHttpClient = OkHttpClient.Builder()
        // Таймауты
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.MINUTES)   // большие файлы
        .writeTimeout(10, TimeUnit.MINUTES)
        .callTimeout(0, TimeUnit.MINUTES)    // без общего лимита (долгие загрузки)
        // Connection pooling
        .connectionPool(ConnectionPool(5, 5, TimeUnit.MINUTES))
        // Redirects
        .followRedirects(true)
        .followSslRedirects(true)
        // Retry interceptor
        .addInterceptor(RetryInterceptor(maxRetries = 3))
        // Auth interceptor (добавляется динамически)
        .addInterceptor(AuthInterceptor())
        .build()

    // ==================== Auth Interceptor ====================
    private class AuthInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            // Credentials передаются через header, добавленный вызывающим кодом
            return chain.proceed(request)
        }
    }

    // ==================== Retry Interceptor ====================
    private class RetryInterceptor(private val maxRetries: Int = 3) : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            var lastException: IOException? = null
            val request = chain.request()

            for (attempt in 0..maxRetries) {
                try {
                    val response = chain.proceed(request)
                    // Retry только на 5xx ошибки сервера
                    if (response.isSuccessful || response.code in 400..499) {
                        return response
                    }
                    // 5xx — пробуем снова
                    Log.w(TAG, "HTTP ${response.code} (попытка ${attempt + 1}/$maxRetries)")
                    response.close()
                    if (attempt < maxRetries) Thread.sleep((1000L * (attempt + 1))) // backoff
                } catch (e: IOException) {
                    lastException = e
                    Log.w(TAG, "IOException (попытка ${attempt + 1}/$maxRetries): ${e.message}")
                    if (attempt < maxRetries) Thread.sleep((1000L * (attempt + 1)))
                }
            }
            throw lastException ?: IOException("Max retries exceeded for ${request.url}")
        }
    }

    // ==================== Утилиты ====================
    private fun authHeader(user: String, pass: String): String =
        "Basic " + android.util.Base64.encodeToString("$user:$pass".toByteArray(), android.util.Base64.NO_WRAP)

    private fun Request.Builder.withAuth(user: String, pass: String): Request.Builder =
        addHeader("Authorization", authHeader(user, pass))

    // ==================== Асинхронные suspend-функции ====================

    /** Выполняет запрос асинхронно, возвращает Response. Вызывающий ОБЯЗАН закрыть body. */
    private suspend fun executeAsync(request: Request): Response =
        suspendCancellableCoroutine { continuation ->
            val call = httpClient.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }

            call.enqueue(object : Callback {
                override fun onResponse(call: Call, response: Response) {
                    if (continuation.isActive) continuation.resume(response)
                }

                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) continuation.resumeWithException(e)
                }
            })
        }

    // ==================== WebDAV-операции ====================

    suspend fun options(server: String, user: String, pass: String): Pair<Int, String> {
        val request = Request.Builder()
            .url(server)
            .method("OPTIONS", null)
            .withAuth(user, pass)
            .build()
        return executeAsync(request).use { response ->
            val dav = response.headers["DAV"] ?: ""
            response.code to dav
        }
    }

    suspend fun propfind(server: String, user: String, pass: String): String {
        val body = """<?xml version="1.0" encoding="utf-8"?>
            <D:propfind xmlns:D="DAV:">
                <D:prop>
                    <D:displayname/>
                    <D:getcontentlength/>
                    <D:resourcetype/>
                    <D:getlastmodified/>
                </D:prop>
            </D:propfind>""".trimIndent()

        val request = Request.Builder()
            .url(server)
            .method("PROPFIND", body.toRequestBody("application/xml; charset=utf-8".toMediaType()))
            .addHeader("Depth", "1")
            .withAuth(user, pass)
            .build()

        return executeAsync(request).use { response ->
            if (!response.isSuccessful) {
                throw IOException("PROPFIND failed: HTTP ${response.code}")
            }
            response.body?.string() ?: ""
        }
    }

    /** GET — возвращает Response. Вызывающий ОБЯЗАН закрыть response.body. */
    suspend fun get(url: String, user: String, pass: String): Response {
        val request = Request.Builder()
            .url(url)
            .get()
            .withAuth(user, pass)
            .build()
        return executeAsync(request)
    }

    /** PUT — загружает InputStream, возвращает HTTP-код. */
    suspend fun put(url: String, user: String, pass: String, inputStream: InputStream): Int {
        val requestBody = object : RequestBody() {
            override fun contentType() = "application/octet-stream".toMediaType()
            override fun isOneShot() = true  // OkHttp не будет буферизовать
            override fun writeTo(sink: okio.BufferedSink) {
                val buffer = ByteArray(64 * 1024)
                var bytesRead: Int
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    sink.write(buffer, 0, bytesRead)
                }
            }
        }

        val request = Request.Builder()
            .url(url)
            .put(requestBody)
            .withAuth(user, pass)
            .build()

        return executeAsync(request).use { response ->
            Log.d(TAG, "PUT -> HTTP ${response.code}: $url")
            response.code
        }
    }

    suspend fun delete(url: String, user: String, pass: String): Int {
        val request = Request.Builder()
            .url(url)
            .delete()
            .withAuth(user, pass)
            .build()
        return executeAsync(request).use { it.code }
    }

    suspend fun mkcol(url: String, user: String, pass: String): Int {
        val request = Request.Builder()
            .url(url)
            .method("MKCOL", null)
            .withAuth(user, pass)
            .build()
        return executeAsync(request).use { it.code }
    }

    suspend fun head(url: String, user: String, pass: String): Int {
        val request = Request.Builder()
            .url(url)
            .head()
            .withAuth(user, pass)
            .build()
        return executeAsync(request).use { it.code }
    }
}