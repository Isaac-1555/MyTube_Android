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

    window.MyTubeUserPaused = false;
    window.MyTubeBgMode = false;

    window.setBackgroundMode = function(enabled) {
        window.MyTubeBgMode = enabled;
    };

    window.MyTubePause = function() {
        window.MyTubeUserPaused = true;
        var v = document.querySelector('video');
        if (v) v.pause();
    };
    window.MyTubePlay = function() {
        window.MyTubeUserPaused = false;
        var v = document.querySelector('video');
        if (v) v.play();
    };

    window.MyTubeBgTick = function() {
        var v = document.querySelector('video');
        if (!v) return;
        if (window.MyTubeBgMode && !window.MyTubeUserPaused && !v.ended && v.currentTime > 0 && v.paused) {
            v.play().catch(function(){});
        }
        reportState();
    };

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

    function hasNext() { return !!findNextButton(); }
    function hasPrev() { return !!findPrevButton(); }

    window.MyTubeNext = function() {
        var b = findNextButton();
        if (b) { b.click(); return; }
        try {
            document.dispatchEvent(new KeyboardEvent('keydown', { key: 'MediaTrackNext', bubbles: true }));
        } catch (e) {}
    };

    window.MyTubePrev = function() {
        var b = findPrevButton();
        if (b) { b.click(); return; }
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

    function getArtUrl() {
        var v = document.querySelector('video');
        if (v && v.poster) return v.poster;
        var id = ytVideoId();
        if (id) return 'https://i.ytimg.com/vi/' + id + '/hqdefault.jpg';
        var og = document.querySelector('meta[property="og:image"]');
        if (og && og.content) return og.content;
        var img = document.querySelector('ytmusic-player-bar img, .ytp-cued-thumbnail-overlay-image');
        var src = img && (img.src || (img.style && img.style.backgroundImage));
        if (src) {
            var clean = src.replace(/^url\(["']?/, '').replace(/["']?\)$/, '');
            if (clean.indexOf('http') === 0) return clean;
        }
        return '';
    }

    function shouldResume(v) {
        return window.MyTubeBgMode && !window.MyTubeUserPaused && !v.ended && v.currentTime > 0 && v.paused;
    }

    function forceResume(v) {
        if (shouldResume(v)) v.play().catch(function(){});
    }

    var lastReportKey = '';
    function reportState() {
        var v = document.querySelector('video');
        if (!v || !window.Android) return;
        var title = document.title.replace(/^(\(\d+\)\s+)?/, '').replace(' - YouTube', '');
        var artUrl = getArtUrl();
        var canNext = hasNext();
        var canPrev = hasPrev();
        var key = (!!v.paused) + '|' + title + '|' + (v.duration | 0) + '|' + Math.floor(v.currentTime / 10) +
            '|' + artUrl + '|' + canNext + '|' + canPrev;
        if (key === lastReportKey) return;
        lastReportKey = key;
        window.Android.onPlaybackStateChanged(!v.paused, title, v.duration || 0, v.currentTime || 0, artUrl, canNext, canPrev);
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
        var v = document.querySelector('video');
        if (v && v !== lastVideo) attachVideo(v);
        if (v) forceResume(v);
    }

    var v0 = document.querySelector('video');
    if (v0) attachVideo(v0);

    var videoObserver = new MutationObserver(function() {
        var v = document.querySelector('video');
        if (v && v !== lastVideo) attachVideo(v);
    });
    videoObserver.observe(document.body, { childList: true, subtree: true });

    setInterval(function() {
        var v = document.querySelector('video');
        if (!v) return;
        forceResume(v);
        reportState();
    }, 1500);
})();
