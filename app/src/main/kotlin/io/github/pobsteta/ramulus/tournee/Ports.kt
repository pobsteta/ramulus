package io.github.pobsteta.ramulus.tournee

import io.github.pobsteta.ramulus.core.echange.EchangeDeperis
import java.io.File

/** L'échange `deperis-v1` (mission 004), vu de l'écran. Les résultats sont les textes JSON de [EchangeDeperis]. */
interface Echange {
    fun ouvrir(gpkg: File): String
    fun instantane(fichier: File): String
    fun enregistrerCentre(gpkg: File, centre: String)
    fun enregistrerNotation(gpkg: File, notation: String): String
    fun cloturerPlacette(gpkg: File, plotId: String)
    fun rendre(gpkg: File, depart: File, racinePhotos: File, operateur: String, versionRamulus: String, sortie: File): String
}

/** Seule porte vers le GeoPackage : [EchangeDeperis]. */
object EchangeReel : Echange {
    override fun ouvrir(gpkg: File) = EchangeDeperis.ouvrir(gpkg)
    override fun instantane(fichier: File) = EchangeDeperis.instantane(fichier)
    override fun enregistrerCentre(gpkg: File, centre: String) = EchangeDeperis.enregistrerCentre(gpkg, centre)
    override fun enregistrerNotation(gpkg: File, notation: String) = EchangeDeperis.enregistrerNotation(gpkg, notation)
    override fun cloturerPlacette(gpkg: File, plotId: String) = EchangeDeperis.cloturerPlacette(gpkg, plotId)
    override fun rendre(gpkg: File, depart: File, racinePhotos: File, operateur: String, versionRamulus: String, sortie: File) =
        EchangeDeperis.rendre(gpkg, depart, racinePhotos, operateur, versionRamulus, sortie)
}

/** Le journal en ajout seul (mission 005) et sa sauvegarde, vus de l'écran. Bloquant : hors du fil principal. */
interface Journal {
    /** Ajoute une saisie (`notation`, `contre_notation`, `centre`, `placette_notee`). */
    fun saisir(codeTournee: String, objetUuid: String, type: String, valeur: String, operateur: String)

    /** Sauvegarde `.ramsync` si c'est dû (cadence de 005) ; rend le nom écrit ou `null`. */
    fun sauvegarderSiDue(codeTournee: String, versionNemeton: String?, finDePlacette: Boolean): String?
}

/** Retient le nom de l'opérateur, saisi une seule fois. */
interface StockOperateur {
    fun lire(): String?
    fun ecrire(nom: String)
}

/** Dépose le `.zip` rendu là où l'opérateur et Nemeton le retrouvent ; rend le nom sous lequel il est écrit. */
interface ExportRendu {
    fun exporter(zip: File, nomSouhaite: String): String
}
