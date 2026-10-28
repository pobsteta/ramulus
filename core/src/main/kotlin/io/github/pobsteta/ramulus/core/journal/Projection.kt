package io.github.pobsteta.ramulus.core.journal

/** Clé de l'état courant (Surculus ADR 0005, l. 47) : une autre tournée ou un autre type n'influe jamais. */
data class CleEtat(val codeTournee: String, val objetUuid: String, val type: String)

/**
 * État courant d'une clé. `tetes` : lignes de la clé qui ne sont ni remplacées ni annulées, dans l'ordre total.
 * `courante` n'est donnée que s'il n'y a qu'une tête et aucun conflit : sinon, rien n'est choisi en silence.
 */
data class EtatCourant(val cle: CleEtat, val tetes: List<LigneJournal>, val enConflit: Boolean) {
    val courante: LigneJournal? get() = if (enConflit) null else tetes.singleOrNull()
}

/**
 * Projection de l'état courant, recalculable depuis le journal seul (Surculus ADR 0005, l. 47 ; plan 005, Q7).
 *
 * - une ligne dont l'`uuid` est le `remplace_uuid` d'une autre ligne **de la même tournée** est remplacée
 *   (corrigée ou annulée) et n'est plus une tête ; un `remplace_uuid` qui vise une autre tournée est sans effet ;
 * - les lignes `annulation` et `saut_horloge` ne sont jamais des états ;
 * - une clé est en conflit si elle a plusieurs têtes, ou si une de ses lignes est la cible d'un [Conflit] ;
 * - une clé dont toutes les lignes sont remplacées ou annulées n'a pas d'état et n'apparaît pas.
 */
object Projection {
    private val HORS_ETAT = setOf(TypeLigne.ANNULATION, TypeLigne.SAUT_HORLOGE)

    fun calculer(lignes: Collection<LigneJournal>): Map<CleEtat, EtatCourant> {
        val triees = OrdreTotal.trier(lignes)
        val parUuid = triees.associateBy { it.uuid }
        val remplaces = triees.mapNotNull { l ->
            l.remplaceUuid?.takeIf { parUuid[it]?.codeTournee == l.codeTournee }
        }.toSet()
        val ciblesEnConflit = Conflits.detecter(triees).map { it.remplaceUuid }.toSet()

        val resultat = linkedMapOf<CleEtat, EtatCourant>()
        triees.filter { it.type !in HORS_ETAT }
            .groupBy { CleEtat(it.codeTournee, it.objetUuid, it.type) }
            .forEach { (cle, lignesCle) ->
                val tetes = lignesCle.filter { it.uuid !in remplaces }
                if (tetes.isEmpty()) return@forEach
                val conflit = tetes.size > 1 || lignesCle.any { it.uuid in ciblesEnConflit }
                resultat[cle] = EtatCourant(cle, tetes, conflit)
            }
        return resultat
    }
}
