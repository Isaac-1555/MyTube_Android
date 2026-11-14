package com.example.mytube.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Where a movies or anime tab opens.
 *
 * The rule that was broken: a source set in Settings had no effect at all, because
 * the tab carried on resuming the last page it had visited. These tests pin the
 * precedence down so it cannot drift back.
 */
class SourceUrlPolicyTest {

    private val default = "https://default.example/home"
    private val remote = "https://remote.example/home"
    private val user = "https://user.example/home"

    // ---- precedence -----------------------------------------------------------

    @Test
    fun `user source wins over remote and default`() {
        assertEquals(
            user,
            SourceUrlPolicy.resolveHome(user, remote, default)
        )
    }

    @Test
    fun `remote home is used when there is no user source`() {
        assertEquals(
            remote,
            SourceUrlPolicy.resolveHome(null, remote, default)
        )
    }

    @Test
    fun `default home is the last resort`() {
        assertEquals(
            default,
            SourceUrlPolicy.resolveHome(null, null, default)
        )
    }

    @Test
    fun `a blank user source is not a user source`() {
        assertEquals(
            remote,
            SourceUrlPolicy.resolveHome("   ", remote, default)
        )
    }

    // ---- what a tab opens -----------------------------------------------------

    @Test
    fun `an explicit source owns the tab and beats the last visited page`() {
        assertEquals(
            user,
            SourceUrlPolicy.resolveTarget(user, remote, default, "https://old.example/watch")
        )
    }

    @Test
    fun `without a source the tab resumes the last visited page`() {
        assertEquals(
            "https://old.example/watch",
            SourceUrlPolicy.resolveTarget(null, remote, default, "https://old.example/watch")
        )
    }

    @Test
    fun `without a source and without a last page the tab opens the home`() {
        assertEquals(
            remote,
            SourceUrlPolicy.resolveTarget(null, remote, default, null)
        )
    }

    @Test
    fun `without a source and without a last page the default home is used`() {
        assertEquals(
            default,
            SourceUrlPolicy.resolveTarget(null, null, default, null)
        )
    }

    // ---- host comparison ------------------------------------------------------

    @Test
    fun `same host counts as the same site`() {
        assertTrue(SourceUrlPolicy.sameHost("https://user.example/watch", user))
    }

    @Test
    fun `a www host is the same site as the bare host`() {
        assertTrue(SourceUrlPolicy.sameHost("https://www.user.example/watch", user))
    }

    @Test
    fun `a different host is a different site`() {
        assertFalse(SourceUrlPolicy.sameHost("https://old.example/watch", user))
    }

    @Test
    fun `a missing url has no host`() {
        assertFalse(SourceUrlPolicy.sameHost(null, user))
        assertFalse(SourceUrlPolicy.sameHost(user, null))
        assertFalse(SourceUrlPolicy.sameHost("", ""))
    }

    @Test
    fun `host comparison ignores case`() {
        assertTrue(SourceUrlPolicy.sameHost("https://USER.example/watch", user))
    }

    // ---- origin comparison -----------------------------------------------------

    @Test
    fun `same origin is the same site`() {
        assertTrue(SourceUrlPolicy.sameOrigin("https://user.example/watch", user))
    }

    @Test
    fun `a cleartext page is not the configured site`() {
        // A page that was loaded over http and failed is an error page, and the
        // tab has to be moved off it.
        assertFalse(SourceUrlPolicy.sameOrigin("http://user.example/", user))
    }

    @Test
    fun `a different host is a different origin`() {
        assertFalse(SourceUrlPolicy.sameOrigin("https://old.example/watch", user))
    }

    @Test
    fun `a missing url has no origin`() {
        assertFalse(SourceUrlPolicy.sameOrigin(null, user))
        assertFalse(SourceUrlPolicy.sameOrigin(user, null))
    }
}
