package com.mos.nira.features.artwork.presentation

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import com.mos.nira.R
import com.mos.nira.core.image.ColourName

private val HEX = Regex("^#[0-9A-Fa-f]{6}$")

/** "Navy", "Dark green", "Light grey" in the app language, for a hex colour. Hex if unreadable. */
@Composable
fun colourName(hex: String): String {
    val name = ColourName.of(hex) ?: return hex
    val base = stringResource(
        when (name.base) {
            ColourName.Base.WHITE -> R.string.colour_white
            ColourName.Base.LIGHT_GREY -> R.string.colour_light_grey
            ColourName.Base.GREY -> R.string.colour_grey
            ColourName.Base.DARK_GREY -> R.string.colour_dark_grey
            ColourName.Base.BLACK -> R.string.colour_black
            ColourName.Base.RED -> R.string.colour_red
            ColourName.Base.PINK -> R.string.colour_pink
            ColourName.Base.MAGENTA -> R.string.colour_magenta
            ColourName.Base.ORANGE -> R.string.colour_orange
            ColourName.Base.BROWN -> R.string.colour_brown
            ColourName.Base.YELLOW -> R.string.colour_yellow
            ColourName.Base.OLIVE -> R.string.colour_olive
            ColourName.Base.CREAM -> R.string.colour_cream
            ColourName.Base.GREEN -> R.string.colour_green
            ColourName.Base.TEAL -> R.string.colour_teal
            ColourName.Base.BLUE -> R.string.colour_blue
            ColourName.Base.NAVY -> R.string.colour_navy
            ColourName.Base.PURPLE -> R.string.colour_purple
        }
    )
    val locale = LocalConfiguration.current.locales[0]
    val text = when (name.shade) {
        ColourName.Shade.NONE -> return base
        ColourName.Shade.DARK -> stringResource(R.string.colour_dark, base.lowercase(locale))
        ColourName.Shade.LIGHT -> stringResource(R.string.colour_light, base.lowercase(locale))
    }
    return text.replaceFirstChar { it.titlecase(locale) }
}

// Phone camera names, and NIRA's own camera ("garment_<time>.jpg", GarmentCameraScreen).
private val CAMERA_NAME = Regex("^(IMG|PXL|DSC|Screenshot|garment)?[_-]?[0-9_ -]{6,}\\.(png|jpe?g|webp)$", RegexOption.IGNORE_CASE)

/**
 * A readable title for a saved job. A real file name is kept (without its extension); a camera
 * or gallery number becomes "Artwork · navy, red" or "Garment photo · navy" from its print colours.
 */
@Composable
fun jobTitle(fileName: String, inputKind: String?, mainHexes: List<String>): String {
    if (!CAMERA_NAME.matches(fileName.trim()) || mainHexes.isEmpty()) return fileName.substringBeforeLast('.')
    val colours = mainHexes.map { colourName(it).lowercase(LocalConfiguration.current.locales[0]) }.distinct().joinToString(", ")
    return stringResource(if (inputKind == "PHOTO") R.string.job_photo else R.string.job_artwork, colours)
}

/** A screen or ink label: its name if it has one, or the colour name when the label is a hex code. */
@Composable
fun inkLabel(label: String): String = if (HEX.matches(label)) colourName(label) else label
