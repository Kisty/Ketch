package com.ketch

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import com.google.common.truth.Truth.assertThat
import com.ketch.internal.database.DatabaseInstance
import com.ketch.internal.database.DownloadDao
import com.ketch.internal.database.DownloadEntity
import com.ketch.internal.utils.FileUtil
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RetryResumptionTest {

    private lateinit var context: Context
    private lateinit var ketch: Ketch
    private val downloadDao = mockk<DownloadDao>(relaxed = true)

    @Before
    fun setUp() {
        // Reset Ketch singleton
        val field = Ketch::class.java.getDeclaredField("ketchInstance")
        field.isAccessible = true
        field.set(null, null)

        context = ApplicationProvider.getApplicationContext()
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
        
        mockkObject(DatabaseInstance)
        every { DatabaseInstance.getInstance(any()).downloadDao() } returns downloadDao

        ketch = Ketch.builder().build(context)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `download with same url and path reuses existing filename and ID`() = runBlocking {
        val url = "https://example.com/file.zip"
        val path = "/downloads"
        val originalFileName = "file.zip"
        
        val originalId = FileUtil.getUniqueId(url, path, originalFileName)

        val existingEntity = DownloadEntity(
            id = originalId,
            url = url,
            path = path,
            fileName = originalFileName,
            status = Status.FAILED.toString()
        )

        coEvery { downloadDao.findByUrlAndPath(url, path) } returns existingEntity
        coEvery { downloadDao.find(originalId) } returns existingEntity

        val newId = ketch.download(url = url, path = path, fileName = "ignored.zip")

        assertThat(newId).isEqualTo(originalId)
    }
}
