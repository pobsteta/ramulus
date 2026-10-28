package io.github.pobsteta.ramulus.core.journal

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Règle « Temps » reprise de Surculus ADR 0005, l. 49 (plan 005, Q2) : jour civil en Europe/Paris, durées sur
 * l'horloge monotone, saut d'horloge de plus de 60 s détecté et noté, heure brute jamais réécrite.
 */
object Temps {
    val FUSEAU: ZoneId = ZoneId.of("Europe/Paris")

    /** Écart au-delà duquel un saut d'horloge est noté (ms). */
    const val SEUIL_SAUT_MS = 60_000L

    /** Âge maximal du fix pour que le jour civil se calcule sur l'heure GNSS (ms). */
    const val AGE_FIX_MAX_MS = 5_000L

    /**
     * Écart (ms) entre l'avance de l'horloge du téléphone et celle de l'horloge monotone depuis un repère pris
     * **dans le même démarrage** ; `null` si les démarrages diffèrent (l'horloge monotone repart de zéro).
     */
    fun ecartHorlogeMs(
        repereUtcMs: Long, repereMonotoneMs: Long, repereDemarrage: Long,
        utcMs: Long, monotoneMs: Long, demarrage: Long,
    ): Long? = if (demarrage != repereDemarrage) null else (utcMs - repereUtcMs) - (monotoneMs - repereMonotoneMs)

    /** Vrai si l'écart dépasse 60 s, dans un sens ou dans l'autre. */
    fun estSaut(ecartMs: Long?): Boolean = ecartMs != null && kotlin.math.abs(ecartMs) > SEUIL_SAUT_MS

    /**
     * Jour civil (Europe/Paris) d'une saisie : sur l'heure GNSS si le fix a au plus 5 s (âge négatif ignoré),
     * sinon sur l'heure du téléphone.
     */
    fun jourCivil(ligne: LigneJournal): LocalDate {
        val gnss = ligne.heureGnssMs
        val age = ligne.ageFixMs
        val instant = if (gnss != null && age != null && age in 0..AGE_FIX_MAX_MS) gnss else ligne.saisiLeUtcMs
        return Instant.ofEpochMilli(instant).atZone(FUSEAU).toLocalDate()
    }
}
