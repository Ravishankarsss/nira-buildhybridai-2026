package com.mos.nira.features.settings

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mos.nira.BuildConfig
import com.mos.nira.R
import com.mos.nira.core.ai.AiSettings
import com.mos.nira.core.ai.AppCheckDebugToken
import com.mos.nira.core.ai.HybridInferenceRepository
import com.mos.nira.core.ai.HybridInferenceResult
import com.mos.nira.core.ai.HybridMode
import com.mos.nira.core.image.KotlinImageOps
import com.mos.nira.core.locale.LocaleHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

private val Navy = Color(0xFF0B1020)
private val CardColor = Color(0xFF171F35)
private val White = Color(0xFFF5F6FF)
private val Muted = Color(0xFF9BA6C0)
private val Cyan = Color(0xFF55D6E8)

private data class SettingsInfo(
    val rulesVersion: String? = null,
    val rulesStatus: String? = null,
    val geometryLibrary: String? = null,
    val appVersion: String? = null
)

/**
 * Settings shows real state read from the app:
 * - App language selection (English, Tamil, Hindi, Spanish, French, German, Vietnamese, Bengali, Chinese)
 * - Rules file version
 * - Geometry library in use
 * - Saved analyses count
 * - App version
 */
@Composable
fun SettingsScreen(
    savedAnalysesCount: Int,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val currentLangCode by LocaleHelper.currentLanguageCode.collectAsState()
    val currentLang = LocaleHelper.supportedLanguages.find { it.code == currentLangCode }
        ?: LocaleHelper.supportedLanguages.first()
    var showLanguageDialog by remember { mutableStateOf(false) }

    val info by produceState(initialValue = SettingsInfo()) {
        value = withContext(Dispatchers.IO) {
            val rules = try {
                JSONObject(context.assets.open("print_rules.json").bufferedReader().use { it.readText() })
            } catch (e: Exception) {
                null
            }
            val version = try {
                context.packageManager.getPackageInfo(context.packageName, 0).versionName
            } catch (e: Exception) {
                null
            }
            SettingsInfo(
                rulesVersion = rules?.optString("rules_version")?.takeIf { it.isNotBlank() },
                rulesStatus = rules?.optString("status")?.takeIf { it.isNotBlank() },
                geometryLibrary = KotlinImageOps.name,
                appVersion = version
            )
        }
    }
    val notAvailable = stringResource(R.string.settings_not_available)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Navy)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        Spacer(modifier = Modifier.height(22.dp))
        Text(
            text = stringResource(R.string.settings_title),
            color = White,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold
        )

        // Language Switcher Section
        SettingsSection(stringResource(R.string.settings_section_language)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showLanguageDialog = true }
                    .padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(text = stringResource(R.string.settings_language_title), color = Muted, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = currentLang.nativeName, color = White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Text(text = stringResource(R.string.settings_language_desc), color = Muted, fontSize = 11.sp)
                }
                Text(
                    text = "Change",
                    color = Cyan,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }

        AiPrivacySection()

        SettingsSection(stringResource(R.string.settings_section_rules)) {
            SettingsRow(stringResource(R.string.settings_rules_version), info.rulesVersion ?: notAvailable)
            SettingsRow(stringResource(R.string.settings_rules_status), info.rulesStatus ?: notAvailable)
        }
        SettingsSection(stringResource(R.string.settings_section_engine)) {
            SettingsRow(stringResource(R.string.settings_engine_library), info.geometryLibrary ?: notAvailable)
        }
        SettingsSection(stringResource(R.string.settings_section_data)) {
            SettingsRow(stringResource(R.string.settings_saved_analyses), savedAnalysesCount.toString())
        }
        SettingsSection(stringResource(R.string.settings_section_app)) {
            SettingsRow(stringResource(R.string.settings_app_version), info.appVersion ?: notAvailable)
        }

        // TEMPORARY (Firebase Stage B): debug builds only. Sends one real request through
        // Firebase AI Logic and shows exactly what came back. Remove when the Hybrid AI
        // Analysis and Diagnostics screens exist. Text is English only on purpose.
        if (BuildConfig.DEBUG) {
            TestAiSection()
        }
        Spacer(modifier = Modifier.height(24.dp))
    }

    if (showLanguageDialog) {
        AlertDialog(
            onDismissRequest = { showLanguageDialog = false },
            title = {
                Text(
                    text = stringResource(R.string.language_dialog_title),
                    color = White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    LocaleHelper.supportedLanguages.forEach { lang ->
                        val isSelected = lang.code == currentLangCode
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showLanguageDialog = false
                                    LocaleHelper.setLanguage(context, lang.code)
                                    (context as? Activity)?.recreate()
                                }
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = {
                                    showLanguageDialog = false
                                    LocaleHelper.setLanguage(context, lang.code)
                                    (context as? Activity)?.recreate()
                                },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = Cyan,
                                    unselectedColor = Muted
                                )
                            )
                            Column(modifier = Modifier.padding(start = 12.dp)) {
                                Text(
                                    text = lang.nativeName,
                                    color = if (isSelected) Cyan else White,
                                    fontSize = 15.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLanguageDialog = false }) {
                    Text(text = "Close", color = Cyan)
                }
            },
            containerColor = CardColor,
            shape = RoundedCornerShape(16.dp)
        )
    }
}

/**
 * AI and privacy: Confidential mode, the real on-device status from the SDK, and the real
 * answer counters. No placeholder values: every line is read from the app or the SDK.
 */
