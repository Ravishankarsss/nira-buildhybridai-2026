package com.mos.nira.features.artwork

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * UI / Instrumentation test verifying package context and base configuration for vivo V60e target.
 */
@RunWith(AndroidJUnit4::class)
class ArtworkStudioInstrumentationTest {

    @Test
    fun verifyAppContext() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("com.mos.nira", appContext.packageName)
        assertNotNull(appContext.filesDir)
    }
}
