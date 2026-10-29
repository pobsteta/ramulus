package io.github.pobsteta.ramulus.accueil

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.ViewColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.pm.PackageInfoCompat
import io.github.pobsteta.ramulus.tournee.EtatTournee
import io.github.pobsteta.ramulus.tournee.TourneeEnCours
import io.github.pobsteta.ramulus.tournee.TourneeViewModel
import io.github.pobsteta.ramulus.ui.BandeauCompact

/** Les entrées du menu « ⋮ » du bandeau, dans l'ordre (arbitrage de Pascal du 2026-10-29 : trois points, comme Surculus et Marculus). */
enum class EntreeMenu(val libelle: String) {
    OUVRIR_TOURNEE("Ouvrir une tournée"),
    PARAMETRES("Paramètres"),
    A_PROPOS("À propos"),
}

val ENTREES_MENU: List<EntreeMenu> = EntreeMenu.entries

fun TourneeEnCours.enLigne(): LigneAccueil = LigneAccueil(code, nature, nbPlacettes, nbNotees, rendue)

/**
 * L'écran d'ouverture, le même que Surculus et Marculus : bandeau vert (titre, bascule liste/kanban, menu ⋮), zone de
 * recherche, puis la liste « À faire » et « Terminées » ou les cinq colonnes du kanban. Structure reprise de Surculus
 * (`tournee/EcranTournees.kt`, commit b73c493) ; la recherche filtre sur le code de la tournée.
 * Il n'est jamais vide : sans tournée, il dit comment en ouvrir une.
 */
