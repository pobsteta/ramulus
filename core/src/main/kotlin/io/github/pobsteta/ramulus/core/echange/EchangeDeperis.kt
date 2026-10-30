package io.github.pobsteta.ramulus.core.echange

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import java.io.File

/**
 * L'échange `deperis-v1` avec Nemeton (mission 004) : lire une tournée, y écrire centres et notes, la clôturer,
 * fusionner des paquets et rendre le `.zip`. Format : `docs/formats/deperis-v1.md` ; décisions : ADR 0003 et 0005.
 *
 * L'interface est celle que les tests d'acceptation de 003 appellent (`PasserelleEchange.kt`) : un objet à fonctions
 * statiques pour la JVM, des résultats en texte JSON, et, pour une écriture refusée, une [EchangeRefuse] dont le
 * message commence par le code du motif ; le fichier reste alors tel qu'il était.
 *
 * Le fichier est lu et écrit avec `sqlite-jdbc`, jamais avec le SQLite d'Android (Surculus ADR 0011). Aucune
 * photo ne quitte l'appareil : les photos du `.zip` viennent du dossier local donné à [rendre] (ADR 0006).
 *
 * Coordonnées : EPSG:2154, X = est, Y = nord, en mètres, partout (jamais de latitude ni de longitude à l'écriture).
 */
object EchangeDeperis {
    /**
     * Lecteur « ramulus » : ouvre une tournée de Nemeton et la contrôle.
     * Rend `{"verdict": "accepte"|"signale"|"refuse", "motifs": [...], "messages": [...]}`.
     */
    @JvmStatic
    fun ouvrir(gpkg: File): String = jugerFichier(gpkg, null)

    /** L'instantané JSON du format (« Instantané ») d'un `.gpkg` ou d'un `.zip` rendu. Lève si le fichier est illisible. */
    @JvmStatic
    fun instantane(fichier: File): String = Paquets.lire(fichier).use { JsonOutils.gson.toJson(it.inst) }

    /**
     * Lecteur « nemeton » : contrôle un paquet rendu par rapport à la tournée de départ (`null` : sans comparaison).
     * Même forme de résultat que [ouvrir].
     */
    @JvmStatic
    fun controlerRendu(zip: File, depart: File?): String = jugerFichier(zip, depart)

    /**
     * Union sur l'`uuid` de paquets rendus, contrôlée comme un paquet, et écrite dans `sortie` sauf refus.
     * Rend `{"verdict", "motifs", "messages", "ecartes": [{"objet", "cle", "message", "plot_id" (centres)}], "placettes_signalees": ["P001"]}`.
     */
    @JvmStatic
    fun fusionner(zips: List<File>, depart: File, operateur: String, versionRamulus: String, sortie: File): String {
        val paquets = ArrayList<PaquetDeperis>()
        var departLu: PaquetDeperis? = null
        val temp = File.createTempFile("union", ".gpkg")
        try {
            try {
                zips.forEach { paquets += Paquets.lire(it) }
                departLu = Paquets.lire(depart)
            } catch (e: PasUnGeopackage) {
                return verdict(Resultat(Resultat.REFUSE, listOf("pas_un_geopackage"), listOf("pas_un_geopackage : ${e.message}")), null)
            }
            val union = Fusion.unir(paquets)
            Fusion.ecrire(paquets[0], union, JsonOutils.nfc(operateur), JsonOutils.nfc(versionRamulus), temp)
            val photos = union.photos
            val inst = lireInstantane(temp, photos)
            val r = Controle.controler(inst, photos, departLu.inst)
            val motifs = if (union.ecartes.isEmpty()) r.motifs else (r.motifs + Controle.MOTIF_CONFLIT).distinct().sorted()
            val messages = r.messages + union.ecartes.map { "${Controle.MOTIF_CONFLIT} : ${it.message}" } +
                union.placettesSignalees.map { "${Controle.MOTIF_CONFLIT} : placette $it signalée, gardée avec ses notes et sans centre ; dire quel centre fait foi, puis rendre de nouveau" }
            val resultat = Resultat(Controle.statut(motifs), motifs, messages)
            if (!resultat.refuse) Paquets.ecrireZip(temp, photos.values, sortie)
            return verdict(resultat, union.ecartes, union.placettesSignalees)
        } finally {
            temp.delete()
            paquets.forEach { it.close() }
            departLu?.close()
        }
    }

    /** Ajoute une ligne à `centres` (JSON = les colonnes sauf `fid` ; `geom` = `{"x", "y"}` en EPSG:2154). */
    @JvmStatic
    fun enregistrerCentre(gpkg: File, centre: String) = Ecriture.enregistrerCentre(gpkg, centre)

    /**
     * Ajoute une ligne à `notations` (première notation) ou à `contre_notations`, selon `tournee.nature`. JSON = les
     * colonnes sauf `fid` et `classe` : la classe se calcule par l'abaque, toute classe fournie est refusée
     * (`classe_saisie`). Rend la ligne telle qu'enregistrée, classe comprise.
     */
    @JvmStatic
    fun enregistrerNotation(gpkg: File, notation: String): String = Ecriture.enregistrerNotation(gpkg, notation)

