package com.mos.nira

import com.mos.nira.core.image.CaptureCleanup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class CaptureCleanupTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test
    fun onlyOldCapturesAreDeletedAndTheKeptOneStays() {
        val dir = tmp.newFolder("captures")
        val now = 10_000_000_000L
        val old = File(dir, "old.jpg").apply { writeText("x"); setLastModified(now - 25 * 3_600_000L) }
        val oldKept = File(dir, "kept.jpg").apply { writeText("x"); setLastModified(now - 30 * 3_600_000L) }
        val fresh = File(dir, "fresh.jpg").apply { writeText("x"); setLastModified(now - 1 * 3_600_000L) }

        assertEquals(1, CaptureCleanup.deleteOld(dir, now, keep = oldKept))
        assertFalse(old.exists())
        assertTrue(oldKept.exists())
        assertTrue(fresh.exists())
    }

    @Test
    fun onlyFilesInsideTheCapturesFolderCount() {
        val dir = tmp.newFolder("captures")
        assertTrue(CaptureCleanup.isCapture(File(dir, "a.jpg").path, dir))
        assertFalse(CaptureCleanup.isCapture(File(tmp.root, "artworks/a.jpg").path, dir))
        assertFalse(CaptureCleanup.isCapture(null, dir))
    }
}
