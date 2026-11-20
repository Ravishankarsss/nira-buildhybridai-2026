package com.mos.nira.core.ai

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import com.google.firebase.FirebaseApp
import com.mos.nira.BuildConfig

/**
 * Reads the Firebase App Check debug token so it can be shown in the debug-only Developer Test
 * card. Needed because some phones (for example the vivo V60e) hide debug-level Logcat lines,
 * so the line the SDK prints with the token never appears.
 *
 * Where the token lives was checked in the firebase-appcheck-debug 20.1.0 library itself
 * (class internal.StorageHelper, used by internal.DebugAppCheckProvider):
 *   SharedPreferences file "com.google.firebase.appcheck.debug.store.<FirebaseApp persistence key>"
 *   key "com.google.firebase.appcheck.debug.DEBUG_SECRET"
 * The SDK creates the token the first time an App Check token is requested, so it does not
 * exist until one AI request has been tried. Re-check these names when the Firebase BoM changes.
 *
 * Rules: the token is never logged, never written to any file, and never read in release
 * builds (every function returns early unless BuildConfig.DEBUG).
 */
object AppCheckDebugToken {

    const val PREFS_TEMPLATE = "com.google.firebase.appcheck.debug.store.%s"
    const val DEBUG_SECRET_KEY = "com.google.firebase.appcheck.debug.DEBUG_SECRET"

    /** The debug token, or null if this is a release build or the SDK has not created it yet. */
    fun read(context: Context): String? {
        if (!BuildConfig.DEBUG) return null
        return try {
            val persistenceKey = FirebaseApp.getInstance().persistenceKey
            context.applicationContext
                .getSharedPreferences(String.format(PREFS_TEMPLATE, persistenceKey), Context.MODE_PRIVATE)
                .getString(DEBUG_SECRET_KEY, null)
                ?.takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            null
        }
    }

    /** Copies the token to the clipboard, marked as sensitive so Android does not preview it. */
    fun copyToClipboard(context: Context, token: String) {
        if (!BuildConfig.DEBUG) return
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        val clip = ClipData.newPlainText("App Check debug token", token)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            clip.description.extras = PersistableBundle().apply {
                putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
            }
        }
        clipboard.setPrimaryClip(clip)
    }
}
