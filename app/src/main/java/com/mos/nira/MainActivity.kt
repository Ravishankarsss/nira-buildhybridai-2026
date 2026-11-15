package com.mos.nira

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import coil.compose.AsyncImage
import com.mos.nira.features.artwork.navigation.ArtworkNavHost
import com.mos.nira.features.artwork.presentation.ArtworkViewModel
import com.mos.nira.features.camera.GarmentCameraScreen
import com.mos.nira.features.dashboard.ArtworkStart
import com.mos.nira.features.dashboard.BottomTab
import com.mos.nira.features.dashboard.DashboardModel
import com.mos.nira.features.dashboard.DayPart
import com.mos.nira.features.dashboard.RecentAnalysisItem
import com.mos.nira.features.dashboard.RootDestinations
import com.mos.nira.features.dashboard.WorkspaceCard
import com.mos.nira.features.settings.SettingsScreen
import com.mos.nira.ui.theme.NIRATheme
import java.io.File
import java.util.Calendar

private val Navy = Color(0xFF0B1020)
private val CardColor = Color(0xFF171F35)
private val Violet = Color(0xFF8B7CFF)
private val Cyan = Color(0xFF55D6E8)
private val White = Color(0xFFF5F6FF)
private val Muted = Color(0xFF9BA6C0)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            NIRATheme {
                NIRAAppRoot()
            }
        }
    }
}

