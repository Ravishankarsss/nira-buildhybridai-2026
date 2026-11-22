package com.mos.nira.features.artwork.presentation

import com.mos.nira.R

import androidx.compose.ui.res.stringResource

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mos.nira.core.image.ColorConverter
import com.mos.nira.features.artwork.domain.ShadeEntry
import com.mos.nira.features.artwork.domain.ShadeLibrary

private val NavyBg = Color(0xFF0B1020)
private val CardBg = Color(0xFF171F35)
private val Violet = Color(0xFF8B7CFF)
private val Cyan = Color(0xFF55D6E8)
private val TextWhite = Color(0xFFF5F6FF)
private val TextMuted = Color(0xFF9BA6C0)

@Composable
fun ShadeLibraryScreen(
    libraries: List<ShadeLibrary>,
    allShades: List<ShadeEntry>,
    selectedTypeFilter: String?,
    onToggleLibrary: (name: String, enabled: Boolean) -> Unit,
    onSelectTypeFilter: (String?) -> Unit,
    onImportCsv: (Uri, libraryName: String) -> Unit,
    onAddManualShade: (type: String, code: String, name: String, labL: Double, labA: Double, labB: Double) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var importLibName by remember { mutableStateOf("Factory_Standard") }
    var pendingImportUri by remember { mutableStateOf<Uri?>(null) }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            pendingImportUri = uri
            showImportDialog = true
        }
    }

    // Add manual shade dialog state
    var inputType by remember { mutableStateOf("TCX") }
    var inputCode by remember { mutableStateOf("") }
    var inputName by remember { mutableStateOf("") }
    var inputHexOrLab by remember { mutableStateOf("#") }
    var useHexInput by remember { mutableStateOf(true) }
    var inputL by remember { mutableStateOf("50.0") }
    var inputA by remember { mutableStateOf("0.0") }
    var inputB by remember { mutableStateOf("0.0") }

    if (showImportDialog && pendingImportUri != null) {
        AlertDialog(
            onDismissRequest = { showImportDialog = false },
            title = { Text(stringResource(R.string.shade_import_title), color = TextWhite, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(stringResource(R.string.shade_import_name), color = TextMuted, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = importLibName,
                        onValueChange = { importLibName = it },
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedTextColor = TextWhite,
                            unfocusedTextColor = TextWhite,
                            focusedContainerColor = Color(0xFF0F1526),
                            unfocusedContainerColor = Color(0xFF0F1526)
                        )
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onImportCsv(pendingImportUri!!, importLibName)
                        showImportDialog = false
                        pendingImportUri = null
                    }
                ) {
                    Text(stringResource(R.string.shade_import_csv), color = Cyan, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showImportDialog = false }) {
                    Text(stringResource(R.string.common_cancel), color = TextWhite)
                }
            },
            containerColor = CardBg,
            shape = RoundedCornerShape(16.dp)
        )
    }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text(stringResource(R.string.shade_add_title), color = TextWhite, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.shade_type), color = TextMuted, fontSize = 11.sp)
                    OutlinedTextField(
                        value = inputType,
                        onValueChange = { inputType = it.uppercase() },
                        singleLine = true,
                        colors = TextFieldDefaults.colors(focusedTextColor = TextWhite, unfocusedTextColor = TextWhite, focusedContainerColor = Color(0xFF0F1526), unfocusedContainerColor = Color(0xFF0F1526))
                    )

                    Text(stringResource(R.string.shade_code), color = TextMuted, fontSize = 11.sp)
                    OutlinedTextField(
                        value = inputCode,
                        onValueChange = { inputCode = it },
                        singleLine = true,
                        colors = TextFieldDefaults.colors(focusedTextColor = TextWhite, unfocusedTextColor = TextWhite, focusedContainerColor = Color(0xFF0F1526), unfocusedContainerColor = Color(0xFF0F1526))
                    )

                    Text(stringResource(R.string.shade_name), color = TextMuted, fontSize = 11.sp)
                    OutlinedTextField(
                        value = inputName,
                        onValueChange = { inputName = it },
                        singleLine = true,
                        colors = TextFieldDefaults.colors(focusedTextColor = TextWhite, unfocusedTextColor = TextWhite, focusedContainerColor = Color(0xFF0F1526), unfocusedContainerColor = Color(0xFF0F1526))
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(if (useHexInput) stringResource(R.string.shade_by_hex) else stringResource(R.string.shade_by_lab), color = Cyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        TextButton(onClick = { useHexInput = !useHexInput }) {
                            Text(if (useHexInput) stringResource(R.string.shade_to_lab) else stringResource(R.string.shade_to_hex), fontSize = 10.sp, color = Violet)
                        }
                    }

                    if (useHexInput) {
                        OutlinedTextField(
                            value = inputHexOrLab,
                            onValueChange = { inputHexOrLab = it.uppercase() },
                            placeholder = { Text("#RRGGBB") },
                            singleLine = true,
                            colors = TextFieldDefaults.colors(focusedTextColor = TextWhite, unfocusedTextColor = TextWhite, focusedContainerColor = Color(0xFF0F1526), unfocusedContainerColor = Color(0xFF0F1526))
                        )
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            OutlinedTextField(
                                value = inputL,
                                onValueChange = { inputL = it },
                                placeholder = { Text("L*") },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                colors = TextFieldDefaults.colors(focusedTextColor = TextWhite, unfocusedTextColor = TextWhite, focusedContainerColor = Color(0xFF0F1526), unfocusedContainerColor = Color(0xFF0F1526))
                            )
                            OutlinedTextField(
                                value = inputA,
                                onValueChange = { inputA = it },
                                placeholder = { Text("a*") },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                colors = TextFieldDefaults.colors(focusedTextColor = TextWhite, unfocusedTextColor = TextWhite, focusedContainerColor = Color(0xFF0F1526), unfocusedContainerColor = Color(0xFF0F1526))
                            )
                            OutlinedTextField(
                                value = inputB,
                                onValueChange = { inputB = it },
                                placeholder = { Text("b*") },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                colors = TextFieldDefaults.colors(focusedTextColor = TextWhite, unfocusedTextColor = TextWhite, focusedContainerColor = Color(0xFF0F1526), unfocusedContainerColor = Color(0xFF0F1526))
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (inputCode.isNotBlank() && inputName.isNotBlank()) {
                            var finalL = 50.0
                            var finalA = 0.0
                            var finalB = 0.0

                            if (useHexInput) {
                                try {
                                    val rgb = ColorConverter.hexToRgb(inputHexOrLab)
                                    val lab = ColorConverter.rgbToLab(rgb.r, rgb.g, rgb.b)
                                    finalL = lab.l
                                    finalA = lab.a
                                    finalB = lab.b
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            } else {
                                finalL = inputL.toDoubleOrNull() ?: 50.0
                                finalA = inputA.toDoubleOrNull() ?: 0.0
                                finalB = inputB.toDoubleOrNull() ?: 0.0
                            }

                            onAddManualShade(inputType, inputCode, inputName, finalL, finalA, finalB)
                            showAddDialog = false
                            inputCode = ""
                            inputName = ""
                        }
                    }
                ) {
                    Text(stringResource(R.string.shade_save), color = Cyan, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text(stringResource(R.string.common_cancel), color = TextWhite)
                }
            },
            containerColor = CardBg,
            shape = RoundedCornerShape(16.dp)
        )
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(NavyBg)
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(10.dp))

            // Navigation header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(onClick = onBack, shape = RoundedCornerShape(10.dp)) {
                    Text(stringResource(R.string.shade_back_studio), color = TextWhite)
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { filePicker.launch("text/*") },
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(stringResource(R.string.shade_import_csv), color = Cyan, fontSize = 11.sp)
                    }

                    Button(
                        onClick = { showAddDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = Violet),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(stringResource(R.string.shade_add), color = TextWhite, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Notice Banner
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF141A2D))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(stringResource(R.string.shade_privacy_title), color = Violet, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.shade_privacy_body),
                        color = TextMuted,
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Libraries Active Toggles
            Text(stringResource(R.string.shade_active), color = Violet, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
        }

        items(libraries) { lib ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = CardBg)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(lib.name, color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(stringResource(if (lib.isBuiltIn) R.string.shade_lib_builtin else R.string.shade_lib_local, lib.entryCount), color = TextMuted, fontSize = 11.sp)
                    }

                    Switch(
                        checked = lib.isEnabled,
                        onCheckedChange = { onToggleLibrary(lib.name, it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Cyan,
                            checkedTrackColor = Violet
                        )
                    )
                }
            }
        }

        // Type Filter Chips (e.g. ALL, TCX, TPG, TN, TSX, FACTORY, BUYER, NIRA_DEMO)
        item {
            Spacer(modifier = Modifier.height(6.dp))
            Text(stringResource(R.string.shade_filter), color = Violet, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
            Spacer(modifier = Modifier.height(8.dp))

            val distinctTypes = listOf("ALL") + allShades.map { it.type }.distinct()

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(distinctTypes) { type ->
                    val isSelected = (selectedTypeFilter == null && type == "ALL") || (selectedTypeFilter == type)
                    OutlinedButton(
                        onClick = { onSelectTypeFilter(if (type == "ALL") null else type) },
                        shape = RoundedCornerShape(8.dp),
                        border = if (isSelected) androidx.compose.foundation.BorderStroke(1.5.dp, Cyan) else null,
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (isSelected) Color(0xFF232D48) else CardBg
                        )
                    ) {
                        Text(type, color = if (isSelected) Cyan else TextWhite, fontSize = 11.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            Text(stringResource(R.string.shade_entries, allShades.size), color = Violet, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
        }

        val filteredShades = if (selectedTypeFilter.isNullOrBlank()) allShades else allShades.filter { it.type.equals(selectedTypeFilter, ignoreCase = true) }

        items(filteredShades.take(50)) { shade ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = CardBg)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("[${shade.type}] ${shade.code}", color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(shade.name, color = TextMuted, fontSize = 12.sp)
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text("${shade.formattedLab}  •  ${shade.libraryName}", color = Cyan, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(30.dp))
        }
    }
}
