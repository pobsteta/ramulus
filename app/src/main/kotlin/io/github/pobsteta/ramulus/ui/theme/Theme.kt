package io.github.pobsteta.ramulus.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import fr.nemeton.terrain.style.Schema
import fr.nemeton.terrain.style.StyleTerrain
import io.github.pobsteta.ramulus.core.deperis.Classe

// Le thème vient de terrain-style v0.7.1 (bb2b519), comme Surculus (ui/theme/Theme.kt au commit b73c493, mission 069) :
// aucune couleur n'est écrite ici, sauf celles des classes DEPERIS plus bas. Les valeurs sont des ARGB 0xFFRRGGBB en Long.

private fun Schema.versColorScheme(sombre: Boolean): ColorScheme {
    fun c(v: Long) = Color(v.toInt())
    return (if (sombre) darkColorScheme() else lightColorScheme()).copy(
        primary = c(primary), onPrimary = c(onPrimary),
        primaryContainer = c(primaryContainer), onPrimaryContainer = c(onPrimaryContainer),
        inversePrimary = c(inversePrimary),
        secondary = c(secondary), onSecondary = c(onSecondary),
        secondaryContainer = c(secondaryContainer), onSecondaryContainer = c(onSecondaryContainer),
        tertiary = c(tertiary), onTertiary = c(onTertiary),
        tertiaryContainer = c(tertiaryContainer), onTertiaryContainer = c(onTertiaryContainer),
        background = c(background), onBackground = c(onBackground),
        surface = c(surface), onSurface = c(onSurface),
        surfaceVariant = c(surfaceVariant), onSurfaceVariant = c(onSurfaceVariant),
        surfaceTint = c(surfaceTint),
        inverseSurface = c(inverseSurface), inverseOnSurface = c(inverseOnSurface),
        error = c(error), onError = c(onError), errorContainer = c(errorContainer), onErrorContainer = c(onErrorContainer),
        outline = c(outline), outlineVariant = c(outlineVariant), scrim = c(scrim),
        surfaceBright = c(surfaceBright), surfaceDim = c(surfaceDim),
        surfaceContainer = c(surfaceContainer), surfaceContainerHigh = c(surfaceContainerHigh),
        surfaceContainerHighest = c(surfaceContainerHighest), surfaceContainerLow = c(surfaceContainerLow),
        surfaceContainerLowest = c(surfaceContainerLowest),
        primaryFixed = c(primaryFixed), primaryFixedDim = c(primaryFixedDim),
        onPrimaryFixed = c(onPrimaryFixed), onPrimaryFixedVariant = c(onPrimaryFixedVariant),
        secondaryFixed = c(secondaryFixed), secondaryFixedDim = c(secondaryFixedDim),
        onSecondaryFixed = c(onSecondaryFixed), onSecondaryFixedVariant = c(onSecondaryFixedVariant),
        tertiaryFixed = c(tertiaryFixed), tertiaryFixedDim = c(tertiaryFixedDim),
        onTertiaryFixed = c(onTertiaryFixed), onTertiaryFixedVariant = c(onTertiaryFixedVariant),
    )
}

val SchemaClair: ColorScheme = StyleTerrain.clair.versColorScheme(sombre = false)
val SchemaSombre: ColorScheme = StyleTerrain.sombre.versColorScheme(sombre = true)

/** Thème choisi dans Paramètres : [ChoixTheme.SYSTEME] par défaut (comme Marculus et Surculus). */
@Composable
fun RamulusTheme(choix: ChoixTheme = ChoixTheme.SYSTEME, content: @Composable () -> Unit) {
    val sombre = choix.estSombre(isSystemInDarkTheme())
    MaterialTheme(colorScheme = if (sombre) SchemaSombre else SchemaClair, content = content)
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
