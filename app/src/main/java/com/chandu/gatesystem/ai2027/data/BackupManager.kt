package com.chandu.gatesystem.ai2027.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import com.chandu.gatesystem.ai2027.data.db.AppDatabase
import java.io.File
import java.io.IOException

/**
 * Backup = a copy of the Room database file (all progress, mistakes, attempts, questions) written through the system file picker.
 * Restore validates the file, keeps a safety copy of the current database, then replaces it; the app must be restarted afterwards.
 * The AI backend token/URL are NOT included in backups on purpose.
 */
class BackupManager(private val context: Context, private val db: AppDatabase) {
    private val dbFile: File get() = context.getDatabasePath(AppDatabase.FILE_NAME)

    @Throws(IOException::class)
    fun export(uri: Uri): Long {
        db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").use { it.moveToFirst() }
        val out = context.contentResolver.openOutputStream(uri) ?: throw IOException("Cannot write to the chosen file")
        return out.use { o -> dbFile.inputStream().use { it.copyTo(o) } }
    }

    data class Check(val ok: Boolean, val message: String)

    private fun validate(f: File): Check {
        val header = ByteArray(16)
        f.inputStream().use { if (it.read(header) < 16) return Check(false, "File is too small to be a database") }
        if (String(header, Charsets.ISO_8859_1) != "SQLite format 3\u0000") return Check(false, "This is not a SQLite database backup")
        return try {
            val d = SQLiteDatabase.openDatabase(f.path, null, SQLiteDatabase.OPEN_READONLY)
            try {
                if (d.version > 1) return Check(false, "This backup is from a NEWER app version (schema ${d.version}); update the app first")
                d.rawQuery("SELECT COUNT(*) FROM sqlite_master WHERE name IN ('player','attempts','mistakes','questions')", null).use { c ->
                    c.moveToFirst(); if (c.getInt(0) < 4) return Check(false, "The file is missing this app's tables")
                }
                Check(true, "Backup looks valid")
            } finally { d.close() }
        } catch (e: Exception) { Check(false, "Cannot read the backup: ${e.message}") }
    }

    /** Returns a message; on success the caller must restart the process. */
    fun restore(uri: Uri): Check {
        val tmp = File(context.cacheDir, "restore_candidate.db")
        try {
            val input = context.contentResolver.openInputStream(uri) ?: return Check(false, "Cannot read the chosen file")
            input.use { i -> tmp.outputStream().use { o -> i.copyTo(o) } }
            val v = validate(tmp)
            if (!v.ok) return v
            db.close()
            val safety = File(dbFile.parentFile, AppDatabase.FILE_NAME + ".pre-restore")
            if (dbFile.exists()) dbFile.copyTo(safety, overwrite = true)
            File(dbFile.path + "-wal").delete(); File(dbFile.path + "-shm").delete()
            tmp.copyTo(dbFile, overwrite = true)
            return Check(true, "Restored. A safety copy of the previous data was kept as ${safety.name}. The app will now close; reopen it.")
        } catch (e: Exception) { return Check(false, "Restore failed: ${e.message}") }
        finally { tmp.delete() }
    }
}
