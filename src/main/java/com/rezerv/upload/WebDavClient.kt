package com.rezerv.upload

import android.util.Base64
import android.util.Log
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

object WebDavClient {
    private const val TAG = "WebDavClient"

    /** 0 = авто, 1 = только Basic, 2 = только Digest. Устанавливается из SecurePrefs. */
    @Volatile var defaultAuthType: Int = 0

    // ИСПРАВЛЕНО: кэш клиентов по ключу. Клиенты БОЛЬШЕ не выключают друг друга —
    // Coil-клиент остаётся живым после создания клиента с кредами.
    private val clients = ConcurrentHashMap<String, OkHttpClient>()

    /** Клиент для Coil: без превентивной авторизации, но с Digest-аутентификатором,
     *  который берёт креды из Basic-заголовка запроса (его ставит адаптер). */
    val httpClient: OkHttpClient
        get() = getClient("", "", defaultAuthType, forCoil = true)

    private fun getClient(user: String, pass: String, authType: Int, forCoil: Boolean = false): OkHttpClient {
        val key = "$user|$pass|$authType|${if (forCoil) "coil" else "api"}"
        clients[key]?.let { return it }

        val builder = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(300, TimeUnit.SECONDS)
            .writeTimeout(300, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)

        if (user.isNotEmpty()) {
            // Превентивный Basic — только в режимах «авто» и «только Basic»
            if (authType != 2) {
                builder.addInterceptor { chain ->
                    chain.proceed(
                        chain.request().newBuilder()
                            .header("Authorization", Credentials.basic(user, pass))
                            .build()
                    )
                }
            }
            // Digest — только в режимах «авто» и «только Digest»
            if (authType != 1) {
                builder.authenticator(DigestAuthenticator(user, pass))
            } else {
                builder.authenticator { _, response ->
                    if (response.request.header("Authorization") != null) null
                    else response.request.newBuilder()
                        .header("Authorization", Credentials.basic(user, pass)).build()
                }
            }
        } else if (forCoil) {
            // НОВОЕ: Coil-клиент умеет Digest, декодируя креды из Basic-заголовка
            builder.authenticator(BasicHeaderDigestAuthenticator())
        }

        val client = builder.build()
        clients[key] = client

        // Не копим бесконечно: >4 — выключаем самый старый, кроме текущего
        if (clients.size > 4) {
            val oldest = clients.keys.firstOrNull { it != key }
            if (oldest != null) clients.remove(oldest)?.dispatcher?.executorService?.shutdown()
        }
        return client
    }

    private fun client(user: String, pass: String) = getClient(user, pass, defaultAuthType)

    // ==================== HTTP-методы WebDAV ====================

    fun options(url: String, user: String, pass: String): Pair<Int, String> {
        client(user, pass).newCall(Request.Builder().url(url).method("OPTIONS", null).build())
            .execute().use { r -> return Pair(r.code, "DAV: ${r.header("DAV") ?: "none"}, Allow: ${r.header("Allow") ?: "none"}") }
    }

    fun propfind(url: String, user: String, pass: String): String {
        val body = """<?xml version="1.0" encoding="utf-8"?>
<D:propfind xmlns:D="DAV:">
  <D:prop>
    <D:displayname/>
    <D:resourcetype/>
    <D:getcontentlength/>
    <D:getlastmodified/>
  </D:prop>
</D:propfind>"""
        val request = Request.Builder().url(url)
            .method("PROPFIND", body.toRequestBody("application/xml; charset=utf-8".toMediaType()))
            .header("Depth", "1").build()
        client(user, pass).newCall(request).execute().use { r ->
            if (!r.isSuccessful && r.code != 207) throw IOException("HTTP ${r.code}")
            return r.body?.string() ?: ""
        }
    }

    fun get(url: String, user: String, pass: String): Response =
        client(user, pass).newCall(Request.Builder().url(url).get().build()).execute()

