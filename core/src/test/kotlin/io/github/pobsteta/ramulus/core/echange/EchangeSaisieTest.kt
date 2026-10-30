package io.github.pobsteta.ramulus.core.echange

import com.google.gson.JsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * Les cas de SAISIE (mission 014 ; format amendé par 010) : ceux dont `attendu.json` porte `enregistrement` (T2).
 * Ils ne s'ouvrent pas comme un fichier : chaque saisie est rejouée par `enregistrerNotation` sur une copie de la
 * tournée, doit être refusée avec le motif du cas dans le message, et laisser la tournée intacte.
 *
 * Désactivé jusqu'à la mission 004 (`ActiveeParLaMission004`). Pour le voir échouer : `RAMULUS_ECHANGE_EXIGER=1`.
 */
@ExtendWith(ActiveeParLaMission004::class)
class EchangeSaisieTest {

    private val saisies = Cas.tous().filter { it.estSaisie }

    @TestFactory
    fun `une saisie des cas de saisie est refusee avec le motif du cas et laisse la tournee intacte`(@TempDir tmp: File): List<DynamicTest> {
        assertEquals(listOf("T2"), saisies.map { it.id }, "cas de saisie lus")
        return saisies.flatMap { c ->
            val enr = c.enregistrement!!
            assertEquals("refuse", c.statut, "${c.nom} : une saisie ne peut être que refusée ici")
            enr.getAsJsonArray("saisies").map { it.asJsonObject }.mapIndexed { i, s ->
                dynamicTest("${c.id} : ${s["nom"].asString} -> ${c.motifs.joinToString()}") {
                    val travail = File(tmp, "${c.id}-$i.gpkg").also { c.fichiers.single().copyTo(it, overwrite = true) }
                    val avant = PasserelleEchange.instantane(travail)
                    assertNull(premiereDifference(Cas.instantaneAttendu(c.fichiers.single()), avant), "${c.id} : la tournée de départ")

                    val ligne: JsonObject = s.getAsJsonObject("ligne").deepCopy().also { it.remove("fid") }
                    val e = runCatching { PasserelleEchange.enregistrerNotation(travail, ligne) }.exceptionOrNull()

                    assertTrue(e != null, "${c.nom} : aucune exception, la saisie a été acceptée")
                    val message = e?.message ?: ""
                    for (m in c.motifs) assertTrue(m in message, "le message doit citer $m : $message")
                    assertFalse("classe_differente_abaque" in message, "motif de la lecture d'un paquet (R2), pas de la saisie : $message")
                    assertNull(premiereDifference(avant, PasserelleEchange.instantane(travail)), "${c.nom} : la tournée a changé malgré le refus")
                }
            }
        }
    }
}
