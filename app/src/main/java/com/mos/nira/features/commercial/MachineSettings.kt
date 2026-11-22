package com.mos.nira.features.commercial

import android.content.Context

/**
 * The factory's own printing machine: how many screens (heads) it can print in one pass.
 * A factory setting entered by the user, kept on this phone; not a rule value.
 */
object MachineSettings {
    private const val PREFS = "machine_settings"
    private const val KEY_SCREENS = "screens"
    const val MIN_SCREENS = 2
    const val MAX_SCREENS = 16

    /** Null until the user sets it. */
    fun screens(context: Context): Int? =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(KEY_SCREENS, -1).takeIf { it in MIN_SCREENS..MAX_SCREENS }

    fun setScreens(context: Context, screens: Int) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putInt(KEY_SCREENS, screens.coerceIn(MIN_SCREENS, MAX_SCREENS)).apply()
    }
}
