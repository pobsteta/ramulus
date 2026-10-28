package io.github.pobsteta.ramulus.core.journal

/**
 * Une ligne du journal des saisies : une saisie, jamais modifiée ni effacée (ADR 0005 ; Surculus ADR 0005, l. 46).
 *
 * Instants en **millisecondes depuis l'époque Unix, UTC** (suffixe `UtcMs`) ; `monotoneMs` est
 * `SystemClock.elapsedRealtime`, valable dans un même `demarrage`.
 *
 * - `valeur` : texte JSON de la saisie, **opaque** pour le journal et recopié au caractère près ; son contenu par
 *   type est fixé par les missions qui saisissent (006, 008). Une notation n'y porte pas de classe : la classe se
 *   calcule par l'abaque (plan 005, Q9).
 * - `operateur` : traçabilité des saisies, non contrôle de l'agent (Surculus ADR 0005, l. 59) ; jamais dans un nom
 *   de fichier.
 * - `appareil` : identifiant opaque de l'appareil d'origine (8 caractères hexadécimaux), jamais réécrit.
 */
data class LigneJournal(
    val uuid: String,
    val objetUuid: String,
    val type: String,
    val valeur: String?,
    val remplaceUuid: String?,
    val operateur: String,
    val appareil: String,
    val sequence: Long,
    val saisiLeUtcMs: Long,
    val heureGnssMs: Long?,
    val monotoneMs: Long,
    val demarrage: Long,
    val ageFixMs: Long?,
    val codeTournee: String,
)

/** Liste fermée des types de ligne (plan 005, Q3, décision de Pascal du 2026-10-28). */
object TypeLigne {
    const val NOTATION = "notation"
    const val CONTRE_NOTATION = "contre_notation"
    const val PHOTO = "photo"
    const val CENTRE = "centre"
    const val PLACETTE_NOTEE = "placette_notee"

    /** Annule la ligne désignée par `remplace_uuid`. */
    const val ANNULATION = "annulation"

    /** Saut d'horloge détecté (règle « Temps », Surculus ADR 0005, l. 49 ; plan 005, Q2). */
    const val SAUT_HORLOGE = "saut_horloge"

    val TOUS: Set<String> = linkedSetOf(
        NOTATION, CONTRE_NOTATION, PHOTO, CENTRE, PLACETTE_NOTEE, ANNULATION, SAUT_HORLOGE,
    )
}
