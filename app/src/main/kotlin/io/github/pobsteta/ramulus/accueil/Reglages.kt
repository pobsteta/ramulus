package io.github.pobsteta.ramulus.accueil

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.pobsteta.ramulus.core.gnss.CollecteCentre
import io.github.pobsteta.ramulus.core.gnss.EcartAuPointPrevu
import io.github.pobsteta.ramulus.ui.theme.ChoixTheme
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Ce que les réglages lisent et écrivent sur le téléphone (SharedPreferences en vrai, mémoire dans les tests). */
interface MagasinReglages {
    fun lireTexte(cle: String): String?
    fun ecrireTexte(cle: String, valeur: String)
    fun lireEntier(cle: String, parDefaut: Int): Int
    fun ecrireEntier(cle: String, valeur: Int)
    fun lireLong(cle: String): Long?
    fun ecrireLong(cle: String, valeur: Long)
}

/** Les préférences privées de l'application (le fichier « ramulus », où vit déjà le nom de l'opérateur). */
class MagasinPrefs(context: Context) : MagasinReglages {
    private val prefs = context.getSharedPreferences("ramulus", Context.MODE_PRIVATE)
    override fun lireTexte(cle: String): String? = prefs.getString(cle, null)
    override fun ecrireTexte(cle: String, valeur: String) { prefs.edit().putString(cle, valeur).apply() }
    override fun lireEntier(cle: String, parDefaut: Int): Int = prefs.getInt(cle, parDefaut)
    override fun ecrireEntier(cle: String, valeur: Int) { prefs.edit().putInt(cle, valeur).apply() }
    override fun lireLong(cle: String): Long? = if (prefs.contains(cle)) prefs.getLong(cle, 0L) else null
    override fun ecrireLong(cle: String, valeur: Long) { prefs.edit().putLong(cle, valeur).apply() }
}

/**
 * Les réglages de Ramulus, **gardés sur le téléphone seulement**. Observables par l'écran (Compose).
 *
 * - Thème : Système par défaut (comme Marculus, ADR 0005, et Surculus).
 * - GNSS : 10 mesures moyennées et 30 m d'écart au point prévu. **Valeurs provisoires** de la mission 007 (gel avant la
 *   première campagne, mesurées par Pascal sur le terrain) ; modifiables ici. Bornées pour qu'un réglage faux ne casse rien.
 * - Dernière sauvegarde `.ramsync` : lecture seule, posée par l'application à chaque sauvegarde écrite.
 */
class ReglagesRamulus(private val magasin: MagasinReglages) {
    var choixTheme: ChoixTheme by mutableStateOf(ChoixTheme.depuis(magasin.lireTexte(CLE_THEME)))
        private set

    var nbMesuresGnss: Int by mutableStateOf(magasin.lireEntier(CLE_MESURES, MESURES_PAR_DEFAUT).coerceIn(MESURES_MIN, MESURES_MAX))
        private set

    var seuilEcartM: Int by mutableStateOf(magasin.lireEntier(CLE_SEUIL, SEUIL_PAR_DEFAUT_M).coerceIn(SEUIL_MIN_M, SEUIL_MAX_M))
        private set

    /** Plein écran : faux par défaut, gardé sur le téléphone (comme Surculus, mission 044). */
    var pleinEcran: Boolean by mutableStateOf(magasin.lireEntier(CLE_PLEIN_ECRAN, 0) == 1)
        private set

    private var derniereMs: Long? by mutableStateOf(magasin.lireLong(CLE_SAUVEGARDE))

    /** Instant de la dernière sauvegarde `.ramsync` écrite, ou `null` s'il n'y en a pas encore. */
    val derniereSauvegarde: Instant? get() = derniereMs?.let { Instant.ofEpochMilli(it) }

    fun definirTheme(choix: ChoixTheme) {
        choixTheme = choix
        magasin.ecrireTexte(CLE_THEME, choix.name)
    }

    fun definirPleinEcran(actif: Boolean) {
        pleinEcran = actif
        magasin.ecrireEntier(CLE_PLEIN_ECRAN, if (actif) 1 else 0)
    }

    fun definirNbMesures(n: Int) {
        nbMesuresGnss = n.coerceIn(MESURES_MIN, MESURES_MAX)
        magasin.ecrireEntier(CLE_MESURES, nbMesuresGnss)
    }

    fun definirSeuilEcart(metres: Int) {
        seuilEcartM = metres.coerceIn(SEUIL_MIN_M, SEUIL_MAX_M)
        magasin.ecrireEntier(CLE_SEUIL, seuilEcartM)
    }

    fun noterSauvegarde(quand: Instant) {
        derniereMs = quand.toEpochMilli()
        magasin.ecrireLong(CLE_SAUVEGARDE, quand.toEpochMilli())
    }

    /** Les mesures à moyenner, pour le relevé du centre. */
    fun nouvelleCollecte(): CollecteCentre = CollecteCentre(nbMesuresGnss)

    /** L'écart au point prévu, signalé au-delà du seuil réglé (strictement, comme le seuil provisoire de 007). */
    fun ecart(metres: Double): EcartAuPointPrevu = EcartAuPointPrevu(metres, metres > seuilEcartM)

    companion object {
        const val CLE_THEME = "theme"
        const val CLE_PLEIN_ECRAN = "plein_ecran"
        const val CLE_MESURES = "gnss_mesures"
        const val CLE_SEUIL = "gnss_seuil_m"
        const val CLE_SAUVEGARDE = "derniere_sauvegarde_ms"

        const val MESURES_PAR_DEFAUT = CollecteCentre.NB_MESURES_PROVISOIRE
        const val MESURES_MIN = 1
        const val MESURES_MAX = 60
        const val SEUIL_PAR_DEFAUT_M = 30
        const val SEUIL_MIN_M = 5
        const val SEUIL_MAX_M = 500
        const val PAS_SEUIL_M = 5
    }
}

/** « 29/10/2026 à 14:05 » (heure de Paris), ou « Aucune sauvegarde pour l'instant ». */
fun texteDerniereSauvegarde(quand: Instant?, zone: ZoneId = ZoneId.of("Europe/Paris")): String =
    if (quand == null) "Aucune sauvegarde pour l'instant"
    else DateTimeFormatter.ofPattern("dd/MM/yyyy 'à' HH:mm", Locale.FRANCE).format(quand.atZone(zone))

/** Le statut du kanban de chaque tournée, gardé sur le téléphone seulement (comme Surculus `ReglagesKanban`). */
class StatutsKanban(private val magasin: MagasinReglages) {
    fun statut(code: String): EtatKanban = EtatKanban.depuisNom(magasin.lireTexte(PREFIXE + code))
    fun definir(code: String, statut: EtatKanban) = magasin.ecrireTexte(PREFIXE + code, statut.name)

    private companion object {
        const val PREFIXE = "statut_"
    }
}
