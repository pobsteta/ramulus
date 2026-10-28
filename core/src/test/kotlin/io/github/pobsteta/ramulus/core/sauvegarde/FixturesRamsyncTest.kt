package io.github.pobsteta.ramulus.core.sauvegarde

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.github.pobsteta.ramulus.core.journal.ligne
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.util.zip.GZIPInputStream

/** Fichiers d'exemple `fixtures/ramsync/` et schéma `docs/formats/ramsync-v1.schema.json`. */
class FixturesRamsyncTest {
    private val dossier = File("../fixtures/ramsync")
    private val attendus = JsonParser.parseString(File(dossier, "attendus.json").readText()).asJsonObject
    private val schema = JsonParser.parseString(File("../docs/formats/ramsync-v1.schema.json").readText()).asJsonObject

    // 21
    @Test
    fun `les fichiers d exemple donnent les signalements attendus`() {
        val fichiers = attendus.getAsJsonObject("fichiers")
        val presents = dossier.listFiles()!!.filter { it.name.endsWith(".ramsync") }.map { it.name }.toSet()
        assertEquals(presents, fichiers.keySet(), "chaque fichier d'exemple a son attendu, et réciproquement")
        for ((nom, a) in fichiers.entrySet()) {
            val o = a.asJsonObject
            val r = LectureRamsync.lire(File(dossier, nom).readBytes(), attendus["lecteur_version"].asInt)
            val motif = o["motif"].takeIf { !it.isJsonNull }?.asString
            assertEquals(motif, r.motif?.code, nom)
            assertEquals(o.getAsJsonArray("signalements").map { it.asString }.toSet(), r.signalements.map { it.code }.toSet(), nom)
            assertEquals(o["lignes_lues"].asInt, r.lignes.size, nom)
            assertEquals(o["lignes_refusees"].asInt, r.lignesRefusees, nom)
            assertEquals(o["lignes_ignorees"].asInt, r.lignesIgnorees, nom)
        }
    }

    @Test
    fun `l exemple ecrit par Python est celui qu ecrit Ramulus, au caractere pres`() {
        val jsonl = File(dossier, "exemple-v1.jsonl").readBytes()
        val ramsync = File(dossier, "exemple-v1.ramsync").readBytes()
        assertArrayEquals(jsonl, GZIPInputStream(ByteArrayInputStream(ramsync)).readBytes())
        val r = LectureRamsync.lire(ramsync)
        val e = r.entete!!
        val reecrit = EcritureRamsync.ecrire(
            e.codeTournee, e.versionNemeton, e.versionRamulus, e.appareil, e.creeLeUtcMs, r.lignes.reversed(),
        )
        assertArrayEquals(jsonl, GZIPInputStream(ByteArrayInputStream(reecrit)).readBytes())
    }

    // 22
    @Test
    fun `les exemples et ce qu ecrit Ramulus sont valides pour le schema`() {
        val lignesExemple = File(dossier, "exemple-v1.jsonl").readLines()
        val ecrit = EcritureRamsync.ecrire(
            "Forêt d'exemple", null, "0.1.0", "a3f9c2d1", 0,
            listOf(ligne("u1", tournee = "Forêt d'exemple"), ligne("u2", tournee = "Forêt d'exemple", heureGnss = 5, ageFix = 0)),
        )
        val lignesEcrites = String(GZIPInputStream(ByteArrayInputStream(ecrit)).readBytes(), Charsets.UTF_8).trimEnd('\n').split("\n")
        for (lignes in listOf(lignesExemple, lignesEcrites)) {
            assertEquals(listOf("entete"), valides(lignes.first()))
            lignes.drop(1).dropLast(1).forEach { assertEquals(listOf("saisie"), valides(it), it) }
            assertEquals(listOf("pied"), valides(lignes.last()))
        }
        // Le validateur refuse bien : type hors liste, champ en trop, champ absent, version 0, pied court.
        val saisie = lignesExemple[1]
        assertTrue(valides(saisie.replace("\"type\":\"centre\"", "\"type\":\"classe\"")).isEmpty())
        assertTrue(valides(saisie.replace("{\"uuid\"", "{\"classe\":\"A\",\"uuid\"")).isEmpty())
        assertTrue(valides(saisie.replace(",\"age_fix_ms\":800", "")).isEmpty())
        assertTrue(valides(lignesExemple[0].replace("\"version\":1", "\"version\":0")).isEmpty())
        assertTrue(valides("{\"sha256\":\"abc\"}").isEmpty())
        assertTrue(valides(lignesExemple[0].replace("\"version_ramulus\":\"exemple\",", "")).isEmpty())
    }

    /** Noms des définitions (`entete`, `saisie`, `pied`) pour lesquelles la ligne est valide. */
    private fun valides(ligne: String): List<String> {
        val o = JsonParser.parseString(ligne)
        return schema.getAsJsonObject("\$defs").entrySet().filter { (_, d) -> valide(o, d.asJsonObject) }.map { it.key }
    }

    /**
     * Validateur du sous-ensemble de JSON Schema qu'emploie `ramsync-v1.schema.json` : `type`, `const`, `enum`,
     * `required`, `properties`, `additionalProperties: false`, `minLength`, `minimum`, `pattern`. Un mot-clé inconnu
     * fait échouer le test, pour qu'un schéma enrichi ne soit pas validé à moitié.
     */
    private fun valide(v: JsonElement, s: JsonObject): Boolean {
        val connus = setOf(
            "type", "const", "enum", "required", "properties", "additionalProperties", "minLength", "minimum",
            "pattern", "description",
        )
        s.keySet().forEach { require(it in connus) { "mot-clé de schéma non pris en charge : $it" } }
        s["type"]?.let { t ->
            val types = if (t.isJsonArray) t.asJsonArray.map { it.asString } else listOf(t.asString)
            if (types.none { aLeType(v, it) }) return false
        }
        s["const"]?.let { if (v != it) return false }
        s["enum"]?.let { e -> if (e.asJsonArray.none { it == v }) return false }
        if (v.isJsonObject) {
            val o = v.asJsonObject
            s["required"]?.asJsonArray?.forEach { if (!o.has(it.asString)) return false }
            val props = s["properties"]?.asJsonObject
            if (s["additionalProperties"]?.asBoolean == false && props != null && o.keySet().any { !props.has(it) }) return false
            props?.entrySet()?.forEach { (k, ps) -> if (o.has(k) && !valide(o[k], ps.asJsonObject)) return false }
        }
        if (v.isJsonPrimitive && v.asJsonPrimitive.isString) {
            s["minLength"]?.let { if (v.asString.codePointCount(0, v.asString.length) < it.asInt) return false }
            s["pattern"]?.let { if (!Regex(it.asString).containsMatchIn(v.asString)) return false }
        }
        if (v.isJsonPrimitive && v.asJsonPrimitive.isNumber) {
            s["minimum"]?.let { if (v.asBigDecimal < it.asBigDecimal) return false }
        }
        return true
    }

    private fun aLeType(v: JsonElement, t: String): Boolean = when (t) {
        "object" -> v.isJsonObject
        "string" -> v.isJsonPrimitive && v.asJsonPrimitive.isString
        "integer" -> v.isJsonPrimitive && v.asJsonPrimitive.isNumber && v.asBigDecimal.stripTrailingZeros().scale() <= 0
        "null" -> v.isJsonNull
        else -> error("type de schéma non pris en charge : $t")
    }
}
