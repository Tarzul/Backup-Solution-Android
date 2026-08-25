package com.rezerv.upload.utils

import java.util.regex.Pattern

/**
 * Утилиты валидации пользовательского ввода.
 */
object Validators {

    // ==================== WebDAV URL ====================
    
    private val URL_PATTERN = Pattern.compile(
        "^https?://[a-zA-Z0-9.-]+(:[0-9]+)?(/[^\\s]*)?$"
    )
    
    /**
     * Проверяет корректность URL WebDAV сервера.
     * @return null если OK, иначе сообщение об ошибке
     */
    fun validateServerUrl(url: String): String? {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) return "Введите адрес сервера"
        if (trimmed.length > 500) return "URL слишком длинный (макс. 500 символов)"
        if (!URL_PATTERN.matcher(trimmed).matches()) {
            return "Некорректный URL. Пример: https://cloud.example.com/remote.php/webdav"
        }
        return null
    }

    // ==================== Логин и пароль ====================
    
    fun validateUsername(username: String): String? {
        val trimmed = username.trim()
        if (trimmed.isEmpty()) return "Введите имя пользователя"
        if (trimmed.length > 200) return "Имя слишком длинное (макс. 200 символов)"
        if (trimmed.contains("\n") || trimmed.contains("\r")) {
            return "Имя не должно содержать переносы строк"
        }
        return null
    }

    fun validatePassword(password: String): String? {
        if (password.isEmpty()) return "Введите пароль"
        if (password.length > 500) return "Пароль слишком длинный (макс. 500 символов)"
        return null
    }

    // ==================== Имя задания ====================
    
    private val FORBIDDEN_IN_NAME = Regex("[/\\\\:*?\"<>|\\x00-\\x1F]")
    
    fun validateTaskName(name: String): String? {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return "Введите имя задания"
        if (trimmed.length < 2) return "Имя слишком короткое (мин. 2 символа)"
        if (trimmed.length > 100) return "Имя слишком длинное (макс. 100 символов)"
        if (FORBIDDEN_IN_NAME.containsMatchIn(trimmed)) {
            return "Имя содержит недопустимые символы: / \\ : * ? \" < > |"
        }
        if (trimmed.startsWith(".") || trimmed.endsWith(".")) {
            return "Имя не должно начинаться или заканчиваться точкой"
        }
        return null
    }

    // ==================== Имя папки ====================
    
    private val FORBIDDEN_FOLDER_NAMES = setOf(
        ".", "..", "con", "prn", "aux", "nul",
        "com1", "com2", "com3", "com4", "com5", "com6", "com7", "com8", "com9",
        "lpt1", "lpt2", "lpt3", "lpt4", "lpt5", "lpt6", "lpt7", "lpt8", "lpt9"
    )
    
    fun validateFolderName(name: String): String? {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return "Введите имя папки"
        if (trimmed.length > 255) return "Имя слишком длинное (макс. 255 символов)"
        if (FORBIDDEN_IN_NAME.containsMatchIn(trimmed)) {
            return "Имя содержит недопустимые символы: / \\ : * ? \" < > |"
        }
        if (trimmed.lowercase() in FORBIDDEN_FOLDER_NAMES) {
            return "Это имя зарезервировано системой"
        }
        if (trimmed.startsWith(".") || trimmed.endsWith(".")) {
            return "Имя не должно начинаться или заканчиваться точкой"
        }
        if (trimmed.endsWith(" ")) {
            return "Имя не должно заканчиваться пробелом"
        }
        return null
    }

    // ==================== Расписание ====================
    
    fun validateIntervalMinutes(value: Int): String? {
        if (value < 1) return "Минимум 1 минута"
        if (value > 1440) return "Максимум 1440 минут (24 часа)"
        return null
    }

    fun validateIntervalHours(value: Int): String? {
        if (value < 1) return "Минимум 1 час"
        if (value > 168) return "Максимум 168 часов (7 дней)"
        return null
    }

    fun validateTime(hour: Int, minute: Int): String? {
        if (hour !in 0..23) return "Некорректный час"
        if (minute !in 0..59) return "Некорректная минута"
        return null
    }

    fun validateWeekDays(weekDays: String): String? {
        if (weekDays.isBlank()) return "Выберите хотя бы один день недели"
        val days = weekDays.split(",").mapNotNull { it.trim().toIntOrNull() }
        if (days.isEmpty()) return "Некорректный формат дней"
        if (days.any { it !in 1..7 }) return "Дни должны быть в диапазоне 1-7"
        return null
    }

    fun validateMonthDays(monthDays: String): String? {
        if (monthDays.isBlank()) return "Выберите хотя бы одно число"
        val days = monthDays.split(",").mapNotNull { it.trim().toIntOrNull() }
        if (days.isEmpty()) return "Некорректный формат чисел"
        if (days.any { it !in 1..31 }) return "Числа должны быть в диапазоне 1-31"
        return null
    }

    // ==================== Путь на сервере ====================
    
    fun validateWebDavPath(path: String): String? {
        if (path.isEmpty()) return "Введите путь"
        if (!path.startsWith("/")) return "Путь должен начинаться с /"
        if (path.length > 1000) return "Путь слишком длинный (макс. 1000 символов)"
        if (path.contains("..")) return "Путь не должен содержать .."
        if (path.contains("//")) return "Путь не должен содержать //"
        return null
    }
}