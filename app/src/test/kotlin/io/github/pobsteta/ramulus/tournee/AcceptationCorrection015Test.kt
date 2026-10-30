package io.github.pobsteta.ramulus.tournee

import io.github.pobsteta.ramulus.core.deperis.Abaque
import io.github.pobsteta.ramulus.core.deperis.Note
import io.github.pobsteta.ramulus.core.journal.TypeLigne
import io.github.pobsteta.ramulus.core.photo.Empreinte
import org.junit.Rule
import java.io.File
import java.util.zip.ZipFile
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Mission 020 : tests d'acceptation de la mission 015 (corriger un arbre ou un centre par remplacement), écrits depuis
 * la fiche 015 (« Décisions qui s'imposent »), la spec et les formats, sans lire le code de 015. Règles 1 à 5 et 7 de la
 * fiche 020 ; la règle 6 (union de deux opérateurs) est dans [DoublonApresUnionTest], verte dès maintenant.
 *
 * ROUGES sur `main` 00d8c24 tant que 015 n'est pas fusionnée : sautés par défaut (voir [ActiveeParLaMission015]) ;
 * `RAMULUS_CORRECTION_EXIGER=1 ./gradlew :app:testFumeeUnitTest --rerun --tests '*AcceptationCorrection015Test'`
 * pour les voir échouer.
 */
class AcceptationCorrection015Test {
    @get:Rule
    val activation = ActiveeParLaMission015()

    private lateinit var tmp: File
    private lateinit var banc: Banc

    @BeforeTest
    fun preparer() {
        tmp = Banc.dossierTemporaire()
        banc = Banc(tmp)
    }

    @AfterTest
    fun nettoyer() {
        tmp.deleteRecursively()
    }

    /** P001 : le centre, puis quatre arbres notés (le cas du terrain de la fiche 015). */
    private fun placetteNotee(photosAuRang2: Int = 0): ControleurTournee {
        val c = banc.ouvrir("A1-tournee-depart")
        c.choisirPlacette("P001")
        banc.releverCentre("P001", 3.0, -2.0)
        c.versArbres("P001")
        banc.noter("Chêne sessile", 0, 0)
        banc.noter("Hêtre", 2, 2, photos = photosAuRang2)
        banc.noter("Chêne sessile", 5, 5)
        banc.noter("Frêne", 1, 4)
        return c
    }

    // --- Règle 1 : une seule note par plot_id et rang, de nouvel uuid, avec les nouvelles notes -------------------

    @Test
    fun `regle 1 - un arbre corrige laisse une seule note pour ce rang, d'un nouvel uuid, avec les nouvelles notes`() {
        placetteNotee()
        val avant = banc.note("P001", 2).uneSeule()
        banc.corriger(rang = 2, mb = 4)

        val apres = banc.note("P001", 2)
        assertEquals(1, apres.size, "une seule note pour P001 rang 2 dans la copie de travail (pas de seconde ligne)")
        assertNotEquals(avant["uuid"].asString, apres.uneSeule()["uuid"].asString, "la note corrigée a un nouvel uuid")
        assertEquals(4, apres.uneSeule()["mb"].asInt)
        assertEquals(2, apres.uneSeule()["second"].asInt)
        assertEquals(Abaque.classe(4, 2).name, apres.uneSeule()["classe"].asString, "la classe est recalculée par l'abaque")
        assertEquals("P001", apres.uneSeule()["plot_id"].asString)
        assertEquals(2, apres.uneSeule()["rang"].asInt)
    }

    @Test
    fun `regle 1 - les autres arbres de la placette ne bougent pas`() {
        placetteNotee()
        val autres = listOf(1, 3, 4).map { banc.note("P001", it).uneSeule() }
        banc.corriger(rang = 2, mb = 4)
        assertEquals(4, banc.notes("P001").size, "quatre notes en tout, comme sur le terrain de la fiche 015")
        assertEquals(autres.map { it["uuid"].asString }, listOf(1, 3, 4).map { banc.note("P001", it).uneSeule()["uuid"].asString })
    }

    @Test
    fun `regle 1 - l'ecran relit la tournee corrigee, une ligne pour le rang, rang suivant inchange`() {
        val c = placetteNotee()
        banc.corriger(rang = 2, mb = 4)
        val p = c.etat.tournee!!.placette("P001")!!
        assertEquals(listOf(1, 2, 3, 4), p.arbres.map { it.rang }.sorted(), "ni ligne en double, ni ligne perdue")
        assertEquals(4, p.arbres.first { it.rang == 2 }.mb)
        assertTrue(p.complete)
    }

    @Test
    fun `regle 1 - corriger deux fois le meme arbre laisse encore une seule note, la derniere`() {
        placetteNotee()
        banc.corriger(rang = 2, mb = 4)
        val milieu = banc.note("P001", 2).uneSeule()["uuid"].asString
        banc.corriger(rang = 2, mb = 3)
        val fin = banc.note("P001", 2).uneSeule()
        assertEquals(3, fin["mb"].asInt)
        assertNotEquals(milieu, fin["uuid"].asString)
        assertEquals(4, banc.notes("P001").size)
    }

    @Test
    fun `regle 1 - en contre-notation la correction remplace dans contre_notations`() {
        val c = banc.ouvrir("A2-tournee-contre-notation")
        c.choisirPlacette("P002")
        c.versArbres("P002")
        banc.noter("Épicéa commun", 2, 3)
        val avant = banc.note("P002", 1, "contre_notations").uneSeule()
        banc.corriger(rang = 1, mb = 4)
        val apres = banc.note("P002", 1, "contre_notations")
        assertEquals(1, apres.size)
        assertNotEquals(avant["uuid"].asString, apres.uneSeule()["uuid"].asString)
        assertEquals(4, apres.uneSeule()["mb"].asInt)
        // Le journal : deux contre_notation, la seconde remplace la première (règle 2 en contre-notation).
        val lignes = banc.journal.deType(TypeLigne.CONTRE_NOTATION)
        assertEquals(2, lignes.size)
        assertEquals(lignes[0].uuid, lignes[1].remplaceUuid, "remplace_uuid = uuid de la LIGNE du journal remplacée (ramsync-v1)")
        assertTrue(banc.journal.deType(TypeLigne.NOTATION).isEmpty(), "jamais une première note dans une contre-notation")
    }

    // --- Règle 2 : le journal garde les deux lignes, la seconde remplace la première, rien n'est modifié ----------

    @Test
    fun `regle 2 - le journal porte deux lignes pour l'arbre, la seconde avec remplace_uuid = uuid de la premiere`() {
        placetteNotee()
        val avant = banc.note("P001", 2).uneSeule()["uuid"].asString
        banc.corriger(rang = 2, mb = 4)
        val apres = banc.note("P001", 2).first { it["uuid"].asString != avant }["uuid"].asString

        val notations = banc.journal.deType(TypeLigne.NOTATION)
        assertEquals(5, notations.size, "quatre notes d'origine + la correction : toutes gardées")
        val duRang2 = notations.filter { it.valeur.orEmpty().contains("\"rang\":2") }
        assertEquals(2, duRang2.size, "deux lignes pour cet arbre")
        val (premiere, seconde) = duRang2
        assertEquals(avant, premiere.objetUuid)
        assertNull(premiere.remplaceUuid, "la première ligne ne remplace rien")
        assertEquals(apres, seconde.objetUuid, "la seconde est celle de la nouvelle note")
        assertEquals(premiere.uuid, seconde.remplaceUuid, "remplace_uuid = uuid de la LIGNE du journal remplacée, pas de la note (ramsync-v1)")
        assertTrue(duRang2.all { it.operateur == "operateur-A" })
    }

    @Test
    fun `regle 2 - le journal reste en ajout seul, les lignes d'avant la correction sont intactes et dans le meme ordre`() {
        placetteNotee()
        val instantane = banc.journal.lignes.toList()
        banc.corriger(rang = 2, mb = 4)
        val apres = banc.journal.lignes.toList()
        assertEquals(instantane, apres.take(instantane.size), "aucune ligne modifiée, supprimée ou réordonnée")
        assertEquals(instantane.size + 1, apres.size, "une ligne de plus, la correction (pas de photo ici)")
        assertEquals(TypeLigne.NOTATION, apres.last().type)
    }

    @Test
    fun `regle 2 - le port du journal n'offre aucune operation de modification ni de suppression`() {
        val interdits = Regex("(?i)(modifi|supprim|efface|delete|update|remove|effacer)")
        val offertes = banc.journal.methodesDuPort.filter { it !in setOf("equals", "hashCode", "toString", "getClass", "notify", "notifyAll", "wait") }
        assertTrue(offertes.none { interdits.containsMatchIn(it) }, "le journal est en ajout seul (ADR 0005) : $offertes")
    }

    @Test
    fun `regle 2 - une premiere saisie ne remplace rien`() {
        placetteNotee()
        banc.journal.lignes.forEach { assertNull(it.remplaceUuid, "ligne ${it.type} ${it.objetUuid}") }
    }

    // --- Règle 3 : les photos passent à la note corrigée ---------------------------------------------------------

    @Test
    fun `regle 3 - les photos de la note d'origine sont citees par la note corrigee, renommees, meme SHA-256`() {
        placetteNotee(photosAuRang2 = 2)
        val avant = banc.note("P001", 2).uneSeule()
        val avantUuid = avant["uuid"].asString
        val citeesAvant = avant.photosCitees()
        assertEquals(2, citeesAvant.size)

        banc.corriger(rang = 2, mb = 4) // l'opérateur ne reprend aucune photo

        val apres = banc.note("P001", 2).first { it["uuid"].asString != avantUuid }
        val nouvelUuid = apres["uuid"].asString
        assertEquals(
            listOf("photos/${nouvelUuid}_1.jpg", "photos/${nouvelUuid}_2.jpg"),
            apres.photosCitees().map { it.first },
            "citées sous le nouvel uuid, numérotées à partir de 1",
        )
        assertEquals(citeesAvant.map { it.second }, apres.photosCitees().map { it.second }, "même SHA-256, dans le même ordre")
        // Les fichiers de la copie de travail : renommés (les anciens noms ont disparu), contenu identique.
        assertEquals(
            setOf("${nouvelUuid}_1.jpg", "${nouvelUuid}_2.jpg"),
            banc.dossierPhotos.listFiles { f -> f.name.endsWith(".jpg") }.orEmpty().map { it.name }.toSet(),
            "plus aucun fichier sous l'ancien uuid $avantUuid",
        )
        apres.photosCitees().forEach { (nom, sha) ->
            assertEquals(sha, Empreinte.sha256(File(banc.racinePhotos, nom).readBytes()), "le fichier $nom a l'empreinte citée")
        }
    }

    @Test
    fun `regle 3 - le paquet rendu apres correction ne leve ni photo_absente, ni empreinte_photo_differente, ni photo_non_citee`() {
        placetteNotee(photosAuRang2 = 2)
        banc.corriger(rang = 2, mb = 4)
        // Sans cette condition le test passerait à vide : l'ancienne note, seule gardienne des photos, les citerait encore.
        assertEquals(2, banc.note("P001", 2).uneSeule().photosCitees().size, "la note corrigée, seule pour ce rang, cite les 2 photos")

        val verdict = banc.rendreDirect()
        val motifs = banc.motifs(verdict)
        assertNotEquals("refuse", verdict["verdict"].asString, "le rendu n'est pas refusé : $motifs")
        for (interdit in listOf("photo_absente", "empreinte_photo_differente", "photo_non_citee")) {
            assertFalse(interdit in motifs, "$interdit levé après une correction : $motifs")
        }
    }

    @Test
    fun `regle 3 - le journal porte une nouvelle ligne photo par vue reportee, qui remplace la ligne photo de meme numero`() {
        placetteNotee(photosAuRang2 = 2)
        val ancienUuid = banc.note("P001", 2).uneSeule()["uuid"].asString
        val anciennes = banc.journal.deType(TypeLigne.PHOTO)
        assertEquals(2, anciennes.size)
        banc.corriger(rang = 2, mb = 4)
        val nouvelUuid = banc.note("P001", 2).first { it["uuid"].asString != ancienUuid }["uuid"].asString

        val toutes = banc.journal.deType(TypeLigne.PHOTO)
        assertEquals(4, toutes.size, "deux lignes photo d'origine gardées + une nouvelle par vue reportée")
        assertEquals(anciennes, toutes.take(2), "les lignes photo d'origine sont intactes")
        val nouvelles = toutes.drop(2)
        for (n in 1..2) {
            val ancienne = anciennes.first { it.valeur.orEmpty().contains("${ancienUuid}_$n.jpg") }
            val nouvelle = nouvelles.first { it.valeur.orEmpty().contains("${nouvelUuid}_$n.jpg") }
            assertEquals(ancienne.uuid, nouvelle.remplaceUuid, "la vue $n remplace la ligne photo de même numéro")
            assertTrue(nouvelle.valeur.orEmpty().contains(nouvelUuid), "note_uuid = la nouvelle note")
        }
    }

    @Test
    fun `regle 3 - le zip rendu par l'ecran porte les photos sous le nouvel uuid, et seulement elles`() {
        val c = placetteNotee(photosAuRang2 = 2)
        val ancienUuid = banc.note("P001", 2).uneSeule()["uuid"].asString
        banc.corriger(rang = 2, mb = 4)
        val nouvelUuid = banc.note("P001", 2).first { it["uuid"].asString != ancienUuid }["uuid"].asString

        c.versRendu()
        c.rendre()
        val r = c.etat.rendu!!
        assertFalse(r.refuse, r.toString())
        val zip = File(banc.export.dossier, r.nomZip!!)
        val photos = ZipFile(zip).use { z -> z.entries().asSequence().map { it.name }.filter { it.startsWith("photos/") && !it.endsWith("/") }.toSet() }
        assertEquals(setOf("photos/${nouvelUuid}_1.jpg", "photos/${nouvelUuid}_2.jpg"), photos)
    }

    // --- Règle 4 : le centre ------------------------------------------------------------------------------------

    @Test
    fun `regle 4 - un centre releve deux fois laisse une seule ligne centres pour la placette, a la seconde position`() {
        val c = banc.ouvrir("A1-tournee-depart")
        c.choisirPlacette("P001")
        val prevu = c.etat.tournee!!.placette("P001")!!.prevu
        banc.releverCentre("P001", 3.0, -2.0)
        val premier = banc.centres("P001").uneSeule()
        banc.releverCentre("P001", 5.0, 4.0)

        val centres = banc.centres("P001")
        assertEquals(1, centres.size, "au plus un centre par placette dans la copie de travail")
        val geom = centres.uneSeule().getAsJsonObject("geom")
        assertEquals(prevu.xMetres + 5.0, geom["x"].asDouble, 0.01, "la seconde position (x, Lambert 93)")
        assertEquals(prevu.yMetres + 4.0, geom["y"].asDouble, 0.01, "la seconde position (y, Lambert 93)")
        assertNotEquals(premier["uuid"].asString, centres.uneSeule()["uuid"].asString, "nouvel uuid")
        val lu = c.etat.tournee!!.placette("P001")!!.centre!!
        assertEquals(prevu.xMetres + 5.0, lu.x, 0.01, "l'écran relit le centre remplaçant")
        assertEquals(prevu.yMetres + 4.0, lu.y, 0.01)
    }

    @Test
    fun `regle 4 - le journal a deux lignes centre, la seconde avec remplace_uuid`() {
        val c = banc.ouvrir("A1-tournee-depart")
        c.choisirPlacette("P001")
        banc.releverCentre("P001", 3.0, -2.0)
        val premier = banc.centres("P001").uneSeule()["uuid"].asString
        banc.releverCentre("P001", 5.0, 4.0)
        val second = banc.centres("P001").first { it["uuid"].asString != premier }["uuid"].asString

        val lignes = banc.journal.deType(TypeLigne.CENTRE)
        assertEquals(2, lignes.size)
        assertEquals(premier, lignes[0].objetUuid)
        assertNull(lignes[0].remplaceUuid)
        assertEquals(second, lignes[1].objetUuid)
        assertEquals(lignes[0].uuid, lignes[1].remplaceUuid, "remplace_uuid = uuid de la ligne centre remplacée")
    }

    @Test
    fun `regle 4 - relever de nouveau le centre d'une placette ne touche pas le centre d'une autre`() {
        val c = banc.ouvrir("A1-tournee-depart")
        c.choisirPlacette("P002")
        banc.releverCentre("P002", 1.0, 1.0)
        val autre = banc.centres("P002").uneSeule()["uuid"].asString
        c.retour()
        c.choisirPlacette("P001")
        banc.releverCentre("P001", 3.0, -2.0)
        banc.releverCentre("P001", 5.0, 4.0)
        assertEquals(1, banc.centres("P001").size)
        assertEquals(autre, banc.centres("P002").uneSeule()["uuid"].asString, "P002 intacte")
        assertEquals(1, banc.journal.deType(TypeLigne.CENTRE).count { it.remplaceUuid == null && it.objetUuid == autre })
    }

    // --- Règle 5 : pas de faux doublon_arbre -------------------------------------------------------------------

    @Test
    fun `regle 5 - le rendu d'une placette dont un arbre a ete corrige ne signale aucun doublon_arbre`() {
        placetteNotee()
        banc.corriger(rang = 2, mb = 4)
        val verdict = banc.rendreDirect()
        val motifs = banc.motifs(verdict)
        assertFalse("doublon_arbre" in motifs, "une correction n'est pas un doublon : $motifs")
        assertNotEquals("refuse", verdict["verdict"].asString, motifs.toString())
    }

    @Test
    fun `regle 5 - le rendu par l'ecran d'un arbre corrige n'affiche pas de doublon`() {
        val c = placetteNotee()
        banc.corriger(rang = 2, mb = 4)
        c.versRendu()
        c.rendre()
        val r = c.etat.rendu!!
        assertFalse(r.refuse, r.toString())
        assertTrue(r.messages.none { it.contains("doublon_arbre") }, r.messages.toString())
        assertTrue(r.motifs.none { it.contains("même arbre", ignoreCase = true) || it.contains("doublon", ignoreCase = true) }, r.motifs.toString())
    }

    // --- Règle 7 : la marque « rendue » ------------------------------------------------------------------------

    @Test
    fun `regle 7 - une correction apres le rendu retire la marque rendue et ne laisse qu'une note`() {
        // Le rendu est celui de l'échange, mais le verdict est forcé « accepté » : on teste la marque, pas le contrôle.
        val rendu = object : Echange by EchangeReel {
            override fun rendre(gpkg: File, depart: File, racinePhotos: File, operateur: String, versionRamulus: String, sortie: File): String {
                sortie.writeText("zip")
                return """{"verdict":"accepte","motifs":[],"messages":[]}"""
            }
        }
        val b = Banc(tmp, echange = rendu)
        val c = b.ouvrir("A1-tournee-depart")
        c.choisirPlacette("P001")
        c.versArbres("P001")
        b.noter("Chêne sessile", 0, 0)
        b.noter("Hêtre", 2, 2)
        c.rendre()
        assertTrue(b.marqueRendue.exists(), "la tournée est rendue")
        c.reprendre(c.etat.tournee!!.code)
        c.choisirPlacette("P001")
        c.versArbres("P001")
        assertTrue(b.marqueRendue.exists(), "reprendre et naviguer n'écrivent rien : la marque tient")

        b.corriger(rang = 2, mb = 4)

        assertFalse(b.marqueRendue.exists(), "la correction a retiré la marque rendue")
        assertEquals(1, b.note("P001", 2).size, "et la copie de travail n'a qu'une note pour ce rang")
        assertEquals(4, b.note("P001", 2).uneSeule()["mb"].asInt)
    }

    @Test
    fun `regle 7 - un centre relevé de nouveau apres le rendu retire aussi la marque`() {
        val rendu = object : Echange by EchangeReel {
            override fun rendre(gpkg: File, depart: File, racinePhotos: File, operateur: String, versionRamulus: String, sortie: File): String {
                sortie.writeText("zip")
                return """{"verdict":"accepte","motifs":[],"messages":[]}"""
            }
        }
        val b = Banc(tmp, echange = rendu)
        val c = b.ouvrir("A1-tournee-depart")
        c.choisirPlacette("P001")
        b.releverCentre("P001", 3.0, -2.0)
        c.rendre()
        assertTrue(b.marqueRendue.exists())
        b.releverCentre("P001", 5.0, 4.0)
        assertFalse(b.marqueRendue.exists())
        assertEquals(1, b.centres("P001").size)
    }

    // --- Garde-fous communs à toutes les règles ------------------------------------------------------------------

    @Test
    fun `une correction a Note egale est quand meme un remplacement, nouvel uuid, une seule ligne`() {
        val c = placetteNotee()
        val avant = banc.note("P001", 2).uneSeule()["uuid"].asString
        c.rouvrirArbre(2)
        c.choisirMb(Note(2)) // inchangé
        c.enregistrerArbre()
        val apres = banc.note("P001", 2)
        assertEquals(1, apres.size)
        assertNotEquals(avant, apres.uneSeule()["uuid"].asString)
    }

    @Test
    fun `la copie de depart n'est jamais ecrite par une correction`() {
        placetteNotee(photosAuRang2 = 2)
        val avant = banc.depart.readBytes().toList()
        banc.corriger(rang = 2, mb = 4)
        banc.releverCentre("P001", 5.0, 4.0)
        assertEquals(avant, banc.depart.readBytes().toList())
    }
}
