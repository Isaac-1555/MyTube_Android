(function() {
    if (window.MyTubeVideoStateInj) return;
    window.MyTubeVideoStateInj = true;

    var STYLE_ID = 'mytube-fs';

    // One style element for both fullscreen modes. They used to be separate <style>
    // blocks and both pinned .html5-video-player with !important, so whichever was
    // injected last silently won.
var CSS = [
        'html.mytube-fs-css, html.mytube-fs-css body { background:#000 !important; overflow:hidden !important; }',

        // position:fixed resolves against the nearest ancestor with a transform,
        // filter, contain or will-change. YouTube puts those on the player
        // container chain, which is why the pin "worked sometimes" before.
        'html.mytube-fs-css #content, html.mytube-fs-css #primary,',
        'html.mytube-fs-css #primary-inner, html.mytube-fs-css #player,',
        'html.mytube-fs-css #player-container-outer, html.mytube-fs-css #below,',
        'html.mytube-fs-css ytd-watch-flexy, html.mytube-fs-css ytd-player,',
        'html.mytube-fs-css ytd-player-legacy, html.mytube-fs-css ytd-watch-flexy[is-active] {',
        '  transform: none !important; filter: none !important; contain: none !important;',
        '  will-change: auto !important; perspective: none !important;',
        '  backdrop-filter: none !important; content-visibility: visible !important;',
        '}',

        // Hide the page chrome by hiding everything that is not the player, instead of
        // listing selectors for it. YouTube ships several different mastheads (desktop,
        // logged-out, new "supernav", yt- prefixed) and a selector list only ever
        // matches whichever one was current when it was written. Blanket hiding plus an
        // explicit re-show of the player subtree is also what YouTube's own fullscreen
        // does, and it keeps the player's controls working.
        'html.mytube-fs-css body * { visibility: hidden !important; }',
        'html.mytube-fs-css .html5-video-player,',
        'html.mytube-fs-css .html5-video-player * { visibility: visible !important; }',

        'html.mytube-fs-css .html5-video-player {',
        '  position: fixed !important; top: 0 !important; left: 0 !important; right: 0 !important;',
        '  bottom: 0 !important; width: 100vw !important; height: 100vh !important;',
        '  max-width: none !important; max-height: none !important;',
        '  margin: 0 !important; z-index: 2147483647 !important; background: #000 !important;',
        '}',

        // Nothing above targets the player's own chrome, so the seek bar, play button
        // and quality menu stay interactive. Deliberately no `display: revert` rule
        // here: at author origin `revert` resolves to the UA value (block), not
        // YouTube's own flex layout, which would break the control bar.

        'html.mytube-fs-pip, html.mytube-fs-pip body { background:#000 !important; overflow:hidden !important; }',
        'html.mytube-fs-pip .html5-video-player {',
        '  position: fixed !important; top: 0 !important; left: 0 !important;',
        '  width: 100vw !important; height: 100vh !important; max-height: none !important;',
        '  margin: 0 !important; z-index: 2147483647 !important; background: #000 !important;',
        '}'
    ].join('');

    function styleEl() {
        var s = document.getElementById(STYLE_ID);
        if (!s) {
            s = document.createElement('style');
            s.id = STYLE_ID;
            s.textContent = CSS;
            (document.head || document.documentElement).appendChild(s);
        }
        return s;
    }

    function toggleClass(cls, on) {
        var root = document.documentElement;
        if (!root) return;
        if (on) {
            styleEl();
            root.classList.add(cls);
        } else {
            root.classList.remove(cls);
        }
    }

    // ---- native (Fullscreen API) attempt chain --------------------------------
    // Chromium gates every entry below on transient user activation, so each one
    // can fail. The Kotlin side watches for onShowCustomView and falls back to the
    // CSS path on a timer; this just tries in order of likelihood.
    window.MyTubeTryNativeFullscreen = function() {
        var v = document.querySelector('video');
        var tries = [];

        if (v && typeof v.webkitEnterFullscreen === 'function') tries.push('video.webkitEnterFullscreen');
        tries.push('documentElement.requestFullscreen');

        if (v && typeof v.webkitEnterFullscreen === 'function') {
            try { v.webkitEnterFullscreen(); return reportTier('video.webkitEnterFullscreen'); } catch (e) {}
        }
        var root = document.documentElement;
        if (root && typeof root.requestFullscreen === 'function') {
            try {
                var p = root.requestFullscreen();
                if (p && p.catch) p.catch(function() {});
                return reportTier('documentElement.requestFullscreen');
            } catch (e) {}
        }
        var btn = document.querySelector('.fullscreen-icon.ytp-large-mode-button') ||
                  document.querySelector('.ytp-fullscreen-button');
        if (btn) {
            try { btn.click(); return reportTier('player-button-click'); } catch (e) {}
        }
        return reportTier('none');
    };

    function reportTier(tier) {
        try {
            if (window.Android && window.Android.onNativeFullscreenAttempt) {
                window.Android.onNativeFullscreenAttempt(tier);
            }
        } catch (e) {}
        return tier;
    }

    window.MyTubeExitNativeFullscreen = function() {
        try {
            if (document.fullscreenElement && document.exitFullscreen) {
                var p = document.exitFullscreen();
                if (p && p.catch) p.catch(function() {});
                return true;
            }
        } catch (e) {}
        var v = document.querySelector('video');
        try {
            if (v && typeof v.webkitExitFullscreen === 'function') { v.webkitExitFullscreen(); return true; }
        } catch (e) {}
        return false;
    };

    // ---- verification ---------------------------------------------------------
    // Did the pin actually take? Reported back so device logs carry hard data
    // instead of guesses. Called on apply and again after the page settles, because
    // a late relayout can undo the pin.
    window.MyTubeFsApplied = function() {
        var p = document.querySelector('.html5-video-player');
        if (!p) return false;
        var r = p.getBoundingClientRect();
        if (!r || r.width < 1 || r.height < 1) return false;
        var covered = Math.min(r.width, window.innerWidth) * Math.min(r.height, window.innerHeight);
        return covered / (window.innerWidth * window.innerHeight) > 0.9;
    };

    // Names whatever is painted on top of the player, at points where chrome lives.
    // Uses elementsFromPoint instead of a selector list: a selector list can only
    // match markup we already know about, and YouTube's header is not stable.
    window.MyTubeFsChromeAudit = function() {
        var px = [[0.03, 0.03], [0.15, 0.03], [0.5, 0.025], [0.85, 0.03], [0.95, 0.03], [0.985, 0.03]];
        var seen = {};
        var out = [];
        for (var i = 0; i < px.length; i++) {
            var stack = document.elementsFromPoint(
                Math.round(window.innerWidth * px[i][0]),
                Math.round(window.innerHeight * px[i][1])
            );
            for (var j = 0; j < stack.length && j < 5; j++) {
                var el = stack[j];
                if (!el || el === document.body || el === document.documentElement) continue;
                if (el.closest && el.closest('.html5-video-player')) continue;
                var cn = (typeof el.className === 'string' ? el.className : '')
                    .split(/\s+/).filter(function(c) { return c && c.indexOf('yt') === 0; })[0] || '';
                var key = el.tagName.toLowerCase() + (el.id ? '#' + el.id : '') + (cn ? '.' + cn : '');
                if (seen[key]) continue;
                seen[key] = 1;
                var cs = getComputedStyle(el);
                out.push(key + '{z:' + cs.zIndex + ',pos:' + cs.position +
                    ',vis:' + cs.visibility + ',bg:' + cs.backgroundColor + '}');
            }
        }
        return out.length ? out.join(' ') : 'clean';
    };

    function verify(tag) {
        if (!window.MyTubeFsCssOn) return;
        if (!window.Android || !window.Android.onFullscreenCssApplied) return;
        try {
            window.Android.onFullscreenCssApplied(window.MyTubeFsApplied());
            window.Android.onFullscreenCssAudit(window.MyTubeFsChromeAudit());
        } catch (e) {}
    }

    window.MyTubeSetFsCss = function(on) {
        on = !!on;
        // The stylesheet hides everything that is not the player, so if the player is
        // not in the DOM yet this would paint the page black. Wait for it instead.
        if (on && !document.querySelector('.html5-video-player')) {
            Logless('defer: no .html5-video-player yet');
            return;
        }
        if (window.MyTubeFsCssOn === on) {
            if (on) reassert();
            return;
        }
        window.MyTubeFsCssOn = on;
        toggleClass('mytube-fs-css', on);
        if (on) {
            reassert();
            verify('immediate');
            // YouTube re-lays-out for a while after a rotation, so verify late too.
            setTimeout(function() { if (window.MyTubeFsCssOn) { reassert(); verify('300ms'); } }, 300);
            setTimeout(function() { if (window.MyTubeFsCssOn) { reassert(); verify('900ms'); } }, 900);
        } else {
            verify('off');
        }
    };

    window.MyTubeSetPipMode = function(on) { toggleClass('mytube-fs-pip', !!on); };

    function Logless(msg) {
        try {
            if (window.Android && window.Android.onFullscreenCssAudit) {
                window.Android.onFullscreenCssAudit(msg);
            }
        } catch (e) {}
    }

    function reassert() {
        if (!window.MyTubeFsCssOn) return;
        var root = document.documentElement;
        if (root && !root.classList.contains('mytube-fs-css')) {
            styleEl();
            root.classList.add('mytube-fs-css');
        }
    }

    // YouTube re-renders the player constantly (quality changes, ad hand-off,
    // navigation). A one-shot class add gets wiped; keep it pinned while active.
    function startObserver() {
        if (window.MyTubeFsObserver) return;
        window.MyTubeFsObserver = new MutationObserver(function() { reassert(); });
        window.MyTubeFsObserver.observe(document.documentElement, {
            childList: true, subtree: true
        });
    }
    if (document.documentElement) startObserver();

    // ---- video state reporting ------------------------------------------------
    function isWatchPage() {
        if (!/(^|\.)youtube\.com$/.test(location.hostname)) return false;
        var p = location.pathname;
        if (p === '/watch') return true;
        if (/^\/(shorts|embed|live)\//.test(p)) return true;
        return false;
    }

    var lastKey = '';
    function report(force) {
        if (!window.Android || !window.Android.onVideoStateChanged) return;
        var v = document.querySelector('video');
        var watch = isWatchPage();
        var has = !!v;
        // Deliberately not gated on currentTime > 0. During a rotation the page can
        // briefly swap the video element, and a fresh element sits at 0 for a frame;
        // counting that as "not playing" dropped fullscreen at exactly the wrong
        // moment. A video that has not started is already paused.
        var playing = has && !v.paused && !v.ended;
        var key = has + '|' + watch + '|' + playing + '|' + location.pathname;
        if (!force && key === lastKey) return;
        lastKey = key;
        try {
            window.Android.onVideoStateChanged(has, watch, playing);
        } catch (e) {}
        if (window.MyTubeFsCssOn) verify('report');
    }

    window.MyTubeReportVideoState = function() { report(true); };

    function bindVideo(v) {
        ['play', 'playing', 'pause', 'ended', 'seeked', 'emptied'].forEach(function(t) {
            v.addEventListener(t, function() { report(true); });
        });
    }

    var boundVideo = null;
    function track() {
        var v = document.querySelector('video');
        if (v && v !== boundVideo) {
            boundVideo = v;
            bindVideo(v);
        }
        return v;
    }

    var lastPath = location.pathname;
    function onRouteChange() {
        if (location.pathname !== lastPath) {
            lastPath = location.pathname;
            report(true);
        }
    }

    function install() {
        track();
        report(true);
        var root = document.body || document.documentElement;
        if (!root || window.MyTubeStateObserver) return;
        window.MyTubeStateObserver = new MutationObserver(function() {
            track();
            onRouteChange();
        });
        window.MyTubeStateObserver.observe(root, { childList: true, subtree: true });
        window.addEventListener('yt-navigate-finish', function() { report(true); });
        window.addEventListener('popstate', function() { report(true); });
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', install);
    } else {
        install();
    }

    // Safety net: if install never ran (body never appeared) retry briefly.
    var tries = 0;
    var iv = setInterval(function() {
        if (window.MyTubeStateObserver || ++tries > 40) { clearInterval(iv); return; }
        install();
    }, 250);
})();