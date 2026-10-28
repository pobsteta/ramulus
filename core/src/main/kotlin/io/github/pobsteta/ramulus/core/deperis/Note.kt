package io.github.pobsteta.ramulus.core.deperis

/**
 * Note d'un critère DEPERIS, de 0 à 5.
 *
 * Source : fiches essences DEPERIS du DSF, version 2025, page 1, tableau « Note / Intensité / % indicatif »
 * (docs/referentiels/deperis/README.md). Les bornes sont inclusives et ne se recouvrent pas.
 */
@JvmInline
value class Note(val valeur: Int) : Comparable<Note> {
    init {
        require(valeur in 0..5) { "Une note DEPERIS va de 0 à 5 (reçu : $valeur)" }
    }

    /** Intensité telle qu'écrite sur les fiches. */
    val intensite: String get() = INTENSITES[valeur]

    /** Pourcentage indicatif, borne basse incluse. */
    val pourcentMin: Int get() = BORNES[valeur].first

    /** Pourcentage indicatif, borne haute incluse. */
    val pourcentMax: Int get() = BORNES[valeur].last

    override fun compareTo(other: Note): Int = valeur.compareTo(other.valeur)

    override fun toString(): String = valeur.toString()

    companion object {
        private val INTENSITES = listOf(
            "Absence ou trace", "Légère", "Assez forte", "Forte", "Très forte", "Total ou presque",
        )
        private val BORNES = listOf(0..5, 6..25, 26..50, 51..75, 76..95, 96..100)

        val TOUTES: List<Note> = (0..5).map(::Note)

        /** Note correspondant à un pourcentage estimé (0 à 100). */
        fun depuisPourcentage(pourcent: Int): Note {
            require(pourcent in 0..100) { "Un pourcentage va de 0 à 100 (reçu : $pourcent)" }
            return Note(BORNES.indexOfFirst { pourcent in it })
        }
    }
}
