package com.mos.nira.features.artwork.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.mos.nira.features.artwork.presentation.AnalysisHistoryScreen
import com.mos.nira.features.artwork.presentation.ArtworkPreviewScreen
import com.mos.nira.features.artwork.presentation.ArtworkStudioLandingScreen
import com.mos.nira.features.artwork.presentation.ArtworkViewModel
import com.mos.nira.features.artwork.presentation.ColorIntelligenceScreen
import com.mos.nira.features.artwork.presentation.ColorSeparationScreen

object ArtworkDestinations {
    const val LANDING = "artwork_landing"
    const val PREVIEW = "artwork_preview"
    const val COLOR_INTELLIGENCE = "artwork_color_intelligence"
    const val COLOR_SEPARATION = "artwork_color_separation"
    const val HISTORY = "artwork_history"
}

@Composable
fun ArtworkNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    viewModel: ArtworkViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    NavHost(
        navController = navController,
        startDestination = ArtworkDestinations.LANDING,
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
                paletteSize = uiState.paletteSize,
                analysisState = uiState.analysisState,
                onPaletteSizeChange = { viewModel.setPaletteSize(it) },
                onReplaceImage = { uri -> viewModel.onImageSelected(uri) },
                onStartAnalysis = {
                    viewModel.analyzeArtwork()
                    navController.navigate(ArtworkDestinations.COLOR_INTELLIGENCE)
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(ArtworkDestinations.COLOR_INTELLIGENCE) {
            ColorIntelligenceScreen(
                bitmap = uiState.selectedBitmap,
                palette = uiState.palette,
                colorGroups = uiState.colorGroups,
                selectedColor = uiState.selectedColor,
                onColorSelected = { viewModel.selectColor(it) },
                onNavigateToSeparation = {
                    navController.navigate(ArtworkDestinations.COLOR_SEPARATION)
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(ArtworkDestinations.COLOR_SEPARATION) {
            ColorSeparationScreen(
                separationResult = uiState.separationResult,
                selectedLayerIndex = uiState.selectedLayerIndex,
                onSelectLayer = { viewModel.selectLayer(it) },
                onBack = { navController.popBackStack() }
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
                onBack = { navController.popBackStack() }
            )
        }
    }
}
