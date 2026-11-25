package com.mos.nira.core.image

import java.io.File

/**
 * Photos from the in-app garment camera are written to filesDir/captures. When the job is saved,
 * the photo is copied into the job's own storage (ArtworkImageProcessor.saveArtworkCopy), so the
 * capture is no longer needed. These helpers keep the folder from growing.
 */
object CaptureCleanup {
    const val DIR = "captures"

    /** A capture left behind (job never saved) is removed after this many hours. */
    const val MAX_AGE_HOURS = 24L

    /** True when [path] is a file directly inside [capturesDir]. */
    fun isCapture(path: String?, capturesDir: File): Boolean {
        if (path == null) return false
        val f = File(path)
        return f.parentFile?.canonicalPath == capturesDir.canonicalPath
    }

    /** Deletes captures older than [MAX_AGE_HOURS], except [keep]. Returns how many were deleted. */
    fun deleteOld(capturesDir: File, nowMillis: Long = System.currentTimeMillis(), keep: File? = null): Int {
        val limit = nowMillis - MAX_AGE_HOURS * 3_600_000L
        val keepPath = keep?.canonicalPath
        return capturesDir.listFiles().orEmpty()
            .filter { it.isFile && it.lastModified() < limit && it.canonicalPath != keepPath }
            .count { it.delete() }
    }
}
