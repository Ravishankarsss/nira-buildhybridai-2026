package com.mos.nira

import com.mos.nira.features.advice.JobSummary
import com.mos.nira.features.dashboard.DashboardModel
import com.mos.nira.features.dashboard.JobSummaryStore
import com.mos.nira.features.artwork.domain.ArtworkAnalysisRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale

/** Home shows each saved job's decision: the summary survives saving and reaches the recent list. */
class JobSummaryStoreTest {

    private val s = JobSummary("Discharge + digital print", 1, 50.4, "INR", true, 0, true, true)

    @Test
    fun summaryRoundTrips() {
        assertEquals(s, JobSummaryStore.decode(JobSummaryStore.encode(s)))
        val noCost = s.copy(perPc = null, screens = null)
        assertEquals(noCost, JobSummaryStore.decode(JobSummaryStore.encode(noCost)))
        assertNull(JobSummaryStore.decode("not json"))
    }

    @Test
    fun recentJobsCarryTheirSummary() {
        val rec = ArtworkAnalysisRecord("r1", "a.png", "content://x", null, 10, 10, 1, 5_000, emptyList())
        val other = rec.copy(id = "r2", timestamp = 4_000)
        val items = DashboardModel.recentAnalyses(listOf(rec, other), locale = Locale.US, summaries = mapOf("r1" to s))
        assertEquals(s, items.first { it.id == "r1" }.summary)
        assertNull(items.first { it.id == "r2" }.summary)
    }
}
