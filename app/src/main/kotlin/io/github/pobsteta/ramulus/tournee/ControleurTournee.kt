package io.github.pobsteta.ramulus.tournee

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.github.pobsteta.ramulus.core.deperis.Abaque
import io.github.pobsteta.ramulus.core.deperis.Classe
import io.github.pobsteta.ramulus.core.deperis.EssenceDeperis
import io.github.pobsteta.ramulus.core.deperis.Note
import io.github.pobsteta.ramulus.core.echange.EchangeRefuse
import io.github.pobsteta.ramulus.core.gnss.CentreReleve
import io.github.pobsteta.ramulus.core.gnss.EcartAuPointPrevu
import io.github.pobsteta.ramulus.core.journal.TypeLigne
import io.github.pobsteta.ramulus.core.journal.UuidV7
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

/** L'arbre que l'opérateur est en train de noter. La classe n'est jamais saisie : elle sort de l'abaque. */
data class ArbreEnCours(
    val rang: Int = 1,
    val repere: String = "",
    val essence: EssenceDeperis? = null,
    val mb: Note? = null,
    val second: Note? = null,
) {
    val classe: Classe? get() = if (mb != null && second != null) Abaque.classe(mb, second) else null
    val complet: Boolean get() = essence != null && mb != null && second != null && repere.isNotBlank()
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
    var etat by mutableStateOf(EtatTournee())
        private set

    private companion object {
        /** Fichier posé dans le dossier de la tournée une fois le `.zip` rendu et déposé. */
        const val MARQUE_RENDUE = "rendue"
    }

    private var derniereEssence: EssenceDeperis? = null

    private fun dossier(code: String) = File(racine, code)
    private fun depart(code: String) = File(dossier(code), "depart/deperis.gpkg")
    private fun travail(code: String) = File(dossier(code), "travail/deperis.gpkg")
    private fun photos(code: String) = File(dossier(code), "photos")

    @Synchronized
    private fun maj(f: EtatTournee.() -> EtatTournee) {
        etat = etat.f()
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
        maj { copy(ecran = Ecran.Arbres(plotId), erreur = null, arbre = ArbreEnCours(rang = p.prochainRang, essence = derniereEssence)) }
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

    /** Enregistre le centre relevé dans la copie de travail, puis au journal. Sans écriture si l'échange refuse. */
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
        try {
            echange.enregistrerCentre(travail(t.code), ligne.toString())
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
        ecrireJournal(t, uuid, TypeLigne.CENTRE, valeur, op, finDePlacette = false)
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
    fun changerRepere(texte: String) = maj { copy(arbre = arbre.copy(repere = texte)) }

    fun changerRang(delta: Int) {
        val e = etat.ecran as? Ecran.Arbres ?: return
        val n = etat.tournee?.placette(e.plotId)?.nbArbres ?: return
        maj { copy(arbre = arbre.copy(rang = (arbre.rang + delta).coerceIn(1, n))) }
    }

    /** Avertissements de l'arbre en cours : fiche manquante (SA2), rang ou numéro déjà noté. Aucun ne bloque. */
    fun avertissementsArbre(): List<String> {
        val e = etat.ecran as? Ecran.Arbres ?: return emptyList()
        val p = etat.tournee?.placette(e.plotId) ?: return emptyList()
        val a = etat.arbre
        return buildList {
            a.essence?.let { Avertissements.ficheManquante(it, p.saison)?.let(::add) }
            if (p.arbres.any { it.rang == a.rang }) add("Le rang ${a.rang} est déjà noté.")
            if (a.repere.isNotBlank() && p.arbres.any { it.repere == a.repere.trim() }) add("Le numéro ${a.repere.trim()} est déjà noté.")
        }
    }

    /** Avertissement de saison (SA1) de la placette, d'après la date du jour. */
    fun avertissementSaison(plotId: String): String? {
        val p = etat.tournee?.placette(plotId) ?: return null
        return Avertissements.saisonEtDate(p.saison, horloge().atZone(zone).toLocalDate())
    }

    /**
     * Enregistre l'arbre en cours : classe calculée par l'abaque par l'échange (jamais saisie), puis journal, puis
     * l'arbre suivant du tour d'horizon. Sans effet tant qu'une note, l'essence ou le numéro manque.
     */
    fun enregistrerArbre() {
        val ecran = etat.ecran as? Ecran.Arbres ?: return
        val t = etat.tournee ?: return
        val p = t.placette(ecran.plotId) ?: return
        val op = etat.operateur ?: return
        val a = etat.arbre
        val essence = a.essence ?: return
        val mb = a.mb ?: return
        val second = a.second ?: return
        if (a.repere.isBlank()) return

        val maintenant = horloge()
        val uuid = uuids.tirer(maintenant.toEpochMilli())
        val saison = Avertissements.saisonDeLaFiche(essence, p.saison)
        val ligne = JsonObject().apply {
            addProperty("uuid", uuid)
            addProperty("plot_id", p.plotId)
            addProperty("rang", a.rang)
            addProperty("repere", a.repere.trim())
            addProperty("essence", essence.nom)
            addProperty("type", essence.type.name)
            addProperty("mb", mb.valeur)
            addProperty("second", second.valeur)
            addProperty("critere", essence.type.secondCritere.name)
            addProperty("saison", saison.name)
            addProperty("date_notation", iso(maintenant))
            addProperty("operateur", op)
            add("photos", JsonArray())
        }
        try {
            echange.enregistrerNotation(travail(t.code), ligne.toString())
        } catch (e: Exception) {
            maj { copy(erreur = refusDEcriture(e)) }
            return
        }
        // Le journal ne porte ni classe ni proposition de l'IA : MB et MR/MA seulement (plan 005, Q9).
        val valeur = JsonObject().apply {
            addProperty("plot_id", p.plotId)
            addProperty("rang", a.rang)
            addProperty("repere", a.repere.trim())
            addProperty("essence", essence.nom)
            addProperty("mb", mb.valeur)
            addProperty("second", second.valeur)
            addProperty("critere", essence.type.secondCritere.name)
            addProperty("saison", saison.name)
        }
        val type = if (t.estContreNotation) TypeLigne.CONTRE_NOTATION else TypeLigne.NOTATION
        ecrireJournal(t, uuid, type, valeur, op, finDePlacette = false)
        recharger(t.code)
        val apres = etat.tournee?.placette(p.plotId)
        maj { copy(arbre = ArbreEnCours(rang = apres?.prochainRang ?: a.rang, essence = essence), erreur = null) }
    }

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
            addProperty("nb_arbres_notes", p.arbres.size)
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

    private fun recharger(code: String) {
        val lue = LectureTournee.depuis(echange.instantane(travail(code)))
        maj { copy(tournee = lue) }
    }

    /**
     * Le journal suit l'écriture dans le GeoPackage. S'il échoue, la saisie reste dans le fichier et l'opérateur le
     * lit : on ne perd pas une note pour une ligne de journal.
     */
    private fun ecrireJournal(t: TourneeLue, objetUuid: String, type: String, valeur: JsonObject, op: String, finDePlacette: Boolean) {
        try {
            journal.saisir(t.code, objetUuid, type, valeur.toString(), op)
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
