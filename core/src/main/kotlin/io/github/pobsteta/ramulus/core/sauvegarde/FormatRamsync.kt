package io.github.pobsteta.ramulus.core.sauvegarde

import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.github.pobsteta.ramulus.core.journal.LigneJournal
import io.github.pobsteta.ramulus.core.journal.OrdreTotal
import io.github.pobsteta.ramulus.core.journal.TypeLigne
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * Format `.ramsync` (docs/formats/ramsync-v1.md ; ADR 0005), repris de `sursync-v1` de Surculus (FormatSursync.kt,
 * commit 3c01f4b) : JSON Lines UTF-8 compressé en gzip. Ligne 1 : en-tête ; lignes suivantes : une saisie du journal,
 * dans l'ordre total ; dernière ligne : `{"sha256": ...}`, empreinte SHA-256 (hexadécimal minuscule) des octets
 * décompressés de toutes les lignes précédentes, fins de ligne comprises.
 */
object FormatRamsync {
    const val FORMAT = "ramsync"

    /** Version écrite par cette application. */
    const val VERSION_COURANTE = 1

    const val EXTENSION = ".ramsync"

    fun sha256Hex(octets: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(octets).joinToString("") { "%02x".format(it) }
}

/**
 * En-tête du fichier. Instants en millisecondes Unix, UTC. `versionNemeton` : version de Nemeton qui a préparé la
 * tournée (`tournee.version_nemeton` de deperis-v1), ou `null` si elle n'est pas connue ; `versionRamulus` : version
 * de l'application qui écrit (plan 005, Q8).
 */
data class EnTeteRamsync(
    val version: Int,
    val codeTournee: String,
    val versionNemeton: String?,
    val versionRamulus: String,
    val appareil: String,
    val creeLeUtcMs: Long,
    val nbLignes: Int,
)

object EcritureRamsync {
    /**
     * Écrit le `.ramsync` d'**une** tournée : toutes les lignes doivent être de la tournée `codeTournee` ; elles sont
     * écrites dans l'ordre total, quel que soit l'ordre reçu. Le gzip est reproductible (date gzip nulle).
     */
    fun ecrire(
        codeTournee: String,
        versionNemeton: String?,
        versionRamulus: String,
        appareil: String,
        creeLeUtcMs: Long,
        lignes: Collection<LigneJournal>,
        version: Int = FormatRamsync.VERSION_COURANTE,
    ): ByteArray {
        require(codeTournee.isNotBlank()) { "code de tournée vide" }
        require(lignes.all { it.codeTournee == codeTournee }) { "une sauvegarde ne porte qu'une tournée" }
        return compresser(contenu(
            EnTeteRamsync(version, codeTournee, versionNemeton, versionRamulus, appareil, creeLeUtcMs, lignes.size),
            OrdreTotal.trier(lignes),
        ))
    }

    /** Contenu décompressé : en-tête, saisies telles que données, pied. */
    internal fun contenu(entete: EnTeteRamsync, lignes: List<LigneJournal>): ByteArray {
        val contenu = ByteArrayOutputStream()
        fun ajouter(o: JsonObject) = contenu.write((o.toString() + "\n").toByteArray(Charsets.UTF_8))
        ajouter(JsonObject().apply {
            addProperty("format", FormatRamsync.FORMAT)
            addProperty("version", entete.version)
            addProperty("code_tournee", entete.codeTournee)
            texteOuNull("version_nemeton", entete.versionNemeton)
            addProperty("version_ramulus", entete.versionRamulus)
            addProperty("appareil", entete.appareil)
            addProperty("cree_le_utc_ms", entete.creeLeUtcMs)
            addProperty("nb_lignes", entete.nbLignes)
        })
        lignes.forEach { ajouter(versJson(it)) }
        val empreinte = FormatRamsync.sha256Hex(contenu.toByteArray())
        ajouter(JsonObject().apply { addProperty("sha256", empreinte) })
        return contenu.toByteArray()
    }

    fun compresser(octets: ByteArray): ByteArray {
        val sortie = ByteArrayOutputStream()
        GZIPOutputStream(sortie).use { it.write(octets) }
        return sortie.toByteArray()
    }

