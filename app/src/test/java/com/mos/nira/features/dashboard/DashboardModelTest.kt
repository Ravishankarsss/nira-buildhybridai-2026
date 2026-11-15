package com.mos.nira.features.dashboard

import com.mos.nira.features.artwork.domain.ArtworkAnalysisRecord
import com.mos.nira.features.artwork.domain.DominantColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Locale

class DashboardModelTest {

    private fun projectFile(path: String): File =
        listOf(File(path), File("app/$path")).firstOrNull { it.exists() }
            ?: throw AssertionError("$path not found from ${File(".").absolutePath}")

    private fun stringResource(name: String): String {
        val xml = projectFile("src/main/res/values/strings.xml").readText()
        return Regex("<string name=\"$name\">(.*?)</string>").find(xml)?.groupValues?.get(1)
            ?: throw AssertionError("string resource $name is missing")
    }

    private fun colour(id: Int, garment: Boolean = false) = DominantColor(
        id = id, hex = "#112233", r = 17, g = 34, b = 51,
        labL = 10.0, labA = 0.0, labB = 0.0,
        pixelCount = 100, pixelPercentage = 10f, isGarmentBase = garment
    )

    private fun record(id: String, timestamp: Long, colours: Int = 2, withGarment: Boolean = false) =
        ArtworkAnalysisRecord(
            id = id,
            fileName = "$id.png",
            imageUri = "content://test/$id",
            thumbnailPath = "/data/thumbs/$id.webp",
            width = 100,
            height = 100,
            fileSizeBytes = 1000,
            timestamp = timestamp,
            palette = (1..colours).map { colour(it) } + if (withGarment) listOf(colour(99, garment = true)) else emptyList()
        )

    // ---------- greeting ----------

    @Test
    fun greeting_followsTheClock_forThreeTimesOfDay() {
        assertEquals(DayPart.MORNING, DashboardModel.dayPart(8))
        assertEquals(DayPart.AFTERNOON, DashboardModel.dayPart(14))
        assertEquals(DayPart.EVENING, DashboardModel.dayPart(20))

        // The text shown for each part of the day.
        assertEquals("Good morning", stringResource("greeting_morning"))
        assertEquals("Good afternoon", stringResource("greeting_afternoon"))
        assertEquals("Good evening", stringResource("greeting_evening"))
    }

    @Test
    fun greeting_boundaries() {
        assertEquals(DayPart.EVENING, DashboardModel.dayPart(0))
        assertEquals(DayPart.EVENING, DashboardModel.dayPart(4))
        assertEquals(DayPart.MORNING, DashboardModel.dayPart(5))
        assertEquals(DayPart.MORNING, DashboardModel.dayPart(11))
        assertEquals(DayPart.AFTERNOON, DashboardModel.dayPart(12))
        assertEquals(DayPart.AFTERNOON, DashboardModel.dayPart(16))
        assertEquals(DayPart.EVENING, DashboardModel.dayPart(17))
        assertEquals(DayPart.EVENING, DashboardModel.dayPart(23))
    }

    @Test
    fun headline_isTheAgreedText() {
        assertEquals(
            "Print engineering from artwork, privately on your phone.",
            stringResource("dashboard_headline")
        )
    }

    // ---------- history ----------

    @Test
    fun recentAnalyses_showTheLatestThreeSavedRecords_newestFirst() {
        val saved = listOf(
            record("a", timestamp = 1_000),
            record("b", timestamp = 4_000, colours = 5, withGarment = true),
            record("c", timestamp = 2_000),
            record("d", timestamp = 3_000)
        )
        val recent = DashboardModel.recentAnalyses(saved, locale = Locale.US)

        assertEquals(listOf("b", "d", "c"), recent.map { it.id })
        val first = recent.first()
        assertEquals("b.png", first.fileName)
        assertEquals(5, first.colourCount) // the garment colour is not a print colour
        assertEquals("/data/thumbs/b.webp", first.thumbnailPath)
        assertTrue(first.dateText.isNotBlank())
        assertEquals("b", DashboardModel.latestAnalysis(saved)?.id)
    }