@Composable
fun EcranAccueil(vm: TourneeViewModel, etat: EtatTournee, reglages: ReglagesRamulus) {
    val ctx = LocalContext.current
    var recherche by rememberSaveable { mutableStateOf("") }
    var modeColonnes by rememberSaveable { mutableStateOf(false) }
    var aPropos by rememberSaveable { mutableStateOf(false) }
    val statuts = remember { StatutsKanban(MagasinPrefs(ctx)) }
    var versionStatuts by remember { mutableStateOf(0) }

    val choisir = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            vm.enArriere {
                val flux = ctx.contentResolver.openInputStream(uri) ?: error("fichier illisible")
                vm.controleur.ouvrirTournee(flux)
            }
        }
    }
    if (aPropos) BoiteAPropos(onFermer = { aPropos = false })

    Column(Modifier.fillMaxSize()) {
        BandeauCompact(titre = "Ramulus", actions = {
            // Juste à gauche du « ⋮ » : l'icône montre la vue où l'on va passer (comme Surculus et Marculus).
            IconButton(onClick = { modeColonnes = !modeColonnes }) {
                Icon(
                    if (modeColonnes) Icons.AutoMirrored.Filled.ViewList else Icons.Filled.ViewColumn,
                    contentDescription = if (modeColonnes) "Voir la liste" else "Voir le kanban",
                )
            }
            MenuTrois(
                onOuvrir = { choisir.launch(arrayOf("*/*")) },
                onParametres = { vm.enArriere { vm.controleur.versParametres() } },
                onAPropos = { aPropos = true },
            )
        })
        Column(
            Modifier
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = recherche,
                onValueChange = { recherche = it },
                label = { Text("Chercher une tournée") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            etat.erreur?.let { Text(it, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold) }

            val toutes = etat.enCours.map { it.enLigne() }
            val trouvees = filtrer(toutes, recherche)
            when {
                toutes.isEmpty() -> Text(
                    "Aucune tournée sur ce téléphone. Menu ⋮, puis « Ouvrir une tournée ».",
                    style = MaterialTheme.typography.bodyLarge,
                )
                trouvees.isEmpty() -> Text("Aucune tournée trouvée.", style = MaterialTheme.typography.bodyLarge)
                modeColonnes -> {
                    val rafraichi = versionStatuts // relu pour recomposer après un dépôt
                    VueKanban(
                        cartes = trouvees.map { CarteKanban(it, if (rafraichi >= 0) statuts.statut(it.code) else EtatKanban.PROPOSEE) },
                        onOuvrir = { vm.enArriere { vm.controleur.reprendre(it.code) } },
                        onDeposer = { l, nouveau ->
                            statuts.definir(l.code, nouveau)
                            versionStatuts++
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
                else -> ListeDesTournees(repartirListe(trouvees), Modifier.weight(1f)) { vm.enArriere { vm.controleur.reprendre(it.code) } }
            }
            Text(
                "Opérateur : ${etat.operateur ?: "à saisir"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ListeDesTournees(liste: ListeAccueil, modifier: Modifier, onOuvrir: (LigneAccueil) -> Unit) {
    LazyColumn(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (liste.aFaire.isNotEmpty()) {
            item { EnTete("À faire (${liste.aFaire.size})") }
            items(liste.aFaire, key = { "a-" + it.code }) { LigneDeTournee(it) { onOuvrir(it) } }
        }
        if (liste.terminees.isNotEmpty()) {
            item { EnTete("Terminées (${liste.terminees.size})") }
            items(liste.terminees, key = { "t-" + it.code }) { LigneDeTournee(it) { onOuvrir(it) } }
        }
    }
}

@Composable
private fun EnTete(texte: String) {
    Text(
        texte,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp),
    )
}

/** Le code en grand, puis la nature et l'avancement. Toute la ligne est la cible (gants, une main). */
@Composable
private fun LigneDeTournee(ligne: LigneAccueil, onOuvrir: () -> Unit) {
    Column {
        Column(
            Modifier.fillMaxWidth().heightIn(min = 64.dp).clickable(onClick = onOuvrir).padding(vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(ligne.code, style = MaterialTheme.typography.titleLarge)
            Text("${ligne.natureEnClair} · ${ligne.avancement}", style = MaterialTheme.typography.bodyMedium)
        }
        HorizontalDivider()
    }
}

/** Menu « ⋮ » (Icons.Filled.MoreVert, comme Marculus) : Ouvrir une tournée, Paramètres, À propos ; cibles de 48 dp. */
@Composable
private fun MenuTrois(onOuvrir: () -> Unit, onParametres: () -> Unit, onAPropos: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "Menu") }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            for (entree in ENTREES_MENU) {
                DropdownMenuItem(
                    text = { Text(entree.libelle) },
                    modifier = Modifier.heightIn(min = 48.dp),
                    onClick = {
                        menu = false
                        when (entree) {
                            EntreeMenu.OUVRIR_TOURNEE -> onOuvrir()
                            EntreeMenu.PARAMETRES -> onParametres()
                            EntreeMenu.A_PROPOS -> onAPropos()
                        }
                    },
                )
            }
        }
    }
}

/** « À propos » : nom, version (name et code du paquet installé), licence, protocole suivi, lien vers le dépôt. */
@Composable
private fun BoiteAPropos(onFermer: () -> Unit) {
    val ctx = LocalContext.current
    val texte = remember {
        val info = runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0) }.getOrNull()
        aPropos(info?.versionName, info?.let { PackageInfoCompat.getLongVersionCode(it) })
    }
    AlertDialog(
        onDismissRequest = onFermer,
        title = { Text(texte.nom) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(texte.version, style = MaterialTheme.typography.titleMedium)
                Text("Licence : ${texte.licence}", style = MaterialTheme.typography.bodyMedium)
                Text("Protocole : ${texte.protocole}", style = MaterialTheme.typography.bodyMedium)
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    // Protégé contre ActivityNotFoundException : sans navigateur, rien ne plante.
                    runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(URL_DEPOT))) }
                    onFermer()
                },
                modifier = Modifier.height(48.dp),
            ) { Text("Dépôt GitHub") }
        },
        dismissButton = { TextButton(onClick = onFermer, modifier = Modifier.height(48.dp)) { Text("Fermer") } },
    )
}
