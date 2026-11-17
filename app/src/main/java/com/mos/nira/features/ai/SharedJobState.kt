package com.mos.nira.features.ai

import android.content.ContextWrapper
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner

/**
 * Identifies the artwork currently open, so AI results and choices made for one artwork are
 * never shown for another. The bitmap object changes whenever a different artwork is loaded.
 */
data class ArtworkKey(val bitmapId: Int, val printColourHexes: List<String>) {
    companion object {
        fun of(bitmap: Bitmap?, printColourHexes: List<String>): ArtworkKey? =
            bitmap?.let { ArtworkKey(System.identityHashCode(it), printColourHexes) }
    }
}

/**
 * The activity's ViewModel store, so the AI read and the print method choices are shared by
 * the Colour screen and the Print Report screen.
 */
@Composable
fun activityViewModelStoreOwner(): ViewModelStoreOwner {
    var context = LocalContext.current
    while (context is ContextWrapper) {
        if (context is ViewModelStoreOwner) return context
        context = context.baseContext
    }
    return checkNotNull(LocalViewModelStoreOwner.current) { "No ViewModelStoreOwner" }
}
