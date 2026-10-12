package com.seifsayed0.wasalalarm.data

import android.annotation.SuppressLint
import android.content.Context
import androidx.core.content.edit
import com.seifsayed0.wasalalarm.domain.LocalAppData

sealed interface LocalDataRead {
    data class Loaded(val data: LocalAppData) : LocalDataRead
    data object Missing : LocalDataRead
    data object Corrupt : LocalDataRead
}

/** Bounded local-only data store. The active trip remains in the legacy TripStore. */
class LocalDataStore(context: Context) {
    private val prefs = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    fun read(): LocalDataRead = synchronized(PROCESS_LOCK) { readLocked() }

    fun load(): LocalAppData = when (val result = read()) {
        is LocalDataRead.Loaded -> result.data
        LocalDataRead.Missing, LocalDataRead.Corrupt -> LocalAppData()
    }

    /** Replace the full value, preserving one bounded backup if the prior bytes are corrupt. */
    @SuppressLint("ApplySharedPref", "UseKtx")
    fun save(data: LocalAppData): Boolean = synchronized(PROCESS_LOCK) { writeLocked(data) }

    /** Read-modify-write transaction shared by every store instance in this app process. */
    @SuppressLint("ApplySharedPref", "UseKtx")
    fun update(transform: (LocalAppData) -> LocalAppData, failIfCorrupt: Boolean = false): Boolean = synchronized(PROCESS_LOCK) {
        val current = when (val result = readLocked()) {
            is LocalDataRead.Loaded -> result.data
            LocalDataRead.Missing -> LocalAppData()
            LocalDataRead.Corrupt -> if (failIfCorrupt) return@synchronized false else LocalAppData()
        }
        val next = runCatching { transform(current) }.getOrNull() ?: return@synchronized false
        writeLocked(next)
    }

    private fun readLocked(): LocalDataRead {
        val raw = prefs.getString(DATA_KEY, null) ?: return LocalDataRead.Missing
        val decoded = LocalAppDataCodec.decode(raw) ?: return LocalDataRead.Corrupt
        return LocalDataRead.Loaded(decoded)
    }

    @SuppressLint("ApplySharedPref", "UseKtx")
    private fun writeLocked(data: LocalAppData): Boolean {
        val encoded = runCatching { LocalAppDataCodec.encode(data) }.getOrNull() ?: return false
        val existing = prefs.getString(DATA_KEY, null)
        val preserveCorrupt = existing?.let { LocalAppDataCodec.decode(it) == null } == true
        return try {
            val editor = prefs.edit()
            if (preserveCorrupt && !prefs.contains(CORRUPT_BACKUP_KEY)) {
                editor.putString(CORRUPT_BACKUP_KEY, existing.take(LocalAppDataCodec.MAX_ENCODED_CHARS))
            }
            editor.putString(DATA_KEY, encoded).commit()
        } catch (_: Exception) {
            false
        }
    }

    companion object {
        private const val FILE_NAME = "wasal_local_data_v1"
        private const val DATA_KEY = "app_data"
        private const val CORRUPT_BACKUP_KEY = "corrupt_backup"
        private val PROCESS_LOCK = Any()
    }
}
