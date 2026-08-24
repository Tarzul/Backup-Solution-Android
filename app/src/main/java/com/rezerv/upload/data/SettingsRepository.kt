package com.rezerv.upload.data

import android.content.Context

/**
 * Интерфейс репозитория настроек (учётные данные WebDAV).
 */
interface SettingsRepository {
    fun saveCredentials(
        context: Context,
        server: String,
        user: String,
        pass: String,
        authType: Int = 0
    )
    fun loadCredentials(context: Context): Triple<String, String, String>
    fun loadAuthType(context: Context): Int
}