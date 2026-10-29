package io.github.pobsteta.ramulus.tournee

import io.github.pobsteta.ramulus.core.deperis.EssenceDeperis
import io.github.pobsteta.ramulus.core.deperis.Saison
import java.time.LocalDate

/** Les avertissements de saison : jamais un blocage (spec § 4 ; Pascal, 2026-10-28, SA1 et SA2). */
object Avertissements {
    /**
     * **Calendrier provisoire** : aucune source ne donne de dates pour « été » ni « hiver » (saison-deperis.yaml,
     * E2). Valeur de travail, à fixer par Pascal : été = mai à septembre, hiver = novembre à mars ; avril et octobre
     * ne donnent aucun avertissement.
     */
    private val MOIS_ETE = 5..9
    private val MOIS_HIVER = listOf(11, 12, 1, 2, 3)

    /** SA1 : la saison de la campagne ne correspond pas à la date du jour. `null` si tout va bien. */
    fun saisonEtDate(saison: Saison, jour: LocalDate): String? {
        val mois = jour.monthValue
        val ecart = when (saison) {
            Saison.ETE -> mois in MOIS_HIVER
            Saison.HIVER -> mois in MOIS_ETE
        }
        if (!ecart) return null
        val nomSaison = if (saison == Saison.ETE) "d'été" else "d'hiver"
        return "Campagne $nomSaison, mais nous sommes en ${NOMS_MOIS[mois - 1]}. Vous pouvez noter."
    }

    /** SA2 : l'essence n'a pas de fiche pour la saison (charme, chêne liège en hiver) : fiche d'été, avec avertissement. */
    fun ficheManquante(essence: EssenceDeperis, saison: Saison): String? =
        if (essence.fiche(saison) == null) {
            "${essence.nom} : pas de fiche d'${if (saison == Saison.HIVER) "hiver" else "été"}. Notez avec la fiche d'été."
        } else {
            null
        }

    /** La saison enregistrée avec la note : celle de la fiche utilisée (été si la fiche d'hiver n'existe pas). */
    fun saisonDeLaFiche(essence: EssenceDeperis, saison: Saison): Saison =
        if (essence.fiche(saison) == null) Saison.ETE else saison

    private val NOMS_MOIS = listOf(
        "janvier", "février", "mars", "avril", "mai", "juin", "juillet", "août", "septembre", "octobre", "novembre", "décembre",
    )

    /** Les motifs du catalogue de deperis-v1, en français pour l'opérateur (docs/formats/deperis-v1.md). */
    val MOTIFS_EN_CLAIR: Map<String, String> = mapOf(
        "pas_un_geopackage" to "Ce fichier n'est pas un GeoPackage.",
        "version_illisible" to "La version du fichier est illisible.",
        "structure" to "La structure du fichier n'est pas celle attendue.",
        "index_spatial" to "Le fichier porte un index spatial interdit.",
        "repere_hors_2154" to "Un repère n'est pas le Lambert-93.",
        "hors_france" to "Un point tombe hors de France.",
        "colonne_date" to "Une colonne porte le nom « date ».",
        "notations_dans_contre_notation" to "Une première note figure dans une contre-notation.",
        "essence_hors_referentiel" to "Une essence n'est pas dans les 19 fiches.",
        "type_incoherent" to "Le type ne correspond pas à l'essence.",
        "critere_incoherent" to "MR ou MA ne correspond pas à l'essence.",
        "note_hors_bornes" to "Une note n'est pas comprise entre 0 et 5.",
        "classe_differente_abaque" to "Une classe n'est pas celle de l'abaque.",
        "classe_saisie" to "La classe ne se saisit pas : elle se calcule.",
        "ia_incomplete" to "Les colonnes de l'IA sont à moitié remplies.",
        "uuid_duplique" to "Un identifiant apparaît deux fois.",
        "rattachement_invalide" to "Une note est rattachée à une placette inconnue, ou à un rang trop grand.",
        "plan_modifie" to "Ce que Nemeton a écrit a changé.",
        "photo_absente" to "Une photo citée manque.",
        "empreinte_photo_differente" to "Une photo ne correspond plus à son empreinte.",
        "photo_non_citee" to "Une photo n'est citée par aucune note.",
        "texte_non_nfc" to "Un texte n'est pas en Unicode NFC.",
        "doublon_arbre" to "Un arbre est noté deux fois (même rang ou même numéro).",
        "centre_eloigne" to "Un centre est loin du point prévu.",
        "uuid_contenu_different" to "Deux versions d'une même saisie se contredisent.",
    )

    fun enClair(motif: String): String = MOTIFS_EN_CLAIR[motif] ?: motif
}
