package io.github.pobsteta.ramulus.core.journal

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.Random

/** Fabrique de lignes pour les tests : seuls les champs utiles au cas sont donnés. */
internal fun ligne(
    uuid: String,
    objet: String = "OBJ1",
    type: String = TypeLigne.NOTATION,
    valeur: String? = "{\"mb\":1}",
    remplace: String? = null,
    operateur: String = "op1",
    appareil: String = "a3f9c2d1",
    sequence: Long = 1,
    saisiLe: Long = 1_800_000_000_000,
    heureGnss: Long? = null,
    monotone: Long = 1_000,
    demarrage: Long = 1,
    ageFix: Long? = null,
    tournee: String = "DEPERIS-21-2027",
) = LigneJournal(
    uuid, objet, type, valeur, remplace, operateur, appareil, sequence, saisiLe, heureGnss, monotone, demarrage,
    ageFix, tournee,
)

class JournalTest {

    // 1
    @Test
    fun `un UUID v7 est conforme, porte son instant et croit strictement`() {
        val gen = UuidV7(Random(42))
        val t0 = 1_800_000_000_000L
        // Même milliseconde, puis horloge qui recule, puis qui avance.
        val instants = List(5000) { t0 } + List(10) { t0 - 10_000 } + List(10) { i -> t0 + 1 + i }
        val uuids = instants.map { gen.tirer(it) }
        uuids.forEach { assertTrue(UuidV7.estValide(it), it) }
        assertEquals(uuids.sorted(), uuids, "ordre lexicographique = ordre de tirage")
        assertEquals(uuids.size, uuids.toSet().size)
        assertEquals(t0, UuidV7.instantMs(uuids.first()))
        assertTrue(uuids.all { it == it.lowercase() })
        // Variante RFC 9562 : les deux bits de poids fort de l'octet 8 valent 10.
        assertTrue(uuids.all { it[19] in "89ab" })
        assertFalse(UuidV7.estValide("0190f5e2-8c4a-4b1e-9f00-000000000000"), "un v4 n'est pas un v7")
    }

    // 2
    @Test
    fun `l ordre total ne depend pas de l ordre d arrivee`() {
        val lignes = listOf(
            ligne("u3", saisiLe = 3), ligne("u1", saisiLe = 1), ligne("u2", saisiLe = 2),
            ligne("u4", saisiLe = 2, appareil = "b0000000"), ligne("u5", saisiLe = 2, sequence = 0),
        )
        val attendu = OrdreTotal.trier(lignes)
        repeat(20) { graine ->
            assertEquals(attendu, OrdreTotal.trier(lignes.shuffled(Random(graine.toLong()))))
        }
    }

    // 3
    @Test
    fun `departage dans l ordre instant, appareil, sequence, uuid`() {
        val base = ligne("m", saisiLe = 10, appareil = "b0000000", sequence = 5)
        val parInstant = base.copy(uuid = "z", saisiLeUtcMs = 9)
        assertTrue(OrdreTotal.compare(parInstant, base) < 0, "instant d'abord, même si l'uuid est plus grand")
        val parAppareil = base.copy(uuid = "z", appareil = "a0000000", sequence = 99)
        assertTrue(OrdreTotal.compare(parAppareil, base) < 0, "puis l'appareil, avant la séquence")
        val parSequence = base.copy(uuid = "z", sequence = 4)
        assertTrue(OrdreTotal.compare(parSequence, base) < 0, "puis la séquence, avant l'uuid")
        val parUuid = base.copy(uuid = "a")
        assertTrue(OrdreTotal.compare(parUuid, base) < 0, "enfin l'uuid")
        assertEquals(0, OrdreTotal.compare(base, base.copy()))
    }

    // 4
    @Test
    fun `deux corrections de la meme ligne font un conflit et les deux lignes restent`() {
        val a = ligne("A", saisiLe = 1)
        val b = ligne("B", saisiLe = 2, remplace = "A", appareil = "a0000001")
        val c = ligne("C", saisiLe = 3, remplace = "A", appareil = "a0000002")
        val journal = listOf(c, a, b)
        val conflits = Conflits.detecter(journal)
        assertEquals(listOf(Conflit("A", listOf(b, c))), conflits)
        assertEquals(3, journal.size, "rien n'est retiré du journal")
        val etat = Projection.calculer(journal).values.single()
        assertTrue(etat.enConflit)
        assertNull(etat.courante, "aucun choix en silence")
        assertEquals(listOf(b, c), etat.tetes)
    }

    // 5
    @Test
    fun `une correction de correction n est pas un conflit`() {
        val a = ligne("A", saisiLe = 1)
        val b = ligne("B", saisiLe = 2, remplace = "A")
        val c = ligne("C", saisiLe = 3, remplace = "B")
        assertTrue(Conflits.detecter(listOf(a, b, c)).isEmpty())
        val etat = Projection.calculer(listOf(a, b, c)).values.single()
        assertFalse(etat.enConflit)
        assertEquals(c, etat.courante)
    }

