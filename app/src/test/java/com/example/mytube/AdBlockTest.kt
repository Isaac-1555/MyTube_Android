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

    @Test
    fun multiDomainCosmeticMatchesAnyListedHost() {
        val f = FilterParser.parseLine("a.com,b.net##.ad-banner") as UblockFilter.Cosmetic
        assertEquals("a.com,b.net", f.domain)
        assertTrue(FilterParser.cosmeticApplies(f.domain, "www.a.com"))
        assertTrue(FilterParser.cosmeticApplies(f.domain, "b.net"))
        assertFalse(FilterParser.cosmeticApplies(f.domain, "c.org"))
    }

    @Test
    fun negatedCosmeticDomainExcludes() {
        val list = "example.com,~ads.example.com"
        assertTrue(FilterParser.cosmeticApplies(list, "www.example.com"))
        assertFalse(FilterParser.cosmeticApplies(list, "ads.example.com"))
    }

    @Test
    fun genericCosmeticAppliesEverywhere() {
        val f = FilterParser.parseLine("##[id*=\"popunder\"]") as UblockFilter.Cosmetic
        assertNull(f.domain)
        assertTrue(FilterParser.cosmeticApplies(f.domain, "anything.tld"))
    }

    @Test
    fun advancedSelectorWithHashIsKept() {
        val f = FilterParser.parseLine("site.com##div:has(> #sponsor)") as UblockFilter.Cosmetic
        assertEquals("div:has(> #sponsor)", f.selector)
    }

    @Test
    fun cosmeticExceptionRuleIsSkipped() {
        assertNull(FilterParser.parseLine("site.com#@#.ad-banner"))
    }

    @Test
    fun popupOptionIsFlagged() {
        val f = FilterParser.parseLine("||popads.net^\$popup") as UblockFilter.Network
        assertEquals("||popads.net", f.pattern)
        assertTrue(f.isPopup)
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

    @Test
    fun siteGuardNeutralizesWindowOpenAndBlankLinks() {
        val js = UblockScriptlets.getSiteGuardJs("")
        assertTrue(js.contains("window.open=function(){return null;}"))
        assertTrue(js.contains("_blank"))
        assertTrue(js.contains("__mytube_site_guard"))
    }

    @Test
    fun siteGuardEmbedsCss() {
        val js = UblockScriptlets.getSiteGuardJs("[id*=\"popunder\"]{display:none!important}")
        assertTrue(js.contains("popunder"))
    }
}
