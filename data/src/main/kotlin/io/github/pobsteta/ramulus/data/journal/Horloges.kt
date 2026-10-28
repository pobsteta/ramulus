package io.github.pobsteta.ramulus.data.journal

import android.content.Context
import android.os.SystemClock
import android.provider.Settings

/** Les trois horloges d'une saisie (Surculus ADR 0005, l. 46 et 49). */
interface Horloges {
    /** Heure du téléphone, ms Unix UTC (peut sauter). */
    fun utcMs(): Long

    /** `SystemClock.elapsedRealtime` : monotone dans un même démarrage. */
    fun monotoneMs(): Long

    /** Numéro de démarrage du téléphone. */
    fun demarrage(): Long
}

/** Horloges d'Android ; le numéro de démarrage est `Settings.Global.BOOT_COUNT` (API 24 et plus). */
class HorlogesAndroid(private val context: Context) : Horloges {
    override fun utcMs(): Long = System.currentTimeMillis()
    override fun monotoneMs(): Long = SystemClock.elapsedRealtime()
    override fun demarrage(): Long =
        Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, 0).toLong()
}
