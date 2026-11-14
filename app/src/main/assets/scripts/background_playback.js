(function() {
    if (window.MyTubeBgInj) return;
    window.MyTubeBgInj = true;

    Object.defineProperty(document, 'hidden', { get: () => false });
    Object.defineProperty(document, 'visibilityState', { get: () => 'visible' });
    Object.defineProperty(document, 'webkitHidden', { get: () => false });
    Object.defineProperty(document, 'webkitVisibilityState', { get: () => 'visible' });
    Object.defineProperty(document, 'hasFocus', { get: () => true });

    document.addEventListener('visibilitychange', function(e) {
        e.stopImmediatePropagation();
    }, true);
    window.addEventListener('webkitvisibilitychange', function(e) {
        e.stopImmediatePropagation();
    }, true);
    window.addEventListener('blur', function(e) {
        e.stopImmediatePropagation();
        e.preventDefault();
    }, true);
    window.addEventListener('focus', function(e) {
        e.stopImmediatePropagation();
    }, true);

    var _origMediaSession = navigator.mediaSession;
    try {
        Object.defineProperty(navigator, 'mediaSession', {
            get: function() { return _origMediaSession; },
            configurable: true
        });
    } catch(e) {}

    window.MyTubeBgMode = false;

    /**
     * The video element that is actually playing.
     *
     * A player rebuild leaves two <video> elements in the document for a moment:
     * the old one, paused, and the new one, playing. document.querySelector('video')
     * hands back the first in document order, which is the stale one - so every
     * rebuild was reported to the app as "playback stopped". The app then resumed
     * the stale element, and the media notification flipped between pause and
     * play, several times a second.
     *
     * The playing element wins. With nothing playing, the element inside the
     * player is the one that matters.
     */
    function activeVideo() {
        var all = document.querySelectorAll('video');
        if (!all.length) return null;
        for (var i = 0; i < all.length; i++) {
            if (!all[i].paused && !all[i].ended) return all[i];
        }
        for (var j = 0; j < all.length; j++) {
            if (all[j].closest && all[j].closest('.html5-video-player, #movie_player')) {
                return all[j];
            }
        }
        return all[0];
    }

    window.setBackgroundMode = function(enabled) {
        window.MyTubeBgMode = enabled;
    };

    window.MyTubeBgTick = function() {
        var v = activeVideo();
        if (!v) return;
        if (window.MyTubeBgMode && !window.MyTubeUserPaused && !v.ended && v.currentTime > 0 && v.paused) {
            forceResume(v);
        }
        reportState();
    };

    function isYtMusic() { return location.hostname === 'music.youtube.com'; }

    function isYouTube() {
        return /(^|\.)youtube\.com$/.test(location.hostname) && !isYtMusic();
    }

    // Internal watch history, so "previous" works on the mobile SPA where the
    // browser history may span pages rather than videos.
    var vidStack = [];
    function pushCurrentVideo() {
        var id = ytVideoId();
        if (!id) return;
        if (vidStack[vidStack.length - 1] !== id) {
            vidStack.push(id);
            if (vidStack.length > 50) vidStack.shift();
        }
    }
    function popPreviousVideo() {
        if (vidStack.length >= 2) {
            vidStack.pop();
            return vidStack[vidStack.length - 1] || '';
        }
        return '';
    }

    // First playable video id in the page's initial data (mobile + desktop).
    function nextVideoIdFromData() {
        var data = window.ytInitialData;
        if (!data) return '';
        var target = data;
        try {
            var c = data.contents || {};
            target =
                (c.twoColumnWatchNextResults && c.twoColumnWatchNextResults.playlist && c.twoColumnWatchNextResults.playlist.playlist) ||
                (c.singleColumnWatchNextResults && c.singleColumnWatchNextResults.playlist && c.singleColumnWatchNextResults.playlist.playlist) ||
                (c.twoColumnWatchNextResults && c.twoColumnWatchNextResults.secondaryResults && c.twoColumnWatchNextResults.secondaryResults.secondaryResults) ||
                (c.singleColumnWatchNextResults && c.singleColumnWatchNextResults.results && c.singleColumnWatchNextResults.results.results) ||
                c;
        } catch (e) { target = data; }
        var current = ytVideoId();
        var found = '';
        (function walk(node) {
            if (found || !node || typeof node !== 'object') return;
            var renderer = node.playlistPanelVideoRenderer || node.compactVideoRenderer || node.videoRenderer;
            if (renderer && renderer.videoId && renderer.videoId !== current) {
                found = renderer.videoId;
                return;
            }
            for (var k in node) {
                if (Object.prototype.hasOwnProperty.call(node, k)) {
                    walk(node[k]);
                    if (found) return;
                }
            }
        })(target);
        return found;
    }

    function goToVideo(id) {
        if (!id) return false;
        location.assign('/watch?v=' + id);
        return true;
    }

    function findNextButton() {
        return document.querySelector('.ytp-next-button') ||
            document.querySelector('ytmusic-player-bar .next-button') ||
            document.querySelector('.next-button') ||
            document.querySelector('button[aria-label="Next"]') ||
            document.querySelector('button[title="Next"]');
    }

    function findPrevButton() {
        return document.querySelector('.ytp-prev-button') ||
            document.querySelector('ytmusic-player-bar .previous-button') ||
            document.querySelector('.previous-button') ||
            document.querySelector('button[aria-label="Previous"]') ||
            document.querySelector('button[title="Previous"]');
    }

    function player() {
        var p = document.getElementById('movie_player');
        if (p && (typeof p.nextVideo === 'function' || typeof p.previousVideo === 'function')) return p;
        return null;
    }

    function hasNext() {
        if (isYouTube() || isYtMusic()) return true;
        return !!findNextButton();
    }

    function hasPrev() {
        if (isYtMusic()) return !!findPrevButton();
        if (isYouTube()) {
            var p = player();
            return !!(p && typeof p.previousVideo === 'function') || vidStack.length > 1;
        }
        return !!findPrevButton();
    }

    window.MyTubeNext = function() {
        var p = player();
        if (p && typeof p.nextVideo === 'function') { p.nextVideo(); return; }
        var b = findNextButton();
        if (b) { b.click(); return; }
        if (isYouTube() && goToVideo(nextVideoIdFromData())) return;
        try {
            document.dispatchEvent(new KeyboardEvent('keydown', { key: 'MediaTrackNext', bubbles: true }));
        } catch (e) {}
    };

    window.MyTubePrev = function() {
        var p = player();
        if (p && typeof p.previousVideo === 'function') { p.previousVideo(); return; }
        var b = findPrevButton();
        if (b) { b.click(); return; }
        if (isYouTube() && goToVideo(popPreviousVideo())) return;
        try {
            document.dispatchEvent(new KeyboardEvent('keydown', { key: 'MediaTrackPrevious', bubbles: true }));
        } catch (e) {}
    };

    function ytVideoId() {
        var m = location.href.match(/[?&]v=([A-Za-z0-9_-]{6,})/);
        if (m) return m[1];
        var p = location.pathname.match(/^\/(?:shorts|embed|live)\/([A-Za-z0-9_-]{6,})/);
        if (p) return p[1];
        return '';
    }

    function absoluteUrl(u) {
        if (!u) return '';
        u = ('' + u).trim();
        if (u.indexOf('//') === 0) u = location.protocol + u;
        if (u.indexOf('http://') !== 0 && u.indexOf('https://') !== 0) return '';
        return u;
    }

    function getArtUrl() {
        var id = ytVideoId();
        if (id) return 'https://i.ytimg.com/vi/' + id + '/hqdefault.jpg';
        var img = document.querySelector('ytmusic-player-bar img, .ytp-cued-thumbnail-overlay-image');
        var src = img && (img.src || (img.style && img.style.backgroundImage));
        if (src) {
            var clean = absoluteUrl(src.replace(/^url\(["']?/, '').replace(/["']?\)$/, ''));
            if (clean) return clean;
        }
        var og = document.querySelector('meta[property="og:image"]');
        if (og && absoluteUrl(og.content)) return absoluteUrl(og.content);
        var v = activeVideo();
        if (v && absoluteUrl(v.poster)) return absoluteUrl(v.poster);
        return '';
    }

    function shouldResume(v) {
        return window.MyTubeBgMode && !window.MyTubeUserPaused && !v.ended && v.currentTime > 0 && v.paused;
    }

    /**
     * Puts the playback back on after a pause the page made on its own.
     *
     * The pause is not reported to the app: it is about to be undone, and
     * reporting it flips the media notification to paused and straight back to
     * playing - which is what the notification was doing, several times a
     * second. The flag is dropped if the resume does not take.
     */
    function forceResume(v) {
        if (!shouldResume(v)) return;
        window.MyTubeAutoResume = true;
        var p = v.play();
        if (p && p.catch) {
            p.catch(function() { window.MyTubeAutoResume = false; });
        }
        setTimeout(function() { window.MyTubeAutoResume = false; }, 1500);
    }

    var lastReportKey = '';
    function reportState() {
        var v = activeVideo();
        if (!v || !window.Android) return;
        pushCurrentVideo();
        var title = document.title.replace(/^(\(\d+\)\s+)?/, '').replace(' - YouTube', '');
        var artUrl = getArtUrl();
        var canNext = hasNext();
        var canPrev = hasPrev();
        // A pause that is about to be undone is not a pause. Reporting it flips
        // the media notification between pause and play.
        var paused = !!v.paused && !window.MyTubeAutoResume;
        // The position is reported in five-second steps: the service extrapolates
        // it from there, and a report per second meant a bridge call, a service
        // intent and a media-session update every second, for nothing.
        var key = paused + '|' + title + '|' + (v.duration | 0) + '|' +
            Math.floor(v.currentTime / 5) + '|' + artUrl + '|' + canNext + '|' + canPrev;
        if (key === lastReportKey) return;
        lastReportKey = key;
        window.Android.onPlaybackStateChanged(!paused, title, v.duration || 0, v.currentTime || 0, artUrl, canNext, canPrev);
    }

    var lastVideo = null;
    function attachVideo(v) {
        if (v === lastVideo) return;
        if (lastVideo) {
            lastVideo.removeEventListener('play', reportState);
            lastVideo.removeEventListener('pause', reportState);
            lastVideo.removeEventListener('pause', onVideoPaused);
        }
        lastVideo = v;
        v.addEventListener('play', reportState);
        v.addEventListener('pause', reportState);
        v.addEventListener('pause', onVideoPaused);
        reportState();
    }

    function onVideoPaused() {
        var v = activeVideo();
        if (v && v !== lastVideo) attachVideo(v);
        // Only the element that is actually the player's is resumed. A stale
        // element left behind by a player rebuild would be started again, and
        // then the two of them would fight over the audio.
        if (v && v === lastVideo) forceResume(v);
    }

    var v0 = activeVideo();
    if (v0) attachVideo(v0);

    var videoObserver = new MutationObserver(function() {
        var v = activeVideo();
        if (v && v !== lastVideo) attachVideo(v);
    });
    videoObserver.observe(document.body, { childList: true, subtree: true });

    setInterval(function() {
        var v = activeVideo();
        if (!v) return;
        forceResume(v);
        reportState();
    }, 1500);
})();
