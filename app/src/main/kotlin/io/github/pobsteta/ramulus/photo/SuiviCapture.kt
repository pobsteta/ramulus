package io.github.pobsteta.ramulus.photo

import io.github.pobsteta.ramulus.core.photo.RegimePhotos
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/** Ce que l'écran de prise de vue sait d'une capture : en cours ou non, et le dernier mot d'échec (mission 017). */
data class EtatCapture(val enCours: Boolean = false, val message: String? = null)

/**
 * L'état de la capture, tenu par le `TourneeViewModel` et non par l'écran : il survit à une rotation et à un retour
 * d'arrière-plan. Il ne se modifie que depuis le fil principal (l'écran y renvoie le retour de CameraX).
 *
 * Chaque capture porte un numéro (« tour »). Quand l'écran est recréé ([entree]), les captures en vol sont oubliées : leur
 * verdict, s'il arrive, est ignoré, et le bouton revient. Aucune capture perdue ne peut donc griser le bouton pour toujours.
 */
class SuiviCapture {
    private val flux = MutableStateFlow(EtatCapture())
    private var tour = 0
    val etat: StateFlow<EtatCapture> get() = flux

    /** Une capture commence ; rend son numéro, à redonner avec le verdict. */
    fun commence(): Int {
        tour++
        flux.value = EtatCapture(enCours = true)
        return tour
    }

    fun reussit(t: Int) { if (t == tour) flux.value = EtatCapture() }

    fun echoue(t: Int, message: String) { if (t == tour) flux.value = EtatCapture(message = message) }

    /** Une capture interrompue par la rotation n'est pas une panne : on ne le dit pas. */
    fun interrompue(t: Int) { if (t == tour) flux.update { it.copy(enCours = false) } }

    /** Un message sans capture (caméra indisponible). */
    fun signale(message: String) { flux.value = EtatCapture(message = message) }

    /** L'écran (re)naît : aucune capture n'est en vol pour lui ; le bouton revient. Le message, lui, reste. */
    fun entree() {
        tour++
        flux.update { it.copy(enCours = false) }
    }

    /** L'écran est quitté pour de bon : tout repart à zéro. */
    fun quitte() {
        tour++
        flux.value = EtatCapture()
    }
}

/** « 2 à 4 » ou « 1 » : la fourchette de vues, lue du régime (PV1, provisoire, n'est écrite que dans `RegimePhotos`). */
fun fourchetteDeVues(regime: RegimePhotos): String = if (regime.max == regime.min) "${regime.min}" else "${regime.min} à ${regime.max}"
