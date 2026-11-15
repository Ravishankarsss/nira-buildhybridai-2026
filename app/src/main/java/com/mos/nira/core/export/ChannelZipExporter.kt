package com.mos.nira.core.export

import com.mos.nira.core.adjust.SeparationProof
import com.mos.nira.core.adjust.SeparationState
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.util.zip.CRC32
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.roundToInt

/**
 * Minimal PNG writer in pure Kotlin, so channel export is real 8-bit greyscale and can be
 * tested on the JVM without Android Bitmap.
 */
object PngWriter {

    private val SIGNATURE = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)

    /** 8-bit greyscale PNG. grey holds one value 0..255 per pixel. */
    fun greyscale(grey: ByteArray, width: Int, height: Int): ByteArray {
        require(grey.size == width * height) { "grey buffer does not match width x height" }
        return write(width, height, colourType = 0, bytesPerPixel = 1) { row, out ->
            System.arraycopy(grey, row * width, out, 0, width)
        }
    }

    /** 8-bit RGB PNG from ARGB ints (alpha is dropped). */
    fun rgb(pixels: IntArray, width: Int, height: Int): ByteArray {
        require(pixels.size == width * height) { "pixel buffer does not match width x height" }
        return write(width, height, colourType = 2, bytesPerPixel = 3) { row, out ->
            for (x in 0 until width) {
                val p = pixels[row * width + x]
                out[x * 3] = (p shr 16).toByte()
                out[x * 3 + 1] = (p shr 8).toByte()
                out[x * 3 + 2] = p.toByte()
            }
        }
    }

    private fun write(
        width: Int,
        height: Int,
        colourType: Int,
        bytesPerPixel: Int,
        fillRow: (row: Int, out: ByteArray) -> Unit
    ): ByteArray {
        require(width > 0 && height > 0) { "image is empty" }
        val out = ByteArrayOutputStream()
        out.write(SIGNATURE)

        val header = ByteArrayOutputStream()
        writeInt(header, width)
        writeInt(header, height)
        header.write(8)          // bit depth
        header.write(colourType) // 0 = greyscale, 2 = RGB
        header.write(0)          // compression
        header.write(0)          // filter
        header.write(0)          // no interlace
        writeChunk(out, "IHDR", header.toByteArray())

        val data = ByteArrayOutputStream()
        val deflater = Deflater(Deflater.DEFAULT_COMPRESSION)
        DeflaterOutputStream(data, deflater).use { z ->
            val row = ByteArray(width * bytesPerPixel)
            for (y in 0 until height) {
                z.write(0) // filter type: none
                fillRow(y, row)
                z.write(row)
            }
        }
        deflater.end()
        writeChunk(out, "IDAT", data.toByteArray())
        writeChunk(out, "IEND", ByteArray(0))
        return out.toByteArray()
    }

    private fun writeChunk(out: OutputStream, type: String, data: ByteArray) {
        val typeBytes = type.toByteArray(Charsets.US_ASCII)
        writeInt(out, data.size)
        out.write(typeBytes)
        out.write(data)
        val crc = CRC32()
        crc.update(typeBytes)
        crc.update(data)
        writeInt(out, crc.value.toInt())
    }

    private fun writeInt(out: OutputStream, value: Int) {
        out.write(value ushr 24)
        out.write(value ushr 16)
        out.write(value ushr 8)
        out.write(value)
    }
}

/**
 * Exports every ink channel as a separate greyscale PNG at working resolution, plus the proof,
 * in one ZIP. Files are named by ink, with the screen number first so they sort in print order.
 */
object ChannelZipExporter {

    const val PROOF_ENTRY = "proof.png"

    /** Film positive convention: black (0) = 100% ink, white (255) = no ink. */
    fun channelGrey(coverage: FloatArray): ByteArray =
        ByteArray(coverage.size) { ((1f - coverage[it].coerceIn(0f, 1f)) * 255f).roundToInt().toByte() }

    fun entryName(index: Int, inkName: String): String {
        val safe = inkName.trim().replace(Regex("[^A-Za-z0-9]+"), "_").trim('_').ifEmpty { "ink" }
        return String.format(java.util.Locale.US, "%02d_%s.png", index + 1, safe)
    }

    /** Writes the ZIP to [out] and returns the entry names in order. The stream is closed. */
    fun export(state: SeparationState, out: OutputStream, proof: IntArray = SeparationProof.render(state)): List<String> {
        val names = ArrayList<String>()
        ZipOutputStream(out).use { zip ->
            state.channels.forEachIndexed { index, ch ->
                val name = entryName(index, ch.name)
                zip.putNextEntry(ZipEntry(name))
                zip.write(PngWriter.greyscale(channelGrey(ch.coverage), state.width, state.height))
                zip.closeEntry()
                names.add(name)
            }
            zip.putNextEntry(ZipEntry(PROOF_ENTRY))
            zip.write(PngWriter.rgb(proof, state.width, state.height))
            zip.closeEntry()
            names.add(PROOF_ENTRY)
        }
        return names
    }
}
