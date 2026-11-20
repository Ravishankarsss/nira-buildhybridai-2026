package com.mos.nira

import com.mos.nira.core.ai.SpeechLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The read-aloud voice follows the answer's script, so a Tamil answer gets the Tamil voice. */
class SpeechLanguageTest {

    @Test
    fun voiceFollowsTheAnswersScript() {
        assertEquals("ta", SpeechLanguage.of("இந்த முறை discharge மற்றும் digital print ஆகும்."))
        assertEquals("hi", SpeechLanguage.of("यह तरीका soft feel देता है।"))
        assertEquals("bn", SpeechLanguage.of("এই পদ্ধতি নরম অনুভূতি দেয়।"))
        assertEquals("zh", SpeechLanguage.of("这种工艺手感柔软。"))
        // Latin script: the caller uses the app's language.
        assertNull(SpeechLanguage.of("Discharge + digital print gives a soft feel."))
        assertNull(SpeechLanguage.of(""))
    }
}
