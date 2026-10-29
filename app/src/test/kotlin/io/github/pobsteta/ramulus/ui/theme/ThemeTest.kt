package io.github.pobsteta.ramulus.ui.theme

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class ThemeTest {
    @Test
    fun `le bandeau est en VertVif de Marculus, texte blanc, dans les deux themes`() {
        assertEquals(Color(0xFF3E7A40), SchemaClair.primary)
        assertEquals(Color(0xFF3E7A40), SchemaSombre.primary)
        assertEquals(Color.White, SchemaClair.onPrimary)
        assertEquals(Color.White, SchemaSombre.onPrimary)
    }

    @Test
    fun `clair et sombre different`() {
        assertNotEquals(SchemaClair.background, SchemaSombre.background)
    }

    @Test
    fun `Systeme suit le telephone, Clair et Sombre le forcent`() {
        assertTrue(ChoixTheme.SYSTEME.estSombre(true))
        assertFalse(ChoixTheme.SYSTEME.estSombre(false))
        assertFalse(ChoixTheme.CLAIR.estSombre(true))
        assertTrue(ChoixTheme.SOMBRE.estSombre(false))
    }

    @Test
    fun `un choix absent ou inconnu donne Systeme`() {
        assertEquals(ChoixTheme.SYSTEME, ChoixTheme.depuis(null))
        assertEquals(ChoixTheme.SYSTEME, ChoixTheme.depuis("ROSE"))
        assertEquals(ChoixTheme.SOMBRE, ChoixTheme.depuis("SOMBRE"))
    }
}
