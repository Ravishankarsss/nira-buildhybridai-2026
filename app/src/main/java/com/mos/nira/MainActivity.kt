package com.mos.nira

import com.mos.nira.features.artwork.domain.mainPrintHexes
import com.mos.nira.core.image.InputKind
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
import androidx.compose.foundation.layout.safeDrawingPadding
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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import coil.compose.AsyncImage
import com.mos.nira.features.artwork.navigation.ArtworkNavHost
import com.mos.nira.features.artwork.presentation.ArtworkViewModel
import com.mos.nira.features.camera.GarmentCameraScreen
import com.mos.nira.features.dashboard.ArtworkStart
import com.mos.nira.core.locale.LocaleHelper
import com.mos.nira.features.dashboard.BottomTab
import com.mos.nira.features.dashboard.DashboardModel
import com.mos.nira.features.dashboard.DayPart
import com.mos.nira.features.dashboard.RecentAnalysisItem
import com.mos.nira.features.dashboard.RootDestinations
import com.mos.nira.features.dashboard.WorkspaceCard
import com.mos.nira.features.settings.SettingsScreen
import com.mos.nira.ui.theme.NIRATheme
import androidx.compose.runtime.key
import java.io.File
import java.util.Calendar

private val Navy = Color(0xFF0B1020)
private val CardColor = Color(0xFF171F35)
private val Violet = Color(0xFFB3A8FF)
private val Cyan = Color(0xFF55D6E8)
private val White = Color(0xFFF5F6FF)
private val Muted = Color(0xFFA8B4CE)

class MainActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(LocaleHelper.getLocalizedContext(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        LocaleHelper.init(this)
        enableEdgeToEdge()

        setContent {
            val currentLang by LocaleHelper.currentLanguageCode.collectAsState()
            key(currentLang) {
                NIRATheme {
                    NIRAAppRoot()
                }
            }
        }
    }
}

