package com.example.mytube.adblock

import android.webkit.WebResourceResponse

private val blockedUrlSubstrings = listOf(
    "/pagead/", "/ads/", "adclick", "googleads", "doubleclick",
    "adservice", "yt_ads", "ad_break", "adunit", "adinteractive",
)

private val blockedHosts = setOf(
    "doubleclick.net", "googletagservices.com", "googlesyndication.com",
    "googleadservices.com",
)

private val contentHosts = setOf(
    "googlevideo.com", "youtube.com", "ytimg.com",
    "youtube-nocookie.com", "ggpht.com", "googleusercontent.com",
    "ytstatic.l.google.com",
)

private fun isContentHost(host: String): Boolean {
    return contentHosts.any { host == it || host.endsWith(".$it") }
}

class AdBlockManager(private val updater: FilterListUpdater) {
    @Volatile
    private var networkFilters: List<UblockFilter.Network> = emptyList()

    @Volatile
    private var cosmeticFilters: List<UblockFilter.Cosmetic> = emptyList()

    @Volatile
    private var scriptletFilters: List<UblockFilter.ScriptletFilter> = emptyList()

    @Volatile
    private var ready = false

    val isReady: Boolean get() = ready

    fun loadCached() {
        val lines = updater.loadCached()
        if (lines.isEmpty()) return
        val parsed = lines.mapNotNull { FilterParser.parseLine(it) }
        networkFilters = parsed.filterIsInstance<UblockFilter.Network>()
        cosmeticFilters = parsed.filterIsInstance<UblockFilter.Cosmetic>()
        scriptletFilters = parsed.filterIsInstance<UblockFilter.ScriptletFilter>()
        ready = true
    }

    suspend fun downloadAndLoad() {
        updater.update()
        loadCached()
    }

    fun shouldBlock(url: String): Boolean {
        if (ready) {
            val host = try { java.net.URI(url).host } catch (_: Exception) { return false }
            if (host == null || isContentHost(host)) return false
            var blocked = false
            for (f in networkFilters) {
                if (FilterParser.filterMatches(f, url, host)) {
                    if (f.isException) return false
                    blocked = true
                }
            }
            return blocked
        }

        val uri = try { java.net.URI(url) } catch (_: Exception) { return false }
        val host = uri.host ?: return false
        if (isContentHost(host)) return false
        if (blockedHosts.any { host.endsWith(it) }) return true
        return blockedUrlSubstrings.any { url.contains(it, ignoreCase = true) }
    }

    fun getCosmeticCss(domain: String): String {
        if (!ready) return ""
        val sb = StringBuilder()
        for (f in cosmeticFilters) {
            if (f.domain == null || domain.contains(f.domain, ignoreCase = true)) {
                sb.append(f.selector).append("{display:none!important}\n")
            }
        }
        return sb.toString()
    }

    fun getScriptletJs(domain: String): String {
        if (!ready) return ""
        val scripts = scriptletFilters.map { Scriptlet(it.domain, it.name, it.args) }
        return UblockScriptlets.generate(domain, scripts)
    }

    fun createEmptyResponse(): WebResourceResponse {
        return WebResourceResponse("text/plain", "utf-8", null)
    }
}
