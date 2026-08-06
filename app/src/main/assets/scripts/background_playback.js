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
        var key = (!!v.paused) + '|' + title + '|' + (v.duration | 0) + '|' + Math.floor(v.currentTime / 10);
        if (key === lastReportKey) return;
        lastReportKey = key;
        window.Android.onPlaybackStateChanged(!v.paused, title, v.duration || 0, v.currentTime || 0);
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
