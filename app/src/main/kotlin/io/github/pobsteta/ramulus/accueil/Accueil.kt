package io.github.pobsteta.ramulus.accueil

import java.text.Normalizer
import java.util.Locale

/*
 * Ce que l'accueil sait calculer sans Android : la recherche, la répartition « À faire / Terminées », les cinq colonnes
 * du kanban et leur règle de statut minimal. Repris de Surculus (`tournee/RechercheTournees.kt` et `tournee/EtatKanban.kt`,
 * commit b73c493), adapté aux tournées de Ramulus : on y cherche le code de la tournée, et l'avancement compte les
 * placettes notées (et non les saisies).
 */

/** Une tournée telle que l'accueil la montre. */
data class LigneAccueil(
    val code: String,
    /** `premiere` ou `contre_notation` (docs/formats/deperis-v1.md). */
    val nature: String,
    val nbPlacettes: Int,
    val nbNotees: Int,
    /** Vrai une fois le `.zip` rendu et déposé. */
    val rendue: Boolean,
) {
    val estContreNotation: Boolean get() = nature == "contre_notation"

    /** « notation » ou « contre-notation ». */
    val natureEnClair: String get() = if (estContreNotation) "contre-notation" else "notation"

    /** « 3 placettes sur 12 » (au singulier : « 1 placette sur 12 »). */
    val avancement: String get() = "$nbNotees ${if (nbNotees > 1) "placettes" else "placette"} sur $nbPlacettes"
}

/** Sans accents ni casse, « œ » = « oe » et « æ » = « ae ». Repris de Surculus `normaliser`. */
fun normaliser(s: String): String =
    Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT)
        .replace("œ", "oe").replace("æ", "ae")

/** Recherche vide = tout. Sinon : la saisie se trouve dans le code de la tournée, sans tenir compte des accents ni de la casse. */
fun correspond(ligne: LigneAccueil, recherche: String): Boolean {
    val n = normaliser(recherche.trim())
    return n.isEmpty() || normaliser(ligne.code).contains(n)
}

fun filtrer(lignes: List<LigneAccueil>, recherche: String): List<LigneAccueil> = lignes.filter { correspond(it, recherche) }

/** Les deux parties de la liste : « À faire » (non rendues) puis « Terminées » (rendues), dans l'ordre d'origine. */
data class ListeAccueil(val aFaire: List<LigneAccueil>, val terminees: List<LigneAccueil>) {
    val vide: Boolean get() = aFaire.isEmpty() && terminees.isEmpty()
}

fun repartirListe(lignes: List<LigneAccueil>): ListeAccueil {
    val (terminees, aFaire) = lignes.partition { it.rendue }
    return ListeAccueil(aFaire, terminees)
}

/** Ce que dit la liste quand elle est vide (comme Surculus `messageVide`). */
enum class MessageVide(val texte: String) {
    AUCUNE_TROUVEE("Aucune tournée trouvée."),
    AUCUNE_TERMINEE("Aucune tournée terminée."),
    AUCUNE_A_FAIRE("Aucune tournée à faire."),
}

/** Recherche non vide : rien ne correspond. Sinon, c'est la liste de la puce choisie qui est vide. */
fun messageVide(recherche: String, voirTerminees: Boolean): MessageVide = when {
    recherche.isNotBlank() -> MessageVide.AUCUNE_TROUVEE
    voirTerminees -> MessageVide.AUCUNE_TERMINEE
    else -> MessageVide.AUCUNE_A_FAIRE
}

/** La liste montrée : les terminées si la puce est cochée, sinon celles à faire. */
fun listeAffichee(liste: ListeAccueil, voirTerminees: Boolean): List<LigneAccueil> =
    if (voirTerminees) liste.terminees else liste.aFaire

/** L'autre liste, celle que la puce ne montre pas. */
fun autreListe(liste: ListeAccueil, voirTerminees: Boolean): List<LigneAccueil> =
    if (voirTerminees) liste.aFaire else liste.terminees

/**
 * La recherche dit ce qu'elle trouve dans l'autre liste : « 1 tournée terminée correspond », « 2 tournées à faire
 * correspondent ». `null` si rien (ou pas de recherche).
 */
fun texteAutreListe(recherche: String, nb: Int, voirTerminees: Boolean): String? {
    if (recherche.isBlank() || nb <= 0) return null
    val nom = if (voirTerminees) "à faire" else if (nb > 1) "terminées" else "terminée"
    return if (nb > 1) "$nb tournées $nom correspondent" else "1 tournée $nom correspond"
}

/** Le texte de la puce : « Terminées (n) », n étant le nombre de tournées rendues sur le téléphone. */
fun textePuceTerminees(nb: Int): String = "Terminées ($nb)"

/**
 * Les cinq colonnes du kanban, celles de Marculus et de Surculus (`EtatKanban`). L'ordre déclaré est l'ordre des colonnes
 * **et** l'ordre de comparaison, comme dans Surculus.
 */
enum class EtatKanban(val libelle: String) {
    PROPOSEE("Proposée"),
    VALIDEE("Validée"),
    PLANIFIEE("Planifiée"),
    REALISEE("Réalisée"),
    ABANDONNEE("Abandonnée"),
    ;

    companion object {
        /** Statut gardé sur le téléphone, relu depuis son nom ; absent ou inconnu : Proposée. */
        fun depuisNom(nom: String?): EtatKanban = entries.firstOrNull { it.name == nom } ?: PROPOSEE
    }
}

/** Statut minimal imposé par l'avancement : rendue → Réalisée ; au moins une placette notée → Planifiée ; sinon Proposée. */
fun statutMinimal(nbNotees: Int, rendue: Boolean): EtatKanban = when {
    rendue -> EtatKanban.REALISEE
    nbNotees > 0 -> EtatKanban.PLANIFIEE
    else -> EtatKanban.PROPOSEE
}

/** Colonne affichée : le plus avancé du statut gardé et du minimum. */
fun colonneAffichee(stocke: EtatKanban, minimal: EtatKanban): EtatKanban = if (stocke.ordinal < minimal.ordinal) minimal else stocke

/** Dépôt d'une carte dans [cible] : le nouveau statut à garder, ou `null` si refusé (sous le minimum) ou sans effet. */
fun deposer(stocke: EtatKanban, cible: EtatKanban, minimal: EtatKanban): EtatKanban? =
    if (cible != stocke && cible.ordinal >= minimal.ordinal) cible else null

/** Répartit les tournées dans les cinq colonnes (toutes présentes, éventuellement vides), dans l'ordre d'origine. */
fun <T> repartir(lignes: List<T>, colonne: (T) -> EtatKanban): Map<EtatKanban, List<T>> =
    EtatKanban.entries.associateWith { e -> lignes.filter { colonne(it) == e } }

/** Une carte du kanban : la tournée, et le statut gardé sur le téléphone. */
data class CarteKanban(val ligne: LigneAccueil, val stocke: EtatKanban) {
    val minimal: EtatKanban get() = statutMinimal(ligne.nbNotees, ligne.rendue)
    val colonne: EtatKanban get() = colonneAffichee(stocke, minimal)
}
