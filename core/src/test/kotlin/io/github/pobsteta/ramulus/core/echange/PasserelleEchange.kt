package io.github.pobsteta.ramulus.core.echange

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.jupiter.api.extension.ConditionEvaluationResult
import org.junit.jupiter.api.extension.ExecutionCondition
import org.junit.jupiter.api.extension.ExtensionContext
import java.io.File
import java.lang.reflect.InvocationTargetException
import java.util.zip.ZipFile

/**
 * LA COUTURE ENTRE LES TESTS D'ACCEPTATION (mission 003) ET LE MODULE GEOPACKAGE (mission 004).
 *
 * Le testeur n'a pas lu le code de 004, qui n'existe pas encore. Ces tests demandent donc une interface, écrite ici
 * comme ils l'attendent : un `object EchangeDeperis` dans le paquet `io.github.pobsteta.ramulus.core.echange`, dont
 * les fonctions sont statiques pour la JVM (`@JvmStatic`) et dont tout résultat structuré est un texte JSON. Elles
 * sont appelées par réflexion, pour que `:core:test` compile aujourd'hui sans 004.
 *
 * C'est une PROPOSITION, pas une décision : si 004 retient d'autres noms ou d'autres types, la seule chose à
 * retoucher est ce fichier (les neuf méthodes ci-dessous), jamais les tests ni les cas.
 *
 * ```
 * ouvrir(gpkg: File): String
 *     lecteur « ramulus » : ouvre une tournée de Nemeton sans réseau et la contrôle.
 *     -> {"verdict": "accepte"|"signale"|"refuse", "motifs": ["code", ...]}
 * instantane(fichier: File): String
 *     l'instantané JSON du format (docs/formats/deperis-v1.md, « Instantané ») d'un .gpkg ou d'un .zip rendu.
 * controlerRendu(zip: File, depart: File?): String
 *     lecteur « nemeton » : contrôle un paquet rendu par rapport à la tournée de départ, s’il y en a une (`null` : pas de comparaison, cas X2) ; même forme de résultat.
 * fusionner(zips: List<File>, depart: File, operateur: String, versionRamulus: String, sortie: File): String
 *     union sur l'uuid, contrôlée comme un paquet ; écrit le .zip de l'union dans `sortie`.
 *     -> {"verdict", "motifs", "ecartes": [{"objet": "notations"|"centres"|"photos", "cle": "<uuid ou nom>"}]}
 * enregistrerCentre(gpkg: File, centre: String)
 *     ajoute une ligne à `centres` (JSON = les colonnes sauf `fid`, geom = {"x","y"} en EPSG:2154).
 * enregistrerNotation(gpkg: File, notation: String): String
 *     ajoute une ligne à `notations` (tournée de première notation) ou à `contre_notations` (contre-notation),
 *     selon `tournee.nature` : jamais l'une pour l'autre. JSON = les colonnes sauf `fid` et `classe` : la classe
 *     ne se saisit pas, elle est calculée par l'abaque. Rend la ligne telle qu'enregistrée, classe comprise.
 * cloturerPlacette(gpkg: File, plotId: String)
 *     `placettes.etat` = « notee » ; rien d'autre n'est modifié dans `placettes`.
 * rendre(gpkg: File, depart: File, racinePhotos: File, operateur: String, versionRamulus: String, sortie: File): String
 *     contrôle la tournée (comme `controlerRendu`, plus les signalements), renseigne `tournee.operateur` et
 *     `tournee.version_ramulus`, et écrit `sortie` (.zip : deperis.gpkg + photos/) SAUF si le verdict est « refuse ».
 *     `racinePhotos` est un dossier qui contient `photos/<uuid>_<n>.jpg` ; les empreintes sont celles des fichiers.
 * ```
 *
 * Toute erreur d'écriture (valeur refusée) est une exception dont le message contient le code du motif du
 * catalogue (`note_hors_bornes`, `hors_france`…) ; le fichier est alors laissé tel qu'il était.
 */
object PasserelleEchange {
    const val CLASSE = "io.github.pobsteta.ramulus.core.echange.EchangeDeperis"

