package io.github.pobsteta.ramulus.core.journal

/**
 * Deux lignes ou plus qui remplacent (corrigent ou annulent) la **même** ligne : corrections concurrentes,
 * **signalées, jamais résolues** (Surculus ADR 0005, l. 48 ; plan 005, Q4 : une annulation et une correction
 * concurrentes de la même ligne forment aussi un conflit). Les lignes restent toutes au journal.
 */
data class Conflit(val remplaceUuid: String, val lignes: List<LigneJournal>)

object Conflits {
    /**
     * Les conflits du journal, dans l'ordre total de leur première ligne ; les lignes de chaque conflit sont dans
     * l'ordre total. Une correction de correction (B remplace A, C remplace B) n'est pas un conflit.
     */
    fun detecter(lignes: Collection<LigneJournal>): List<Conflit> =
        OrdreTotal.trier(lignes)
            .filter { it.remplaceUuid != null }
            .groupBy { it.remplaceUuid!! }
            .filterValues { it.size >= 2 }
            .map { (cible, concurrentes) -> Conflit(cible, concurrentes) }
}
