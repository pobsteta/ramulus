package io.github.pobsteta.ramulus.accueil

import io.github.pobsteta.ramulus.tournee.TourneeEnCours
import java.io.File
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun ligne(code: String, notees: Int = 0, sur: Int = 12, rendue: Boolean = false, nature: String = "premiere") =
    LigneAccueil(code, nature, sur, notees, rendue)

/** In-memory : la même interface que les SharedPreferences. */
class MagasinMemoire : MagasinReglages {
    private val textes = mutableMapOf<String, String>()
    private val entiers = mutableMapOf<String, Int>()
    private val longs = mutableMapOf<String, Long>()
    override fun lireTexte(cle: String) = textes[cle]
    override fun ecrireTexte(cle: String, valeur: String) { textes[cle] = valeur }
    override fun lireEntier(cle: String, parDefaut: Int) = entiers[cle] ?: parDefaut
    override fun ecrireEntier(cle: String, valeur: Int) { entiers[cle] = valeur }
    override fun lireLong(cle: String) = longs[cle]
    override fun ecrireLong(cle: String, valeur: Long) { longs[cle] = valeur }
}

class AccueilTest {
    // --- Recherche -----------------------------------------------------------------------------------------------

    @Test
    fun `la recherche vide garde tout`() {
        val l = listOf(ligne("EXEMPLE-2027-01"), ligne("FORET-A"))
        assertEquals(l, filtrer(l, ""))
        assertEquals(l, filtrer(l, "   "))
    }

    @Test
    fun `la recherche ignore la casse`() {
        val l = listOf(ligne("EXEMPLE-2027-01"), ligne("FORET-A"))
        assertEquals(listOf("EXEMPLE-2027-01"), filtrer(l, "exemple").map { it.code })
        assertEquals(listOf("EXEMPLE-2027-01"), filtrer(l, "EXEMPLE").map { it.code })
    }

    @Test
    fun `la recherche ignore les accents dans les deux sens`() {
        val l = listOf(ligne("Forêt-Vénérée"), ligne("Autre"))
        assertEquals(listOf("Forêt-Vénérée"), filtrer(l, "foret-venere").map { it.code })
        assertEquals(listOf("Forêt-Vénérée"), filtrer(l, "FORÊT").map { it.code })
        assertEquals(listOf("Bœuf"), filtrer(listOf(ligne("Bœuf")), "boeuf").map { it.code })
    }

    @Test
    fun `la recherche cherche dans le code et pas ailleurs`() {
        val l = listOf(ligne("T1", nature = "contre_notation"))
        assertTrue(filtrer(l, "contre").isEmpty())
        assertEquals(1, filtrer(l, "t1").size)
        assertTrue(filtrer(l, "zzz").isEmpty())
    }

    // --- Liste -----------------------------------------------------------------------------------------------------

    @Test
    fun `A faire reunit les tournees non rendues, Terminees les rendues, dans l'ordre d'origine`() {
        val l = listOf(ligne("A", rendue = true), ligne("B"), ligne("C", notees = 2), ligne("D", rendue = true))
        val r = repartirListe(l)
        assertEquals(listOf("B", "C"), r.aFaire.map { it.code })
        assertEquals(listOf("A", "D"), r.terminees.map { it.code })
        assertTrue(repartirListe(emptyList()).vide)
    }

    @Test
    fun `une ligne dit sa nature et son avancement`() {
        assertEquals("notation", ligne("A").natureEnClair)
        assertEquals("contre-notation", ligne("A", nature = "contre_notation").natureEnClair)
        assertEquals("3 placettes sur 12", ligne("A", notees = 3).avancement)
        assertEquals("1 placette sur 12", ligne("A", notees = 1).avancement)
        assertEquals("0 placette sur 12", ligne("A").avancement)
    }

    @Test
    fun `une tournee de l'appareil devient une ligne`() {
        val l = TourneeEnCours("T", "contre_notation", 5, 2, rendue = true).enLigne()
        assertEquals(LigneAccueil("T", "contre_notation", 5, 2, true), l)
    }

    // --- Kanban ----------------------------------------------------------------------------------------------------

    @Test
    fun `le kanban a les cinq colonnes communes, dans l'ordre de Surculus et Marculus`() {
        assertEquals(
            listOf("Proposée", "Validée", "Planifiée", "Réalisée", "Abandonnée"),
            EtatKanban.entries.map { it.libelle },
        )
        assertEquals(EtatKanban.entries.toSet(), repartir(emptyList<LigneAccueil>()) { EtatKanban.PROPOSEE }.keys)
    }

    @Test
    fun `statut minimal - rendue Realisee, une placette notee Planifiee, sinon Proposee`() {
        assertEquals(EtatKanban.REALISEE, statutMinimal(0, rendue = true))
        assertEquals(EtatKanban.REALISEE, statutMinimal(5, rendue = true))
        assertEquals(EtatKanban.PLANIFIEE, statutMinimal(1, rendue = false))
        assertEquals(EtatKanban.PROPOSEE, statutMinimal(0, rendue = false))
    }