    val presente: Boolean = try {
        Class.forName(CLASSE)
        true
    } catch (_: ClassNotFoundException) {
        false
    }

    private fun appeler(nom: String, vararg args: Any?): Any? {
        check(presente) { "Mission 004 non livrée : la classe $CLASSE n'existe pas" }
        val classe = Class.forName(CLASSE)
        val methode = classe.methods.firstOrNull { it.name == nom && it.parameterCount == args.size }
            ?: throw AssertionError("$CLASSE.$nom(${args.size} paramètres) manque : voir la couture, PasserelleEchange.kt")
        val cible = if (java.lang.reflect.Modifier.isStatic(methode.modifiers)) null else classe.getField("INSTANCE").get(null)
        try {
            return methode.invoke(cible, *args)
        } catch (e: InvocationTargetException) {
            throw e.targetException
        }
    }

    private fun json(texte: Any?): JsonObject = JsonParser.parseString(texte as String).asJsonObject

    fun ouvrir(gpkg: File): Verdict = Verdict.de(json(appeler("ouvrir", gpkg)))

    fun instantane(fichier: File): JsonObject = json(appeler("instantane", fichier))

    fun controlerRendu(zip: File, depart: File?): Verdict = Verdict.de(json(appeler("controlerRendu", zip, depart)))

    fun fusionner(zips: List<File>, depart: File, operateur: String, versionRamulus: String, sortie: File): Verdict =
        Verdict.de(json(appeler("fusionner", zips, depart, operateur, versionRamulus, sortie)))

    fun enregistrerCentre(gpkg: File, centre: JsonObject) {
        appeler("enregistrerCentre", gpkg, centre.toString())
    }

    fun enregistrerNotation(gpkg: File, notation: JsonObject): JsonObject =
        json(appeler("enregistrerNotation", gpkg, notation.toString()))

    fun cloturerPlacette(gpkg: File, plotId: String) {
        appeler("cloturerPlacette", gpkg, plotId)
    }

    fun rendre(gpkg: File, depart: File, racinePhotos: File, operateur: String, versionRamulus: String, sortie: File): Verdict =
        Verdict.de(json(appeler("rendre", gpkg, depart, racinePhotos, operateur, versionRamulus, sortie)))
}

/** Ce que conclut un lecteur : le statut et les motifs du catalogue, sans autre commentaire. */
data class Verdict(val statut: String, val motifs: Set<String>, val ecartes: Set<Pair<String, String>>) {
    companion object {
        fun de(j: JsonObject): Verdict = Verdict(
            j["verdict"].asString,
            j.getAsJsonArray("motifs").map { it.asString }.toSet(),
            (j["ecartes"]?.takeUnless { it.isJsonNull }?.asJsonArray ?: JsonArray())
                .map { it.asJsonObject["objet"].asString to it.asJsonObject["cle"].asString }.toSet(),
        )
    }
}

/**
 * Active les tests tant que la classe de 004 existe. Tant qu'elle n'existe pas, ils sont désactivés avec renvoi à
 * 004 (fiche 003 : « rouge justifiée »), sauf si `RAMULUS_ECHANGE_EXIGER=1` (ou `-Dramulus.echange.exiger=true`) :
 * alors ils tournent et échouent, ce qui montre qu'ils ne passent pas à vide.
 * Réserve : si 004 nomme sa classe autrement, les tests restent désactivés sans bruit ; le test
 * `CasCommunsLusTest` n'y voit rien. D'où la question posée à Pascal et à 004 dans le rendu de 003.
 */
class ActiveeParLaMission004 : ExecutionCondition {
    override fun evaluateExecutionCondition(context: ExtensionContext): ConditionEvaluationResult {
        val exiger = System.getenv("RAMULUS_ECHANGE_EXIGER") == "1" || System.getProperty("ramulus.echange.exiger") == "true"
        return if (PasserelleEchange.presente || exiger) {
            ConditionEvaluationResult.enabled("004 livrée, ou rouge exigée")
        } else {
            ConditionEvaluationResult.disabled("Désactivé jusqu'à la mission 004 (${PasserelleEchange.CLASSE} absente)")
        }
    }
}

