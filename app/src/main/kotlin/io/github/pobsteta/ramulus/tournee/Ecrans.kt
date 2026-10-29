package io.github.pobsteta.ramulus.tournee

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import io.github.pobsteta.ramulus.core.deperis.Essences
import io.github.pobsteta.ramulus.gnss.ReleverLeCentre
import io.github.pobsteta.ramulus.notation.ClasseEnCours
import io.github.pobsteta.ramulus.notation.LigneNotes
import io.github.pobsteta.ramulus.photo.EcranPhoto
import io.github.pobsteta.ramulus.photo.PriseDeVue
import io.github.pobsteta.ramulus.photo.rappelDeConvention
import io.github.pobsteta.ramulus.ui.theme.couleurClasse
import java.util.Locale

/*
 * Les écrans d'une tournée (mission 006). Fiche d'affichage : chaque écran répond à une question de l'opérateur en
 * forêt, montre une chose en grand, et tient en peu de lignes. Gants, soleil, une main : cibles de 64 dp au moins,
 * aucune saisie au clavier sauf le numéro peint et le nom de l'opérateur. Quatre couleurs : vert (action), rouge
 * (refus), ambre (à vérifier), gris (le reste) ; les couleurs des classes A à F sont celles de l'abaque.
 */

private val Ambre = Color(0xFF8F7208)

@Composable
fun AppRamulus(vm: TourneeViewModel) {
    val c = vm.controleur
    val etat = c.etat
    BackHandler(enabled = etat.ecran !in listOf(Ecran.Accueil, Ecran.Operateur)) { vm.enArriere { c.retour() } }
    when (val e = etat.ecran) {
        Ecran.Operateur -> EcranOperateur(etat) { nom -> vm.enArriere { c.definirOperateur(nom) } }
        Ecran.Accueil -> if (etat.operateur != null) EcranAccueil(vm, etat)
        Ecran.Placettes -> EcranPlacettes(vm, etat)
        is Ecran.Placette -> EcranPlacette(vm, etat, e.plotId)
        is Ecran.Centre -> EcranCentre(vm, etat, e.plotId)
        is Ecran.Arbres -> EcranArbres(vm, etat, e.plotId)
        Ecran.Rendu -> EcranRendu(vm, etat)
    }
}

// --- Éléments communs -------------------------------------------------------------------------------------------

@Composable
private fun Page(
    titre: String,
    question: String? = null,
    pied: String? = null,
    retour: (() -> Unit)? = null,
    contenu: @Composable () -> Unit,
) {
    Scaffold { marges ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(marges)
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (retour != null) {
                TextButton(onClick = retour, Modifier.heightIn(min = 48.dp)) { Text("‹ Retour", fontSize = 17.sp) }
            }
            Text(titre, fontSize = 34.sp, fontWeight = FontWeight.Bold)
            if (question != null) Text(question, style = MaterialTheme.typography.titleMedium)
            contenu()
            if (pied != null) Text(pied, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        }
    }
}

@Composable
private fun GrosBouton(texte: String, enabled: Boolean = true, principal: Boolean = true, onClick: () -> Unit) {
    val mod = Modifier.fillMaxWidth().heightIn(min = 64.dp)
    if (principal) {
        Button(onClick = onClick, enabled = enabled, modifier = mod) { Text(texte, fontSize = 20.sp) }
    } else {
        OutlinedButton(onClick = onClick, enabled = enabled, modifier = mod) { Text(texte, fontSize = 20.sp) }
    }
}