    @Test
    fun `la colonne affichee est la plus avancee du statut garde et du minimum`() {
        assertEquals(EtatKanban.PLANIFIEE, CarteKanban(ligne("A", notees = 1), EtatKanban.PROPOSEE).colonne)
        assertEquals(EtatKanban.VALIDEE, CarteKanban(ligne("A"), EtatKanban.VALIDEE).colonne)
        assertEquals(EtatKanban.REALISEE, CarteKanban(ligne("A", rendue = true), EtatKanban.VALIDEE).colonne)
        assertEquals(EtatKanban.ABANDONNEE, CarteKanban(ligne("A", rendue = true), EtatKanban.ABANDONNEE).colonne)
    }

    @Test
    fun `un depot sous le minimum est refuse, un depot sans effet aussi`() {
        assertNull(deposer(EtatKanban.PLANIFIEE, EtatKanban.PROPOSEE, EtatKanban.PLANIFIEE))
        assertNull(deposer(EtatKanban.VALIDEE, EtatKanban.VALIDEE, EtatKanban.PROPOSEE))
        assertEquals(EtatKanban.VALIDEE, deposer(EtatKanban.PROPOSEE, EtatKanban.VALIDEE, EtatKanban.PROPOSEE))
        assertEquals(EtatKanban.REALISEE, deposer(EtatKanban.PLANIFIEE, EtatKanban.REALISEE, EtatKanban.PLANIFIEE))
    }

    @Test
    fun `le kanban repartit chaque tournee dans sa colonne`() {
        val cartes = listOf(
            CarteKanban(ligne("A"), EtatKanban.PROPOSEE),
            CarteKanban(ligne("B", notees = 1), EtatKanban.PROPOSEE),
            CarteKanban(ligne("C", rendue = true), EtatKanban.PROPOSEE),
        )
        val col = repartir(cartes) { it.colonne }
        assertEquals(listOf("A"), col.getValue(EtatKanban.PROPOSEE).map { it.ligne.code })
        assertEquals(listOf("B"), col.getValue(EtatKanban.PLANIFIEE).map { it.ligne.code })
        assertEquals(listOf("C"), col.getValue(EtatKanban.REALISEE).map { it.ligne.code })
        assertTrue(col.getValue(EtatKanban.ABANDONNEE).isEmpty())
    }

    @Test
    fun `le statut garde d'une tournee se relit, inconnu donne Proposee`() {
        val m = MagasinMemoire()
        val s = StatutsKanban(m)
        assertEquals(EtatKanban.PROPOSEE, s.statut("T"))
        s.definir("T", EtatKanban.ABANDONNEE)
        assertEquals(EtatKanban.ABANDONNEE, StatutsKanban(m).statut("T"))
        m.ecrireTexte("statut_U", "n'importe quoi")
        assertEquals(EtatKanban.PROPOSEE, s.statut("U"))
    }

    // --- Menu ------------------------------------------------------------------------------------------------------

    @Test
    fun `le menu trois points a trois entrees dans l'ordre`() {
        assertEquals(listOf("Ouvrir une tournée", "Paramètres", "À propos"), ENTREES_MENU.map { it.libelle })
    }

    // --- Réglages ----------------------------------------------------------------------------------------------------

    @Test
    fun `les reglages ont leurs valeurs par defaut`() {
        val r = ReglagesRamulus(MagasinMemoire())
        assertEquals(io.github.pobsteta.ramulus.ui.theme.ChoixTheme.SYSTEME, r.choixTheme)
        assertEquals(10, r.nbMesuresGnss)
        assertEquals(30, r.seuilEcartM)
        assertNull(r.derniereSauvegarde)
        assertEquals(10, r.nouvelleCollecte().nbCible)
    }

    @Test
    fun `les reglages s'ecrivent et se relisent`() {
        val m = MagasinMemoire()
        val r = ReglagesRamulus(m)
        r.definirTheme(io.github.pobsteta.ramulus.ui.theme.ChoixTheme.SOMBRE)
        r.definirNbMesures(15)
        r.definirSeuilEcart(45)
        val quand = Instant.parse("2026-10-29T12:05:00Z")
        r.noterSauvegarde(quand)
        val relu = ReglagesRamulus(m)
        assertEquals(io.github.pobsteta.ramulus.ui.theme.ChoixTheme.SOMBRE, relu.choixTheme)
        assertEquals(15, relu.nbMesuresGnss)
        assertEquals(45, relu.seuilEcartM)
        assertEquals(quand, relu.derniereSauvegarde)
        assertEquals(15, relu.nouvelleCollecte().nbCible)
    }

