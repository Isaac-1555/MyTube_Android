package com.example.mytube.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.example.mytube.data.entity.BookmarkEntity
import com.example.mytube.data.entity.FilterRuleEntity
import com.example.mytube.data.entity.ScriptEntity

class LocalDatabase(context: Context) : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE scripts (
                id TEXT PRIMARY KEY,
                name TEXT NOT NULL,
                enabled INTEGER NOT NULL DEFAULT 1,
                source TEXT NOT NULL,
                injection_time TEXT NOT NULL DEFAULT 'atDocumentEnd'
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE bookmarks (
                url TEXT PRIMARY KEY,
                title TEXT NOT NULL,
                timestamp INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE filter_rules (
                pattern TEXT PRIMARY KEY,
                enabled INTEGER NOT NULL DEFAULT 1,
                type TEXT NOT NULL DEFAULT 'host'
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS scripts")
        db.execSQL("DROP TABLE IF EXISTS bookmarks")
        db.execSQL("DROP TABLE IF EXISTS filter_rules")
        onCreate(db)
    }

    // Scripts
    fun getAllScripts(): List<ScriptEntity> {
        val list = mutableListOf<ScriptEntity>()
        val cursor = readableDatabase.query("scripts", null, null, null, null, null, null)
        cursor.use {
            while (it.moveToNext()) {
                list.add(
                    ScriptEntity(
                        id = it.getString(it.getColumnIndexOrThrow("id")),
                        name = it.getString(it.getColumnIndexOrThrow("name")),
                        enabled = it.getInt(it.getColumnIndexOrThrow("enabled")) == 1,
                        source = it.getString(it.getColumnIndexOrThrow("source")),
                        injectionTime = it.getString(it.getColumnIndexOrThrow("injection_time"))
                    )
                )
            }
        }
        return list
    }

    fun getEnabledScripts(): List<ScriptEntity> {
        val list = mutableListOf<ScriptEntity>()
        val cursor = readableDatabase.query(
            "scripts", null, "enabled = 1", null, null, null, null
        )
        cursor.use {
            while (it.moveToNext()) {
                list.add(
                    ScriptEntity(
                        id = it.getString(it.getColumnIndexOrThrow("id")),
                        name = it.getString(it.getColumnIndexOrThrow("name")),
                        enabled = true,
                        source = it.getString(it.getColumnIndexOrThrow("source")),
                        injectionTime = it.getString(it.getColumnIndexOrThrow("injection_time"))
                    )
                )
            }
        }
        return list
    }

    fun upsertScript(script: ScriptEntity) {
        val values = ContentValues().apply {
            put("id", script.id)
            put("name", script.name)
            put("enabled", if (script.enabled) 1 else 0)
            put("source", script.source)
            put("injection_time", script.injectionTime)
        }
        writableDatabase.insertWithOnConflict("scripts", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun setScriptEnabled(id: String, enabled: Boolean) {
        val values = ContentValues().apply { put("enabled", if (enabled) 1 else 0) }
        writableDatabase.update("scripts", values, "id = ?", arrayOf(id))
    }

    // Bookmarks
    fun getAllBookmarks(): List<BookmarkEntity> {
        val list = mutableListOf<BookmarkEntity>()
        val cursor = readableDatabase.query("bookmarks", null, null, null, null, null, "timestamp DESC")
        cursor.use {
            while (it.moveToNext()) {
                list.add(
                    BookmarkEntity(
                        url = it.getString(it.getColumnIndexOrThrow("url")),
                        title = it.getString(it.getColumnIndexOrThrow("title")),
                        timestamp = it.getLong(it.getColumnIndexOrThrow("timestamp"))
                    )
                )
            }
        }
        return list
    }

    fun insertBookmark(bookmark: BookmarkEntity) {
        val values = ContentValues().apply {
            put("url", bookmark.url)
            put("title", bookmark.title)
            put("timestamp", bookmark.timestamp)
        }
        writableDatabase.insertWithOnConflict("bookmarks", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun deleteBookmark(url: String) {
        writableDatabase.delete("bookmarks", "url = ?", arrayOf(url))
    }

    fun bookmarkExists(url: String): Boolean {
        val cursor = readableDatabase.query(
            "bookmarks", arrayOf("url"), "url = ?", arrayOf(url), null, null, null
        )
        return cursor.use { it.count > 0 }
    }

    // Filter rules
    fun getEnabledHostPatterns(): List<String> {
        val list = mutableListOf<String>()
        val cursor = readableDatabase.query(
            "filter_rules", arrayOf("pattern"), "enabled = 1 AND type = 'host'",
            null, null, null, null
        )
        cursor.use {
            while (it.moveToNext()) {
                list.add(it.getString(it.getColumnIndexOrThrow("pattern")))
            }
        }
        return list
    }

    fun upsertFilterRule(rule: FilterRuleEntity) {
        val values = ContentValues().apply {
            put("pattern", rule.pattern)
            put("enabled", if (rule.enabled) 1 else 0)
            put("type", rule.type)
        }
        writableDatabase.insertWithOnConflict("filter_rules", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun setFilterEnabled(pattern: String, enabled: Boolean) {
        val values = ContentValues().apply { put("enabled", if (enabled) 1 else 0) }
        writableDatabase.update("filter_rules", values, "pattern = ?", arrayOf(pattern))
    }

    companion object {
        const val DB_NAME = "mytube_db"
        const val DB_VERSION = 1
    }
}
