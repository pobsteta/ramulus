package io.github.pobsteta.ramulus.data.sauvegarde

import io.github.pobsteta.ramulus.core.sauvegarde.Cadence
import io.github.pobsteta.ramulus.core.sauvegarde.EcritureRamsync
import io.github.pobsteta.ramulus.core.sauvegarde.FormatRamsync
import io.github.pobsteta.ramulus.core.sauvegarde.NomFichier
import io.github.pobsteta.ramulus.core.sauvegarde.Rotation
import io.github.pobsteta.ramulus.data.journal.Horloges
import io.github.pobsteta.ramulus.data.journal.JournalRepository
import io.github.pobsteta.ramulus.data.journal.RamulusDatabase
import io.github.pobsteta.ramulus.data.journal.SauvegardeEntity

/**
 * Écrit les `.ramsync` d'une tournée (ADR 0005 ; docs/formats/ramsync-v1.md) : à la cadence de [Cadence], jamais sans
 * saisie nouvelle, sous un nom libre (jamais d'écrasement), puis rotation des 5 dernières de la même tournée.
 * Chaque fichier écrit est inscrit à la table `sauvegardes`. Méthodes bloquantes : hors du fil principal.
 */
class SauvegardeRepository(
    private val db: RamulusDatabase,
    private val journal: JournalRepository,
    private val destination: Destination,
    private val horloges: Horloges,
    private val versionRamulus: String,
) {
    /** Saisies de la tournée écrites depuis sa dernière sauvegarde. */
    fun nouvelles(codeTournee: String): Int =
        db.journal().nombre(codeTournee) - (db.sauvegardes().derniere(codeTournee)?.nbLignes ?: 0)

    /**
     * Sauvegarde si c'est dû : fin de placette, 20 saisies ou 5 minutes depuis la dernière sauvegarde (ou depuis la
     * première saisie de la tournée s'il n'y en a pas eu), et au moins une saisie nouvelle. Rend le nom écrit, ou
     * `null` si rien n'était dû.
     */
    fun sauvegarderSiDue(codeTournee: String, versionNemeton: String?, finDePlacette: Boolean): String? {
        val derniere = db.sauvegardes().derniere(codeTournee)
        val repere = derniere?.let { it.monotoneMs to it.demarrage }
            ?: db.journal().premiereInseree(codeTournee)?.let { it.monotoneMs to it.demarrage }
        val due = Cadence.estDue(
            nouvelles = nouvelles(codeTournee),
            finDePlacette = finDePlacette,
            repereMonotoneMs = repere?.first,
            repereDemarrage = repere?.second,
            maintenantMonotoneMs = horloges.monotoneMs(),
            demarrage = horloges.demarrage(),
        )
        return if (due) sauvegarder(codeTournee, versionNemeton) else null
    }

    /** Sauvegarde tout de suite s'il y a au moins une saisie nouvelle ; rend le nom écrit, ou `null`. */
    fun sauvegarder(codeTournee: String, versionNemeton: String?): String? {
        if (nouvelles(codeTournee) <= 0) return null
        val lignes = journal.lignes(codeTournee)
        val appareil = journal.appareil()
        val utc = horloges.utcMs()
        val octets = EcritureRamsync.ecrire(codeTournee, versionNemeton, versionRamulus, appareil, utc, lignes)
        val nom = NomFichier.libre(NomFichier.nom(codeTournee, appareil, utc), destination.noms())
        destination.ecrire(nom, octets)
        db.sauvegardes().inserer(SauvegardeEntity(
            codeTournee = codeTournee, nomFichier = nom, voie = SauvegardeEntity.VOIE_DOCUMENTS,
            nbLignes = lignes.size, sha256 = FormatRamsync.sha256Hex(octets), ecriteLeUtcMs = utc,
            monotoneMs = horloges.monotoneMs(), demarrage = horloges.demarrage(),
        ))
        Rotation.aSupprimer(destination.noms(), codeTournee).forEach { destination.supprimer(it) }
        return nom
    }
}
