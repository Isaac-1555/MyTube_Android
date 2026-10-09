package com.example.mytube

import com.example.mytube.adblock.FilterParser
import com.example.mytube.adblock.UblockFilter
import com.example.mytube.adblock.UblockScriptlets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FilterParserTest {
    @Test
    fun hostAnchoredRuleWithOptionsIsKept() {
        val f = FilterParser.parseLine("||doubleclick.net^\$third-party") as UblockFilter.Network
        assertEquals("||doubleclick.net", f.pattern)
        assertFalse(f.isException)
    }

    @Test
    fun exceptionRuleWithOptionsIsKept() {
        val f = FilterParser.parseLine("@@||googlevideo.com^\$script") as UblockFilter.Network
        assertTrue(f.isException)
    }

    @Test
    fun domainScopedRuleIsSkipped() {
        assertNull(FilterParser.parseLine("||example.com^\$domain=foo.com"))
    }

    @Test
    fun blanketRuleIsRejected() {
        assertNull(FilterParser.parseLine("||com^\$script"))
    }

    @Test
    fun cosmeticRuleStillParses() {
        val f = FilterParser.parseLine("youtube.com##ytd-promoted-video-renderer") as UblockFilter.Cosmetic
        assertEquals("youtube.com", f.domain)
        assertEquals("ytd-promoted-video-renderer", f.selector)
    }
}

class UblockScriptletsTest {
    @Test
    fun documentStartScriptContainsAdKeys() {
        val js = UblockScriptlets.getDocumentStartJs("")
        assertTrue(js.contains("promotedSparklesWebRenderer"))
        assertTrue(js.contains("AD_KEYS"))
    }

    @Test
    fun documentStartScriptEmbedsCss() {
        val js = UblockScriptlets.getDocumentStartJs("ytd-ad-slot-renderer{display:none}")
        assertTrue(js.contains("ytd-ad-slot-renderer{display:none}"))
    }

    @Test
    fun adKeysCoverFeedRenderers() {
        assertTrue(UblockScriptlets.AD_KEYS.contains("adSlotRenderer"))
        assertTrue(UblockScriptlets.AD_KEYS.contains("inFeedAdLayoutRenderer"))
        assertTrue(UblockScriptlets.AD_KEYS.contains("adPlacements"))
    }
}
