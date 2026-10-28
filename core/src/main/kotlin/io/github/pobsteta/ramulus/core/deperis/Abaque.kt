package io.github.pobsteta.ramulus.core.deperis

/**
 * Abaque DEPERIS : croise la mortalité de branches (MB, en ligne) et le manque de ramification (MR, feuillus)
 * ou d'aiguilles (MA, résineux) en colonne.
 *
 * Source : fiches essences DEPERIS du DSF, 2025, page 1, encadré « Note synthétique DEPERIS ». La même table est
 * imprimée sur les fiches feuillus et résineux ; seule la case MB = 5, MR/MA = 5 porte « Mort », rendu ici par F
 * avec [estMort].
 */
object Abaque {
    private val TABLE = arrayOf(
        "ABCDEF",
        "BBCDEF",
        "CCDDEF",
        "DDDEFF",
        "EEEFFF",
        "FFFFFF",
    )

    fun classe(mb: Note, second: Note): Classe = Classe.valueOf(TABLE[mb.valeur][second.valeur].toString())

    fun classe(mb: Int, second: Int): Classe = classe(Note(mb), Note(second))

    /** La case « Mort » de l'abaque : toutes les branches mortes et plus de ramification. */
    fun estMort(mb: Note, second: Note): Boolean = mb.valeur == 5 && second.valeur == 5
}
