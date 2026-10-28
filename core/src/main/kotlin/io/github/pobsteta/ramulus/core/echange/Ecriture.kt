package io.github.pobsteta.ramulus.core.echange

import com.google.gson.JsonArray
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import io.github.pobsteta.ramulus.core.deperis.Abaque
import java.io.File
import java.sql.Connection

/**
 * Une écriture refusée : le message commence par le code du motif du catalogue (`note_hors_bornes`, `hors_france`,
 * `classe_saisie`…), et le fichier reste tel qu'il était (la transaction est annulée).
 */
class EchangeRefuse(val motifs: List<String>, detail: String) :
    IllegalArgumentException("${motifs.joinToString(", ")} : $detail")

/** Ce que Ramulus écrit dans la tournée de Nemeton : centres, notes, état des placettes, opérateur (ADR 0003 § 2-3). */
internal object Ecriture {
    private val COLONNES_CENTRE = listOf("uuid", "plot_id", "precision_m", "nb_mesures", "source", "date_releve", "operateur", "geom")
    private val COLONNES_NOTE = listOf(
        "uuid", "plot_id", "rang", "repere", "essence", "type", "mb", "second", "critere", "saison", "date_notation",
        "operateur", "photos", "ia_premiere_mb", "ia_premiere_second", "ia_mb", "ia_second", "ia_confiance", "ia_modele",
    )

    fun avecFid(ligne: JsonObject, fid: Long): JsonObject = JsonObject().also { o ->
        o.addProperty("fid", fid)
        ligne.entrySet().filter { it.key != "fid" }.forEach { (k, v) -> o.add(k, v) }
    }

    /** Insère `lignes` (valeurs de l'instantané : géométrie `{x, y}`, `photos` en tableau) dans `table`. */
    fun inserer(cnx: Connection, table: String, colonnes: List<String>, lignes: List<JsonObject>, srsId: Int?) {
        if (lignes.isEmpty()) return
        val geo = colonneGeometrique(cnx, table)
        val sql = "INSERT INTO ${Gpkg.citer(table)} (${colonnes.joinToString(",") { Gpkg.citer(it) }}) VALUES (${colonnes.joinToString(",") { "?" }})"
        cnx.prepareStatement(sql).use { ps ->
            for (ligne in lignes) {
                for ((i, col) in colonnes.withIndex()) {
                    val v = ligne[col]
                    when {
                        col == geo && v is JsonObject -> ps.setBytes(i + 1, GeometrieGpkg.encoderPoint(v["x"].asDouble, v["y"].asDouble, srsId ?: error("repère inconnu de $table")))
                        v is JsonArray -> ps.setString(i + 1, JsonOutils.gson.toJson(v))
                        else -> Gpkg.lier(ps, i + 1, v)
                    }
                }
                ps.executeUpdate()
            }
        }
    }

    fun colonneGeometrique(cnx: Connection, table: String): String? =
        cnx.prepareStatement("SELECT column_name FROM gpkg_geometry_columns WHERE table_name = ?").use { ps ->
            ps.setString(1, table)
            ps.executeQuery().use { rs -> if (rs.next()) rs.getString(1) else null }
        }

    /** Recalcule l'emprise de `gpkg_contents` d'une couche de points d'après ses géométries. */
    fun recalculerEmprise(cnx: Connection, table: String) {
        val geo = colonneGeometrique(cnx, table) ?: return
        var minX = Double.POSITIVE_INFINITY
        var minY = Double.POSITIVE_INFINITY
        var maxX = Double.NEGATIVE_INFINITY
        var maxY = Double.NEGATIVE_INFINITY
        cnx.createStatement().use { st ->
            st.executeQuery("SELECT ${Gpkg.citer(geo)} FROM ${Gpkg.citer(table)}").use { rs ->
                while (rs.next()) {
                    val p = GeometrieGpkg.decoderPoint(rs.getBytes(1)) ?: continue
                    minX = minOf(minX, p.x); maxX = maxOf(maxX, p.x)
                    minY = minOf(minY, p.y); maxY = maxOf(maxY, p.y)
                }
            }
        }
        cnx.prepareStatement("UPDATE gpkg_contents SET min_x = ?, min_y = ?, max_x = ?, max_y = ? WHERE table_name = ?").use { ps ->
            if (minX.isFinite()) {
                ps.setDouble(1, minX); ps.setDouble(2, minY); ps.setDouble(3, maxX); ps.setDouble(4, maxY)
            } else {
                for (i in 1..4) ps.setObject(i, null)
            }
            ps.setString(5, table)
            ps.executeUpdate()
        }
    }

