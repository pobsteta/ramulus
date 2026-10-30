package io.github.pobsteta.ramulus.photo

import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.camera.core.ImageProxy
import io.github.pobsteta.ramulus.core.photo.ControlesImage
import io.github.pobsteta.ramulus.core.photo.ImageGris
import io.github.pobsteta.ramulus.core.photo.SignalementImage
import java.io.ByteArrayOutputStream

/** Une photo prête à garder : ses octets JPEG, sans EXIF, et ce que les contrôles en ont dit. */
class PhotoPrise(val jpeg: ByteArray, val signalements: List<SignalementImage>)

/**
 * De l'image de CameraX au JPEG gardé (mission 008, ADR 0006).
 *
 * Le JPEG est **réécrit** à partir des pixels (`Bitmap.compress`) : il n'emporte donc aucun EXIF (ni position, ni
 * heure, ni modèle de téléphone). L'orientation de l'EXIF est appliquée aux pixels avant d'être perdue.
 */
object PriseDeVue {
    /** Grand côté maximal de la photo gardée, en pixels. **Valeur provisoire** (à fixer par Pascal, avec la qualité). */
    const val COTE_MAX_GARDE = 2048

    /** Qualité JPEG de la photo gardée (0 à 100). **Valeur provisoire.** */
    const val QUALITE_JPEG = 85

    /** Traite et ferme [image]. Bloquant : hors du fil principal. */
    fun traiter(image: ImageProxy): PhotoPrise {
        try {
            val brute = image.toBitmap()
            val degres = image.imageInfo.rotationDegrees
            return depuis(brute, degres)
        } finally {
            image.close()
        }
    }

    /**
     * Les pixels de [brute] sont libérés ici même (réserve 5 de la mission 017) : une photo de 12 à 50 Mpx pèse 50 à
     * 200 Mo décodée, et plusieurs prises de suite ne doivent pas attendre le ramasse-miettes. [brute] ne sert plus après.
     */
    fun depuis(brute: Bitmap, rotationDegres: Int): PhotoPrise {
        var droite: Bitmap? = null
        var gardee: Bitmap? = null
        try {
            droite = if (rotationDegres % 360 != 0) {
                Bitmap.createBitmap(brute, 0, 0, brute.width, brute.height, Matrix().apply { postRotate(rotationDegres.toFloat()) }, true)
            } else {
                brute
            }
            val (l, h) = ControlesImage.dimensionsReduites(droite.width, droite.height, COTE_MAX_GARDE)
            gardee = if (l == droite.width && h == droite.height) droite else Bitmap.createScaledBitmap(droite, l, h, true)
            val sortie = ByteArrayOutputStream()
            check(gardee.compress(Bitmap.CompressFormat.JPEG, QUALITE_JPEG, sortie)) { "JPEG non écrit" }
            return PhotoPrise(sortie.toByteArray(), controler(gardee))
        } finally {
            // Chaque tampon est libéré une fois, quel que soit le chemin (même Bitmap référencé deux fois).
            setOfNotNull(gardee, droite, brute).forEach { if (!it.isRecycled) it.recycle() }
        }
    }

    private fun controler(b: Bitmap): List<SignalementImage> {
        val (l, h) = ControlesImage.dimensionsReduites(b.width, b.height, ControlesImage.COTE_DE_MESURE)
        val petite = if (l == b.width && h == b.height) b else Bitmap.createScaledBitmap(b, l, h, true)
        val pixels = IntArray(l * h)
        petite.getPixels(pixels, 0, l, 0, 0, l, h)
        if (petite !== b) petite.recycle()
        return ControlesImage.controler(ImageGris.depuisArgb(l, h, pixels))
    }

    /** Le signalement dit à l'opérateur, en clair. Rien ne bloque. */
    fun enClair(s: SignalementImage): String = when (s) {
        SignalementImage.FLOU -> "Photo peut-être floue : reprenez-la si vous le pouvez."
        SignalementImage.CONTRE_JOUR -> "Contre-jour probable : placez-vous le soleil dans le dos."
    }
}
