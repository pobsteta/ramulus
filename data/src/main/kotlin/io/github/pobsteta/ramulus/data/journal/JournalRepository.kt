package io.github.pobsteta.ramulus.data.journal

import io.github.pobsteta.ramulus.core.journal.LigneJournal
import io.github.pobsteta.ramulus.core.journal.Temps
import io.github.pobsteta.ramulus.core.journal.TypeLigne
import io.github.pobsteta.ramulus.core.journal.UuidV7
import io.github.pobsteta.ramulus.core.sauvegarde.LectureRamsync
import io.github.pobsteta.ramulus.core.sauvegarde.ResultatLecture
import java.security.SecureRandom

/** Bilan d'une restauration : ce que dit la lecture du fichier, et ce que l'union a fait. */
data class ResultatRestauration(
    val lecture: ResultatLecture,
    val ajoutees: Int,
    val ignorees: Int,
    /** Lignes dont l'`uuid` existait déjà avec un autre contenu : gardées au refus, le journal local inchangé. */
    val refusees: Int,
)

/**
 * Le journal des saisies (ADR 0005). Méthodes bloquantes : à appeler hors du fil principal.
 *
 * Une saisie = une transaction. La ligne n'est jamais modifiée ni effacée ; une correction est une nouvelle ligne
 * qui `remplace` l'ancienne, une annulation aussi.
 */
class JournalRepository(
    private val db: RamulusDatabase,
    private val horloges: Horloges,
    private val uuids: UuidV7 = UuidV7(),
    private val aleatoire: SecureRandom = SecureRandom(),
) {
    /** Identifiant opaque de cette installation (8 caractères hexadécimaux), tiré à la première saisie. */
    fun appareil(): String = db.runInTransaction<String> { appareilOuCreer().identifiant }

    /**
     * Ajoute une saisie au journal et la rend. Refuse un code de tournée vide (Surculus ADR 0005, l. 47), un type
     * hors de la liste ou `saut_horloge` (que seul le journal écrit), une annulation sans `remplaceUuid`.
     *
     * Si l'horloge du téléphone a sauté de plus de 60 s depuis la saisie précédente de cet appareil dans le même
     * démarrage, une ligne `saut_horloge` est d'abord ajoutée (règle « Temps ») ; l'heure brute n'est pas réécrite.
     */
    fun saisir(
        codeTournee: String,
        objetUuid: String,
        type: String,
        valeur: String?,
        operateur: String,
        remplaceUuid: String? = null,
        heureGnssMs: Long? = null,
        ageFixMs: Long? = null,
    ): LigneJournal {
        require(codeTournee.isNotBlank()) { "code de tournée vide : saisie refusée" }
        require(objetUuid.isNotBlank()) { "objet sans uuid : saisie refusée" }
        require(type in TypeLigne.TOUS && type != TypeLigne.SAUT_HORLOGE) { "type hors de la liste : $type" }
        require(type != TypeLigne.ANNULATION || remplaceUuid != null) { "une annulation désigne la ligne annulée" }
        return db.runInTransaction<LigneJournal> {
            val appareil = appareilOuCreer()
            var sequence = appareil.prochaineSequence
            val utc = horloges.utcMs()
            val mono = horloges.monotoneMs()
            val demarrage = horloges.demarrage()

            val precedente = db.journal().derniereDe(appareil.identifiant)
            val ecart = precedente?.let {
                Temps.ecartHorlogeMs(it.saisiLeUtcMs, it.monotoneMs, it.demarrage, utc, mono, demarrage)
            }
            if (Temps.estSaut(ecart)) {
                val uuidSaut = uuids.tirer(utc.coerceAtLeast(0))
                db.journal().inserer(LigneEntity(
                    uuid = uuidSaut, objetUuid = uuidSaut, type = TypeLigne.SAUT_HORLOGE, valeur = null,
                    remplaceUuid = null, operateur = operateur, appareil = appareil.identifiant, sequence = sequence++,
                    saisiLeUtcMs = utc, heureGnssMs = heureGnssMs, monotoneMs = mono, demarrage = demarrage,
                    ageFixMs = ageFixMs, codeTournee = codeTournee,
                ))
            }
            val ligne = LigneJournal(
                uuid = uuids.tirer(utc.coerceAtLeast(0)), objetUuid = objetUuid, type = type, valeur = valeur,
                remplaceUuid = remplaceUuid, operateur = operateur, appareil = appareil.identifiant,
                sequence = sequence++, saisiLeUtcMs = utc, heureGnssMs = heureGnssMs, monotoneMs = mono,
                demarrage = demarrage, ageFixMs = ageFixMs, codeTournee = codeTournee,
            )
            db.journal().inserer(LigneEntity.de(ligne))
            db.appareil().avancer(sequence)
            ligne
        }
    }

    /** Les lignes d'une tournée, dans l'ordre total. */
    fun lignes(codeTournee: String): List<LigneJournal> = db.journal().deLaTournee(codeTournee).map { it.versLigne() }

    /** Tout le journal, dans l'ordre total. */
    fun toutes(): List<LigneJournal> = db.journal().toutes().map { it.versLigne() }

    /** Les lignes gardées au refus, dans leur ordre d'arrivée. */
    fun refus(): List<RefusEntity> = db.refus().tous()

    /**
     * Restaure un `.ramsync` par **union sur l'`uuid`** (sursync-v1, « Restauration ») : une ligne absente est
     * ajoutée telle quelle (son `appareil` et sa `sequence` d'origine, la tournée de l'en-tête) ; une ligne déjà
     * présente à l'identique est ignorée ; une ligne présente avec un autre contenu part au refus
     * (`uuid_contenu_different`), une seule fois ; les lignes locales restent. Idempotente. Un fichier refusé en entier
     * (version) ne touche à rien.
     */
    fun restaurer(octets: ByteArray): ResultatRestauration {
        val lecture = LectureRamsync.lire(octets)
        if (!lecture.accepte) return ResultatRestauration(lecture, 0, 0, 0)
        return db.runInTransaction<ResultatRestauration> {
            var ajoutees = 0
            var ignorees = 0
            var refusees = 0
            val recuLe = horloges.utcMs()
            for (l in lecture.lignes) {
                val locale = db.journal().parUuid(l.uuid)?.versLigne()
                when {
                    locale == null -> { db.journal().inserer(LigneEntity.de(l)); ajoutees++ }
                    locale == l -> ignorees++
                    else -> {
                        refusees++
                        if (db.refus().parUuid(l.uuid).none { it.versLigne() == l }) {
                            db.refus().inserer(RefusEntity.de(l, RefusEntity.UUID_CONTENU_DIFFERENT, recuLe))
                        }
                    }
                }
            }
            ResultatRestauration(lecture, ajoutees, ignorees, refusees)
        }
    }

    private fun appareilOuCreer(): AppareilEntity =
        db.appareil().lire() ?: AppareilEntity(
            identifiant = "%08x".format(aleatoire.nextInt()),
            prochaineSequence = 1,
        ).also { db.appareil().creer(it) }
}