    @Test
    fun `les reglages restent dans leurs bornes`() {
        val r = ReglagesRamulus(MagasinMemoire())
        r.definirNbMesures(0)
        assertEquals(ReglagesRamulus.MESURES_MIN, r.nbMesuresGnss)
        r.definirNbMesures(10_000)
        assertEquals(ReglagesRamulus.MESURES_MAX, r.nbMesuresGnss)
        r.definirSeuilEcart(-3)
        assertEquals(ReglagesRamulus.SEUIL_MIN_M, r.seuilEcartM)
        r.definirSeuilEcart(1_000_000)
        assertEquals(ReglagesRamulus.SEUIL_MAX_M, r.seuilEcartM)
        // Une valeur absurde déjà écrite sur le téléphone est ramenée dans les bornes à la lecture.
        val m = MagasinMemoire().apply { ecrireEntier(ReglagesRamulus.CLE_MESURES, -5) }
        assertEquals(ReglagesRamulus.MESURES_MIN, ReglagesRamulus(m).nbMesuresGnss)
    }

    @Test
    fun `un theme inconnu donne Systeme`() {
        val m = MagasinMemoire().apply { ecrireTexte(ReglagesRamulus.CLE_THEME, "VIOLET") }
        assertEquals(io.github.pobsteta.ramulus.ui.theme.ChoixTheme.SYSTEME, ReglagesRamulus(m).choixTheme)
    }

    @Test
    fun `l'ecart est signale au-dela du seuil regle, strictement`() {
        val r = ReglagesRamulus(MagasinMemoire())
        assertEquals(false, r.ecart(30.0).signale) // comme 007 : exactement 30 m n'est pas signalé
        assertEquals(true, r.ecart(30.01).signale)
        r.definirSeuilEcart(50)
        assertEquals(false, r.ecart(45.0).signale)
        assertEquals(true, r.ecart(50.5).signale)
        assertEquals(45.0, r.ecart(45.0).metres)
    }

    @Test
    fun `les valeurs par defaut sont celles provisoires de 007`() {
        assertEquals(io.github.pobsteta.ramulus.core.gnss.CollecteCentre.NB_MESURES_PROVISOIRE, ReglagesRamulus.MESURES_PAR_DEFAUT)
        assertEquals(io.github.pobsteta.ramulus.core.gnss.EcartAuPointPrevu.SEUIL_PROVISOIRE_M.toInt(), ReglagesRamulus.SEUIL_PAR_DEFAUT_M)
    }

    @Test
    fun `la derniere sauvegarde se lit en heure de Paris`() {
        assertEquals("Aucune sauvegarde pour l'instant", texteDerniereSauvegarde(null))
        assertEquals("29/10/2026 à 13:05", texteDerniereSauvegarde(Instant.parse("2026-10-29T12:05:00Z")))
        assertEquals("29/01/2027 à 13:05", texteDerniereSauvegarde(Instant.parse("2027-01-29T12:05:00Z")))
    }

    @Test
    fun `Parametres montre les seuils provisoires des photos, lus aux constantes de 008`() {
        val s = seuilsPhotos().associate { it.nom to it.valeur }
        assertEquals(5, s.size)
        assertEquals("100", s.entries.first { it.key.startsWith("Flou") }.value)
        assertEquals("20 %", s.entries.first { it.key.contains("clairs") }.value)
        assertEquals("25 %", s.entries.first { it.key.contains("sombres") }.value)
        assertEquals("2048 px", s.entries.first { it.key.contains("grand côté") }.value)
        assertEquals("85", s.entries.first { it.key.contains("JPEG") }.value)
        assertTrue(s.keys.any { it.contains("230") } && s.keys.any { it.contains("60") })
    }

    // --- À propos ----------------------------------------------------------------------------------------------------

    @Test
    fun `A propos donne la version, le code, la licence et le protocole`() {
        val a = aPropos("0.3.1", 42)
        assertEquals("Ramulus", a.nom)
        assertEquals("Version 0.3.1 (code 42)", a.version)
        assertEquals("DEPERIS, DSF, fiches 2025", a.protocole)
        assertTrue(a.licence.contains("GPL"))
    }

    @Test
    fun `A propos n'invente pas de version`() {
        assertEquals("Version inconnue", aPropos(null, null).version)
        assertEquals("Version inconnue", aPropos("  ", 3).version)
        assertEquals("Version 1.0", aPropos("1.0", null).version)
    }

    @Test
    fun `la licence affichee est celle du fichier LICENSE du depot`() {
        val licence = File("../LICENSE")
        assertTrue(licence.exists(), "LICENSE absent : $licence")
        val debut = licence.readLines().take(3).joinToString(" ")
        assertTrue(debut.contains("GNU GENERAL PUBLIC LICENSE"), debut)
        assertTrue(debut.contains("Version 3"), debut)
        assertTrue(LICENCE.contains("GPL version 3"))
    }

    @Test
    fun `le lien du depot est celui de origin`() {
        assertEquals("https://github.com/pobsteta/ramulus", URL_DEPOT)
        assertTrue(URL_LICENCE.startsWith(URL_DEPOT))
    }
}
