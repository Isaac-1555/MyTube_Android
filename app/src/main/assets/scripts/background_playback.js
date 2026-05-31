(function() {
    if (window.MyTubeBgInj) return;
    window.MyTubeBgInj = true;

    Object.defineProperty(document, 'hidden', { get: () => false });
    Object.defineProperty(document, 'visibilityState', { get: () => 'visible' });
    Object.defineProperty(document, 'webkitHidden', { get: () => false });
    Object.defineProperty(document, 'webkitVisibilityState', { get: () => 'visible' });
    
    const stopProp = (e) => { e.stopImmediatePropagation(); };
    window.addEventListener('visibilitychange', stopProp, true);
    document.addEventListener('visibilitychange', stopProp, true);
    window.addEventListener('webkitvisibilitychange', stopProp, true);
    document.addEventListener('webkitvisibilitychange', stopProp, true);

    function reportState() {
        const v = document.querySelector('video');
        if (!v || !window.Android) return;
        let title = document.title.replace(/^(\(\d+\)\s+)?/, '').replace(' - YouTube', '');
        window.Android.onPlaybackStateChanged(!v.paused, title, v.duration || 0, v.currentTime || 0);
    }

    window.MyTubePause = function() {
        const v = document.querySelector('video');
        if (v) v.pause();
    };
    window.MyTubePlay = function() {
        const v = document.querySelector('video');
        if (v) v.play();
    };
    window.MyTubeBgTick = function() {
        const v = document.querySelector('video');
        if (!v) return;
        if (!v.paused && v.currentTime > 0) {
            v.play().catch(function(){});
        }
        reportState();
    };

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
