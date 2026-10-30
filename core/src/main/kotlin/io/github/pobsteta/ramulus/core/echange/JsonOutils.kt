package io.github.pobsteta.ramulus.core.echange

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import java.io.StringReader
import java.math.BigDecimal
import java.text.Normalizer

/** Petits outils sur l'arbre JSON (Gson), partagés par la lecture, le contrôle, la fusion et l'écriture. */
internal object JsonOutils {
    /** Gson sans échappement HTML (les accents sont écrits tels quels) et qui garde les null (l'instantané les porte). */
    val gson: Gson = GsonBuilder().disableHtmlEscaping().serializeNulls().create()

    fun estNul(e: JsonElement?): Boolean = e == null || e.isJsonNull

    fun estTexte(e: JsonElement?): Boolean = e is JsonPrimitive && e.isString

    fun estNombre(e: JsonElement?): Boolean = e is JsonPrimitive && e.isNumber

    /** Entier au sens de JSON Schema : un nombre sans partie fractionnaire (3 et 3.0 le sont). */
    fun estEntier(e: JsonElement?): Boolean {
        if (e !is JsonPrimitive || !e.isNumber) return false
        return try {
            e.asBigDecimal.stripTrailingZeros().scale() <= 0
        } catch (_: NumberFormatException) {
            false
        }
    }

    fun nombre(e: JsonElement): BigDecimal = e.asBigDecimal

    /** Égalité de deux valeurs JSON : les nombres se comparent par leur valeur, un booléen n'est pas un entier. */
    fun egal(a: JsonElement?, b: JsonElement?): Boolean {
        if (a == null || a.isJsonNull || b == null || b.isJsonNull) return estNul(a) && estNul(b)
        return when {
            a is JsonPrimitive && b is JsonPrimitive -> when {
                a.isNumber && b.isNumber -> a.asBigDecimal.compareTo(b.asBigDecimal) == 0
                a.isBoolean && b.isBoolean -> a.asBoolean == b.asBoolean
                a.isString && b.isString -> a.asString == b.asString
                else -> false
            }
            a is JsonObject && b is JsonObject ->
                a.keySet() == b.keySet() && a.keySet().all { egal(a[it], b[it]) }
            a is JsonArray && b is JsonArray ->
                a.size() == b.size() && (0 until a.size()).all { egal(a[it], b[it]) }
            else -> false
        }
    }

    /** Lecture stricte d'un texte JSON (ni commentaire ni guillemet simple) ; `null` si ce n'en est pas. */
    fun lireStrict(texte: String): JsonElement? = try {
        val lecteur = JsonReader(StringReader(texte))
        lecteur.isLenient = false
        val e = gson.getAdapter(JsonElement::class.java).read(lecteur)
        if (lecteur.peek() == JsonToken.END_DOCUMENT) e else null
    } catch (_: Exception) {
        null
    }

    fun nfc(texte: String): String = Normalizer.normalize(texte, Normalizer.Form.NFC)

    fun nonNfc(texte: String): Boolean = !Normalizer.isNormalized(texte, Normalizer.Form.NFC)

    /** Copie de `e` où tout texte (valeurs, y compris dans les tableaux et objets imbriqués) est en forme NFC. */
    fun enNfc(e: JsonElement?): JsonElement = when {
        e == null || e.isJsonNull -> JsonNull.INSTANCE
        e is JsonPrimitive && e.isString -> JsonPrimitive(nfc(e.asString))
        e is JsonArray -> JsonArray().also { a -> e.forEach { a.add(enNfc(it)) } }
        e is JsonObject -> JsonObject().also { o -> e.entrySet().forEach { (k, v) -> o.add(k, enNfc(v)) } }
        else -> e
    }

    fun texte(e: JsonElement?): String? = if (estTexte(e)) e!!.asString else null
}
