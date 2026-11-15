package com.mos.nira.core.locale

import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import androidx.annotation.StringRes
import com.mos.nira.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

data class SupportedLanguage(
    val code: String,
    @StringRes val nameRes: Int,
    val nativeName: String
)

object LocaleHelper {

    private const val PREFS_NAME = "nira_locale_prefs"
    private const val KEY_LANGUAGE = "selected_language"

    val supportedLanguages = listOf(
        SupportedLanguage("en", R.string.lang_en, "English"),
        SupportedLanguage("ta", R.string.lang_ta, "தமிழ் (Tamil)"),
        SupportedLanguage("hi", R.string.lang_hi, "हिन्दी (Hindi)"),
        SupportedLanguage("es", R.string.lang_es, "Español (Spanish)"),
        SupportedLanguage("fr", R.string.lang_fr, "Français (French)"),
        SupportedLanguage("de", R.string.lang_de, "Deutsch (German)"),
        SupportedLanguage("vi", R.string.lang_vi, "Tiếng Việt (Vietnamese)"),
        SupportedLanguage("bn", R.string.lang_bn, "বাংলা (Bengali)"),
        SupportedLanguage("zh", R.string.lang_zh, "中文 (Chinese)")
    )

    private val _currentLanguageCode = MutableStateFlow("en")
    val currentLanguageCode: StateFlow<String> = _currentLanguageCode.asStateFlow()

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedLang = prefs.getString(KEY_LANGUAGE, null)

        val codeToApply = if (!savedLang.isNullOrBlank()) {
            savedLang
        } else {
            // Check system language
            val systemLang = Locale.getDefault().language
            if (supportedLanguages.any { it.code.equals(systemLang, ignoreCase = true) }) {
                systemLang
            } else {
                "en"
            }
        }

        _currentLanguageCode.value = codeToApply
        applyLocale(context, codeToApply, persist = false)
    }

    fun setLanguage(context: Context, languageCode: String) {
        val validCode = if (supportedLanguages.any { it.code == languageCode }) languageCode else "en"
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_LANGUAGE, validCode).apply()

        _currentLanguageCode.value = validCode
        applyLocale(context, validCode, persist = true)
    }

    fun getLocalizedContext(context: Context): Context {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val code = prefs.getString(KEY_LANGUAGE, null) ?: _currentLanguageCode.value
        _currentLanguageCode.value = code
        val locale = Locale(code)
        Locale.setDefault(locale)

        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        return context.createConfigurationContext(config)
    }

    private fun applyLocale(context: Context, languageCode: String, persist: Boolean) {
        val targetLocale = Locale(languageCode)
        Locale.setDefault(targetLocale)

        // Android 13+ (API 33) system-level per-app language
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            try {
                val localeManager = context.getSystemService(LocaleManager::class.java)
                localeManager?.applicationLocales = LocaleList(targetLocale)
            } catch (e: Exception) {
                // Fallback gracefully
            }
        }

        // Update legacy resources configuration for immediate in-process access
        val resources = context.resources
        val config = Configuration(resources.configuration)
        config.setLocale(targetLocale)
        @Suppress("DEPRECATION")
        resources.updateConfiguration(config, resources.displayMetrics)

        val appCtx = context.applicationContext
        if (appCtx != null && appCtx != context) {
            @Suppress("DEPRECATION")
            appCtx.resources.updateConfiguration(config, appCtx.resources.displayMetrics)
        }
    }
}