@Composable
private fun Erreur(texte: String?) {
    if (texte != null) Text(texte, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
}

@Composable
private fun AVerifier(lignes: List<String>) {
    for (l in lignes) Text(l, color = Ambre, fontWeight = FontWeight.SemiBold)
}

private fun pied(t: TourneeLue, operateur: String?): String =
    "Tournée ${t.code} · préparée le ${t.prepareLe?.take(10) ?: "?"} · Nemeton ${t.versionNemeton ?: "?"} · $operateur"

// --- 1. Qui êtes-vous ? -----------------------------------------------------------------------------------------

@Composable
private fun EcranOperateur(etat: EtatTournee, valider: (String) -> Unit) {
    var nom by rememberSaveable { mutableStateOf("") }
    Page("Ramulus", "Quel est votre nom ?", pied = "Saisi une fois. Il reste sur ce téléphone et signe vos notes.") {
        OutlinedTextField(
            value = nom,
            onValueChange = { nom = it },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            modifier = Modifier.fillMaxWidth(),
            textStyle = MaterialTheme.typography.titleLarge,
        )
        Erreur(etat.erreur)
        GrosBouton("Valider", enabled = nom.isNotBlank()) { valider(nom) }
    }
}

// --- 2. Quelle tournée ? ----------------------------------------------------------------------------------------

@Composable
private fun EcranAccueil(vm: TourneeViewModel, etat: EtatTournee) {
    val contexte = LocalContext.current
    val choisir = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            vm.enArriere {
                val flux = contexte.contentResolver.openInputStream(uri) ?: error("fichier illisible")
                vm.controleur.ouvrirTournee(flux)
            }
        }
    }
    Page("Ramulus", "Quelle tournée noter ?", pied = "Opérateur : ${etat.operateur}") {
        GrosBouton("Ouvrir une tournée") { choisir.launch(arrayOf("*/*")) }
        Erreur(etat.erreur)
        if (etat.enCours.isNotEmpty()) {
            Text("En cours sur ce téléphone", style = MaterialTheme.typography.titleMedium)
            for (t in etat.enCours) {
                GrosBouton(
                    "${t.code}\n${t.nbNotees} sur ${t.nbPlacettes} placettes notées" + if (t.nature == TourneeLue.NATURE_CONTRE_NOTATION) " · contre-notation" else "",
                    principal = false,
                ) { vm.enArriere { vm.controleur.reprendre(t.code) } }
            }
        }
    }
}

// --- 3. Quelle placette ? ---------------------------------------------------------------------------------------

