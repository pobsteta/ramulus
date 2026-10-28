package io.github.pobsteta.ramulus.core.journal

import java.security.SecureRandom
import java.util.Random

/**
 * UUID version 7 (RFC 9562, § 5.7), écrit en minuscules : 48 bits d'instant Unix en millisecondes, puis la version,
 * 12 bits `rand_a`, la variante et 62 bits aléatoires.
 *
 * Dans une même milliseconde, `rand_a` sert de compteur (RFC 9562, § 6.2, méthode 1) : les uuid tirés par un même
 * générateur sont strictement croissants, même si l'horloge recule (l'instant écrit ne recule jamais).
 */
class UuidV7(private val aleatoire: Random = SecureRandom()) {
    private var dernierMs = -1L
    private var compteur = 0

    @Synchronized
    fun tirer(instantUtcMs: Long): String {
        require(instantUtcMs in 0..0xFFFF_FFFF_FFFFL) { "instant hors de 48 bits : $instantUtcMs" }
        var ms = instantUtcMs
        if (ms <= dernierMs) {
            ms = dernierMs
            compteur++
            if (compteur > 0xFFF) {
                ms = dernierMs + 1
                compteur = 0
            }
        } else {
            compteur = 0
        }
        dernierMs = ms
        val haut = (ms shl 16) or (0x7L shl 12) or compteur.toLong()
        val bas = (aleatoire.nextLong() and 0x3FFF_FFFF_FFFF_FFFFL) or Long.MIN_VALUE // variante 10
        return format(haut, bas)
    }

    companion object {
        private val MOTIF = Regex("^[0-9a-f]{8}-[0-9a-f]{4}-7[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$")

        /** Vrai si `uuid` est un UUID v7 en minuscules. */
        fun estValide(uuid: String): Boolean = MOTIF.matches(uuid)

        /** Instant (ms Unix, UTC) porté par un UUID v7. */
        fun instantMs(uuid: String): Long = uuid.replace("-", "").substring(0, 12).toLong(16)

        private fun format(haut: Long, bas: Long): String {
            val h = "%016x".format(haut)
            val b = "%016x".format(bas)
            return "${h.substring(0, 8)}-${h.substring(8, 12)}-${h.substring(12, 16)}-${b.substring(0, 4)}-${b.substring(4)}"
        }
    }
}
