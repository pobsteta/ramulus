package io.github.pobsteta.ramulus.photo

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import io.github.pobsteta.ramulus.core.photo.RegimePhotos
import io.github.pobsteta.ramulus.tournee.TourneeViewModel
import io.github.pobsteta.ramulus.ui.theme.couleurAVerifier

/** Le rappel de convention de prise de vue (ADR 0006), montré à l'écran de prise de vue. */
fun rappelDeConvention(regime: RegimePhotos): List<String> = buildList {
    add("Une hauteur d'arbre de distance, le houppier entier dans le cadre.")
    add("Évitez le contre-jour : le soleil dans le dos.")
    if (regime.max > 1) add("Placette « collecte » : ${fourchetteDeVues(regime)} vues, tous les 90° autour de l'arbre.")
}

/**
 * Prise de vue du houppier dans Ramulus, avec CameraX (jamais l'appareil photo du téléphone, qui peut ranger la photo en
 * galerie ou l'envoyer dans un nuage : ADR 0006). La photo est gardée par le contrôleur ; les contrôles (flou,
 * contre-jour) sont signalés sans rien bloquer ; le houppier coupé n'est qu'un rappel de cadrage.
 */
@Composable
fun EcranPhoto(vm: TourneeViewModel, plotId: String, fini: () -> Unit) {
    val c = vm.controleur
    val contexte = LocalContext.current
    val regime = c.regimePhotos(plotId)
    val etatCourant by c.etats.collectAsState()
    val arbre = etatCourant.arbre
    BackHandler(onBack = fini)

    var autorisee by rememberSaveable {
        mutableStateOf(ContextCompat.checkSelfPermission(contexte, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val demande = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { autorisee = it }
    // L'état de la capture est au ViewModel : il survit à la rotation, et il n'est écrit que depuis le fil principal.
    val suivi = vm.capture
    val capturee by suivi.etat.collectAsState()
    val message = capturee.message
    val enCours = capturee.enCours
    val principal = ContextCompat.getMainExecutor(contexte)
    // Entrée : aucune capture n'est en vol pour ce nouvel écran (rotation, retour d'arrière-plan). Sortie définitive de
    // l'écran (pas une rotation) : l'état repart à zéro, le message d'échec ne suit pas l'arbre suivant.
    DisposableEffect(Unit) {
        suivi.entree()
        onDispose { if ((contexte as? Activity)?.isChangingConfigurations != true) suivi.quitte() }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Photo du houppier · ${arbre.nbPhotos} sur ${fourchetteDeVues(regime)}", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        for (l in rappelDeConvention(regime)) Text(l, style = MaterialTheme.typography.bodyMedium)

        if (!autorisee) {
            Text("Ramulus a besoin de la caméra pour photographier le houppier. La photo reste sur ce téléphone.", fontSize = 18.sp)
            Button(onClick = { demande.launch(Manifest.permission.CAMERA) }, Modifier.fillMaxWidth().heightIn(min = 64.dp)) {
                Text("Autoriser la caméra", fontSize = 20.sp)
            }
        } else {
            val capture = remember {
                ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                    .setFlashMode(ImageCapture.FLASH_MODE_OFF)
                    .build()
            }
            val executeur = vm.executeurPhoto
            val vue = remember { PreviewView(contexte).apply { scaleType = PreviewView.ScaleType.FIT_CENTER } }
            val proprietaire = LocalLifecycleOwner.current
            DisposableEffect(proprietaire) {
                val futur = ProcessCameraProvider.getInstance(contexte)
                var fournisseur: ProcessCameraProvider? = null
                futur.addListener({
                    try {
                        val f = futur.get()
                        fournisseur = f
                        val apercu = Preview.Builder().build().also { it.surfaceProvider = vue.surfaceProvider }
                        f.unbindAll()
                        f.bindToLifecycle(proprietaire, CameraSelector.DEFAULT_BACK_CAMERA, apercu, capture)
                    } catch (e: Exception) {
                        suivi.signale("Caméra indisponible : ${e.message ?: e.javaClass.simpleName}")
                    }
                }, ContextCompat.getMainExecutor(contexte))
                onDispose {
                    fournisseur?.unbindAll()
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                AndroidView(factory = { vue }, modifier = Modifier.fillMaxSize())
            }

            val derniere = arbre.photos.lastOrNull()
            if (derniere != null) {
                for (s in derniere.signalements) Text(PriseDeVue.enClair(s), color = couleurAVerifier(), fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
            }
            if (message != null) Text(message, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
            if (etatCourant.erreur != null) Text(etatCourant.erreur!!, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)

            Button(
                onClick = {
                    val tour = suivi.commence()
                    capture.takePicture(
                        executeur,
                        object : ImageCapture.OnImageCapturedCallback() {
                            override fun onCaptureSuccess(image: ImageProxy) {
                                // Fil de la caméra : le traitement s'y fait ; l'état n'est écrit que sur le fil principal.
                                val verdict: String? = try {
                                    val prise = PriseDeVue.traiter(image)
                                    vm.enArriere { c.ajouterPhoto(prise.jpeg, prise.signalements) }
                                    null
                                } catch (e: Throwable) {
                                    "Photo non gardée : ${e.message ?: e.javaClass.simpleName}"
                                }
                                principal.execute { if (verdict == null) suivi.reussit(tour) else suivi.echoue(tour, verdict) }
                            }

                            override fun onError(exception: ImageCaptureException) {
                                val interrompue = exception.imageCaptureError == ImageCapture.ERROR_CAMERA_CLOSED
                                principal.execute {
                                    if (interrompue) suivi.interrompue(tour)
                                    else suivi.echoue(tour, "Prise de vue impossible : ${exception.message ?: "erreur de la caméra"}")
                                }
                            }
                        },
                    )
                },
                enabled = !enCours && (arbre.vueAReprendre != null || arbre.nbPhotos < regime.max),
                modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp),
            ) { Text(if (arbre.vueAReprendre == null && arbre.nbPhotos >= regime.max) "Nombre de vues atteint" else "Prendre la photo", fontSize = 22.sp) }
        }
        OutlinedButton(onClick = fini, Modifier.fillMaxWidth().heightIn(min = 64.dp)) { Text("Terminé", fontSize = 20.sp) }
    }
}
