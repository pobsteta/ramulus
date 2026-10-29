package io.github.pobsteta.ramulus.notation

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.pobsteta.ramulus.core.deperis.Classe
import io.github.pobsteta.ramulus.core.deperis.Essences
import io.github.pobsteta.ramulus.core.deperis.Note
import io.github.pobsteta.ramulus.core.deperis.Saison
import io.github.pobsteta.ramulus.core.session.SessionNotation
import io.github.pobsteta.ramulus.ui.theme.couleurClasse

/**
 * Écran de notation de la tranche 0 : l'opérateur choisit l'essence et la saison, pose MB et MR (ou MA), lit la
 * classe donnée par l'abaque, enregistre et passe à l'arbre suivant. Le bilan du peuplement suit en bas d'écran.
 *
 * Gros boutons (gants, soleil) ; aucune saisie au clavier. Pas encore de photo ni d'IA : missions suivantes.
 */
@Composable
fun EcranNotation() {
    var session by remember { mutableStateOf(SessionNotation(Essences.toutes.first { it.nom == "Chêne sessile" })) }

    Scaffold { marges ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(marges)
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            ChoixEssence(session) { session = it }
            LigneNotes(
                titre = "Mortalité de branches (MB)",
                choisie = session.mb,
            ) { session = session.avecMb(it) }
            LigneNotes(
                titre = session.essence.type.secondCritere.libelle + " (" + session.essence.type.secondCritere.name + ")",
                choisie = session.second,
            ) { session = session.avecSecond(it) }
            ClasseEnCours(session.classeEnCours)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = { session = session.enregistrer() },
                    enabled = session.classeEnCours != null,
                    modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                ) { Text("Enregistrer l'arbre", fontSize = 17.sp) }
                OutlinedButton(
                    onClick = { session = session.annulerDernier() },
                    enabled = session.notees.isNotEmpty(),
                    modifier = Modifier.heightIn(min = 56.dp),
                ) { Text("Annuler le dernier") }
            }
            BilanSession(session)
        }
    }
}

@Composable
private fun ChoixEssence(session: SessionNotation, changer: (SessionNotation) -> Unit) {
    var ouvert by rememberSaveable { mutableStateOf(false) }
    val contexte = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Ramulus · notation DEPERIS", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.weight(1f)) {
                OutlinedButton(onClick = { ouvert = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                    Text(session.essence.nom, fontSize = 17.sp)
                }
                DropdownMenu(expanded = ouvert, onDismissRequest = { ouvert = false }) {
                    for (e in Essences.toutes) {
                        DropdownMenuItem(
                            text = { Text(e.nom) },
                            onClick = {
                                changer(session.avecEssence(e))
                                ouvert = false
                            },
                        )
                    }
                }
            }
            for (s in Saison.entries) {
                val actif = session.saison == s
                val libelle = if (s == Saison.ETE) "Été" else "Hiver"
                if (actif) {
                    Button(onClick = {}, modifier = Modifier.heightIn(min = 52.dp)) { Text(libelle) }
                } else {
                    OutlinedButton(onClick = { changer(session.avecSaison(s)) }, modifier = Modifier.heightIn(min = 52.dp)) {
                        Text(libelle)
                    }
                }
            }
        }
        val fiche = session.essence.fiche(session.saison)
        if (fiche != null) {
            TextButton(onClick = { contexte.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(fiche))) }) {
                Text("Ouvrir la fiche de référence du DSF")
            }
        } else {
            Text(
                "Le DSF ne publie pas de fiche ${if (session.saison == Saison.HIVER) "d'hiver" else "d'été"} pour cette essence.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
internal fun LigneNotes(titre: String, choisie: Note?, choisir: (Note) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(titre, style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (n in Note.TOUTES) {
                val actif = n == choisie
                val contenu: @Composable () -> Unit = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(n.toString(), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text("${n.pourcentMin}–${n.pourcentMax}", fontSize = 10.sp)
                    }
                }
                val mod = Modifier.weight(1f).heightIn(min = 64.dp)
                if (actif) {
                    Button(onClick = { choisir(n) }, modifier = mod, contentPadding = PaddingValues(2.dp)) { contenu() }
                } else {
                    OutlinedButton(onClick = { choisir(n) }, modifier = mod, contentPadding = PaddingValues(2.dp)) { contenu() }
                }
            }
        }
        if (choisie != null) {
            Text(choisie.intensite, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
internal fun ClasseEnCours(classe: Classe?) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(
            Modifier
                .size(72.dp)
                .background(classe?.let(::couleurClasse) ?: Color.Gray, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(classe?.name ?: "?", color = Color.White, fontSize = 40.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        }
        Text(
            when (classe?.etat) {
                null -> "Posez MB et le second critère"
                Classe.Etat.SAIN -> "Arbre sain"
                Classe.Etat.INTERMEDIAIRE -> "Classe intermédiaire"
                Classe.Etat.DEGRADE -> "Arbre dégradé"
                Classe.Etat.TRES_DEGRADE -> "Arbre très dégradé"
            },
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

@Composable
private fun BilanSession(session: SessionNotation) {
    val b = session.bilan
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Spacer(Modifier.height(4.dp))
        Text("Peuplement : ${b.effectif} arbre${if (b.effectif > 1) "s" else ""} noté${if (b.effectif > 1) "s" else ""}", style = MaterialTheme.typography.titleMedium)
        if (b.effectif > 0) {
            Text(Classe.entries.joinToString("   ") { "${it.name} ${b.parClasse.getValue(it)}" })
            Text("Dégradés (D, E, F) : ${Math.round(b.partDegrades * 100)} %   ·   très dégradés (E, F) : ${Math.round(b.partTresDegrades * 100)} %")
            Text(
                when {
                    b.estTresDegrade -> "Peuplement très dégradé (plus de 20 % d'arbres E ou F)"
                    b.estDegrade -> "Peuplement dégradé (plus de 20 % d'arbres D, E ou F)"
                    else -> "Peuplement non dégradé"
                },
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}
