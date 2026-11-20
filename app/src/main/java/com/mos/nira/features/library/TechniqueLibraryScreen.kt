package com.mos.nira.features.library

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mos.nira.R
import com.mos.nira.core.advice.TechniqueLibrary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val LBg = Color(0xFF0B1020)
private val LCard = Color(0xFF151C30)
private val LInner = Color(0xFF1C2440)
private val LCyan = Color(0xFF55D6E8)
private val LViolet = Color(0xFFB3A8FF)
private val LWhite = Color(0xFFF5F6FF)
private val LMuted = Color(0xFFA8B4CE)
private val LGreen = Color(0xFF7BE0A0)
private val LAmber = Color(0xFFFFC566)

/** Every print technique in print_techniques.json, searchable, shown as the file writes it. */
@Composable
fun TechniqueLibraryScreen(onBack: () -> Unit, initialQuery: String = "") {
    val context = LocalContext.current
    val library by produceState<TechniqueLibrary.Library?>(initialValue = null) {
        value = withContext(Dispatchers.IO) {
            TechniqueLibrary.parse(context.assets.open("print_techniques.json").bufferedReader().use { it.readText() })
        }
    }
    var query by rememberSaveable { mutableStateOf(initialQuery) }
    var group by rememberSaveable { mutableStateOf<String?>(null) }
    var open by rememberSaveable { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize().background(LBg).safeDrawingPadding().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = onBack) { Text(stringResource(R.string.lib_back), color = LWhite) }
        }
        Spacer(Modifier.height(10.dp))
        Text(stringResource(R.string.card_technique_library), color = LWhite, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text(stringResource(R.string.lib_subtitle), color = LMuted, fontSize = 12.sp, lineHeight = 16.sp)
        Spacer(Modifier.height(10.dp))

        val lib = library
        if (lib == null) {
            Text(stringResource(R.string.advice_loading), color = LMuted)
            return@Column
        }
        OutlinedTextField(
            value = query, onValueChange = { query = it.take(60) }, singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(stringResource(R.string.lib_search), fontSize = 13.sp) },
            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = LWhite, unfocusedTextColor = LWhite, focusedBorderColor = LCyan)
        )
        Spacer(Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item { GroupChip(stringResource(R.string.lib_all), group == null) { group = null } }
            items(lib.groups) { g -> GroupChip(groupLabel(g), group == g) { group = if (group == g) null else g } }
        }
        val shown = remember(lib, query, group) { TechniqueLibrary.filter(lib, query, group) }
        Text(stringResource(R.string.lib_count, shown.size), color = LMuted, fontSize = 11.sp, modifier = Modifier.padding(vertical = 6.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
            if (shown.isEmpty()) item { Text(stringResource(R.string.lib_empty), color = LMuted, fontSize = 13.sp) }
            items(shown, key = { it.rule.id }) { e ->
                EntryCard(e, lib, expanded = open == e.rule.id) { open = if (open == e.rule.id) null else e.rule.id }
            }
        }
    }
}

@Composable
private fun GroupChip(text: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected, onClick = onClick, label = { Text(text, fontSize = 12.sp) },
        colors = FilterChipDefaults.filterChipColors(containerColor = LInner, labelColor = LMuted, selectedContainerColor = LCyan.copy(alpha = 0.2f), selectedLabelColor = LCyan)
    )
}

@Composable
private fun groupLabel(g: String): String = when (g) {
    "screen_ink_system" -> stringResource(R.string.lib_group_screen)
    "digital" -> stringResource(R.string.lib_group_digital)
    "special_effect" -> stringResource(R.string.lib_group_effect)
    "design_effect" -> stringResource(R.string.lib_group_design)
    "separation_route" -> stringResource(R.string.lib_group_separation)
    "transfer" -> stringResource(R.string.lib_group_transfer)
    "fabric_printing" -> stringResource(R.string.lib_group_fabric)
    else -> g.replace('_', ' ')
}

@Composable
private fun EntryCard(e: TechniqueLibrary.Entry, lib: TechniqueLibrary.Library, expanded: Boolean, onToggle: () -> Unit) {
    val r = e.rule
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(LCard).clickable(onClick = onToggle).padding(14.dp).animateContentSize()
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(r.name, color = LWhite, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text(groupLabel(r.group), color = LViolet, fontSize = 11.sp)
            }
            Text(if (expanded) "▲" else "▼", color = LMuted, fontSize = 12.sp)
        }
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(if (r.verifiedByRavi) R.string.lib_verified else R.string.lib_not_verified),
            color = if (r.verifiedByRavi) LGreen else LAmber, fontSize = 11.sp, fontWeight = FontWeight.Bold
        )
        Text(r.whatItIs, color = LMuted, fontSize = 12.sp, lineHeight = 17.sp, maxLines = if (expanded) Int.MAX_VALUE else 2)
        if (!expanded) return@Column

        Section(stringResource(R.string.lib_fit), listOf(r.artworkFit + (lib.artworkFitKey[r.artworkFit]?.let { " — $it" } ?: "")))
        Section(stringResource(R.string.lib_fibres_ok), r.suitableFibres.map(::plain))
        Section(stringResource(R.string.lib_fibres_no), r.notSuitableFibres.map(::plain))
        Section(stringResource(R.string.lib_cautions), r.cautions.map(::plain))
        r.garmentColour?.let { Section(stringResource(R.string.lib_garment), listOf(it)) }
        r.screens?.let { n -> Section(stringResource(R.string.lib_screens), listOf("$n" + (r.screensDetail?.let { " — $it" } ?: ""))) }
        if (r.processSteps.isNotEmpty()) Section(stringResource(R.string.lib_steps), r.processSteps.mapIndexed { i, s -> "${i + 1}. $s" }, bullet = false)
        if (e.params.isNotEmpty()) Section(
            stringResource(R.string.lib_params),
            e.params.map { p ->
                plain(p.key) + ": " + p.value +
                    (p.published?.let { " ($it)" } ?: "") +
                    (p.confidence?.let { " · $it" } ?: "") +
                    (p.note?.let { " — $it" } ?: "")
            }
        )
        Section(stringResource(R.string.lib_risks), r.risks)
        val sources = listOfNotNull(e.source, e.processSource)
        if (sources.isNotEmpty()) Section(stringResource(R.string.lib_source), sources)
    }
}

@Composable
private fun Section(title: String, lines: List<String>, bullet: Boolean = true) {
    if (lines.isEmpty()) return
    Spacer(Modifier.height(8.dp))
    Text(title, color = LCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    lines.forEach { Text(if (bullet) "• $it" else it, color = LWhite, fontSize = 12.sp, lineHeight = 17.sp) }
}

private fun plain(s: String) = s.replace('_', ' ')
