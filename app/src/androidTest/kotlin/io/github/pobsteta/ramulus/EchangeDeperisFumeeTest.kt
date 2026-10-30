package io.github.pobsteta.ramulus

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.JsonParser
import io.github.pobsteta.ramulus.core.echange.EchangeDeperis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Essai de fumée de la mission 013 : sur l'APK « fumee » (R8, natifs, désucrage), ouvrir la tournée de départ A1,
 * y écrire un centre, une notation, la clôture, puis relire. Si libsqlitejdbc.so manque, ou si R8 a renommé ce que
 * le code natif cherche, ce test plante : c'est son rôle.
 *
 * Coordonnées de l'exemple : EPSG:2154, x = est, y = nord, en mètres (Lambert-93, en France métropolitaine).
 */
@RunWith(AndroidJUnit4::class)
class EchangeDeperisFumeeTest {
    private fun tournee(): File {
        val instr = InstrumentationRegistry.getInstrumentation()
        val cible = File(instr.targetContext.cacheDir, "fumee-deperis.gpkg")
        instr.context.assets.open("deperis.gpkg").use { e -> cible.outputStream().use { e.copyTo(it) } }
        return cible
    }

    @Test
    fun ouvrir_ecrire_et_relire_la_tournee_A1() {
        val gpkg = tournee()

        val avant = JsonParser.parseString(EchangeDeperis.ouvrir(gpkg)).asJsonObject
        assertEquals("accepte", avant["verdict"].asString)

        EchangeDeperis.enregistrerCentre(
            gpkg,
            """{"geom":{"x":856563.43,"y":6670349.24},"uuid":"01a68e23-f4c0-7124-bdae-f15d2458b1eb","plot_id":"P001",""" +
                """"precision_m":5.8,"nb_mesures":60,"source":"telephone","date_releve":"2027-07-06T08:02:00.000Z",""" +
                """"operateur":"operateur-A"}""",
        )
        // MB 2 et MR 3 : la classe n'est pas saisie, l'abaque la calcule (D, comme dans la fixture A3).
        val ligne = JsonParser.parseString(
            EchangeDeperis.enregistrerNotation(
                gpkg,
                """{"uuid":"01a68e2b-47c0-7d87-9db2-164808fe8603","plot_id":"P001","rang":1,"repere":"1",""" +
                    """"essence":"Chêne sessile","type":"FEUILLU","mb":2,"second":3,"critere":"MR","saison":"ETE",""" +
                    """"date_notation":"2027-07-06T08:10:00.000Z","operateur":"operateur-A","photos":[],""" +
                    """"ia_premiere_mb":null,"ia_premiere_second":null,"ia_mb":null,"ia_second":null,""" +
                    """"ia_confiance":null,"ia_modele":null}""",
            ),
        ).asJsonObject
        assertEquals("D", ligne["classe"].asString)
        EchangeDeperis.cloturerPlacette(gpkg, "P001")

        val inst = JsonParser.parseString(EchangeDeperis.instantane(gpkg)).asJsonObject.getAsJsonObject("tables")
        val centre = inst.getAsJsonObject("centres").getAsJsonArray("lignes")[0].asJsonObject
        assertEquals(856563.43, centre.getAsJsonObject("geom")["x"].asDouble, 1e-6)
        assertEquals(6670349.24, centre.getAsJsonObject("geom")["y"].asDouble, 1e-6)
        assertEquals(1, inst.getAsJsonObject("notations").getAsJsonArray("lignes").size())
        val p001 = inst.getAsJsonObject("placettes").getAsJsonArray("lignes").map { it.asJsonObject }.first { it["plot_id"].asString == "P001" }
        assertEquals("notee", p001["etat"].asString)
        assertTrue(gpkg.length() > 0)
    }
}
