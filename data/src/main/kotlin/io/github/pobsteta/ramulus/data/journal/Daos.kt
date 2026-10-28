package io.github.pobsteta.ramulus.data.journal

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/** Journal en ajout seul : insertion (refusée si l'`uuid` existe) et lecture, rien d'autre. */
@Dao
interface JournalDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun inserer(ligne: LigneEntity)

    @Query("SELECT * FROM journal WHERE uuid = :uuid")
    fun parUuid(uuid: String): LigneEntity?

    @Query("SELECT * FROM journal WHERE code_tournee = :codeTournee ORDER BY saisi_le_utc_ms, appareil, sequence, uuid")
    fun deLaTournee(codeTournee: String): List<LigneEntity>

    @Query("SELECT * FROM journal ORDER BY saisi_le_utc_ms, appareil, sequence, uuid")
    fun toutes(): List<LigneEntity>

    @Query("SELECT COUNT(*) FROM journal WHERE code_tournee = :codeTournee")
    fun nombre(codeTournee: String): Int

    /** Dernière ligne écrite par cet appareil (ordre d'insertion), repère du saut d'horloge. */
    @Query("SELECT * FROM journal WHERE appareil = :appareil ORDER BY sequence DESC LIMIT 1")
    fun derniereDe(appareil: String): LigneEntity?

    /** Première ligne de la tournée dans l'ordre d'insertion, repère de la cadence avant toute sauvegarde. */
    @Query("SELECT * FROM journal WHERE code_tournee = :codeTournee ORDER BY rowid LIMIT 1")
    fun premiereInseree(codeTournee: String): LigneEntity?
}

/** Refus en ajout seul : insertion et lecture. */
@Dao
interface RefusJournalDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun inserer(refus: RefusEntity)

    @Query("SELECT * FROM refus_journal WHERE uuid = :uuid ORDER BY id")
    fun parUuid(uuid: String): List<RefusEntity>

    @Query("SELECT * FROM refus_journal ORDER BY id")
    fun tous(): List<RefusEntity>
}

/** Sauvegardes écrites, en ajout seul : insertion et lecture. */
@Dao
interface SauvegardeDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun inserer(sauvegarde: SauvegardeEntity)

    @Query("SELECT * FROM sauvegardes WHERE code_tournee = :codeTournee ORDER BY id DESC LIMIT 1")
    fun derniere(codeTournee: String): SauvegardeEntity?

    @Query("SELECT * FROM sauvegardes ORDER BY id")
    fun toutes(): List<SauvegardeEntity>
}

/** La ligne unique de l'appareil : seule table mise à jour, elle ne porte aucune saisie. */
@Dao
interface AppareilDao {
    @Query("SELECT * FROM appareil WHERE id = 1")
    fun lire(): AppareilEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun creer(appareil: AppareilEntity)

    @Query("UPDATE appareil SET prochaine_sequence = :prochaine WHERE id = 1")
    fun avancer(prochaine: Long)
}
