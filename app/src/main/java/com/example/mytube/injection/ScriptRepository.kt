package com.example.mytube.injection

import com.example.mytube.data.LocalDatabase
import com.example.mytube.data.entity.ScriptEntity

class ScriptRepository(private val db: LocalDatabase) {
    fun getAll(): List<ScriptEntity> = db.getAllScripts()

    fun getEnabled(): List<ScriptEntity> = db.getEnabledScripts()

    fun upsert(script: ScriptEntity) {
        db.upsertScript(script)
    }

    fun setEnabled(id: String, enabled: Boolean) {
        db.setScriptEnabled(id, enabled)
    }
}
