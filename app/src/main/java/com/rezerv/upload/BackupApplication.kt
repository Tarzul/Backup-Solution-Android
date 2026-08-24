package com.rezerv.upload

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Application класс с Hilt.
 * Точка входа для DI-контейнера.
 */
@HiltAndroidApp
class BackupApplication : Application() {
    // Hilt автоматически создаёт компоненты и управляет зависимостями
}