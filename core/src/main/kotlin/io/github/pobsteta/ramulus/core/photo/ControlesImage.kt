package io.github.pobsteta.ramulus.core.photo

/** Une image en niveaux de gris, ligne par ligne, luminance de 0 (noir) à 255 (blanc). */
class ImageGris(val largeur: Int, val hauteur: Int, val luminance: IntArray) {
    init {
        require(largeur > 0 && hauteur > 0) { "image vide" }
        require(luminance.size == largeur * hauteur) { "${luminance.size} pixels pour $largeur x $hauteur" }
    }

    operator fun get(x: Int, y: Int): Int = luminance[y * largeur + x]

    companion object {
        /** Depuis des pixels ARGB d'Android (`Bitmap.getPixels`) : luminance Rec. 601, entière. */
        fun depuisArgb(largeur: Int, hauteur: Int, argb: IntArray): ImageGris =
            ImageGris(
                largeur,
                hauteur,
                IntArray(argb.size) { i ->
                    val p = argb[i]
                    (299 * ((p shr 16) and 0xFF) + 587 * ((p shr 8) and 0xFF) + 114 * (p and 0xFF)) / 1000
                },
            )
    }
}

/** Ce qui est signalé à l'opérateur à la prise. Rien ne bloque (ADR 0006). */
enum class SignalementImage { FLOU, CONTRE_JOUR }

data class MesuresImage(val varianceLaplacien: Double, val partClaire: Double, val partSombre: Double)

/**
 * Les contrôles à la prise (ADR 0006) : le flou par la variance du laplacien, le contre-jour par l'histogramme. Le
 * houppier coupé n'est pas mesuré : c'est un rappel de cadrage à l'écran.
 *
 * **Tous les seuils sont provisoires** (aucune source ne les donne ; mission 008, « Valeur provisoire ») : à régler
 * par Pascal sur des photos de forêt avant le gel, d'après les signalements vus sur le terrain. Ils valent pour une
 * image dont le grand côté fait [COTE_DE_MESURE] pixels.
 */
object ControlesImage {
    /** Grand côté de l'image sur laquelle on mesure. Provisoire. */
    const val COTE_DE_MESURE = 640

    /** En dessous de cette variance du laplacien (3 x 3, luminance 0 à 255), la photo est signalée floue. Provisoire. */
    const val SEUIL_FLOU = 100.0

    /** Un pixel est « clair » à partir de cette luminance (ciel brûlé). Provisoire. */
    const val LUMINANCE_CLAIRE = 230

    /** Un pixel est « sombre » jusqu'à cette luminance (houppier à contre-jour). Provisoire. */
    const val LUMINANCE_SOMBRE = 60

    /** Contre-jour : au moins cette part de pixels clairs… Provisoire. */
    const val PART_CLAIRE_MIN = 0.20

    /** … et au moins cette part de pixels sombres. Provisoire. */
    const val PART_SOMBRE_MIN = 0.25

    fun mesurer(image: ImageGris): MesuresImage {
        require(image.largeur >= 3 && image.hauteur >= 3) { "image trop petite pour le laplacien" }
        var somme = 0.0
        var sommeCarres = 0.0
        var n = 0
        for (y in 1 until image.hauteur - 1) {
            for (x in 1 until image.largeur - 1) {
                val l = (image[x - 1, y] + image[x + 1, y] + image[x, y - 1] + image[x, y + 1] - 4 * image[x, y]).toDouble()
                somme += l
                sommeCarres += l * l
                n++
            }
        }
        val moyenne = somme / n
        val variance = sommeCarres / n - moyenne * moyenne
        val total = image.luminance.size.toDouble()
        val claires = image.luminance.count { it >= LUMINANCE_CLAIRE } / total
        val sombres = image.luminance.count { it <= LUMINANCE_SOMBRE } / total
        return MesuresImage(variance, claires, sombres)
    }

    fun signalements(m: MesuresImage): List<SignalementImage> = buildList {
        if (m.varianceLaplacien < SEUIL_FLOU) add(SignalementImage.FLOU)
        if (m.partClaire >= PART_CLAIRE_MIN && m.partSombre >= PART_SOMBRE_MIN) add(SignalementImage.CONTRE_JOUR)
    }

    fun controler(image: ImageGris): List<SignalementImage> = signalements(mesurer(image))

    /** Dimensions après réduction pour que le grand côté ne dépasse pas [coteMax] ; jamais d'agrandissement. */
    fun dimensionsReduites(largeur: Int, hauteur: Int, coteMax: Int): Pair<Int, Int> {
        val grand = maxOf(largeur, hauteur)
        if (grand <= coteMax) return largeur to hauteur
        val f = coteMax.toDouble() / grand
        return maxOf(1, Math.round(largeur * f).toInt()) to maxOf(1, Math.round(hauteur * f).toInt())
    }
}