/** Un cas commun : son dossier et son `attendu.json`. */
class Cas(val dossier: File) {
    val attendu: JsonObject = JsonParser.parseString(File(dossier, "attendu.json").readText()).asJsonObject
    val id: String = attendu["cas"].asString
    val titre: String = attendu["titre"].asString
    val lecteur: String = attendu["lecteur"].asString
    val statut: String = attendu["attendu"].asString
    val motifs: Set<String> = attendu.getAsJsonArray("motifs").map { it.asString }.toSet()
    val fichiers: List<File> = attendu.getAsJsonArray("fichiers").map { File(dossier, it.asString) }
    val depart: File? = attendu["depart"].takeUnless { it.isJsonNull }?.let { File(dossier, it.asString).canonicalFile }
    val fusion: JsonObject? = attendu["fusion"].takeUnless { it.isJsonNull }?.asJsonObject
    val nom: String get() = "$id : $titre"

    fun verdictAttendu() = Verdict(statut, motifs, emptySet())

    companion object {
        private val RACINES = listOf("../fixtures/echange", "../fixtures/echange-complements")

        fun tous(): List<Cas> = RACINES.flatMap { racine ->
            (File(racine).listFiles() ?: emptyArray()).filter { File(it, "attendu.json").isFile }
        }.sortedBy { it.name }.map { Cas(it) }

        /** L'instantané déjà écrit à côté d'un fichier du cas (`x.gpkg` ou `x.zip` -> `x.instantane.json`). */
        fun instantaneAttendu(fichier: File): JsonObject =
            JsonParser.parseString(File(fichier.parentFile, fichier.nameWithoutExtension + ".instantane.json").readText()).asJsonObject
    }
}

/** Décompresse les photos d'un paquet dans `racine/photos/`, comme l'appareil les garde. */
fun extrairePhotos(zip: File, racine: File) {
    ZipFile(zip).use { z ->
        for (e in z.entries().asSequence().filter { it.name.startsWith("photos/") && !it.isDirectory }) {
            val cible = File(racine, e.name)
            cible.parentFile.mkdirs()
            z.getInputStream(e).use { entree -> cible.outputStream().use { entree.copyTo(it) } }
        }
    }
}

/** Premier écart entre deux instantanés (ou toute valeur JSON), dit par son chemin ; `null` s'ils sont égaux. */
fun premiereDifference(attendu: JsonElement?, obtenu: JsonElement?, chemin: String = "instantané"): String? {
    if (attendu == null || attendu.isJsonNull) return if (obtenu == null || obtenu.isJsonNull) null else "$chemin : attendu null, obtenu $obtenu"
    if (obtenu == null || obtenu.isJsonNull) return "$chemin : attendu $attendu, obtenu null"
    return when {
        attendu.isJsonObject && obtenu.isJsonObject -> {
            val a = attendu.asJsonObject
            val o = obtenu.asJsonObject
            if (a.keySet() != o.keySet()) {
                "$chemin : clés attendues ${a.keySet()}, obtenues ${o.keySet()}"
            } else {
                a.keySet().firstNotNullOfOrNull { premiereDifference(a[it], o[it], "$chemin.$it") }
            }
        }
        attendu.isJsonArray && obtenu.isJsonArray -> {
            val a = attendu.asJsonArray
            val o = obtenu.asJsonArray
            if (a.size() != o.size()) {
                "$chemin : ${a.size()} éléments attendus, ${o.size()} obtenus"
            } else {
                (0 until a.size()).firstNotNullOfOrNull { premiereDifference(a[it], o[it], "$chemin[$it]") }
            }
        }
        attendu.isJsonPrimitive && obtenu.isJsonPrimitive -> {
            val pa = attendu.asJsonPrimitive
            val po = obtenu.asJsonPrimitive
            val egal = if (pa.isNumber && po.isNumber) pa.asBigDecimal.compareTo(po.asBigDecimal) == 0 else pa == po
            if (egal) null else "$chemin : attendu $pa, obtenu $po"
        }
        else -> "$chemin : attendu $attendu, obtenu $obtenu"
    }
}
