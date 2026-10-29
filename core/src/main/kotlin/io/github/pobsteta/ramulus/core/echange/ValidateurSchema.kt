package io.github.pobsteta.ramulus.core.echange

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive

/** Un écart au schéma : le motif du catalogue (`x-motif` du sous-schéma, sinon `structure`) et sa description. */
internal class ErreurSchema(val motif: String, val message: String)

/**
 * Validateur de JSON Schema pour le seul sous-ensemble que `docs/formats/deperis-v1.schema.json` emploie
 * (`$ref` local, type, const, enum, minimum, maximum, minLength, pattern, required, properties,
 * additionalProperties, propertyNames, items, minItems, maxItems, allOf, anyOf, not, if/then, schéma booléen).
 *
 * Le schéma reste la source unique des règles ET des motifs (ADR 0003 § 7) : il est lu tel quel, sans copie. Comme
 * `jsonschema` (Python) dans le contrôle de référence, le motif d'un écart est celui du sous-schéma qui porte le
 * mot-clé en défaut ; pour `anyOf`, `not` et les mots-clés simples, c'est le schéma qui les contient, sans
 * héritage du parent. Un mot-clé hors de ce sous-ensemble fait échouer le chargement plutôt que d'être ignoré.
 */
internal class ValidateurSchema(private val racine: JsonObject) {
    private val motifs = HashMap<String, Regex>()

    init {
        controlerMotsCles(racine)
    }

    fun valider(instance: JsonElement): List<ErreurSchema> {
        val sortie = ArrayList<ErreurSchema>()
        verifier(racine, instance, "", sortie)
        return sortie
    }

    private fun controlerMotsCles(e: JsonElement, estSchema: Boolean = true) {
        // On ne descend que dans les sous-schémas ; les valeurs de const, enum et x-motifs sont des données.
        if (!estSchema || e !is JsonObject) return
        for ((cle, v) in e.entrySet()) {
            require(cle in CONNUS) { "Mot-clé de schéma non géré : $cle" }
            when (cle) {
                "properties", "\$defs" -> (v as JsonObject).entrySet().forEach { controlerMotsCles(it.value) }
                "allOf", "anyOf" -> (v as JsonArray).forEach { controlerMotsCles(it) }
                "items", "additionalProperties", "propertyNames", "not", "if", "then", "else" -> controlerMotsCles(v)
            }
        }
    }

    private fun resoudre(ref: String): JsonElement {
        require(ref.startsWith("#/")) { "\$ref non local : $ref" }
        var courant: JsonElement = racine
        for (segment in ref.removePrefix("#/").split('/')) {
            courant = (courant as JsonObject)[segment] ?: error("\$ref introuvable : $ref")
        }
        return courant
    }

    private fun valide(schema: JsonElement, inst: JsonElement): Boolean {
        val sortie = ArrayList<ErreurSchema>()
        verifier(schema, inst, "", sortie)
        return sortie.isEmpty()
    }

