package com.arflix.tv.data.local

import android.app.Application
import android.content.Context
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, manifest = Config.NONE)
@ConscryptMode(ConscryptMode.Mode.OFF)
class LegacyDataCleanupTest {
    @Test fun removesLegacyStorageOnceAndPreservesPlaybackData() {
        val context: Context = RuntimeEnvironment.getApplication()
        val directory = File(context.filesDir, LegacyDataCleanup.files.first()).apply { mkdirs() }
        val oldCache = File(directory, "channels.bin").apply { writeText("legacy") }
        val preference = LegacyDataCleanup.sharedPreferences.first()
        context.getSharedPreferences(preference, Context.MODE_PRIVATE).edit().putString("token", "old").commit()
        val database = LegacyDataCleanup.databases.first()
        context.openOrCreateDatabase(database, Context.MODE_PRIVATE, null).close()
        val oldSchedule = File(context.cacheDir, LegacyDataCleanup.cacheFiles.first()).apply { writeText("old") }
        val progress = File(context.filesDir, "home_continue_watching_current.json").apply { writeText("saved") }
        val playback = File(context.cacheDir, "media3_playback_cache/segment").apply {
            parentFile?.mkdirs()
            writeText("stream")
        }

        LegacyDataCleanup.runOnce(context)

        assertFalse(oldCache.exists())
        assertFalse(oldSchedule.exists())
        assertFalse(context.getDatabasePath(database).exists())
        assertTrue(context.getSharedPreferences(preference, Context.MODE_PRIVATE).all.isEmpty())
        assertTrue(progress.exists())
        assertTrue(playback.exists())
        directory.mkdirs()
        oldCache.writeText("created after cleanup")
        LegacyDataCleanup.runOnce(context)
        assertTrue(oldCache.exists())
    }
}
