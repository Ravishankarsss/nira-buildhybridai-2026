package com.mos.nira.features.artwork.data

import android.content.Context
import com.mos.nira.core.image.ColorConverter
import com.mos.nira.features.artwork.domain.ShadeEntry
import com.mos.nira.features.artwork.domain.ShadeLibrary
import com.mos.nira.features.artwork.domain.ShadeMatch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStream
import java.io.InputStreamReader

/**
 * Repository for managing both built-in asset shade library (nira_demo_shades.csv)
 * and user-imported CSV libraries stored in app-private storage (`filesDir/user_libraries/`).
 */
class ShadeLibraryRepository(private val context: Context) {

    private val userLibDir: File
        get() = File(context.filesDir, "user_libraries").apply {
            if (!exists()) mkdirs()
        }

    /**
     * Loads the built-in generic demo library from assets/nira_demo_shades.csv.
     */
    suspend fun loadBuiltInDemoLibrary(): List<ShadeEntry> = withContext(Dispatchers.IO) {
        try {
            context.assets.open("nira_demo_shades.csv").use { inputStream ->
                parseCsvShades(inputStream, libraryName = "Built-in NIRA Demo", isUser = false)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    /**
     * Loads all user-imported shade libraries from app-private storage.
     */
    suspend fun loadUserLibraries(): Map<String, List<ShadeEntry>> = withContext(Dispatchers.IO) {
        val result = mutableMapOf<String, List<ShadeEntry>>()
        try {
            val files = userLibDir.listFiles { f -> f.extension.equals("csv", ignoreCase = true) } ?: return@withContext result
            for (file in files) {
                val libName = file.nameWithoutExtension.replace('_', ' ')
                file.inputStream().use { stream ->
                    val shades = parseCsvShades(stream, libraryName = libName, isUser = true)
                    result[libName] = shades
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        result
    }

    /**
     * Imports a user CSV file into app-private storage.
     * Expected CSV format: type,code,name,L,a,b (header optional or verified).
     */
    suspend fun importUserLibraryFromStream(
        inputStream: InputStream,
        rawLibraryName: String
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val safeName = rawLibraryName.trim().replace(Regex("[^a-zA-Z0-9_-]"), "_").ifBlank { "User_Library" }
            val targetFile = File(userLibDir, "$safeName.csv")

            val content = inputStream.bufferedReader().readText()
            targetFile.writeText(content)

            // Validate and count shades
            val shades = targetFile.inputStream().use { stream ->
                parseCsvShades(stream, libraryName = rawLibraryName, isUser = true)
            }
            Result.success(shades.size)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    /**
     * Adds a single custom shade manually to the default user custom library.
     */
    suspend fun addManualShade(
        type: String,
        code: String,
        name: String,
        labL: Double,
        labA: Double,
        labB: Double,
        targetLibrary: String = "My_Custom_Shades"
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val targetFile = File(userLibDir, "$targetLibrary.csv")
            val isNew = !targetFile.exists()
            targetFile.appendText(
                (if (isNew) "type,code,name,L,a,b\n" else "") +
                        "${type.trim()},${code.trim()},${name.trim()},$labL,$labA,$labB\n"
            )
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Finds the top [topN] nearest shades using CIEDE2000.
     */
    fun findNearestShades(
        targetLab: ColorConverter.Lab,
        candidates: List<ShadeEntry>,
        topN: Int = 3,
        typeFilter: String? = null
    ): List<ShadeMatch> {
        val filtered = if (typeFilter.isNullOrBlank()) candidates else candidates.filter { it.type.equals(typeFilter, ignoreCase = true) }

        return filtered
            .map { shade ->
                val shadeLab = ColorConverter.Lab(shade.labL, shade.labA, shade.labB)
                val dE00 = ColorConverter.deltaE00(targetLab, shadeLab)
                val grade = ColorConverter.matchGrade(dE00)
                ShadeMatch(shade = shade, deltaE00 = dE00, grade = grade)
            }
            .sortedBy { it.deltaE00 }
            .take(topN)
    }

    /**
     * Parses standard CSV format: type,code,name,L,a,b
     */
    fun parseCsvShades(
        inputStream: InputStream,
        libraryName: String,
        isUser: Boolean
    ): List<ShadeEntry> {
        val list = ArrayList<ShadeEntry>()
        val reader = BufferedReader(InputStreamReader(inputStream))
        var lineIndex = 0

        reader.forEachLine { line ->
            lineIndex++
            val trimmed = line.trim()
            if (trimmed.isNotBlank()) {
                val parts = trimmed.split(',')
                if (parts.size >= 6) {
                    val pType = parts[0].trim()
                    val pCode = parts[1].trim()
                    val pName = parts[2].trim()

                    // Skip header line if detected
                    if (lineIndex == 1 && (pType.equals("type", ignoreCase = true) || pCode.equals("code", ignoreCase = true))) {
                        return@forEachLine
                    }

                    val l = parts[3].trim().toDoubleOrNull()
                    val a = parts[4].trim().toDoubleOrNull()
                    val b = parts[5].trim().toDoubleOrNull()

                    if (l != null && a != null && b != null) {
                        list.add(
                            ShadeEntry(
                                type = pType,
                                code = pCode,
                                name = pName,
                                labL = l,
                                labA = a,
                                labB = b,
                                libraryName = libraryName,
                                isUserCreated = isUser
                            )
                        )
                    }
                }
            }
        }
        return list
    }
}
