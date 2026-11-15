package com.mos.nira.features.artwork.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.mos.nira.core.image.ColorConverter
import com.mos.nira.features.artwork.domain.DominantColor
import com.mos.nira.features.artwork.presentation.AnalysisHistoryScreen
import com.mos.nira.features.artwork.presentation.ArtworkPreviewScreen
import com.mos.nira.features.artwork.presentation.ArtworkStudioLandingScreen
import com.mos.nira.features.artwork.presentation.ArtworkViewModel
import com.mos.nira.features.artwork.presentation.ColorIntelligenceScreen
import com.mos.nira.features.artwork.presentation.ColorSeparationScreen
import com.mos.nira.features.artwork.presentation.PrintReportScreen
import com.mos.nira.features.artwork.presentation.ShadeLibraryScreen

object ArtworkDestinations {
    const val LANDING = "artwork_landing"
    const val PREVIEW = "artwork_preview"
    const val COLOR_INTELLIGENCE = "artwork_color_intelligence"
    const val COLOR_SEPARATION = "artwork_color_separation"
    const val HISTORY = "artwork_history"
    const val SHADE_LIBRARY = "artwork_shade_library"
    const val PRINT_REPORT = "artwork_print_report"
}

@Composable
fun ArtworkNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    viewModel: ArtworkViewModel = viewModel(),
    startDestination: String = ArtworkDestinations.LANDING,
    onExit: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()

    // Back from the first screen of this flow leaves the flow (for example back to the dashboard).
    val goBack: () -> Unit = {
        if (navController.previousBackStackEntry != null) {
            navController.popBackStack()
        } else {
            onExit()
        }
    }

    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier
    ) {
        composable(ArtworkDestinations.LANDING) {
            ArtworkStudioLandingScreen(
                recentAnalyses = uiState.savedAnalyses,
                onImageSelected = { uri ->
                    viewModel.onImageSelected(uri)
                    navController.navigate(ArtworkDestinations.PREVIEW)
                },
                onOpenRecord = { record ->
                    viewModel.openAnalysisRecord(record)
                    navController.navigate(ArtworkDestinations.COLOR_INTELLIGENCE)
                },
                onViewAllHistory = {
                    navController.navigate(ArtworkDestinations.HISTORY)
                }
            )
        }

        composable(ArtworkDestinations.PREVIEW) {
            ArtworkPreviewScreen(
                bitmap = uiState.selectedBitmap,
                metadata = uiState.metadata,
                maxScreens = uiState.maxScreens,
                garmentColor = uiState.garmentColor,
                analysisState = uiState.analysisState,
                onMaxScreensChange = { viewModel.setMaxScreens(it) },
                onSelectGarmentHex = { hex ->
                    if (hex == null) {
                        viewModel.setGarmentColor(null)
                    } else {
                        val rgb = ColorConverter.hexToRgb(hex)
                        val lab = ColorConverter.rgbToLab(rgb.r, rgb.g, rgb.b)
                        viewModel.setGarmentColor(
                            DominantColor(
                                id = -1,
                                hex = hex,
                                r = rgb.r,
                                g = rgb.g,
                                b = rgb.b,
                                labL = lab.l,
                                labA = lab.a,
                                labB = lab.b,
                                pixelCount = 0,
                                pixelPercentage = 0f,
                                isGarmentBase = true
                            )
                        )
                    }
                },
                onReplaceImage = { uri -> viewModel.onImageSelected(uri) },
                onStartAnalysis = {
                    viewModel.analyzeArtwork()
                    navController.navigate(ArtworkDestinations.COLOR_INTELLIGENCE)
                },
                onBack = goBack
            )
        }

        composable(ArtworkDestinations.COLOR_INTELLIGENCE) {
            ColorIntelligenceScreen(
                bitmap = uiState.selectedBitmap,
                palette = uiState.palette,
                colorGroups = uiState.colorGroups,
                selectedColor = uiState.selectedColor,
                garmentColor = uiState.garmentColor,
                onColorSelected = { viewModel.selectColor(it) },
                onMergeColors = { keepId, mergeId -> viewModel.mergeColors(keepId, mergeId) },
                onRemoveColor = { colorId -> viewModel.removeColor(colorId) },
                onToggleLock = { colorId -> viewModel.toggleLockColor(colorId) },
                onAddHexColor = { hex -> viewModel.addColorByHex(hex) },
                onSetGarmentColor = { color -> viewModel.setGarmentColor(color) },
                onOpenShadeLibrary = { navController.navigate(ArtworkDestinations.SHADE_LIBRARY) },
                onAssignShade = { colorId, shade -> viewModel.assignShadeToColor(colorId, shade) },
                onNavigateToSeparation = {
                    navController.navigate(ArtworkDestinations.COLOR_SEPARATION)
                },
                onNavigateToPrintReport = {
                    navController.navigate(ArtworkDestinations.PRINT_REPORT)
                },
                onBack = goBack
            )
        }

        composable(ArtworkDestinations.SHADE_LIBRARY) {
            ShadeLibraryScreen(
                libraries = uiState.shadeLibraries,
                allShades = uiState.activeShades,
                selectedTypeFilter = uiState.selectedTypeFilter,
                onToggleLibrary = { name, enabled -> viewModel.toggleLibrary(name, enabled) },
                onSelectTypeFilter = { type -> viewModel.setTypeFilter(type) },
                onImportCsv = { uri, name -> viewModel.importCsvLibrary(uri, name) },
                onAddManualShade = { type, code, name, l, a, b ->
                    viewModel.addManualShade(type, code, name, l, a, b)
                },
                onBack = goBack
            )
        }

        composable(ArtworkDestinations.COLOR_SEPARATION) {
            ColorSeparationScreen(
                separationResult = uiState.separationResult,
                selectedLayerIndex = uiState.selectedLayerIndex,
                onSelectLayer = { viewModel.selectLayer(it) },
                onBack = goBack
            )
        }

        composable(ArtworkDestinations.PRINT_REPORT) {
            val context = LocalContext.current
            PrintReportScreen(
                partAResult = uiState.partAResult,
                partBResult = uiState.partBResult,
                partCResult = uiState.partCResult,
                partDResult = uiState.partDResult,
                inputs = uiState.printJobInputs,
                isComputingA = uiState.isComputingPrintReport,
                isComputingB = uiState.isComputingPartB,
                isComputingC = uiState.isComputingPartC,
                isComputingD = uiState.isComputingPartD,
                errorMessage = uiState.printReportError,
                exportMessage = uiState.exportStatusMessage,
                onUpdatePrintWidth = { viewModel.updatePrintWidth(it) },
                onUpdateGarmentHex = { viewModel.updateGarmentColorHex(it) },
                onUpdateGarmentThreshold = { viewModel.updateGarmentThresholdDeltaE00(it) },
                onUpdateMeshTpi = { viewModel.updateMeshTpi(it) },
                onUpdateStrokes = { viewModel.updateStrokes(it) },
                onUpdateWastage = { viewModel.updateWastagePercent(it) },
                onUpdateOrderPieces = { viewModel.updateOrderPieces(it) },
                onToggleWhiteBase = { viewModel.toggleUseWhiteBase(it) },
                onToggleWhitePrintActsAsBase = { viewModel.toggleWhitePrintActsAsBase(it) },
                onSetUserGarmentColor = { viewModel.setUserGarmentColor(it) },
                onAddErasedRect = { viewModel.addErasedRectangle(it) },
                onClearErasedRects = { viewModel.clearErasedRectangles() },
                onSelectStrategy = { viewModel.selectSeparationStrategy(it) },
                onExportSeparationSheet = { viewModel.saveSeparationSheetToDownloads(context) { } },
                onShareSeparationSheet = { viewModel.shareSeparationSheet(context) { } },
                onClearExportMessage = { viewModel.clearExportMessage() },
                onRecalculate = { viewModel.computePartAReport() },
                onBack = goBack
            )
        }

        composable(ArtworkDestinations.HISTORY) {
            AnalysisHistoryScreen(
                savedAnalyses = uiState.savedAnalyses,
                onOpenRecord = { record ->
                    viewModel.openAnalysisRecord(record)
                    navController.navigate(ArtworkDestinations.COLOR_INTELLIGENCE)
                },
                onDeleteRecord = { id -> viewModel.deleteAnalysis(id) },
                onBack = goBack
            )
        }
    }
}
