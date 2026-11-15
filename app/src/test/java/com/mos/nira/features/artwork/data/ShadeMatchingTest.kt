package com.mos.nira.features.artwork.data

import com.mos.nira.core.image.ColorConverter
import com.mos.nira.features.artwork.domain.ShadeEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.InputStreamReader

class ShadeMatchingTest {

    private val sampleCsv = """
        type,code,name,L,a,b
        TCX,19-4052,Classic Navy,16.2,2.4,-18.5
        TCX,11-0601,Bright White,96.5,-0.5,1.2
        TPG,18-1662,Flame Scarlet,45.8,65.2,34.1
        BUYER,BY-101,Standard Black,10.0,0.0,0.0
    """.trimIndent()

    private fun parseCsv(inputStream: InputStream, libName: String): List<ShadeEntry> {
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
                                libraryName = libName,
                                isUserCreated = true
                            )
                        )
                    }
                }
            }
        }
        return list
    }

    @Test
    fun parseCsv_validFormat_parsesAllRecords() {
        val stream = ByteArrayInputStream(sampleCsv.toByteArray())
        val shades = parseCsv(stream, "Test Lib")

        assertEquals(4, shades.size)
        assertEquals("TCX", shades[0].type)
        assertEquals("19-4052", shades[0].code)
        assertEquals("Classic Navy", shades[0].name)
        assertEquals(16.2, shades[0].labL, 0.001)
        assertEquals(2.4, shades[0].labA, 0.001)
        assertEquals(-18.5, shades[0].labB, 0.001)
    }

    @Test
    fun parseCsv_malformedRows_skippedSafely() {
        val malformedCsv = """
            type,code,name,L,a,b
            TCX,19-4052,Good Row,16.2,2.4,-18.5
            INVALID_ROW,missing,columns
            TPG,18-1662,Bad Numbers,not_a_number,2.0,3.0
            TCX,11-0601,Another Good,96.5,-0.5,1.2
        """.trimIndent()

        val stream = ByteArrayInputStream(malformedCsv.toByteArray())
        val shades = parseCsv(stream, "Malformed Lib")

        assertEquals(2, shades.size)
        assertEquals("Good Row", shades[0].name)
        assertEquals("Another Good", shades[1].name)
    }

    @Test
    fun findNearestShades_returnsClosestInCIEDE2000() {
        val stream = ByteArrayInputStream(sampleCsv.toByteArray())
        val shades = parseCsv(stream, "Test Lib")

        val targetLab = ColorConverter.Lab(17.0, 2.0, -18.0)
        val matches = shades.map { shade ->
            val shadeLab = ColorConverter.Lab(shade.labL, shade.labA, shade.labB)
            val dE00 = ColorConverter.deltaE00(targetLab, shadeLab)
            val grade = ColorConverter.matchGrade(dE00)
            com.mos.nira.features.artwork.domain.ShadeMatch(shade, dE00, grade)
        }.sortedBy { it.deltaE00 }.take(3)

        assertEquals(3, matches.size)
        assertEquals("19-4052", matches[0].shade.code)
        assertTrue(matches[0].deltaE00 < 1.5)
        assertEquals("Excellent (< 1.0 ΔE00)", matches[0].grade)
    }

    @Test
    fun findNearestShades_filtersByType() {
        val stream = ByteArrayInputStream(sampleCsv.toByteArray())
        val shades = parseCsv(stream, "Test Lib")

        val targetLab = ColorConverter.Lab(50.0, 0.0, 0.0)
        val matches = shades
            .filter { it.type.equals("BUYER", ignoreCase = true) }
            .map { shade ->
                val shadeLab = ColorConverter.Lab(shade.labL, shade.labA, shade.labB)
                val dE00 = ColorConverter.deltaE00(targetLab, shadeLab)
                val grade = ColorConverter.matchGrade(dE00)
                com.mos.nira.features.artwork.domain.ShadeMatch(shade, dE00, grade)
            }.sortedBy { it.deltaE00 }.take(3)

        assertEquals(1, matches.size)
        assertEquals("BUYER", matches[0].shade.type)
        assertEquals("BY-101", matches[0].shade.code)
    }
}
