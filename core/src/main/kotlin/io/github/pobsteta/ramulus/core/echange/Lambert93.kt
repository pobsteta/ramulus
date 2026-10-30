package io.github.pobsteta.ramulus.core.echange

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * Lambert-93 (EPSG:2154, RGF93 v1) : conique conforme de Lambert à deux parallèles (EPSG, méthode 9802), sur
 * l'ellipsoïde GRS 1980. Coordonnées : X = est, Y = nord, en mètres ; longitude puis latitude, en degrés.
 *
 * Repris du contrôle de référence (`fixtures/echange/deperis_v1.py`, `l93_vers_lonlat`), qui fait foi pour les cas communs.
 */
internal object Lambert93 {
    private const val A = 6378137.0
    private const val F = 1 / 298.257222101
    private val E = sqrt(2 * F - F * F)
    private val LAT0 = Math.toRadians(46.5)
    private val LAT1 = Math.toRadians(49.0)
    private val LAT2 = Math.toRadians(44.0)
    private val LON0 = Math.toRadians(3.0)
    private const val X0 = 700000.0
    private const val Y0 = 6600000.0

    /** Emprise d'EPSG:2154 en degrés : ouest, sud, est, nord (domaine d'emploi EPSG, lu dans PROJ le 2026-10-28). */
    const val OUEST = -9.86
    const val SUD = 41.15
    const val EST = 10.38
    const val NORD = 51.56

    private fun m(phi: Double) = cos(phi) / sqrt(1 - (E * sin(phi)).pow(2))

    private fun t(phi: Double): Double {
        val es = E * sin(phi)
        return tan(PI / 4 - phi / 2) / ((1 - es) / (1 + es)).pow(E / 2)
    }

    private val N = (ln(m(LAT1)) - ln(m(LAT2))) / (ln(t(LAT1)) - ln(t(LAT2)))
    private val FF = m(LAT1) / (N * t(LAT1).pow(N))
    private val R0 = A * FF * t(LAT0).pow(N)

    /** (X est, Y nord) en mètres -> (longitude, latitude) en degrés. */
    fun versLonLat(x: Double, y: Double): Pair<Double, Double> {
        val dx = x - X0
        val dy = R0 - (y - Y0)
        val r = hypot(dx, dy) * sign(N)
        if (r == 0.0) return Math.toDegrees(LON0) to 90.0 * sign(N)
        val tt = (r / (A * FF)).pow(1 / N)
        val theta = atan2(dx, dy)
        val lon = theta / N + LON0
        var phi = PI / 2 - 2 * atan(tt)
        for (i in 0 until 20) {
            val es = E * sin(phi)
            val suivant = PI / 2 - 2 * atan(tt * ((1 - es) / (1 + es)).pow(E / 2))
            val fini = abs(suivant - phi) < 1e-14
            phi = suivant
            if (fini) break
        }
        return Math.toDegrees(lon) to Math.toDegrees(phi)
    }

    /** Vrai si le point (X est, Y nord, EPSG:2154) tombe dans l'emprise d'EPSG:2154 : « en France » (Pascal, Q3). */
    fun enFrance(x: Double, y: Double): Boolean {
        if (!x.isFinite() || !y.isFinite()) return false
        val (lon, lat) = versLonLat(x, y)
        return lon in OUEST..EST && lat in SUD..NORD
    }
}
