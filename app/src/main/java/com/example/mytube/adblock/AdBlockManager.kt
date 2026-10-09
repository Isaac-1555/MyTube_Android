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

/**
 * Selectors for YouTube ad containers that must be hidden before first paint.
 * Applied at document-start so elevated/promoted tiles never flash.
 */
private val YOUTUBE_EXTRA_CSS = """
ytd-ad-slot-renderer,
ytd-promoted-sparkles-web-renderer,
ytd-promoted-sparkles-text-search-renderer,
ytd-promoted-video-renderer,
ytd-compact-promoted-video-renderer,
ytd-display-ad-renderer,
ytd-in-feed-ad-layout-renderer,
ytd-search-pyv-renderer,
ytd-player-legacy-desktop-watch-ads-renderer,
ytmusic-mealbar-promo-renderer,
ytmusic-statement-banner-renderer,
#masthead-ad,
#player-ads,
.ytp-ad-module,
.ytp-ad-overlay-container,
.video-ads{display:none!important}
""".trimIndent()

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

    /**
     * Ship a small built-in rule set so ad blocking is active on the very first
     * frame, before the async filter-list download/parse finishes.
     */
    fun loadBundled() {
        val lines = updater.loadBundled()
        if (lines.isEmpty()) return
        val parsed = lines.mapNotNull { FilterParser.parseLine(it) }
        networkFilters = parsed.filterIsInstance<UblockFilter.Network>()
        cosmeticFilters = parsed.filterIsInstance<UblockFilter.Cosmetic>()
        scriptletFilters = parsed.filterIsInstance<UblockFilter.ScriptletFilter>()
        ready = true
    }

    fun loadCached() {
        val lines = updater.loadCached()
        if (lines.isNotEmpty()) {
            val parsed = lines.mapNotNull { FilterParser.parseLine(it) }
            networkFilters = networkFilters + parsed.filterIsInstance<UblockFilter.Network>()
            cosmeticFilters = cosmeticFilters + parsed.filterIsInstance<UblockFilter.Cosmetic>()
            scriptletFilters = scriptletFilters + parsed.filterIsInstance<UblockFilter.ScriptletFilter>()
        }
        ready = true
    }

    suspend fun downloadAndLoad() {
        updater.update()
        loadCached()
    }

    /**
     * Document-start script (hooks + inline-data pruning + early cosmetic CSS)
     * built from the current filter set plus the built-in YouTube selectors.
     *
     * YouTube gets the full YouTube-specific hook set. Every other site gets the
     * generic anti-popup guard so click-triggered ad windows are suppressed and
     * generic cosmetic filters apply before first paint.
     */
    fun documentStartScript(forYoutube: Boolean = true): String {
        if (!forYoutube) {
            return UblockScriptlets.getSiteGuardJs(getGenericCosmeticCss())
        }
        val css = buildString {
            append(getCosmeticCss("youtube.com"))
            if (isNotEmpty()) append('\n')
            append(YOUTUBE_EXTRA_CSS)
        }
        return UblockScriptlets.getDocumentStartJs(css)
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

    /**
     * Top-level navigation gate for non-YouTube tabs. The page's own host and
     * its parent/subdomains are always allowed so in-site navigation (and
     * provider/embed links) keep working; anything the network filter set flags
     * as an ad/tracker is cancelled before it can load.
     */
    fun shouldBlockNavigation(url: String, pageHost: String?): Boolean {
        if (!url.startsWith("http://") && !url.startsWith("https://")) return false
        val host = try { java.net.URI(url).host?.lowercase() } catch (_: Exception) { null } ?: return false
        if (isContentHost(host)) return false
        if (!pageHost.isNullOrBlank()) {
            val ph = pageHost.lowercase()
            if (host == ph || host.endsWith(".$ph") || ph.endsWith(".$host")) return false
        }
        return shouldBlock(url)
    }

    fun getCosmeticCss(domain: String): String {
        if (!ready) return ""
        val sb = StringBuilder()
        for (f in cosmeticFilters) {
            if (FilterParser.cosmeticApplies(f.domain, domain)) {
                sb.append(f.selector).append("{display:none!important}\n")
            }
        }
        return sb.toString()
    }

    /** Cosmetic CSS for rules with no domain scope, safe on every site. */
    fun getGenericCosmeticCss(): String {
        if (!ready) return ""
        val sb = StringBuilder()
        for (f in cosmeticFilters) {
            if (f.domain.isNullOrBlank()) {
                sb.append(f.selector).append("{display:none!important}\n")
            }
        }
        return sb.toString()
    }

    fun getScriptletJs(domain: String): String {
        if (!ready) return ""
        val scripts = scriptletFilters
            .filter { FilterParser.cosmeticApplies(it.domain, domain) }
            .map { Scriptlet(it.domain, it.name, it.args) }
        return UblockScriptlets.generate(domain, scripts)
    }

    fun createEmptyResponse(): WebResourceResponse {
        return WebResourceResponse("text/plain", "utf-8", null)
    }
}
