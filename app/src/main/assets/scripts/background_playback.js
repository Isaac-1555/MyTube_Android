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
        if (!window.MyTubeUserPaused && v.currentTime > 0 && v.paused) {
            v.play().catch(function(){});
        }
        reportState();
    };

    function reportState() {
        var v = document.querySelector('video');
        if (!v || !window.Android) return;
        var title = document.title.replace(/^(\(\d+\)\s+)?/, '').replace(' - YouTube', '');
        window.Android.onPlaybackStateChanged(!v.paused, title, v.duration || 0, v.currentTime || 0);
    }

    var videoObserver = new MutationObserver(function() {
        var v = document.querySelector('video');
        if (v && !v._bgEvents) {
            v._bgEvents = true;
            v.addEventListener('play', reportState);
            v.addEventListener('pause', reportState);
            reportState();
        }
    });
    videoObserver.observe(document.body, { childList: true, subtree: true });
    setInterval(reportState, 2000);
})();
