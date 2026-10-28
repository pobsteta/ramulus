package io.github.pobsteta.ramulus.core.echange

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import java.io.File

/** Une version écartée de l'union : `objet` est une table (`centres`, `notations`, `contre_notations`) ou `photos`. */
internal data class Ecarte(val objet: String, val cle: String, val message: String)

/** L'union de paquets rendus : les lignes gardées par table, les photos gardées, les placettes notées, les conflits. */
internal class Union(
    val lignes: Map<String, List<JsonObject>>,
    val photos: Map<String, PhotoPaquet>,
    val notees: Set<String>,
    val ecartes: List<Ecarte>,
)

/**
 * Fusion de paquets rendus : union sur l'`uuid` des centres et des notes (ADR 0003 § 5, ADR 0005), rejouable.
 * Port de `deperis_v1.fusionner`. Conflit traité comme dans `.sursync` (Surculus `sursync-v1.md`, l. 63) : un même
 * `uuid` de contenu différent n'entre pas dans l'union, ni l'une ni l'autre de ses versions ; le conflit est
 * signalé (`uuid_contenu_different`), jamais tranché en silence ; le reste de l'union est gardé.
 *
 * Une placette « notee » dont le centre est en conflit est gardée avec ses notes, et signalée (F1, Pascal,
 * 2026-10-28) : l'état « notee » vient de la placette, jamais du centre.
 */
internal object Fusion {
    private val TABLES_NOTES = listOf("notations", "contre_notations")

    fun unir(paquets: List<PaquetDeperis>): Union {
        require(paquets.isNotEmpty()) { "aucun paquet à fusionner" }
        val tablesDe = paquets[0].inst.getAsJsonObject("tables")
        val tables = (listOf("centres") + TABLES_NOTES).filter { tablesDe.has(it) }

        fun lignes(p: PaquetDeperis, table: String): List<JsonObject> =
            p.inst.getAsJsonObject("tables")[table]?.takeIf { it.isJsonObject }?.asJsonObject
                ?.getAsJsonArray("lignes")?.map { it.asJsonObject } ?: emptyList()

        val versions = tables.associateWith { LinkedHashMap<String, MutableList<JsonObject>>() }
        for (table in tables) {
            for (p in paquets) {
                for (ligne in lignes(p, table)) {
                    val contenu = JsonObject().also { o -> ligne.entrySet().filter { it.key != "fid" }.forEach { (k, v) -> o.add(k, v) } }
                    val vus = versions.getValue(table).getOrPut(JsonOutils.texte(contenu["uuid"]) ?: "") { ArrayList() }
                    if (vus.none { JsonOutils.egal(it, contenu) }) vus += contenu
                }
            }
        }
        val photosVersions = LinkedHashMap<String, MutableList<PhotoPaquet>>()
        for (p in paquets) {
            for ((nom, photo) in p.photos.orEmpty()) {
                val vus = photosVersions.getOrPut(nom) { ArrayList() }
                if (vus.none { it.sha256 == photo.sha256 }) vus += photo
            }
        }

        val conflits = ArrayList<Ecarte>()
        val ecartes = tables.associateWith { HashSet<String>() }
        for (table in tables) {
            for ((u, vus) in versions.getValue(table)) {
                if (vus.size > 1) {
                    val cles = vus.flatMap { it.keySet() }.toSet().filter { k -> vus.any { a -> vus.any { b -> !JsonOutils.egal(a[k], b[k]) } } }.sorted()
                    conflits += Ecarte(table, u, "$table : uuid $u porte ${vus.size} contenus différents (colonnes $cles) ; aucune version gardée")
                    ecartes.getValue(table) += u
                }
            }
        }
        val photosEnConflit = photosVersions.filterValues { it.size > 1 }.keys
        for (nom in photosEnConflit.sorted()) {
            conflits += Ecarte("photos", nom, "photo $nom : ${photosVersions.getValue(nom).size} contenus différents ; aucune version gardée, ni la note qui la cite")
        }

        fun citees(contenu: JsonObject): Set<String> =
            (contenu["photos"] as? JsonArray)?.mapNotNull { (it as? JsonObject)?.let { p -> JsonOutils.texte(p["nom"]) } }?.toSet() ?: emptySet()

        for (table in TABLES_NOTES.filter { it in tables }) {
            for ((u, vus) in versions.getValue(table)) {
                val noms = vus.flatMap { citees(it) }.toSet()
                if (u !in ecartes.getValue(table) && noms.any { it in photosEnConflit }) {
                    ecartes.getValue(table) += u
                    conflits += Ecarte(table, u, "$table : uuid $u écartée avec sa photo en conflit (${noms.filter { it in photosEnConflit }.sorted()})")
                }
            }
        }
        val photosEcartees = HashSet(photosEnConflit)
        for (table in TABLES_NOTES.filter { it in tables }) {
            for (u in ecartes.getValue(table)) versions.getValue(table).getValue(u).forEach { photosEcartees += citees(it) }
        }
        for (nom in (photosEcartees - photosEnConflit).sorted()) {
            if (nom in photosVersions) conflits += Ecarte("photos", nom, "photo $nom écartée avec la note qui la cite")
        }

        val gardees = tables.associateWith { table ->
            versions.getValue(table).filterKeys { it !in ecartes.getValue(table) }.toSortedMap().map { (_, vus) -> vus[0] }
        }
        val notees = paquets.flatMap { lignes(it, "placettes") }
            .filter { JsonOutils.texte(it["etat"]) == "notee" }.mapNotNull { JsonOutils.texte(it["plot_id"]) }.toSet()
        val photos = photosVersions.filterKeys { it !in photosEcartees }.toSortedMap().mapValues { it.value[0] }
        return Union(gardees, photos, notees, conflits.sortedWith(compareBy({ it.objet }, { it.cle })))
    }

