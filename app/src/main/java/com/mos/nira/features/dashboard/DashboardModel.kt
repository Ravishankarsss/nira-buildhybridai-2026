package com.mos.nira.features.dashboard

import com.mos.nira.features.artwork.domain.ArtworkAnalysisRecord
import com.mos.nira.features.artwork.domain.mainPrintHexes
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Top-level routes of the app.
 *
 * [BUILT] lists the routes that have a real screen registered in the NavHost in MainActivity.
 * The dashboard and bottom bar only show items whose route is in [BUILT], so nothing can lead
 * to an empty or missing destination. When a new screen is built: register it in the NavHost
 * and add its route here.
 */
object RootDestinations {
    const val DASHBOARD = "dashboard"
    const val ARTWORK_FLOW = "artwork_flow"
    /** Artwork flow opened at a given screen: artwork_flow_at/{start}. */
    const val ARTWORK_FLOW_AT = "artwork_flow_at"
    const val GARMENT_CAMERA = "garment_camera"
    const val SETTINGS = "settings"

    const val TECHNIQUE_LIBRARY = "technique_library"
    const val AI_ACTIVITY = "ai_activity"

    // Not built as a screen: Ask NIRA is on each job's decision card. Do not add to BUILT.
    const val ASK_NIRA = "ask_nira"

    val BUILT: Set<String> = setOf(DASHBOARD, ARTWORK_FLOW, ARTWORK_FLOW_AT, GARMENT_CAMERA, SETTINGS, TECHNIQUE_LIBRARY, AI_ACTIVITY)

    fun artworkFlowAt(start: String): String = "$ARTWORK_FLOW_AT/$start"
}

/** Screens inside the artwork flow that the dashboard can open directly. */
object ArtworkStart {
    const val PREVIEW = "artwork_preview"
    const val SHADE_LIBRARY = "artwork_shade_library"
    const val PRINT_REPORT = "artwork_print_report"
    /** The job page (summary bar and tabs). */
    const val JOB = "artwork_color_intelligence"
}

enum class DayPart { MORNING, AFTERNOON, EVENING }

/** Workspace cards in display order. route = the top-level route the card opens. */
enum class WorkspaceCard(val route: String) {
    PRINT_REPORT(RootDestinations.ARTWORK_FLOW_AT),
    SHADE_LIBRARY(RootDestinations.ARTWORK_FLOW_AT),
    TECHNIQUE_LIBRARY(RootDestinations.TECHNIQUE_LIBRARY),
    ASK_NIRA(RootDestinations.ASK_NIRA)
}

/** Bottom bar tabs in display order. */
enum class BottomTab(val route: String) {
    HOME(RootDestinations.DASHBOARD),
    ARTWORK(RootDestinations.ARTWORK_FLOW),
    ASK_NIRA(RootDestinations.ASK_NIRA),
    SETTINGS(RootDestinations.SETTINGS)
}

/** One row of the "Recent analyses" list. */
data class RecentAnalysisItem(
    val id: String,
    val fileName: String,
    val dateText: String,
    val colourCount: Int,
    val thumbnailPath: String?,
    val imageUri: String,
    val inputKind: String? = null,
    val mainHexes: List<String> = emptyList(),
    /** Method, screens and cost per piece, once the job page has worked them out. */
    val summary: com.mos.nira.features.advice.JobSummary? = null
)

object DashboardModel {

    const val RECENT_LIMIT = 3

    /** Greeting follows the device clock. hourOfDay is 0..23. */
    fun dayPart(hourOfDay: Int): DayPart = when (hourOfDay) {
        in 5..11 -> DayPart.MORNING
        in 12..16 -> DayPart.AFTERNOON
        else -> DayPart.EVENING
    }

    /** Only cards whose destination is built are shown. */
    fun visibleCards(built: Set<String> = RootDestinations.BUILT): List<WorkspaceCard> =
        WorkspaceCard.values().filter { it.route in built }

    /** Only tabs whose destination is built are shown. */
    fun visibleTabs(built: Set<String> = RootDestinations.BUILT): List<BottomTab> =
        BottomTab.values().filter { it.route in built }

    /** The newest saved analyses, newest first. Empty only when the history is empty. */
    fun recentAnalyses(
        saved: List<ArtworkAnalysisRecord>,
        limit: Int = RECENT_LIMIT,
        locale: Locale = Locale.getDefault(),
        summaries: Map<String, com.mos.nira.features.advice.JobSummary> = emptyMap()
    ): List<RecentAnalysisItem> {
        val format = SimpleDateFormat("d MMM yyyy, HH:mm", locale)
        return saved.sortedByDescending { it.timestamp }.take(limit).map { record ->
            RecentAnalysisItem(
                id = record.id,
                fileName = record.fileName,
                dateText = format.format(Date(record.timestamp)),
                colourCount = record.printColourCount,
                thumbnailPath = record.thumbnailPath,
                imageUri = record.imageUri,
                inputKind = record.inputKind,
                mainHexes = record.mainPrintHexes(),
                summary = summaries[record.id]
            )
        }
    }

    /** The analysis the Print Report card opens, or null when the user must pick artwork first. */
    fun latestAnalysis(saved: List<ArtworkAnalysisRecord>): ArtworkAnalysisRecord? =
        saved.maxByOrNull { it.timestamp }
}
