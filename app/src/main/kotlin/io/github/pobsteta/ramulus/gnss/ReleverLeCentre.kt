package io.github.pobsteta.ramulus.gnss

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.pobsteta.ramulus.core.gnss.CentreReleve
import io.github.pobsteta.ramulus.accueil.ReglagesRamulus
import io.github.pobsteta.ramulus.core.gnss.EcartAuPointPrevu
import io.github.pobsteta.ramulus.core.gnss.PointLambert93
import io.github.pobsteta.ramulus.core.gnss.ResultatReleve
import java.util.Locale

/**
 * « Relever le centre » : écoute le GNSS du téléphone, moyenne les mesures, affiche la précision, le nombre de
 * mesures et l'écart au point [prevu] (Lambert-93, s'il est connu). Un écart de plus que le seuil des Paramètres (30 m par défaut, provisoire) est **signalé**, jamais
 * bloquant : [surCentre] est appelé quand l'opérateur valide, avec l'écart. L'écriture dans le GeoPackage n'est pas
 * ici (mission 004). Gros boutons : gants, soleil.
 */
@Composable
fun ReleverLeCentre(
    prevu: PointLambert93?,
    surCentre: (CentreReleve, EcartAuPointPrevu?) -> Unit,
    reglages: ReglagesRamulus,
    modifier: Modifier = Modifier,
) {
    val ctx = LocalContext.current
    var autorisee by remember { mutableStateOf(SourcePosition.localisationAutorisee(ctx)) }
    var enCours by remember { mutableStateOf(false) }
    var collecte by remember { mutableStateOf(reglages.nouvelleCollecte()) }
    var nbMesures by remember { mutableIntStateOf(0) }
    var precisionRefusee by remember { mutableStateOf(false) }
    val demande = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        // Seule la position précise sert : une autorisation approximative ne suffit pas au GPS.
        autorisee = SourcePosition.localisationAutorisee(ctx)
        // Position approximative seule, ou demande plus affichée par Android : il faut passer par les réglages.
        precisionRefusee = !autorisee
    }

    // Au retour des réglages de l'application, l'autorisation est relue.
    val cycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(cycle) {
        val obs = LifecycleEventObserver { _, ev ->
            if (ev == Lifecycle.Event.ON_RESUME) {
                autorisee = SourcePosition.localisationAutorisee(ctx)
                if (autorisee) precisionRefusee = false
            }
        }
        cycle.addObserver(obs)
        onDispose { cycle.removeObserver(obs) }
    }

    DisposableEffect(enCours, autorisee) {
        val ecoute = if (enCours && autorisee) {
            SourcePosition.ecouter(ctx) { mesure ->
                collecte.ajouter(mesure)
                nbMesures = collecte.nbMesures
                if (collecte.complete) enCours = false
            }
        } else {
            AutoCloseable {}
        }
        onDispose { ecoute.close() }
    }

    val resultat = if (nbMesures > 0) collecte.resultat() else ResultatReleve.AucuneMesure

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Centre de la placette", style = MaterialTheme.typography.titleMedium)
        when {
            !autorisee && precisionRefusee -> {
                Text("Position précise requise : le GPS ne marche pas avec la seule position approximative. Elle reste sur l'appareil.")
                Button(
                    onClick = {
                        ctx.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", ctx.packageName, null))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    },
                    Modifier.heightIn(min = 56.dp),
                ) { Text("Ouvrir les réglages") }
            }
            !autorisee -> {
                Text("La position du téléphone est nécessaire pour relever le centre. Elle reste sur l'appareil.")
                Button(onClick = { demande.launch(arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION, android.Manifest.permission.ACCESS_COARSE_LOCATION)) }, Modifier.heightIn(min = 56.dp)) {
                    Text("Autoriser la position")
                }
            }
            else -> {
                Text("Position simulée écartée (récepteur externe non pris en charge pour l'instant).")
                Text("Mesures : $nbMesures / ${collecte.nbCible}")
                when (resultat) {
                    is ResultatReleve.Centre -> {
                        val c = resultat.centre
                        Text("Précision annoncée : ${un(c.precisionM)} m ; dispersion des mesures : ${un(c.dispersionM)} m")
                        if (prevu != null) {
                            val e = reglages.ecart(c.position.distanceA(prevu))
                            Text("Écart au point prévu : ${un(e.metres)} m")
                            if (e.signale) {
                                Text(
                                    "Centre éloigné du point prévu (plus de ${reglages.seuilEcartM} m) : " +
                                        "vérifiez le piquet. Vous pouvez valider quand même.",
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                    is ResultatReleve.HorsFrance ->
                        Text("Position hors de France : mesure refusée. Vérifiez le GPS du téléphone.", color = MaterialTheme.colorScheme.error)
                    ResultatReleve.AucuneMesure ->
                        Text(if (enCours) "En attente du GPS (à découvert, patientez)…" else "Prêt à relever.")
                }
                Button(
                    onClick = {
                        if (!enCours) {
                            collecte = reglages.nouvelleCollecte()
                            nbMesures = 0
                        }
                        enCours = !enCours
                    },
                    Modifier.fillMaxWidth().heightIn(min = 56.dp),
                ) { Text(if (enCours) "Arrêter" else if (nbMesures > 0) "Recommencer" else "Relever le centre") }
                if (resultat is ResultatReleve.Centre && !enCours) {
                    val e = prevu?.let { reglages.ecart(resultat.centre.position.distanceA(it)) }
                    OutlinedButton(
                        onClick = { surCentre(resultat.centre, e) },
                        Modifier.fillMaxWidth().heightIn(min = 56.dp),
                    ) { Text("Valider ce centre") }
                }
            }
        }
    }
}

private fun un(v: Double) = String.format(Locale.FRANCE, "%.1f", v)
