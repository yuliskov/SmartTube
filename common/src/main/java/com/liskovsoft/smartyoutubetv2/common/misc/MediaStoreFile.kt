package com.liskovsoft.smartyoutubetv2.common.misc

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import java.io.File
import java.io.InputStream
import java.io.OutputStream

/**
 * Wrapper around MediaStore API to provide a File-like interface
 * for Android 10+ scoped storage.
 *
 * All files and folders are created under:
 * `Documents/<packageName>/...`
 *
 * Directories are simulated using a hidden `.dir` file marker.
 *
 * Example usage:
 * ```
 * val backupsDir = MFile(context, "backups")
 * backupsDir.mkdirs()
 *
 * val backupFile = backupsDir.child("backup.zip")
 * backupFile.copyFrom(sourceFile)   // copy from java.io.File
 *
 * println(backupFile.length())
 * println(backupFile.lastModified())
 *
 * val renamedFile = backupFile.child("backup_new.zip")
 * backupFile.renameTo(renamedFile)
 * renamedFile.setLastModified(System.currentTimeMillis())
 *
 * val files = backupsDir.listFiles()
 * files.forEach { println(it.resolve()) }
 * ```
 *
 * @param context Context used to access ContentResolver
 * @param path Relative path to the root directory (app package folder)
 */
