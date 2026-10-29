package io.github.pobsteta.ramulus.core.echange

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import org.sqlite.SQLiteConfig
import java.io.File
import java.sql.Connection
import java.sql.PreparedStatement
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/** Le fichier n'est pas un GeoPackage lisible (motif `pas_un_geopackage`). */
internal class PasUnGeopackage(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Accès au GeoPackage par `sqlite-jdbc`, le même sur la JVM et sur l'appareil (Surculus ADR 0011, ADR 0003 § 1).
 * La connexion est ouverte sans `DriverManager` (inutile, et son chargement par `ServiceLoader` est fragile sous
 * Android). Aucune table n'est jamais créée ni supprimée (Q13).
 */
internal object Gpkg {
    fun ouvrir(fichier: File, lectureSeule: Boolean = false): Connection {
        val config = SQLiteConfig()
        if (lectureSeule) config.setReadOnly(true)
        return config.createConnection("jdbc:sqlite:${fichier.absolutePath}")
    }

    fun citer(nom: String): String = "\"" + nom.replace("\"", "\"\"") + "\""

    /** Horodatage de `gpkg_contents.last_change` : ISO 8601 en UTC, suffixe Z, millisecondes (forme de GDAL). */
    fun maintenant(): String =
        ZonedDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"))

    /** Les colonnes d'une table, dans l'ordre : nom -> type déclaré en majuscules ; et la clé si elle est unique. */
    class Colonnes(val types: LinkedHashMap<String, String>, val cle: String?)

    fun colonnes(cnx: Connection, table: String): Colonnes {
        val types = LinkedHashMap<String, String>()
        val cles = ArrayList<String>()
        cnx.createStatement().use { st ->
            st.executeQuery("PRAGMA table_info(${citer(table)})").use { rs ->
                while (rs.next()) {
                    types[rs.getString("name")] = (rs.getString("type") ?: "").uppercase()
                    if (rs.getInt("pk") > 0) cles += rs.getString("name")
                }
            }
        }
        return Colonnes(types, cles.singleOrNull())
    }

    fun tables(cnx: Connection): Map<String, String> {
        val sortie = LinkedHashMap<String, String>()
        cnx.createStatement().use { st ->
            st.executeQuery("SELECT table_name, data_type FROM gpkg_contents ORDER BY table_name").use { rs ->
                while (rs.next()) sortie[rs.getString(1)] = rs.getString(2)
            }
        }
        return sortie
    }

    /** `srs_id` de la colonne géométrique d'une table, lu dans `gpkg_geometry_columns` (jamais supposé). */
    fun srsIdDe(cnx: Connection, table: String): Int? =
        cnx.prepareStatement("SELECT srs_id FROM gpkg_geometry_columns WHERE table_name = ?").use { ps ->
            ps.setString(1, table)
            ps.executeQuery().use { rs -> if (rs.next()) rs.getInt(1) else null }
        }

    /** Affecte une valeur JSON au paramètre `i` : entier si le nombre s'écrit sans fraction, réel sinon. */
    fun lier(ps: PreparedStatement, i: Int, v: JsonElement?) {
        when {
            v == null || v.isJsonNull -> ps.setObject(i, null)
            v is JsonPrimitive && v.isBoolean -> ps.setInt(i, if (v.asBoolean) 1 else 0)
            v is JsonPrimitive && v.isNumber -> {
                val texte = v.asString
                if (ENTIER.matches(texte)) ps.setLong(i, texte.toLong()) else ps.setDouble(i, v.asDouble)
            }
            v is JsonPrimitive -> ps.setString(i, v.asString)
            else -> ps.setString(i, JsonOutils.gson.toJson(v))
        }
    }

    private val ENTIER = Regex("-?[0-9]{1,18}")

    /** Met à jour `last_change` de la table, et l'emprise de `gpkg_contents` si `etendre` donne un point. */
    fun toucherContenu(cnx: Connection, table: String, x: Double? = null, y: Double? = null) {
        if (x == null || y == null) {
            cnx.prepareStatement("UPDATE gpkg_contents SET last_change = ? WHERE table_name = ?").use { ps ->
                ps.setString(1, maintenant())
                ps.setString(2, table)
                ps.executeUpdate()
            }
        } else {
            cnx.prepareStatement(
                "UPDATE gpkg_contents SET last_change = ?, " +
                    "min_x = MIN(COALESCE(min_x, ?), ?), max_x = MAX(COALESCE(max_x, ?), ?), " +
                    "min_y = MIN(COALESCE(min_y, ?), ?), max_y = MAX(COALESCE(max_y, ?), ?) WHERE table_name = ?",
            ).use { ps ->
                ps.setString(1, maintenant())
                listOf(x, x, x, x, y, y, y, y).forEachIndexed { k, d -> ps.setDouble(2 + k, d) }
                ps.setString(10, table)
                ps.executeUpdate()
            }
        }
    }
}

/**
 * L'instantané JSON d'un GeoPackage (docs/formats/deperis-v1.md, « Instantané »), construit comme le contrôle de
 * référence (`deperis_v1.instantane`) et comme le fera Nemeton en R : c'est ce que valide le schéma.
 */
internal object Instantane {
    private val COLONNES_JSON = setOf("photos")

    fun lire(cnx: Connection, photosDuPaquet: List<Pair<String, String>>? = null): JsonObject {
        try {
            return construire(cnx, photosDuPaquet)
        } catch (e: java.sql.SQLException) {
            throw PasUnGeopackage("fichier SQLite illisible comme GeoPackage : ${e.message}", e)
        }
    }

    private fun construire(cnx: Connection, photosDuPaquet: List<Pair<String, String>>?): JsonObject {
        val reperes = HashMap<Int, JsonObject>()
        val geometries = HashMap<String, Triple<String, String, Int>>()
        val rtreeExt = HashSet<String>()
        val maitre = ArrayList<Pair<String, String>>()
        cnx.createStatement().use { st ->
            st.executeQuery("SELECT srs_id, organization, organization_coordsys_id FROM gpkg_spatial_ref_sys").use { rs ->
                while (rs.next()) {
                    reperes[rs.getInt(1)] = JsonObject().also {
                        it.addProperty("organisation", rs.getString(2))
                        it.addProperty("code", rs.getLong(3))
                    }
                }
            }
            st.executeQuery("SELECT table_name, column_name, geometry_type_name, srs_id FROM gpkg_geometry_columns").use { rs ->
                while (rs.next()) geometries[rs.getString(1)] = Triple(rs.getString(2), rs.getString(3), rs.getInt(4))
            }
            try {
                st.executeQuery("SELECT table_name FROM gpkg_extensions WHERE extension_name = 'gpkg_rtree_index'").use { rs ->
                    while (rs.next()) rtreeExt += rs.getString(1)
                }
            } catch (_: java.sql.SQLException) {
                // pas de table gpkg_extensions : aucune extension
            }
            st.executeQuery("SELECT type, name FROM sqlite_master").use { rs ->
                while (rs.next()) maitre += rs.getString(1) to rs.getString(2)
            }
        }

        val tables = JsonObject()
        for ((nom, dataType) in Gpkg.tables(cnx)) {
            val cols = Gpkg.colonnes(cnx, nom)
            val geo = geometries[nom]
            val geometrie: JsonElement = if (geo == null) JsonNull.INSTANCE else JsonObject().also {
                it.addProperty("colonne", geo.first)
                it.addProperty("type", geo.second.uppercase())
                it.add("srs", reperes[geo.third] ?: JsonNull.INSTANCE)
            }
            val prefixe = "rtree_${nom}_"
            val index = nom in rtreeExt || maitre.any { (t, n) -> n.startsWith(prefixe) && (t == "table" || t == "trigger") }
            val lignes = JsonArray()
            val ordre = cols.cle?.let { " ORDER BY ${Gpkg.citer(it)}" } ?: ""
            cnx.createStatement().use { st ->
                st.executeQuery("SELECT * FROM ${Gpkg.citer(nom)}$ordre").use { rs ->
                    val meta = rs.metaData
                    val noms = (1..meta.columnCount).map { meta.getColumnName(it) }
                    while (rs.next()) {
                        val ligne = JsonObject()
                        for ((i, k) in noms.withIndex()) {
                            ligne.add(k, valeur(rs.getObject(i + 1), k, geo?.first))
                        }
                        lignes.add(ligne)
                    }
                }
            }
            tables.add(
                nom,
                JsonObject().also {
                    it.addProperty("data_type", dataType)
                    it.add("geometrie", geometrie)
                    it.addProperty("index_spatial", index)
                    it.add("cle_primaire", cols.cle?.let { c -> JsonPrimitive(c) } ?: JsonNull.INSTANCE)
                    it.add("colonnes", JsonObject().also { c -> cols.types.forEach { (k, t) -> c.addProperty(k, t) } })
                    it.add("lignes", lignes)
                },
            )
        }

        val tournee = tables["tournee"]?.asJsonObject?.getAsJsonArray("lignes")
        val unique = tournee != null && tournee.size() == 1
        val resultat = JsonObject()
        resultat.addProperty("application_id", pragma(cnx, "application_id"))
        resultat.addProperty("user_version", pragma(cnx, "user_version"))
        resultat.add("version_schema", if (unique) tournee!![0].asJsonObject["version_schema"] ?: JsonNull.INSTANCE else JsonNull.INSTANCE)
        resultat.add("nature", if (unique) tournee!![0].asJsonObject["nature"] ?: JsonNull.INSTANCE else JsonNull.INSTANCE)
        resultat.add("tables", tables)
        if (photosDuPaquet != null) {
            resultat.add(
                "photos_du_paquet",
                JsonArray().also { a ->
                    photosDuPaquet.forEach { (nom, sha) -> a.add(JsonObject().also { o -> o.addProperty("nom", nom); o.addProperty("sha256", sha) }) }
                },
            )
        }
        return resultat
    }

    private fun pragma(cnx: Connection, nom: String): Long =
        cnx.createStatement().use { st -> st.executeQuery("PRAGMA $nom").use { rs -> rs.next(); rs.getLong(1) } }

    private fun valeur(v: Any?, colonne: String, colonneGeometrique: String?): JsonElement = when {
        v == null -> JsonNull.INSTANCE
        colonne == colonneGeometrique -> try {
            val p = GeometrieGpkg.decoderPoint(v as? ByteArray)
            if (p == null) JsonNull.INSTANCE else JsonObject().also { it.addProperty("x", p.x); it.addProperty("y", p.y) }
        } catch (e: IllegalArgumentException) {
            JsonPrimitive("géométrie illisible : ${e.message}")
        }
        v is ByteArray -> JsonPrimitive(v.joinToString("") { "%02x".format(it) })
        v is String && colonne in COLONNES_JSON -> JsonOutils.lireStrict(v) ?: JsonPrimitive(v)
        v is String -> JsonPrimitive(v)
        v is Int -> JsonPrimitive(v.toLong())
        v is Long -> JsonPrimitive(v)
        v is Number -> JsonPrimitive(v.toDouble())
        v is Boolean -> JsonPrimitive(if (v) 1L else 0L)
        else -> JsonPrimitive(v.toString())
    }
}
