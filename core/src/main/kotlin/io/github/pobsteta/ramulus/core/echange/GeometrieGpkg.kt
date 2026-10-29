package io.github.pobsteta.ramulus.core.echange

import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Un point lu dans une géométrie GeoPackage : X = est, Y = nord (dans le repère `srsId`). */
internal data class PointGpkg(val x: Double, val y: Double, val srsId: Int)

/** Géométrie GeoPackage (OGC 12-128, § 2.1.3) : en-tête « GP » puis WKB. Seuls les points servent ici. */
internal object GeometrieGpkg {
    /** Point 2D en petit-boutiste, sans enveloppe, comme GDAL l'écrit pour un point. */
    fun encoderPoint(x: Double, y: Double, srsId: Int): ByteArray {
        val b = ByteBuffer.allocate(8 + 21).order(ByteOrder.LITTLE_ENDIAN)
        b.put('G'.code.toByte()).put('P'.code.toByte()).put(0).put(0x01)
        b.putInt(srsId)
        b.put(1).putInt(1).putDouble(x).putDouble(y)
        return b.array()
    }

    /** `null` pour une géométrie vide ; lève [IllegalArgumentException] si ce n'est pas un point lisible. */
    fun decoderPoint(blob: ByteArray?): PointGpkg? {
        if (blob == null) return null
        require(blob.size >= 8 && blob[0] == 'G'.code.toByte() && blob[1] == 'P'.code.toByte()) { "géométrie sans en-tête GeoPackage" }
        val drapeaux = blob[3].toInt() and 0xFF
        val ordre = if (drapeaux and 0x01 != 0) ByteOrder.LITTLE_ENDIAN else ByteOrder.BIG_ENDIAN
        val srsId = ByteBuffer.wrap(blob, 4, 4).order(ordre).int
        val tailleEnveloppe = when ((drapeaux shr 1) and 0x07) {
            0 -> 0
            1 -> 32
            2, 3 -> 48
            4 -> 64
            else -> throw IllegalArgumentException("indicateur d'enveloppe invalide")
        }
        if (drapeaux and 0x10 != 0) return null
        val debut = 8 + tailleEnveloppe
        require(blob.size >= debut + 21) { "WKB trop court" }
        val wkb = ByteBuffer.wrap(blob, debut, blob.size - debut)
        wkb.order(if (blob[debut].toInt() == 1) ByteOrder.LITTLE_ENDIAN else ByteOrder.BIG_ENDIAN)
        wkb.get()
        val type = wkb.int
        require(type == 1) { "géométrie de type WKB $type, POINT attendu" }
        val x = wkb.double
        val y = wkb.double
        if (x.isNaN() && y.isNaN()) return null
        return PointGpkg(x, y, srsId)
    }
}
