package com.rezerv.upload.data.impl

import android.content.Context
import com.rezerv.upload.SecurePrefs
import com.rezerv.upload.data.SettingsRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Реализация SettingsRepository через существующий SecurePrefs.
 */
@Singleton
class SettingsRepositoryImpl @Inject constructor() : SettingsRepository {

    override fun saveCredentials(
        context: Context,
        server: String,
        user: String,
        pass: String,
        authType: Int
    ) = SecurePrefs.saveCredentials(context, server, user, pass, authType)

    override fun loadCredentials(context: Context): Triple<String, String, String> =
        SecurePrefs.loadCredentials(context)

    override fun loadAuthType(context: Context): Int =
        SecurePrefs.loadAuthType(context)
}