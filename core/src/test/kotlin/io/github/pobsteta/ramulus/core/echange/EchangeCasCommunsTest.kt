package io.github.pobsteta.ramulus.core.echange

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * Critères 2, 3, 4 et 5 de la spec § 5, par les cas communs de `fixtures/echange/` (rejoués aussi en R par Nemeton,
 * ADR 0003 § 7) et ceux du testeur de `fixtures/echange-complements/` : le module GeoPackage doit conclure comme
 * `attendu.json` (statut ET motifs exacts, ni plus ni moins), relire chaque fichier comme son instantané, et fusionner
 * comme le dit le cas.
 *
 * Désactivés jusqu'à la mission 004 (`ActiveeParLaMission004`). Pour les voir échouer : `RAMULUS_ECHANGE_EXIGER=1`.
 */
@ExtendWith(ActiveeParLaMission004::class)
class EchangeCasCommunsTest {

    private val cas = Cas.tous()

    private fun verifier(c: Cas, obtenu: Verdict) {
        assertEquals(c.statut, obtenu.statut, "${c.nom} : statut")
        assertEquals(c.motifs, obtenu.motifs, "${c.nom} : motifs (ni plus, ni moins)")
    }

    /** Lecteur « ramulus » : à l'ouverture d'une tournée de Nemeton (A1, A2, A6, R5, R6, R9, R11, R12). */
    @TestFactory
    fun `a l ouverture, la tournee de Nemeton est jugee comme attendu`(): List<DynamicTest> =
        cas.filter { it.lecteur == "ramulus" }.map { c ->
            dynamicTest(c.nom) { verifier(c, PasserelleEchange.ouvrir(c.fichiers.single())) }
        }

    /** Lecteur « nemeton » sur un seul paquet : le rendu est contrôlé avant d'être rendu (A3, A4, R1 à R3, R7 à R10, S1, S2, X*). */
    @TestFactory
    fun `un paquet rendu est juge comme attendu par rapport a la tournee de depart`(): List<DynamicTest> =
        cas.filter { it.lecteur == "nemeton" && it.fusion == null }.map { c ->
            dynamicTest(c.nom) { verifier(c, PasserelleEchange.controlerRendu(c.fichiers.single(), c.depart)) }
        }

    /** Chaque fichier de chaque cas se relit avec tous ses attributs : l'instantané est celui du cas (critère 2). */
    @TestFactory
    fun `chaque fichier se relit sans perte d attribut`(): List<DynamicTest> =
        cas.flatMap { c ->
            c.fichiers.map { f ->
                dynamicTest("${c.id} : ${f.name}") {
                    val ecart = premiereDifference(Cas.instantaneAttendu(f), PasserelleEchange.instantane(f))
                    assertNull(ecart, "${c.id} ${f.name}")
                }
            }
        }

    /** Plusieurs opérateurs (A5, S3) : chaque paquet seul, puis l'union, dans les deux ordres (critère 4). */
    @TestFactory
    fun `la fusion garde toutes les saisies et ecarte les conflits, quel que soit l ordre`(@TempDir tmp: File): List<DynamicTest> =
        cas.filter { it.fusion != null }.flatMap { c ->
            val fusion = c.fusion!!
            val operateur = fusion["operateur"].asString
            val version = fusion["version_ramulus"].asString
            val resultat = File(c.dossier, fusion["resultat"].asString).canonicalFile
            val ecartesAttendus = fusion.getAsJsonArray("ecartes")
                .map { it.asJsonObject["objet"].asString to it.asJsonObject["cle"].asString }.toSet()
            val seuls = c.fichiers.map { f ->
                dynamicTest("${c.id} : ${f.name} seul est ${fusion["chaque_fichier"].asString}") {
                    val v = PasserelleEchange.controlerRendu(f, c.depart!!)
                    assertEquals(fusion["chaque_fichier"].asString, v.statut, "${c.nom} : ${f.name}")
                    assertEquals(emptySet<String>(), v.motifs, "${c.nom} : ${f.name} seul, motifs")
                }
            }
            val unions = listOf(c.fichiers, c.fichiers.reversed()).mapIndexed { i, ordre ->
                dynamicTest("${c.id} : l'union (ordre ${if (i == 0) "direct" else "inverse"}) est celle du cas") {
                    val sortie = File(tmp, "${c.id}-$i.zip")
                    val v = PasserelleEchange.fusionner(ordre, c.depart!!, operateur, version, sortie)
                    verifier(c, v)
                    assertEquals(ecartesAttendus, v.ecartes, "${c.nom} : éléments écartés (objet, clé)")
                    val ecart = premiereDifference(Cas.instantaneAttendu(resultat), PasserelleEchange.instantane(sortie))
                    assertNull(ecart, "${c.nom} : paquet de l'union")
                }
            }
            seuls + unions
        }

    /**
     * Un import rejoué ne double rien (ADR 0003 § 6, « rejouable sans doublon ») : fusionner un paquet avec lui-même,
     * ou avec l'union qu'il a déjà donnée, redonne cette union.
     */
    @TestFactory
    fun `la fusion est rejouable sans doublon`(@TempDir tmp: File): List<DynamicTest> =
        cas.filter { it.id == "A3" || it.id == "A5" }.map { c ->
            dynamicTest(c.nom) {
                val rendu = if (c.fusion == null) c.fichiers.single() else File(c.dossier, c.fusion!!["resultat"].asString).canonicalFile
                val operateur = rendu.let { Cas.instantaneAttendu(it) }
                    .getAsJsonObject("tables").getAsJsonObject("tournee").getAsJsonArray("lignes")[0].asJsonObject["operateur"].asString
                val sortie = File(tmp, "rejeu-${c.id}.zip")
                val v = PasserelleEchange.fusionner(listOf(rendu, rendu), c.depart!!, operateur, "exemple", sortie)
                assertEquals("accepte", v.statut, c.nom)
                assertEquals(emptySet<String>(), v.motifs, c.nom)
                assertNull(premiereDifference(Cas.instantaneAttendu(rendu), PasserelleEchange.instantane(sortie)), c.nom)
            }
        }

    /** Critère 5 : la tournée de contre-notation ne porte aucune première note, et le format le refuse sinon (R6). */
    @TestFactory
    fun `la contre-notation ne montre jamais la premiere notation`(): List<DynamicTest> = listOf(
        dynamicTest("A2 : l'instantané de la tournée de contre-notation n'a pas de table notations") {
            val a2 = cas.single { it.id == "A2" }
            val tables = PasserelleEchange.instantane(a2.fichiers.single()).getAsJsonObject("tables").keySet()
            assertFalse("notations" in tables, "tables : $tables")
            assertEquals(setOf("centres", "contre_notations", "placettes", "tournee"), tables)
        },
        dynamicTest("R6 : une première note dans une contre-notation est refusée (notations_dans_contre_notation)") {
            val r6 = cas.single { it.id == "R6" }
            verifier(r6, PasserelleEchange.ouvrir(r6.fichiers.single()))
        },
    )
}
