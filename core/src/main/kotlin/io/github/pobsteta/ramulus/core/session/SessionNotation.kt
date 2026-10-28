package io.github.pobsteta.ramulus.core.session

import io.github.pobsteta.ramulus.core.deperis.Abaque
import io.github.pobsteta.ramulus.core.deperis.BilanPeuplement
import io.github.pobsteta.ramulus.core.deperis.Classe
import io.github.pobsteta.ramulus.core.deperis.EssenceDeperis
import io.github.pobsteta.ramulus.core.deperis.Note
import io.github.pobsteta.ramulus.core.deperis.Notation
import io.github.pobsteta.ramulus.core.deperis.Origine
import io.github.pobsteta.ramulus.core.deperis.Saison
import java.util.UUID

/**
 * État de l'écran de notation, sans Android : l'essence et la saison choisies, l'arbre en cours (MB et second
 * critère) et les arbres déjà notés de la session.
 *
 * Tranche 0 : la session vit en mémoire. Le journal en ajout seul (Room, comme Surculus) et l'export GeoPackage
 * sont des missions à venir.
 */
data class SessionNotation(
    val essence: EssenceDeperis,
    val saison: Saison = Saison.ETE,
    val mb: Note? = null,
    val second: Note? = null,
    val notees: List<Notation> = emptyList(),
) {
    /** Classe de l'arbre en cours, dès que les deux notes sont posées. */
    val classeEnCours: Classe? get() = if (mb != null && second != null) Abaque.classe(mb, second) else null

    val bilan: BilanPeuplement get() = BilanPeuplement.de(notees.map { it.classe })

    fun avecEssence(e: EssenceDeperis) = copy(essence = e, mb = null, second = null)

    fun avecSaison(s: Saison) = copy(saison = s)

    fun avecMb(n: Note) = copy(mb = n)

    fun avecSecond(n: Note) = copy(second = n)

    /** Enregistre l'arbre en cours et prépare le suivant ; sans effet tant qu'une note manque. */
    fun enregistrer(uuid: String = UUID.randomUUID().toString()): SessionNotation {
        val m = mb ?: return this
        val s = second ?: return this
        val n = Notation(uuid, essence.nom, essence.type, m, s, Origine.OPERATEUR)
        return copy(mb = null, second = null, notees = notees + n)
    }

    /** Retire le dernier arbre noté (erreur de saisie). Le futur journal gardera la trace de l'annulation. */
    fun annulerDernier(): SessionNotation = if (notees.isEmpty()) this else copy(notees = notees.dropLast(1))
}
