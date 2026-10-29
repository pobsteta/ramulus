package io.github.pobsteta.ramulus.core.photo

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.security.MessageDigest

/**
 * Les photos du houppier (mission 008, ADR 0006) : le régime d'une placette, le nom des fichiers, leur empreinte et
 * la colonne `photos` de `notations` (docs/formats/deperis-v1.md, R10a à R10c).
 */

/** Combien de photos par arbre : 1 (placette ordinaire) ou 2 à 4 (placette « collecte »). **Valeur provisoire (PV1).** */
data class RegimePhotos(val min: Int, val max: Int) {
    companion object {
        /** Une photo du houppier par arbre. */
        val ORDINAIRE = RegimePhotos(1, 1)

        /** 2 à 4 vues à 90° (ADR 0006, PV1 : non sourcée, à mesurer sur le terrain avant le gel de la mission 008). */
        val COLLECTE = RegimePhotos(2, 4)

        fun de(collectePhotos: Boolean) = if (collectePhotos) COLLECTE else ORDINAIRE
    }
}

/** Une photo gardée : nom dans le paquet (`photos/<uuid>_<n>.jpg`) et SHA-256 de ses octets. */
data class PhotoGardee(val nom: String, val sha256: String)

object NomsPhotos {
    const val DOSSIER = "photos"

    /** Le nom d'un fichier dans le dossier `photos/` : `<uuid>_<n>.jpg`, `n` partant de 1. */
    fun fichier(uuidNote: String, n: Int): String {
        require(n >= 1) { "le numéro d'une photo part de 1" }
        return "${uuidNote}_$n.jpg"
    }

    /** Le nom cité par la colonne `photos` : `photos/<uuid>_<n>.jpg`. */
    fun nom(uuidNote: String, n: Int): String = "$DOSSIER/${fichier(uuidNote, n)}"

    /** La colonne `photos` : `[{"nom":…,"sha256":…}, …]`, `[]` sans photo. */
    fun colonne(photos: List<PhotoGardee>): JsonArray = JsonArray().also { a ->
        photos.forEach { p -> a.add(JsonObject().also { o -> o.addProperty("nom", p.nom); o.addProperty("sha256", p.sha256) }) }
    }
}

object Empreinte {
    /** SHA-256 en 64 hexadécimaux minuscules, comme le format l'exige. */
    fun sha256(octets: ByteArray): String = hex(MessageDigest.getInstance("SHA-256").digest(octets))

    fun sha256(flux: InputStream): String {
        val md = MessageDigest.getInstance("SHA-256")
        flux.use { f ->
            val tampon = ByteArray(64 * 1024)
            while (true) {
                val n = f.read(tampon)
                if (n < 0) break
                md.update(tampon, 0, n)
            }
        }
        return hex(md.digest())
    }

    private fun hex(octets: ByteArray) = octets.joinToString("") { "%02x".format(it) }
}

/**
 * Un JPEG sans EXIF (ADR 0006) : position, heure et saison sont dans le GeoPackage, pas dans la photo. On retire les
 * segments APP1 (EXIF et XMP) et APP13 (IPTC) ; le reste du fichier est recopié tel quel.
 */
object JpegSansExif {
    private const val SOS = 0xDA
    private const val EOI = 0xD9
    private const val APP1 = 0xE1
    private const val APP13 = 0xED

    private data class Segment(val marqueur: Int, val debut: Int, val fin: Int)

    fun estUnJpeg(o: ByteArray) = o.size >= 4 && (o[0].toInt() and 0xFF) == 0xFF && (o[1].toInt() and 0xFF) == 0xD8

    /** Vrai si le fichier porte un segment APP1 ou APP13. */
    fun contientMetadonnees(o: ByteArray): Boolean = segmentsAEnlever(o).isNotEmpty()

    /** Vrai si le fichier porte un EXIF (APP1 débutant par `Exif`). */
    fun contientExif(o: ByteArray): Boolean = segmentsAEnlever(o).any { s ->
        s.marqueur == APP1 && o.size >= s.debut + 8 && String(o, s.debut + 4, 4, Charsets.ISO_8859_1) == "Exif"
    }

    fun retirer(o: ByteArray): ByteArray {
        val aEnlever = segmentsAEnlever(o)
        if (aEnlever.isEmpty()) return o
        val sortie = ByteArrayOutputStream(o.size)
        var curseur = 0
        for (s in aEnlever) {
            sortie.write(o, curseur, s.debut - curseur)
            curseur = s.fin
        }
        sortie.write(o, curseur, o.size - curseur)
        return sortie.toByteArray()
    }

    private fun segmentsAEnlever(o: ByteArray): List<Segment> {
        require(estUnJpeg(o)) { "ce n'est pas un JPEG" }
        val trouves = mutableListOf<Segment>()
        var i = 2
        while (i + 4 <= o.size) {
            if ((o[i].toInt() and 0xFF) != 0xFF) break
            val marqueur = o[i + 1].toInt() and 0xFF
            if (marqueur == SOS || marqueur == EOI) break
            val longueur = ((o[i + 2].toInt() and 0xFF) shl 8) or (o[i + 3].toInt() and 0xFF)
            if (longueur < 2 || i + 2 + longueur > o.size) break
            if (marqueur == APP1 || marqueur == APP13) trouves += Segment(marqueur, i, i + 2 + longueur)
            i += 2 + longueur
        }
        return trouves
    }
}
