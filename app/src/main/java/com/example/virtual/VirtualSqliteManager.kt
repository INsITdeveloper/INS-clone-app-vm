package com.example.virtual

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import java.io.File

data class SqliteColumnSchema(
    val cid: Int,
    val name: String,
    val type: String,
    val isPrimaryKey: Boolean
)

data class SqliteTableData(
    val tableName: String,
    val columns: List<SqliteColumnSchema>,
    val rows: List<Map<String, String>>, // Includes "_rowid_" where available
    val statusMessage: String = ""
)

/**
 * Interactive SQLite Database Engine for INS Manager.
 * Opens real `.db` files inside `/virtual/user/<instance>/<package_name>/databases/`
 * and allows browsing tables, editing cell values, inserting/deleting rows, and running custom SQL.
 */
object VirtualSqliteManager {

    fun listTables(dbFile: File): List<String> {
        if (!dbFile.exists()) return emptyList()
        val tables = mutableListOf<String>()
        val db = SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
        try {
            val cursor = db.rawQuery(
                "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'android_%' AND name NOT LIKE 'sqlite_%' ORDER BY name",
                null
            )
            cursor.use {
                while (it.moveToNext()) {
                    tables.add(it.getString(0))
                }
            }
        } finally {
            db.close()
        }
        return tables
    }

    fun readTable(dbFile: File, tableName: String, limit: Int = 100): SqliteTableData {
        if (!dbFile.exists()) {
            return SqliteTableData(tableName, emptyList(), emptyList(), "Database file not found")
        }
        val columns = mutableListOf<SqliteColumnSchema>()
        val rows = mutableListOf<Map<String, String>>()

        val db = SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
        try {
            val sanitizedTable = tableName.replace("[^a-zA-Z0-9_]".toRegex(), "")
            db.rawQuery("PRAGMA table_info($sanitizedTable)", null).use { pragmaCursor ->
                val cidIdx = pragmaCursor.getColumnIndex("cid")
                val nameIdx = pragmaCursor.getColumnIndex("name")
                val typeIdx = pragmaCursor.getColumnIndex("type")
                val pkIdx = pragmaCursor.getColumnIndex("pk")
                while (pragmaCursor.moveToNext()) {
                    columns.add(
                        SqliteColumnSchema(
                            cid = if (cidIdx >= 0) pragmaCursor.getInt(cidIdx) else 0,
                            name = if (nameIdx >= 0) pragmaCursor.getString(nameIdx) else "",
                            type = if (typeIdx >= 0) pragmaCursor.getString(typeIdx) else "TEXT",
                            isPrimaryKey = if (pkIdx >= 0) pragmaCursor.getInt(pkIdx) > 0 else false
                        )
                    )
                }
            }

            db.rawQuery("SELECT rowid AS _rowid_, * FROM $sanitizedTable LIMIT $limit", null).use { cursor ->
                val colNames = cursor.columnNames
                while (cursor.moveToNext()) {
                    val rowMap = linkedMapOf<String, String>()
                    for (i in colNames.indices) {
                        rowMap[colNames[i]] = runCatching { cursor.getString(i) ?: "NULL" }.getOrDefault("BLOB")
                    }
                    rows.add(rowMap)
                }
            }
        } catch (e: Exception) {
            return SqliteTableData(tableName, columns, rows, "Error: ${e.message}")
        } finally {
            db.close()
        }

        return SqliteTableData(
            tableName = tableName,
            columns = columns,
            rows = rows,
            statusMessage = "${rows.size} baris dimuat dari tabel '$tableName'"
        )
    }

    fun updateRow(
        dbFile: File,
        tableName: String,
        rowId: String,
        updatedValues: Map<String, String>
    ): Result<String> {
        return runCatching {
            val sanitizedTable = tableName.replace("[^a-zA-Z0-9_]".toRegex(), "")
            val db = SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READWRITE)
            try {
                val cv = ContentValues()
                updatedValues.forEach { (col, value) ->
                    if (col != "_rowid_") {
                        cv.put(col, value)
                    }
                }
                val affected = db.update(sanitizedTable, cv, "rowid = ?", arrayOf(rowId))
                "Berhasil memperbarui $affected baris (rowid=$rowId)"
            } finally {
                db.close()
            }
        }
    }

    fun insertRow(
        dbFile: File,
        tableName: String,
        values: Map<String, String>
    ): Result<String> {
        return runCatching {
            dbFile.parentFile?.mkdirs()
            val sanitizedTable = tableName.replace("[^a-zA-Z0-9_]".toRegex(), "")
            val db = SQLiteDatabase.openOrCreateDatabase(dbFile, null)
            try {
                val cv = ContentValues()
                values.forEach { (col, value) ->
                    if (col != "_rowid_" && value.isNotBlank()) {
                        cv.put(col, value)
                    }
                }
                val newId = db.insertOrThrow(sanitizedTable, null, cv)
                "Baris baru berhasil ditambahkan (rowid=$newId)"
            } finally {
                db.close()
            }
        }
    }

    fun deleteRow(
        dbFile: File,
        tableName: String,
        rowId: String
    ): Result<String> {
        return runCatching {
            val sanitizedTable = tableName.replace("[^a-zA-Z0-9_]".toRegex(), "")
            val db = SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READWRITE)
            try {
                val deleted = db.delete(sanitizedTable, "rowid = ?", arrayOf(rowId))
                "Berhasil menghapus $deleted baris (rowid=$rowId)"
            } finally {
                db.close()
            }
        }
    }

    fun executeCustomSql(dbFile: File, sql: String): Result<SqliteTableData> {
        return runCatching {
            dbFile.parentFile?.mkdirs()
            val trimmed = sql.trim()
            val db = SQLiteDatabase.openOrCreateDatabase(dbFile, null)
            try {
                if (trimmed.startsWith("SELECT", ignoreCase = true) || trimmed.startsWith("PRAGMA", ignoreCase = true)) {
                    val cols = mutableListOf<SqliteColumnSchema>()
                    val rows = mutableListOf<Map<String, String>>()
                    db.rawQuery(trimmed, null).use { cursor ->
                        val names = cursor.columnNames
                        names.forEachIndexed { idx, name ->
                            cols.add(SqliteColumnSchema(idx, name, "TEXT", false))
                        }
                        while (cursor.moveToNext()) {
                            val map = linkedMapOf<String, String>()
                            for (i in names.indices) {
                                map[names[i]] = runCatching { cursor.getString(i) ?: "NULL" }.getOrDefault("BLOB")
                            }
                            rows.add(map)
                        }
                    }
                    SqliteTableData("Query Result", cols, rows, "Query berhasil: ${rows.size} baris")
                } else {
                    db.execSQL(trimmed)
                    SqliteTableData("Executed", emptyList(), emptyList(), "Perintah SQL berhasil dieksekusi: $trimmed")
                }
            } finally {
                db.close()
            }
        }
    }
}