@RequiresApi(29)
internal class MediaStoreFile @JvmOverloads constructor(
    private val context: Context,
    private val path: String, // relative to rootDir
    private val rootDir: String = context.packageName,
    private val publicDirType: String = Environment.DIRECTORY_DOCUMENTS
) {
    private var cachedUri: Uri? = null

    private val resolver get() = context.contentResolver

    private fun name(): String =
        path.substringAfterLast("/")

    private fun parent(): String =
        path.substringBeforeLast("/", "")

    private fun relativePath(): String {
        val parent = parent()
        return if (parent.isEmpty())
            "$publicDirType/$rootDir"
        else
            "$publicDirType/$rootDir/$parent"
    }

    private fun fullRelativeDir(): String {
        return "$publicDirType/$rootDir/$path"
    }

    private fun findUri(): Uri? {
        if (cachedUri != null) return cachedUri

        val projection = arrayOf(MediaStore.Files.FileColumns._ID)

        val selection =
            "${MediaStore.Files.FileColumns.DISPLAY_NAME}=? AND ${MediaStore.Files.FileColumns.RELATIVE_PATH}=?"

        val selectionArgs = arrayOf(
            name(),
            relativePath() + "/"
        )

        resolver.query(
            MediaStore.Files.getContentUri("external"),
            projection,
            selection,
            selectionArgs,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val id = cursor.getLong(0)
                cachedUri = Uri.withAppendedPath(
                    MediaStore.Files.getContentUri("external"),
                    id.toString()
                )
                return cachedUri
            }
        }
        return null
    }

    fun exists(): Boolean = findUri() != null

    fun isFile(): Boolean = exists()

    /**
     * The .dir marker is just a convention we use to simulate directories in MediaStore, because MediaStore doesn’t really have “directory” entries — it only stores files, each with a RELATIVE_PATH.
     *
     * So if you want a folder to “exist” even if it’s empty, you create a hidden placeholder file called .dir inside that folder.
     */
    fun isDirectory(): Boolean {
        val dirMarker = MediaStoreFile(context, "$path/.dir", rootDir)
        if (dirMarker.exists()) return true

        val projection = arrayOf(MediaStore.Files.FileColumns._ID)
        val selection =
            "${MediaStore.Files.FileColumns.RELATIVE_PATH} LIKE ?"
        val selectionArgs = arrayOf(fullRelativeDir() + "/%")

        resolver.query(
            MediaStore.Files.getContentUri("external"),
            projection,
            selection,
            selectionArgs,
            null
        )?.use { cursor ->
            return cursor.count > 0
        }

        return false
    }

    fun createNewFile(): Boolean {
        if (exists()) return false

        val values = ContentValues().apply {
            put(MediaStore.Files.FileColumns.DISPLAY_NAME, name())
            put(MediaStore.Files.FileColumns.MIME_TYPE, "application/octet-stream")
            put(MediaStore.Files.FileColumns.RELATIVE_PATH, relativePath())
        }

        val uri = resolver.insert(
            MediaStore.Files.getContentUri("external"),
            values
        )

        cachedUri = uri
        return uri != null
    }

    fun mkdirs() {
        if (path.isEmpty()) return

        val parts = path.split("/")
        var current = ""

        for (part in parts) {
            if (part.isEmpty()) continue
            current = if (current.isEmpty()) part else "$current/$part"
            val dummy = MediaStoreFile(context, "$current/.dir", rootDir)
            if (!dummy.exists()) {
                dummy.createNewFile()
            }
        }
    }

    fun delete(): Boolean {
        val uri = findUri() ?: return false
        val result = resolver.delete(uri, null, null) > 0
        if (result) cachedUri = null
        return result
    }

    fun listFiles(): List<MediaStoreFile> {
        val list = mutableListOf<MediaStoreFile>()

        val projection = arrayOf(
            MediaStore.Files.FileColumns.DISPLAY_NAME
        )

        val selection =
            "${MediaStore.Files.FileColumns.RELATIVE_PATH}=?"

        val selectionArgs = arrayOf(
            fullRelativeDir() + "/"
        )

        resolver.query(
            MediaStore.Files.getContentUri("external"),
            projection,
            selection,
            selectionArgs,
            null
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val fileName = cursor.getString(0)
                if (fileName != ".dir") {
                    list.add(MediaStoreFile(context, "$path/$fileName", rootDir))
                }
            }
        }

        return list
    }

    fun openInputStream(): InputStream? {
        return findUri()?.let { resolver.openInputStream(it) }
    }

    fun openOutputStream(): OutputStream? {
        val uri = findUri() ?: run {
            createNewFile()
            findUri()
        } ?: return null

        return resolver.openOutputStream(uri)
    }

    fun copyFrom(src: File) {
        val uri = findUri() ?: run {
            createNewFile()
            findUri()
        } ?: return

        resolver.openOutputStream(uri)?.use { out ->
            src.inputStream().use { input ->
                input.copyTo(out)
            }
        }
    }

    fun copyTo(dest: MediaStoreFile) {
        val input = openInputStream() ?: return
        val output = dest.openOutputStream() ?: return

        input.use { inp ->
            output.use { out ->
                inp.copyTo(out)
            }
        }
    }

    fun copyTo(dest: File) {
        val input = openInputStream() ?: return

        dest.outputStream().use { out ->
            input.use { inp ->
                inp.copyTo(out)
            }
        }
    }

    /**
     * Create file in the current directory
     */
    fun child(name: String): MediaStoreFile {
        return if (path.isEmpty())
            MediaStoreFile(context, name, rootDir)
        else
            MediaStoreFile(context, "$path/$name", rootDir)
    }

    fun resolve(): String {
        return Environment.getExternalStoragePublicDirectory(
            publicDirType
        ).absolutePath + "/" + rootDir + "/" + path
    }

    /**
     * Returns the full path in external storage if available.
     * Note: The file may not exist on disk yet (MediaStore may manage it).
     */
    fun getAbsolutePath(): String {
        val fileName = getStoredName() ?: name()
        return Environment.getExternalStoragePublicDirectory(
            publicDirType
        ).absolutePath + "/" + rootDir + "/" + if (parent().isNotEmpty()) parent() + "/" else "" + fileName
    }

    fun renameTo(dest: MediaStoreFile): Boolean {
        val uri = findUri() ?: return false

        val values = ContentValues().apply {
            put(MediaStore.Files.FileColumns.DISPLAY_NAME, dest.name())
            put(MediaStore.Files.FileColumns.RELATIVE_PATH, dest.relativePath())
        }

        val updated = resolver.update(uri, values, null, null) > 0
        if (updated) {
            cachedUri = null // invalidate cache
        }
        return updated
    }

    fun length(): Long {
        val uri = findUri() ?: return 0
        val projection = arrayOf(MediaStore.Files.FileColumns.SIZE)

        resolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                return cursor.getLong(0)
            }
        }
        return 0
    }

    fun size(): Long = length() // alias

    fun lastModified(): Long {
        val uri = findUri() ?: return 0
        val projection = arrayOf(MediaStore.Files.FileColumns.DATE_MODIFIED)

        resolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                return cursor.getLong(0) * 1000 // convert to milliseconds
            }
        }
        return 0
    }

    fun setLastModified(timeMillis: Long): Boolean {
        val uri = findUri() ?: return false

        val values = ContentValues().apply {
            put(MediaStore.Files.FileColumns.DATE_MODIFIED, timeMillis / 1000) // seconds
        }

        val updated = resolver.update(uri, values, null, null) > 0
        return updated
    }

    /**
     * The real name of the file (if the same created by another app)
     */
    fun getStoredName(): String? {
        val uri = findUri() ?: return null
        val projection = arrayOf(MediaStore.Files.FileColumns.DISPLAY_NAME)
        resolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) return cursor.getString(0)
        }
        return null
    }

    /**
     * Returns true if this file can be written to without MediaStore renaming it.
     *
     * MediaStore may silently rename files if the app does not have permission
     * to overwrite an existing file (e.g. after app reinstall). This method
     * attempts a zero-byte write and then verifies that the stored file name
     * matches the expected file name. If MediaStore created a renamed file,
     * it will be deleted.
     */
    fun isWritable(): Boolean {
        val uri = findUri() ?: run {
            createNewFile()
            findUri()
        } ?: return false

        return try {
            // Attempt zero-byte write
            resolver.openOutputStream(uri)?.use { out ->
                out.write(byteArrayOf())
            }

            val storedName = getStoredName()

            if (storedName != null && storedName != name()) {
                // MediaStore created renamed file like "file.zip (1)"
                delete()
                return false
            }

            true
        } catch (e: SecurityException) {
            false
        } catch (e: Exception) {
            false
        }
    }
}