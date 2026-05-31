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

    window.MyTubePause = function() {
        const v = document.querySelector('video');
        if (v) v.pause();
    };
    window.MyTubePlay = function() {
        const v = document.querySelector('video');
        if (v) v.play();
    };

    setInterval(() => {
        const v = document.querySelector('video');
        if (!v) return;
        
        if (window.Android && window.Android.onPlaybackStateChanged) {
            let title = document.title.replace(/^(\(\d+\)\s+)?/, '').replace(' - YouTube', '');
            window.Android.onPlaybackStateChanged(
                !v.paused, 
                title, 
                v.duration || 0.0, 
                v.currentTime || 0.0
            );
        }
    }, 1000);
})();