@Composable
fun NIRAAppRoot() {
    val rootNavController = rememberNavController()
    // One view model for the dashboard and the artwork flow, so both see the same saved history.
    val artworkViewModel: ArtworkViewModel = viewModel()
    // Only the saved history is read here. Reading the whole UI state made the root and the
    // dashboard redraw on every unrelated change (for example when shade libraries finish loading).
    val savedAnalyses by remember(artworkViewModel) {
        artworkViewModel.uiState.map { it.savedAnalyses }.distinctUntilChanged()
    }.collectAsState(initial = artworkViewModel.uiState.value.savedAnalyses)
    val navBackStackEntry by rootNavController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: RootDestinations.DASHBOARD

    // Gallery picker shared by "Photograph a garment" and the Print Report card.
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            // "Photograph a garment" -> camera or gallery: a garment photo.
            artworkViewModel.onImageSelected(uri, InputKind.PHOTO)
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

                val saved = savedAnalyses
                val homeContext = androidx.compose.ui.platform.LocalContext.current
                var homeAskOpen by remember { mutableStateOf(false) }
                if (homeAskOpen) {
                    // Ask NIRA on Home: facts from the saved jobs, the techniques file, AI activity and the rates.
                    val library by androidx.compose.runtime.produceState<com.mos.nira.core.advice.TechniqueLibrary.Library?>(null) {
                        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                            com.mos.nira.core.advice.TechniqueLibrary.parse(homeContext.assets.open("print_techniques.json").bufferedReader().use { it.readText() })
                        }
                    }
                    LaunchedEffect(Unit) { com.mos.nira.core.ai.AiActivityLog.load(homeContext); com.mos.nira.features.commercial.FactoryRatesStore.load(homeContext) }
                    val activity by com.mos.nira.core.ai.AiActivityLog.entries.collectAsState()
                    val rates by com.mos.nira.features.commercial.FactoryRatesStore.rates.collectAsState()
                    val summaries = remember(saved) { com.mos.nira.features.dashboard.JobSummaryStore.all(homeContext) }
                    val facts = remember(saved, summaries, library, activity, rates) {
                        com.mos.nira.core.ai.HomeFacts.build(
                            saved, summaries, library, com.mos.nira.core.ai.AiActivityLog.totals(activity),
                            ratesSet = !rates.isEmpty, ratesSample = rates.isSample, currency = rates.currency
                        )
                    }
                    val homeContextActions = remember(saved) {
                        com.mos.nira.core.ai.NiraActions.Context(emptyList(), emptyList(), emptyList(), emptyList(), jobs = com.mos.nira.core.ai.HomeFacts.jobs(saved))
                    }
                    com.mos.nira.features.ai.AskNiraSheet(
                        jobKey = "home",
                        facts = facts,
                        actionContext = homeContextActions,
                        onActions = { actions ->
                            actions.forEach { a ->
                                when (a) {
                                    is com.mos.nira.core.ai.NiraActions.Action.OpenJob -> saved.firstOrNull { it.id == a.id }?.let { record ->
                                        homeAskOpen = false
                                        artworkViewModel.openAnalysisRecord(record)
                                        rootNavController.navigate(RootDestinations.artworkFlowAt(ArtworkStart.JOB))
                                    }
                                    is com.mos.nira.core.ai.NiraActions.Action.OpenLibrary -> {
                                        homeAskOpen = false
                                        rootNavController.navigate(RootDestinations.TECHNIQUE_LIBRARY + "?q=" + android.net.Uri.encode(a.query))
                                    }
                                    is com.mos.nira.core.ai.NiraActions.Action.NewJob -> {
                                        homeAskOpen = false
                                        rootNavController.navigate(if (a.photo) RootDestinations.GARMENT_CAMERA else RootDestinations.ARTWORK_FLOW)
                                    }
                                    com.mos.nira.core.ai.NiraActions.Action.OpenAiActivity -> { homeAskOpen = false; rootNavController.navigate(RootDestinations.AI_ACTIVITY) }
                                    com.mos.nira.core.ai.NiraActions.Action.OpenSettings -> { homeAskOpen = false; rootNavController.navigate(RootDestinations.SETTINGS) }
                                    else -> Unit
                                }
                            }
                            null
                        },
                        onDismiss = { homeAskOpen = false },
                        textPrompt = com.mos.nira.core.ai.HomeFacts.TEXT_PROMPT,
                        voicePrompt = com.mos.nira.core.ai.HomeFacts.VOICE_PROMPT,
                        suggestions = listOf(
                            stringResource(R.string.home_ask_s1), stringResource(R.string.home_ask_s2),
                            stringResource(R.string.home_ask_s3), stringResource(R.string.home_ask_s4)
                        ),
                        intro = stringResource(R.string.home_ask_intro),
                        placeholder = stringResource(R.string.home_ask_placeholder),
                        voicePrivacy = stringResource(R.string.home_voice_privacy)
                    )
                }
                val recent = remember(saved) {
                    DashboardModel.recentAnalyses(saved, summaries = com.mos.nira.features.dashboard.JobSummaryStore.all(homeContext))
                }
                NIRADashboard(
                    modifier = Modifier,
                    recent = recent,
                    latestFileName = DashboardModel.latestAnalysis(saved)?.let {
                        com.mos.nira.features.artwork.presentation.jobTitle(it.fileName, it.inputKind, it.mainPrintHexes())
                    },
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
                            WorkspaceCard.TECHNIQUE_LIBRARY -> rootNavController.navigate(RootDestinations.TECHNIQUE_LIBRARY)
                            // Not a screen of its own: Ask NIRA lives on each job's decision card (see RootDestinations.BUILT).
                            WorkspaceCard.ASK_NIRA -> Unit
                        }
                    },
                    onOpenRecent = { id ->
                        saved.firstOrNull { it.id == id }?.let { record ->
                            artworkViewModel.openAnalysisRecord(record)
                            rootNavController.navigate(RootDestinations.artworkFlowAt(ArtworkStart.JOB))
                        }
                    },
                    onSeeAll = { rootNavController.navigate(RootDestinations.ARTWORK_FLOW) },
                    onOpenAiActivity = { rootNavController.navigate(RootDestinations.AI_ACTIVITY) },
                    onAskNira = { homeAskOpen = true },
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
                        artworkViewModel.onImageSelected(uri, InputKind.PHOTO)
                        rootNavController.navigate(RootDestinations.artworkFlowAt(ArtworkStart.PREVIEW)) {
                            popUpTo(RootDestinations.GARMENT_CAMERA) { inclusive = true }
                        }
                    },
                    onBack = { rootNavController.popBackStack() },
                )
            }

            composable(RootDestinations.AI_ACTIVITY) {
                com.mos.nira.features.ai.AiActivityScreen(onBack = { rootNavController.popBackStack() })
            }

            composable(RootDestinations.TECHNIQUE_LIBRARY + "?q={q}",
                arguments = listOf(androidx.navigation.navArgument("q") { defaultValue = "" })
            ) { entry ->
                com.mos.nira.features.library.TechniqueLibraryScreen(
                    onBack = { rootNavController.popBackStack() },
                    initialQuery = entry.arguments?.getString("q").orEmpty()
                )
            }

            composable(RootDestinations.SETTINGS) {
                SettingsScreen(savedAnalysesCount = savedAnalyses.size)
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
    onSeeAll: () -> Unit = {},
    onOpenAiActivity: () -> Unit = {},
    onAskNira: () -> Unit = {},
) {
    val homeContext = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(Unit) { com.mos.nira.core.ai.AiSettings.load(homeContext) }
    val aiCounts by com.mos.nira.core.ai.AiSettings.counts.collectAsState()
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
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        Spacer(modifier = Modifier.height(22.dp))

        // Brand header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // NIRA mark from Ravi's logo: the ribbon N with a needle.
            androidx.compose.foundation.Image(
                painter = androidx.compose.ui.res.painterResource(R.drawable.nira_mark),
                contentDescription = null,
                modifier = Modifier.size(56.dp).clip(RoundedCornerShape(14.dp)),
            )
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
        }

        // The promise in one short line, and the greeting.
        Spacer(modifier = Modifier.height(10.dp))
        Text(text = stringResource(R.string.dashboard_headline), color = Muted, fontSize = 13.sp, lineHeight = 18.sp)
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = stringResource(
                when (dayPart) {
                    DayPart.MORNING -> R.string.greeting_morning
                    DayPart.AFTERNOON -> R.string.greeting_afternoon
                    DayPart.EVENING -> R.string.greeting_evening
                },
            ),
            color = White,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(12.dp))

        // Start a print job: two big choices side by side.
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Brush.linearGradient(listOf(Color(0xFF302B63), Color(0xFF242044), Color(0xFF171F35))))
                    .padding(16.dp),
            ) {
                Text(stringResource(R.string.home_start_title), color = White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.home_start_hint), color = Muted, fontSize = 12.sp, lineHeight = 16.sp)
                Spacer(modifier = Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StartChoice("◈", stringResource(R.string.home_start_artwork), Violet, Modifier.weight(1f), onAnalyseArtwork)
                    StartChoice("◉", stringResource(R.string.home_start_photo), Cyan, Modifier.weight(1f)) { showPhotoSource = true }
                }
            }
        }

        // Ask NIRA anything: the centre of Home (text or voice, about all your jobs).
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
                .background(Brush.linearGradient(listOf(Color(0xFF1F3A4A), Color(0xFF17283A))))
                .clickable(onClick = onAskNira).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("✦", color = Cyan, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.home_ask_title), color = White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.home_ask_hint), color = Muted, fontSize = 12.sp, lineHeight = 16.sp)
            }
            Text("🎤", fontSize = 22.sp)
        }

        // Tools in one row.
        if (cards.isNotEmpty()) {
            Spacer(modifier = Modifier.height(22.dp))
            SectionLabel(stringResource(R.string.section_workspace))
            Spacer(modifier = Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                cards.forEach { card ->
                    ToolTile(
                        symbol = when (card) {
                            WorkspaceCard.PRINT_REPORT -> "▤"
                            WorkspaceCard.SHADE_LIBRARY -> "◐"
                            WorkspaceCard.TECHNIQUE_LIBRARY -> "✧"
                            WorkspaceCard.ASK_NIRA -> "✦"
                        },
                        title = stringResource(
                            when (card) {
                                WorkspaceCard.PRINT_REPORT -> R.string.home_tool_report
                                WorkspaceCard.SHADE_LIBRARY -> R.string.home_tool_shades
                                WorkspaceCard.TECHNIQUE_LIBRARY -> R.string.home_tool_techniques
                                WorkspaceCard.ASK_NIRA -> R.string.card_ask_nira
                            },
                        ),
                        accent = if (card.ordinal % 2 == 0) Violet else Cyan,
                        onClick = { onOpenCard(card) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        // Recent jobs with their decision.
        Spacer(modifier = Modifier.height(22.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { SectionLabel(stringResource(R.string.section_recent)) }
            if (recent.isNotEmpty()) {
                Text(
                    stringResource(R.string.home_see_all), color = Cyan, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onSeeAll).padding(horizontal = 6.dp, vertical = 4.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        if (recent.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = CardColor),
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(text = stringResource(R.string.recent_empty_title), color = White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(text = stringResource(R.string.recent_empty_message), color = Muted, fontSize = 13.sp, lineHeight = 19.sp)
                }
            }
        } else {
            recent.forEachIndexed { index, item ->
                if (index > 0) Spacer(modifier = Modifier.height(10.dp))
                RecentAnalysisCard(item = item, onClick = { onOpenRecent(item.id) })
            }
        }

        // Hybrid AI on this phone: the real counts NIRA keeps (Settings has the details).
        Spacer(modifier = Modifier.height(22.dp))
        Row(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(CardColor).clickable(onClick = onOpenAiActivity).padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("✦", color = Violet, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.home_ai_title), color = White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text(
                    stringResource(R.string.home_ai_counts, aiCounts.answersOnDevice, aiCounts.answersInCloud, aiCounts.imagesUploaded),
                    color = Muted, fontSize = 12.sp, lineHeight = 16.sp,
                )
            }
            Text("›", color = Cyan, fontSize = 22.sp)
        }

        Spacer(modifier = Modifier.height(28.dp))
    }
}

@Composable
private fun StartChoice(symbol: String, title: String, accent: Color, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF0F1526).copy(alpha = 0.7f))
            .clickable(onClick = onClick)
            .padding(vertical = 16.dp, horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(symbol, color = accent, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(6.dp))
        Text(title, color = White, fontSize = 14.sp, fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.Center, lineHeight = 18.sp)
    }
}

@Composable
private fun ToolTile(symbol: String, title: String, accent: Color, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(CardColor)
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(symbol, color = accent, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(6.dp))
        Text(title, color = White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, textAlign = androidx.compose.ui.text.style.TextAlign.Center, maxLines = 2, lineHeight = 15.sp)
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
        shape = RoundedCornerShape(18.dp),
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
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = symbol,
                color = accent,
                fontSize = 24.sp,
                lineHeight = 24.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = title,
                    color = White,
                    fontSize = 15.sp,
                    lineHeight = 19.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = hint,
                    color = Muted,
                    fontSize = 12.sp,
                    lineHeight = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Text(text = "→", color = accent, fontSize = 18.sp, lineHeight = 18.sp, fontWeight = FontWeight.Bold)
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
                    text = com.mos.nira.features.artwork.presentation.jobTitle(item.fileName, item.inputKind, item.mainHexes),
                    color = White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(3.dp))
                val sm = item.summary
                Text(
                    text = if (sm?.method != null) {
                        listOfNotNull(
                            sm.method,
                            sm.perPc?.let { stringResource(R.string.cost_per_pc, com.mos.nira.core.commercial.Money.format(sm.currency, it, 2)) + if (sm.sampleRates) " " + stringResource(com.mos.nira.R.string.rates_sample_tag) else "" },
                        ).joinToString("  ·  ")
                    } else {
                        pluralStringResource(R.plurals.recent_colour_count, item.colourCount, item.colourCount)
                    },
                    color = Cyan,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
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
            color = Muted.copy(alpha = 0.85f),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
        )
        Text(
            text = stringResource(R.string.soon_tag),
            color = White,
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
    onClick: () -> Unit = {},
) {
    Card(
        modifier = modifier
            .clearAndSetSemantics {
                contentDescription = "$title, $subtitle"
                role = Role.Button
                // clearAndSetSemantics removes the tap action of .clickable, so it is set here.
                onClick(label = title) { onClick(); true }
            }
            .clickable(
                onClick = onClick,
                onClickLabel = title,
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = CardColor,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = symbol,
                color = accent,
                fontSize = 24.sp,
                lineHeight = 24.sp,
                fontWeight = FontWeight.Bold,
            )

            Column {
                Text(
                    text = title,
                    color = White,
                    fontSize = 13.sp,
                    lineHeight = 16.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    color = Muted,
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    maxLines = 1,
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
