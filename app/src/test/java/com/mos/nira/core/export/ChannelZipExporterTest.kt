package com.mos.nira.core.export

import com.mos.nira.core.adjust.ChannelRole
import com.mos.nira.core.adjust.InkChannel
import com.mos.nira.core.adjust.InkModel
import com.mos.nira.core.adjust.SeparationState
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.util.zip.CRC32
import java.util.zip.Inflater
import java.util.zip.ZipInputStream

/**
 * The export ZIP must hold one real greyscale PNG per ink, named by ink, plus the proof.
 * The PNGs are read back by an independent decoder below (signature, chunk CRCs, zlib data).
 */
class ChannelZipExporterTest {

    private val w = 6
    private val h = 4

    private class Png(val width: Int, val height: Int, val bitDepth: Int, val colourType: Int, val rows: List<ByteArray>)

    /** Reads a non-interlaced PNG whose rows all use filter 0, checking every chunk CRC. */
    private fun decodePng(bytes: ByteArray): Png {
        val input = DataInputStream(ByteArrayInputStream(bytes))
        val signature = ByteArray(8).also { input.readFully(it) }
        assertArrayEquals(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A), signature)

        var width = 0
        var height = 0
        var bitDepth = 0
        var colourType = 0
        val idat = ByteArrayOutputStream()
        var sawEnd = false
        while (!sawEnd) {
            val length = input.readInt()
            val type = ByteArray(4).also { input.readFully(it) }
            val data = ByteArray(length).also { input.readFully(it) }
            val crc = CRC32().apply { update(type); update(data) }
            assertEquals("chunk CRC", crc.value.toInt(), input.readInt())
            when (String(type, Charsets.US_ASCII)) {
                "IHDR" -> {
                    val header = DataInputStream(ByteArrayInputStream(data))
                    width = header.readInt()
                    height = header.readInt()
                    bitDepth = header.read()
                    colourType = header.read()
                    assertEquals("compression", 0, header.read())
                    assertEquals("filter method", 0, header.read())
                    assertEquals("interlace", 0, header.read())
                }
                "IDAT" -> idat.write(data)
                "IEND" -> sawEnd = true
            }
        }

        val bytesPerPixel = if (colourType == 2) 3 else 1
        val raw = ByteArray(height * (1 + width * bytesPerPixel))
        val inflater = Inflater()
        inflater.setInput(idat.toByteArray())
        assertEquals("all image bytes present", raw.size, inflater.inflate(raw))
        inflater.end()

        val rows = (0 until height).map { y ->
            val start = y * (1 + width * bytesPerPixel)
            assertEquals("row filter", 0, raw[start].toInt())
            raw.copyOfRange(start + 1, start + 1 + width * bytesPerPixel)
        }
        return Png(width, height, bitDepth, colourType, rows)
    }

    private fun state(): SeparationState {
        val base = FloatArray(w * h) { 1f }
        val blue = FloatArray(w * h) { if (it % w < 3) 1f else 0f }
        val half = FloatArray(w * h) { 0.5f }
        return SeparationState(
            width = w,
            height = h,
            sourcePixels = IntArray(w * h) { 0xFF0000FF.toInt() },
            printMask = BooleanArray(w * h) { true },
            channels = listOf(
                InkChannel("Base White (Underbase)", "#FFFFFF", base, 110, ChannelRole.BASE_WHITE),
                InkChannel("Royal Blue / 2", "#0033AA", blue, 200),
                InkChannel("Red", "#FF0000", half, 200)
            ),
            garmentHex = "#000000",
            inkModel = InkModel.OPAQUE,
            pixelsPerCm = 10.0,
            boxAreaCm2 = 0.24,
            boxPixelCount = w * h,
            garmentThresholdDeltaE00 = 5.0
        )
    }

    private fun unzip(bytes: ByteArray): Map<String, ByteArray> {
        val entries = LinkedHashMap<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                entries[entry.name] = zip.readBytes()
            }
        }
        return entries
    }

    private fun grey(png: Png, x: Int, y: Int): Int = png.rows[y][x].toInt() and 0xFF

    @Test
    fun entryNames_areNumberedAndNamedByInk() {
        assertEquals("01_Base_White_Underbase.png", ChannelZipExporter.entryName(0, "Base White (Underbase)"))
        assertEquals("02_Royal_Blue_2.png", ChannelZipExporter.entryName(1, "Royal Blue / 2"))
        assertEquals("10_00AEEF.png", ChannelZipExporter.entryName(9, "#00AEEF"))
    }

    @Test
    fun zip_holdsOneGreyscalePngPerInk_plusTheProof() {
        val out = ByteArrayOutputStream()
        val names = ChannelZipExporter.export(state(), out)
        val entries = unzip(out.toByteArray())

        val expected = listOf("01_Base_White_Underbase.png", "02_Royal_Blue_2.png", "03_Red.png", "proof.png")
        assertEquals(expected, names)
        assertEquals(expected, entries.keys.toList())

        // Channel files: 8-bit greyscale (colour type 0) at working resolution, black = ink.
        val blue = decodePng(entries["02_Royal_Blue_2.png"]!!)
        assertEquals(w, blue.width)
        assertEquals(h, blue.height)
        assertEquals(8, blue.bitDepth)
        assertEquals(0, blue.colourType)
        assertEquals(0, grey(blue, 0, 0))   // 100% ink
        assertEquals(255, grey(blue, 5, 0)) // no ink

        val red = decodePng(entries["03_Red.png"]!!)
        assertEquals(0, red.colourType)
        assertEquals(128, grey(red, 2, 2)) // 50% ink

        val base = decodePng(entries["01_Base_White_Underbase.png"]!!)
        assertEquals(0, grey(base, 3, 3))

        // Proof: 8-bit RGB (colour type 2), same size.
        val proof = decodePng(entries["proof.png"]!!)
        assertEquals(w, proof.width)
        assertEquals(h, proof.height)
        assertEquals(2, proof.colourType)
    }

    @Test
    fun rgbPng_roundTripsPixelValues() {
        val pixels = intArrayOf(0xFFFF0000.toInt(), 0xFF00FF00.toInt(), 0xFF0000FF.toInt(), 0xFF102030.toInt())
        val png = decodePng(PngWriter.rgb(pixels, 2, 2))
        assertEquals(2, png.colourType)
        assertArrayEquals(byteArrayOf(0xFF.toByte(), 0, 0, 0, 0xFF.toByte(), 0), png.rows[0])
        assertArrayEquals(byteArrayOf(0, 0, 0xFF.toByte(), 0x10, 0x20, 0x30), png.rows[1])
    }
}
