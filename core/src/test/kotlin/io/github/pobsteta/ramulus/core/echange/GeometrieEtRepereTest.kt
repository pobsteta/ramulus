package io.github.pobsteta.ramulus.core.echange

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Tests propres de la mission 004 (distincts des tests d'acceptation de 003) : repère Lambert-93 et géométrie
 * GeoPackage. Coordonnées : X = est, Y = nord, en mètres (EPSG:2154) ; longitude puis latitude, en degrés.
 */
class GeometrieEtRepereTest {

    @Test
    fun `la placette P001 des cas communs (5,0650 E, 47,1150 N) tombe en Cote-d Or, a quelques decimetres pres`() {
        // Valeurs de fixtures/echange/A1 (docs/formats/deperis-v1.md, exemple d'instantané) : X est, Y nord.
        val (lon, lat) = Lambert93.versLonLat(856560.23, 6670351.34)
        assertEquals(5.0650, lon, 1e-4)
        assertEquals(47.1150, lat, 1e-4)
        assertTrue(Lambert93.enFrance(856560.23, 6670351.34))
    }

    @Test
    fun `la placette en Somalie n est pas en France`() {
        // Mogadiscio (45,3418 E, 2,0469 N) projeté en Lambert-93 : valeurs de R3a.
        assertFalse(Lambert93.enFrance(6552517.43, 2808067.4))
    }

    @Test
    fun `X et Y inverses, ou des degres a la place des metres, ne sont pas en France`() {
        assertTrue(Lambert93.enFrance(856563.43, 6670349.24))
        assertFalse(Lambert93.enFrance(6670349.24, 856563.43))
        assertFalse(Lambert93.enFrance(5.065, 47.115))
        assertFalse(Lambert93.enFrance(Double.NaN, 6670349.24))
        assertFalse(Lambert93.enFrance(856563.43, Double.POSITIVE_INFINITY))
    }

    @Test
    fun `l origine du Lambert-93 tombe en France, un point a 800 km a l est non`() {
        assertTrue(Lambert93.enFrance(700000.0, 6600000.0)) // origine du Lambert-93 : 3° E, ~46,5° N
        assertFalse(Lambert93.enFrance(1_500_000.0, 6_600_000.0))
    }

    @Test
    fun `un point s ecrit puis se relit identique, X en premier, avec son repere`() {
        val blob = GeometrieGpkg.encoderPoint(856560.23, 6670351.34, 2154)
        val p = GeometrieGpkg.decoderPoint(blob)!!
        assertEquals(856560.23, p.x, 0.0)
        assertEquals(6670351.34, p.y, 0.0)
        assertEquals(2154, p.srsId)
        assertEquals('G'.code.toByte(), blob[0])
        assertEquals('P'.code.toByte(), blob[1])
    }

    @Test
    fun `une geometrie vide ou nulle donne null, une geometrie illisible leve`() {
        assertNull(GeometrieGpkg.decoderPoint(null))
        val vide = GeometrieGpkg.encoderPoint(1.0, 2.0, 2154).also { it[3] = (it[3].toInt() or 0x10).toByte() }
        assertNull(GeometrieGpkg.decoderPoint(vide))
        assertThrows(IllegalArgumentException::class.java) { GeometrieGpkg.decoderPoint(byteArrayOf(1, 2, 3)) }
        val ligne = GeometrieGpkg.encoderPoint(1.0, 2.0, 2154).also { it[9] = 2 } // type WKB 2 : ligne
        assertThrows(IllegalArgumentException::class.java) { GeometrieGpkg.decoderPoint(ligne) }
    }

    @Test
    fun `un point gros-boutiste avec enveloppe est lu comme GDAL ou QGIS peuvent l ecrire`() {
        val b = ByteBuffer.allocate(8 + 32 + 21).order(ByteOrder.BIG_ENDIAN)
        b.put('G'.code.toByte()).put('P'.code.toByte()).put(0).put(0x02) // grand-boutiste, enveloppe XY
        b.putInt(2154)
        repeat(4) { b.putDouble(0.0) }
        b.put(0).putInt(1).putDouble(856560.23).putDouble(6670351.34)
        val p = GeometrieGpkg.decoderPoint(b.array())!!
        assertEquals(856560.23, p.x, 0.0)
        assertEquals(6670351.34, p.y, 0.0)
        assertEquals(2154, p.srsId)
    }
}