    private fun JsonObject.texteOuNull(cle: String, v: String?) =
        if (v == null) add(cle, JsonNull.INSTANCE) else addProperty(cle, v)

    private fun JsonObject.entierOuNull(cle: String, v: Long?) =
        if (v == null) add(cle, JsonNull.INSTANCE) else addProperty(cle, v)

    /**
     * `valeur` est écrite en **texte** (la chaîne JSON de la ligne) : l'aller-retour est exact au caractère.
     * `code_tournee` n'est pas écrit : la ligne prend celui de l'en-tête.
     */
    internal fun versJson(l: LigneJournal) = JsonObject().apply {
        addProperty("uuid", l.uuid)
        addProperty("objet_uuid", l.objetUuid)
        addProperty("type", l.type)
        texteOuNull("valeur", l.valeur)
        texteOuNull("remplace_uuid", l.remplaceUuid)
        addProperty("operateur", l.operateur)
        addProperty("appareil", l.appareil)
        addProperty("sequence", l.sequence)
        addProperty("saisi_le_utc_ms", l.saisiLeUtcMs)
        entierOuNull("heure_gnss_ms", l.heureGnssMs)
        addProperty("monotone_ms", l.monotoneMs)
        addProperty("demarrage", l.demarrage)
        entierOuNull("age_fix_ms", l.ageFixMs)
    }
}

/** Motifs de refus du fichier en entier. */
enum class MotifRefus(val code: String) {
    VERSION_FUTURE("version_future"),
    VERSION_ILLISIBLE("version_illisible"),
}

/** Défauts d'un fichier lu quand même (ils se cumulent). */
enum class Signalement(val code: String) {
    FICHIER_TRONQUE("fichier_tronque"),
    LIGNE_ILLISIBLE("ligne_illisible"),
    EMPREINTE_DIFFERENTE("empreinte_differente"),
    UUID_CONTENU_DIFFERENT("uuid_contenu_different"),

