package io.github.pobsteta.ramulus.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import io.github.pobsteta.ramulus.core.deperis.Classe

// Thème commun de la plateforme Néméton, repris de Surculus (ui/theme/Theme.kt, mission 038), lui-même recopié de
// Marculus v0.65.0. À remplacer par la dépendance terrain-style dès sa publication.
private val VertMarculus = Color(0xFF374742)
private val VertVif = Color(0xFF3E7A40)
private val Creme = Color(0xFFF5EBD8)

val SchemaClair: ColorScheme = lightColorScheme(
    primary = VertVif,
    onPrimary = Color.White,
    primaryContainer = VertMarculus,
    onPrimaryContainer = Creme,
    secondary = VertMarculus,
)

val SchemaSombre: ColorScheme = darkColorScheme(
    primary = VertVif,
    onPrimary = Color.White,
    primaryContainer = VertMarculus,
    onPrimaryContainer = Creme,
    secondary = Creme,
)

@Composable
fun RamulusTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) SchemaSombre else SchemaClair, content = content)
}

/** Couleur de chaque classe DEPERIS, du vert (A) au brun (F) ; texte blanc lisible sur chacune. */
fun couleurClasse(c: Classe): Color = when (c) {
    Classe.A -> Color(0xFF2F7D4F)
    Classe.B -> Color(0xFF4F8A3A)
    Classe.C -> Color(0xFF8F7208)
    Classe.D -> Color(0xFFB0581A)
    Classe.E -> Color(0xFFA03E24)
    Classe.F -> Color(0xFF7A2A1F)
}