@Composable
private fun EcranPlacettes(vm: TourneeViewModel, etat: EtatTournee) {
    val t = etat.tournee ?: return
    val c = vm.controleur
    Page(
        titre = t.code,
        question = "${t.nbNotees} sur ${t.placettes.size} placettes notées" + if (t.estContreNotation) " · contre-notation" else "",
        pied = pied(t, etat.operateur),
        retour = { vm.enArriere { c.retour() } },
    ) {
        LinearProgressIndicator(progress = { t.nbNotees.toFloat() / t.placettes.size.coerceAtLeast(1) }, Modifier.fillMaxWidth())
        AVerifier(etat.avertissements)
        for (p in t.placettes) {
            val statut = p.statut
            val (libelle, couleur) = when (statut) {
                Statut.NOTEE -> "Notée" to MaterialTheme.colorScheme.primary
                Statut.EN_COURS -> "${p.arbres.size} sur ${p.nbArbres} arbres" to Ambre
                Statut.CENTRE_RELEVE -> "Centre relevé" to Ambre
                Statut.A_FAIRE -> "À faire" to Color.Gray
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 72.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                    .clickable { c.choisirPlacette(p.plotId) }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(p.plotId, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                Text(libelle, fontSize = 20.sp, color = couleur, fontWeight = FontWeight.SemiBold)
            }
        }
        GrosBouton("Rendre le .zip", principal = t.nbNotees > 0) { c.versRendu() }
    }
}

// --- 4. Que faire sur cette placette ? --------------------------------------------------------------------------

@Composable
private fun EcranPlacette(vm: TourneeViewModel, etat: EtatTournee, plotId: String) {
    val t = etat.tournee ?: return
    val p = t.placette(plotId) ?: return
    val c = vm.controleur
    val saison = c.avertissementSaison(plotId)
    val centre = p.centre
    Page(
        titre = plotId,
        question = when (p.statut) {
            Statut.NOTEE -> "Placette notée"
            else -> "${p.arbres.size} sur ${p.nbArbres} arbres notés"
        },
        pied = pied(t, etat.operateur),
        retour = { vm.enArriere { c.retour() } },
    ) {
        // K1 : la règle de choix et le tour d'horizon, rappelés ici (Pascal, 2026-10-28).
        Column(
            Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp)).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("Règle de choix", style = MaterialTheme.typography.labelLarge)
            Text(p.regleChoix ?: REGLE_CHOIX_PAR_DEFAUT, fontSize = 18.sp)
        }
        if (saison != null) AVerifier(listOf(saison))
        Erreur(etat.erreur)
        if (t.estContreNotation) {
            Text(
                if (centre != null) "Centre du premier opérateur : ${un(centre.x)} ; ${un(centre.y)} (lecture seule)" else "Centre : non fourni",
                style = MaterialTheme.typography.bodyMedium,
            )
        } else {
            GrosBouton(
                if (centre != null) "Centre relevé · ±${centre.precisionM?.let { un(it) } ?: "?"} m" else "Relever le centre",
                enabled = centre == null && !p.estNotee,
                principal = centre == null,
            ) { c.versCentre(plotId) }
        }
        GrosBouton(
            if (p.estNotee) "Voir les arbres" else "Noter les arbres",
            principal = centre != null || t.estContreNotation,
        ) { c.versArbres(plotId) }
        if (!p.estNotee) {
            GrosBouton("Clore la placette", enabled = p.arbres.isNotEmpty(), principal = p.arbres.size >= p.nbArbres) {
                vm.enArriere { c.cloturerPlacette(plotId) }
            }
            if (p.arbres.isNotEmpty() && p.arbres.size < p.nbArbres) {
                AVerifier(listOf("${p.arbres.size} sur ${p.nbArbres} arbres notés : vous pouvez clore quand même."))
            }
            if (!t.estContreNotation && centre == null && p.arbres.isNotEmpty()) AVerifier(listOf("Aucun centre relevé."))
        }
        if (p.collectePhotos) {
            Text("Placette « collecte » : 2 à 4 photos du houppier par arbre, à 90° les unes des autres.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        }
    }
}

// --- 5. Où est le centre ? --------------------------------------------------------------------------------------

@Composable
private fun EcranCentre(vm: TourneeViewModel, etat: EtatTournee, plotId: String) {
    val t = etat.tournee ?: return
    val p = t.placette(plotId) ?: return
    Page(plotId, pied = pied(t, etat.operateur), retour = { vm.enArriere { vm.controleur.retour() } }) {
        Erreur(etat.erreur)
        ReleverLeCentre(
            prevu = p.prevu,
            surCentre = { centre, ecart -> vm.enArriere { vm.controleur.enregistrerCentre(plotId, centre, ecart) } },
        )
    }
}

// --- 6. Cet arbre, quelle note ? --------------------------------------------------------------------------------

@Composable
private fun EcranArbres(vm: TourneeViewModel, etat: EtatTournee, plotId: String) {
    val t = etat.tournee ?: return
    val p = t.placette(plotId) ?: return
    val c = vm.controleur
    val a = etat.arbre
    val contexte = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    var photographie by rememberSaveable { mutableStateOf(false) }
    if (photographie) {
        EcranPhoto(vm, plotId) { photographie = false }
        return
    }
    val regime = c.regimePhotos(plotId)

    Page(
        titre = "$plotId · arbre ${a.rang} sur ${p.nbArbres}",
        question = p.regleChoix ?: REGLE_CHOIX_PAR_DEFAUT,
        pied = pied(t, etat.operateur),
        retour = { vm.enArriere { c.retour() } },
    ) {
        Text(
            "Arbre ${a.rang} sur ${p.nbArbres} — continuez le tour depuis le nord, sens horaire",
            style = MaterialTheme.typography.titleLarge,
        )
        Box {
            OutlinedButton(onClick = { menu = true }, Modifier.fillMaxWidth().heightIn(min = 64.dp)) {
                Text(a.essence?.nom ?: "Choisir l'essence", fontSize = 20.sp)
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                for (e in Essences.toutes) {
                    DropdownMenuItem(text = { Text(e.nom, fontSize = 18.sp) }, onClick = { c.choisirEssence(e); menu = false })
                }
            }
        }
        val essence = a.essence
        if (essence != null) {
            val fiche = essence.fiche(p.saison) ?: essence.fiche(io.github.pobsteta.ramulus.core.deperis.Saison.ETE)
            if (fiche != null) {
                TextButton(onClick = { contexte.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(fiche))) }) {
                    Text("Ouvrir la fiche du DSF")
                }
            }
            LigneNotes("Mortalité de branches (MB)", a.mb) { c.choisirMb(it) }
            LigneNotes("${essence.type.secondCritere.libelle} (${essence.type.secondCritere.name})", a.second) { c.choisirSecond(it) }
            ClasseEnCours(a.classe)
        }
        // La note d'abord, la photo après (brief) ; elle n'est jamais obligatoire pour enregistrer l'arbre.
        if (a.complet && !p.estNotee) {
            Text("Photo du houppier", style = MaterialTheme.typography.titleMedium)
            for (l in rappelDeConvention(regime)) Text(l, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            a.photos.forEachIndexed { i, photo ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    val fichier = c.fichierEnAttente(photo)
                    val miniature = remember(photo.fichier) {
                        fichier?.let { f ->
                            BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = 8 })?.asImageBitmap()
                        }
                    }
                    if (miniature != null) Image(miniature, contentDescription = "Vue ${i + 1}", modifier = Modifier.size(64.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Vue ${i + 1}", fontWeight = FontWeight.Bold)
                        for (sg in photo.signalements) Text(PriseDeVue.enClair(sg), color = Ambre, fontSize = 14.sp)
                    }
                    TextButton(onClick = { vm.enArriere { c.retirerPhoto(i) } }, Modifier.heightIn(min = 48.dp)) { Text("Retirer") }
                }
            }
            GrosBouton(
                if (a.photos.isEmpty()) "Photographier le houppier" else "Photographier une autre vue",
                enabled = a.photos.size < regime.max,
                principal = a.photos.size < regime.min,
            ) { photographie = true }
        }
        AVerifier(c.avertissementsArbre() + etat.avertissements)
        Erreur(etat.erreur)
        GrosBouton(if (p.arbres.any { it.rang == a.rang }) "Enregistrer la correction" else "Enregistrer l'arbre", enabled = a.complet && !p.estNotee) { vm.enArriere { c.enregistrerArbre() } }

        if (p.arbres.isNotEmpty()) {
            Text("Déjà notés (touchez un arbre pour le corriger)", style = MaterialTheme.typography.titleMedium)
            for (n in p.arbres) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(enabled = !p.estNotee) { c.rouvrirArbre(n.rang) },
                ) {
                    Text("${n.rang}", fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.size(24.dp))
                    Box(
                        Modifier.size(36.dp).background(n.classe?.let(::couleurClasse) ?: Color.Gray, RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center,
                    ) { Text(n.classe?.name ?: "?", color = Color.White, fontWeight = FontWeight.Bold) }
                    Column {
                        Text("${n.essence} · MB ${n.mb} · ${n.second}")
                        if (n.photos.size < regime.min) Text("photo manquante", color = Ambre, fontSize = 14.sp)
                    }
                }
            }
        }
        if (!p.estNotee && p.arbres.isNotEmpty()) {
            GrosBouton("Clore la placette", principal = p.arbres.size >= p.nbArbres) { vm.enArriere { c.cloturerPlacette(plotId) } }
        }
    }
}

