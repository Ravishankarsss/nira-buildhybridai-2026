package com.mos.nira.features.advice

import androidx.lifecycle.ViewModel
import com.mos.nira.core.advice.ArtworkKind
import com.mos.nira.core.advice.Fibre
import com.mos.nira.core.advice.GarmentTone
import com.mos.nira.features.ai.ArtworkKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The user's print method choices for one artwork. */
data class PrintMethodChoices(
    val key: ArtworkKey?,
    val fibre: Fibre? = null,
    val artwork: ArtworkKind? = null,
    val artworkFromAi: Boolean = false,
    val garment: GarmentTone? = null,
    val garmentFromMeasure: Boolean = false
)

/**
 * Keeps the fabric, artwork and garment choices while the user moves between the Colour screen
 * and the Print Report. Choices for a different artwork start fresh.
 */
class PrintMethodChoicesViewModel : ViewModel() {

    private val _choices = MutableStateFlow(PrintMethodChoices(key = null))
    val choices: StateFlow<PrintMethodChoices> = _choices.asStateFlow()

    fun update(key: ArtworkKey?, change: (PrintMethodChoices) -> PrintMethodChoices) {
        val current = _choices.value.takeIf { it.key == key } ?: PrintMethodChoices(key)
        _choices.value = change(current).copy(key = key)
    }

    fun choicesFor(key: ArtworkKey?): PrintMethodChoices? = _choices.value.takeIf { key != null && it.key == key }
}
