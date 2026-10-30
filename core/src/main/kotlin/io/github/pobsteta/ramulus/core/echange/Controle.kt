package io.github.pobsteta.ramulus.core.echange

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import kotlin.math.hypot

/** Ce que conclut un lecteur : `accepte`, `signale` ou `refuse`, les motifs du catalogue, et un message par écart. */
internal class Resultat(val statut: String, val motifs: List<String>, val messages: List<String>) {
    val refuse: Boolean get() = statut == REFUSE

    companion object {
        const val ACCEPTE = "accepte"
        const val SIGNALE = "signale"
        const val REFUSE = "refuse"
    }
}

/**
 * Le contrôle de référence en Kotlin : ce que conclut un lecteur conforme, Ramulus à l'ouverture d'une tournée ou
 * avant de la rendre, Nemeton à l'import. Port de `fixtures/echange/deperis_v1.py` (`controler`), dont les cas
 * communs donnent le résultat attendu ; les règles sont celles de `docs/formats/deperis-v1.md`.
 */
internal object Controle {
    const val MOTIF_CONFLIT = "uuid_contenu_different"
    const val SEUIL_CENTRE_ELOIGNE_M = 30.0 // PROVISOIRE : proposé par l'ADR 0004, non tranché par Pascal
    private val TABLES_NOTES = listOf("notations", "contre_notations")

    /** Ce que Ramulus peut modifier dans ce que Nemeton a écrit (ADR 0003 § 2-3). */
    private val ECRIT_PAR_RAMULUS = mapOf("tournee" to setOf("operateur", "version_ramulus"), "placettes" to setOf("etat"))

    val schema: JsonObject by lazy {
        val flux = Controle::class.java.getResourceAsStream("/deperis-v1.schema.json")
            ?: error("Schéma absent : /deperis-v1.schema.json (docs/formats/deperis-v1.schema.json)")
        JsonOutils.lireStrict(flux.bufferedReader(Charsets.UTF_8).use { it.readText() })!!.asJsonObject
    }

    private val validateur: ValidateurSchema by lazy { ValidateurSchema(schema) }

    /** Les motifs du catalogue `x-motifs` du schéma, avec leur statut (`refuse` ou `signale`). */
    val catalogue: Map<String, String> by lazy {
        schema.getAsJsonObject("x-motifs").entrySet().associate { (k, v) -> k to v.asJsonObject["statut"].asString }
    }

    fun statut(motifs: Collection<String>): String = when {
        motifs.any { catalogue.getValue(it) == Resultat.REFUSE } -> Resultat.REFUSE
        motifs.isNotEmpty() -> Resultat.SIGNALE
        else -> Resultat.ACCEPTE
    }

    private fun lignes(inst: JsonObject, table: String): List<JsonObject> {
        val t = inst.getAsJsonObject("tables")?.get(table)
        return if (t is JsonObject) t.getAsJsonArray("lignes").map { it.asJsonObject } else emptyList()
    }

    private fun est2154(inst: JsonObject, table: String): Boolean {
        val geo = inst.getAsJsonObject("tables")?.get(table)?.takeIf { it.isJsonObject }?.asJsonObject?.get("geometrie")
        if (geo == null || !geo.isJsonObject) return false
        val srs = geo.asJsonObject["srs"]
        return srs is JsonObject && srs["organisation"]?.asString == "EPSG" && srs["code"]?.asLong == 2154L
    }

    private fun point(v: JsonElement?): Pair<Double, Double>? {
        if (v !is JsonObject) return null
        val x = v["x"]
        val y = v["y"]
        return if (JsonOutils.estNombre(x) && JsonOutils.estNombre(y)) x!!.asDouble to y!!.asDouble else null
    }

    fun controler(paquet: PaquetDeperis, depart: PaquetDeperis? = null): Resultat =
        controler(paquet.inst, paquet.photos, depart?.inst)

