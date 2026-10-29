package io.github.pobsteta.ramulus.core.gnss

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * Point en Lambert-93 (RGF93 / Lambert-93, **EPSG:2154**), en **mètres**.
 * [xMetres] est l'abscisse (vers l'est), [yMetres] l'ordonnée (vers le nord) : l'ordre des axes d'EPSG:2154.
 */
data class PointLambert93(val xMetres: Double, val yMetres: Double) {
    init {
        require(xMetres.isFinite() && yMetres.isFinite()) { "Coordonnées Lambert-93 non finies : x = $xMetres, y = $yMetres" }
    }

    /** Distance plane, en mètres, jusqu'à [autre]. */
    fun distanceA(autre: PointLambert93): Double =
        sqrt((xMetres - autre.xMetres).pow(2) + (yMetres - autre.yMetres).pow(2))
}

/** Position géographique WGS 84, en **degrés décimaux**, ordre explicite : latitude puis longitude. */
data class PositionWgs84(val latitudeDeg: Double, val longitudeDeg: Double) {
    init {
        require(latitudeDeg.isFinite() && longitudeDeg.isFinite()) { "Position non finie" }
    }
}

/**
 * Emprise rectangulaire en degrés (WGS 84), bornes incluses. Pour la France : domaine d'usage d'EPSG:2154, registre
 * EPSG, `BBOX[41.15, -9.86, 51.56, 10.38]` (latitude min, longitude min, latitude max, longitude max). C'est un
 * rectangle : il déborde sur la mer et les pays voisins ; il attrape l'inversion des axes et une position lointaine
 * (la Somalie), pas un point en Suisse. Même rectangle que Surculus (`EmpriseDegres.FRANCE_METROPOLITAINE`).
 */
data class EmpriseDegres(
    val latitudeMinDeg: Double,
    val longitudeMinDeg: Double,
    val latitudeMaxDeg: Double,
    val longitudeMaxDeg: Double,
) {
    fun contient(p: PositionWgs84): Boolean =
        p.latitudeDeg in latitudeMinDeg..latitudeMaxDeg && p.longitudeDeg in longitudeMinDeg..longitudeMaxDeg

    companion object {
        val FRANCE_METROPOLITAINE = EmpriseDegres(41.15, -9.86, 51.56, 10.38)
    }
}

/**
 * WGS 84 → Lambert-93 (conique conforme à deux parallèles sur GRS80), note IGN NTG_71 : latitude isométrique (ALG0001) et projection
 * (ALG0003) ; les autres numéros d'algorithmes cités auparavant (ALG0004, ALG0054) sont non vérifiés et retirés.
 * Le résultat fait foi par comparaison avec PROJ (voir CentreGnssTest).
 * Paramètres IGN de Lambert-93 : parallèles d'échelle 44° et 49° N, origine 46,5° N et 3° E, X0 = 700 000 m,
 * Y0 = 6 600 000 m. RGF93 et WGS 84 sont pris pour le même datum (quelques centimètres, sous ce que mesure un téléphone).
 */
object Lambert93 {
    private const val A = 6378137.0
    private const val F = 1 / 298.257222101
    private val E = sqrt(F * (2 - F))
    private const val X0 = 700000.0
    private const val Y0 = 6600000.0
    private val LON0 = Math.toRadians(3.0)
    private val PHI1 = Math.toRadians(44.0)
    private val PHI2 = Math.toRadians(49.0)
    private val PHI0 = Math.toRadians(46.5)

    private fun m(phi: Double) = cos(phi) / sqrt(1 - (E * sin(phi)).pow(2))
    private fun t(phi: Double) =
        tan(PI / 4 - phi / 2) / ((1 - E * sin(phi)) / (1 + E * sin(phi))).pow(E / 2)

    private val N = (ln(m(PHI1)) - ln(m(PHI2))) / (ln(t(PHI1)) - ln(t(PHI2)))
    private val C = A * m(PHI1) / (N * t(PHI1).pow(N))
    private val RHO0 = C * t(PHI0).pow(N)

    /** Position WGS 84 (degrés) → Lambert-93 (mètres). */
    fun depuisWgs84(p: PositionWgs84): PointLambert93 {
        val rho = C * t(Math.toRadians(p.latitudeDeg)).pow(N)
        val theta = N * (Math.toRadians(p.longitudeDeg) - LON0)
        return PointLambert93(X0 + rho * sin(theta), Y0 + RHO0 - rho * cos(theta))
    }
}
