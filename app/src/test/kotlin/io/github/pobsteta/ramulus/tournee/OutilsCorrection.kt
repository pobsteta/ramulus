package io.github.pobsteta.ramulus.tournee

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.github.pobsteta.ramulus.core.deperis.Essences
import io.github.pobsteta.ramulus.core.deperis.Note
import io.github.pobsteta.ramulus.core.echange.EchangeDeperis
import io.github.pobsteta.ramulus.core.gnss.CentreReleve
import io.github.pobsteta.ramulus.core.gnss.PointLambert93
import io.github.pobsteta.ramulus.core.journal.LigneJournal
import io.github.pobsteta.ramulus.core.journal.TypeLigne
import org.junit.Assume.assumeTrue
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.lang.reflect.Proxy
import java.nio.file.Files
import java.time.Instant
import javax.imageio.ImageIO

/**
 * Outils des tests d'acceptation de la mission 015 (correction par remplacement), écrits par la mission 020 sans lire le
 * code de 015. Tout passe par l'interface publique de `main` 00d8c24 : [ControleurTournee], [EchangeReel],
 * [EchangeDeperis], le port [Journal].
 */

/**
 * Garde les tests de 015 hors de `.claude/test-command` tant que 015 n'est pas fusionnée (fiche 020, « CI rouge
 * attendue »).
 *
 *  - par défaut, ils sont SAUTÉS (JUnit « ignoré », avec renvoi à la mission 015) ;
 *  - `RAMULUS_CORRECTION_EXIGER=1` : ils tournent et échouent sur `main`, ce qui montre qu'ils ne passent pas à vide.
 *    Gradle réutilise un résultat de test inchangé : ajouter `--rerun` (ou `cleanTestFumeeUnitTest`) ;
 *  - quand 015 est fusionnée : passer [CORRECTION_015_LIVREE] à `true` (une seule ligne, ci-dessous) ; les tests
 *    tournent alors toujours, sans autre retouche.
 */
const val CORRECTION_015_LIVREE = false

class ActiveeParLaMission015 : TestRule {
    override fun apply(base: Statement, description: Description): Statement = object : Statement() {
        override fun evaluate() {
            val exiger = System.getenv("RAMULUS_CORRECTION_EXIGER") == "1" || System.getProperty("ramulus.correction.exiger") == "true"
            assumeTrue(
                "Désactivé jusqu'à la mission 015 (correction par remplacement). Pour le voir échouer : RAMULUS_CORRECTION_EXIGER=1",
                CORRECTION_015_LIVREE || exiger,
            )
            base.evaluate()
        }
    }
}

/**
 * Un journal factice qui enregistre de vraies [LigneJournal], chacune avec son propre `uuid` de ligne, et les rend par
 * `lignes(code)` (port `Journal` de 015 : `saisir` à 5 ou 6 arguments, `remplaceUuid` désignant l'`uuid` de la LIGNE
 * remplacée, ramsync-v1). C'est un proxy dynamique pour compiler aussi sur `main`, dont le port n'a ni la version à 6
 * arguments ni `lignes`. Est une saisie tout appel à au moins 5 arguments dont le 3e est un type du journal.
 */
class JournalEnregistreur {
    val lignes = mutableListOf<LigneJournal>()
    val methodesDuPort: List<String> = Journal::class.java.methods.map { it.name }
    private var sequence = 0L

    val journal: Journal = Proxy.newProxyInstance(Journal::class.java.classLoader, arrayOf(Journal::class.java)) { _, m, args ->
        val a = args?.toList().orEmpty()
        if (a.size >= 5 && a[2] in TypeLigne.TOUS) {
            sequence++
            lignes += LigneJournal(
                uuid = java.util.UUID.randomUUID().toString(), objetUuid = a[1] as String, type = a[2] as String,
                valeur = a[3] as String, remplaceUuid = a.getOrNull(5) as? String, operateur = a[4] as String,
                appareil = "00000001", sequence = sequence, saisiLeUtcMs = sequence, heureGnssMs = null,
                monotoneMs = sequence, demarrage = 1, ageFixMs = null, codeTournee = a[0] as String,
            )
        }
        when {
            m.name == "lignes" && a.size == 1 -> lignes.filter { it.codeTournee == a[0] }
            m.returnType == java.lang.Boolean.TYPE -> false
            m.returnType == Integer.TYPE -> 0
            m.returnType == java.lang.Long.TYPE -> 0L
            List::class.java.isAssignableFrom(m.returnType) -> emptyList<Any>()
            else -> null
        }
    } as Journal

    fun deType(vararg types: String) = lignes.filter { it.type in types }
}

class StockNom(var nom: String?) : StockOperateur {
    override fun lire() = nom
    override fun ecrire(nom: String) { this.nom = nom }
}

class ExportDansDossier(val dossier: File) : ExportRendu {
    override fun exporter(zip: File, nomSouhaite: String): String {
        dossier.mkdirs()
        zip.copyTo(File(dossier, nomSouhaite), overwrite = true)
        return nomSouhaite
    }
}

