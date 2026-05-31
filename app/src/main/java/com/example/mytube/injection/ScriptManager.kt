package com.example.mytube.injection

import android.content.Context
import com.example.mytube.data.entity.ScriptEntity

class ScriptManager(
    private val context: Context,
    private val repository: ScriptRepository
) {
    fun loadAndInject(injector: (String, InjectionTime) -> Unit) {
        val enabled = repository.getEnabled()
        for (script in enabled) {
            val time = when (script.injectionTime) {
                "atDocumentStart" -> InjectionTime.AT_DOCUMENT_START
                else -> InjectionTime.AT_DOCUMENT_END
            }
            injector(script.source, time)
        }
    }

    fun seedDefaults() {
        val existing = repository.getAll()
        val existingIds = existing.map { it.id }.toSet()

        listOf(
            "scripts/background_playback.js" to "Background Playback"
        ).forEach { (assetPath, name) ->
            val id = assetPath.removePrefix("scripts/").removeSuffix(".js")
            if (id !in existingIds) {
                val source = loadAsset(assetPath) ?: return@forEach
                repository.upsert(
                    ScriptEntity(
                        id = id,
                        name = name,
                        enabled = true,
                        source = source,
                        injectionTime = "atDocumentEnd"
                    )
                )
            }
        }
    }

    fun loadAsset(path: String): String? {
        return try {
            context.assets.open(path).bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            null
        }
    }
}
