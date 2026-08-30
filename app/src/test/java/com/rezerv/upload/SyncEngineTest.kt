package com.rezerv.upload

class SyncEngineTest {

    @Mock
    private lateinit var mockTask: SyncTask

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
    }

    @Test
    fun `test SyncTask isValid with valid two-way sync`() {
        val task = SyncTask(
            id = "test-1",
            name = "Test Task",
            syncType = "two_way",
            leftIsWebdav = false,
            leftLocalUri = "content://local/path",
            leftWebdavPath = "",
            rightIsWebdav = true,
            rightLocalUri = "",
            rightWebdavPath = "/webdav/path"
        )

        assertTrue("Task should be valid", task.isValid())
    }

    @Test
    fun `test SyncTask isValid with invalid both local`() {
        val task = SyncTask(
            id = "test-2",
            name = "Invalid Task",
            syncType = "two_way",
            leftIsWebdav = false,
            leftLocalUri = "content://local/path",
            leftWebdavPath = "",
            rightIsWebdav = false,  // Обе стороны локальные - невалидно
            rightLocalUri = "content://local/path2",
            rightWebdavPath = ""
        )

        assertFalse("Task should be invalid when both sides are local", task.isValid())
    }

    @Test
    fun `test SyncTask isValid with missing local path`() {
        val task = SyncTask(
            id = "test-3",
            name = "Missing Path Task",
            syncType = "to_right",
            leftIsWebdav = false,
            leftLocalUri = "",  // Пустой локальный путь
            leftWebdavPath = "",
            rightIsWebdav = true,
            rightLocalUri = "",
            rightWebdavPath = "/webdav/path"
        )

        assertFalse("Task should be invalid when local path is empty", task.isValid())
    }

    @Test
    fun `test SyncTask isValid with missing webdav path`() {
        val task = SyncTask(
            id = "test-4",
            name = "Missing WebDAV Path",
            syncType = "to_left",
            leftIsWebdav = true,
            leftLocalUri = "",
            leftWebdavPath = "",  // Пустой WebDAV путь
            rightIsWebdav = false,
            rightLocalUri = "content://local/path",
            rightWebdavPath = ""
        )

        assertFalse("Task should be invalid when WebDAV path is empty", task.isValid())
    }

    @Test
    fun `test syncTypeLabel for two_way`() {
        val task = SyncTask(syncType = "two_way")
        assertEquals("⇄ Двусторонняя", task.syncTypeLabel())
    }

    @Test
    fun `test syncTypeLabel for to_right`() {
        val task = SyncTask(syncType = "to_right")
        assertEquals("→ В правую папку", task.syncTypeLabel())
    }

    @Test
    fun `test syncTypeLabel for to_left`() {
        val task = SyncTask(syncType = "to_left")
        assertEquals("← В левую папку", task.syncTypeLabel())
    }

    @Test
    fun `test scheduleLabel for daily`() {
        val task = SyncTask(
            scheduleMode = "daily",
            hour = 14,
            minute = 30
        )
        assertEquals("ежедневно 14:30", task.scheduleLabel())
    }

    @Test
    fun `test scheduleLabel for hourly`() {
        val task = SyncTask(
            scheduleMode = "hourly",
            intervalValue = 2
        )
        assertEquals("каждые 2 ч", task.scheduleLabel())
    }

    @Test
    fun `test scheduleLabel for minutes`() {
        val task = SyncTask(
            scheduleMode = "minutes",
            intervalValue = 15
        )
        assertEquals("каждые 15 мин", task.scheduleLabel())
    }

    @Test
    fun `test leftDescription for WebDAV`() {
        val task = SyncTask(
            leftIsWebdav = true,
            leftWebdavPath = "/remote/folder"
        )
        assertEquals("☁ WebDAV: /remote/folder", task.leftDescription())
    }

    @Test
    fun `test leftDescription for local`() {
        val task = SyncTask(
            leftIsWebdav = false,
            leftLocalUri = "content://local/folder"
        )
        assertEquals("📱 Устройство: content://local/folder", task.leftDescription())
    }

    @Test
    fun `test rightDescription for WebDAV`() {
        val task = SyncTask(
            rightIsWebdav = true,
            rightWebdavPath = "/backup"
        )
        assertEquals("☁ WebDAV: /backup", task.rightDescription())
    }

    @Test
    fun `test rightDescription for local`() {
        val task = SyncTask(
            rightIsWebdav = false,
            rightLocalUri = "content://storage/photos"
        )
        assertEquals("📱 Устройство: content://storage/photos", task.rightDescription())
    }
}