    private fun verifier(schema: JsonElement, inst: JsonElement, chemin: String, sortie: MutableList<ErreurSchema>) {
        if (schema is JsonPrimitive) {
            if (!schema.asBoolean) sortie += ErreurSchema("structure", "${ou(chemin)} : aucune valeur n'est permise ici")
            return
        }
        val s = schema.asJsonObject
        val motif = JsonOutils.texte(s["x-motif"]) ?: "structure"
        fun ecart(message: String) {
            sortie += ErreurSchema(motif, "${ou(chemin)} : $message")
        }

        s["\$ref"]?.let { verifier(resoudre(it.asString), inst, chemin, sortie) }

        s["type"]?.let { t ->
            val types = if (t.isJsonArray) t.asJsonArray.map { it.asString } else listOf(t.asString)
            if (types.none { type(it, inst) }) ecart("type $types attendu, valeur ${resume(inst)}")
        }
        s["const"]?.let { if (!JsonOutils.egal(it, inst)) ecart("valeur ${resume(it)} attendue, obtenue ${resume(inst)}") }
        s["enum"]?.let { e ->
            if (e.asJsonArray.none { JsonOutils.egal(it, inst) }) ecart("valeur ${resume(inst)} hors de la liste ${resume(e)}")
        }
        if (JsonOutils.estNombre(inst)) {
            s["minimum"]?.let { if (JsonOutils.nombre(inst) < it.asBigDecimal) ecart("${resume(inst)} < minimum ${it.asString}") }
            s["maximum"]?.let { if (JsonOutils.nombre(inst) > it.asBigDecimal) ecart("${resume(inst)} > maximum ${it.asString}") }
        }
        if (JsonOutils.estTexte(inst)) {
            val texte = inst.asString
            s["minLength"]?.let { if (texte.codePointCount(0, texte.length) < it.asInt) ecart("texte plus court que ${it.asInt}") }
            s["pattern"]?.let { p ->
                if (!motifs.getOrPut(p.asString) { Regex(p.asString) }.containsMatchIn(texte)) {
                    ecart("${resume(inst)} ne correspond pas à ${p.asString}")
                }
            }
        }
        if (inst is JsonObject) {
            s["required"]?.asJsonArray?.forEach { r ->
                if (!inst.has(r.asString)) ecart("clé ${r.asString} absente")
            }
            val proprietes = s["properties"]?.asJsonObject
            proprietes?.entrySet()?.forEach { (nom, sous) ->
                inst[nom]?.let { verifier(sous, it, "$chemin/$nom", sortie) }
            }
            s["additionalProperties"]?.let { autres ->
                for ((nom, v) in inst.entrySet()) {
                    if (proprietes == null || !proprietes.has(nom)) verifier(autres, v, "$chemin/$nom", sortie)
                }
            }
            s["propertyNames"]?.let { sous ->
                for (nom in inst.keySet()) verifier(sous, JsonPrimitive(nom), "$chemin/<nom $nom>", sortie)
            }
        }
        if (inst is JsonArray) {
            s["minItems"]?.let { if (inst.size() < it.asInt) ecart("${inst.size()} éléments, ${it.asInt} au moins") }
            s["maxItems"]?.let { if (inst.size() > it.asInt) ecart("${inst.size()} éléments, ${it.asInt} au plus") }
            s["items"]?.let { sous -> inst.forEachIndexed { i, v -> verifier(sous, v, "$chemin/$i", sortie) } }
        }
        s["allOf"]?.asJsonArray?.forEach { verifier(it, inst, chemin, sortie) }
        s["anyOf"]?.asJsonArray?.let { variantes ->
            if (variantes.none { valide(it, inst) }) ecart("aucune des variantes ne convient à ${resume(inst)}")
        }
        s["not"]?.let { if (valide(it, inst)) ecart("la valeur ${resume(inst)} est interdite ici") }
        s["if"]?.let { condition ->
            if (valide(condition, inst)) s["then"]?.let { verifier(it, inst, chemin, sortie) }
            else s["else"]?.let { verifier(it, inst, chemin, sortie) }
        }
    }

    private fun type(nom: String, v: JsonElement): Boolean = when (nom) {
        "null" -> v.isJsonNull
        "string" -> JsonOutils.estTexte(v)
        "integer" -> JsonOutils.estEntier(v)
        "number" -> JsonOutils.estNombre(v)
        "object" -> v.isJsonObject
        "array" -> v.isJsonArray
        "boolean" -> v is JsonPrimitive && v.isBoolean
        else -> error("Type de schéma inconnu : $nom")
    }

    private fun ou(chemin: String) = chemin.ifEmpty { "/" }

    private fun resume(v: JsonElement): String = v.toString().let { if (it.length > 120) it.take(120) + "…" else it }

    private companion object {
        val CONNUS = setOf(
            "\$schema", "\$id", "\$ref", "\$defs", "title", "description", "x-motif", "x-motifs",
            "type", "const", "enum", "minimum", "maximum", "minLength", "pattern", "required", "properties",
            "additionalProperties", "propertyNames", "items", "minItems", "maxItems", "allOf", "anyOf", "not",
            "if", "then", "else",
        )
    }
}
