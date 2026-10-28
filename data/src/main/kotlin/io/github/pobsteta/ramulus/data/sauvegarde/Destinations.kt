package io.github.pobsteta.ramulus.data.sauvegarde

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/** Où s'écrivent les `.ramsync`. Un fichier n'est visible sous son nom qu'une fois complet. */
interface Destination {
    /** Noms des fichiers présents. */
    fun noms(): Set<String>

    /** Écrit un fichier complet sous `nom` ; ne remplace jamais un fichier existant. */
    fun ecrire(nom: String, octets: ByteArray)

    fun supprimer(nom: String)

    companion object {
        /** Sous-dossier de `Documents/` (ADR 0005, l. 34 du tableau « Ce qui est repris »). */
        const val DOSSIER = "Ramulus"

        /** MediaStore avec `IS_PENDING` à partir d'Android 10 ; dossier public `Documents/Ramulus/` avant. */
        fun pourCetAppareil(context: Context): Destination =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) DestinationMediaStore(context)
            else DestinationDossier(File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), DOSSIER))
    }
}

/**
 * Dossier ordinaire : Android 8 et 9 (`Documents/Ramulus/` public, `WRITE_EXTERNAL_STORAGE` limitée à l'API 28 ;
 * Surculus ADR 0005, l. 50). Le fichier est écrit sous `<nom>.part`, synchronisé, puis renommé une fois complet.
 */
class DestinationDossier(private val dossier: File) : Destination {
    override fun noms(): Set<String> =
        dossier.listFiles()?.filter { it.isFile && !it.name.endsWith(PART) }?.map { it.name }?.toSet() ?: emptySet()

    override fun ecrire(nom: String, octets: ByteArray) {
        if (!dossier.isDirectory && !dossier.mkdirs()) throw IOException("dossier impossible à créer : $dossier")
        val final = File(dossier, nom)
        if (final.exists()) throw IOException("fichier déjà présent, jamais écrasé : $nom")
        val part = File(dossier, nom + PART)
        FileOutputStream(part).use { sortie ->
            sortie.write(octets)
            sortie.fd.sync()
        }
        if (!part.renameTo(final)) {
            part.delete()
            throw IOException("renommage impossible : $nom")
        }
    }

    override fun supprimer(nom: String) {
        File(dossier, nom).delete()
    }

    private companion object {
        const val PART = ".part"
    }
}

/**
 * MediaStore, Android 10 et plus : `Documents/Ramulus/` du stockage partagé, avec `IS_PENDING` (fichier invisible
 * tant qu'il n'est pas complet), pour que la sauvegarde survive à la désinstallation (Surculus ADR 0005, l. 37 et 50).
 * Vérifiée sur appareil par un test instrumenté à venir (plan 005, Q12).
 */
@RequiresApi(Build.VERSION_CODES.Q)
class DestinationMediaStore(context: Context) : Destination {
    private val resolver = context.contentResolver
    private val collection: Uri = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
    private val chemin = Environment.DIRECTORY_DOCUMENTS + "/" + Destination.DOSSIER + "/"

    private fun lister(): Map<String, Long> {
        val resultat = mutableMapOf<String, Long>()
        resolver.query(
            collection,
            arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME),
            "${MediaStore.MediaColumns.RELATIVE_PATH} = ? AND ${MediaStore.MediaColumns.IS_PENDING} = 0",
            arrayOf(chemin),
            null,
        )?.use { c ->
            while (c.moveToNext()) resultat[c.getString(1)] = c.getLong(0)
        }
        return resultat
    }

    override fun noms(): Set<String> = lister().keys

    override fun ecrire(nom: String, octets: ByteArray) {
        if (nom in noms()) throw IOException("fichier déjà présent, jamais écrasé : $nom")
        val valeurs = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, nom)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/gzip")
            put(MediaStore.MediaColumns.RELATIVE_PATH, chemin)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(collection, valeurs) ?: throw IOException("MediaStore a refusé $nom")
        try {
            resolver.openOutputStream(uri, "w")?.use { it.write(octets) } ?: throw IOException("écriture impossible : $nom")
            resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
        } catch (e: IOException) {
            resolver.delete(uri, null, null)
            throw e
        }
    }

    override fun supprimer(nom: String) {
        lister()[nom]?.let { resolver.delete(ContentUris.withAppendedId(collection, it), null, null) }
    }
}
