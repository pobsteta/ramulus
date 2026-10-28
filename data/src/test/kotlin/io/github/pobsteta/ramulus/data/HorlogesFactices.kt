package io.github.pobsteta.ramulus.data

import io.github.pobsteta.ramulus.data.journal.Horloges

/** Horloges réglables à la main : 2027-07-06T09:12:00Z, monotone 1 000 000 ms, premier démarrage. */
class HorlogesFactices(
    var utc: Long = 1_814_865_120_000L,
    var mono: Long = 1_000_000L,
    var dem: Long = 1L,
) : Horloges {
    override fun utcMs() = utc
    override fun monotoneMs() = mono
    override fun demarrage() = dem

    /** Le temps passe d'autant sur les deux horloges. */
    fun avancer(ms: Long) {
        utc += ms
        mono += ms
    }
}