    /** Ligne dont `code_tournee` n'est pas celui de l'en-tête : refusée, jamais rattachée à la tournée du fichier. */
    LIGNE_AUTRE_TOURNEE("ligne_autre_tournee"),
}

/**
 * Résultat de la lecture. Refusé en entier : `motif` non nul et aucune ligne. Sinon les lignes saines sont dans
 * l'ordre du fichier, les fautives comptées dans `lignesRefusees` (avec leur uuid quand il se lit).
 */
data class ResultatLecture(
    val motif: MotifRefus?,
    val entete: EnTeteRamsync?,
    val lignes: List<LigneJournal>,
    val signalements: Set<Signalement>,
    val lignesRefusees: Int,
    val uuidRefuses: List<String>,
    val lignesIgnorees: Int,
) {
    val accepte: Boolean get() = motif == null
}

object LectureRamsync {
    /**
     * Lit un `.ramsync`. `versionCourante` : version du lecteur ; sont lues `[courante-1, courante]` (et au moins 1) ;
     * une version plus haute est refusée (`version_future`) ; une version absente, illisible ou trop ancienne l'est
     * aussi (`version_illisible`), de même qu'un fichier de 0 octet ou non gzip, un en-tête illisible ou sans code de
     * tournée. Sinon le fichier n'est jamais refusé en entier : les lignes saines sont gardées, les défauts signalés.
     */
    fun lire(octets: ByteArray, versionCourante: Int = FormatRamsync.VERSION_COURANTE): ResultatLecture {
        val decompresse = decompresser(octets) ?: return refus(MotifRefus.VERSION_ILLISIBLE)
        val signalements = linkedSetOf<Signalement>()
        if (decompresse.tronque) signalements += Signalement.FICHIER_TRONQUE

        // Découpage en lignes terminées par « \n » ; un reste sans fin de ligne est une ligne coupée, ignorée.
        val brut = decompresse.octets
        val lignesBrutes = mutableListOf<IntRange>() // début..fin exclue, sans \n
        var debut = 0
        for (i in brut.indices) if (brut[i] == '\n'.code.toByte()) { lignesBrutes += debut until i; debut = i + 1 }

        fun json(r: IntRange): JsonObject? = try {
            JsonParser.parseString(String(brut, r.first, r.last + 1 - r.first, Charsets.UTF_8))
                .takeIf { it.isJsonObject }?.asJsonObject
        } catch (e: RuntimeException) { null }

        if (lignesBrutes.isEmpty()) return refus(MotifRefus.VERSION_ILLISIBLE)
        val jEntete = json(lignesBrutes[0]) ?: return refus(MotifRefus.VERSION_ILLISIBLE)
        val version = try {
            jEntete.get("version")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asBigDecimal
        } catch (e: RuntimeException) { null }
        if (version == null || version.stripTrailingZeros().scale() > 0 || version.signum() < 1) {
            return refus(MotifRefus.VERSION_ILLISIBLE)
        }
        val v = version.toBigInteger()
        if (v > versionCourante.toBigInteger()) return refus(MotifRefus.VERSION_FUTURE)
        if (v < maxOf(1, versionCourante - 1).toBigInteger()) return refus(MotifRefus.VERSION_ILLISIBLE)

        val entete = try {
            require(jEntete.get("format").asString == FormatRamsync.FORMAT)
            EnTeteRamsync(
                version = v.toInt(),
                codeTournee = jEntete.get("code_tournee").asString,
                versionNemeton = jEntete.get("version_nemeton")?.takeIf { !it.isJsonNull }?.asString,
                versionRamulus = jEntete.get("version_ramulus").asString,
                appareil = jEntete.get("appareil").asString,
                creeLeUtcMs = jEntete.get("cree_le_utc_ms").asLong,
                nbLignes = jEntete.get("nb_lignes").asInt,
            )
        } catch (e: RuntimeException) { return refus(MotifRefus.VERSION_ILLISIBLE) }
        // Sans code de tournée, les lignes ne se rattachent à rien : en-tête illisible.
        if (entete.codeTournee.isBlank()) return refus(MotifRefus.VERSION_ILLISIBLE)

        // Pied : dernière ligne terminée, objet à la seule clé « sha256 ».
        var finSaisies = lignesBrutes.size
        var pied: String? = null
        if (lignesBrutes.size >= 2) {
            val dernier = json(lignesBrutes.last())
            if (dernier != null && dernier.keySet() == setOf("sha256")) {
                pied = try { dernier.get("sha256").asString } catch (e: RuntimeException) { "" }
                finSaisies = lignesBrutes.size - 1
            }
        }
        if (pied == null) {
            signalements += Signalement.FICHIER_TRONQUE
        } else {
            val attendu = FormatRamsync.sha256Hex(brut.copyOfRange(0, lignesBrutes.last().first))
            if (attendu != pied) signalements += Signalement.EMPREINTE_DIFFERENTE
        }

        var refusees = 0
        val uuidRefuses = mutableListOf<String>()
        val lus = mutableListOf<LigneJournal>()
        fun uuidDe(o: JsonObject?) = try {
            o?.get("uuid")?.takeIf { it.isJsonPrimitive }?.asString
        } catch (e: RuntimeException) { null }
        for (i in 1 until finSaisies) {
            val o = json(lignesBrutes[i])
            val ligne = o?.let { versLigne(it, entete.codeTournee) }
            when {
                ligne != null -> lus += ligne
                o != null && autreTournee(o, entete.codeTournee) -> {
                    refusees++
                    signalements += Signalement.LIGNE_AUTRE_TOURNEE
                    uuidDe(o)?.let { uuidRefuses += it }
                }
                else -> {
                    refusees++
                    signalements += Signalement.LIGNE_ILLISIBLE
                    uuidDe(o)?.let { uuidRefuses += it }
                }
            }
        }

        // Même uuid deux fois dans le fichier : identique, la seconde est ignorée ; différent, les deux sont refusées.
        val fautifs = lus.groupBy { it.uuid }.filterValues { copies -> copies.any { it != copies[0] } }.keys
        var ignorees = 0
        val gardees = mutableListOf<LigneJournal>()
        val deja = mutableSetOf<String>()
        for (l in lus) {
            when {
                l.uuid in fautifs -> {
                    refusees++
                    if (l.uuid !in uuidRefuses) uuidRefuses += l.uuid
                }
                deja.add(l.uuid) -> gardees += l
                else -> ignorees++
            }
        }
        if (fautifs.isNotEmpty()) signalements += Signalement.UUID_CONTENU_DIFFERENT
        return ResultatLecture(null, entete, gardees, signalements, refusees, uuidRefuses, ignorees)
    }

