package com.mos.nira.features.artwork.presentation

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mos.nira.core.image.ArtworkImageProcessor
import com.mos.nira.core.image.ColorExtractor
import com.mos.nira.core.image.ColorSeparator
import com.mos.nira.features.artwork.data.ArtworkRepository
import com.mos.nira.features.artwork.domain.ArtworkAnalysisRecord
import com.mos.nira.features.artwork.domain.ArtworkMetadata
import com.mos.nira.features.artwork.domain.ColorGroup
import com.mos.nira.features.artwork.domain.DominantColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

sealed interface AnalysisState {
    data object Idle : AnalysisState
    data class Loading(val message: String) : AnalysisState
    data object Success : AnalysisState
    data class Error(val message: String) : AnalysisState
}

data class ArtworkStudioUiState(
    val selectedImageUri: Uri? = null,
    val selectedBitmap: Bitmap? = null,
    val metadata: ArtworkMetadata? = null,
    val analysisState: AnalysisState = AnalysisState.Idle,
    val paletteSize: Int = 8,
    val palette: List<DominantColor> = emptyList(),
    val colorGroups: List<ColorGroup> = emptyList(),
    val selectedColor: DominantColor? = null,
    val separationResult: ColorSeparator.SeparationResult? = null,
    val selectedLayerIndex: Int = -1, // -1 means combined composite preview
    val savedAnalyses: List<ArtworkAnalysisRecord> = emptyList(),
    val currentRecordId: String? = null,
    val isSavedLocally: Boolean = false
)

class ArtworkViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ArtworkRepository(application)

    private val _uiState = MutableStateFlow(ArtworkStudioUiState())
    val uiState: StateFlow<ArtworkStudioUiState> = _uiState.asStateFlow()

    init {
        loadSavedAnalyses()
    }

    fun loadSavedAnalyses() {
        viewModelScope.launch {
            val list = repository.getAllAnalyses()
            _uiState.update { it.copy(savedAnalyses = list) }
        }
    }

    /**
     * Handles selection of a new image from the system picker.
     */
    fun onImageSelected(uri: Uri) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    analysisState = AnalysisState.Loading("Reading image metadata..."),
                    selectedImageUri = uri,
                    palette = emptyList(),
                    colorGroups = emptyList(),
                    selectedColor = null,
                    separationResult = null,
                    selectedLayerIndex = -1,
                    isSavedLocally = false,
                    currentRecordId = null
                )
            }

            try {
                val context = getApplication<Application>()
                val meta = ArtworkImageProcessor.probeMetadata(context, uri)
                val bitmap = ArtworkImageProcessor.decodeBitmapSafely(context, uri)

                if (bitmap == null) {
                    _uiState.update {
                        it.copy(
                            analysisState = AnalysisState.Error("Unable to decode artwork image. Please select a valid JPEG, PNG or WebP file.")
                        )
                    }
                    return@launch
                }

                _uiState.update {
                    it.copy(
                        selectedBitmap = bitmap,
                        metadata = meta,
                        analysisState = AnalysisState.Idle
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.update {
                    it.copy(analysisState = AnalysisState.Error("Failed to load image: ${e.localizedMessage}"))
                }
            }
        }
    }

    fun setPaletteSize(size: Int) {
        _uiState.update { it.copy(paletteSize = size.coerceIn(3, 16)) }
    }

    fun selectColor(color: DominantColor?) {
        _uiState.update { it.copy(selectedColor = color) }
    }

    fun selectLayer(layerIndex: Int) {
        _uiState.update { it.copy(selectedLayerIndex = layerIndex) }
    }

    /**
     * Executes deterministic color extraction and separation in background threads.
     */
    fun analyzeArtwork() {
        val currentBitmap = _uiState.value.selectedBitmap ?: return
        val currentUri = _uiState.value.selectedImageUri ?: return
        val meta = _uiState.value.metadata ?: return
        val k = _uiState.value.paletteSize

        viewModelScope.launch {
            _uiState.update {
                it.copy(analysisState = AnalysisState.Loading("Extracting dominant colors (CIELAB K-Means)..."))
            }

            try {
                // Step 1: Palette extraction
                val extractionResult = withContext(Dispatchers.Default) {
                    ColorExtractor.extractPalette(currentBitmap, targetPaletteSize = k)
                }

                _uiState.update {
                    it.copy(
                        analysisState = AnalysisState.Loading("Generating digital color separations..."),
                        palette = extractionResult.palette,
                        colorGroups = extractionResult.colorGroups,
                        selectedColor = extractionResult.palette.firstOrNull()
                    )
                }

                // Step 2: Separation generation
                val separationResult = withContext(Dispatchers.Default) {
                    ColorSeparator.separateColors(currentBitmap, extractionResult.palette)
                }

                val analysisId = UUID.randomUUID().toString()
                val context = getApplication<Application>()
                val thumbPath = ArtworkImageProcessor.saveThumbnail(context, currentBitmap, analysisId)

                val record = ArtworkAnalysisRecord(
                    id = analysisId,
                    fileName = meta.fileName,
                    imageUri = currentUri.toString(),
                    thumbnailPath = thumbPath,
                    width = meta.width,
                    height = meta.height,
                    fileSizeBytes = meta.fileSizeBytes,
                    timestamp = System.currentTimeMillis(),
                    palette = extractionResult.palette,
                    colorGroups = extractionResult.colorGroups
                )

                // Save locally
                repository.saveAnalysis(record)
                val updatedAnalyses = repository.getAllAnalyses()

                _uiState.update {
                    it.copy(
                        analysisState = AnalysisState.Success,
                        separationResult = separationResult,
                        currentRecordId = analysisId,
                        isSavedLocally = true,
                        savedAnalyses = updatedAnalyses
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.update {
                    it.copy(analysisState = AnalysisState.Error("Analysis failed: ${e.localizedMessage}"))
                }
            }
        }
    }

    /**
     * Reopens a previously saved analysis record.
     */
    fun openAnalysisRecord(record: ArtworkAnalysisRecord) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    analysisState = AnalysisState.Loading("Loading analysis record..."),
                    selectedColor = record.palette.firstOrNull(),
                    palette = record.palette,
                    colorGroups = record.colorGroups,
                    currentRecordId = record.id,
                    isSavedLocally = true
                )
            }

            val context = getApplication<Application>()
            val uri = Uri.parse(record.imageUri)
            val bitmap = ArtworkImageProcessor.decodeBitmapSafely(context, uri)

            val meta = ArtworkMetadata(
                fileName = record.fileName,
                width = record.width,
                height = record.height,
                fileSizeBytes = record.fileSizeBytes,
                mimeType = "image/*"
            )

            val separation = if (bitmap != null) {
                withContext(Dispatchers.Default) {
                    ColorSeparator.separateColors(bitmap, record.palette)
                }
            } else null

            _uiState.update {
                it.copy(
                    selectedImageUri = uri,
                    selectedBitmap = bitmap,
                    metadata = meta,
                    separationResult = separation,
                    analysisState = AnalysisState.Success
                )
            }
        }
    }

    /**
     * Deletes a saved analysis with confirmation.
     */
    fun deleteAnalysis(id: String) {
        viewModelScope.launch {
            repository.deleteAnalysis(id)
            val updated = repository.getAllAnalyses()
            _uiState.update { current ->
                current.copy(
                    savedAnalyses = updated,
                    isSavedLocally = if (current.currentRecordId == id) false else current.isSavedLocally
                )
            }
        }
    }
}
