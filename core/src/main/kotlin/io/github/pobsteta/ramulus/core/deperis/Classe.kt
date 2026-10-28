package io.github.pobsteta.ramulus.core.deperis

/**
 * Note synthétique DEPERIS.
 *
 * Source : fiches essences DEPERIS du DSF, 2025, page 1 (« Arbres dégradés : D, E, F ; arbres très dégradés : E, F »)
 * et page « La méthode DEPERIS » du ministère de l'Agriculture (« A et B sont considérées comme saines »).
 * La fiche ne qualifie pas C : Ramulus la dit « intermédiaire » sans lui donner d'autre sens.
 */
enum class Classe(val etat: Etat) {
    A(Etat.SAIN),
    B(Etat.SAIN),
    C(Etat.INTERMEDIAIRE),
    D(Etat.DEGRADE),
    E(Etat.TRES_DEGRADE),
    F(Etat.TRES_DEGRADE),
    ;

    /** Vrai pour D, E et F. */
    val estDegradee: Boolean get() = etat == Etat.DEGRADE || etat == Etat.TRES_DEGRADE

    /** Vrai pour E et F. */
    val estTresDegradee: Boolean get() = etat == Etat.TRES_DEGRADE

    enum class Etat { SAIN, INTERMEDIAIRE, DEGRADE, TRES_DEGRADE }

    companion object {
        /** Lit une classe imprimée sur une fiche : « A » à « F » ; « 0 » (fiche Sapin) vaut A ; « Mort » vaut F. */
        fun lire(texte: String): Classe = when (val t = texte.trim()) {
            "0" -> A
            "Mort", "mort" -> F
            else -> entries.firstOrNull { it.name == t.uppercase() }
                ?: throw IllegalArgumentException("Classe DEPERIS inconnue : « $texte »")
        }
    }
}
