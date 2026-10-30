package io.github.pobsteta.ramulus.accueil

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import io.github.pobsteta.ramulus.tournee.EtatTournee
import io.github.pobsteta.ramulus.tournee.TourneeViewModel
import io.github.pobsteta.ramulus.ui.BandeauCompact
import io.github.pobsteta.ramulus.ui.theme.ChoixTheme

/**
 * Paramètres : un seul écran, en sections (Opérateur, Thème, GNSS, Sauvegarde). Structure du thème reprise de Surculus
 * (`parametres/EcranParametres.kt`, commit b73c493) et de Marculus (`ChoixThemeReglage`, ADR 0005). Gros boutons − et + :
 * pas de clavier pour les chiffres (gants, une main).
 */
@Composable
fun EcranParametres(vm: TourneeViewModel, etat: EtatTournee, reglages: ReglagesRamulus) {
    Column(Modifier.fillMaxSize()) {
        BandeauCompact(
            titre = "Paramètres",
            navigationIcon = {
                IconButton(onClick = { vm.enArriere { vm.controleur.retour() } }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                }
            },
        )
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Section("Opérateur")
            SectionOperateur(etat, vm)

            Section("Affichage")
            LignePleinEcran(reglages.pleinEcran) { reglages.definirPleinEcran(it) }

            Section("Thème")
            LigneTheme(reglages.choixTheme) { reglages.definirTheme(it) }

            Section("GNSS")
            Text(
                "Valeurs provisoires de la mission 007, à mesurer sur le terrain avant la première campagne.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Reglette("Mesures moyennées", "${reglages.nbMesuresGnss}", "10 par défaut",
                onMoins = { reglages.definirNbMesures(reglages.nbMesuresGnss - 1) },
                onPlus = { reglages.definirNbMesures(reglages.nbMesuresGnss + 1) })
            Reglette("Écart au point prévu", "${reglages.seuilEcartM} m", "30 m par défaut ; au-delà, un signal",
                onMoins = { reglages.definirSeuilEcart(reglages.seuilEcartM - ReglagesRamulus.PAS_SEUIL_M) },
                onPlus = { reglages.definirSeuilEcart(reglages.seuilEcartM + ReglagesRamulus.PAS_SEUIL_M) })

            Text(
                "Alerte au relevé seulement ; le contrôle du rendu garde 30 m (format deperis-v1).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Section("Photos")
            Text(
                "Seuils provisoires de la mission 008, affichés ici ; Pascal les règle sur des photos de forêt avant le gel.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            for (s in seuilsPhotos()) {
                Row(Modifier.fillMaxWidth().heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(s.nom, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Text(s.valeur, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            }

            Section("Sauvegarde")
            Text("Dernière sauvegarde .ramsync", style = MaterialTheme.typography.titleMedium)
            Text(texteDerniereSauvegarde(reglages.derniereSauvegarde), style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun Section(titre: String) {
    HorizontalDivider()
    Text(titre, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
}

@Composable
private fun SectionOperateur(etat: EtatTournee, vm: TourneeViewModel) {
    var nom by remember(etat.operateur) { mutableStateOf(etat.operateur.orEmpty()) }
    OutlinedTextField(
        value = nom,
        onValueChange = { nom = it },
        label = { Text("Votre nom") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
        modifier = Modifier.fillMaxWidth(),
    )
    etat.erreur?.let { Text(it, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold) }
    Button(
        onClick = { vm.enArriere { vm.controleur.renommerOperateur(nom) } },
        enabled = nom.isNotBlank() && nom.trim() != etat.operateur,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
    ) { Text("Enregistrer le nom") }
}

/** Case à bascule « Plein écran » (comme Surculus, mission 044) : toute la ligne est la cible (48 dp, une main, gants). */
@Composable
private fun LignePleinEcran(valeur: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp)
            .toggleable(value = valeur, role = Role.Switch, onValueChange = onChange)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("Plein écran", style = MaterialTheme.typography.titleMedium)
            Text("Masque les barres du système ; elles reviennent en glissant depuis le bord.", style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = valeur, onCheckedChange = null)
    }
}

/** Liste Système / Clair / Sombre avec une coche sur le choix actuel, comme `ChoixThemeReglage` de Marculus. */
@Composable
private fun LigneTheme(valeur: ChoixTheme, onChange: (ChoixTheme) -> Unit) {
    var ouvert by remember { mutableStateOf(false) }
    fun libelle(c: ChoixTheme) = when (c) {
        ChoixTheme.SYSTEME -> "Système"
        ChoixTheme.CLAIR -> "Clair"
        ChoixTheme.SOMBRE -> "Sombre"
    }
    Box {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.DropdownList) { ouvert = true },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(libelle(valeur), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(expanded = ouvert, onDismissRequest = { ouvert = false }) {
            for (choix in ChoixTheme.entries) {
                DropdownMenuItem(
                    text = { Text(libelle(choix)) },
                    modifier = Modifier.heightIn(min = 48.dp),
                    trailingIcon = if (choix == valeur) ({ Icon(Icons.Filled.Check, contentDescription = "Choix actuel") }) else null,
                    onClick = { onChange(choix); ouvert = false },
                )
            }
        }
    }
}

/** Une valeur réglable par − et + (64 dp : gants). */
@Composable
private fun Reglette(titre: String, valeur: String, aide: String, onMoins: () -> Unit, onPlus: () -> Unit) {
    Column {
        Text(titre, style = MaterialTheme.typography.titleMedium)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onMoins, Modifier.size(64.dp)) { Text("−", style = MaterialTheme.typography.headlineSmall) }
            Text(valeur, Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            OutlinedButton(onClick = onPlus, Modifier.size(64.dp)) { Text("+", style = MaterialTheme.typography.headlineSmall) }
        }
        Text(aide, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