/** Une tournée de l'essai : un contrôleur sur l'échange RÉEL, le journal enregistreur, et les chemins de la copie de travail. */
class Banc(
    val tmp: File,
    val operateur: String = "operateur-A",
    val maintenant: Instant = Instant.parse("2027-07-06T09:12:00Z"),
    echange: Echange = EchangeReel,
) {
    val journal = JournalEnregistreur()
    val stock = StockNom(operateur)
    val export = ExportDansDossier(File(tmp, "documents"))
    val racine = File(tmp, "tournees")

    val controleur = ControleurTournee(
        racine = racine, echange = echange, journal = journal.journal, stock = stock, export = export,
        versionRamulus = "test", horloge = { maintenant },
    ).also { it.demarrer() }

    fun ouvrir(cas: String): ControleurTournee {
        val gpkg = File("../fixtures/echange/$cas/deperis.gpkg")
        check(gpkg.exists()) { "fixture absente : $gpkg" }
        controleur.ouvrirTournee(gpkg.inputStream())
        check(controleur.etat.tournee != null) { "tournée non ouverte : ${controleur.etat.erreur}" }
        return controleur
    }

    private val code get() = controleur.etat.tournee!!.code
    val travail get() = File(racine, "$code/travail/deperis.gpkg")
    val depart get() = File(racine, "$code/depart/deperis.gpkg")
    /** Contient `photos/<uuid>_<n>.jpg` (contrat de `EchangeDeperis.rendre`). */
    val racinePhotos get() = File(racine, "$code/photos")
    val dossierPhotos get() = File(racinePhotos, "photos")
    val marqueRendue get() = File(racine, "$code/rendue")

    /** Les lignes d'une table de la copie de travail, lues par l'instantané du format. */
    fun lignes(table: String): List<JsonObject> {
        val t = JsonParser.parseString(EchangeDeperis.instantane(travail)).asJsonObject.getAsJsonObject("tables")
        return t.getAsJsonObject(table)?.getAsJsonArray("lignes")?.map { it.asJsonObject }.orEmpty()
    }

    fun notes(plot: String, table: String = "notations") = lignes(table).filter { it["plot_id"].asString == plot }
    fun note(plot: String, rang: Int, table: String = "notations") = notes(plot, table).filter { it["rang"].asInt == rang }
    fun centres(plot: String) = lignes("centres").filter { it["plot_id"].asString == plot }

    /** Rendu direct par l'échange (verdict JSON avec les codes), sans passer par l'écran. */
    fun rendreDirect(): JsonObject {
        val sortie = File(tmp, "direct/${System.nanoTime()}.zip").also { it.parentFile!!.mkdirs() }
        return JsonParser.parseString(EchangeReel.rendre(travail, depart, racinePhotos, operateur, "test", sortie)).asJsonObject
    }

    fun motifs(verdict: JsonObject): Set<String> = verdict.getAsJsonArray("motifs").map { it.asString }.toSet()

    fun noter(essence: String, mb: Int, second: Int, photos: Int = 0) {
        controleur.choisirEssence(Essences.toutes.first { it.nom == essence })
        controleur.choisirMb(Note(mb))
        controleur.choisirSecond(Note(second))
        repeat(photos) { check(controleur.ajouterPhoto(jpeg(it + 1), emptyList())) { "photo refusée" } }
        controleur.enregistrerArbre()
        check(controleur.etat.erreur == null) { controleur.etat.erreur!! }
    }

    /** Rouvre l'arbre de ce rang, change MB, enregistre. Les photos ne sont pas reprises. */
    fun corriger(rang: Int, mb: Int) {
        controleur.rouvrirArbre(rang)
        check(controleur.etat.arbre.rang == rang && controleur.etat.arbre.rouvert) { "l'arbre $rang ne s'est pas rouvert" }
        controleur.choisirMb(Note(mb))
        controleur.enregistrerArbre()
        check(controleur.etat.erreur == null) { controleur.etat.erreur!! }
    }

    fun releverCentre(plot: String, dx: Double, dy: Double) {
        val p = controleur.etat.tournee!!.placette(plot)!!
        controleur.enregistrerCentre(plot, CentreReleve(PointLambert93(p.prevu.xMetres + dx, p.prevu.yMetres + dy), 4.5, 10, maintenant, 1.0), null)
        check(controleur.etat.erreur == null) { controleur.etat.erreur!! }
    }

    companion object {
        fun jpeg(graine: Int): ByteArray {
            val img = BufferedImage(24, 24, BufferedImage.TYPE_INT_RGB)
            for (x in 0 until 24) for (y in 0 until 24) img.setRGB(x, y, (x * 10 + y * graine * 7) and 0xFFFFFF)
            return ByteArrayOutputStream().also { ImageIO.write(img, "jpg", it) }.toByteArray()
        }

        fun dossierTemporaire(): File = Files.createTempDirectory("ramulus-correction").toFile()
    }
}

/** Les photos citées par une ligne de l'instantané : (nom, sha256). */
fun JsonObject.photosCitees(): List<Pair<String, String>> =
    this.getAsJsonArray("photos")?.map { it.asJsonObject["nom"].asString to it.asJsonObject["sha256"].asString }.orEmpty()

/** Exactement un élément, avec un message qui dit ce qui a été trouvé (au lieu de « List has more than one element »). */
fun <T> List<T>.uneSeule(): T {
    kotlin.test.assertEquals(1, size, "une seule ligne attendue dans la copie de travail, trouvées : $size")
    return first()
}
