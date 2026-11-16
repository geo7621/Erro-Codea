package com.obdstudio.app.storage

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/** Fully offline database; no credentials, telemetry or upload. */
data class ScanRecord(val id: Long, val timestamp: Long, val adapter: String, val stored: String, val pending: String, val permanent: String) {
    val totalCodes: Int get() = listOf(stored, pending, permanent).sumOf { if (it.isBlank()) 0 else it.split(',').size }
}

class ScanHistoryStore(context: Context) : SQLiteOpenHelper(context, "obdstudio_scans.sqlite", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""CREATE TABLE scans (id INTEGER PRIMARY KEY AUTOINCREMENT, timestamp INTEGER NOT NULL, adapter TEXT NOT NULL, stored TEXT NOT NULL, pending TEXT NOT NULL, permanent TEXT NOT NULL)""")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    fun insert(adapter: String, stored: List<String>, pending: List<String>, permanent: List<String>) {
        val row = ContentValues().apply {
            put("timestamp", System.currentTimeMillis())
            put("adapter", adapter)
            put("stored", stored.joinToString(","))
            put("pending", pending.joinToString(","))
            put("permanent", permanent.joinToString(","))
        }
        writableDatabase.insertOrThrow("scans", null, row)
    }

    fun recent(): List<ScanRecord> {
        val scans = mutableListOf<ScanRecord>()
        readableDatabase.rawQuery("SELECT id,timestamp,adapter,stored,pending,permanent FROM scans ORDER BY id DESC LIMIT 30", null).use { cursor ->
            while (cursor.moveToNext()) scans += ScanRecord(cursor.getLong(0),cursor.getLong(1),cursor.getString(2),cursor.getString(3),cursor.getString(4),cursor.getString(5))
        }
        return scans
    }
}
