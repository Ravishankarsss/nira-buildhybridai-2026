package com.mos.nira.features.artwork.data

import com.mos.nira.features.artwork.domain.ArtworkAnalysisRecord
import com.mos.nira.features.artwork.domain.ColorGroup
import com.mos.nira.features.artwork.domain.DominantColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Local file-based JSON repository for persistent storage of Artwork Analysis History.
 * Stores structured analysis metadata in app-private storage (`context.filesDir/analyses/`).
 */
class ArtworkRepository(private val context: android.content.Context) {

    private val analysesDir: File
        get() = File(context.filesDir, "analyses").apply {
            if (!exists()) mkdirs()
        }

    suspend fun saveAnalysis(record: ArtworkAnalysisRecord): Boolean = withContext(Dispatchers.IO) {
        try {
            val json = serializeRecord(record)
            val file = File(analysesDir, "${record.id}.json")
            file.writeText(json.toString())
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun getAllAnalyses(): List<ArtworkAnalysisRecord> = withContext(Dispatchers.IO) {
        try {
            val files = analysesDir.listFiles { f -> f.extension == "json" } ?: return@withContext emptyList()
            val list = ArrayList<ArtworkAnalysisRecord>()
            for (file in files) {
                try {
                    val content = file.readText()
                    val json = JSONObject(content)
                    list.add(deserializeRecord(json))
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            list.sortedByDescending { it.timestamp }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    suspend fun getAnalysisById(id: String): ArtworkAnalysisRecord? = withContext(Dispatchers.IO) {
        try {
            val file = File(analysesDir, "$id.json")
            if (!file.exists()) return@withContext null
            val json = JSONObject(file.readText())
            deserializeRecord(json)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun deleteAnalysis(id: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val file = File(analysesDir, "$id.json")
            val record = if (file.exists()) deserializeRecord(JSONObject(file.readText())) else null

            record?.thumbnailPath?.let { path ->
                val thumbFile = File(path)
                if (thumbFile.exists()) thumbFile.delete()
            }

            // Delete the private artwork copy, but only a file inside this app's own storage.
            record?.imageUri?.takeIf { it.startsWith("file:") }?.let { uri ->
                val copy = File(android.net.Uri.parse(uri).path ?: return@let)
                val artworkDir = File(context.filesDir, "artworks").canonicalPath
                if (copy.canonicalPath.startsWith(artworkDir) && copy.exists()) copy.delete()
            }

            if (file.exists()) file.delete() else true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun serializeRecord(record: ArtworkAnalysisRecord): JSONObject {
        val root = JSONObject()
        root.put("id", record.id)
        root.put("fileName", record.fileName)
        root.put("imageUri", record.imageUri)
        root.put("thumbnailPath", record.thumbnailPath ?: "")
        root.put("width", record.width)
        root.put("height", record.height)
        root.put("fileSizeBytes", record.fileSizeBytes)
        root.put("timestamp", record.timestamp)
        root.put("algorithmVersion", record.algorithmVersion)
        record.measuredPrintColourCount?.let { root.put("measuredPrintColourCount", it) }
        record.inputKind?.let { root.put("inputKind", it) }
        root.put("inputKindConfirmed", record.inputKindConfirmed)

        val paletteArray = JSONArray()
        for (color in record.palette) {
            val cJson = JSONObject().apply {
                put("id", color.id)
                put("hex", color.hex)
                put("r", color.r)
                put("g", color.g)
                put("b", color.b)
                put("labL", color.labL)
                put("labA", color.labA)
                put("labB", color.labB)
                put("pixelCount", color.pixelCount)
                put("pixelPercentage", color.pixelPercentage.toDouble())
                put("groupId", color.groupId)
                put("isLocked", color.isLocked)
                put("isGarmentBase", color.isGarmentBase)
            }
            paletteArray.put(cJson)
        }
        root.put("palette", paletteArray)

        val groupsArray = JSONArray()
        for (group in record.colorGroups) {
            val gJson = JSONObject().apply {
                put("groupId", group.groupId)
                put("totalPercentage", group.totalPercentage.toDouble())
                put("primaryColorId", group.primaryColor.id)
                val memberIds = JSONArray()
                group.members.forEach { memberIds.put(it.id) }
                put("memberIds", memberIds)
            }
            groupsArray.put(gJson)
        }
        root.put("colorGroups", groupsArray)

        record.garmentColor?.let { g ->
            val gJson = JSONObject().apply {
                put("id", g.id)
                put("hex", g.hex)
                put("r", g.r)
                put("g", g.g)
                put("b", g.b)
                put("labL", g.labL)
                put("labA", g.labA)
                put("labB", g.labB)
            }
            root.put("garmentColor", gJson)
        }

        return root
    }

    private fun deserializeRecord(json: JSONObject): ArtworkAnalysisRecord {
        val id = json.getString("id")
        val fileName = json.getString("fileName")
        val imageUri = json.getString("imageUri")
        val thumbnailPath = json.optString("thumbnailPath").takeIf { it.isNotBlank() }
        val width = json.getInt("width")
        val height = json.getInt("height")
        val fileSizeBytes = json.optLong("fileSizeBytes", 0L)
        val timestamp = json.getLong("timestamp")
        val algorithmVersion = json.optString("algorithmVersion", "2.0.0-ciede2000-automerge")

        val palette = ArrayList<DominantColor>()
        val paletteArray = json.optJSONArray("palette") ?: JSONArray()
        for (i in 0 until paletteArray.length()) {
            val cJson = paletteArray.getJSONObject(i)
            palette.add(
                DominantColor(
                    id = cJson.getInt("id"),
                    hex = cJson.getString("hex"),
                    r = cJson.getInt("r"),
                    g = cJson.getInt("g"),
                    b = cJson.getInt("b"),
                    labL = cJson.getDouble("labL"),
                    labA = cJson.getDouble("labA"),
                    labB = cJson.getDouble("labB"),
                    pixelCount = cJson.optInt("pixelCount", 0),
                    pixelPercentage = cJson.getDouble("pixelPercentage").toFloat(),
                    groupId = cJson.optInt("groupId", cJson.getInt("id")),
                    isLocked = cJson.optBoolean("isLocked", false),
                    isGarmentBase = cJson.optBoolean("isGarmentBase", false)
                )
            )
        }

        val paletteMap = palette.associateBy { it.id }
        val groups = ArrayList<ColorGroup>()
        val groupsArray = json.optJSONArray("colorGroups") ?: JSONArray()
        for (i in 0 until groupsArray.length()) {
            val gJson = groupsArray.getJSONObject(i)
            val groupId = gJson.getInt("groupId")
            val totalPercentage = gJson.getDouble("totalPercentage").toFloat()
            val primaryId = gJson.getInt("primaryColorId")
            val primaryColor = paletteMap[primaryId] ?: palette.firstOrNull() ?: continue

            val memberIds = gJson.optJSONArray("memberIds") ?: JSONArray()
            val members = ArrayList<DominantColor>()
            for (m in 0 until memberIds.length()) {
                paletteMap[memberIds.getInt(m)]?.let { members.add(it) }
            }

            groups.add(
                ColorGroup(
                    groupId = groupId,
                    primaryColor = primaryColor,
                    members = if (members.isEmpty()) listOf(primaryColor) else members,
                    totalPercentage = totalPercentage
                )
            )
        }

        val garmentColor = json.optJSONObject("garmentColor")?.let { gJson ->
            DominantColor(
                id = gJson.optInt("id", -1),
                hex = gJson.getString("hex"),
                r = gJson.getInt("r"),
                g = gJson.getInt("g"),
                b = gJson.getInt("b"),
                labL = gJson.getDouble("labL"),
                labA = gJson.getDouble("labA"),
                labB = gJson.getDouble("labB"),
                pixelCount = 0,
                pixelPercentage = 0f,
                isGarmentBase = true
            )
        }

        return ArtworkAnalysisRecord(
            id = id,
            fileName = fileName,
            imageUri = imageUri,
            thumbnailPath = thumbnailPath,
            width = width,
            height = height,
            fileSizeBytes = fileSizeBytes,
            timestamp = timestamp,
            palette = palette,
            colorGroups = groups,
            garmentColor = garmentColor,
            algorithmVersion = algorithmVersion,
            measuredPrintColourCount = if (json.has("measuredPrintColourCount")) json.getInt("measuredPrintColourCount") else null,
            inputKind = json.optString("inputKind").takeIf { it == "PHOTO" || it == "ARTWORK" },
            inputKindConfirmed = json.optBoolean("inputKindConfirmed", false)
        )
    }
}
