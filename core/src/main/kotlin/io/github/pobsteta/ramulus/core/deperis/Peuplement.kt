package io.github.pobsteta.ramulus.core.deperis

/**
 * Bilan d'un peuplement à partir des classes de ses arbres notés.
 *
 * Source : fiches essences DEPERIS du DSF, 2025, page 1, encadré « PEUPLEMENT » : peuplement dégradé si plus de 20 %
 * d'arbres dégradés (D, E, F) ; très dégradé si plus de 20 % d'arbres très dégradés (E, F). « Plus de » est lu
 * strictement : 20 % tout juste n'est pas dégradé.
 */
data class BilanPeuplement(
    val effectif: Int,
    val parClasse: Map<Classe, Int>,
) {
    val degrades: Int get() = parClasse.filterKeys { it.estDegradee }.values.sum()
    val tresDegrades: Int get() = parClasse.filterKeys { it.estTresDegradee }.values.sum()

    /** Part d'arbres D, E, F, entre 0 et 1 ; 0 sans arbre. */
    val partDegrades: Double get() = if (effectif == 0) 0.0 else degrades.toDouble() / effectif
    val partTresDegrades: Double get() = if (effectif == 0) 0.0 else tresDegrades.toDouble() / effectif

    // Comparaison en entiers, pour qu'un effectif de 5 avec 1 arbre dégradé (20 % tout juste) ne bascule pas
    // par arrondi flottant.
    val estDegrade: Boolean get() = effectif > 0 && degrades * 100 > SEUIL_POURCENT * effectif
    val estTresDegrade: Boolean get() = effectif > 0 && tresDegrades * 100 > SEUIL_POURCENT * effectif

    companion object {
        const val SEUIL_POURCENT = 20

        fun de(classes: Iterable<Classe>): BilanPeuplement {
            val compte = Classe.entries.associateWith { 0 }.toMutableMap()
            var n = 0
            for (c in classes) {
                compte[c] = compte.getValue(c) + 1
                n++
            }
            return BilanPeuplement(n, compte)
        }
    }
}
