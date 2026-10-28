package io.github.pobsteta.ramulus.core.deperis

import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Les notes imprimées sous les 250 photos des fiches DSF 2025 (fixtures/fiches-dsf-2025.json) suivent l'abaque,
 * sauf quatre incohérences de la fiche elle-même, listées dans le fichier et signalées au DSF.
 */
class FichesDsfTest {

    private val racine = JsonParser.parseString(File("../fixtures/fiches-dsf-2025.json").readText()).asJsonObject
    private val photos = racine.getAsJsonArray("photos").map { it.asJsonObject }
    private val incoherences = racine.getAsJsonArray("incoherences_connues").map { it.asString }.toSet()

    @Test
    fun `250 photos, essences toutes au referentiel`() {
        assertEquals(250, photos.size)
        for (p in photos) assertNotNull(Essences.parNom(p["essence"].asString), p["essence"].asString)
    }

    @Test
    fun `l abaque redonne la classe imprimee, hors incoherences connues`() {
        val ecarts = photos.filter { !it["mb"].isJsonNull && !it["second"].isJsonNull }
            .filter { Abaque.classe(it["mb"].asInt, it["second"].asInt) != Classe.lire(it["classe"].asString) }
            .map { it["id"].asString }
            .toSet()
        assertEquals(incoherences, ecarts)
    }
}
