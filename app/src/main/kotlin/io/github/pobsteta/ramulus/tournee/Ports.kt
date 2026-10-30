package io.github.pobsteta.ramulus.tournee

import io.github.pobsteta.ramulus.core.echange.EchangeDeperis
import io.github.pobsteta.ramulus.core.journal.LigneJournal
import java.io.File

/** L'échange `deperis-v1` (mission 004), vu de l'écran. Les résultats sont les textes JSON de [EchangeDeperis]. */
interface Echange {
    fun ouvrir(gpkg: File): String
    fun instantane(fichier: File): String
    fun enregistrerCentre(gpkg: File, centre: String)
    fun enregistrerNotation(gpkg: File, notation: String): String
    fun cloturerPlacette(gpkg: File, plotId: String)
    fun rendre(gpkg: File, depart: File, racinePhotos: File, operateur: String, versionRamulus: String, sortie: File): String

    /** Corrige une note : l'ancienne est retirée de la copie de travail, la nouvelle ajoutée (mission 015). */
    fun remplacerNotation(gpkg: File, ancienUuid: String, notation: String): String =
        EchangeDeperis.remplacerNotation(gpkg, ancienUuid, notation)

    /** Relève de nouveau un centre : les centres de la placette sont retirés, le nouveau ajouté ; rend les `uuid` retirés. */
    fun remplacerCentre(gpkg: File, centre: String): String = EchangeDeperis.remplacerCentre(gpkg, centre)
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
    /** Ajoute une saisie (`notation`, `contre_notation`, `photo`, `centre`, `placette_notee`). */
    fun saisir(codeTournee: String, objetUuid: String, type: String, valeur: String, operateur: String)

    /**
     * Ajoute une saisie qui **remplace** la ligne `remplaceUuid` du journal (une correction, mission 015 ; une
     * `annulation`). Le journal reste en ajout seul : la ligne remplacée n'est ni modifiée ni effacée. Sans
     * `remplaceUuid`, c'est une saisie ordinaire. Un journal qui ne sait pas remplacer garde la saisie, sans le lien.
     */
    fun saisir(codeTournee: String, objetUuid: String, type: String, valeur: String, operateur: String, remplaceUuid: String?) {
        saisir(codeTournee, objetUuid, type, valeur, operateur)
    }

    /** Les lignes de la tournée, dans l'ordre total : pour retrouver la ligne qu'une correction remplace. */
    fun lignes(codeTournee: String): List<LigneJournal> = emptyList()

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
