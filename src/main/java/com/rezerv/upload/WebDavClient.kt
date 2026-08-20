package com.rezerv.upload

import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

object WebDavClient {
    private val clientRef = AtomicReference<OkHttpClient?>(null)
    private val currentAuthRef = AtomicReference<String?>(null)

    /** 0 = авто, 1 = только Basic, 2 = только Digest. Устанавливается из SecurePrefs. */
    @Volatile var defaultAuthType: Int = 0

    @Synchronized
    private fun getClient(user: String, pass: String, authType: Int): OkHttpClient {
        val authKey = "$user:$pass:$authType"
        clientRef.get()?.let { if (currentAuthRef.get() == authKey) return it }
        val builder = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(300, TimeUnit.SECONDS)
            .writeTimeout(300, TimeUnit.SECONDS)
            .followRedirects(true)          // ИСПРАВЛЕНО: редиректы включены
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
        }
        val newClient = builder.build()
        clientRef.get()?.dispatcher?.executorService?.shutdown()   // ИСПРАВЛЕНО: не копим клиенты
        clientRef.set(newClient)
        currentAuthRef.set(authKey)
        return newClient
    }

    private fun client(user: String, pass: String) = getClient(user, pass, defaultAuthType)

    fun options(url: String, user: String, pass: String): Pair<Int, String> {
        client(user, pass).newCall(Request.Builder().url(url).method("OPTIONS", null).build())
            .execute().use { r -> return Pair(r.code, "DAV: ${r.header("DAV") ?: "none"}, Allow: ${r.header("Allow") ?: "none"}") }
    }

    fun propfind(url: String, user: String, pass: String): String {
        // ИСПРАВЛЕНО: валидный XML
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
        val requestBody = object : RequestBody() {
            override fun contentType(): MediaType = "application/octet-stream".toMediaType()
            override fun contentLength(): Long = length
            override fun writeTo(sink: okio.BufferedSink) {
                val buffer = ByteArray(64 * 1024)
                inputStream.use { input ->
                    var n: Int
                    while (input.read(buffer).also { n = it } != -1) { sink.write(buffer, 0, n); sink.flush() }
                }
            }
        }
        client(user, pass).newCall(Request.Builder().url(url).put(requestBody).build()).execute().use { return it.code }
    }

    fun delete(url: String, user: String, pass: String): Int =
        client(user, pass).newCall(Request.Builder().url(url).delete().build()).execute().use { it.code }

    fun mkcol(url: String, user: String, pass: String): Int =
        client(user, pass).newCall(Request.Builder().url(url).method("MKCOL", null).build()).execute().use { it.code }

    fun head(url: String, user: String, pass: String): Int =
        client(user, pass).newCall(Request.Builder().url(url).head().build()).execute().use { it.code }

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
}