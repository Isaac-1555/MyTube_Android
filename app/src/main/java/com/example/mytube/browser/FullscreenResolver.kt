package com.example.mytube.browser

/** User preference for the rotate-to-fullscreen behaviour. */
enum class RotateFullscreenMode {
    /** Feature disabled. */
    OFF,

    /** Enter immersive fullscreen when the device rotates into landscape. */
    ON_ROTATE,

    /** Stay in immersive fullscreen for the whole time a video plays. */
    ALWAYS;

    companion object {
        val DEFAULT = ON_ROTATE

        fun fromStorage(raw: String?): RotateFullscreenMode =
            entries.firstOrNull { it.name == raw } ?: DEFAULT
    }
}

/** Native/CSS fullscreen is only ever entered or left through these decisions. */
enum class FullscreenDecision { Stay, Exit, Enter }

/**
 * What the page currently reports about its video. Fed by
 * [assets/scripts/video_state.js] through the `Android.onVideoStateChanged` bridge.
 */
data class VideoState(
    val hasVideo: Boolean = false,
    val isWatchPage: Boolean = false,
    val isPlaying: Boolean = false
) {
    val isWatchable: Boolean get() = hasVideo && isWatchPage && isPlaying
}

/**
 * Every input that can change the fullscreen decision. Deliberately free of Android
 * types so [FullscreenResolver] is unit-testable on the JVM.
 */
data class FullscreenInputs(
    val pref: RotateFullscreenMode = RotateFullscreenMode.DEFAULT,
    val mode: BrowserMode = BrowserMode.YOUTUBE,
    val video: VideoState = VideoState(),
    val isLandscape: Boolean = false,
    val isPip: Boolean = false,
    val isLocked: Boolean = false,
    val nativeFullscreen: Boolean = false
)

/**
 * Decides whether immersive landscape fullscreen should be active.
 *
 * The whole point of this living outside the WebView: nothing here consults a web
 * API. The previous approach called `screen.orientation.lock()` and
 * `element.requestFullscreen()` from JavaScript, both of which are gated on
 * transient user activation inside WebView and therefore failed almost every time.
 */
object FullscreenResolver {

    fun resolve(i: FullscreenInputs): FullscreenDecision {
        // Kill switch.
        if (i.pref == RotateFullscreenMode.OFF) return FullscreenDecision.Stay

        // Only the YouTube tab. Movies/anime mirrors use unrelated players whose
        // containers the CSS pin cannot safely target.
        if (i.mode != BrowserMode.YOUTUBE) return FullscreenDecision.Exit

        // Never fight the system for the window, and never engage while input is locked.
        if (i.isPip || i.isLocked) return FullscreenDecision.Exit

        // Home feed, shorts shelf, paused video, ended video, ad: nothing to do.
        if (!i.video.isWatchable) return FullscreenDecision.Exit

        // Already in real custom-view fullscreen and rotated to landscape: leave it
        // alone, otherwise our own rotation would tear the native view back down.
        if (i.nativeFullscreen && i.isLandscape) return FullscreenDecision.Stay

        if (i.pref == RotateFullscreenMode.ALWAYS) return FullscreenDecision.Enter

        return if (i.isLandscape) FullscreenDecision.Enter else FullscreenDecision.Exit
    }
}