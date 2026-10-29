package io.github.pobsteta.ramulus.core.gnss

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class CentreGnssTest {
    // Références : EPSG:4326 -> EPSG:2154 par PROJ (pyproj 3.6.1, always_xy). PROJ fait foi ; l'origine (3° E, 46,5° N) est la définition de Lambert-93.
    private data class Ref(val nom: String, val lat: Double, val lon: Double, val x: Double, val y: Double)

    private val references = listOf(
        Ref("origine", 46.5, 3.0, 700000.0, 6600000.0),
        Ref("Paris", 48.8566, 2.3522, 652469.0227, 6862035.2594),
        Ref("Nancy", 48.6921, 6.1844, 934325.4277, 6848275.6184),
        Ref("Ajaccio", 41.9192, 8.7386, 1176667.5614, 6108273.2834),
        Ref("Brest", 48.3904, -4.4861, 146632.9785, 6836262.3267),
    )

    @Test
    fun `points connus convertis en Lambert-93 au centimetre pres`() {
        for (r in references) {
            val p = Lambert93.depuisWgs84(PositionWgs84(latitudeDeg = r.lat, longitudeDeg = r.lon))
            assertEquals(r.x, p.xMetres, 0.01, "x de ${r.nom}")
            assertEquals(r.y, p.yMetres, 0.01, "y de ${r.nom}")
        }
    }

    @Test
    fun `un point de France metropolitaine tombe dans l'emprise de France`() {
        for (r in references) assertTrue(EmpriseDegres.FRANCE_METROPOLITAINE.contient(PositionWgs84(r.lat, r.lon)), r.nom)
    }

    private fun mesure(lat: Double, lon: Double, prec: Double = 5.0, t: Long = 0L) =
        MesureGnss(PositionWgs84(latitudeDeg = lat, longitudeDeg = lon), prec, t)

    @Test
    fun `moyenne, precision, nb_mesures et date sont justes`() {
        // Trois mesures au même point de Paris, de précisions 4, 6 et 8 m, dans le désordre : la précision moyenne
        // est 6 m, la date est celle de la plus récente, la dispersion est nulle.
        val paris = Lambert93.depuisWgs84(PositionWgs84(48.8566, 2.3522))
        val ms = listOf(
            mesure(48.8566, 2.3522, prec = 4.0, t = 1_000),
            mesure(48.8566, 2.3522, prec = 6.0, t = 3_000),
            mesure(48.8566, 2.3522, prec = 8.0, t = 2_000),
        )
        val c = assertIs<ResultatReleve.Centre>(MoyenneGnss.moyenner(ms)).centre
        assertEquals(3, c.nbMesures)
        assertEquals(6.0, c.precisionM, 1e-9)
        assertEquals(paris.xMetres, c.position.xMetres, 1e-6)
        assertEquals(paris.yMetres, c.position.yMetres, 1e-6)
        assertEquals(0.0, c.dispersionM, 1e-6)
        assertEquals(3_000L, c.dateReleve.toEpochMilli())
        assertEquals("telephone", c.source)
    }

    @Test
    fun `la moyenne de deux mesures est le milieu en Lambert-93`() {
        val a = Lambert93.depuisWgs84(PositionWgs84(48.0, 2.0))
        val b = Lambert93.depuisWgs84(PositionWgs84(48.001, 2.001))
        val c = assertIs<ResultatReleve.Centre>(
            MoyenneGnss.moyenner(listOf(mesure(48.0, 2.0), mesure(48.001, 2.001))),
        ).centre
        assertEquals((a.xMetres + b.xMetres) / 2, c.position.xMetres, 1e-6)
        assertEquals((a.yMetres + b.yMetres) / 2, c.position.yMetres, 1e-6)
        assertEquals(a.distanceA(b) / 2, c.dispersionM, 1e-6)
    }

    @Test
    fun `aucune mesure n'est pas un centre`() {
        assertEquals(ResultatReleve.AucuneMesure, MoyenneGnss.moyenner(emptyList()))
    }

    @Test
    fun `la placette en Somalie est refusee`() {
        val somalie = mesure(lat = 2.05, lon = 45.32)
        assertEquals(ResultatReleve.HorsFrance(somalie), MoyenneGnss.moyenner(listOf(mesure(48.0, 2.0), somalie)))
    }

    @Test
    fun `des axes inverses sont refuses`() {
        // Paris avec latitude et longitude échangées : lat 2,35, lon 48,86.
        assertIs<ResultatReleve.HorsFrance>(MoyenneGnss.moyenner(listOf(mesure(lat = 2.3522, lon = 48.8566))))
    }

    @Test
    fun `une precision negative ou non finie est refusee`() {
        assertFailsWith<IllegalArgumentException> { mesure(48.0, 2.0, prec = -1.0) }
        assertFailsWith<IllegalArgumentException> { mesure(48.0, 2.0, prec = Double.NaN) }
    }

    private val prevu = PointLambert93(xMetres = 652469.0, yMetres = 6862035.0)

    @Test
    fun `un ecart de plus de 30 m est signale`() {
        val e = EcartAuPointPrevu.entre(PointLambert93(652469.0 + 30.01, 6862035.0), prevu)
        assertTrue(e.signale)
        assertEquals(30.01, e.metres, 1e-6)
    }

    @Test
    fun `un ecart de 30 m ou moins n'est pas signale`() {
        assertFalse(EcartAuPointPrevu.entre(PointLambert93(652469.0 + 30.0, 6862035.0), prevu).signale)
        assertFalse(EcartAuPointPrevu.entre(PointLambert93(652469.0 + 18.0, 6862035.0 + 24.0), prevu).signale) // 30 m en diagonale
        assertFalse(EcartAuPointPrevu.entre(prevu, prevu).signale)
    }

    @Test
    fun `le seuil est le seuil provisoire de 30 m`() {
        assertEquals(30.0, EcartAuPointPrevu.SEUIL_PROVISOIRE_M)
    }

    @Test
    fun `la collecte s'arrete a la cible et moyenne ce qu'elle a`() {
        val collecte = CollecteCentre(nbCible = 3)
        assertEquals(ResultatReleve.AucuneMesure, collecte.resultat())
        repeat(5) { collecte.ajouter(mesure(48.0, 2.0, t = it.toLong())) }
        assertTrue(collecte.complete)
        assertEquals(3, collecte.nbMesures)
        assertEquals(3, assertIs<ResultatReleve.Centre>(collecte.resultat()).centre.nbMesures)
    }

    @Test
    fun `l'operateur peut valider avant la cible`() {
        val collecte = CollecteCentre()
        assertEquals(10, collecte.nbCible)
        collecte.ajouter(mesure(48.0, 2.0))
        assertFalse(collecte.complete)
        assertEquals(1, assertIs<ResultatReleve.Centre>(collecte.resultat()).centre.nbMesures)
    }

    @Test
    fun `une collecte sans cible est refusee`() {
        assertFailsWith<IllegalArgumentException> { CollecteCentre(0) }
    }
}