    /**
     * Écrit l'union dans `destination`, copie du GeoPackage du premier paquet : ce qui y est inconnu de ce lecteur
     * (tables, colonnes) est gardé tel quel. Les `fid` repartent de 1 dans l'ordre des `uuid`, comme le contrôle de
     * référence ; `etat` vaut « notee » dès qu'un paquet l'a ; `operateur` et `version_ramulus` sont ceux de qui rend.
     */
    fun ecrire(base: PaquetDeperis, union: Union, operateur: String, versionRamulus: String, destination: File) {
        base.gpkg.copyTo(destination, overwrite = true)
        Gpkg.ouvrir(destination).use { cnx ->
            cnx.autoCommit = false
            try {
                for ((table, lignes) in union.lignes) {
                    val cols = Gpkg.colonnes(cnx, table).types
                    cnx.createStatement().use { it.executeUpdate("DELETE FROM ${Gpkg.citer(table)}") }
                    val srsId = Gpkg.srsIdDe(cnx, table)
                    Ecriture.inserer(cnx, table, cols.keys.toList(), lignes.mapIndexed { i, l -> Ecriture.avecFid(l, i + 1L) }, srsId)
                    if (srsId != null) Ecriture.recalculerEmprise(cnx, table)
                    Gpkg.toucherContenu(cnx, table)
                }
                // Les états viennent de l'union : on repart de « vide » puis on pose « notee ».
                cnx.createStatement().use { it.executeUpdate("UPDATE placettes SET etat = NULL") }
                cnx.prepareStatement("UPDATE placettes SET etat = 'notee' WHERE plot_id = ?").use { ps ->
                    for (pid in union.notees) {
                        ps.setString(1, pid)
                        ps.executeUpdate()
                    }
                }
                Gpkg.toucherContenu(cnx, "placettes")
                Ecriture.poserOperateur(cnx, operateur, versionRamulus)
                cnx.commit()
            } catch (e: Throwable) {
                cnx.rollback()
                throw e
            }
        }
    }
}
