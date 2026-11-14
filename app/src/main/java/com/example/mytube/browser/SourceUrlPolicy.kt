package com.example.mytube.browser

import java.net.URI

/**
 * Where a movies or anime tab should open.
 *
 * An explicit source set in Settings owns the tab: it always opens that URL, and
 * it is not resumed from whatever page happened to be visited last. Without one
 * the tab resumes the last page, and falls back to the advertised or built-in
 * home.
 *
 * Deliberately free of Android types so it is unit-testable on the JVM.
 */
object SourceUrlPolicy {

    /** User override first, then the remotely advertised home, then the built-in one. */
    fun resolveHome(userUrl: String?, remoteHome: String?, defaultHome: String): String =
        firstNonBlank(userUrl, remoteHome, defaultHome)

    /**
     * The URL a tab opens.
     *
     * With an explicit source the tab belongs to that site, so the last visited
     * page is deliberately not used - that was the bug: a source set in Settings
     * had no effect at all, because the tab carried on resuming the old site.
     */
    fun resolveTarget(
        userUrl: String?,
        remoteHome: String?,
        defaultHome: String,
        lastUrl: String?
    ): String {
        val home = resolveHome(userUrl, remoteHome, defaultHome)
        if (!userUrl.isNullOrBlank()) return home
        return firstNonBlank(lastUrl, home)
    }

    /**
     * True when both URLs are on the same host. Used to decide whether a tab has
     * to be moved: same site, leave the user where they are; different site,
     * navigate.
     */
    fun sameHost(a: String?, b: String?): Boolean {
        val hostA = hostOf(a)
        val hostB = hostOf(b)
        return hostA != null && hostA == hostB
    }

    /**
     * True when both URLs are the same origin - same scheme, same host.
     *
     * Stricter than [sameHost] on purpose: a page that was loaded over cleartext
     * http and failed is not the site the tab is configured for, and leaving it
     * there would strand the tab on an error page.
     */
    fun sameOrigin(a: String?, b: String?): Boolean {
        val originA = originOf(a)
        val originB = originOf(b)
        return originA != null && originA == originB
    }

    private fun originOf(url: String?): String? {
        if (url.isNullOrBlank()) return null
        val uri = runCatching { URI(url) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase() ?: return null
        val host = uri.host?.lowercase()?.removePrefix("www.") ?: return null
        return "$scheme://$host"
    }

    private fun hostOf(url: String?): String? {
        if (url.isNullOrBlank()) return null
        val host = runCatching { URI(url).host }.getOrNull() ?: return null
        return host.lowercase().removePrefix("www.")
    }

    private fun firstNonBlank(vararg values: String?): String =
        values.firstOrNull { !it.isNullOrBlank() }?.trim().orEmpty()
}
