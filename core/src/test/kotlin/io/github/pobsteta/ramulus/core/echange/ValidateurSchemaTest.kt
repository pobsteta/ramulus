package io.github.pobsteta.ramulus.core.echange

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Tests propres de la mission 004 : le validateur de JSON Schema, qui rend le motif du sous-schéma en défaut. */
class ValidateurSchemaTest {

    private fun motifs(schema: String, instance: String): List<String> =
        ValidateurSchema(JsonParser.parseString(schema).asJsonObject).valider(JsonParser.parseString(instance)).map { it.motif }

    @Test
    fun `le motif est celui du sous-schema en defaut, structure sinon`() {
        val schema = """{"properties":{"mb":{"type":"integer","minimum":0,"maximum":5,"x-motif":"note_hors_bornes"},"nom":{"type":"string"}}}"""
        assertEquals(emptyList<String>(), motifs(schema, """{"mb":3,"nom":"x"}"""))
        assertEquals(listOf("note_hors_bornes"), motifs(schema, """{"mb":6}"""))
        assertEquals(listOf("note_hors_bornes"), motifs(schema, """{"mb":-1}"""))
        assertEquals(listOf("structure"), motifs(schema, """{"nom":3}"""))
    }

    @Test
    fun `un entier peut s ecrire 3 ou 3 0, un booleen n est pas un entier, 1 n est pas true`() {
        val schema = """{"properties":{"n":{"type":"integer"},"b":{"const":true}}}"""
        assertEquals(emptyList<String>(), motifs(schema, """{"n":3.0}"""))
        assertEquals(listOf("structure"), motifs(schema, """{"n":3.5}"""))
        assertEquals(listOf("structure"), motifs(schema, """{"n":true}"""))
        assertEquals(listOf("structure"), motifs(schema, """{"b":1}"""))
    }

    @Test
    fun `anyOf et not donnent le motif du schema qui les contient, if then descend dans then`() {
        val schema = """{"allOf":[
            {"x-motif":"ia_incomplete","anyOf":[{"properties":{"a":{"type":"null"},"b":{"type":"null"}}},{"properties":{"a":{"type":"integer"},"b":{"type":"integer"}}}]},
            {"if":{"required":["k"]},"then":{"properties":{"k":{"x-motif":"type_incoherent","enum":["X"]}}}},
            {"properties":{"nom":{"x-motif":"colonne_date","not":{"pattern":"^[Dd][Aa][Tt][Ee]$"}}}}
        ]}"""
        assertEquals(emptyList<String>(), motifs(schema, """{"a":null,"b":null,"k":"X","nom":"jour"}"""))
        assertEquals(listOf("ia_incomplete"), motifs(schema, """{"a":1,"b":null}"""))
        assertEquals(listOf("type_incoherent"), motifs(schema, """{"a":null,"b":null,"k":"Y"}"""))
        assertEquals(listOf("colonne_date"), motifs(schema, """{"a":null,"b":null,"nom":"DATE"}"""))
    }

    @Test
    fun `required, additionalProperties, propertyNames, items et ref local`() {
        val schema = """{"${'$'}defs":{"o":{"type":"object","required":["id"]}},
            "properties":{"liste":{"type":"array","minItems":1,"items":{"${'$'}ref":"#/${'$'}defs/o"}},
                          "tables":{"propertyNames":{"x-motif":"colonne_date","not":{"enum":["date"]}},"additionalProperties":{"type":"integer"}}}}"""
        assertEquals(emptyList<String>(), motifs(schema, """{"liste":[{"id":1}],"tables":{"a":1}}"""))
        assertEquals(listOf("structure"), motifs(schema, """{"liste":[{}]}"""))
        assertEquals(listOf("structure"), motifs(schema, """{"liste":[]}"""))
        assertEquals(listOf("structure"), motifs(schema, """{"tables":{"a":"x"}}"""))
        assertEquals(listOf("colonne_date"), motifs(schema, """{"tables":{"date":1}}"""))
    }

    @Test
    fun `un mot-cle que le validateur ne gere pas fait echouer le chargement, il n est jamais ignore`() {
        assertThrows(IllegalArgumentException::class.java) { motifs("""{"oneOf":[{"type":"string"}]}""", "\"x\"") }
    }

    @Test
    fun `le schema du format se charge avec ses 25 motifs, tous de statut refuse ou signale`() {
        val catalogue: Map<String, String> = Controle.catalogue
        assertEquals(25, catalogue.size)
        assertTrue(catalogue.values.all { it == "refuse" || it == "signale" })
        assertEquals(setOf("doublon_arbre", "centre_eloigne", "uuid_contenu_different"), catalogue.filterValues { it == "signale" }.keys)
        val racine: JsonObject = Controle.schema
        assertTrue(racine.has("\$defs"))
    }
}
