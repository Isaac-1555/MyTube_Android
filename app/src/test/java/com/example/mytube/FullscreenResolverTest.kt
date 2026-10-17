package com.example.mytube.browser

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The rotate-to-fullscreen decision must hold under every combination of state, because
 * every escape route in the Activity and the WebView funnels through it. A rule that
 * lets the app get stuck in landscape with the bars hidden is the worst failure here.
 */
class FullscreenResolverTest {

    private fun inputs(
        pref: RotateFullscreenMode = RotateFullscreenMode.ON_ROTATE,
        mode: BrowserMode = BrowserMode.YOUTUBE,
        video: VideoState = VideoState(hasVideo = true, isWatchPage = true),
        isLandscape: Boolean = false,
        isPip: Boolean = false,
        isLocked: Boolean = false,
        nativeFullscreen: Boolean = false
    ) = FullscreenInputs(
        pref, mode, video, isLandscape, isPip, isLocked, nativeFullscreen
    )

    private fun resolve(i: FullscreenInputs) = FullscreenResolver.resolve(i)

    // ---- the headline behaviour ------------------------------------------------

    @Test
    fun `rotate into landscape while watching enters fullscreen`() {
        assertEquals(
            FullscreenDecision.Enter,
            resolve(inputs(isLandscape = true))
        )
    }

    @Test
    fun `rotate back to portrait exits fullscreen`() {
        assertEquals(
            FullscreenDecision.Exit,
            resolve(inputs(isLandscape = false))
        )
    }

    @Test
    fun `always mode stays fullscreen in portrait`() {
        assertEquals(
            FullscreenDecision.Enter,
            resolve(inputs(pref = RotateFullscreenMode.ALWAYS, isLandscape = false))
        )
    }

    // ---- rule 1: kill switch ---------------------------------------------------

    @Test
    fun `off never enters even in landscape with video playing`() {
        assertEquals(
            FullscreenDecision.Stay,
            resolve(inputs(pref = RotateFullscreenMode.OFF, isLandscape = true))
        )
    }

    @Test
    fun `off leaves an already engaged session alone`() {
        assertEquals(
            FullscreenDecision.Stay,
            resolve(
                inputs(
                    pref = RotateFullscreenMode.OFF,
                    isLandscape = true,
                    nativeFullscreen = true
                )
            )
        )
    }

    // ---- rule 2: YouTube tab only ---------------------------------------------

    @Test
    fun `other tabs exit fullscreen even while watching in landscape`() {
        listOf(BrowserMode.MUSIC, BrowserMode.MOVIES, BrowserMode.ANIME).forEach { mode ->
            assertEquals(
                "mode $mode must not enter fullscreen",
                FullscreenDecision.Exit,
                resolve(inputs(mode = mode, isLandscape = true))
            )
        }
    }

    // ---- rule 3: never fight the system for the window ---------------------

    @Test
    fun `pip forces exit`() {
        assertEquals(
            FullscreenDecision.Exit,
            resolve(inputs(isLandscape = true, isPip = true))
        )
    }

    @Test
    fun `always mode still yields to pip`() {
        assertEquals(
            FullscreenDecision.Exit,
            resolve(inputs(pref = RotateFullscreenMode.ALWAYS, isPip = true))
        )
    }

    /**
     * The input lock is offered inside fullscreen, so it must not be a reason to leave
     * it. Locking mid-fullscreen used to kick the app straight back to portrait.
     */
    @Test
    fun `input lock does not end fullscreen`() {
        assertEquals(
            FullscreenDecision.Enter,
            resolve(inputs(isLandscape = true, isLocked = true))
        )
        assertEquals(
            FullscreenDecision.Exit,
            resolve(inputs(isLandscape = false, isLocked = true))
        )
    }

    // ---- rule 4: only when there is a video on a watch page -------------------

    @Test
    fun `home feed in landscape does nothing`() {
        val home = VideoState(hasVideo = false, isWatchPage = false)
        assertEquals(
            FullscreenDecision.Exit,
            resolve(inputs(video = home, isLandscape = true))
        )
    }

