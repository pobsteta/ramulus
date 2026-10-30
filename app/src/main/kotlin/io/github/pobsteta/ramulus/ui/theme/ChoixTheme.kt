package io.github.pobsteta.ramulus.ui.theme

/**
 * Choix du thème : suivre le téléphone, ou forcer le clair ou le sombre. Repris de Surculus
 * (`ui/theme/ChoixTheme.kt`, commit b73c493, mission 051), lui-même calqué sur Marculus (ADR 0005, v0.65.0).
 */
enum class ChoixTheme {
    SYSTEME, CLAIR, SOMBRE;

    /** Vrai si l'application doit s'afficher en sombre, [systemeSombre] étant l'état du téléphone. */
    fun estSombre(systemeSombre: Boolean): Boolean = when (this) {
        SYSTEME -> systemeSombre
        CLAIR -> false
        SOMBRE -> true
    }

    companion object {
        /** Relit un choix enregistré (nom de l'énumération). Absent ou inconnu : [SYSTEME], le défaut. */
        fun depuis(nom: String?): ChoixTheme = entries.firstOrNull { it.name == nom } ?: SYSTEME
    }
}