    fun put(url: String, user: String, pass: String, inputStream: java.io.InputStream, length: Long): Int {

        val bytes = inputStream.use { it.readBytes() }
        val body = bytes.toRequestBody("application/octet-stream".toMediaType())
        client(user, pass).newCall(Request.Builder().url(url).put(body).build()).execute().use { r ->
            Log.d(TAG, "PUT -> HTTP ${r.code}: $url")
            return r.code
        }
    }

    fun delete(url: String, user: String, pass: String): Int =
        client(user, pass).newCall(Request.Builder().url(url).delete().build()).execute().use { return it.code }

    fun mkcol(url: String, user: String, pass: String): Int =
        client(user, pass).newCall(Request.Builder().url(url).method("MKCOL", null).build()).execute().use { return it.code }

    fun head(url: String, user: String, pass: String): Int =
        client(user, pass).newCall(Request.Builder().url(url).head().build()).execute().use { return it.code }

    // ==================== Digest (RFC 2617, MD5, qop=auth) ====================

    private class DigestAuthenticator(private val user: String, private val pass: String) : Authenticator {
        override fun authenticate(route: Route?, response: Response): Request? {
            val challenge = response.header("WWW-Authenticate") ?: return null
            if (!challenge.startsWith("Digest", true)) return null
            if (response.request.header("Authorization")?.startsWith("Digest", true) == true) return null
            val p = parse(challenge.substringAfter("Digest", ""))
            val realm = p["realm"] ?: return null
            val nonce = p["nonce"] ?: return null
            val qop = p["qop"]?.split(",")?.firstOrNull()?.trim()
            val opaque = p["opaque"]
            val req = response.request
            val uri = req.url.encodedPath.ifEmpty { "/" } + (req.url.encodedQuery?.let { "?$it" } ?: "")
            val ha1 = md5("$user:$realm:$pass")
            val ha2 = md5("${req.method}:$uri")
            val nc = "00000001"
            val cnonce = java.util.UUID.randomUUID().toString().replace("-", "")
            val resp = if (qop == "auth") md5("$ha1:$nonce:$nc:$cnonce:auth:$ha2") else md5("$ha1:$nonce:$ha2")
            val header = buildString {
                append("Digest username=\"").append(user).append("\", realm=\"").append(realm)
                append("\", nonce=\"").append(nonce).append("\", uri=\"").append(uri)
                append("\", response=\"").append(resp).append('"')
                if (qop == "auth") append(", qop=auth, nc=").append(nc).append(", cnonce=\"").append(cnonce).append('"')
                if (opaque != null) append(", opaque=\"").append(opaque).append('"')
                append(", algorithm=MD5")
            }
            return req.newBuilder().header("Authorization", header).build()
        }

        private fun parse(s: String): Map<String, String> {
            val map = mutableMapOf<String, String>()
            for (m in Regex("(\\w+)=(\"[^\"]*\"|[^,]*)").findAll(s)) {
                var v = m.groupValues[2].trim()
                if (v.length >= 2 && v.startsWith("\"") && v.endsWith("\"")) v = v.substring(1, v.length - 1)
                map[m.groupValues[1]] = v
            }
            return map
        }

        private fun md5(s: String) = java.security.MessageDigest.getInstance("MD5")
            .digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    // НОВОЕ: аутентификатор для Coil-клиента — берёт креды из Basic-заголовка запроса
    private class BasicHeaderDigestAuthenticator : Authenticator {
        override fun authenticate(route: Route?, response: Response): Request? {
            val basic = response.request.header("Authorization") ?: return null
            if (!basic.startsWith("Basic ", true)) return null
            val decoded = try {
                String(android.util.Base64.decode(basic.substring(6).trim(), android.util.Base64.DEFAULT))
            } catch (e: Exception) {
                return null
            }
            val idx = decoded.indexOf(':')
            if (idx <= 0) return null
            val user = decoded.substring(0, idx)
            val pass = decoded.substring(idx + 1)
            return DigestAuthenticator(user, pass).authenticate(route, response)
        }
    }
}