@Composable
private fun AiPrivacySection() {
    val context = LocalContext.current
    remember { AiSettings.load(context) }
    val confidential by AiSettings.confidentialMode.collectAsState()
    val counts by AiSettings.counts.collectAsState()
    val onDeviceStatus by produceState<String?>(initialValue = null) {
        value = HybridInferenceRepository().checkOnDeviceStatus()
    }

    SettingsSection(stringResource(R.string.settings_section_ai)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.settings_confidential_title),
                    color = White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(text = stringResource(R.string.settings_confidential_desc), color = Muted, fontSize = 11.sp)
            }
            Switch(
                checked = confidential,
                onCheckedChange = { AiSettings.setConfidentialMode(context, it) },
                colors = SwitchDefaults.colors(checkedTrackColor = Cyan)
            )
        }
        SettingsRow(
            stringResource(R.string.settings_ai_on_device_status),
            onDeviceStatus ?: "…"
        )
        SettingsRow(stringResource(R.string.settings_ai_answers_device), counts.answersOnDevice.toString())
        SettingsRow(stringResource(R.string.settings_ai_answers_cloud), counts.answersInCloud.toString())
        SettingsRow(stringResource(R.string.settings_ai_images_uploaded), counts.imagesUploaded.toString())
    }
}

@Composable
private fun TestAiSection() {
    val scope = rememberCoroutineScope()
    val repository = remember { HybridInferenceRepository() }
    val context = LocalContext.current
    var isRunning by remember { mutableStateOf(false) }
    var selectedMode by remember { mutableStateOf(HybridMode.PREFER_ON_DEVICE) }
    var result by remember { mutableStateOf<HybridInferenceResult?>(null) }

    val onDeviceStatus by produceState(initialValue = "checking...") {
        value = repository.checkOnDeviceStatus()
    }

    // The App Check debug token. The SDK creates it on the first AI request, so it is read
    // again after every run. It is only shown here; it is never logged or written to a file.
    var debugToken by remember { mutableStateOf(AppCheckDebugToken.read(context)) }
    var copied by remember { mutableStateOf(false) }

    SettingsSection("DEVELOPER TEST (DEBUG BUILD ONLY)") {
        SettingsRow("On-device model status (SDK report)", onDeviceStatus)

        Text(
            text = "Target Mode",
            color = Muted,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 8.dp)
        )
        Column(modifier = Modifier.padding(vertical = 4.dp)) {
            val modes = listOf(
                HybridMode.PREFER_ON_DEVICE,
                HybridMode.ONLY_ON_DEVICE,
                HybridMode.ONLY_IN_CLOUD,
                HybridMode.PREFER_IN_CLOUD
            )
            modes.forEach { mode ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selectedMode = mode }
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = (selectedMode == mode),
                        onClick = { selectedMode = mode },
                        colors = RadioButtonDefaults.colors(
                            selectedColor = Cyan,
                            unselectedColor = Muted
                        )
                    )
                    Text(
                        text = mode.name,
                        color = if (selectedMode == mode) Cyan else White,
                        fontSize = 13.sp,
                        fontWeight = if (selectedMode == mode) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = !isRunning) {
                    isRunning = true
                    result = null
                    scope.launch {
                        result = repository.generateText(
                            prompt = "In one short sentence, what is a screen printing underbase?",
                            mode = selectedMode
                        )
                        debugToken = AppCheckDebugToken.read(context)
                        isRunning = false
                    }
                }
                .padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "Test AI", color = White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    text = "Sends one text prompt through Firebase AI Logic",
                    color = Muted,
                    fontSize = 11.sp
                )
            }
            Text(
                text = if (isRunning) "Running..." else "Run",
                color = Cyan,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 8.dp)
            )
        }

        result?.let { r ->
            SettingsRow("Inference source (reported by the SDK)", r.inferenceSource)
            SettingsRow("Requested mode", r.requestedMode.name)
            SettingsRow("Cloud model configured", r.cloudModelName)
            SettingsRow("Duration", "${r.durationMs} ms")
            if (r.text != null) SettingsRow("Response", r.text)
            if (r.errorMessage != null) SettingsRow("Error", if (r.limitReached) stringResource(R.string.ai_limit_reached) else r.errorMessage)
        }

        // App Check debug token, for registering this phone in the Firebase console.
        val token = debugToken
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "App Check debug token", color = Muted, fontSize = 12.sp)
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = token ?: "Not created yet. Tap Run once, then look here again.",
                    color = White,
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
            }
            if (token != null) {
                Text(
                    text = if (copied) "Copied" else "Copy",
                    color = Cyan,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .clickable {
                            AppCheckDebugToken.copyToClipboard(context, token)
                            copied = true
                        }
                        .padding(8.dp)
                )
            }
        }
        Text(
            text = "Firebase console > App Check > Apps > NIRA > Manage debug tokens > Add debug token. " +
                "Paste, save, wait one minute, then tap Run.",
            color = Muted,
            fontSize = 12.sp,
            lineHeight = 18.sp,
            modifier = Modifier.padding(bottom = 10.dp)
        )
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Spacer(modifier = Modifier.height(24.dp))
    Text(
        text = title,
        color = Muted,
        fontSize = 11.sp,
        letterSpacing = 1.5.sp,
        fontWeight = FontWeight.SemiBold
    )
    Spacer(modifier = Modifier.height(10.dp))
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = CardColor)
    ) {
        Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp)) {
            content()
        }
    }
}

@Composable
private fun SettingsRow(label: String, value: String) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text(text = label, color = Muted, fontSize = 12.sp)
        Spacer(modifier = Modifier.height(2.dp))
        Text(text = value, color = White, fontSize = 14.sp, lineHeight = 20.sp)
    }
}
