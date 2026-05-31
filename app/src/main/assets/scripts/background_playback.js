(function() {
    if (window.MyTubeBgInj) return;
    window.MyTubeBgInj = true;

    Object.defineProperty(document, 'hidden', { get: () => false });
    Object.defineProperty(document, 'visibilityState', { get: () => 'visible' });
    Object.defineProperty(document, 'webkitHidden', { get: () => false });
    Object.defineProperty(document, 'webkitVisibilityState', { get: () => 'visible' });
    Object.defineProperty(document, 'hasFocus', { get: () => true });
    
    const stopProp = (e) => { e.stopImmediatePropagation(); };
    window.addEventListener('visibilitychange', stopProp, true);
    document.addEventListener('visibilitychange', stopProp, true);
    window.addEventListener('webkitvisibilitychange', stopProp, true);
    document.addEventListener('webkitvisibilitychange', stopProp, true);
    window.addEventListener('blur', function(e) {
        e.stopImmediatePropagation();
        e.preventDefault();
    }, true);
    window.addEventListener('focus', stopProp, true);

    window.requestAnimationFrame = function(cb) {
        return setTimeout(function() { cb(Date.now()); }, 16);
    };
    window.webkitRequestAnimationFrame = window.requestAnimationFrame;

    window.MyTubeUserPaused = false;

    window.MyTubePause = function() {
        window.MyTubeUserPaused = true;
        const v = document.querySelector('video');
        if (v) v.pause();
    };
    window.MyTubePlay = function() {
        window.MyTubeUserPaused = false;
        const v = document.querySelector('video');
        if (v) v.play();
    };

    var origMediaSource = window.MediaSource;
    var _blobToSource = {};
    if (origMediaSource) {
        var origCreateObjectURL = URL.createObjectURL;
        URL.createObjectURL = function(obj) {
            var url = origCreateObjectURL.call(this, obj);
            if (obj instanceof origMediaSource) _blobToSource[url] = obj;
            return url;
        };

        var origAddSb = origMediaSource.prototype.addSourceBuffer;
        origMediaSource.prototype.addSourceBuffer = function(mimeType) {
            var sb = origAddSb.call(this, mimeType);
            if (!this.__sbs) this.__sbs = [];
            this.__sbs.push(sb);
            return sb;
        };
    }

    function unstickMediaPipeline() {
        var v = document.querySelector('video');
        if (!v || v.paused || v.readyState >= 3) return;
        if (!v.currentTime) return;

        var ms = v.src && _blobToSource[v.src];
        if (ms && ms.readyState === 'open' && ms.__sbs) {
            for (var i = 0; i < ms.__sbs.length; i++) {
                if (ms.__sbs[i].updating) {
                    try { ms.__sbs[i].abort(); } catch(e) {}
                }
            }
        }

        var yt = document.getElementById('movie_player');
        if (yt && yt.playVideo) {
            try { yt.playVideo(); } catch(e) {}
        }

        try { v.dispatchEvent(new Event('playing')); } catch(e) {}
    }

    window.MyTubeBgTick = function() {
        const v = document.querySelector('video');
        if (!v) return;
        if (!window.MyTubeUserPaused && v.currentTime > 0) {
            v.play().catch(function(){});
        }
        unstickMediaPipeline();
        reportState();
    };

    function reportState() {
        const v = document.querySelector('video');
        if (!v || !window.Android) return;
        let title = document.title.replace(/^(\(\d+\)\s+)?/, '').replace(' - YouTube', '');
        window.Android.onPlaybackStateChanged(!v.paused, title, v.duration || 0, v.currentTime || 0);
    }

    const videoObserver = new MutationObserver(function() {
        const v = document.querySelector('video');
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
