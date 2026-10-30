package io.github.pobsteta.ramulus.core.gnss

import java.time.Instant
import kotlin.math.sqrt

/**
 * Une mesure du GNSS du téléphone : position WGS 84, précision horizontale déclarée par le téléphone (rayon à
 * 68 % pour Android, en mètres) et instant UTC de la mesure.
 */
data class MesureGnss(val position: PositionWgs84, val precisionM: Double, val instantUtcMs: Long) {
    init {
        require(precisionM.isFinite() && precisionM >= 0) { "Précision horizontale invalide : $precisionM" }
    }
}

/**
 * Centre relevé, tel que `centres` le porte (docs/formats/deperis-v1.md) : [position] en Lambert-93 (EPSG:2154),
 * [precisionM] (`precision_m`), [nbMesures] (`nb_mesures`), [dateReleve] (`date_releve`, instant de la dernière
 * mesure). La source est toujours `telephone` ici (le RTK n'est pas dans cette mission).
 *
 * [dispersionM] n'est pas dans `centres` : écart quadratique moyen des mesures à leur moyenne, pour l'affichage.
 */
data class CentreReleve(
    val position: PointLambert93,
    val precisionM: Double,
    val nbMesures: Int,
    val dateReleve: Instant,
    val dispersionM: Double,
) {
    val source: String get() = SOURCE_TELEPHONE

    companion object {
        const val SOURCE_TELEPHONE = "telephone"
    }
}

/** Écart du centre relevé au point prévu. [signale] vaut vrai au-delà du seuil (`centre_eloigne`), sans bloquer. */
data class EcartAuPointPrevu(val metres: Double, val signale: Boolean) {
    companion object {
        /**
         * Seuil du signal `centre_eloigne`, en mètres. **Valeur provisoire** (ADR 0004, décision G1) : Pascal la
         * mesure par 5 relevés d'un même piquet sous couvert ; gel avant la première campagne. Strictement
         * supérieur : un écart d'exactement 30 m n'est pas signalé.
         */
        const val SEUIL_PROVISOIRE_M = 30.0

        fun entre(centre: PointLambert93, prevu: PointLambert93): EcartAuPointPrevu {
            val d = centre.distanceA(prevu)
            return EcartAuPointPrevu(d, d > SEUIL_PROVISOIRE_M)
        }
    }
}

/** Issue d'un relevé. Aucune ne bloque l'opérateur sur l'écart : l'écart se lit à part ([EcartAuPointPrevu]). */
sealed interface ResultatReleve {
    data class Centre(val centre: CentreReleve) : ResultatReleve

    /** Une mesure tombe hors de la France métropolitaine : axes inversés, ou position lointaine. Rien n'est enregistré. */
    data class HorsFrance(val mesure: MesureGnss) : ResultatReleve

    /** Aucune mesure encore. */
    data object AucuneMesure : ResultatReleve
}

/**
 * Moyenne des mesures du téléphone. Les positions sont moyennées **en Lambert-93** (mètres), non en degrés.
 * La précision enregistrée est la moyenne des précisions déclarées par le téléphone : elle ne dit pas que la
 * moyenne est plus juste (Purfürst 2022 ne teste pas le moyennage, docs/referentiels/gnss-telephone.yaml, E3).
 */
object MoyenneGnss {
    fun moyenner(mesures: List<MesureGnss>): ResultatReleve {
        if (mesures.isEmpty()) return ResultatReleve.AucuneMesure
        mesures.firstOrNull { !EmpriseDegres.FRANCE_METROPOLITAINE.contient(it.position) }
            ?.let { return ResultatReleve.HorsFrance(it) }
        val points = mesures.map { Lambert93.depuisWgs84(it.position) }
        val moyenne = PointLambert93(points.map { it.xMetres }.average(), points.map { it.yMetres }.average())
        val dispersion = sqrt(points.map { val d = it.distanceA(moyenne); d * d }.average())
        return ResultatReleve.Centre(
            CentreReleve(
                position = moyenne,
                precisionM = mesures.map { it.precisionM }.average(),
                nbMesures = mesures.size,
                dateReleve = Instant.ofEpochMilli(mesures.maxOf { it.instantUtcMs }),
                dispersionM = dispersion,
            ),
        )
    }
}

/**
 * Collecte des mesures d'un relevé. [nbCible] = 10 mesures à 1 Hz : **valeur provisoire nommée** (Surculus ne
 * fixe qu'une « position moyennée quelques secondes », ADR 0003 l. 54) ; gel avant la première campagne, avec le
 * seuil de 30 m. L'opérateur peut valider avant la cible (au moins une mesure) : le décompte est alors affiché.
 */
class CollecteCentre(val nbCible: Int = NB_MESURES_PROVISOIRE) {
    private val mesures = mutableListOf<MesureGnss>()

    init {
        require(nbCible >= 1) { "Il faut au moins une mesure" }
    }

    val nbMesures: Int get() = mesures.size
    val complete: Boolean get() = mesures.size >= nbCible

    /** Ajoute une mesure ; sans effet une fois la cible atteinte. */
    fun ajouter(mesure: MesureGnss) {
        if (!complete) mesures += mesure
    }

    fun resultat(): ResultatReleve = MoyenneGnss.moyenner(mesures.toList())

    companion object {
        const val NB_MESURES_PROVISOIRE = 10
    }
}
