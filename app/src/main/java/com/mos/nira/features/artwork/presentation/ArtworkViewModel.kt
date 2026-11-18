package com.mos.nira.features.artwork.presentation

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mos.nira.core.image.ArtworkImageProcessor
import com.mos.nira.core.image.ColorConverter
import com.mos.nira.core.image.ColorExtractor
import com.mos.nira.core.image.ColorSeparator
import com.mos.nira.core.image.InputKind
import com.mos.nira.core.image.PrintAreaAndPaletteEngine
import com.mos.nira.core.image.SeparationStrategyEngine
import com.mos.nira.features.artwork.data.ArtworkRepository
import com.mos.nira.features.artwork.data.PrintRulesRepository
import com.mos.nira.features.artwork.data.ShadeLibraryRepository
import com.mos.nira.features.artwork.domain.ArtworkAnalysisRecord
import com.mos.nira.features.artwork.domain.ArtworkMetadata
import com.mos.nira.features.artwork.domain.ColorGroup
import com.mos.nira.features.artwork.domain.DominantColor
import com.mos.nira.features.artwork.domain.NormalizedRect
import com.mos.nira.features.artwork.domain.PartAResult
import com.mos.nira.features.artwork.domain.PartBResult
import com.mos.nira.features.artwork.domain.PartCResult
import com.mos.nira.features.artwork.domain.PartDResult
import com.mos.nira.features.artwork.domain.PrintColorConfig
import com.mos.nira.features.artwork.domain.PrintJobInputs
import com.mos.nira.features.artwork.domain.RegionAnswer
import com.mos.nira.features.artwork.domain.SeparationStrategyType
import com.mos.nira.features.artwork.domain.ShadeEntry
import com.mos.nira.features.artwork.domain.ShadeLibrary
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
    val maxScreens: Int = 8,
    val garmentColor: DominantColor? = null,
    val palette: List<DominantColor> = emptyList(),
    val colorGroups: List<ColorGroup> = emptyList(),
    val selectedColor: DominantColor? = null,
    val separationResult: ColorSeparator.SeparationResult? = null,
    val selectedLayerIndex: Int = -1, // -1 means combined composite preview
    val savedAnalyses: List<ArtworkAnalysisRecord> = emptyList(),
    val currentRecordId: String? = null,
    val isSavedLocally: Boolean = false,
    // Stage 2: Shade Library State
    val shadeLibraries: List<ShadeLibrary> = emptyList(),
    val activeShades: List<ShadeEntry> = emptyList(),
    val selectedTypeFilter: String? = null,
    // Part A: NIRA Print Report State
    val printJobInputs: PrintJobInputs = PrintJobInputs(),
    val partAResult: PartAResult? = null,
    val isComputingPrintReport: Boolean = false,
    val printReportError: String? = null,
    // Part B: Separation Strategies State
    val partBResult: PartBResult? = null,
    val isComputingPartB: Boolean = false,
    val selectedStrategyType: SeparationStrategyType = SeparationStrategyType.CMYK_PROCESS,
    // True once the user has picked a separation. Until then the recommended one is used.
    val userPickedStrategy: Boolean = false,
    // Part C: Screens, Proof & Film Positives State
    val partCResult: PartCResult? = null,
    val isComputingPartC: Boolean = false,
    val exportStatusMessage: String? = null,
    // Part D: Ink Consumption State
    val partDResult: PartDResult? = null,
    val isComputingPartD: Boolean = false
)

class ArtworkViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ArtworkRepository(application)
    private val shadeRepository = ShadeLibraryRepository(application)
    private val printRulesRepository = PrintRulesRepository(application)

    private val _uiState = MutableStateFlow(ArtworkStudioUiState())
    val uiState: StateFlow<ArtworkStudioUiState> = _uiState.asStateFlow()

    private val enabledLibraryNames = mutableSetOf("Built-in NIRA Demo")
    private var partAJob: Job? = null
    private var partBJob: Job? = null

    init {
        loadSavedAnalyses()
        loadShadeLibraries()
    }

    fun loadSavedAnalyses() {
        viewModelScope.launch {
            val list = repository.getAllAnalyses()
            _uiState.update { it.copy(savedAnalyses = list) }
        }
    }

    fun loadShadeLibraries() {
        viewModelScope.launch {
            val builtIn = shadeRepository.loadBuiltInDemoLibrary()
            val userLibs = shadeRepository.loadUserLibraries()

            val libraries = mutableListOf<ShadeLibrary>()
            libraries.add(
                ShadeLibrary(
                    name = "Built-in NIRA Demo",
                    isEnabled = enabledLibraryNames.contains("Built-in NIRA Demo"),
                    isBuiltIn = true,
                    entryCount = builtIn.size,
                    types = builtIn.map { it.type }.distinct()
                )
            )

            userLibs.forEach { (name, shades) ->
                libraries.add(
                    ShadeLibrary(
                        name = name,
                        isEnabled = enabledLibraryNames.contains(name),
                        isBuiltIn = false,
                        entryCount = shades.size,
                        types = shades.map { it.type }.distinct()
                    )
                )
            }

            val allActive = mutableListOf<ShadeEntry>()
            if (enabledLibraryNames.contains("Built-in NIRA Demo")) {
                allActive.addAll(builtIn)
            }
            userLibs.forEach { (name, shades) ->
                if (enabledLibraryNames.contains(name)) {
                    allActive.addAll(shades)
                }
            }

            _uiState.update {
                it.copy(
                    shadeLibraries = libraries,
                    activeShades = allActive
                )
            }

            // If a palette already exists, refresh shade matching
            if (_uiState.value.palette.isNotEmpty()) {
                refreshPaletteShadeMatches()
            }
        }
    }

    fun toggleLibrary(name: String, enabled: Boolean) {
        if (enabled) enabledLibraryNames.add(name) else enabledLibraryNames.remove(name)
        loadShadeLibraries()
    }

    fun setTypeFilter(type: String?) {
        _uiState.update { it.copy(selectedTypeFilter = type) }
        refreshPaletteShadeMatches()
    }

    fun importCsvLibrary(uri: Uri, libraryName: String) {
        viewModelScope.launch {
            val context = getApplication<Application>()
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val result = shadeRepository.importUserLibraryFromStream(stream, libraryName)
                if (result.isSuccess) {
                    enabledLibraryNames.add(libraryName)
                    loadShadeLibraries()
                } else {
                    _uiState.update {
                        it.copy(analysisState = AnalysisState.Error("CSV Import failed: ${result.exceptionOrNull()?.message}"))
                    }
                }
            }
        }
    }

    fun addManualShade(type: String, code: String, name: String, labL: Double, labA: Double, labB: Double) {
        viewModelScope.launch {
            val ok = shadeRepository.addManualShade(type, code, name, labL, labA, labB)
            if (ok) {
                enabledLibraryNames.add("My_Custom_Shades")
                loadShadeLibraries()
            }
        }
    }

    fun assignShadeToColor(colorId: Int, shade: ShadeEntry) {
        _uiState.update { state ->
            val updated = state.palette.map { c ->
                if (c.id == colorId) {
                    val sLab = ColorConverter.Lab(shade.labL, shade.labA, shade.labB)
                    val targetLab = ColorConverter.Lab(c.labL, c.labA, c.labB)
                    val dE00 = ColorConverter.deltaE00(targetLab, sLab)
                    val grade = ColorConverter.matchGrade(dE00)
                    val match = com.mos.nira.features.artwork.domain.ShadeMatch(shade, dE00, grade)
                    c.copy(assignedShade = shade, nearestShades = listOf(match) + c.nearestShades.filter { it.shade.code != shade.code })
                } else c
            }
            state.copy(palette = updated)
        }
    }

    private fun refreshPaletteShadeMatches() {
        val candidates = _uiState.value.activeShades
        val filter = _uiState.value.selectedTypeFilter
        val currentPalette = _uiState.value.palette
        if (currentPalette.isEmpty() || candidates.isEmpty()) return

        val refreshed = currentPalette.map { c ->
            val targetLab = ColorConverter.Lab(c.labL, c.labA, c.labB)
            val matches = shadeRepository.findNearestShades(targetLab, candidates, 3, filter)
            c.copy(nearestShades = matches)
        }
        _uiState.update { it.copy(palette = refreshed) }
    }

    /**
     * A new image. [kind] is how the user loaded it: PHOTO from "Photograph a garment" (camera or
     * gallery), ARTWORK from "Analyse artwork"; null keeps the current mode (replace image).
     */
    fun onImageSelected(uri: Uri, kind: InputKind? = null) {
        viewModelScope.launch {
            val cameraExif = readCameraExif(uri)
            _uiState.update {
                it.copy(
                    printJobInputs = it.printJobInputs.copy(
                        inputKind = kind ?: it.printJobInputs.inputKind,
                        inputKindConfirmed = false,
                        cameraExif = cameraExif,
                        regionAnswers = emptyList(),
                        allUncertainAreFoldShadows = false
                    ),
                    analysisState = AnalysisState.Loading("Reading image metadata..."),
                    selectedImageUri = uri,
                    palette = emptyList(),
                    colorGroups = emptyList(),
                    selectedColor = null,
                    separationResult = null,
                    selectedLayerIndex = -1,
                    garmentColor = null,
                    isSavedLocally = false,
                    currentRecordId = null,
                    partAResult = null,
                    partBResult = null,
                    partCResult = null,
                    partDResult = null,
                    userPickedStrategy = false
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

                // Automatically trigger initial Part A calculation
                computePartAReport()
            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.update {
                    it.copy(analysisState = AnalysisState.Error("Failed to load image: ${e.localizedMessage}"))
                }
            }
        }
    }

    fun setMaxScreens(screens: Int) {
        _uiState.update { it.copy(maxScreens = screens.coerceIn(2, 16)) }
    }

    fun setGarmentColor(color: DominantColor?) {
        _uiState.update { it.copy(garmentColor = color) }
        if (_uiState.value.selectedBitmap != null && _uiState.value.palette.isNotEmpty()) {
            analyzeArtwork()
        }
        if (color != null) {
            updateGarmentColorHex(color.hex)
        }
    }

    fun selectColor(color: DominantColor?) {
        _uiState.update { it.copy(selectedColor = color) }
    }

    fun selectLayer(layerIndex: Int) {
        _uiState.update { it.copy(selectedLayerIndex = layerIndex) }
    }

    fun analyzeArtwork() {
        val currentBitmap = _uiState.value.selectedBitmap ?: return
        val currentUri = _uiState.value.selectedImageUri ?: return
        val meta = _uiState.value.metadata ?: return
        val maxScreens = _uiState.value.maxScreens
        val garment = _uiState.value.garmentColor

        viewModelScope.launch {
            _uiState.update {
                it.copy(analysisState = AnalysisState.Loading("Auto-detecting print colors (CIEDE2000)..."))
            }

            try {
                val config = PrintColorConfig(
                    maxScreens = maxScreens,
                    mergeThresholdDeltaE00 = 3.2,
                    minCoveragePercentage = 0.5f,
                    garmentColorHex = garment?.hex
                )

                val extractionResult = withContext(Dispatchers.Default) {
                    ColorExtractor.extractPalette(currentBitmap, config)
                }

                // Match nearest shades from active library
                val candidates = _uiState.value.activeShades
                val filter = _uiState.value.selectedTypeFilter
                val matchedPalette = extractionResult.palette.map { c ->
                    val targetLab = ColorConverter.Lab(c.labL, c.labA, c.labB)
                    val matches = shadeRepository.findNearestShades(targetLab, candidates, 3, filter)
                    c.copy(nearestShades = matches)
                }

                _uiState.update {
                    it.copy(
                        analysisState = AnalysisState.Loading("Rendering 1024px digital separations..."),
                        palette = matchedPalette,
                        colorGroups = extractionResult.colorGroups,
                        selectedColor = matchedPalette.firstOrNull()
                    )
                }

                val separationResult = withContext(Dispatchers.Default) {
                    ColorSeparator.separateColors(
                        sourceBitmap = currentBitmap,
                        palette = matchedPalette,
                        garmentColor = garment,
                        maxDimension = 1024
                    )
                }

                val analysisId = UUID.randomUUID().toString()
                val context = getApplication<Application>()
                val thumbPath = ArtworkImageProcessor.saveThumbnail(context, currentBitmap, analysisId)
                // Keep a private copy so the analysis can be reopened after the app closes.
                val savedImageUri = ArtworkImageProcessor.saveArtworkCopy(context, currentUri, analysisId)
                    ?: currentUri.toString()

                val record = ArtworkAnalysisRecord(
                    id = analysisId,
                    fileName = meta.fileName,
                    imageUri = savedImageUri,
                    thumbnailPath = thumbPath,
                    width = meta.width,
                    height = meta.height,
                    fileSizeBytes = meta.fileSizeBytes,
                    timestamp = System.currentTimeMillis(),
                    palette = matchedPalette,
                    colorGroups = extractionResult.colorGroups,
                    garmentColor = garment,
                    measuredPrintColourCount = _uiState.value.partAResult?.measuredPalette?.size,
                    inputKind = _uiState.value.printJobInputs.inputKind.name,
                    inputKindConfirmed = _uiState.value.printJobInputs.inputKindConfirmed
                )

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

    fun mergeColors(keepColorId: Int, mergeColorId: Int) {
        val currentPalette = _uiState.value.palette
        val keep = currentPalette.find { it.id == keepColorId } ?: return
        val merge = currentPalette.find { it.id == mergeColorId } ?: return
        val bitmap = _uiState.value.selectedBitmap ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(analysisState = AnalysisState.Loading("Merging colors...")) }
            val updatedPalette = currentPalette.filter { it.id != mergeColorId }.mapIndexed { idx, c ->
                c.copy(id = idx)
            }
            reassignAndSeparate(bitmap, updatedPalette)
        }
    }

    fun removeColor(colorId: Int) {
        val currentPalette = _uiState.value.palette
        if (currentPalette.size <= 1) return
        val bitmap = _uiState.value.selectedBitmap ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(analysisState = AnalysisState.Loading("Removing color...")) }
            val updated = currentPalette.filter { it.id != colorId }.mapIndexed { idx, c ->
                c.copy(id = idx)
            }
            reassignAndSeparate(bitmap, updated)
        }
    }

    fun toggleLockColor(colorId: Int) {
        _uiState.update { state ->
            val updated = state.palette.map {
                if (it.id == colorId) it.copy(isLocked = !it.isLocked) else it
            }
            state.copy(
                palette = updated,
                selectedColor = updated.find { it.id == state.selectedColor?.id } ?: updated.firstOrNull()
            )
        }
    }

    fun addColorByHex(hex: String) {
        val bitmap = _uiState.value.selectedBitmap ?: return
        val currentPalette = _uiState.value.palette

        viewModelScope.launch {
            try {
                val rgb = ColorConverter.hexToRgb(hex)
                val lab = ColorConverter.rgbToLab(rgb.r, rgb.g, rgb.b)
                val targetLab = ColorConverter.Lab(lab.l, lab.a, lab.b)
                val matches = shadeRepository.findNearestShades(targetLab, _uiState.value.activeShades, 3, _uiState.value.selectedTypeFilter)

                val newColor = DominantColor(
                    id = currentPalette.size,
                    hex = ColorConverter.rgbToHex(rgb),
                    r = rgb.r,
                    g = rgb.g,
                    b = rgb.b,
                    labL = lab.l,
                    labA = lab.a,
                    labB = lab.b,
                    pixelCount = 0,
                    pixelPercentage = 0f,
                    isLocked = true,
                    nearestShades = matches
                )

                _uiState.update { it.copy(analysisState = AnalysisState.Loading("Adding color and reassigning pixels...")) }
                val updated = currentPalette + newColor
                reassignAndSeparate(bitmap, updated)
            } catch (e: Exception) {
                _uiState.update { it.copy(analysisState = AnalysisState.Error("Invalid HEX color: ${e.message}")) }
            }
        }
    }

    private suspend fun reassignAndSeparate(bitmap: Bitmap, palette: List<DominantColor>) = withContext(Dispatchers.Default) {
        val separation = ColorSeparator.separateColors(
            sourceBitmap = bitmap,
            palette = palette,
            garmentColor = _uiState.value.garmentColor,
            maxDimension = 1024
        )

        val updatedPalette = palette.mapIndexed { idx, c ->
            val layer = separation.layers.getOrNull(idx)
            val coverage = layer?.pixelCoverage ?: 0f
            c.copy(pixelPercentage = coverage)
        }

        _uiState.update {
            it.copy(
                palette = updatedPalette,
                separationResult = separation,
                selectedColor = updatedPalette.firstOrNull(),
                analysisState = AnalysisState.Success
            )
        }
    }

    fun openAnalysisRecord(record: ArtworkAnalysisRecord) {
        viewModelScope.launch {
            val cameraExif = readCameraExif(Uri.parse(record.imageUri))
            _uiState.update {
                it.copy(
                    printJobInputs = it.printJobInputs.copy(
                        // Records saved before 18 Nov 2026 have no mode: treated as artwork, and the
                        // app asks if the photo signals disagree.
                        inputKind = record.inputKind?.let { k -> InputKind.valueOf(k) } ?: InputKind.ARTWORK,
                        inputKindConfirmed = record.inputKindConfirmed,
                        cameraExif = cameraExif,
                        regionAnswers = emptyList(),
                        allUncertainAreFoldShadows = false
                    ),
                    analysisState = AnalysisState.Loading("Loading analysis record..."),
                    selectedColor = record.palette.firstOrNull(),
                    palette = record.palette,
                    colorGroups = record.colorGroups,
                    garmentColor = record.garmentColor,
                    currentRecordId = record.id,
                    isSavedLocally = true,
                    // Clear the previous artwork's report so it is never shown for this one.
                    partAResult = null,
                    partBResult = null,
                    partCResult = null,
                    partDResult = null,
                    separationResult = null,
                    userPickedStrategy = false
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

            _uiState.update {
                it.copy(
                    selectedImageUri = uri,
                    selectedBitmap = bitmap,
                    metadata = meta,
                    analysisState = AnalysisState.Success
                )
            }
            refreshPaletteShadeMatches()
            // Start the Print Report measurement at once; it does not need the separation layers.
            computePartAReport()

            // The colour separation layers (Separation screen) are made after Part A, so the
            // two do not compete for the phone's cores and the measured colours come first.
            if (bitmap != null) {
                partAJob?.join()
                val separation = withContext(Dispatchers.Default) {
                    ColorSeparator.separateColors(bitmap, record.palette, record.garmentColor, 1024)
                }
                _uiState.update {
                    // Only if the same analysis is still open and nothing newer has set the layers.
                    if (it.currentRecordId == record.id && it.separationResult == null) {
                        it.copy(separationResult = separation)
                    } else it
                }
            }
        }
    }

    /**
     * The user's answer to "This looks like a photo. Treat it as a photo?" (or the reverse).
     * Saved with the analysis, and Part A runs again with the chosen mode.
     */
    fun answerInputKind(kind: InputKind) {
        _uiState.update {
            it.copy(
                printJobInputs = it.printJobInputs.copy(
                    inputKind = kind,
                    inputKindConfirmed = true,
                    regionAnswers = emptyList(),
                    allUncertainAreFoldShadows = false
                )
            )
        }
        _uiState.value.currentRecordId?.let { id ->
            viewModelScope.launch {
                val record = repository.getAnalysisById(id) ?: return@launch
                repository.saveAnalysis(record.copy(inputKind = kind.name, inputKindConfirmed = true))
            }
        }
        computePartAReport()
    }

    /**
     * The user's answer for a colour marked "Possible fold shadow - tap to confirm": a point inside
     * each uncertain region and whether it is ink. Part A runs again with the answers.
     */
    fun answerFoldShadow(points: List<Pair<Float, Float>>, isInk: Boolean) {
        if (points.isEmpty()) return
        _uiState.update {
            it.copy(
                printJobInputs = it.printJobInputs.copy(
                    regionAnswers = it.printJobInputs.regionAnswers + points.map { (x, y) -> RegionAnswer(x, y, isInk) }
                )
            )
        }
        computePartAReport()
    }

    /** "All flagged areas are fold shadows": every uncertain region is removed; Part A runs again. */
    fun answerAllFoldShadows() {
        _uiState.update {
            it.copy(printJobInputs = it.printJobInputs.copy(allUncertainAreFoldShadows = true))
        }
        computePartAReport()
    }

    /** True when the file has camera make or exposure information; null when it cannot be read. */
    private suspend fun readCameraExif(uri: Uri): Boolean? = withContext(Dispatchers.IO) {
        try {
            getApplication<Application>().contentResolver.openInputStream(uri)?.use { stream ->
                val exif = android.media.ExifInterface(stream)
                exif.getAttribute(android.media.ExifInterface.TAG_MAKE) != null ||
                    exif.getAttribute(android.media.ExifInterface.TAG_EXPOSURE_TIME) != null
            }
        } catch (e: Exception) {
            null
        }
    }

    /** Keeps the saved analysis in step with the latest measured print colour count. */
    private fun saveMeasuredColourCount(count: Int) {
        val id = _uiState.value.currentRecordId ?: return
        viewModelScope.launch {
            val record = repository.getAnalysisById(id) ?: return@launch
            if (record.measuredPrintColourCount == count) return@launch
            repository.saveAnalysis(record.copy(measuredPrintColourCount = count))
            _uiState.update { it.copy(savedAnalyses = repository.getAllAnalyses()) }
        }
    }

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

    // ==========================================
    // PART A: NIRA Print Report Actions
    // ==========================================

    fun updatePrintWidth(widthCm: Double) {
        _uiState.update { current ->
            current.copy(printJobInputs = current.printJobInputs.copy(printWidthCm = widthCm.coerceAtLeast(1.0)))
        }
        computePartAReport()
    }

    fun updateGarmentColorHex(hex: String) {
        _uiState.update { current ->
            // A preset is only a first guess: the fabric is measured from the photo border.
            // Only "tap the fabric" (setUserGarmentColor) fixes the garment colour exactly.
            current.copy(printJobInputs = current.printJobInputs.copy(garmentColorHex = hex, userGarmentHex = null))
        }
        computePartAReport()
    }

    fun setUserGarmentColor(hex: String?) {
        _uiState.update { current ->
            current.copy(printJobInputs = current.printJobInputs.copy(userGarmentHex = hex))
        }
        computePartAReport()
    }

    fun setPrintBox(box: NormalizedRect) {
        _uiState.update { current ->
            current.copy(printJobInputs = current.printJobInputs.copy(printBox = box))
        }
        computePartAReport()
    }

    fun toggleUseWhiteBase(force: Boolean?) {
        _uiState.update { current ->
            current.copy(printJobInputs = current.printJobInputs.copy(useWhiteBaseOverride = force))
        }
        computePartBReport()
    }

    fun toggleWhitePrintActsAsBase(actsAsBase: Boolean) {
        _uiState.update { current ->
            current.copy(printJobInputs = current.printJobInputs.copy(whitePrintActsAsBase = actsAsBase))
        }
        computePartBReport()
    }

    fun updateOrderPieces(pieces: Int) {
        _uiState.update { current ->
            current.copy(printJobInputs = current.printJobInputs.copy(orderPieces = pieces.coerceAtLeast(1)))
        }
        computePartDReport()
    }

    fun updateGarmentThresholdDeltaE00(threshold: Double) {
        _uiState.update { current ->
            current.copy(printJobInputs = current.printJobInputs.copy(garmentThresholdDeltaE00 = threshold.coerceIn(1.0, 25.0)))
        }
        computePartAReport()
    }

    fun updateMeshTpi(meshTpi: Int) {
        _uiState.update { current ->
            current.copy(printJobInputs = current.printJobInputs.copy(meshTpi = meshTpi))
        }
        computePartDReport()
    }

    fun updateStrokes(strokes: Int) {
        _uiState.update { current ->
            current.copy(printJobInputs = current.printJobInputs.copy(strokes = strokes.coerceIn(1, 10)))
        }
        computePartDReport()
    }

    fun updateWastagePercent(wastagePercent: Double) {
        _uiState.update { current ->
            current.copy(printJobInputs = current.printJobInputs.copy(wastagePercent = wastagePercent.coerceIn(0.0, 100.0)))
        }
        computePartDReport()
    }

    fun addErasedRectangle(normRect: NormalizedRect) {
        _uiState.update { current ->
            val updated = current.printJobInputs.erasedRectangles + normRect
            current.copy(printJobInputs = current.printJobInputs.copy(erasedRectangles = updated))
        }
        computePartAReport()
    }

    fun clearErasedRectangles() {
        _uiState.update { current ->
            current.copy(printJobInputs = current.printJobInputs.copy(erasedRectangles = emptyList()))
        }
        computePartAReport()
    }

    fun computePartAReport() {
        val bitmap = _uiState.value.selectedBitmap ?: return
        partAJob?.cancel()
        partAJob = viewModelScope.launch {
            _uiState.update { it.copy(isComputingPrintReport = true, printReportError = null) }
            try {
                val inputs = _uiState.value.printJobInputs
                val shades = _uiState.value.activeShades
                val decisionRules = printRulesRepository.getSeparationDecisionRules()
                val maskRules = printRulesRepository.getGarmentMaskRules()
                val photoRules = printRulesRepository.getPhotoVsArtworkRules()
                val shadowRules = printRulesRepository.getShadowVsInkRules()
                val result = withContext(Dispatchers.Default) {
                    val maxDim = 2048
                    val width = bitmap.width
                    val height = bitmap.height
                    val scaled = if (width > maxDim || height > maxDim) {
                        val factor = maxDim.toFloat() / maxOf(width, height)
                        val targetW = (width * factor).toInt().coerceAtLeast(1)
                        val targetH = (height * factor).toInt().coerceAtLeast(1)
                        Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
                    } else bitmap

                    val pixels = IntArray(scaled.width * scaled.height)
                    scaled.getPixels(pixels, 0, scaled.width, 0, 0, scaled.width, scaled.height)
                    if (scaled != bitmap) {
                        scaled.recycle()
                    }

                    PrintAreaAndPaletteEngine.computePartA(
                        pixels = pixels,
                        width = scaled.width,
                        height = scaled.height,
                        inputs = inputs,
                        shadeCandidates = shades,
                        seed = 42L,
                        generateMaskBitmap = true,
                        decisionRules = decisionRules,
                        maskRules = maskRules,
                        photoRules = photoRules,
                        shadowRules = shadowRules
                    )
                }

                _uiState.update { it.copy(partAResult = result, isComputingPrintReport = false) }
                saveMeasuredColourCount(result.measuredPalette.size)

                // Automatically trigger Part B strategy comparison
                computePartBReport()
            } catch (e: Exception) {
                if (e is kotlin.coroutines.cancellation.CancellationException) throw e // a newer run replaced this one
                e.printStackTrace()
                _uiState.update { it.copy(isComputingPrintReport = false, printReportError = e.message) }
            }
        }
    }

    // ==========================================
    // PART B: Separation Strategies Actions
    // ==========================================

    fun selectSeparationStrategy(type: SeparationStrategyType) {
        _uiState.update { current ->
            current.copy(
                selectedStrategyType = type,
                userPickedStrategy = true,
                partBResult = current.partBResult?.copy(selectedStrategyType = type)
            )
        }
        computePartCReport(type)
    }

    fun computePartBReport() {
        val bitmap = _uiState.value.selectedBitmap ?: return
        val partA = _uiState.value.partAResult ?: return

        partBJob?.cancel()
        partBJob = viewModelScope.launch {
            _uiState.update { it.copy(isComputingPartB = true) }
            try {
                val inputs = _uiState.value.printJobInputs
                val processInks = printRulesRepository.getProcessInks()
                val decisionRules = printRulesRepository.getSeparationDecisionRules()
                val selectedStrat = _uiState.value.selectedStrategyType

                val result = withContext(Dispatchers.Default) {
                    val maxDim = 1024
                    val width = bitmap.width
                    val height = bitmap.height
                    val scaled = if (width > maxDim || height > maxDim) {
                        val factor = maxDim.toFloat() / maxOf(width, height)
                        val targetW = (width * factor).toInt().coerceAtLeast(1)
                        val targetH = (height * factor).toInt().coerceAtLeast(1)
                        Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
                    } else bitmap

                    val pixels = IntArray(scaled.width * scaled.height)
                    scaled.getPixels(pixels, 0, scaled.width, 0, 0, scaled.width, scaled.height)
                    if (scaled != bitmap) {
                        scaled.recycle()
                    }

                    val sW = scaled.width
                    val sH = scaled.height
                    val box = partA.printBox
                    val minXPx = (box.minX * sW).toInt().coerceIn(0, sW - 1)
                    val maxXPx = (box.maxX * sW).toInt().coerceIn(minXPx + 1, sW)
                    val minYPx = (box.minY * sH).toInt().coerceIn(0, sH - 1)
                    val maxYPx = (box.maxY * sH).toInt().coerceIn(minYPx + 1, sH)

                    val isMask = PrintAreaAndPaletteEngine.computePrintMask(pixels, sW, sH, inputs)
                    val maskedList = ArrayList<Int>((maxXPx - minXPx) * (maxYPx - minYPx))

                    for (y in minYPx until maxYPx) {
                        val rowOffset = y * sW
                        for (x in minXPx until maxXPx) {
                            val idx = rowOffset + x
                            if (isMask[idx]) {
                                maskedList.add(pixels[idx])
                            }
                        }
                    }

                    SeparationStrategyEngine.evaluateStrategies(
                        maskedPixels = maskedList.toIntArray(),
                        partA = partA,
                        inputs = inputs,
                        processInks = processInks,
                        decisionRules = decisionRules,
                        selectedStrategy = selectedStrat
                    )
                }

                // Screens and ink follow the recommended separation until the user picks one.
                val chosen = if (_uiState.value.userPickedStrategy) {
                    result.selectedStrategyType
                } else {
                    result.strategies.firstOrNull { it.isRecommended }?.strategyType ?: result.selectedStrategyType
                }
                val finalResult = result.copy(selectedStrategyType = chosen)
                _uiState.update {
                    it.copy(partBResult = finalResult, selectedStrategyType = chosen, isComputingPartB = false)
                }

                // Automatically trigger Part C generation for chosen strategy
                computePartCReport(chosen)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.update { it.copy(isComputingPartB = false) }
            }
        }
    }

    // ==========================================
    // PART C: Screens, Proof & Film Positives
    // ==========================================

    private var partCJob: Job? = null

    fun computePartCReport(chosenStrategy: SeparationStrategyType = _uiState.value.selectedStrategyType) {
        val bitmap = _uiState.value.selectedBitmap ?: return
        val partA = _uiState.value.partAResult ?: return
        val partB = _uiState.value.partBResult ?: return

        partCJob?.cancel()
        partCJob = viewModelScope.launch {
            _uiState.update { it.copy(isComputingPartC = true) }
            try {
                val inputs = _uiState.value.printJobInputs
                val processInks = printRulesRepository.getProcessInks()
                val decisionRules = printRulesRepository.getSeparationDecisionRules()

                val result = withContext(Dispatchers.Default) {
                    val maxDim = 800
                    val width = bitmap.width
                    val height = bitmap.height
                    val scaled = if (width > maxDim || height > maxDim) {
                        val factor = maxDim.toFloat() / maxOf(width, height)
                        val targetW = (width * factor).toInt().coerceAtLeast(1)
                        val targetH = (height * factor).toInt().coerceAtLeast(1)
                        Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
                    } else bitmap

                    val pixels = IntArray(scaled.width * scaled.height)
                    scaled.getPixels(pixels, 0, scaled.width, 0, 0, scaled.width, scaled.height)

                    val partC = SeparationStrategyEngine.generatePartC(
                        pixels = pixels,
                        width = scaled.width,
                        height = scaled.height,
                        originalBitmap = scaled,
                        partA = partA,
                        partB = partB,
                        chosenStrategyType = chosenStrategy,
                        inputs = inputs,
                        processInks = processInks,
                        decisionRules = decisionRules
                    )

                    if (scaled != bitmap && partC.originalBitmap != scaled) {
                        scaled.recycle()
                    }
                    partC
                }

                _uiState.update { it.copy(partCResult = result, isComputingPartC = false) }

                // Automatically trigger Part D ink calculations
                computePartDReport()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.update { it.copy(isComputingPartC = false) }
            }
        }
    }

    // ==========================================
    // PART D: Ink Consumption Calculations
    // ==========================================

    fun computePartDReport() {
        val partA = _uiState.value.partAResult ?: return
        val partC = _uiState.value.partCResult ?: return
        val inputs = _uiState.value.printJobInputs

        viewModelScope.launch {
            _uiState.update { it.copy(isComputingPartD = true) }
            try {
                val depositRules = printRulesRepository.getMeshDepositRules()
                val partD = withContext(Dispatchers.Default) {
                    SeparationStrategyEngine.calculatePartD(
                        partA = partA,
                        partC = partC,
                        inputs = inputs,
                        depositRules = depositRules
                    )
                }
                _uiState.update { it.copy(partDResult = partD, isComputingPartD = false) }
            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.update { it.copy(isComputingPartD = false) }
            }
        }
    }

    // ==========================================
    // EXPORT & SHARE ACTIONS
    // ==========================================

    fun exportSeparationSheet(context: Context, onResult: (String) -> Unit) {
        saveSeparationSheetToDownloads(context, onResult)
    }

    fun saveSeparationSheetToDownloads(context: Context, onResult: (String) -> Unit) {
        val compositeBitmap = _uiState.value.partCResult?.compositeSheetBitmap
        if (compositeBitmap == null) {
            onResult("Separation sheet bitmap not generated yet.")
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val fileName = "NIRA_Separation_Sheet_${System.currentTimeMillis()}.png"
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val values = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                        put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                        put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/NIRA")
                    }
                    val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    if (uri != null) {
                        context.contentResolver.openOutputStream(uri)?.use { out ->
                            compositeBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                        }
                    }
                } else {
                    val exportDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "NIRA")
                    if (!exportDir.exists()) exportDir.mkdirs()
                    val file = File(exportDir, fileName)
                    FileOutputStream(file).use { out ->
                        compositeBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                    }
                }

                withContext(Dispatchers.Main) {
                    val msg = "Saved to Downloads: $fileName"
                    _uiState.update { it.copy(exportStatusMessage = msg) }
                    onResult(msg)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    val err = "Save failed: ${e.localizedMessage}"
                    _uiState.update { it.copy(exportStatusMessage = err) }
                    onResult(err)
                }
            }
        }
    }

    fun shareSeparationSheet(context: Context, onResult: (String) -> Unit) {
        val compositeBitmap = _uiState.value.partCResult?.compositeSheetBitmap
        if (compositeBitmap == null) {
            onResult("Separation sheet bitmap not generated yet.")
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val exportDir = File(context.cacheDir, "exports")
                if (!exportDir.exists()) exportDir.mkdirs()
                val file = File(exportDir, "NIRA_Separation_Sheet_${System.currentTimeMillis()}.png")
                FileOutputStream(file).use { out ->
                    compositeBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }

                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "image/png"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, "NIRA Separation Sheet")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }

                withContext(Dispatchers.Main) {
                    context.startActivity(Intent.createChooser(shareIntent, "Share Separation Sheet via"))
                    val msg = "Opened share chooser for ${file.name}"
                    _uiState.update { it.copy(exportStatusMessage = msg) }
                    onResult(msg)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    val err = "Share failed: ${e.localizedMessage}"
                    _uiState.update { it.copy(exportStatusMessage = err) }
                    onResult(err)
                }
            }
        }
    }

    fun clearExportMessage() {
        _uiState.update { it.copy(exportStatusMessage = null) }
    }
}

