package com.mos.nira

import android.app.Application
import android.content.pm.ApplicationInfo
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.appCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.initialize

/**
 * Starts Firebase and App Check before anything else in the app runs, so App Check is always
 * ready before the first Firebase AI Logic request.
 *
 * Development builds use the App Check debug provider. On first launch it prints a debug
 * secret in Logcat (tag "DebugAppCheckProvider"); register that secret in the Firebase console
 * (see docs/FIREBASE_SETUP.md). Never commit the secret.
 *
 * Release builds install no provider yet. Play Integrity is the planned production provider
 * and is a documented future task.
 */
class NiraApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        Firebase.initialize(context = this)

        val isDebuggable = (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        val judgeToken = BuildConfig.JUDGE_APP_CHECK_TOKEN
        if (judgeToken.isNotBlank()) {
            // Judge build (route A): put the token registered for the judge app where the debug
            // provider keeps its secret, before App Check starts. The token is never logged.
            val prefs = getSharedPreferences(
                String.format(com.mos.nira.core.ai.AppCheckDebugToken.PREFS_TEMPLATE, FirebaseApp.getInstance().persistenceKey),
                MODE_PRIVATE
            )
            if (prefs.getString(com.mos.nira.core.ai.AppCheckDebugToken.DEBUG_SECRET_KEY, null) != judgeToken) {
                prefs.edit().putString(com.mos.nira.core.ai.AppCheckDebugToken.DEBUG_SECRET_KEY, judgeToken).commit()
            }
        }
        if (isDebuggable || judgeToken.isNotBlank()) {
            Firebase.appCheck.installAppCheckProviderFactory(
                DebugAppCheckProviderFactory.getInstance(),
            )
        }
    }
}
