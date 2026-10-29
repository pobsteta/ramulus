package io.github.pobsteta.ramulus.accueil

import io.github.pobsteta.ramulus.core.photo.ControlesImage
import io.github.pobsteta.ramulus.photo.PriseDeVue
import java.util.Locale

/** Un seuil provisoire de la photo, tel que Paramètres l'affiche : son nom et sa valeur. */
data class SeuilPhoto(val nom: String, val valeur: String)

private fun pourcent(part: Double) = String.format(Locale.FRANCE, "%.0f %%", part * 100)

/**
 * Les seuils provisoires des photos (mission 008), lus aux constantes de `ControlesImage` et de `PriseDeVue` : une seule
 * source, rien n'est recopié. Affichés en lecture seule ; leur réglage viendra avec les mesures de Pascal sur des
 * photos de forêt, avant le gel.
 */
fun seuilsPhotos(): List<SeuilPhoto> = listOf(
    SeuilPhoto("Flou : variance du laplacien sous", "${ControlesImage.SEUIL_FLOU.toInt()}"),
    SeuilPhoto("Contre-jour : pixels clairs (luminance ≥ ${ControlesImage.LUMINANCE_CLAIRE}) à partir de", pourcent(ControlesImage.PART_CLAIRE_MIN)),
    SeuilPhoto("Contre-jour : pixels sombres (luminance ≤ ${ControlesImage.LUMINANCE_SOMBRE}) à partir de", pourcent(ControlesImage.PART_SOMBRE_MIN)),
    SeuilPhoto("Photo gardée : grand côté", "${PriseDeVue.COTE_MAX_GARDE} px"),
    SeuilPhoto("Photo gardée : qualité JPEG", "${PriseDeVue.QUALITE_JPEG}"),
)