    @Test
    fun recentAnalyses_fewerThanThree_showsAllOfThem() {
        val recent = DashboardModel.recentAnalyses(listOf(record("only", 5_000)))
        assertEquals(listOf("only"), recent.map { it.id })
    }

    @Test
    fun recentAnalyses_emptyOnlyWhenHistoryIsEmpty() {
        assertTrue(DashboardModel.recentAnalyses(emptyList()).isEmpty())
        assertNull(DashboardModel.latestAnalysis(emptyList()))
        assertFalse(DashboardModel.recentAnalyses(listOf(record("x", 1))).isEmpty())
    }

    // ---------- no card or tab leads to a missing destination ----------

    @Test
    fun everyVisibleCardAndTab_hasABuiltDestination() {
        val cards = DashboardModel.visibleCards()
        val tabs = DashboardModel.visibleTabs()

        assertTrue(cards.all { it.route in RootDestinations.BUILT })
        assertTrue(tabs.all { it.route in RootDestinations.BUILT })

        // Built today.
        assertEquals(listOf(WorkspaceCard.PRINT_REPORT, WorkspaceCard.SHADE_LIBRARY), cards)
        assertEquals(listOf(BottomTab.HOME, BottomTab.ARTWORK, BottomTab.SETTINGS), tabs)

        // Not built yet, so hidden. Lab and Profile no longer exist as tabs.
        assertFalse(WorkspaceCard.TECHNIQUE_LIBRARY in cards)
        assertFalse(WorkspaceCard.ASK_NIRA in cards)
        assertFalse(BottomTab.ASK_NIRA in tabs)
        assertEquals(setOf("HOME", "ARTWORK", "ASK_NIRA", "SETTINGS"), BottomTab.values().map { it.name }.toSet())
    }

    @Test
    fun cardsAndTabsAppear_whenTheirDestinationIsBuilt() {
        val built = RootDestinations.BUILT + RootDestinations.ASK_NIRA + RootDestinations.TECHNIQUE_LIBRARY
        assertEquals(WorkspaceCard.values().toList(), DashboardModel.visibleCards(built))
        assertEquals(BottomTab.values().toList(), DashboardModel.visibleTabs(built))
    }

    @Test
    fun everyBuiltRoute_isRegisteredInTheNavHost() {
        val mainActivity = projectFile("src/main/java/com/mos/nira/MainActivity.kt").readText()
        val names = mapOf(
            RootDestinations.DASHBOARD to "DASHBOARD",
            RootDestinations.ARTWORK_FLOW to "ARTWORK_FLOW",
            RootDestinations.ARTWORK_FLOW_AT to "ARTWORK_FLOW_AT",
            RootDestinations.GARMENT_CAMERA to "GARMENT_CAMERA",
            RootDestinations.SETTINGS to "SETTINGS"
        )
        assertEquals("update this test when BUILT changes", RootDestinations.BUILT, names.keys)
        for ((_, constant) in names) {
            assertTrue(
                "NavHost has no screen for RootDestinations.$constant",
                Regex("composable\\(RootDestinations\\.$constant[ )+]").containsMatchIn(mainActivity)
            )
        }
        // Routes that are not built must not be registered or linked.
        assertFalse(mainActivity.contains("composable(RootDestinations.ASK_NIRA"))
        assertFalse(mainActivity.contains("composable(RootDestinations.TECHNIQUE_LIBRARY"))
    }

    @Test
    fun artworkStartScreens_existInTheArtworkFlow() {
        val navHost = projectFile("src/main/java/com/mos/nira/features/artwork/navigation/ArtworkNavHost.kt").readText()
        for (route in listOf(ArtworkStart.PREVIEW, ArtworkStart.SHADE_LIBRARY, ArtworkStart.PRINT_REPORT)) {
            assertTrue("artwork flow has no destination $route", navHost.contains("\"$route\""))
        }
    }
}
