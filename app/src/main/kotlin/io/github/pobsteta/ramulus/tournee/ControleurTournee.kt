package io.github.pobsteta.ramulus.tournee

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.github.pobsteta.ramulus.core.deperis.Abaque
import io.github.pobsteta.ramulus.core.deperis.Classe
import io.github.pobsteta.ramulus.core.deperis.EssenceDeperis
import io.github.pobsteta.ramulus.core.deperis.Essences
import io.github.pobsteta.ramulus.core.deperis.Note
import io.github.pobsteta.ramulus.core.echange.EchangeRefuse
import io.github.pobsteta.ramulus.core.gnss.CentreReleve
import io.github.pobsteta.ramulus.core.gnss.EcartAuPointPrevu
import io.github.pobsteta.ramulus.core.journal.LigneJournal
import io.github.pobsteta.ramulus.core.journal.TypeLigne
import io.github.pobsteta.ramulus.core.journal.UuidV7
import io.github.pobsteta.ramulus.core.photo.Empreinte
import io.github.pobsteta.ramulus.photo.fourchetteDeVues
import io.github.pobsteta.ramulus.core.photo.JpegSansExif
import io.github.pobsteta.ramulus.core.photo.NomsPhotos
import io.github.pobsteta.ramulus.core.photo.PhotoGardee
import io.github.pobsteta.ramulus.core.photo.RegimePhotos
import io.github.pobsteta.ramulus.core.photo.SignalementImage
import java.io.File
import java.io.InputStream
import java.text.Normalizer
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** Les écrans, dans leur ordre : opérateur, accueil, placettes, une placette, son centre, ses arbres, le rendu. */
sealed interface Ecran {
    data object Operateur : Ecran
    data object Accueil : Ecran
    data object Parametres : Ecran
    data object Placettes : Ecran
    data class Placette(val plotId: String) : Ecran
    data class Centre(val plotId: String) : Ecran
    data class Arbres(val plotId: String) : Ecran
    data object Rendu : Ecran
}

/** Une photo prise pour l'arbre en cours, pas encore nommée : elle prend son nom `<uuid>_<n>.jpg` à l'enregistrement de l'arbre. */
data class PhotoEnAttente(
    val fichier: String,
    val signalements: List<SignalementImage>,
    /** Arbre rouvert : le numéro (à partir de 1) de la vue de la note corrigée que cette photo reprend, sinon `null`. */
    val reprendVue: Int? = null,
)

/** L'arbre que l'opérateur est en train de noter. La classe n'est jamais saisie : elle sort de l'abaque. */
data class ArbreEnCours(
    val rang: Int = 1,
    /** Arbre déjà noté, rouvert pour correction : seul cas où l'on écrit un rang déjà noté. */
    val rouvert: Boolean = false,
    val essence: EssenceDeperis? = null,
    val mb: Note? = null,
    val second: Note? = null,
    /** Photos nouvelles, prises pour cet arbre (vues ajoutées, ou reprises d'une vue de la note corrigée). */
    val photos: List<PhotoEnAttente> = emptyList(),
    /** `uuid` de la note que l'enregistrement remplacera (arbre rouvert, mission 015). */
    val remplace: String? = null,
    /**
     * Photos de la note corrigée, citées `photos/<ancien uuid>_<n>.jpg`, dans l'ordre de leur numéro. Chacune passe à la
     * nouvelle note sous le même numéro (renommée, même SHA-256), sauf si l'opérateur la reprend : la photo reprise
     * prend alors ce numéro (arbitrage du 2026-10-30).
     */
    val photosAnciennes: List<String> = emptyList(),
    /** Vue de la note corrigée que la prochaine photo reprendra (numéro à partir de 1), ou `null`. */
    val vueAReprendre: Int? = null,
) {
    /** Combien de vues portera la note : celles de la note corrigée (gardées ou reprises), puis les ajoutées. */
    val nbPhotos: Int get() = photosAnciennes.size + photos.count { it.reprendVue == null }

    /** Les photos de la note corrigée qui passent telles quelles à la nouvelle. */
    val photosGardees: List<String>
        get() = photosAnciennes.filterIndexed { i, _ -> photos.none { it.reprendVue == i + 1 } }

    val classe: Classe? get() = if (mb != null && second != null) Abaque.classe(mb, second) else null
    val complet: Boolean get() = essence != null && mb != null && second != null
}

/** Une tournée déjà copiée sur l'appareil, à reprendre. */
data class TourneeEnCours(val code: String, val nature: String, val nbPlacettes: Int, val nbNotees: Int, val rendue: Boolean = false)

/** Ce que le rendu a donné : verdict, motifs en clair, nom du `.zip` déposé (absent si refusé). */
data class ResultatRendu(
    val verdict: String,
    val motifs: List<String>,
    val messages: List<String>,
    val nomZip: String?,
) {
    val refuse: Boolean get() = verdict == "refuse"
}

