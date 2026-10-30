package io.github.pobsteta.ramulus.tournee

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import io.github.pobsteta.ramulus.data.journal.JournalRepository
import io.github.pobsteta.ramulus.data.sauvegarde.Destination
import io.github.pobsteta.ramulus.data.sauvegarde.SauvegardeRepository
import java.io.File
import java.io.IOException

/** Le journal de la mission 005 et sa sauvegarde, derrière le port [Journal]. */
class JournalAndroid(
    private val journal: JournalRepository,
    private val sauvegarde: SauvegardeRepository,
    /** Appelé avec l'instant de chaque `.ramsync` écrit : Paramètres en montre la dernière date. */
    private val surSauvegarde: (java.time.Instant) -> Unit = {},
) : Journal {
    override fun saisir(codeTournee: String, objetUuid: String, type: String, valeur: String, operateur: String) {
        journal.saisir(codeTournee, objetUuid, type, valeur, operateur)
    }

    override fun sauvegarderSiDue(codeTournee: String, versionNemeton: String?, finDePlacette: Boolean): String? =
        sauvegarde.sauvegarderSiDue(codeTournee, versionNemeton, finDePlacette)
            ?.also { surSauvegarde(java.time.Instant.now()) }
}

/** Le nom de l'opérateur, dans les préférences privées de l'application (il ne quitte pas l'appareil). */
class StockOperateurPrefs(context: Context) : StockOperateur {
    private val prefs = context.getSharedPreferences("ramulus", Context.MODE_PRIVATE)
    override fun lire(): String? = prefs.getString(CLE, null)
    override fun ecrire(nom: String) {
        prefs.edit().putString(CLE, nom).commit()
    }

    private companion object {
        const val CLE = "operateur"
    }
}

/**
 * Dépose le `.zip` dans `Documents/Ramulus/` : MediaStore avec `IS_PENDING` à partir d'Android 10 (le fichier n'est
 * visible qu'une fois complet), dossier public avant (autorisation de stockage, API 28 et moins). Un nom déjà pris
 * n'est jamais écrasé : le rendu suivant s'appelle `<code>-2.zip`, puis `-3`…
 */
class ExportDocuments(private val context: Context) : ExportRendu {
    override fun exporter(zip: File, nomSouhaite: String): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) viaMediaStore(zip, nomSouhaite) else viaDossier(zip, nomSouhaite)

    private fun libre(nomSouhaite: String, pris: Set<String>): String {
        if (nomSouhaite !in pris) return nomSouhaite
        val base = nomSouhaite.removeSuffix(".zip")
        return generateSequence(2) { it + 1 }.map { "$base-$it.zip" }.first { it !in pris }
    }

    private fun viaDossier(zip: File, nomSouhaite: String): String {
        val dossier = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), Destination.DOSSIER)
        if (!dossier.isDirectory && !dossier.mkdirs()) throw IOException("dossier Documents/Ramulus impossible à créer")
        val nom = libre(nomSouhaite, dossier.list().orEmpty().toSet())
        zip.copyTo(File(dossier, nom))
        return nom
    }

    private fun viaMediaStore(zip: File, nomSouhaite: String): String {
        val resolver = context.contentResolver
        val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val chemin = Environment.DIRECTORY_DOCUMENTS + "/" + Destination.DOSSIER + "/"
        val pris = mutableSetOf<String>()
        resolver.query(
            collection,
            arrayOf(MediaStore.MediaColumns.DISPLAY_NAME),
            "${MediaStore.MediaColumns.RELATIVE_PATH} = ?",
            arrayOf(chemin),
            null,
        )?.use { c -> while (c.moveToNext()) pris += c.getString(0) }
        val nom = libre(nomSouhaite, pris)
        val valeurs = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, nom)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/zip")
            put(MediaStore.MediaColumns.RELATIVE_PATH, chemin)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(collection, valeurs) ?: throw IOException("MediaStore a refusé $nom")
        try {
            resolver.openOutputStream(uri, "w")?.use { sortie -> zip.inputStream().use { it.copyTo(sortie) } }
                ?: throw IOException("écriture impossible : $nom")
            resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
        } catch (e: IOException) {
            resolver.delete(uri, null, null)
            throw e
        }
        return nom
    }
}
