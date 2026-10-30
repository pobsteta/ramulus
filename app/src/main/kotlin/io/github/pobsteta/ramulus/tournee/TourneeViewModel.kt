package io.github.pobsteta.ramulus.tournee

import androidx.lifecycle.ViewModel
import java.util.concurrent.Executors

/**
 * Tient le [ControleurTournee] à travers les rotations d'écran. Les gestes qui touchent un fichier ou le journal
 * (ouvrir, enregistrer, clore, rendre) passent par un seul fil de travail, dans l'ordre ; les gestes qui ne touchent
 * que l'écran (choisir une note, avancer d'un écran) se font tout de suite.
 */
class TourneeViewModel(val controleur: ControleurTournee) : ViewModel() {
    private val travail = Executors.newSingleThreadExecutor { r -> Thread(r, "ramulus-tournee").apply { isDaemon = true } }

    init {
        enArriere { controleur.demarrer() }
    }

    /** Un geste qui écrit : hors du fil principal, un à la fois. */
    fun enArriere(geste: () -> Unit) {
        travail.execute {
            try {
                geste()
            } catch (e: Throwable) {
                controleur.echec(e)
            }
        }
    }

    override fun onCleared() {
        travail.shutdown()
    }
}
