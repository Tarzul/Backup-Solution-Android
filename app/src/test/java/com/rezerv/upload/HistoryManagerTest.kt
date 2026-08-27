package com.rezerv.upload

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HistoryManagerTest {

    private lateinit var context: Context
    private var counter = 0L

    /** Свежие timestamp'ы, чтобы записи не попадали под stale-таймаут */
    private fun nextTime() = System.currentTimeMillis() + (++counter)

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        HistoryManager.clear(context)
    }

    // createLiveRecord создаёт running-запись
    @Test
    fun createLiveRecord_createsRunningRecord() {
        val t = nextTime()
        assertTrue(HistoryManager.createLiveRecord(context, t, "Тест", "user", "task-A"))
        val r = HistoryManager.getRecords(context).first()
        assertEquals("running", r.status)
        assertEquals("Тест", r.taskName)
        assertEquals("task-A", r.taskId)
    }

    // дубль по taskId блокируется
    @Test
    fun duplicateTaskId_isBlocked() {
        assertTrue(HistoryManager.createLiveRecord(context, nextTime(), "Тест", "user", "task-dup"))
        assertFalse(HistoryManager.createLiveRecord(context, nextTime(), "Тест", "user", "task-dup"))
        assertEquals(1, HistoryManager.getRecords(context).count { it.status == "running" })
    }

    // живая запись не помечается как сирота
    @Test
    fun liveRecord_notMarkedAsOrphan() {
        val t = nextTime()
        HistoryManager.createLiveRecord(context, t, "Живая", "user", "task-live")
        val r = HistoryManager.getRecords(context).first { it.time == t }
        assertEquals("running", r.status)
    }

    // осиротевшая запись помечается error с диагностикой
    @Test
    fun orphanRecord_markedAsErrorWithDiagnostics() {
        val t = nextTime()
        HistoryManager.addRecord(context, HistoryRecord(
            time = t, durationMs = 0, checked = 0, uploaded = 0, downloaded = 0,
            deleted = 0, errors = 0, status = "running", trigger = "user",
            liveStartedAt = t, liveLastUpdateAt = t
        ))
        val r = HistoryManager.getRecords(context).first { it.time == t }
        assertEquals("error", r.status)
        assertTrue(r.errors >= 1)
        assertTrue(r.errorsJson.contains("процесс завершён"))
    }

    // осиротевшая запись с файлом содержит имя файла в причине
    @Test
    fun orphanRecordWithFile_containsFileNameInReason() {
        val t = nextTime()
        HistoryManager.addRecord(context, HistoryRecord(
            time = t, durationMs = 0, checked = 1, uploaded = 0, downloaded = 0,
            deleted = 0, errors = 0, status = "running", trigger = "user",
            currentFileName = "video.mp4",
            liveStartedAt = t, liveLastUpdateAt = t
        ))
        val r = HistoryManager.getRecords(context).first { it.time == t }
        assertEquals("error", r.status)
        assertTrue(r.errorsJson.contains("прервано на файле: video.mp4"))
    }

    // finalizeRecord заменяет running на финальную
    @Test
    fun finalizeRecord_replacesRunningWithFinal() {
        val t = nextTime()
        HistoryManager.createLiveRecord(context, t, "Финал", "user", "task-fin")
        HistoryManager.finalizeRecord(context, HistoryRecord(
            time = t, durationMs = 5000, checked = 3, uploaded = 2, downloaded = 0,
            deleted = 0, errors = 0, status = "ok", trigger = "user",
            taskName = "Финал", taskId = "task-fin"
        ))
        val r = HistoryManager.getRecords(context).first { it.time == t }
        assertEquals("ok", r.status)
        assertEquals(3, r.checked)
        assertEquals(2, r.uploaded)
    }

    // лимит 50 записей соблюдается
    @Test
    fun limit50Records_isEnforced() {
        repeat(55) {
            HistoryManager.addRecord(context, HistoryRecord(
                time = nextTime(), durationMs = 1, checked = 0, uploaded = 0,
                downloaded = 0, deleted = 0, errors = 0, status = "ok", trigger = "test"
            ))
        }
        assertEquals(50, HistoryManager.getRecords(context).size)
    }

    // parseFiles корректно разбирает JSON
    @Test
    fun parseFiles_correctlyParsesJson() {
        val files = HistoryManager.parseFiles("""[{"n":"a.jpg","s":1024,"m":500,"d":"Справа"}]""")
        assertEquals(1, files.size)
        assertEquals("a.jpg", files[0].name)
        assertEquals(1024L, files[0].size)
    }
}