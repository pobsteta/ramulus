package io.github.pobsteta.ramulus.core.sauvegarde

import java.text.Normalizer
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Nom d'un `.ramsync` : `<code-tournée>-<appareil>-<yyyyMMddTHHmmssZ>.ramsync` (sursync-v1, « Nom du fichier » ;
 * ADR 0005, confidentialité). Le nom ne porte **jamais** de nom d'opérateur ni de propriétaire : il n'est construit
 * qu'à partir du code de la tournée, de l'identifiant opaque de l'appareil et de l'heure.
 */
object NomFichier {
    private val HORODATAGE = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC)
    private val APPAREIL = Regex("^[0-9a-f]{8}$")

    /** `<code>-<appareil 8 hex>-<horodatage>` puis un suffixe `-2`, `-3`… facultatif, puis l'extension. */
    private val MOTIF = Regex("^(.+)-([0-9a-f]{8})-(\\d{8}T\\d{6}Z)(?:-(\\d+))?\\.ramsync$")

    /** Accents retirés ; tout caractère hors lettres ASCII, chiffres, `.`, `_`, `-` remplacé par `_`. */
    fun assainir(code: String): String =
        Normalizer.normalize(code, Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .replace(Regex("[^A-Za-z0-9._-]"), "_")

    /** Nom de base, sans suffixe. `appareil` : identifiant opaque de 8 caractères hexadécimaux minuscules. */
    fun nom(codeTournee: String, appareil: String, creeLeUtcMs: Long): String {
        require(codeTournee.isNotBlank()) { "code de tournée vide" }
        require(APPAREIL.matches(appareil)) { "appareil non opaque : 8 caractères hexadécimaux attendus" }
        return "${assainir(codeTournee)}-$appareil-${HORODATAGE.format(Instant.ofEpochMilli(creeLeUtcMs))}" +
            FormatRamsync.EXTENSION
    }

    /** Un nom déjà pris n'est jamais écrasé : `...Z.ramsync`, puis `...Z-2.ramsync`, `...Z-3.ramsync`… */
    fun libre(nom: String, pris: Set<String>): String {
        if (nom !in pris) return nom
        val base = nom.removeSuffix(FormatRamsync.EXTENSION)
        return generateSequence(2) { it + 1 }.map { "$base-$it${FormatRamsync.EXTENSION}" }.first { it !in pris }
    }

    /** Morceaux d'un nom de `.ramsync`, ou `null` si le nom ne suit pas le motif. */
    data class Morceaux(val code: String, val appareil: String, val horodatage: String, val suffixe: Int)

    fun lire(nom: String): Morceaux? = MOTIF.matchEntire(nom)?.let { m ->
        Morceaux(m.groupValues[1], m.groupValues[2], m.groupValues[3], m.groupValues[4].ifEmpty { "1" }.toInt())
    }
}

/** Rotation : on garde les 5 dernières sauvegardes **de chaque tournée** (sursync-v1, « Nom du fichier »). */
object Rotation {
    const val GARDEES = 5

    /**
     * Noms à supprimer parmi `noms` (les fichiers présents) après l'écriture d'une sauvegarde de `codeTournee` : les
     * plus anciennes de **cette** tournée au-delà de 5 ; jamais celles d'une autre tournée, ni un fichier au nom
     * inconnu.
     */
    fun aSupprimer(noms: Collection<String>, codeTournee: String, gardees: Int = GARDEES): List<String> {
        val code = NomFichier.assainir(codeTournee)
        val siens = noms.mapNotNull { n -> NomFichier.lire(n)?.takeIf { it.code == code }?.let { n to it } }
            .sortedWith(compareBy({ it.second.horodatage }, { it.second.suffixe }, { it.first }))
        return siens.dropLast(gardees).map { it.first }
    }
}

/**
 * Cadence de sauvegarde (ADR 0005 ; Surculus ADR 0005, l. 50) : à chaque fin de placette, **et** au plus tard toutes
 * les 20 saisies ou les 5 minutes (horloge monotone), au premier des deux ; **jamais sans saisie nouvelle**.
 * Valeurs provisoires, non mesurées pour Ramulus (fiche 005) : gel avant la mission 009.
 */
object Cadence {
    const val N_SAISIES = 20
    const val M_MINUTES = 5
    const val M_MS = M_MINUTES * 60_000L

    /**
     * @param nouvelles saisies de la tournée écrites depuis la dernière sauvegarde (ou depuis le début).
     * @param repereMonotoneMs instant monotone de la dernière sauvegarde de la tournée, ou, s'il n'y en a pas, de la
     *   première saisie non sauvegardée ; `null` si inconnu.
     * @param repereDemarrage démarrage de ce repère : si le téléphone a redémarré depuis, l'horloge monotone ne se
     *   compare plus, et la sauvegarde est due.
     */
    fun estDue(
        nouvelles: Int,
        finDePlacette: Boolean,
        repereMonotoneMs: Long?,
        repereDemarrage: Long?,
        maintenantMonotoneMs: Long,
        demarrage: Long,
    ): Boolean {
        if (nouvelles <= 0) return false
        if (finDePlacette || nouvelles >= N_SAISIES) return true
        if (repereMonotoneMs == null || repereDemarrage != demarrage) return true
        return maintenantMonotoneMs - repereMonotoneMs >= M_MS
    }
}
