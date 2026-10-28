package io.github.pobsteta.ramulus.data.journal

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import io.github.pobsteta.ramulus.core.journal.LigneJournal

/**
 * Table `journal` : une ligne par saisie, **ajout seul** (ADR 0005 ; Surculus ADR 0005, l. 46). Aucune mise à jour ni
 * suppression : le DAO n'en offre pas, et des déclencheurs SQLite les refusent ([RamulusDatabase]).
 */
@Entity(
    tableName = "journal",
    indices = [
        Index(value = ["saisi_le_utc_ms", "appareil", "sequence", "uuid"], name = "index_journal_ordre_total"),
        Index(value = ["remplace_uuid"], name = "index_journal_remplace_uuid"),
    ],
)
data class LigneEntity(
    @PrimaryKey @ColumnInfo(name = "uuid") val uuid: String,
    @ColumnInfo(name = "objet_uuid") val objetUuid: String,
    @ColumnInfo(name = "type") val type: String,
    @ColumnInfo(name = "valeur") val valeur: String?,
    @ColumnInfo(name = "remplace_uuid") val remplaceUuid: String?,
    @ColumnInfo(name = "operateur") val operateur: String,
    @ColumnInfo(name = "appareil") val appareil: String,
    @ColumnInfo(name = "sequence") val sequence: Long,
    @ColumnInfo(name = "saisi_le_utc_ms") val saisiLeUtcMs: Long,
    @ColumnInfo(name = "heure_gnss_ms") val heureGnssMs: Long?,
    @ColumnInfo(name = "monotone_ms") val monotoneMs: Long,
    @ColumnInfo(name = "demarrage") val demarrage: Long,
    @ColumnInfo(name = "age_fix_ms") val ageFixMs: Long?,
    @ColumnInfo(name = "code_tournee") val codeTournee: String,
) {
    fun versLigne() = LigneJournal(
        uuid, objetUuid, type, valeur, remplaceUuid, operateur, appareil, sequence, saisiLeUtcMs, heureGnssMs,
        monotoneMs, demarrage, ageFixMs, codeTournee,
    )

    companion object {
        fun de(l: LigneJournal) = LigneEntity(
            l.uuid, l.objetUuid, l.type, l.valeur, l.remplaceUuid, l.operateur, l.appareil, l.sequence, l.saisiLeUtcMs,
            l.heureGnssMs, l.monotoneMs, l.demarrage, l.ageFixMs, l.codeTournee,
        )
    }
}

/**
 * Table `refus_journal`, **ajout seul** (plan 005, Q5) : la seconde version d'un `uuid` reçue avec un contenu
 * différent (restauration ou fusion), gardée à côté, jamais à la place. Colonnes du journal, plus `motif` et
 * `recu_le_utc_ms`.
 */
@Entity(tableName = "refus_journal", indices = [Index(value = ["uuid"], name = "index_refus_journal_uuid")])
data class RefusEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "id") val id: Long = 0,
    @ColumnInfo(name = "uuid") val uuid: String,
    @ColumnInfo(name = "objet_uuid") val objetUuid: String,
    @ColumnInfo(name = "type") val type: String,
    @ColumnInfo(name = "valeur") val valeur: String?,
    @ColumnInfo(name = "remplace_uuid") val remplaceUuid: String?,
    @ColumnInfo(name = "operateur") val operateur: String,
    @ColumnInfo(name = "appareil") val appareil: String,
    @ColumnInfo(name = "sequence") val sequence: Long,
    @ColumnInfo(name = "saisi_le_utc_ms") val saisiLeUtcMs: Long,
    @ColumnInfo(name = "heure_gnss_ms") val heureGnssMs: Long?,
    @ColumnInfo(name = "monotone_ms") val monotoneMs: Long,
    @ColumnInfo(name = "demarrage") val demarrage: Long,
    @ColumnInfo(name = "age_fix_ms") val ageFixMs: Long?,
    @ColumnInfo(name = "code_tournee") val codeTournee: String,
    @ColumnInfo(name = "motif") val motif: String,
    @ColumnInfo(name = "recu_le_utc_ms") val recuLeUtcMs: Long,
) {
    fun versLigne() = LigneJournal(
        uuid, objetUuid, type, valeur, remplaceUuid, operateur, appareil, sequence, saisiLeUtcMs, heureGnssMs,
        monotoneMs, demarrage, ageFixMs, codeTournee,
    )

    companion object {
        const val UUID_CONTENU_DIFFERENT = "uuid_contenu_different"

        fun de(l: LigneJournal, motif: String, recuLeUtcMs: Long) = RefusEntity(
            uuid = l.uuid, objetUuid = l.objetUuid, type = l.type, valeur = l.valeur, remplaceUuid = l.remplaceUuid,
            operateur = l.operateur, appareil = l.appareil, sequence = l.sequence, saisiLeUtcMs = l.saisiLeUtcMs,
            heureGnssMs = l.heureGnssMs, monotoneMs = l.monotoneMs, demarrage = l.demarrage, ageFixMs = l.ageFixMs,
            codeTournee = l.codeTournee, motif = motif, recuLeUtcMs = recuLeUtcMs,
        )
    }
}

/**
 * Table `sauvegardes`, **ajout seul** (plan 005, Q6) : une ligne par `.ramsync` écrit. Sert à la cadence (« jamais
 * sans saisie nouvelle » : `nb_lignes` du journal de la tournée au moment de l'écriture ; 5 minutes : `monotone_ms`,
 * `demarrage`). `voie` : où le fichier a été écrit ; seule `documents` (Documents/Ramulus/) existe en 005, les trois
 * voies hors de l'appareil viendront avec leur mission (plan 005, Q12).
 */
@Entity(tableName = "sauvegardes", indices = [Index(value = ["code_tournee"], name = "index_sauvegardes_code_tournee")])
data class SauvegardeEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "id") val id: Long = 0,
    @ColumnInfo(name = "code_tournee") val codeTournee: String,
    @ColumnInfo(name = "nom_fichier") val nomFichier: String,
    @ColumnInfo(name = "voie") val voie: String,
    @ColumnInfo(name = "nb_lignes") val nbLignes: Int,
    @ColumnInfo(name = "sha256") val sha256: String,
    @ColumnInfo(name = "ecrite_le_utc_ms") val ecriteLeUtcMs: Long,
    @ColumnInfo(name = "monotone_ms") val monotoneMs: Long,
    @ColumnInfo(name = "demarrage") val demarrage: Long,
) {
    companion object {
        const val VOIE_DOCUMENTS = "documents"
    }
}

/**
 * Table `appareil`, **une seule ligne** (`id` = 1) : identifiant opaque de l'installation (8 caractères
 * hexadécimaux, tiré une fois, sursync-v1 l. 74) et prochaine séquence du compteur local monotone. Seule table qui
 * se met à jour : elle ne porte aucune saisie.
 */
@Entity(tableName = "appareil")
data class AppareilEntity(
    @PrimaryKey @ColumnInfo(name = "id") val id: Int = 1,
    @ColumnInfo(name = "identifiant") val identifiant: String,
    @ColumnInfo(name = "prochaine_sequence") val prochaineSequence: Long,
)
