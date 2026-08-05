package com.example.mytube.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
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
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS scripts")
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

    companion object {
        const val DB_NAME = "mytube_db"
        const val DB_VERSION = 2
    }
}