    // 6
    @Test
    fun `annulation et correction concurrentes de la meme ligne font un conflit`() {
        val a = ligne("A", saisiLe = 1)
        val annul = ligne("X", saisiLe = 2, type = TypeLigne.ANNULATION, valeur = null, remplace = "A")
        val corr = ligne("B", saisiLe = 3, remplace = "A", appareil = "a0000002")
        val conflits = Conflits.detecter(listOf(a, annul, corr))
        assertEquals(1, conflits.size)
        assertEquals(listOf(annul, corr), conflits.single().lignes)
        val etat = Projection.calculer(listOf(a, annul, corr)).values.single()
        assertTrue(etat.enConflit, "la projection ne choisit pas la correction en silence")
        assertNull(etat.courante)
    }

    @Test
    fun `une annulation seule retire l etat, une autre tournee ou un autre type n influent pas`() {
        val a = ligne("A", saisiLe = 1)
        val annul = ligne("X", saisiLe = 2, type = TypeLigne.ANNULATION, valeur = null, remplace = "A")
        assertTrue(Projection.calculer(listOf(a, annul)).isEmpty())

        val autre = ligne("B", saisiLe = 3, tournee = "AUTRE", remplace = "A")
        val photo = ligne("P", saisiLe = 4, type = TypeLigne.PHOTO)
        val etats = Projection.calculer(listOf(a, autre, photo))
        assertEquals(a, etats[CleEtat("DEPERIS-21-2027", "OBJ1", TypeLigne.NOTATION)]!!.courante,
            "un remplace_uuid venu d'une autre tournée est sans effet")
        assertEquals(photo, etats[CleEtat("DEPERIS-21-2027", "OBJ1", TypeLigne.PHOTO)]!!.courante)
        assertEquals(autre, etats[CleEtat("AUTRE", "OBJ1", TypeLigne.NOTATION)]!!.courante)
    }

    @Test
    fun `deux tetes sans remplacement sont en conflit, un saut d horloge n est pas un etat`() {
        val a = ligne("A", saisiLe = 1)
        val b = ligne("B", saisiLe = 2)
        val saut = ligne("S", objet = "S", type = TypeLigne.SAUT_HORLOGE, valeur = null)
        val etats = Projection.calculer(listOf(a, b, saut))
        assertEquals(1, etats.size)
        assertTrue(etats.values.single().enConflit)
    }

    @Test
    fun `la liste des types est fermee`() {
        assertEquals(
            setOf("notation", "contre_notation", "photo", "centre", "placette_notee", "annulation", "saut_horloge"),
            TypeLigne.TOUS,
        )
    }
}

class TempsTest {
    @Test
    fun `un saut de plus de 60 s dans un meme demarrage est detecte, pas d un demarrage a l autre`() {
        // Repère : 10:00:00 UTC, monotone 1 000 000.
        val e1 = Temps.ecartHorlogeMs(1_000_000_000L, 1_000_000, 1, 1_000_060_000L, 1_060_000, 1)
        assertEquals(0L, e1)
        assertFalse(Temps.estSaut(e1))
        val e2 = Temps.ecartHorlogeMs(1_000_000_000L, 1_000_000, 1, 1_000_000_000L + 3_600_000, 1_060_000, 1)
        assertTrue(Temps.estSaut(e2), "horloge avancée d'une heure")
        val e3 = Temps.ecartHorlogeMs(1_000_000_000L, 1_000_000, 1, 1_000_000_000L - 61_001, 1_000_000, 1)
        assertTrue(Temps.estSaut(e3), "horloge reculée")
        assertFalse(Temps.estSaut(Temps.ecartHorlogeMs(0, 0, 1, 60_000, 0, 1)), "60 s pile : pas de saut")
        assertNull(Temps.ecartHorlogeMs(0, 5_000_000, 1, 10, 20, 2))
        assertFalse(Temps.estSaut(null))
    }

    @Test
    fun `jour civil en Europe Paris, sur l heure GNSS si le fix a au plus 5 s`() {
        // 2027-07-05T22:30:00Z = 2027-07-06 00:30 à Paris (heure d'été, UTC+2).
        val tardUtc = 1_814_826_600_000L
        assertEquals(LocalDate.of(2027, 7, 6), Temps.jourCivil(ligne("u", saisiLe = tardUtc)))
        // Téléphone faux de deux jours, fix GNSS frais : le GNSS fait foi.
        val faux = tardUtc + 2 * 86_400_000L
        assertEquals(LocalDate.of(2027, 7, 6), Temps.jourCivil(ligne("u", saisiLe = faux, heureGnss = tardUtc, ageFix = 5_000)))
        assertEquals(LocalDate.of(2027, 7, 8), Temps.jourCivil(ligne("u", saisiLe = faux, heureGnss = tardUtc, ageFix = 5_001)))
        assertEquals(LocalDate.of(2027, 7, 8), Temps.jourCivil(ligne("u", saisiLe = faux, heureGnss = tardUtc, ageFix = -1)))
        assertEquals(LocalDate.of(2027, 7, 8), Temps.jourCivil(ligne("u", saisiLe = faux, heureGnss = tardUtc, ageFix = null)))
    }
}
