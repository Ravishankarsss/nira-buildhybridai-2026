package com.mos.nira.features.artwork.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.mos.nira.features.artwork.domain.PartAResult
import com.mos.nira.features.artwork.domain.PartBResult
import com.mos.nira.features.artwork.domain.SeparationStrategyType
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/**
 * The engine's Part A and Part B results of a saved job, kept on this phone (app-private storage),
 * so reopening the job takes seconds instead of measuring again. Used only when the key matches:
 * the same answers and settings, the same app version and the same rules files. Text and the
 * job's own mask pictures only; deleted with the job.
 */
object EngineDiskCache {

    private const val DIR = "engine"

    class Saved(
        val partA: PartAResult,
        val partB: PartBResult,
        val strategy: SeparationStrategyType,
        val userPickedStrategy: Boolean
    )

    /** The key for one set of inputs: [inputsText] plus the app version and the rules files. */
    fun key(context: Context, inputsText: String, appVersion: Long): String {
        val md = MessageDigest.getInstance("SHA-256")
        md.update(inputsText.toByteArray())
        md.update("|v$appVersion|c${EngineResultCodec.VERSION}|".toByteArray())
        for (asset in listOf("print_rules.json", "print_techniques.json")) {
            runCatching { context.assets.open(asset).use { md.update(it.readBytes()) } }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    private fun dir(context: Context) = File(context.filesDir, DIR)
    private fun json(context: Context, id: String) = File(dir(context), "$id.json")
    private fun maskPicture(context: Context, id: String) = File(dir(context), "$id.mask.png")
    private fun maskBits(context: Context, id: String) = File(dir(context), "$id.mask.bin")

    fun save(context: Context, recordId: String, key: String, a: PartAResult, b: PartBResult, strategy: SeparationStrategyType, userPicked: Boolean) {
        runCatching {
            dir(context).mkdirs()
            val o = JSONObject()
                .put("key", key)
                .put("partA", EngineResultCodec.encodePartA(a))
                .put("partB", EngineResultCodec.encodePartB(b))
                .put("strategy", strategy.name)
                .put("userPicked", userPicked)
            a.printMask?.let { maskBits(context, recordId).writeBytes(EngineResultCodec.packMask(it)) } ?: maskBits(context, recordId).delete()
            a.maskBitmap?.let { bmp -> maskPicture(context, recordId).outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) } }
                ?: maskPicture(context, recordId).delete()
            // The text last: a job is only read back when its text file is complete.
            val tmp = File(dir(context), "$recordId.json.tmp")
            tmp.writeText(o.toString())
            tmp.renameTo(json(context, recordId))
        }.onFailure { remove(context, recordId) }
    }

    fun load(context: Context, recordId: String, key: String): Saved? = runCatching {
        val f = json(context, recordId)
        if (!f.exists()) return null
        val o = JSONObject(f.readText())
        if (o.getString("key") != key) return null
        val ao = o.getJSONObject("partA")
        val size = ao.getInt("maskWidth") * ao.getInt("maskHeight")
        val bits = maskBits(context, recordId).takeIf { it.exists() }?.readBytes()?.let { EngineResultCodec.unpackMask(it, size) }
        val picture = maskPicture(context, recordId).takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.path) }
        Saved(
            EngineResultCodec.decodePartA(ao, bits, picture),
            EngineResultCodec.decodePartB(o.getJSONObject("partB")),
            SeparationStrategyType.valueOf(o.getString("strategy")),
            o.getBoolean("userPicked")
        )
    }.getOrNull()

    fun remove(context: Context, recordId: String) {
        listOf(json(context, recordId), maskPicture(context, recordId), maskBits(context, recordId)).forEach { it.delete() }
    }
}
