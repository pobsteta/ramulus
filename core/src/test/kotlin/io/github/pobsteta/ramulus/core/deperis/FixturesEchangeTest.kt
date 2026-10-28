package io.github.pobsteta.ramulus.core.deperis

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Cas communs du format deperis-v1 (fixtures/echange/, docs/formats/deperis-v1.md) rejoués par le domaine de `:core` :
 * sur les règles qu'il porte (essence, type, critère, bornes des notes, abaque), il conclut comme `attendu.json`.
 * La lecture du GeoPackage lui-même viendra avec la mission 004 ; ici, on lit les notes recopiées dans `attendu.json`.
 */
class FixturesEchangeTest {

    private val cas: List<JsonObject> = File("../fixtures/echange").listFiles()!!
        .map { File(it, "attendu.json") }
        .filter { it.isFile }
        .sortedBy { it.parentFile.name }
        .map { JsonParser.parseString(it.readText()).asJsonObject }

    private val fiches = JsonParser.parseString(File("../fixtures/fiches-dsf-2025.json").readText()).asJsonObject
    private val photosDsf = fiches.getAsJsonArray("photos").map { it.asJsonObject }.associateBy { it["id"].asString }
    private val incoherences = fiches.getAsJsonArray("incoherences_connues").map { it.asString }.toSet()

    private fun JsonObject.texte(cle: String): String = this[cle].asString

    private fun JsonObject.notes(): List<JsonObject> = getAsJsonArray("notes").map { it.asJsonObject }

    /** Ce que le domaine reproche à une note, avec les codes du catalogue du format. */
    private fun motifs(n: JsonObject): Set<String> {
        val motifs = mutableSetOf<String>()
        // Au caractère près (accents compris) : `Essences.parNom` tolère la casse, le format non.
        val essence = Essences.toutes.firstOrNull { it.nom == n.texte("essence") }
        if (essence == null) motifs += "essence_hors_referentiel"
        val type = TypeEssence.entries.firstOrNull { it.name == n.texte("type") }
        if (type == null || (essence != null && essence.type != type)) motifs += "type_incoherent"
        if (type != null && n.texte("critere") != type.secondCritere.name) motifs += "critere_incoherent"
        val mb = n["mb"].asInt
        val second = n["second"].asInt
        if (mb !in 0..5 || second !in 0..5) {
            motifs += "note_hors_bornes"
        } else if (Classe.entries.firstOrNull { it.name == n.texte("classe") } != Abaque.classe(mb, second)) {
            motifs += "classe_differente_abaque"
        }
        return motifs
    }

    @Test
    fun `les cas communs sont la`() {
        assertTrue(cas.size >= 26, "${cas.size} cas lus")
        assertTrue(cas.any { it.texte("attendu") == "accepte" && it.notes().isNotEmpty() })
    }

    @Test
    fun `le domaine conclut comme attendu json sur les regles qu il porte`() {
        for (c in cas) {
            val attendus = c.getAsJsonArray("motifs").map { it.asString }.toSet() intersect MOTIFS_DU_DOMAINE
            val trouves = c.notes().flatMap { motifs(it) }.toSet()
            assertEquals(attendus, trouves, c.texte("cas"))
        }
    }

    @Test
    fun `chaque note acceptee est celle d une photo des fiches DSF, hors incoherences`() {
        for (c in cas.filter { it.texte("attendu") != "refuse" }) {
            for (n in c.notes()) {
                val brut = n["photo_dsf"].takeUnless { it.isJsonNull }?.asString
                assertNotNull(brut, "${c.texte("cas")} ${n.texte("uuid")}")
                val id = brut!!
                assertFalse(id in incoherences, id)
                val p = photosDsf.getValue(id)
                assertEquals(p.texte("essence"), n.texte("essence"), id)
                assertEquals(p["mb"].asInt, n["mb"].asInt, id)
                assertEquals(p["second"].asInt, n["second"].asInt, id)
                assertEquals(Classe.lire(p.texte("classe")), Abaque.classe(n["mb"].asInt, n["second"].asInt), id)
                assertEquals(Essences.parNom(n.texte("essence"))!!.type.secondCritere.name, n.texte("critere"), id)
            }
        }
    }

    private companion object {
        val MOTIFS_DU_DOMAINE = setOf(
            "essence_hors_referentiel", "type_incoherent", "critere_incoherent", "note_hors_bornes",
            "classe_differente_abaque",
        )
    }
}
