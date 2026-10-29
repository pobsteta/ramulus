/*
 * Ramulus — notation de l'état des houppiers (protocole DEPERIS).
 * Copyright (C) 2026 Pascal Obstétar
 *
 * Ce fichier est un logiciel libre : vous pouvez le redistribuer et/ou le modifier selon
 * les termes de la licence publique générale GNU (GPL), version 3 ou ultérieure (voir LICENSE).
 *
 * Origine : Surculus (https://github.com/pobsteta/surculus), fichier
 *           app/src/main/kotlin/io/github/pobsteta/surculus/gnss/SourcePosition.kt, lu au commit 3c01f4b
 *           (lui-même repris de Groma). Reprise réduite à la seule source « telephone » (ADR 0004) : pas de
 *           récepteur RTK, pas de flux partagé.
 * Modifications : paquet ; une seule source ; fournisseur GPS seul ; mesures rendues par rappel.
 */
package io.github.pobsteta.ramulus.gnss

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Looper
import androidx.core.content.ContextCompat
import io.github.pobsteta.ramulus.core.gnss.MesureGnss
import io.github.pobsteta.ramulus.core.gnss.PositionWgs84

/**
 * Position du GNSS du téléphone (ADR 0004). Aucune position ne quitte l'appareil : pas de fournisseur réseau,
 * pas d'envoi, pas d'écoute en arrière-plan.
 */
object SourcePosition {
    fun localisationAutorisee(ctx: Context): Boolean =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /**
     * Écoute le GPS du téléphone toutes les secondes ; chaque mesure utilisable est remise à [surMesure].
     * Fermer le résultat arrête l'écoute. Sans autorisation ou sans GPS, ne fait rien.
     *
     * Une mesure sans précision déclarée est écartée (elle ne dirait rien de sa justesse), ainsi qu'une
     * position simulée par une autre application.
     */
    @SuppressLint("MissingPermission")
    fun ecouter(ctx: Context, surMesure: (MesureGnss) -> Unit): AutoCloseable {
        val lm = ctx.getSystemService(LocationManager::class.java)
        if (lm == null || !localisationAutorisee(ctx)) return AutoCloseable {}
        val auditeur = LocationListener { l -> mesureDepuis(l)?.let(surMesure) }
        runCatching {
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, auditeur, Looper.getMainLooper())
        }
        return AutoCloseable { runCatching { lm.removeUpdates(auditeur) } }
    }

    @Suppress("DEPRECATION")
    private fun estSimulee(l: Location): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) l.isMock else l.isFromMockProvider

    private fun mesureDepuis(l: Location): MesureGnss? {
        if (!l.hasAccuracy() || estSimulee(l)) return null
        return MesureGnss(
            position = PositionWgs84(latitudeDeg = l.latitude, longitudeDeg = l.longitude),
            precisionM = l.accuracy.toDouble(),
            instantUtcMs = l.time,
        )
    }
}
