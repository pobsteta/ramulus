package io.github.pobsteta.ramulus.accueil

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * Vue kanban des tournées : une colonne par état, appui long puis glissé pour changer de colonne, appui court pour ouvrir
 * la tournée. [onDeposer] reçoit le nouveau statut à garder ; un dépôt sous le minimum imposé par l'avancement n'est pas
 * transmis. Repris de Surculus (`tournee/VueKanban.kt`, commit b73c493, mission 043), lui-même calqué sur Marculus.
 */
@Composable
fun VueKanban(
    cartes: List<CarteKanban>,
    onOuvrir: (LigneAccueil) -> Unit,
    onDeposer: (LigneAccueil, EtatKanban) -> Unit,
    modifier: Modifier = Modifier,
) {
    var tiree by remember { mutableStateOf<CarteKanban?>(null) }
    var doigt by remember { mutableStateOf(Offset.Zero) }
    var origine by remember { mutableStateOf(Offset.Zero) }
    val rectsColonnes = remember { mutableStateMapOf<EtatKanban, Rect>() }
    val colonnes = repartir(cartes) { it.colonne }

    Box(modifier.fillMaxSize().onGloballyPositioned { origine = it.boundsInRoot().topLeft }) {
        Row(Modifier.fillMaxSize().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            EtatKanban.entries.forEach { etat ->
                val cartesDeLaColonne = colonnes.getValue(etat)
                Column(Modifier.width(260.dp).fillMaxHeight().onGloballyPositioned { rectsColonnes[etat] = it.boundsInRoot() }) {
                    Text(
                        "${etat.libelle} (${cartesDeLaColonne.size})",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(8.dp),
                    )
                    LazyColumn(contentPadding = PaddingValues(bottom = 80.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(cartesDeLaColonne, key = { it.ligne.code }) { carte ->
                            CarteDeKanban(
                                carte = carte,
                                estTiree = tiree?.ligne?.code == carte.ligne.code,
                                onOuvrir = { onOuvrir(carte.ligne) },
                                onDebut = { p -> tiree = carte; doigt = p },
                                onGlisse = { p -> doigt = p },
                                onFin = {
                                    val cible = rectsColonnes.entries.firstOrNull { it.value.contains(doigt) }?.key
                                    cible?.let { deposer(carte.stocke, it, carte.minimal) }?.let { onDeposer(carte.ligne, it) }
                                    tiree = null
                                },
                                onAnnule = { tiree = null },
                            )
                        }
                    }
                }
            }
        }
        tiree?.let { c ->
            ElevatedCard(
                Modifier
                    .offset { IntOffset((doigt.x - origine.x - 110.dp.toPx()).roundToInt(), (doigt.y - origine.y - 24.dp.toPx()).roundToInt()) }
                    .width(220.dp),
            ) {
                Text(
                    c.ligne.code,
                    modifier = Modifier.padding(10.dp),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun CarteDeKanban(
    carte: CarteKanban,
    estTiree: Boolean,
    onOuvrir: () -> Unit,
    onDebut: (Offset) -> Unit,
    onGlisse: (Offset) -> Unit,
    onFin: () -> Unit,
    onAnnule: () -> Unit,
) {
    var coin by remember { mutableStateOf(Offset.Zero) }
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { coin = it.boundsInRoot().topLeft }
            .graphicsLayer { alpha = if (estTiree) 0.3f else 1f }
            .pointerInput(carte.ligne.code) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { offset -> onDebut(coin + offset) },
                    onDrag = { change, _ -> change.consume(); onGlisse(coin + change.position) },
                    onDragEnd = { onFin() },
                    onDragCancel = { onAnnule() },
                )
            }
            .clickable(onClick = onOuvrir),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(carte.ligne.code, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                "${carte.ligne.natureEnClair} · ${carte.ligne.avancement}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
