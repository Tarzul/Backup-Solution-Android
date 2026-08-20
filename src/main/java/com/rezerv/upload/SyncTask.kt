package com.rezerv.upload

data class SyncTask(
    val id: String = "",
    val name: String = "",
    val syncType: String = "two_way",
    val leftIsWebdav: Boolean = false,
    val leftLocalUri: String = "",
    val leftWebdavPath: String = "",
    val rightIsWebdav: Boolean = true,
    val rightLocalUri: String = "",
    val rightWebdavPath: String = "/",
    val scheduleEnabled: Boolean = false,
    val scheduleMode: String = "daily",
    val intervalValue: Int = 1,
    val hour: Int = 3,
    val minute: Int = 0,
    val weekDays: String = "",
    val monthDays: String = "",
    val useWifi: Boolean = true,
    val useMobile: Boolean = false,
    val onlyCharging: Boolean = false,
    val notifyOnSuccess: Boolean = false,
    val notifyOnError: Boolean = true,
    val lastRun: Long = 0L,
    val lastStatus: String = ""
) {
    fun syncTypeLabel(): String = when (syncType) {
        "two_way" -> "⇄ Двусторонняя"
        "to_left" -> "← В левую папку"
        "to_right" -> "→ В правую папку"
        else -> syncType
    }

    fun scheduleLabel(): String = when (scheduleMode) {
        "minutes" -> "каждые $intervalValue мин"
        "hourly" -> "каждые $intervalValue ч"
        "daily" -> String.format("ежедневно %02d:%02d", hour, minute)
        "weekly" -> String.format("еженедельно %02d:%02d", hour, minute)
        "monthly" -> String.format("ежемесячно %02d:%02d", hour, minute)
        else -> scheduleMode
    }

    fun isValid(): Boolean {
        val leftLocal = !leftIsWebdav
        val rightLocal = !rightIsWebdav
        if (leftLocal == rightLocal) return false
        if (leftLocal && leftLocalUri.isBlank()) return false
        if (rightLocal && rightLocalUri.isBlank()) return false
        if (leftIsWebdav && leftWebdavPath.isBlank()) return false
        if (rightIsWebdav && rightWebdavPath.isBlank()) return false
        return true
    }

    fun leftDescription(): String = if (leftIsWebdav) "☁ WebDAV: $leftWebdavPath" else "📱 Устройство: $leftLocalUri"
    fun rightDescription(): String = if (rightIsWebdav) "☁ WebDAV: $rightWebdavPath" else "📱 Устройство: $rightLocalUri"
}