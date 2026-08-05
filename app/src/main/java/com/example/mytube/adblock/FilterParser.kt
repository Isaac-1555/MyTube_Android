package com.example.mytube.adblock

private val domainRe = Regex("""^([\w.\-]+?(?:,[\w.\-]+)*?)?##(.+)$""")
private val networkRe = Regex("""^(@@)?(\|\|?)([^^$|]+)""")
private val scriptletRe = Regex("""^\+js\((.+)\)$""")

sealed class UblockFilter {
    data class Network(
        val pattern: String,
        val isException: Boolean = false,
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

            if (raw.isNotBlank() && !raw.contains("#") && !raw.startsWith("+")) {
                return UblockFilter.Cosmetic(domain = domain, selector = raw)
            }
        }

        val net = networkRe.find(s)
        if (net != null) {
            val isException = net.groupValues[1] == "@@"
            val prefix = net.groupValues[2]
            var pattern = net.groupValues[3].removeSuffix("^")

            val dollarIdx = pattern.indexOf('$')
            if (dollarIdx >= 0) pattern = pattern.substring(0, dollarIdx)

            if (pattern.isNotBlank()) {
                return UblockFilter.Network(
                    pattern = if (prefix == "||") "||$pattern" else pattern,
                    isException = isException
                )
            }
        }

        return null
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
}
