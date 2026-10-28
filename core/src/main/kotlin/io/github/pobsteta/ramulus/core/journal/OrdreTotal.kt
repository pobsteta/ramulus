package io.github.pobsteta.ramulus.core.journal

/**
 * Ordre total du journal : départage déterministe par (`saisi_le_utc_ms`, `appareil`, `sequence`, `uuid`)
 * (Surculus ADR 0005, l. 48). Le résultat ne dépend pas de l'ordre d'arrivée des lignes.
 */
object OrdreTotal : Comparator<LigneJournal> {
    override fun compare(a: LigneJournal, b: LigneJournal): Int =
        compareValuesBy(a, b, { it.saisiLeUtcMs }, { it.appareil }, { it.sequence }, { it.uuid })

    fun trier(lignes: Collection<LigneJournal>): List<LigneJournal> = lignes.sortedWith(this)
}