    fun poserOperateur(cnx: Connection, operateur: String, versionRamulus: String) {
        cnx.prepareStatement("UPDATE tournee SET operateur = ?, version_ramulus = ?").use { ps ->
            ps.setString(1, JsonOutils.nfc(operateur))
            ps.setString(2, JsonOutils.nfc(versionRamulus))
            ps.executeUpdate()
        }
    }

    // ------------------------------------------------------------------------------------------------------------
    // Les écritures de l'opérateur : une transaction, un contrôle, et rien d'écrit si le résultat est refusé
    // ------------------------------------------------------------------------------------------------------------

    /**
     * Exécute `action` dans une transaction, relit l'instantané du fichier ainsi modifié et le contrôle comme le fait
     * l'ouverture d'une tournée. Un motif de refus annule tout (le fichier reste tel qu'il était) et lève
     * [EchangeRefuse] ; un simple signalement (centre éloigné, doublon d'arbre) ne bloque pas l'écriture.
     */
    private fun <T> ecrireControle(gpkg: File, action: (Connection) -> T): T {
        val cnx = try {
            Gpkg.ouvrir(gpkg)
        } catch (e: java.sql.SQLException) {
            throw EchangeRefuse(listOf("pas_un_geopackage"), "${gpkg.name} : ${e.message}")
        }
        cnx.use {
            it.autoCommit = false
            try {
                val resultat = action(it)
                val controle = Controle.controler(Instantane.lire(it), null, null)
                if (controle.refuse) {
                    val motifs = controle.motifs.filter { m -> Controle.catalogue.getValue(m) == Resultat.REFUSE }
                    throw EchangeRefuse(motifs, controle.messages.firstOrNull { m -> motifs.any { x -> m.startsWith("$x :") } } ?: motifs.toString())
                }
                it.commit()
                return resultat
            } catch (e: Throwable) {
                it.rollback()
                throw e
            }
        }
    }

    private fun lireObjet(texte: String, nature: String): JsonObject {
        val e = JsonOutils.lireStrict(texte)
        if (e == null || !e.isJsonObject) throw EchangeRefuse(listOf("structure"), "$nature : texte JSON d'objet attendu")
        return JsonOutils.enNfc(e).asJsonObject
    }

    private fun natureDeLaTournee(cnx: Connection): String {
        val natures = ArrayList<String?>()
        cnx.createStatement().use { st -> st.executeQuery("SELECT nature FROM tournee").use { rs -> while (rs.next()) natures += rs.getString(1) } }
        val n = natures.singleOrNull()
        if (n != "premiere" && n != "contre_notation") throw EchangeRefuse(listOf("version_illisible"), "tournee : une ligne et une nature `premiere` ou `contre_notation` attendues")
        return n
    }

    fun enregistrerCentre(gpkg: File, centreJson: String) {
        val centre = lireObjet(centreJson, "centre")
        val inconnus = centre.keySet() - COLONNES_CENTRE.toSet()
        if (inconnus.isNotEmpty()) throw EchangeRefuse(listOf("structure"), "centre : colonnes inconnues $inconnus")
        val geom = centre["geom"]
        if (geom !is JsonObject || !JsonOutils.estNombre(geom["x"]) || !JsonOutils.estNombre(geom["y"])) {
            throw EchangeRefuse(listOf("structure"), "centre : geom attendu sous la forme {\"x\": est, \"y\": nord} en EPSG:2154")
        }
        ecrireControle(gpkg) { cnx ->
            if (natureDeLaTournee(cnx) == "contre_notation") {
                throw EchangeRefuse(listOf("plan_modifie"), "centres : dans une contre-notation, le centre du premier opérateur est en lecture (Q12)")
            }
            val ligne = JsonObject().also { o -> COLONNES_CENTRE.forEach { o.add(it, centre[it] ?: JsonNull.INSTANCE) } }
            val srsId = Gpkg.srsIdDe(cnx, "centres")
            inserer(cnx, "centres", COLONNES_CENTRE, listOf(ligne), srsId)
            Gpkg.toucherContenu(cnx, "centres", geom["x"].asDouble, geom["y"].asDouble)
        }
    }

