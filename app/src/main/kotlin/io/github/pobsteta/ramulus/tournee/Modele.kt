package io.github.pobsteta.ramulus.tournee

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.github.pobsteta.ramulus.core.deperis.Classe
import io.github.pobsteta.ramulus.core.deperis.Saison
import io.github.pobsteta.ramulus.core.gnss.PointLambert93

/** Ce que l'écran sait d'une tournée : lu dans l'instantané du `deperis.gpkg` de travail (docs/formats/deperis-v1.md). */
data class TourneeLue(
    val code: String,
    /** `premiere` ou `contre_notation`. */
    val nature: String,
    val versionNemeton: String?,
    val prepareLe: String?,
    val placettes: List<PlacetteLue>,
) {
    val estContreNotation: Boolean get() = nature == NATURE_CONTRE_NOTATION

    fun placette(plotId: String): PlacetteLue? = placettes.firstOrNull { it.plotId == plotId }

    val nbNotees: Int get() = placettes.count { it.statut == Statut.NOTEE }

    companion object {
        const val NATURE_PREMIERE = "premiere"
        const val NATURE_CONTRE_NOTATION = "contre_notation"
    }
}

enum class Statut { A_FAIRE, CENTRE_RELEVE, EN_COURS, NOTEE }

data class CentreLu(val x: Double, val y: Double, val precisionM: Double?, val operateur: String?)

/** Un arbre déjà noté, tel que le fichier le porte (la classe vient de l'abaque, calculée par l'échange). */
data class ArbreLu(
    val uuid: String,
    val rang: Int,
    val repere: String,
    val essence: String,
    val mb: Int,
    val second: Int,
    val classe: Classe?,
)

data class PlacetteLue(
    val plotId: String,
    val prevu: PointLambert93,
    val nbArbres: Int,
    val regleChoix: String?,
    val saison: Saison,
    val collectePhotos: Boolean,
    val contreNotation: Boolean,
    val etat: String?,
    val centre: CentreLu?,
    val arbres: List<ArbreLu>,
) {
    val estNotee: Boolean get() = etat == ETAT_NOTEE

    val statut: Statut
        get() = when {
            estNotee -> Statut.NOTEE
            arbres.isNotEmpty() -> Statut.EN_COURS
            centre != null -> Statut.CENTRE_RELEVE
            else -> Statut.A_FAIRE
        }

    /** Premier rang du tour d'horizon qui n'a pas encore de note, ou le dernier rang si tout est noté. */
    val prochainRang: Int get() = (1..nbArbres).firstOrNull { r -> arbres.none { it.rang == r } } ?: nbArbres

    companion object {
        const val ETAT_NOTEE = "notee"
    }
}

/**
 * Lit l'instantané JSON de l'échange. En contre-notation, **seule** la table `contre_notations` est lue : une première
 * note n'est jamais montrée au second opérateur (décision de Pascal, 2026-10-28).
 */
object LectureTournee {
    fun depuis(instantaneJson: String): TourneeLue {
        val racine = JsonParser.parseString(instantaneJson).asJsonObject
        val tables = racine.getAsJsonObject("tables")
        val tournee = lignes(tables, "tournee").single()
        val nature = texte(tournee, "nature") ?: TourneeLue.NATURE_PREMIERE
        val tableNotes = if (nature == TourneeLue.NATURE_CONTRE_NOTATION) "contre_notations" else "notations"
        val notes = lignes(tables, tableNotes)
        val centres = lignes(tables, "centres")

        val placettes = lignes(tables, "placettes").map { p ->
            val id = texte(p, "plot_id") ?: error("placette sans plot_id")
            val geom = p.getAsJsonObject("geom")
            val centre = centres.lastOrNull { texte(it, "plot_id") == id }?.let { c ->
                val g = c.getAsJsonObject("geom")
                CentreLu(g["x"].asDouble, g["y"].asDouble, nombre(c, "precision_m"), texte(c, "operateur"))
            }
            PlacetteLue(
                plotId = id,
                prevu = PointLambert93(geom["x"].asDouble, geom["y"].asDouble),
                nbArbres = entier(p, "nb_arbres") ?: 1,
                regleChoix = texte(p, "regle_choix"),
                saison = if (texte(p, "saison") == "HIVER") Saison.HIVER else Saison.ETE,
                collectePhotos = entier(p, "collecte_photos") == 1,
                contreNotation = entier(p, "contre_notation") == 1,
                etat = texte(p, "etat")?.ifBlank { null },
                centre = centre,
                arbres = notes.filter { texte(it, "plot_id") == id }.map { n ->
                    ArbreLu(
                        uuid = texte(n, "uuid") ?: "",
                        rang = entier(n, "rang") ?: 0,
                        repere = texte(n, "repere") ?: "",
                        essence = texte(n, "essence") ?: "",
                        mb = entier(n, "mb") ?: 0,
                        second = entier(n, "second") ?: 0,
                        classe = texte(n, "classe")?.let { runCatching { Classe.lire(it) }.getOrNull() },
                    )
                }.sortedBy { it.rang },
            )
        }
        return TourneeLue(
            code = texte(tournee, "code_tournee") ?: error("tournée sans code"),
            nature = nature,
            versionNemeton = texte(tournee, "version_nemeton"),
            prepareLe = texte(tournee, "prepare_le"),
            placettes = placettes,
        )
    }

    private fun lignes(tables: JsonObject, nom: String): List<JsonObject> =
        tables.getAsJsonObject(nom)?.getAsJsonArray("lignes")?.map { it.asJsonObject } ?: emptyList()

    private fun valeur(o: JsonObject, cle: String): JsonElement? = o[cle]?.takeUnless { it.isJsonNull }
    private fun texte(o: JsonObject, cle: String): String? = valeur(o, cle)?.asString
    private fun entier(o: JsonObject, cle: String): Int? = valeur(o, cle)?.asInt
    private fun nombre(o: JsonObject, cle: String): Double? = valeur(o, cle)?.asDouble
}
