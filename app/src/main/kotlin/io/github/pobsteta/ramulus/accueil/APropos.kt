package io.github.pobsteta.ramulus.accueil

/** Dépôt du projet et licence (fichier `LICENSE` à la racine du dépôt : GNU GPL version 3). */
const val URL_DEPOT = "https://github.com/pobsteta/ramulus"
const val URL_LICENCE = "https://github.com/pobsteta/ramulus/blob/main/LICENSE"
const val LICENCE = "GNU GPL version 3 (texte complet : fichier LICENSE du dépôt)"
const val PROTOCOLE = "DEPERIS, DSF, fiches 2025"

/** Ce que « À propos » dit : le nom, la version, la licence, le protocole suivi. Pas de numéro inventé si la version manque. */
data class TexteAPropos(val nom: String, val version: String, val licence: String, val protocole: String)

/** [versionName] et [versionCode] sont ceux du paquet installé ; absents ou vides, on le dit. */
fun aPropos(versionName: String?, versionCode: Long?): TexteAPropos {
    val nom = versionName?.trim()?.takeIf { it.isNotEmpty() }
    val version = when {
        nom == null -> "Version inconnue"
        versionCode == null -> "Version $nom"
        else -> "Version $nom (code $versionCode)"
    }
    return TexteAPropos("Ramulus", version, LICENCE, PROTOCOLE)
}
