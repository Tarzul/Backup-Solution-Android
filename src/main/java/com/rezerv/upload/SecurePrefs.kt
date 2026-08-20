package com.rezerv.upload

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

object SecurePrefs {
    private const val TAG = "SecurePrefs"
    private const val PREFS_NAME = "webdav_settings_encrypted"

    @Volatile private var prefs: SharedPreferences? = null

    @Synchronized
    private fun getPrefs(context: Context): SharedPreferences? {
        prefs?.let { return it }
        return try {
            // В 1.0.0 используется MasterKeys.getOrCreate() -> String (alias)
            val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
            val p = EncryptedSharedPreferences.create(
                PREFS_NAME,           // 1) имя файла
                masterKeyAlias,       // 2) alias ключа (String)
                context,              // 3) context
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
            prefs = p
            p
        } catch (e: Exception) {
            Log.e(TAG, "Failed to init EncryptedSharedPreferences", e)
            null
        }
    }

    fun saveCredentials(context: Context, server: String, user: String, pass: String, authType: Int = 0) {
        getPrefs(context)?.edit()
            ?.putString("server", server)
            ?.putString("user", user)
            ?.putString("pass", pass)
            ?.putInt("auth_type", authType)
            ?.apply()
    }

    fun loadCredentials(context: Context): Triple<String, String, String> {
        val p = getPrefs(context) ?: return Triple("", "", "")
        return Triple(
            p.getString("server", "") ?: "",
            p.getString("user", "") ?: "",
            p.getString("pass", "") ?: ""
        )
    }

    fun loadAuthType(context: Context): Int =
        getPrefs(context)?.getInt("auth_type", 0) ?: 0
}