    fun enregistrerNotation(gpkg: File, notationJson: String): String {
        val note = lireObjet(notationJson, "notation")
        // La classe ne se saisit pas, juste ou fausse : elle est calculée par l'abaque (ADR 0001, spec § 4 ; Pascal, T2).
        // Absente, `null`, `""` ou faite de blancs : la classe compte comme absente et se calcule, sans refus (Pascal, Q4 de 010).
        val classeFournie = note["classe"]
        val classeAbsente = JsonOutils.estNul(classeFournie) || (JsonOutils.estTexte(classeFournie) && classeFournie!!.asString.isBlank())
        if (!classeAbsente) {
            throw EchangeRefuse(listOf("classe_saisie"), "la classe ne se saisit pas, elle est calculée par l'abaque à partir de MB et de MR ou MA")
        }
        val inconnus = note.keySet() - COLONNES_NOTE.toSet() - "classe"
        if (inconnus.isNotEmpty()) throw EchangeRefuse(listOf("structure"), "notation : colonnes inconnues $inconnus")
        if (JsonOutils.estNul(note["uuid"])) throw EchangeRefuse(listOf("structure"), "notation : uuid absent")
        // `photos` peut arriver en texte JSON : le fichier la garde en texte, le contrôle la lit en tableau.
        note["photos"]?.takeIf { JsonOutils.estTexte(it) }?.let { t -> JsonOutils.lireStrict(t.asString)?.let { note.add("photos", it) } }

        val mb = note["mb"]
        val second = note["second"]
        val notesValides = JsonOutils.estEntier(mb) && JsonOutils.estEntier(second) &&
            mb!!.asInt in 0..5 && second!!.asInt in 0..5
        // Notes hors bornes : pas de classe à calculer ; « A » n'est qu'un remplissage, le contrôle refuse la note
        // pour `note_hors_bornes` et la transaction est annulée.
        val classe = if (notesValides) Abaque.classe(mb!!.asInt, second!!.asInt).name else "A"

        return ecrireControle(gpkg) { cnx ->
            val table = if (natureDeLaTournee(cnx) == "premiere") "notations" else "contre_notations"
            if (table !in Gpkg.tables(cnx)) throw EchangeRefuse(listOf("structure"), "$table : table absente (Ramulus n'en crée aucune, Q13)")
            val ligne = JsonObject()
            for (col in COLONNES_NOTE.take(8) + "classe" + COLONNES_NOTE.drop(8)) {
                ligne.add(
                    col,
                    when {
                        col == "classe" -> com.google.gson.JsonPrimitive(classe)
                        col == "photos" && JsonOutils.estNul(note[col]) -> JsonArray()
                        else -> note[col] ?: JsonNull.INSTANCE
                    },
                )
            }
            val colonnes = Gpkg.colonnes(cnx, table).types.keys.filter { it != "fid" && ligne.has(it) }
            inserer(cnx, table, colonnes, listOf(ligne), null)
            Gpkg.toucherContenu(cnx, table)
            val enregistree = JsonObject()
            cnx.createStatement().use { st -> st.executeQuery("SELECT last_insert_rowid()").use { rs -> rs.next(); enregistree.addProperty("fid", rs.getLong(1)) } }
            ligne.entrySet().forEach { (k, v) -> enregistree.add(k, v) }
            JsonOutils.gson.toJson(enregistree)
        }
    }

    fun cloturerPlacette(gpkg: File, plotId: String) {
        ecrireControle(gpkg) { cnx ->
            val n = cnx.prepareStatement("UPDATE placettes SET etat = 'notee' WHERE plot_id = ?").use { ps ->
                ps.setString(1, JsonOutils.nfc(plotId))
                ps.executeUpdate()
            }
            if (n == 0) throw EchangeRefuse(listOf("rattachement_invalide"), "plot_id '$plotId' absent de placettes")
            Gpkg.toucherContenu(cnx, "placettes")
        }
    }
}
