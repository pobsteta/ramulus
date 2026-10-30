package io.github.pobsteta.ramulus.tournee

import androidx.lifecycle.ViewModel
import java.util.concurrent.Executors

/**
 * Tient le [ControleurTournee] à travers les rotations d'écran. Les gestes qui touchent un fichier ou le journal
 * (ouvrir, enregistrer, clore, rendre) passent par un seul fil de travail, dans l'ordre ; les gestes qui ne touchent
 * que l'écran (choisir une note, avancer d'un écran) se font tout de suite.
 */
class TourneeViewModel(val controleur: ControleurTournee) : ViewModel() {
    /** L'état de la capture photo, gardé ici pour traverser la rotation (mission 017). */
    val capture = io.github.pobsteta.ramulus.photo.SuiviCapture()

    /** Le fil de la caméra : tenu ici et non par l'écran, pour que CameraX puisse rendre son verdict après une rotation. */
    val executeurPhoto = Executors.newSingleThreadExecutor { r -> Thread(r, "ramulus-photo").apply { isDaemon = true } }

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
        executeurPhoto.shutdown()
    }
}