data class EtatTournee(
    val ecran: Ecran = Ecran.Accueil,
    val operateur: String? = null,
    val tournee: TourneeLue? = null,
    val enCours: List<TourneeEnCours> = emptyList(),
    val arbre: ArbreEnCours = ArbreEnCours(),
    /** Avertissements qui ne bloquent rien (signalements du contrôle, sauvegarde manquée). */
    val avertissements: List<String> = emptyList(),
    /** Un refus ou un échec, en clair. */
    val erreur: String? = null,
    val rendu: ResultatRendu? = null,
)

/**
 * Le déroulé d'une tournée sur le téléphone, sans Android : ouvrir la tournée, choisir une placette, relever son
 * centre, noter ses arbres par rang, clore, rendre le `.zip`.
 *
 * Chaque saisie passe par [Echange] (écriture dans la copie de travail) **puis** par le [Journal] ; jamais ailleurs
 * dans le GeoPackage. La copie de départ (`depart/deperis.gpkg`) n'est jamais écrite (Pascal, T5).
 * Les méthodes sont bloquantes : à appeler hors du fil principal ([TourneeViewModel] s'en charge).
 */
class ControleurTournee(
    private val racine: File,
    private val echange: Echange,
    private val journal: Journal,
    private val stock: StockOperateur,
    private val export: ExportRendu,
    private val versionRamulus: String,
    private val horloge: () -> Instant = { Instant.now() },
    private val uuids: UuidV7 = UuidV7(),
    private val zone: ZoneId = ZoneId.of("Europe/Paris"),
) {
    // L'état vit dans un StateFlow, pas dans un State de Compose : il s'écrit depuis le fil de travail (demarrer(), les
    // gestes) et se lit depuis l'écran (collectAsState) sans dépendre du snapshot où le contrôleur est né.
    private val flux = MutableStateFlow(EtatTournee())

    /** L'état, à collecter par l'écran. */
    val etats: StateFlow<EtatTournee> get() = flux

    val etat: EtatTournee get() = flux.value

    private companion object {
        /** Fichier posé dans le dossier de la tournée une fois le `.zip` rendu et déposé. */
        const val MARQUE_RENDUE = "rendue"
    }

    private var derniereEssence: EssenceDeperis? = null

    private fun dossier(code: String) = File(racine, code)
    private fun depart(code: String) = File(dossier(code), "depart/deperis.gpkg")
    private fun travail(code: String) = File(dossier(code), "travail/deperis.gpkg")
    /** La racine des photos rendues : `racinePhotos/photos/<uuid>_<n>.jpg` (contrat de `EchangeDeperis.rendre`). */
    private fun photos(code: String) = File(dossier(code), "photos")
    private fun dossierPhotos(code: String) = File(photos(code), NomsPhotos.DOSSIER)
    private fun enAttente(code: String) = File(dossier(code), "photos-en-attente")
    /**
     * Les photos qu'une correction a reprises : hors de `photos/` (le rendu, R10c, y tient tout fichier pour une photo à
     * citer), mais gardées sur l'appareil plutôt qu'effacées.
     */
    private fun remplacees(code: String) = File(dossier(code), "photos-remplacees")

    @Synchronized
    private fun maj(f: EtatTournee.() -> EtatTournee) {
        flux.update { it.f() }
    }

    /** Un échec inattendu d'un geste : dit en clair, sans planter l'application. */
    fun echec(e: Throwable) = maj { copy(erreur = "Erreur : ${e.message ?: e.javaClass.simpleName}") }

    // --- Départ -------------------------------------------------------------------------------------------------

    /** Au lancement : le nom de l'opérateur s'il manque, sinon l'accueil avec les tournées à reprendre. */
    fun demarrer() {
        val nom = stock.lire()?.takeIf { it.isNotBlank() }
        maj { copy(operateur = nom, enCours = tourneesEnCours(), ecran = if (nom == null) Ecran.Operateur else Ecran.Accueil) }
    }

    /** Le nom est saisi une fois (NFC, comme tout texte du format) ; il sert au journal et au rendu. */
    fun definirOperateur(nom: String) {
        val propre = Normalizer.normalize(nom.trim(), Normalizer.Form.NFC)
        if (propre.isEmpty()) {
            maj { copy(erreur = "Entrez votre nom.") }
            return
        }
        stock.ecrire(propre)
        maj { copy(operateur = propre, erreur = null, ecran = Ecran.Accueil) }
    }

    // --- Ouvrir une tournée ---------------------------------------------------------------------------------------

    /**
     * Copie la tournée choisie dans le stockage de l'application et la contrôle. Un refus affiche ses motifs en
     * clair et ne garde rien. Une tournée déjà ouverte est reprise là où elle en était : le travail n'est pas écrasé.
     */
    fun ouvrirTournee(source: InputStream) {
        val tmp = File(racine, "ouverture/deperis.gpkg")
        try {
            tmp.parentFile!!.mkdirs()
            source.use { entree -> tmp.outputStream().use { entree.copyTo(it) } }
            val verdict = JsonParser.parseString(echange.ouvrir(tmp)).asJsonObject
            val statut = verdict["verdict"].asString
            if (statut == "refuse") {
                maj { copy(erreur = refusEnClair("Cette tournée est refusée.", verdict), avertissements = emptyList()) }
                return
            }
            val lue = LectureTournee.depuis(echange.instantane(tmp))
            val reprise = travail(lue.code).exists()
            if (!reprise) {
                depart(lue.code).parentFile!!.mkdirs()
                travail(lue.code).parentFile!!.mkdirs()
                photos(lue.code).mkdirs()
                tmp.copyTo(depart(lue.code), overwrite = false)
                tmp.copyTo(travail(lue.code), overwrite = false)
            }
            val avert = buildList {
                if (reprise) add("Tournée déjà ouverte : votre travail est repris, rien n'est écrasé.")
                if (statut == "signale") addAll(messages(verdict))
            }
            val courante = if (reprise) LectureTournee.depuis(echange.instantane(travail(lue.code))) else lue
            maj { copy(tournee = courante, ecran = Ecran.Placettes, erreur = null, avertissements = avert, rendu = null) }
        } catch (e: Exception) {
            maj { copy(erreur = "Ce fichier ne s'ouvre pas : ${e.message ?: e.javaClass.simpleName}") }
        } finally {
            tmp.delete()
        }
    }

    fun reprendre(code: String) {
        try {
            val lue = LectureTournee.depuis(echange.instantane(travail(code)))
            maj { copy(tournee = lue, ecran = Ecran.Placettes, erreur = null, avertissements = emptyList(), rendu = null) }
        } catch (e: Exception) {
            maj { copy(erreur = "Cette tournée ne se rouvre pas : ${e.message ?: e.javaClass.simpleName}") }
        }
    }

    private fun tourneesEnCours(): List<TourneeEnCours> =
        racine.listFiles { f -> f.isDirectory }.orEmpty().sortedBy { it.name }.mapNotNull { d ->
            val g = File(d, "travail/deperis.gpkg")
            if (!g.exists()) return@mapNotNull null
            runCatching {
                val t = LectureTournee.depuis(echange.instantane(g))
                TourneeEnCours(t.code, t.nature, t.placettes.size, t.nbNotees, rendue = File(d, MARQUE_RENDUE).exists())
            }.getOrNull()
        }

    // --- Navigation -----------------------------------------------------------------------------------------------

    fun choisirPlacette(plotId: String) {
        val p = etat.tournee?.placette(plotId) ?: return
        maj { copy(ecran = Ecran.Placette(plotId), erreur = null, arbre = ArbreEnCours(rang = p.prochainRang, essence = derniereEssence)) }
    }

    fun versCentre(plotId: String) {
        val t = etat.tournee ?: return
        if (t.estContreNotation) return // le centre du premier opérateur est en lecture
        maj { copy(ecran = Ecran.Centre(plotId), erreur = null) }
    }

    fun versArbres(plotId: String) {
        val p = etat.tournee?.placette(plotId) ?: return
        // Un nouvel arbre commence : les photos laissées en attente par un arbre abandonné ne servent plus. Choix accepté
        // (orchestrateur, 2026-10-29) : une prise de vue interrompue par la mort du processus est perdue, car l'arbre en
        // cours (ArbreEnCours) n'est pas non plus conservé ; l'opérateur la refait.
        etat.tournee?.let { enAttente(it.code).deleteRecursively() }
        maj { copy(ecran = Ecran.Arbres(plotId), erreur = null, arbre = ArbreEnCours(rang = p.prochainRang, essence = derniereEssence)) }
    }

    // --- Les photos du houppier (mission 008, ADR 0006) ------------------------------------------------------------

    /** Combien de photos pour cette placette : 1, ou 2 à 4 si elle est « collecte » (PV1, provisoire). */
    fun regimePhotos(plotId: String): RegimePhotos = RegimePhotos.de(etat.tournee?.placette(plotId)?.collectePhotos == true)

    /**
     * Garde la photo de l'arbre en cours (JPEG déjà écrit sans EXIF par l'écran de prise de vue ; si un EXIF subsiste,
     * il est retiré ici). Elle reste en attente, privée à l'application : elle reçoit son nom à l'enregistrement de
     * l'arbre. Au-delà du maximum du régime, la photo est refusée avec un mot, sans effet sur la note.
     */
    fun ajouterPhoto(jpeg: ByteArray, signalements: List<SignalementImage>): Boolean {
        val ecran = etat.ecran as? Ecran.Arbres ?: return false
        val t = etat.tournee ?: return false
        val regime = regimePhotos(ecran.plotId)
        val reprise = etat.arbre.vueAReprendre
        if (reprise == null && etat.arbre.nbPhotos >= regime.max) {
            maj { copy(erreur = "Au plus ${regime.max} photo${if (regime.max > 1) "s" else ""} pour cet arbre.") }
            return false
        }
        if (!JpegSansExif.estUnJpeg(jpeg)) {
            maj { copy(erreur = "Cette photo n'est pas un JPEG : elle n'est pas gardée.") }
            return false
        }
        return try {
            val octets = JpegSansExif.retirer(jpeg)
            val dossierAttente = enAttente(t.code).also { it.mkdirs() }
            val nom = "${uuids.tirer(horloge().toEpochMilli())}.jpg"
            File(dossierAttente, nom).writeBytes(octets)
            // Une vue reprise deux fois : la dernière prise la reprend, la précédente ne sert plus.
            etat.arbre.photos.filter { reprise != null && it.reprendVue == reprise }.forEach { File(dossierAttente, it.fichier).delete() }
            maj {
                val gardees = arbre.photos.filterNot { reprise != null && it.reprendVue == reprise }
                copy(arbre = arbre.copy(photos = gardees + PhotoEnAttente(nom, signalements, reprise), vueAReprendre = null), erreur = null)
            }
            true
        } catch (e: Exception) {
            maj { copy(erreur = "Photo non gardée : ${e.message ?: e.javaClass.simpleName}") }
            false
        }
    }

    /** Retire une photo de l'arbre en cours (position `index` dans la liste, à partir de 0). */
    fun retirerPhoto(index: Int) {
        val t = etat.tournee ?: return
        val p = etat.arbre.photos.getOrNull(index) ?: return
        File(enAttente(t.code), p.fichier).delete()
        maj { copy(arbre = arbre.copy(photos = arbre.photos.filterIndexed { i, _ -> i != index })) }
    }

    /**
     * L'opérateur reprend la vue `numero` (à partir de 1) de l'arbre rouvert : la prochaine photo prise la remplace, sous
     * le même numéro (arbitrage du 2026-10-30). L'ancienne sort de `photos/` à l'enregistrement de la correction
     * (gardée dans `photos-remplacees/`), pas avant ; retirer la photo reprise rend l'ancienne.
     */
    fun reprendrePhoto(numero: Int) {
        if (numero !in 1..etat.arbre.photosAnciennes.size) return
        maj { copy(arbre = arbre.copy(vueAReprendre = numero), erreur = null) }
    }

    /** La prochaine photo est une vue ajoutée, pas une reprise (l'opérateur a renoncé à reprendre une vue). */
    fun nouvelleVue() = maj { copy(arbre = arbre.copy(vueAReprendre = null)) }

    /** Le fichier d'une photo gardée de la note corrigée (pour l'aperçu). */
    fun fichierGarde(nom: String): File? = etat.tournee?.let { File(photos(it.code), nom) }

    /** Le fichier d'une photo en attente (pour l'aperçu). */
    fun fichierEnAttente(photo: PhotoEnAttente): File? = etat.tournee?.let { File(enAttente(it.code), photo.fichier) }

    /**
     * Les fichiers de la future note, dans l'ordre de leur numéro : pour chaque vue de la note corrigée, sa reprise ou
     * l'ancienne photo ; puis les vues ajoutées. Le second terme est le nom de la vue de la note corrigée à ce numéro.
     */
    private fun sourcesDesPhotos(code: String, a: ArbreEnCours): List<Pair<File, String?>> =
        a.photosAnciennes.mapIndexed { i, ancien ->
            val reprise = a.photos.lastOrNull { it.reprendVue == i + 1 }
            (if (reprise != null) File(enAttente(code), reprise.fichier) else File(photos(code), ancien)) to ancien
        } + a.photos.filter { it.reprendVue == null }.map { File(enAttente(code), it.fichier) to null }

    /**
     * Donne aux photos leur nom définitif `photos/<uuid>_<n>.jpg` et leur empreinte, dans l'ordre de `sources` (copiées ;
     * l'original d'une photo gardée reste jusqu'à ce que la note soit écrite). Un `.nomedia` au-dessus de
     * `racinePhotos/photos/` empêche toute galerie de les indexer. Rend la colonne `photos` et les fichiers écrits (à
     * défaire si l'écriture de la note est refusée).
     */
    private fun nommerLesPhotos(code: String, uuidNote: String, sources: List<File>): Pair<List<PhotoGardee>, List<File>> {
        if (sources.isEmpty()) return emptyList<PhotoGardee>() to emptyList()
        val cible = dossierPhotos(code).also { it.mkdirs() }
        // Au-dessus de photos/ et non dedans : le rendu (R10c) tient tout fichier de photos/ pour une photo à citer.
        File(photos(code), ".nomedia").takeUnless { it.exists() }?.writeBytes(ByteArray(0))
        val gardees = mutableListOf<PhotoGardee>()
        val ecrits = mutableListOf<File>()
        try {
            sources.forEachIndexed { i, source ->
                val n = i + 1
                val fichier = File(cible, NomsPhotos.fichier(uuidNote, n))
                source.copyTo(fichier, overwrite = false)
                ecrits += fichier
                gardees += PhotoGardee(NomsPhotos.nom(uuidNote, n), Empreinte.sha256(fichier.inputStream()))
            }
        } catch (e: Exception) {
            ecrits.forEach { it.delete() }
            throw e
        }
        return gardees to ecrits
    }

    /** Retour d'un cran : arbres ou centre vers la placette, placette vers la liste, liste vers l'accueil. */
    fun retour() {
        val e = etat.ecran
        val suivant = when (e) {
            is Ecran.Arbres -> Ecran.Placette(e.plotId)
            is Ecran.Centre -> Ecran.Placette(e.plotId)
            is Ecran.Placette -> Ecran.Placettes
            Ecran.Rendu -> Ecran.Placettes
            Ecran.Placettes -> Ecran.Accueil
            Ecran.Parametres -> Ecran.Accueil
            else -> e
        }
        maj { copy(ecran = suivant, erreur = null, enCours = if (suivant == Ecran.Accueil) tourneesEnCours() else enCours) }
    }

    fun versParametres() = maj { copy(ecran = Ecran.Parametres, erreur = null) }

    /** Change le nom de l'opérateur depuis Paramètres, sans quitter l'écran ; un nom vide est refusé en clair. */
    fun renommerOperateur(nom: String) {
        val propre = Normalizer.normalize(nom.trim(), Normalizer.Form.NFC)
        if (propre.isEmpty()) {
            maj { copy(erreur = "Entrez votre nom.") }
            return
        }
        stock.ecrire(propre)
        maj { copy(operateur = propre, erreur = null) }
    }

    fun versRendu() = maj { copy(ecran = Ecran.Rendu, erreur = null, rendu = null) }

    // --- Le centre ------------------------------------------------------------------------------------------------

    /**
     * Enregistre le centre relevé dans la copie de travail, puis au journal. Sans écriture si l'échange refuse.
     * Relever de nouveau **remplace** le centre (mission 015) : l'ancien est retiré de la copie de travail, le journal
     * ajoute une ligne `centre` dont `remplace_uuid` désigne la ligne de l'ancien ; il reste un centre par placette.
     */
    fun enregistrerCentre(plotId: String, centre: CentreReleve, ecart: EcartAuPointPrevu?) {
        val t = etat.tournee ?: return
        val op = etat.operateur ?: return
        if (t.estContreNotation) return
        val maintenant = horloge()
        val uuid = uuids.tirer(maintenant.toEpochMilli())
        val ligne = JsonObject().apply {
            addProperty("uuid", uuid)
            addProperty("plot_id", plotId)
            addProperty("precision_m", centre.precisionM)
            addProperty("nb_mesures", centre.nbMesures)
            addProperty("source", centre.source)
            addProperty("date_releve", iso(centre.dateReleve))
            addProperty("operateur", op)
            add("geom", JsonObject().apply {
                addProperty("x", centre.position.xMetres)
                addProperty("y", centre.position.yMetres)
            })
        }
        val retires = try {
            JsonParser.parseString(echange.remplacerCentre(travail(t.code), ligne.toString())).asJsonArray.map { it.asString }
        } catch (e: Exception) {
            maj { copy(erreur = refusDEcriture(e)) }
            return
        }
        val valeur = JsonObject().apply {
            addProperty("plot_id", plotId)
            addProperty("x", centre.position.xMetres)
            addProperty("y", centre.position.yMetres)
            addProperty("precision_m", centre.precisionM)
            addProperty("nb_mesures", centre.nbMesures)
            addProperty("source", centre.source)
            ecart?.let { addProperty("ecart_m", it.metres) }
        }
        val remplace = retires.asReversed().firstNotNullOfOrNull { ligneDuJournal(t.code, it, TypeLigne.CENTRE) }
        ecrireJournal(t, uuid, TypeLigne.CENTRE, valeur, op, finDePlacette = false, remplaceUuid = remplace?.uuid)
        recharger(t.code)
        maj { copy(ecran = Ecran.Placette(plotId), erreur = null) }
    }

    // --- Les arbres -----------------------------------------------------------------------------------------------

    fun choisirEssence(e: EssenceDeperis) {
        derniereEssence = e
        maj {
            val a = arbre
            // Le second critère suit l'essence : MR pour un feuillu, MA pour un résineux ; une note de l'autre critère tombe.
            val garde = if (a.essence?.type == e.type) a.second else null
            copy(arbre = a.copy(essence = e, second = garde))
        }
    }

    fun choisirMb(n: Note) = maj { copy(arbre = arbre.copy(mb = n)) }
    fun choisirSecond(n: Note) = maj { copy(arbre = arbre.copy(second = n)) }

    /**
     * Rouvre un arbre déjà noté pour le corriger : il garde son rang (attribué par l'application, jamais choisi), ses
     * notes reviennent à l'écran et ses photos sont gardées. L'enregistrer de nouveau **remplace** la note (Pascal,
     * 2026-10-30, mission 015) : une note de nouvel `uuid`, même placette, même rang ; l'ancienne quitte la copie de
     * travail, le journal garde les deux. Une placette déjà close (« notee ») se corrige de la même façon
     * (arbitrage du 2026-10-30) ; elle reste close.
     */
    fun rouvrirArbre(rang: Int) {
        val e = etat.ecran as? Ecran.Arbres ?: return
        val p = etat.tournee?.placette(e.plotId) ?: return
        val lu = p.arbres.lastOrNull { it.rang == rang } ?: return
        val essence = Essences.toutes.firstOrNull { it.nom == lu.essence }
        // Les photos prises pour l'arbre abandonné ne servent plus.
        etat.tournee?.let { t -> etat.arbre.photos.forEach { ph -> File(enAttente(t.code), ph.fichier).delete() } }
        maj {
            copy(
                arbre = ArbreEnCours(
                    rang = lu.rang, rouvert = true, essence = essence, mb = Note(lu.mb), second = Note(lu.second),
                    remplace = lu.uuid.ifBlank { null }, photosAnciennes = lu.photos,
                ),
                erreur = null,
            )
        }
    }

    /** Avertissements de l'arbre en cours : fiche manquante (SA2), rang déjà noté. Aucun ne bloque. */
    fun avertissementsArbre(): List<String> {
        val e = etat.ecran as? Ecran.Arbres ?: return emptyList()
        val p = etat.tournee?.placette(e.plotId) ?: return emptyList()
        val a = etat.arbre
        return buildList {
            a.essence?.let { Avertissements.ficheManquante(it, p.saison)?.let(::add) }
            if (a.complet) photoManquante(p, a.nbPhotos)?.let(::add)
            a.photosGardees.forEach { nom ->
                if (fichierGarde(nom)?.isFile != true) add("Vue ${a.photosAnciennes.indexOf(nom) + 1} absente de l'appareil : reprenez-la.")
            }
            if (a.remplace != null) {
                add("Correction du rang ${a.rang} : la note enregistrée sera remplacée.")
            } else if (p.arbres.any { it.rang == a.rang }) {
                add("Le rang ${a.rang} est déjà noté.")
            }
        }
    }

    /** « Photo manquante » : jamais un blocage (Pascal, 2026-10-28). `null` si le régime de la placette est tenu. */
    private fun photoManquante(p: PlacetteLue, nb: Int): String? {
        val regime = RegimePhotos.de(p.collectePhotos)
        if (nb >= regime.min) return null
        return if (regime.max == 1) "Photo manquante." else "Photo manquante : $nb sur ${fourchetteDeVues(regime)} vues."
    }

    /** Avertissement de saison (SA1) de la placette, d'après la date du jour. */
    fun avertissementSaison(plotId: String): String? {
        val p = etat.tournee?.placette(plotId) ?: return null
        return Avertissements.saisonEtDate(p.saison, horloge().atZone(zone).toLocalDate())
    }

    /**
     * Enregistre l'arbre en cours : classe calculée par l'abaque par l'échange (jamais saisie), puis journal, puis
     * l'arbre suivant du tour d'horizon. Sans effet tant qu'une note ou l'essence manque. Le rang vient de l'application ; le numéro peint
     * (`repere`) n'existe plus : la colonne reste NULL (Pascal, 2026-10-29).
     *
     * Un arbre rouvert **remplace** sa note (mission 015) : nouvel `uuid`, même rang ; l'ancienne note quitte la copie
     * de travail. Chaque vue n de l'ancienne note passe à la nouvelle sous le même numéro
     * (`photos/<nouvel uuid>_<n>.jpg`) : l'ancienne photo, même SHA-256, ou celle qui la reprend (l'ancienne va alors
     * dans `photos-remplacees/`). Le journal ajoute la notation avec `remplace_uuid` = la ligne de l'ancienne note, et
     * une ligne `photo` par vue, rattachée à la nouvelle note, avec `remplace_uuid` = la ligne de la vue de même numéro
     * de l'ancienne : la projection du journal (et donc un `.ramsync` restauré) garde le lien photo ↔ note.
     */
    fun enregistrerArbre() {
        val ecran = etat.ecran as? Ecran.Arbres ?: return
        val t = etat.tournee ?: return
        val p = t.placette(ecran.plotId) ?: return
        val op = etat.operateur ?: return
        val a = etat.arbre
        if (p.complete && !a.rouvert) return // placette complète : pas de rang à noter, seulement des corrections voulues
        if (p.estNotee && !a.rouvert) return // placette close : seulement des corrections
        val essence = a.essence ?: return
        val mb = a.mb ?: return
        val second = a.second ?: return

        val maintenant = horloge()
        val uuid = uuids.tirer(maintenant.toEpochMilli())
        val saison = Avertissements.saisonDeLaFiche(essence, p.saison)
        val ligne = JsonObject().apply {
            addProperty("uuid", uuid)
            addProperty("plot_id", p.plotId)
            addProperty("rang", a.rang)
            // Un repère physique déjà sur l'arbre (plaque, ancienne marque) et porté par la tournée de départ est recopié ;
            // sinon NULL. Ramulus ne le demande jamais à l'opérateur (Pascal, 2026-10-29).
            repereDuDepart(t, p.plotId, a.rang)?.let { addProperty("repere", it) }
            addProperty("essence", essence.nom)
            addProperty("type", essence.type.name)
            addProperty("mb", mb.valeur)
            addProperty("second", second.valeur)
            addProperty("critere", essence.type.secondCritere.name)
            addProperty("saison", saison.name)
            addProperty("date_notation", iso(maintenant))
            addProperty("operateur", op)
        }
        val ancienne = a.remplace?.let { u -> p.arbres.firstOrNull { it.uuid == u } }
        if (a.remplace != null && ancienne == null) {
            maj { copy(erreur = "La note à corriger n'est plus dans la tournée : rouvrez l'arbre.") }
            return
        }
        val sources = sourcesDesPhotos(t.code, a)
        val (gardees, ecrits) = try {
            nommerLesPhotos(t.code, uuid, sources.map { it.first })
        } catch (e: Exception) {
            maj { copy(erreur = "Photos non gardées : ${e.message ?: e.javaClass.simpleName}. L'arbre n'est pas enregistré.") }
            return
        }
        ligne.add("photos", NomsPhotos.colonne(gardees))
        try {
            if (ancienne != null) {
                echange.remplacerNotation(travail(t.code), ancienne.uuid, ligne.toString())
            } else {
                echange.enregistrerNotation(travail(t.code), ligne.toString())
            }
        } catch (e: Exception) {
            ecrits.forEach { it.delete() }
            maj { copy(erreur = refusDEcriture(e)) }
            return
        }
        a.photos.forEach { File(enAttente(t.code), it.fichier).delete() }
        if (ancienne != null) {
            val gardeesTelles = a.photosGardees
            rangerLesPhotosRemplacees(t.code, gardeesTelles, ancienne.photos - gardeesTelles.toSet())
        }
        // Le journal ne porte ni classe ni proposition de l'IA : MB et MR/MA seulement (plan 005, Q9).
        val valeur = JsonObject().apply {
            addProperty("plot_id", p.plotId)
            addProperty("rang", a.rang)
            addProperty("essence", essence.nom)
            addProperty("mb", mb.valeur)
            addProperty("second", second.valeur)
            addProperty("critere", essence.type.secondCritere.name)
            addProperty("saison", saison.name)
        }
        val type = if (t.estContreNotation) TypeLigne.CONTRE_NOTATION else TypeLigne.NOTATION
        val lignesAnciennes = if (ancienne != null) lignesDuJournal(t.code) else emptyList()
        val remplaceNote = ancienne?.let { an -> lignesAnciennes.lastOrNull { it.objetUuid == an.uuid && it.type == type } }
        ecrireJournal(t, uuid, type, valeur, op, finDePlacette = false, remplaceUuid = remplaceNote?.uuid)
        // Une photo = un objet du journal (ADR 0005, plan 005 Q3) : uuid de la note, nom et SHA-256 du fichier gardé.
        // Une vue d'une note corrigée (gardée ou reprise) remplace la ligne de la vue de même numéro de l'ancienne note.
        gardees.forEachIndexed { i, g ->
            val photo = JsonObject().apply {
                addProperty("note_uuid", uuid)
                addProperty("nom", g.nom)
                addProperty("sha256", g.sha256)
            }
            val origine = ancienne?.let { an ->
                sources[i].second?.let { nom -> lignesAnciennes.lastOrNull { it.type == TypeLigne.PHOTO && photoDe(it, an.uuid, nom) } }
            }
            ecrireJournal(t, uuids.tirer(horloge().toEpochMilli()), TypeLigne.PHOTO, photo, op, finDePlacette = false, remplaceUuid = origine?.uuid)
        }
        recharger(t.code)
        val apres = etat.tournee?.placette(p.plotId)
        maj { copy(arbre = ArbreEnCours(rang = apres?.prochainRang ?: a.rang, essence = essence), erreur = null) }
    }

    /** Le `repere` que la copie de départ (jamais écrite) porte pour cet arbre, ou `null`. */
    private fun repereDuDepart(t: TourneeLue, plotId: String, rang: Int): String? = runCatching {
        LectureTournee.depuis(echange.instantane(depart(t.code))).placette(plotId)?.arbres
            ?.firstOrNull { it.rang == rang && !it.repere.isNullOrBlank() }?.repere
    }.getOrNull()

    /**
     * Après l'écriture d'une correction : les photos gardées existent sous leur nouveau nom, leur original part ; les
     * photos remplacées par une reprise sortent de `photos/` vers `photos-remplacees/` (rien n'est perdu, et le rendu reste conforme à R10c).
     */
    private fun rangerLesPhotosRemplacees(code: String, gardees: List<String>, reprises: List<String>) {
        gardees.forEach { File(photos(code), it).delete() }
        if (reprises.isEmpty()) return
        val archive = remplacees(code).also { it.mkdirs() }
        reprises.forEach { nom ->
            val f = File(photos(code), nom)
            if (!f.exists()) return@forEach
            val cible = File(archive, f.name)
            if (!f.renameTo(cible)) {
                f.copyTo(cible, overwrite = true)
                f.delete()
            }
        }
    }

    /** Vrai si la ligne du journal est la photo `nom` de la note `noteUuid`. */
    private fun photoDe(l: LigneJournal, noteUuid: String, nom: String): Boolean {
        val texte = l.valeur ?: return false
        return runCatching {
            val v = JsonParser.parseString(texte).asJsonObject
            v["note_uuid"]?.asString == noteUuid && v["nom"]?.asString == nom
        }.getOrDefault(false)
    }

    /** Les lignes du journal de la tournée ; vide si le journal ne se lit pas (la correction s'écrit quand même). */
    private fun lignesDuJournal(code: String): List<LigneJournal> = runCatching { journal.lignes(code) }.getOrDefault(emptyList())

    /** La dernière ligne du journal qui a saisi l'objet `objetUuid` avec ce `type`, ou `null`. */
    private fun ligneDuJournal(code: String, objetUuid: String, type: String): LigneJournal? =
        lignesDuJournal(code).lastOrNull { it.objetUuid == objetUuid && it.type == type }

    // --- Clore, rendre --------------------------------------------------------------------------------------------

    fun cloturerPlacette(plotId: String) {
        val t = etat.tournee ?: return
        val op = etat.operateur ?: return
        val p = t.placette(plotId) ?: return
        try {
            echange.cloturerPlacette(travail(t.code), plotId)
        } catch (e: Exception) {
            maj { copy(erreur = refusDEcriture(e)) }
            return
        }
        val valeur = JsonObject().apply {
            addProperty("plot_id", plotId)
            addProperty("nb_arbres_notes", p.nbRangsNotes)
        }
        ecrireJournal(t, uuids.tirer(horloge().toEpochMilli()), TypeLigne.PLACETTE_NOTEE, valeur, op, finDePlacette = true)
        recharger(t.code)
        maj { copy(ecran = Ecran.Placettes, erreur = null) }
    }

    /**
     * Rend le `.zip` : contrôle, écriture dans la copie, dépôt dans `Documents/Ramulus/`. Un rendu « signalé »
     * écrit quand même le `.zip` ; un refus n'écrit rien et affiche ses motifs en clair (Pascal, T5).
     */
    fun rendre() {
        val t = etat.tournee ?: return
        val op = etat.operateur ?: return
        val sortie = File(dossier(t.code), "rendu/${t.code}.zip")
        try {
            sortie.parentFile!!.mkdirs()
            sortie.delete()
            val verdict = JsonParser.parseString(
                echange.rendre(travail(t.code), depart(t.code), photos(t.code), op, versionRamulus, sortie),
            ).asJsonObject
            val statut = verdict["verdict"].asString
            val motifs = verdict.getAsJsonArray("motifs").map { Avertissements.enClair(it.asString) }
            val msgs = messages(verdict)
            if (statut == "refuse") {
                maj { copy(rendu = ResultatRendu(statut, motifs, msgs, null), ecran = Ecran.Rendu, erreur = null) }
                return
            }
            val nom = export.exporter(sortie, "${t.code}.zip")
            // La tournée est rendue : l'accueil la range dans « Terminées » (et le kanban en « Réalisée »).
            runCatching { File(dossier(t.code), MARQUE_RENDUE).writeText(nom) }
            runCatching { journal.sauvegarderSiDue(t.code, t.versionNemeton, finDePlacette = true) }
            maj { copy(rendu = ResultatRendu(statut, motifs, msgs, nom), ecran = Ecran.Rendu, erreur = null) }
        } catch (e: Exception) {
            maj { copy(erreur = "Le rendu a échoué : ${e.message ?: e.javaClass.simpleName}", ecran = Ecran.Rendu, rendu = null) }
        }
    }

    // --- Outils ---------------------------------------------------------------------------------------------------

    /**
     * Relit la tournée après une écriture (centre, notation, clôture de placette). Une écriture modifie la tournée : la
     * marque « rendue » tombe, la tournée revient dans « À faire » jusqu'au prochain rendu (arbitrage du 2026-10-29).
     */
    private fun recharger(code: String) {
        File(dossier(code), MARQUE_RENDUE).delete()
        val lue = LectureTournee.depuis(echange.instantane(travail(code)))
        maj { copy(tournee = lue) }
    }

    /**
     * Le journal suit l'écriture dans le GeoPackage. S'il échoue, la saisie reste dans le fichier et l'opérateur le
     * lit : on ne perd pas une note pour une ligne de journal.
     */
    private fun ecrireJournal(
        t: TourneeLue,
        objetUuid: String,
        type: String,
        valeur: JsonObject,
        op: String,
        finDePlacette: Boolean,
        remplaceUuid: String? = null,
    ) {
        try {
            if (remplaceUuid == null) {
                journal.saisir(t.code, objetUuid, type, valeur.toString(), op)
            } else {
                journal.saisir(t.code, objetUuid, type, valeur.toString(), op, remplaceUuid)
            }
        } catch (e: Exception) {
            maj { copy(avertissements = avertissements + "Journal non écrit : ${e.message ?: e.javaClass.simpleName}") }
            return
        }
        try {
            journal.sauvegarderSiDue(t.code, t.versionNemeton, finDePlacette)
        } catch (e: Exception) {
            maj { copy(avertissements = avertissements + "Sauvegarde non écrite : ${e.message ?: e.javaClass.simpleName}") }
        }
    }

    private fun iso(i: Instant): String = i.truncatedTo(ChronoUnit.MILLIS).toString()

    private fun messages(verdict: JsonObject): List<String> = verdict.getAsJsonArray("messages").map { it.asString }

    private fun refusEnClair(titre: String, verdict: JsonObject): String {
        val motifs = verdict.getAsJsonArray("motifs").map { Avertissements.enClair(it.asString) }
        return (listOf(titre) + motifs).joinToString("\n")
    }

    private fun refusDEcriture(e: Exception): String =
        if (e is EchangeRefuse) {
            "Refusé : " + e.motifs.joinToString(" ") { Avertissements.enClair(it) }
        } else {
            "Écriture impossible : ${e.message ?: e.javaClass.simpleName}"
        }
}