    @Test
    fun `video outside a watch page does not engage`() {
        val notWatch = VideoState(hasVideo = true, isWatchPage = false)
        assertEquals(
            FullscreenDecision.Exit,
            resolve(inputs(video = notWatch, isLandscape = true))
        )
    }

    /**
     * The regression that made tapping to pause kick the app out of fullscreen: a
     * pause used to report "not watchable" and the resolver read it as nothing to
     * watch.
     */
    @Test
    fun `a watch page with a video stays fullscreen regardless of playback state`() {
        val watching = VideoState(hasVideo = true, isWatchPage = true)
        assertEquals(
            FullscreenDecision.Enter,
            resolve(inputs(video = watching, isLandscape = true))
        )
        assertEquals(
            FullscreenDecision.Exit,
            resolve(inputs(video = watching, isLandscape = false))
        )
    }

    @Test
    fun `video element gone exits fullscreen`() {
        val gone = VideoState(hasVideo = false, isWatchPage = true)
        assertEquals(
            FullscreenDecision.Exit,
            resolve(inputs(video = gone, isLandscape = true))
        )
    }

    @Test
    fun `leaving the watch page exits fullscreen`() {
        val home = VideoState(hasVideo = false, isWatchPage = false)
        assertEquals(
            FullscreenDecision.Exit,
            resolve(inputs(video = home, isLandscape = true))
        )
    }

    // ---- rule 5: do not tear down our own native fullscreen -------------------

    @Test
    fun `native fullscreen in landscape stays put`() {
        assertEquals(
            FullscreenDecision.Stay,
            resolve(inputs(isLandscape = true, nativeFullscreen = true))
        )
    }

    @Test
    fun `native fullscreen exits when rotated to portrait`() {
        assertEquals(
            FullscreenDecision.Exit,
            resolve(inputs(isLandscape = false, nativeFullscreen = true))
        )
    }

    @Test
    fun `always mode re-enters native fullscreen turned portrait`() {
        assertEquals(
            FullscreenDecision.Enter,
            resolve(
                inputs(
                    pref = RotateFullscreenMode.ALWAYS,
                    isLandscape = false,
                    nativeFullscreen = true
                )
            )
        )
    }

    // ---- stability under rapid rotation ---------------------------------------

    @Test
    fun `orientation flips produce enter then exit then enter`() {
        val sequence = listOf(true, false, true, false, true).map { landscape ->
            resolve(inputs(isLandscape = landscape))
        }
        assertEquals(
            listOf(
                FullscreenDecision.Enter,
                FullscreenDecision.Exit,
                FullscreenDecision.Enter,
                FullscreenDecision.Exit,
                FullscreenDecision.Enter
            ),
            sequence
        )
    }

    @Test
    fun `repeated identical inputs resolve identically`() {
        val i = inputs(isLandscape = true)
        assertEquals(resolve(i), resolve(i))
    }

    // ---- preference mapping ----------------------------------------------------

    @Test
    fun `unknown stored preference falls back to rotate mode`() {
        assertEquals(RotateFullscreenMode.ON_ROTATE, RotateFullscreenMode.fromStorage(null))
        assertEquals(RotateFullscreenMode.ON_ROTATE, RotateFullscreenMode.fromStorage("garbage"))
    }

    @Test
    fun `stored preference names round trip`() {
        RotateFullscreenMode.entries.forEach { mode ->
            assertEquals(mode, RotateFullscreenMode.fromStorage(mode.name))
        }
    }

    // ---- video state -----------------------------------------------------------

    @Test
    fun `watchable needs both signals`() {
        assertEquals(true, VideoState(true, true).isWatchable)
        assertEquals(false, VideoState(false, true).isWatchable)
        assertEquals(false, VideoState(true, false).isWatchable)
    }

    @Test
    fun `default state is not watchable`() {
        assertEquals(false, VideoState().isWatchable)
    }
}