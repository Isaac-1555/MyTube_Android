package com.example.mytube.adblock

private val domainRe = Regex("""^([\w.~\-]+?(?:,[\w.~\-]+)*?)?##(.+)$""")
private val networkRe = Regex("""^(@@)?(\|\|?)([^^$|]+)""")
private val scriptletRe = Regex("""^\+js\((.+)\)$""")

sealed class UblockFilter {
    data class Network(
        val pattern: String,
        val isException: Boolean = false,
        val isPopup: Boolean = false,
    ) : UblockFilter()

    data class Cosmetic(
        val selector: String,
        val domain: String? = null,
    ) : UblockFilter()

    data class ScriptletFilter(
        val domain: String?,
        val name: String,
        val args: List<String>,
    ) : UblockFilter()
}

object FilterParser {
    fun parseLine(line: String): UblockFilter? {
        val s = line.trim()
        if (s.isEmpty() || s.startsWith('!') || s.startsWith('[')) return null
        // Cosmetic exception rules (#@#) are not tracked; drop them so they are
        // never misparsed as network patterns.
        if (s.contains("#@#")) return null

        val dom = domainRe.find(s)
        if (dom != null) {
            val raw = dom.groupValues[2]
            val domain = dom.groupValues[1].ifBlank { null }

            val sc = scriptletRe.find(raw)
            if (sc != null) {
                val body = sc.groupValues[1]
                val parts = parseArgs(body)
                if (parts.isNotEmpty()) {
                    return UblockFilter.ScriptletFilter(
                        domain = domain,
                        name = parts[0].trim(),
                        args = parts.drop(1).map { it.trim() }
                    )
                }
            }

            if (raw.isNotBlank() && !raw.startsWith("+")) {
                return UblockFilter.Cosmetic(domain = domain, selector = raw)
            }
        }

        // Anything left containing `#` is cosmetic syntax we don't support
        // (procedural `#?#`, exceptions `#@#`). Never treat it as a network rule.
        if (s.contains('#')) return null

        // Split off $options so host-anchored rules aren't discarded wholesale
        // (almost every EasyList/uBlock network rule carries options). Options
        // that narrow matching to a scope we can't honor ($domain=, ipaddress=)
        // are skipped rather than applied globally.
        val dollar = s.indexOf('$')
        val patternPart = if (dollar >= 0) s.substring(0, dollar) else s
        val options = if (dollar >= 0) s.substring(dollar + 1) else ""
        if (options.contains("badfilter")) return null
        if (options.contains("domain=") || options.contains("ipaddress=")) return null

        val net = networkRe.find(patternPart)
        if (net != null) {
            val isException = net.groupValues[1] == "@@"
            val prefix = net.groupValues[2]
            val pattern = net.groupValues[3].removeSuffix("^")
            val isPopup = options.contains("popup") || options.contains("popunder")

            if (pattern.isNotBlank() && !isBlanketPattern(prefix, pattern)) {
                return UblockFilter.Network(
                    pattern = if (prefix == "||") "||$pattern" else pattern,
                    isException = isException,
                    isPopup = isPopup
                )
            }
        }

        return null
    }

    /**
     * Reject patterns that would match far too much once stripped of their
     * options: `||com`, bare `http`, etc.
     */
    private fun isBlanketPattern(prefix: String, pattern: String): Boolean {
        if (prefix == "||") {
            val domain = pattern.removePrefix("||").trim('/')
            return domain.isEmpty() || (!domain.contains('.') && !domain.contains('/'))
        }
        return pattern.length < 4
    }

    private fun parseArgs(body: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var depth = 0
        for (ch in body) {
            when {
                ch == ',' && depth == 0 -> { result.add(current.toString()); current.clear() }
                ch == '(' -> depth++
                ch == ')' -> depth--
                else -> current.append(ch)
            }
        }
        if (current.isNotBlank()) result.add(current.toString())
        return result
    }

    fun filterMatches(filter: UblockFilter.Network, url: String, host: String? = null): Boolean {
        val p = filter.pattern
        return when {
            p.startsWith("||") -> {
                val domain = p.removePrefix("||")
                val h = host ?: runCatching { java.net.URI(url).host }.getOrNull() ?: return false
                h == domain || h.endsWith(".$domain") || url.contains("/$domain/")
            }
            p.startsWith("|") -> {
                val exact = p.removePrefix("|")
                url.startsWith(exact)
            }
            else -> url.contains(p)
        }
    }

    /**
     * Whether a cosmetic/scriptlet rule scoped to [domainList] applies to [host].
     * Handles comma-separated lists, suffix matches, and `~` negations
     * (e.g. `a.com,~b.a.com##.ad`).
     */
    fun cosmeticApplies(domainList: String?, host: String?): Boolean {
        if (domainList.isNullOrBlank()) return true
        if (host.isNullOrBlank()) return false
        val h = host.lowercase()
        var hasPositive = false
        var positiveMatch = false
        for (raw in domainList.split(',')) {
            val e = raw.trim().lowercase()
            if (e.isEmpty()) continue
            if (e.startsWith("~")) {
                val d = e.substring(1)
                if (d.isNotEmpty() && (h == d || h.endsWith(".$d"))) return false
            } else {
                hasPositive = true
                if (h == e || h.endsWith(".$e")) positiveMatch = true
            }
        }
        return !hasPositive || positiveMatch
    }
}
