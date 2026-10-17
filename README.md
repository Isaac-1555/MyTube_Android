<div align="center">

<img src="docs/icon.png" width="120" alt="MyTube icon" />

# MyTube

**An all-in-one Android shell for YouTube, YouTube Music, Movies and Anime — with ad blocking, background playback, sleep timer, screen lock and Picture-in-Picture.**

[![Release](https://img.shields.io/github/v/release/Isaac-1555/MyTube_Android?label=release&sort=semver)](https://github.com/Isaac-1555/MyTube_Android/releases/latest)
[![Downloads](https://img.shields.io/github/downloads/Isaac-1555/MyTube_Android/total)](https://github.com/Isaac-1555/MyTube_Android/releases)
[![Platform](https://img.shields.io/badge/platform-Android%2024%2B-3DDC84?logo=android&logoColor=white)](#requirements)
[![Kotlin](https://img.shields.io/badge/Kotlin-Compose-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)

<a href="https://github.com/Isaac-1555/MyTube_Android/releases/latest"><img src="https://img.shields.io/badge/Download-APK-2ea44f?logo=android&logoColor=white" alt="Download APK" height="36" /></a>

</div>

---

## Screenshots

| YouTube Music | YouTube | Settings | Sleep Timer | Screen Lock |
| :-----------: | :-----: | :------: | :---------: | :---------: |
| <img src="docs/screenshots/ytmusic.png" width="200" /> | <img src="docs/screenshots/youtube.png" width="200" /> | <img src="docs/screenshots/settings.png" width="200" /> | <img src="docs/screenshots/sleep-timer.png" width="200" /> | <img src="docs/screenshots/locked.png" width="200" /> |

## Demos

**Switch between tabs**

<img src="docs/gifs/tabs.gif" width="320" alt="Switching between YouTube, YouTube Music, Movies and Anime tabs" />

**Settings**

<img src="docs/gifs/settings.gif" width="320" alt="Opening settings and toggling options" />

**Sleep timer**

<img src="docs/gifs/sleep-timer.gif" width="320" alt="Scrolling the sleep timer wheel" />

**Auto-hide bottom bar**

<img src="docs/gifs/auto-hide-bar.gif" width="320" alt="Bottom bar hiding after inactivity" />

---

## Features

- **Four tabs in one app** — YouTube, YouTube Music, Movies and Anime.
- **Isolated profiles** — Movies and Anime run in separate WebView profiles, so their cookies/storage never mix with YouTube.
- **Ad blocking** — hosts-based blocking plus uBlock-style scriptlets, with a filter list that updates itself.
- **Background playback** — foreground media service keeps audio running when the screen is off or the app is backgrounded.
- **Rich media controls** — play/pause, real previous/next, stop and artwork-driven tint in the notification and on the lock screen (Android 13+).
- **Home-screen widget** — add the MyTube widget to your launcher for artwork, title and transport controls without opening the app.
- **Picture-in-Picture** — manual PiP and optional auto-PiP when you leave the app; page chrome is hidden so only the video shows.
- **Rotate to fullscreen landscape** — turn the phone while a YouTube video is playing and the video fills the screen in landscape, with the system bars and page chrome hidden. Rotating back returns to portrait.
- **Sleep timer** — 1 to 120 minutes with a snap wheel; the app closes itself when the timer ends.
- **Screen lock** — freeze the page and ignore touches, ideal when handing the phone to someone. Also available from inside fullscreen.
- **Edge-swipe navigation** — swipe from the left edge to go back, right edge to go forward.
- **Auto-hide bottom bar** — the bar fades away after 5s of inactivity and returns on touch.
- **Configurable sources** — if a Movies/Anime mirror rotates or goes down, paste a new home URL in Settings.

## What's New in v2.10.1

A fix release for rotate-to-fullscreen, which shipped in v2.10.0 without a working player UI.

- **The media controls and seek bar actually work now.** They were being covered and hidden by MyTube's own fullscreen styling: the player element was pinned above the mobile-web player's control bar and the whole page was hidden with `visibility`, which also hid the bar underneath it. YouTube's control bar is now stacked above the player and the rest of the page is hidden without touching `visibility`.
- **Tapping the video no longer drops you out of fullscreen.** Pausing reported "nothing to watch", so MyTube read your own pause as a reason to leave fullscreen. Fullscreen now holds for as long as a watch page has a video, playing or paused.
- **Double-tap to skip 10 seconds works** — tap the left or right third of the screen to jump back or forward, same as YouTube itself.
- **A lock button in fullscreen** — top right. YouTube's mobile-web player has no lock control at all, so this is MyTube's: it clears YouTube's controls so you cannot change anything by leaning on the screen, and tapping it again brings them back. The lock is also available inside fullscreen now instead of throwing you back to portrait.
- **The controls fade again** after a few seconds, as they do on YouTube. A first attempt at the fix above pinned them visible, which is exactly the property YouTube's own fade is built on.
- **One exit button, not two** — MyTube's corner **✕** now stands down once YouTube is drawing its own fullscreen controls.

Under the hood, rotate-to-fullscreen no longer fakes it: MyTube taps YouTube's own fullscreen button for you, so YouTube enters its real fullscreen and renders its real controls. `requestFullscreen()` refuses to run without a user gesture, which `evaluateJavascript` cannot manufacture, but a touch dispatched through the normal input pipeline is indistinguishable from a real one to the browser. The page is asked to grow its player only as a fallback for the moment before that tap lands.

> The experimental **Use native video fullscreen** switch is gone. It selected a path that could not work — a fullscreen request with no gesture behind it — and it is now what the feature does unconditionally.

## What's New in v2.10.0

- **Rotate to fullscreen landscape** — play a YouTube video, turn the phone, and the video goes edge-to-edge in landscape with the status bar, navigation bar and the YouTube masthead all hidden. YouTube's own controls, seek bar and double-tap 10-second seek all come with it. Turn it back and you land in portrait again.
- **Three modes in Settings** — **Off**, **Rotate to landscape** (default), or **Always landscape** if you would rather it stay in fullscreen for the whole video, YouTube-app style.
- **Reliable exit** — the corner **✕**, the back button, rotating to portrait, switching tabs, leaving the app and entering PiP all leave fullscreen cleanly. You can't get stranded in landscape.
- **Fullscreen on the YouTube tab only** — Movies and Anime mirrors use unrelated players, so they keep their normal layout.

> Previous attempts at this relied on the browser's `screen.orientation.lock()` and `requestFullscreen()`. Both are gated behind a transient user gesture inside WebView, which is why they only worked occasionally. This release decides everything natively and never asks the page for permission.


## What's New in v2.9.2

- **Home-screen media widget** — add the MyTube widget to your launcher for artwork, title, previous/play-pause/next, without opening the app.
- **Android now recognises MyTube as a media app** — the playback service is a real `MediaBrowserService`, so SystemUI media controls, Bluetooth/headset buttons and the lock screen all drive playback.
- **Media controls fixed** — the session now publishes its state as soon as it starts (previously it went active with no state, which Android ignored), reports a zero playback rate while paused so the scrubber stops drifting, and advertises a play/pause action.
- **Smoother scrubber** — playback position advances every second instead of every ten, so the progress bar moves continuously.
- **Notification polish** — tapping the notification opens the app, and it now shows playback progress without re-alerting on every update.

## Download & Install

Grab the latest signed APK from the **[Releases page](https://github.com/Isaac-1555/MyTube_Android/releases/latest)**.

Direct download: **[MyTube-v2.10.1-release.apk](https://github.com/Isaac-1555/MyTube_Android/releases/download/v2.10.1/MyTube-v2.10.1-release.apk)**

### Install on Android

1. Download the `.apk` file on your phone (or transfer it).
2. When prompted, allow your browser/file manager to **install unknown apps**
   (*Settings → Apps → Special access → Install unknown apps*).
3. Open the APK and tap **Install**.
4. On first launch, grant **notifications** if you want media controls on the lock screen and in the shade.

### Install with ADB

```bash
adb install MyTube-v2.10.1-release.apk
```

### Requirements

- Android **7.0 (API 24)** or newer (`minSdk 24`, `targetSdk 36`).
- An internet connection for streaming.

### Verify the download (optional)

```bash
apksigner verify --print-certs MyTube-v2.10.1-release.apk
```

The release is signed with APK Signature Scheme v2. Certificate SHA-256:

```
EB:37:C6:84:4E:4A:BC:DC:68:D7:63:8C:3D:9C:DA:78:
AE:C5:7A:1E:E1:30:1C:A8:31:4D:C9:F5:84:39:D8:F4
```

---

## Usage

The bottom bar is the only chrome the app draws — everything above it is the real website.

| Icon | Action |
| :--- | :----- |
| ⟳ **Reload** | Reload the current page |
| ▶ **YouTube** | Switch to the YouTube tab |
| Ⓜ **YouTube Music** | Switch to the YouTube Music tab |
| 🎬 **Movies** | Switch to the Movies tab (isolated profile) |
| 🌀 **Anime** | Switch to the Anime tab (isolated profile) |
| 🌙 **Sleep timer** | Set a timer (1–120 min); the remaining time shows next to the icon |
| ⚙ **Settings** | Open the settings sheet |
| 🔒 **Lock** | Freeze the page and block touches; tap the lock chip to unlock |

- **Go back / forward** — swipe in from the left or right edge of the screen.
- **Exit** — swiping back from your first page shows an *Exit MyTube?* dialog.
- **Fullscreen** — with a YouTube video playing, rotate the phone to fill the screen in landscape. Rotate back to leave, or use the corner **✕** / the back button.
- **Picture-in-Picture** — leave the app to enter PiP (enable **Auto PiP** in Settings), or use the system gesture.
- **Bottom bar** — hides after 5 seconds when **Auto-hide Bottom Bar** is enabled; tap anywhere to bring it back.
- **Hiding link buttons** — each site button (YouTube, YouTube Music, Movies, Anime) can be switched off individually in Settings. The button for the tab you are currently on always stays visible, so you can never get stranded on a hidden tab.

### Settings

| Setting | Description |
| :------ | :---------- |
| **Background Playback** | Keep audio playing when the app is not in the foreground |
| **Auto PiP** | Automatically enter Picture-in-Picture when you leave the app |
| **Ad Blocking** | Enable hosts + scriptlet based ad blocking |
| **Auto-hide Bottom Bar** | Hide the bottom controls after a few seconds of inactivity |
| **Video Fullscreen** | `Off`, `Rotate to landscape` (default) or `Always landscape` |
| **YouTube Button** | Show the YouTube link in the bottom bar |
| **YouTube Music Button** | Show the YouTube Music link in the bottom bar |
| **Movies Button** | Show the Movies link in the bottom bar |
| **Anime Button** | Show the Anime link in the bottom bar |
| **Movies URL / Anime URL** | Override the mirror used by the Movies and Anime tabs |

> Movies and Anime rely on public streaming mirrors that rotate or get taken down. If a tab stops loading, open **Settings** and paste the current home URL.

---

## Build from source

### Requirements

- JDK 17 or newer
- Android SDK with **build-tools 36.0.0** and **platform 36**
- `local.properties` pointing at your SDK (`sdk.dir=/path/to/Android/sdk`)

### Debug build

```bash
./gradlew assembleDebug
# app/build/outputs/apk/debug/app-debug.apk
```

### Release build (signed)

Create `keystore.properties` in the project root:

```properties
storeFile=mytube-release.jks
storePassword=********
keyAlias=mytube
keyPassword=********
```

Then build:

```bash
./gradlew assembleRelease
# app/build/outputs/apk/release/app-release.apk
```

`keystore.properties`, `*.jks` and `*.keystore` are git-ignored — never commit your signing keys.

## Changelog

### v2.10.1 — 2026-10-18

- Fix: `ytp-fullscreen`-gated chrome never appeared because the CSS pinned `#movie_player` above `#player-control-overlay`, the sibling that holds the mobile-web player's control bar, seek bar and fullscreen button
- Fix: hide everything outside the player chain with `display:none` on the siblings rather than `visibility:hidden` over the page; YouTube fades its own control bar with `visibility`, and a blanket `visibility: visible !important` over the player subtree defeated that permanently
- Fix: tapping to pause tore down fullscreen, because `VideoState.isWatchable` required `isPlaying` and a pause read as "nothing to watch"
- Fix: double-tap to skip 10 seconds was unavailable with no fullscreen chrome to drive it
- Feature: lock control inside fullscreen; YouTube's mobile-web player has no lock element in any state, so it is supplied by the app
- Fix: input lock no longer forces an exit from fullscreen, which is what made the new lock control unusable
- Fix: the app's own exit ✕ no longer stacks on top of YouTube's once YouTube is in its own fullscreen
- Fix: make `isCssFullscreen` and `isNativeFullscreen` Compose-observable; they were plain `var`s, so anything reading them never recomposed
- Fix: rate-limit the fullscreen DOM audit, which ran six `elementsFromPoint` probes plus computed-style reads on every play/pause/seek report
- Browser: hand fullscreen to YouTube rather than imitating it — tap its own fullscreen button with a synthesised touch, which Blink treats as real input and therefore as the user activation `requestFullscreen()` demands
- Browser: locate the button by its accessible label; the mobile-web player has no `.ytp-fullscreen-button` and every class-based selector matched nothing
- Browser: retry the hand-off while YouTube rebuilds the player around the rotation
- Cleanup: remove the `Use native video fullscreen` setting and the gesture-less fullscreen attempt chain behind it
- Tests: 23 unit tests, covering the pause-must-not-exit and lock-must-not-exit regressions

### v2.10.0 — 2026-10-17

- Feature: rotate-to-fullscreen landscape for YouTube video, decided natively rather than through the web fullscreen API
- Feature: `Off` / `Rotate to landscape` / `Always landscape` setting, plus an opt-in native-fullscreen switch
- Feature: fullscreen exit via the corner ✕, the back button, rotation to portrait, tab switch, app backgrounding and PiP entry
- Browser: hide everything outside the player element in fullscreen instead of relying on a selector list or a z-index contest
- Browser: unify the PiP and fullscreen stylesheets into one `<style>` element; they previously both pinned the player with `!important` and fought over it
- Browser: report video presence, watch-page and playback state from the page, pushing on `play`/`pause`/`yt-navigate-finish` instead of a 1.5s poll
- Browser: keep the fullscreen CSS pinned through a `MutationObserver` so YouTube re-renders cannot wipe it mid-rotation
- Browser: address fullscreen teardown to a specific tab rather than the active one, so switching tabs mid-fullscreen cannot strand the CSS
- Fix: `WebViewManager.isFullscreen` read a non-observable field, so nothing observing it ever recomposed
- Fix: wire up `hideCustomView()`, which was defined but never called, and add a fullscreen branch to the back handler
- Fix: pin orientation before hiding the system bars; the previous order was undone by the orientation relayout
- Fix: re-hide the system bars on a guarded poll, since a matching requested orientation dispatches no configuration change at all
- Use the compat insets accessor; the platform `WindowInsets.isVisible` needs API 30 while the app supports 24
- Tests: 22 unit tests over the fullscreen decision matrix

### v2.9.2 — 2026-10-17

- Playback: promote the service to `MediaBrowserService` so Android treats MyTube as a media app
- Playback: publish playback state and metadata as soon as the session starts, not only on the first metadata update
- Playback: report a 0f rate while paused so the system scrubber stops drifting forward
- Playback: advertise `ACTION_PLAY_PAUSE` so SystemUI renders the toggle
- Playback: extrapolate the position from the last WebView sample between reports
- Playback: register `MediaButtonReceiver` and handle `MEDIA_BUTTON`; keep playing when the task is swiped away
- Playback: add a home-screen widget with artwork, title and previous/play-pause/next
- Notification: add a content intent, progress bar and alert-once behaviour
- Playback: report position every second instead of every ten

### v2.9.0 — 2026-10-17

- Adblock: clear YT Music promo backdrop that blocks taps

### v2.8.9 — 2026-10-08

- Adblock: plug YouTube feed and video ad leaks
- Adblock: block movie/anime popups and ad navigation
- Per-tab history for YouTube Music
- Responsive bottom-bar icon sizing
- Theme-adaptive lock icon colors
- Playback: don't resume paused video on background

### v2.0.0 — 2026-09-30

- Movies and Anime tabs with isolated WebView profiles
- Rich media controls: real prev/next and artwork tint (Android 13+)
- Edge-swipe navigation, screen lock, video-only PiP
- Auto-hide bottom bar
- README with screenshots and demo gifs

## Tech stack

- **Kotlin** + **Jetpack Compose** (Material 3) UI
- **AndroidX WebKit** WebViews with per-tab profiles
- **MediaSession / MediaBrowserService** foreground service, so Android, Bluetooth and the widget can all drive playback
- **DataStore** preferences and **Room**-backed script storage
- **Palette** for artwork-tinted media controls
- Gradle **9.7.1**, Android Gradle Plugin **9.4.1**

## Disclaimer

MyTube is an unofficial wrapper and is not affiliated with, endorsed by, or sponsored by YouTube or Google. It renders third-party websites; availability of those sites is outside this project's control. Respect the terms of service and the laws that apply to you.

## License

No license is currently provided for this repository.
