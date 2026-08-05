package com.example.mytube.injection

import android.content.Context
import com.example.mytube.data.entity.ScriptEntity

class ScriptManager(
    private val context: Context,
    private val repository: ScriptRepository
) {
    fun loadAndInject(injector: (String, InjectionTime) -> Unit, includeBgPlayback: Boolean = true) {
        val enabled = repository.getEnabled().filter { it.id != "background_playback" }
        for (script in enabled) {
            val time = when (script.injectionTime) {
                "atDocumentStart" -> InjectionTime.AT_DOCUMENT_START
                else -> InjectionTime.AT_DOCUMENT_END
            }
            injector(script.source, time)
        }
        if (!includeBgPlayback) return
        val bgSource = loadAsset("scripts/background_playback.js") ?: return
        injector(bgSource, InjectionTime.AT_DOCUMENT_END)
    }

    fun seedDefaults() {
        val existing = repository.getAll()
        val existingIds = existing.map { it.id }.toSet()

        // Core scripts seeded here. background_playback is injected unconditionally
        // in loadAndInject() — not user-toggleable.
    }

    fun loadAsset(path: String): String? {
        return try {
            context.assets.open(path).bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            null
        }
    }
}