    fun controler(inst: JsonObject, photos: Map<String, PhotoPaquet>?, depart: JsonObject?): Resultat {
        val motifs = LinkedHashSet<String>()
        val messages = ArrayList<String>()
        fun ajouter(motif: String, message: String) {
            motifs += motif
            messages += "$motif : $message"
        }

        validateur.valider(inst).forEach { ajouter(it.motif, it.message) }

        // Placettes et centres en France : emprise d'EPSG:2154 (Q3), point par point, après projection inverse.
        val horsFrance = HashSet<Int>()
        for (table in listOf("placettes", "centres")) {
            if (!est2154(inst, table)) continue
            for ((i, ligne) in lignes(inst, table).withIndex()) {
                val g = point(ligne["geom"]) ?: continue
                if (!Lambert93.enFrance(g.first, g.second)) {
                    ajouter("hors_france", "$table, ligne ${i + 1} : X=${g.first} Y=${g.second} hors de l'emprise EPSG:2154")
                    if (table == "centres") horsFrance += i
                }
            }
        }

        // Unicité et rattachement.
        val placettes = HashMap<String?, JsonObject>()
        for (ligne in lignes(inst, "placettes")) {
            val pid = JsonOutils.texte(ligne["plot_id"])
            if (pid in placettes) ajouter("structure", "placettes : plot_id '$pid' en double")
            placettes[pid] = ligne
        }
        for (table in listOf("centres") + TABLES_NOTES) {
            val vus = HashSet<String?>()
            for (ligne in lignes(inst, table)) {
                val u = ligne["uuid"]?.let { if (it.isJsonNull) null else it.toString() }
                if (!vus.add(u)) ajouter("uuid_duplique", "$table : uuid $u en double")
                val pid = JsonOutils.texte(ligne["plot_id"])
                val p = placettes[pid]
                if (p == null) {
                    ajouter("rattachement_invalide", "$table : plot_id '${ligne["plot_id"]}' absent de placettes")
                } else if (table in TABLES_NOTES && JsonOutils.estEntier(ligne["rang"]) && JsonOutils.estEntier(p["nb_arbres"]) &&
                    ligne["rang"].asBigDecimal > p["nb_arbres"].asBigDecimal
                ) {
                    ajouter("rattachement_invalide", "$table : rang ${ligne["rang"]} > nb_arbres ${p["nb_arbres"]} de $pid")
                }
            }
        }

        // Signalés : deux notations du même arbre hors contre-notation (ADR 0003 § 5) ; centre loin du point prévu.
        val arbres = HashMap<Triple<JsonElement?, String, JsonElement?>, String?>()
        for (ligne in lignes(inst, "notations")) {
            val uuid = ligne["uuid"]?.toString()
            for (champ in listOf("rang", "repere")) {
                if (ligne[champ] == null || ligne[champ].isJsonNull) continue // repère facultatif : deux NULL ne sont pas le même arbre
                val k = Triple(ligne["plot_id"], champ, ligne[champ])
                if (k in arbres && arbres[k] != uuid) {
                    ajouter("doublon_arbre", "notations : ${k.first} $champ ${k.third} noté deux fois (${arbres[k]}, $uuid)")
                }
                arbres.putIfAbsent(k, uuid)
            }
        }
        if (est2154(inst, "centres") && est2154(inst, "placettes")) {
            for ((i, c) in lignes(inst, "centres").withIndex()) {
                val p = placettes[JsonOutils.texte(c["plot_id"])]
                val gc = point(c["geom"])
                val gp = point(p?.get("geom"))
                if (i in horsFrance || p == null || gc == null || gp == null) continue
                val d = hypot(gc.first - gp.first, gc.second - gp.second)
                if (d > SEUIL_CENTRE_ELOIGNE_M) {
                    ajouter("centre_eloigne", "centres : ${c["plot_id"]} à ${"%.1f".format(d)} m du point prévu (seuil provisoire ${SEUIL_CENTRE_ELOIGNE_M.toInt()} m)")
                }
            }
        }

        // Tous les textes en Unicode NFC (Pascal, 2026-10-28).
        textesNonNfc(inst).forEach { ajouter("texte_non_nfc", it) }

        if (photos != null) controlerPhotos(inst, photos, ::ajouter)
        if (depart != null) controlerPlan(inst, depart, ::ajouter)

        val inconnus = motifs - catalogue.keys
        check(inconnus.isEmpty()) { "motifs hors du catalogue du schéma : $inconnus" }
        return Resultat(statut(motifs), motifs.sorted(), messages)
    }

    /** Chaque texte de l'instantané qui n'est pas en NFC : nom de table, nom de colonne, valeur (photos comprises). */
    fun textesNonNfc(inst: JsonObject): List<String> {
        val sortie = ArrayList<String>()
        fun parcourir(v: JsonElement?, ou: String) {
            when {
                v == null || v.isJsonNull -> {}
                v.isJsonPrimitive -> if (v.asJsonPrimitive.isString && JsonOutils.nonNfc(v.asString)) sortie += "$ou : '${v.asString}' n'est pas en Unicode NFC"
                v is JsonArray -> v.forEachIndexed { i, x -> parcourir(x, "$ou[$i]") }
                v is JsonObject -> v.entrySet().forEach { (k, x) -> parcourir(x, "$ou.$k") }
            }
        }
        val tables = inst.getAsJsonObject("tables") ?: return sortie
        for ((nom, t) in tables.entrySet()) {
            if (JsonOutils.nonNfc(nom)) sortie += "nom de table '$nom' n'est pas en Unicode NFC"
            if (t !is JsonObject) continue
            t.getAsJsonObject("colonnes")?.keySet()?.forEach { col ->
                if (JsonOutils.nonNfc(col)) sortie += "$nom : nom de colonne '$col' n'est pas en Unicode NFC"
            }
            val geo = (t["geometrie"] as? JsonObject)?.get("colonne")?.asString
            t.getAsJsonArray("lignes")?.forEachIndexed { i, ligne ->
                for ((col, v) in ligne.asJsonObject.entrySet()) {
                    if (col != geo) parcourir(v, "$nom, ligne ${i + 1}, $col")
                }
            }
        }
        return sortie
    }

