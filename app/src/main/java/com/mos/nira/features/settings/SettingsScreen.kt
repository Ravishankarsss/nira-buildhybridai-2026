package com.mos.nira.features.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mos.nira.R
import com.mos.nira.core.image.OpenCvImageOps
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

private val Navy = Color(0xFF0B1020)
private val CardColor = Color(0xFF171F35)
private val White = Color(0xFFF5F6FF)
private val Muted = Color(0xFF9BA6C0)

private data class SettingsInfo(
    val rulesVersion: String? = null,
    val rulesStatus: String? = null,
    val geometryLibrary: String? = null,
    val appVersion: String? = null
)

/**
 * Settings shows only real state read from the app: the rules file version, the geometry
 * library actually loaded, saved data and the app version. Language, Confidential mode and the
 * AI status card are added when those features exist; nothing here is a placeholder.
 */
@Composable
fun SettingsScreen(
    savedAnalysesCount: Int,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
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
                geometryLibrary = OpenCvImageOps.getOrFallback().name,
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
        Spacer(modifier = Modifier.height(24.dp))
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
