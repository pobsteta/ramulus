package io.github.pobsteta.ramulus.core.deperis

/** Saison d'observation : certaines essences ont une fiche d'été et une fiche d'hiver. */
enum class Saison { ETE, HIVER }

/** Essence couverte par une fiche DEPERIS du DSF, avec les liens vers ses fiches de référence. */
data class EssenceDeperis(
    val nom: String,
    val type: TypeEssence,
    val ficheEte: String?,
    val ficheHiver: String?,
    val ficheToutesSaisons: String?,
) {
    /** Fiche de référence à montrer à l'opérateur pour la saison donnée ; null si la fiche n'existe pas. */
    fun fiche(saison: Saison): String? = ficheToutesSaisons ?: when (saison) {
        Saison.ETE -> ficheEte
        Saison.HIVER -> ficheHiver
    }
}

/**
 * Référentiel des essences DEPERIS, lu dans docs/referentiels/deperis/essences.csv (seule source, embarquée comme
 * ressource du module). Ramulus n'invente aucune essence : une essence absente n'a pas de fiche de référence.
 */
object Essences {
    const val RESSOURCE = "/deperis/essences.csv"

    val toutes: List<EssenceDeperis> by lazy {
        val flux = Essences::class.java.getResourceAsStream(RESSOURCE)
            ?: error("Référentiel absent : $RESSOURCE")
        flux.bufferedReader(Charsets.UTF_8).use { lire(it.readText()) }
    }

    fun parNom(nom: String): EssenceDeperis? = toutes.firstOrNull { it.nom.equals(nom.trim(), ignoreCase = true) }

    /** Lit le CSV séparé par des points-virgules, avec une ligne d'en-tête. */
    fun lire(csv: String): List<EssenceDeperis> {
        val lignes = csv.lineSequence().map { it.trimEnd('\r') }.filter { it.isNotBlank() }.toList()
        require(lignes.isNotEmpty()) { "Référentiel des essences vide" }
        val entete = lignes.first().split(';')
        require(entete == ENTETE) { "En-tête inattendu : $entete" }
        return lignes.drop(1).mapIndexed { i, ligne ->
            val c = ligne.split(';')
            require(c.size == ENTETE.size) { "Ligne ${i + 2} : ${c.size} colonnes au lieu de ${ENTETE.size}" }
            val e = EssenceDeperis(
                nom = c[0].trim(),
                type = TypeEssence.valueOf(c[1].trim()),
                ficheEte = c[2].ifBlank { null },
                ficheHiver = c[3].ifBlank { null },
                ficheToutesSaisons = c[4].ifBlank { null },
            )
            require(e.ficheToutesSaisons != null || e.ficheEte != null || e.ficheHiver != null) {
                "Ligne ${i + 2} : « ${e.nom} » n'a aucune fiche"
            }
            e
        }
    }

    private val ENTETE = listOf("essence", "type", "fiche_ete", "fiche_hiver", "fiche_toutes_saisons")
}