    private fun controlerPhotos(inst: JsonObject, photos: Map<String, PhotoPaquet>, ajouter: (String, String) -> Unit) {
        val citees = LinkedHashMap<String?, String?>()
        for (table in TABLES_NOTES) {
            for (ligne in lignes(inst, table)) {
                (ligne["photos"] as? JsonArray)?.forEach { p ->
                    if (p is JsonObject) citees[JsonOutils.texte(p["nom"])] = JsonOutils.texte(p["sha256"])
                }
            }
        }
        for ((nom, empreinte) in citees) {
            val photo = photos[nom]
            if (photo == null) ajouter("photo_absente", "$nom citée mais absente du paquet")
            else if (photo.sha256 != empreinte) ajouter("empreinte_photo_differente", "$nom : SHA-256 ${photo.sha256} au lieu de $empreinte")
        }
        for (nom in photos.keys) {
            if (nom !in citees) ajouter("photo_non_citee", "$nom présente dans le paquet mais citée par aucune notation")
        }
    }

    /** Ce que Nemeton a écrit revient intact : placettes (sauf `etat`), champs de Nemeton de `tournee`, et, dans une contre-notation, les centres. */
    private fun controlerPlan(inst: JsonObject, depart: JsonObject, ajouter: (String, String) -> Unit) {
        val aComparer = mutableListOf("tournee", "placettes")
        if (depart["nature"]?.takeIf { !it.isJsonNull }?.asString == "contre_notation") aComparer += "centres"
        val tablesDepart = depart.getAsJsonObject("tables")
        val tablesRendu = inst.getAsJsonObject("tables")
        for (table in aComparer) {
            val t0 = tablesDepart[table] as? JsonObject
            val t1 = tablesRendu[table] as? JsonObject
            if (t0 == null || t1 == null) {
                if ((t0 == null) != (t1 == null)) ajouter("plan_modifie", "$table présente d'un côté seulement")
                continue
            }
            val libres = ECRIT_PAR_RAMULUS[table] ?: emptySet()
            val c0 = t0.getAsJsonObject("colonnes").entrySet().filter { it.key !in libres }.associate { it.key to it.value }
            val c1 = t1.getAsJsonObject("colonnes").entrySet().filter { it.key !in libres }.associate { it.key to it.value }
            if (c0.keys != c1.keys || c0.any { (k, v) -> !JsonOutils.egal(v, c1[k]) }) {
                ajouter("plan_modifie", "$table : colonnes changées (disparues ${(c0.keys - c1.keys).sorted()}, apparues ${(c1.keys - c0.keys).sorted()})")
                continue
            }
            if (!JsonOutils.egal(t0["geometrie"], t1["geometrie"])) ajouter("plan_modifie", "$table : géométrie ou repère changés")

            fun cle(ligne: JsonObject): String =
                (if (table == "placettes") ligne["plot_id"] else ligne["uuid"] ?: ligne["fid"]).toString()

            fun indexer(t: JsonObject) = t.getAsJsonArray("lignes").map { it.asJsonObject }.associate { l ->
                cle(l) to JsonObject().also { o -> l.entrySet().filter { it.key !in libres }.forEach { (k, v) -> o.add(k, v) } }
            }
            val l0 = indexer(t0)
            val l1 = indexer(t1)
            if (l0.keys != l1.keys) ajouter("plan_modifie", "$table : lignes ajoutées ou retirées (${(l0.keys.toSet() xor l1.keys.toSet()).sorted()})")
            for (k in (l0.keys intersect l1.keys).sorted()) {
                if (!JsonOutils.egal(l0.getValue(k), l1.getValue(k))) {
                    val ecarts = l0.getValue(k).keySet().filter { !JsonOutils.egal(l0.getValue(k)[it], l1.getValue(k)[it]) }.sorted()
                    ajouter("plan_modifie", "$table $k : $ecarts modifiés")
                }
            }
        }
    }

    private infix fun <T> Set<T>.xor(autre: Set<T>): Set<T> = (this - autre) + (autre - this)
}