@Composable
fun NIRAAppRoot() {
    val rootNavController = rememberNavController()
    // One view model for the dashboard and the artwork flow, so both see the same saved history.
    val artworkViewModel: ArtworkViewModel = viewModel()
    val uiState by artworkViewModel.uiState.collectAsState()
    val navBackStackEntry by rootNavController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: RootDestinations.DASHBOARD

    // Gallery picker shared by "Photograph a garment" and the Print Report card.
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            artworkViewModel.onImageSelected(uri)
            rootNavController.navigate(RootDestinations.artworkFlowAt(ArtworkStart.PREVIEW))
        }
    }

    Scaffold(
        containerColor = Navy,
        bottomBar = {
            if (currentRoute != RootDestinations.GARMENT_CAMERA) {
                NIRABottomBar(
                    modifier = Modifier,
                    tabs = DashboardModel.visibleTabs(),
                    currentRoute = currentRoute,
                    onNavigate = { route ->
                        if (route != currentRoute) {
                            rootNavController.navigate(route) {
                                popUpTo(RootDestinations.DASHBOARD) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    },
                )
            }
        },
    ) { padding ->
        NavHost(
            navController = rootNavController,
            startDestination = RootDestinations.DASHBOARD,
            modifier = Modifier.padding(padding),
        ) {
            composable(RootDestinations.DASHBOARD) {
                // Re-read the saved history each time the dashboard is shown.
                LaunchedEffect(Unit) { artworkViewModel.loadSavedAnalyses() }

                val saved = uiState.savedAnalyses
                NIRADashboard(
                    modifier = Modifier,
                    recent = DashboardModel.recentAnalyses(saved),
                    latestFileName = DashboardModel.latestAnalysis(saved)?.fileName,
                    onAnalyseArtwork = {
                        rootNavController.navigate(RootDestinations.ARTWORK_FLOW)
                    },
                    onTakePhoto = {
                        rootNavController.navigate(RootDestinations.GARMENT_CAMERA)
                    },
                    onChooseFromGallery = { galleryLauncher.launch("image/*") },
                    onOpenCard = { card ->
                        when (card) {
                            WorkspaceCard.PRINT_REPORT -> {
                                val latest = DashboardModel.latestAnalysis(saved)
                                if (latest != null) {
                                    artworkViewModel.openAnalysisRecord(latest)
                                    rootNavController.navigate(RootDestinations.artworkFlowAt(ArtworkStart.PRINT_REPORT))
                                } else {
                                    galleryLauncher.launch("image/*")
                                }
                            }
                            WorkspaceCard.SHADE_LIBRARY ->
                                rootNavController.navigate(RootDestinations.artworkFlowAt(ArtworkStart.SHADE_LIBRARY))
                            // Not built yet: these cards are never shown (see RootDestinations.BUILT).
                            WorkspaceCard.TECHNIQUE_LIBRARY, WorkspaceCard.ASK_NIRA -> Unit
                        }
                    },
                    onOpenRecent = { id ->
                        saved.firstOrNull { it.id == id }?.let { record ->
                            artworkViewModel.openAnalysisRecord(record)
                            rootNavController.navigate(RootDestinations.artworkFlowAt(ArtworkStart.PRINT_REPORT))
                        }
                    },
                )
            }

            composable(RootDestinations.ARTWORK_FLOW) {
                val artworkNavController = rememberNavController()
                ArtworkNavHost(navController = artworkNavController, viewModel = artworkViewModel)
            }

            composable(RootDestinations.ARTWORK_FLOW_AT + "/{start}") { entry ->
                val start = entry.arguments?.getString("start") ?: ArtworkStart.PREVIEW
                val artworkNavController = rememberNavController()
                ArtworkNavHost(
                    navController = artworkNavController,
                    viewModel = artworkViewModel,
                    startDestination = start,
                    onExit = { rootNavController.popBackStack() },
                )
            }

            composable(RootDestinations.GARMENT_CAMERA) {
                GarmentCameraScreen(
                    onPhotoCaptured = { uri ->
                        artworkViewModel.onImageSelected(uri)
                        rootNavController.navigate(RootDestinations.artworkFlowAt(ArtworkStart.PREVIEW)) {
                            popUpTo(RootDestinations.GARMENT_CAMERA) { inclusive = true }
                        }
                    },
                    onBack = { rootNavController.popBackStack() },
                )
            }

            composable(RootDestinations.SETTINGS) {
                SettingsScreen(savedAnalysesCount = uiState.savedAnalyses.size)
            }
        }
    }
}

@Composable
fun NIRADashboard(
    modifier: Modifier = Modifier,
    dayPart: DayPart = remember { DashboardModel.dayPart(Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) },
    recent: List<RecentAnalysisItem> = emptyList(),
    latestFileName: String? = null,
    cards: List<WorkspaceCard> = DashboardModel.visibleCards(),
    onAnalyseArtwork: () -> Unit = {},
    onTakePhoto: () -> Unit = {},
    onChooseFromGallery: () -> Unit = {},
    onOpenCard: (WorkspaceCard) -> Unit = {},
    onOpenRecent: (String) -> Unit = {},
) {
    var showPhotoSource by remember { mutableStateOf(false) }

    if (showPhotoSource) {
        AlertDialog(
            onDismissRequest = { showPhotoSource = false },
            containerColor = CardColor,
            title = { Text(stringResource(R.string.photo_source_title), color = White) },
            text = { Text(stringResource(R.string.photo_source_message), color = Muted) },
            confirmButton = {
                TextButton(onClick = {
                    showPhotoSource = false
                    onTakePhoto()
                }) { Text(stringResource(R.string.photo_source_camera), color = Violet) }
            },
            dismissButton = {
                TextButton(onClick = {
                    showPhotoSource = false
                    onChooseFromGallery()
                }) { Text(stringResource(R.string.photo_source_gallery), color = Cyan) }
            },
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Navy)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        Spacer(modifier = Modifier.height(22.dp))

        // Brand header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(
                    text = stringResource(R.string.app_name),
                    color = White,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 3.sp,
                )
                Text(
                    text = stringResource(R.string.brand_tagline),
                    color = Violet,
                    fontSize = 10.sp,
                    letterSpacing = 2.sp,
                )
            }

            Surface(
                shape = CircleShape,
                color = CardColor,
            ) {
                Text(
                    text = "N",
                    modifier = Modifier.padding(14.dp),
                    color = Cyan,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Greeting follows the device clock
        Text(
            text = stringResource(
                when (dayPart) {
                    DayPart.MORNING -> R.string.greeting_morning
                    DayPart.AFTERNOON -> R.string.greeting_afternoon
                    DayPart.EVENING -> R.string.greeting_evening
                },
            ).uppercase(),
            color = Muted,
            fontSize = 11.sp,
            letterSpacing = 2.sp,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = stringResource(R.string.dashboard_headline),
            color = White,
            fontSize = 26.sp,
            lineHeight = 34.sp,
            fontWeight = FontWeight.Bold,
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Two primary actions
        PrimaryAction(
            symbol = "◈",
            title = stringResource(R.string.action_analyse_artwork),
            hint = stringResource(R.string.action_analyse_artwork_hint),
            accent = Violet,
            highlighted = true,
            onClick = onAnalyseArtwork,
        )

        Spacer(modifier = Modifier.height(12.dp))

        PrimaryAction(
            symbol = "◉",
            title = stringResource(R.string.action_photograph_garment),
            hint = stringResource(R.string.action_photograph_garment_hint),
            accent = Cyan,
            highlighted = false,
            onClick = { showPhotoSource = true },
        )

        // Workspace cards: only destinations that are built
        if (cards.isNotEmpty()) {
            Spacer(modifier = Modifier.height(30.dp))
            SectionLabel(stringResource(R.string.section_workspace))
            Spacer(modifier = Modifier.height(14.dp))

            cards.chunked(2).forEachIndexed { rowIndex, rowCards ->
                if (rowIndex > 0) Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    rowCards.forEach { card ->
                        WorkspaceTile(
                            symbol = when (card) {
                                WorkspaceCard.PRINT_REPORT -> "▤"
                                WorkspaceCard.SHADE_LIBRARY -> "◐"
                                WorkspaceCard.TECHNIQUE_LIBRARY -> "✧"
                                WorkspaceCard.ASK_NIRA -> "✦"
                            },
                            title = stringResource(
                                when (card) {
                                    WorkspaceCard.PRINT_REPORT -> R.string.card_print_report
                                    WorkspaceCard.SHADE_LIBRARY -> R.string.card_shade_library
                                    WorkspaceCard.TECHNIQUE_LIBRARY -> R.string.card_technique_library
                                    WorkspaceCard.ASK_NIRA -> R.string.card_ask_nira
                                },
                            ),
                            subtitle = when (card) {
                                WorkspaceCard.PRINT_REPORT ->
                                    if (latestFileName != null) {
                                        stringResource(R.string.card_print_report_latest, latestFileName)
                                    } else {
                                        stringResource(R.string.card_print_report_pick)
                                    }
                                WorkspaceCard.SHADE_LIBRARY -> stringResource(R.string.card_shade_library_hint)
                                WorkspaceCard.TECHNIQUE_LIBRARY -> stringResource(R.string.card_technique_library_hint)
                                WorkspaceCard.ASK_NIRA -> stringResource(R.string.card_ask_nira_hint)
                            },
                            accent = if (card.ordinal % 2 == 0) Violet else Cyan,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onOpenCard(card) },
                        )
                    }
                    if (rowCards.size == 1) Spacer(modifier = Modifier.weight(1f))
                }
            }
        }

        // Recent analyses from the saved history
        Spacer(modifier = Modifier.height(30.dp))
        SectionLabel(stringResource(R.string.section_recent))
        Spacer(modifier = Modifier.height(12.dp))

        if (recent.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = CardColor),
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = stringResource(R.string.recent_empty_title),
                        color = White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.recent_empty_message),
                        color = Muted,
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                    )
                }
            }
        } else {
            recent.forEachIndexed { index, item ->
                if (index > 0) Spacer(modifier = Modifier.height(10.dp))
                RecentAnalysisCard(item = item, onClick = { onOpenRecent(item.id) })
            }
        }

        // Coming soon: muted, tagged, not clickable
        Spacer(modifier = Modifier.height(30.dp))
        SectionLabel(stringResource(R.string.section_coming_soon))
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ComingSoonChip(stringResource(R.string.soon_recipe_lab), Modifier.weight(1f))
            ComingSoonChip(stringResource(R.string.soon_cad_studio), Modifier.weight(1f))
        }

        Spacer(modifier = Modifier.height(28.dp))
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        color = Muted,
        fontSize = 11.sp,
        letterSpacing = 1.5.sp,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun PrimaryAction(
    symbol: String,
    title: String,
    hint: String,
    accent: Color,
    highlighted: Boolean,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = CardColor),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    if (highlighted) {
                        Brush.linearGradient(listOf(Color(0xFF302B63), Color(0xFF242044), Color(0xFF171F35)))
                    } else {
                        Brush.linearGradient(listOf(CardColor, CardColor))
                    },
                )
                .padding(horizontal = 20.dp, vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = symbol, color = accent, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, color = White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = hint, color = Muted, fontSize = 13.sp)
            }
            Text(text = "→", color = accent, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun RecentAnalysisCard(item: RecentAnalysisItem, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardColor),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF0F1526))
                    .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) {
                AsyncImage(
                    model = item.thumbnailPath?.let { File(it) } ?: item.imageUri,
                    contentDescription = item.fileName,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.fileName,
                    color = White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = pluralStringResource(R.plurals.recent_colour_count, item.colourCount, item.colourCount),
                    color = Cyan,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                )
                Text(text = item.dateText, color = Muted, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun ComingSoonChip(title: String, modifier: Modifier = Modifier) {
    // Deliberately not clickable.
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(CardColor.copy(alpha = 0.45f))
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = title,
            color = Muted.copy(alpha = 0.6f),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
        )
        Text(
            text = stringResource(R.string.soon_tag),
            color = Muted.copy(alpha = 0.8f),
            fontSize = 10.sp,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White.copy(alpha = 0.06f))
                .padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

@Composable
fun WorkspaceTile(
    symbol: String,
    title: String,
    subtitle: String,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.height(145.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = CardColor,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(15.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = symbol,
                color = accent,
                fontSize = 27.sp,
                fontWeight = FontWeight.Bold,
            )

            Column {
                Text(
                    text = title,
                    color = White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    color = Muted,
                    fontSize = 11.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
fun NIRABottomBar(
    modifier: Modifier = Modifier,
    tabs: List<BottomTab> = DashboardModel.visibleTabs(),
    currentRoute: String = RootDestinations.DASHBOARD,
    onNavigate: (String) -> Unit = {},
) {
    Surface(
        modifier = modifier,
        color = Color(0xFF11182A),
        tonalElevation = 8.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tabs.forEach { tab ->
                BottomItem(
                    symbol = when (tab) {
                        BottomTab.HOME -> "⌂"
                        BottomTab.ARTWORK -> "◈"
                        BottomTab.ASK_NIRA -> "✦"
                        BottomTab.SETTINGS -> "⚙"
                    },
                    label = stringResource(
                        when (tab) {
                            BottomTab.HOME -> R.string.tab_home
                            BottomTab.ARTWORK -> R.string.tab_artwork
                            BottomTab.ASK_NIRA -> R.string.tab_ask_nira
                            BottomTab.SETTINGS -> R.string.tab_settings
                        },
                    ),
                    selected = currentRoute.startsWith(tab.route),
                    modifier = Modifier.clickable { onNavigate(tab.route) },
                )
            }
        }
    }
}

@Composable
fun BottomItem(
    symbol: String,
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = symbol,
            color = if (selected) Violet else Muted,
            fontSize = 21.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = label,
            color = if (selected) Violet else Muted,
            fontSize = 10.sp,
        )
    }
}

@Preview(showBackground = true)
@Composable
fun NIRADashboardPreview() {
    NIRATheme {
        NIRADashboard()
    }
}
