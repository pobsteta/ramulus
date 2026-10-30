package io.github.pobsteta.ramulus.photo

import io.github.pobsteta.ramulus.core.photo.RegimePhotos
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Ce que l'écran de prise de vue sait d'une capture : en cours ou non, et le dernier mot d'échec (mission 017). */
data class EtatCapture(val enCours: Boolean = false, val message: String? = null)

/**
 * L'état de la capture, tenu par le `TourneeViewModel` et non par l'écran : il survit à une rotation et à un retour
 * d'arrière-plan, et une capture lancée avant la rotation rend son verdict à l'écran suivant. Il ne se modifie que depuis
 * le fil principal (l'écran y renvoie le retour de CameraX).
 */
class SuiviCapture {
    private val flux = MutableStateFlow(EtatCapture())
    val etat: StateFlow<EtatCapture> get() = flux

    fun commence() { flux.value = EtatCapture(enCours = true) }

    fun reussit() { flux.value = EtatCapture() }

    fun echoue(message: String) { flux.value = EtatCapture(message = message) }

    /** Une capture interrompue par la rotation n'est pas une panne : on ne le dit pas. */
    fun interrompue() { flux.value = flux.value.copy(enCours = false) }
}

/** « 2 à 4 » ou « 1 » : la fourchette de vues, lue du régime (PV1, provisoire, n'est écrite que dans `RegimePhotos`). */
fun fourchetteDeVues(regime: RegimePhotos): String = if (regime.max == regime.min) "${regime.min}" else "${regime.min} à ${regime.max}"