    private fun refus(m: MotifRefus) = ResultatLecture(m, null, emptyList(), emptySet(), 0, emptyList(), 0)

    private class Decompresse(val octets: ByteArray, val tronque: Boolean)

    /** `null` : pas un gzip lisible du tout (0 octet, en-tête gzip absent). Gzip coupé : le début lisible est gardé. */
    private fun decompresser(octets: ByteArray): Decompresse? {
        if (octets.isEmpty()) return null
        val sortie = ByteArrayOutputStream()
        val entree = try { GZIPInputStream(ByteArrayInputStream(octets)) } catch (e: IOException) { return null }
        var tronque = false
        try {
            val tampon = ByteArray(8192)
            while (true) {
                val n = entree.read(tampon)
                if (n < 0) break
                sortie.write(tampon, 0, n)
            }
        } catch (e: IOException) {
            tronque = true
        }
        return Decompresse(sortie.toByteArray(), tronque)
    }

    /** Vrai si la ligne porte un `code_tournee` texte différent de celui de l'en-tête. */
    private fun autreTournee(o: JsonObject, codeEntete: String): Boolean = try {
        val c = o.get("code_tournee")
        c != null && c.isJsonPrimitive && c.asJsonPrimitive.isString && c.asString != codeEntete
    } catch (e: RuntimeException) { false }

    /**
     * `null` si la ligne n'a pas tous ses champs avec le bon type, un type hors de la liste fermée,
     * ou un `code_tournee` (facultatif) qui n'est pas celui de l'en-tête. Sans `code_tournee`, la ligne prend celui
     * de l'en-tête : un fichier est celui d'une seule tournée.
     */
    internal fun versLigne(o: JsonObject, codeEntete: String): LigneJournal? = try {
        fun texte(c: String) = o.get(c).also { require(it.isJsonPrimitive && it.asJsonPrimitive.isString) }.asString
        fun texteOuNull(c: String) = o.get(c).let {
            if (it.isJsonNull) null else { require(it.isJsonPrimitive && it.asJsonPrimitive.isString); it.asString }
        }
        fun entier(c: String) =
            o.get(c).also { require(it.isJsonPrimitive && it.asJsonPrimitive.isNumber) }.asBigDecimal.longValueExact()
        fun entierOuNull(c: String) = o.get(c).let { if (it.isJsonNull) null else entier(c) }
        require(CHAMPS.all { o.has(it) })
        val type = texte("type")
        require(type in TypeLigne.TOUS)
        if (o.has("code_tournee")) {
            val c = o.get("code_tournee")
            require(c.isJsonPrimitive && c.asJsonPrimitive.isString && c.asString == codeEntete)
        }
        LigneJournal(
            uuid = texte("uuid").also { require(it.isNotEmpty()) },
            objetUuid = texte("objet_uuid").also { require(it.isNotEmpty()) },
            type = type, valeur = texteOuNull("valeur"),
            remplaceUuid = texteOuNull("remplace_uuid"), operateur = texte("operateur"), appareil = texte("appareil"),
            sequence = entier("sequence"), saisiLeUtcMs = entier("saisi_le_utc_ms"),
            heureGnssMs = entierOuNull("heure_gnss_ms"), monotoneMs = entier("monotone_ms"),
            demarrage = entier("demarrage"), ageFixMs = entierOuNull("age_fix_ms"), codeTournee = codeEntete,
        )
    } catch (e: RuntimeException) { null }

    /** Les treize champs d'une saisie (docs/formats/ramsync-v1.md). */
    val CHAMPS = listOf(
        "uuid", "objet_uuid", "type", "valeur", "remplace_uuid", "operateur", "appareil", "sequence",
        "saisi_le_utc_ms", "heure_gnss_ms", "monotone_ms", "demarrage", "age_fix_ms",
    )
}
