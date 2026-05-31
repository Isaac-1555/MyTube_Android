package com.example.mytube.adblock

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL

class FilterListUpdater(private val context: Context) {
    private val cacheDir: File
        get() = File(context.cacheDir, "ublock_filters").also { it.mkdirs() }

    private val sources = listOf(
        "https://easylist.to/easylist/easylist.txt",
        "https://easylist.to/easylist/easyprivacy.txt",
        "https://raw.githubusercontent.com/uBlockOrigin/uAssets/master/filters/filters.txt",
        "https://raw.githubusercontent.com/uBlockOrigin/uAssets/master/filters/badware.txt",
        "https://raw.githubusercontent.com/uBlockOrigin/uAssets/master/filters/privacy.txt",
        "https://raw.githubusercontent.com/uBlockOrigin/uAssets/master/filters/unbreak.txt",
        "https://raw.githubusercontent.com/uBlockOrigin/uAssets/master/filters/quick-fixes.txt",
        "https://raw.githubusercontent.com/uBlockOrigin/uAssets/master/filters/annoyances.txt",
    )

    data class UpdateResult(val filesDownloaded: Int, val totalLines: Int)

    suspend fun update(): UpdateResult = withContext(Dispatchers.IO) {
        val dir = cacheDir
        var downloaded = 0
        var lines = 0
        for (url in sources) {
            try {
                val name = url.substringAfterLast('/')
                val content = URL(url).readText()
                File(dir, name).writeText(content)
                downloaded++
                lines += content.lines().size
            } catch (_: Exception) { }
        }
        UpdateResult(downloaded, lines)
    }

    fun loadCached(): List<String> {
        val dir = cacheDir
        if (!dir.exists()) return emptyList()
        return dir.listFiles()
            ?.filter { it.isFile }
            ?.sortedBy { it.lastModified() }
            ?.flatMap { it.readLines() }
            ?: emptyList()
    }

    fun hasCached(): Boolean = cacheDir.exists() && (cacheDir.listFiles()?.isNotEmpty() == true)
}
