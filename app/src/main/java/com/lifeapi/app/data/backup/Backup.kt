package com.lifeapi.app.data.backup

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import androidx.room.withTransaction
import androidx.sqlite.db.SupportSQLiteDatabase
import com.lifeapi.app.data.LifeDatabase
import com.lifeapi.app.data.LifeRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** Full-database JSON export / import. Format: { app, schema, exportedAt, tables: { name: [ {col: value} ] } } */
object Backup {
    private val SKIP = setOf("android_metadata", "room_master_table", "sqlite_sequence", "room_table_modification_log")

    /** Module grouping for the storage breakdown. */
    val MODULES = linkedMapOf(
        "Money" to listOf("account", "txn", "budget", "networth", "category", "recurring", "savings_goal", "savings_move", "crypto"),
        "Train" to listOf("template", "template_ex", "session", "wset", "exercise"),
        "Health" to listOf("water", "supplement", "intake", "sleep", "weight"),
        "Food" to listOf("meal", "food"),
        "Grow" to listOf("habit", "habit_check", "learning", "goal", "project", "milestone", "task", "task_done", "check_item", "focus", "journal", "wish"),
    )

    private fun tables(db: SupportSQLiteDatabase): List<String> =
        db.query("SELECT name FROM sqlite_master WHERE type='table'").use { c -> buildList { while (c.moveToNext()) add(c.getString(0)) } }
            .filter { it !in SKIP && !it.startsWith("sqlite_") }

    suspend fun rowCounts(database: LifeDatabase): Map<String, Int> = withContext(Dispatchers.IO) {
        val db = database.openHelper.readableDatabase
        tables(db).associateWith { t -> db.query("SELECT COUNT(*) FROM `$t`").use { c -> if (c.moveToFirst()) c.getInt(0) else 0 } }
    }

    fun dbSizeBytes(context: Context): Long {
        val f = context.getDatabasePath(LifeDatabase.NAME)
        return listOf(f, java.io.File(f.path + "-wal"), java.io.File(f.path + "-shm")).sumOf { if (it.exists()) it.length() else 0L }
    }

    suspend fun export(context: Context, database: LifeDatabase, uri: Uri): Int = withContext(Dispatchers.IO) {
        val db = database.openHelper.readableDatabase
        val root = JSONObject()
        root.put("app", "LifeAPI")
        root.put("schema", db.version)
        root.put("exportedAt", System.currentTimeMillis())
        val tables = JSONObject()
        var rows = 0
        for (t in tables(db)) {
            val arr = JSONArray()
            db.query("SELECT * FROM `$t`").use { c ->
                while (c.moveToNext()) {
                    val o = JSONObject()
                    for (i in 0 until c.columnCount) {
                        val name = c.getColumnName(i)
                        when (c.getType(i)) {
                            Cursor.FIELD_TYPE_NULL -> o.put(name, JSONObject.NULL)
                            Cursor.FIELD_TYPE_INTEGER -> o.put(name, c.getLong(i))
                            Cursor.FIELD_TYPE_FLOAT -> o.put(name, c.getDouble(i))
                            Cursor.FIELD_TYPE_BLOB -> o.put(name, android.util.Base64.encodeToString(c.getBlob(i), android.util.Base64.NO_WRAP))
                            else -> o.put(name, c.getString(i))
                        }
                    }
                    arr.put(o); rows++
                }
            }
            tables.put(t, arr)
        }
        root.put("tables", tables)
        context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(root.toString().toByteArray()) } ?: error("Can't write file")
        rows
    }

    /** Replaces every table with the file's content. Unknown tables/columns are skipped; missing columns get defaults. */
    suspend fun import(context: Context, repo: LifeRepository, uri: Uri): Int = withContext(Dispatchers.IO) {
        val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } ?: error("Can't read file")
        val root = JSONObject(text)
        require(root.optString("app") == "LifeAPI") { "Not a LifeAPI backup" }
        val tablesJson = root.getJSONObject("tables")
        val database = repo.db
        var rows = 0
        database.withTransaction {
            val db = database.openHelper.writableDatabase
            val existing = tables(db)
            for (t in existing) db.execSQL("DELETE FROM `$t`")
            for (t in existing) {
                val arr = tablesJson.optJSONArray(t) ?: continue
                val cols = db.query("PRAGMA table_info(`$t`)").use { c -> buildSet { val ni = c.getColumnIndex("name"); while (c.moveToNext()) add(c.getString(ni)) } }
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val cv = ContentValues()
                    o.keys().forEach { k ->
                        if (k !in cols) return@forEach
                        when (val v = o.get(k)) {
                            JSONObject.NULL -> cv.putNull(k)
                            is Int -> cv.put(k, v.toLong())
                            is Long -> cv.put(k, v)
                            is Double -> cv.put(k, v)
                            is Boolean -> cv.put(k, if (v) 1L else 0L)
                            else -> cv.put(k, v.toString())
                        }
                    }
                    db.insert(t, android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE, cv)
                    rows++
                }
            }
        }
        repo.ensureProfile()
        rows
    }

    suspend fun eraseAll(repo: LifeRepository) = withContext(Dispatchers.IO) {
        repo.db.clearAllTables()
        repo.ensureProfile()
    }
}