// --- 7. Rendu ---------------------------------------------------------------------------------------------------

@Composable
private fun EcranRendu(vm: TourneeViewModel, etat: EtatTournee) {
    val t = etat.tournee ?: return
    val c = vm.controleur
    val contexte = LocalContext.current
    val r = etat.rendu
    val lancer = { vm.enArriere { c.rendre() } }
    // Avant Android 10, écrire dans Documents demande l'autorisation de stockage.
    val demande = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { lancer() }
    val rendre = {
        val besoin = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(contexte, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        if (besoin) demande.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE) else lancer()
    }
    Page(
        titre = when {
            r == null -> "Rendre ${t.code}"
            r.refuse -> "Rendu refusé"
            else -> "Rendu écrit"
        },
        question = when {
            r == null -> "${t.nbNotees} sur ${t.placettes.size} placettes notées"
            r.refuse -> "Rien n'est écrit. Corrigez, puis rendez de nouveau."
            else -> "Dans Documents/Ramulus/"
        },
        pied = pied(t, etat.operateur),
        retour = { vm.enArriere { c.retour() } },
    ) {
        Erreur(etat.erreur)
        if (r == null) {
            if (t.nbNotees < t.placettes.size) AVerifier(listOf("Des placettes ne sont pas notées : vous pouvez rendre quand même."))
            GrosBouton("Rendre le .zip") { rendre() }
        } else {
            if (r.nomZip != null) Text(r.nomZip, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            if (r.refuse) {
                for (m in r.motifs) Text(m, color = MaterialTheme.colorScheme.error, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            } else if (r.motifs.isNotEmpty()) {
                Text("À vérifier avant l'import", style = MaterialTheme.typography.titleMedium)
                AVerifier(r.motifs)
            }
            for (m in r.messages) Text(m, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            GrosBouton(if (r.refuse) "Rendre de nouveau" else "Retour aux placettes", principal = !r.refuse) {
                if (r.refuse) rendre() else vm.enArriere { c.retour() }
            }
        }
    }
}

private fun un(v: Double) = String.format(Locale.FRANCE, "%.1f", v)