    /**
     * Corrige une note dans la copie de travail (mission 015) : la note `ancienUuid` est **retirée** et la nouvelle
     * ajoutée, dans une seule transaction, à la table de la nature de la tournée. La nouvelle porte un nouvel `uuid`, le
     * même `plot_id` et le même `rang`. Ainsi une correction ne laisse qu'une ligne par arbre et ne fabrique pas de
     * `doublon_arbre`. Mêmes refus que [enregistrerNotation], plus `uuid_duplique` (même `uuid`) et
     * `rattachement_invalide` (note absente, autre placette, autre rang). Rend la ligne écrite, classe comprise.
     */
    @JvmStatic
    fun remplacerNotation(gpkg: File, ancienUuid: String, notation: String): String =
        Ecriture.remplacerNotation(gpkg, ancienUuid, notation)

    /**
     * Relever de nouveau un centre (mission 015) : les centres déjà écrits pour ce `plot_id` sont retirés de la copie de
     * travail et le nouveau est ajouté, dans une seule transaction ; il reste au plus un centre par placette. Mêmes refus
     * que [enregistrerCentre]. Rend les `uuid` retirés, en texte JSON d'un tableau (`[]` pour un premier relevé).
     */
    @JvmStatic
    fun remplacerCentre(gpkg: File, centre: String): String = Ecriture.remplacerCentre(gpkg, centre)

    /** `placettes.etat` = « notee » ; rien d'autre n'est modifié dans `placettes`. */
    @JvmStatic
    fun cloturerPlacette(gpkg: File, plotId: String) = Ecriture.cloturerPlacette(gpkg, plotId)

    /**
     * Contrôle la tournée comme [controlerRendu], renseigne `tournee.operateur` et `tournee.version_ramulus` (dans
     * une copie : `gpkg` n'est pas modifié), et écrit `sortie` (`deperis.gpkg` et `photos/`) SAUF si le verdict est
     * « refuse » ; un simple signalement n'empêche pas le rendu. `racinePhotos` contient `photos/<uuid>_<n>.jpg` ;
     * `depart` est la copie intacte de la tournée de départ.
     */
    @JvmStatic
    fun rendre(gpkg: File, depart: File, racinePhotos: File, operateur: String, versionRamulus: String, sortie: File): String {
        val temp = File.createTempFile("rendu", ".gpkg")
        var departLu: PaquetDeperis? = null
        try {
            try {
                departLu = Paquets.lire(depart)
                gpkg.copyTo(temp, overwrite = true)
                Gpkg.ouvrir(temp).use { Ecriture.poserOperateur(it, operateur, versionRamulus) }
                val photos = Paquets.photosDeLAppareil(racinePhotos)
                val inst = lireInstantane(temp, photos)
                val r = Controle.controler(inst, photos, departLu.inst)
                if (!r.refuse) Paquets.ecrireZip(temp, photos.values, sortie)
                return verdict(r, null)
            } catch (e: PasUnGeopackage) {
                return verdict(Resultat(Resultat.REFUSE, listOf("pas_un_geopackage"), listOf("pas_un_geopackage : ${e.message}")), null)
            }
        } finally {
            temp.delete()
            departLu?.close()
        }
    }

    // ------------------------------------------------------------------------------------------------------------

    private fun jugerFichier(fichier: File, depart: File?): String {
        var paquet: PaquetDeperis? = null
        var departLu: PaquetDeperis? = null
        try {
            paquet = Paquets.lire(fichier)
            departLu = depart?.let { Paquets.lire(it) }
            return verdict(Controle.controler(paquet, departLu), null)
        } catch (e: PasUnGeopackage) {
            return verdict(Resultat(Resultat.REFUSE, listOf("pas_un_geopackage"), listOf("pas_un_geopackage : ${e.message}")), null)
        } finally {
            paquet?.close()
            departLu?.close()
        }
    }

    private fun lireInstantane(gpkg: File, photos: Map<String, PhotoPaquet>): JsonObject =
        Gpkg.ouvrir(gpkg, lectureSeule = true).use { Instantane.lire(it, photos.values.sortedBy { p -> p.nom }.map { p -> p.nom to p.sha256 }) }

    private fun verdict(r: Resultat, ecartes: List<Ecarte>?, placettesSignalees: List<String> = emptyList()): String {
        val o = JsonObject()
        o.addProperty("verdict", r.statut)
        o.add("motifs", JsonArray().also { a -> r.motifs.forEach { a.add(it) } })
        o.add("messages", JsonArray().also { a -> r.messages.forEach { a.add(it) } })
        if (ecartes != null) {
            o.add(
                "ecartes",
                JsonArray().also { a ->
                    ecartes.forEach { e ->
                        a.add(JsonObject().also { x -> x.addProperty("objet", e.objet); x.addProperty("cle", e.cle); x.addProperty("message", e.message); e.plotId?.let { p -> x.addProperty("plot_id", p) } })
                    }
                },
            )
            o.add("placettes_signalees", JsonArray().also { a -> placettesSignalees.forEach { a.add(it) } })
        }
        return JsonOutils.gson.toJson(o)
    }
}
