(function() {
    if (window.MyTubeVideoStateInj) return;
    window.MyTubeVideoStateInj = true;

    var STYLE_ID = 'mytube-fs';

    // The page chrome is hidden by JS, by display:none on everything outside the
        // player chain. See hideOutsidePlayer.
        //
        // It used to be `body * { visibility: hidden }` plus a re-show of the player
        // subtree. That cannot work here: YouTube fades its own control bar by setting
        // visibility:hidden on .player-controls-content/-top/-bottom, so any blanket
        // `visibility: visible !important` over those elements beats it and the
        // controls never fade again. display:none on the siblings leaves YouTube's
        // visibility transitions alone.
        //
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

        // The player chain has to span the viewport for the overlay below to land in
        // the right place, and with the rest of the page display:none it no longer has
        // any flow content giving it height.
        'html.mytube-fs-css ytd-watch-flexy, html.mytube-fs-css ytd-player,',
        'html.mytube-fs-css #player-container-outer, html.mytube-fs-css #player-container-inner,',
        'html.mytube-fs-css #player-container {',
        '  width: 100% !important; height: 100vh !important; min-height: 0 !important;',
        '}',

        'html.mytube-fs-css .html5-video-player {',
        '  position: fixed !important; top: 0 !important; left: 0 !important; right: 0 !important;',
        '  bottom: 0 !important; width: 100vw !important; height: 100vh !important;',
        '  max-width: none !important; max-height: none !important;',
        '  margin: 0 !important; background: #000 !important;',
        // One below the control overlay below. #movie_player owns the video surface
        // and a full-bleed .ytp-player-content inside it, so at an equal or higher
        // z-index it paints over the controls and eats every tap meant for them.
        '  z-index: 2147483646 !important;',
        '}',

        // The mobile-web player draws its real control bar - seek bar, play button,
        // fullscreen button - in #player-control-overlay, a sibling of #movie_player.
        // z-index only: no visibility, so YouTube's own show/hide of the controls is
        // left alone.
        'html.mytube-fs-css #player-control-overlay { z-index: 2147483647 !important; }',

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


    // Located by label rather than by class: the desktop player's
    // .ytp-fullscreen-button does not exist on the mobile-web player, which ships
    // an unprefixed "icon-button fullscreen-icon" instead. Any attempt to name it
    // by class has matched nothing.
    function findFsButtons() {
        return [].slice.call(document.querySelectorAll(
            '[aria-label*="full screen" i],[aria-label*="fullscreen" i],' +
            '[title*="full screen" i],[title*="fullscreen" i]'
        ));
    }

    /**
     * Turns the next trusted touch into YouTube entering its own fullscreen.
     *
     * A tap dispatched from the app reaches Blink's input pipeline intact - the
     * renderer reports isTrusted:true - so it grants the transient user activation
     * that requestFullscreen() demands and which no evaluateJavascript call can
     * produce. Activation is a frame-level state that outlives the event that set
     * it, so dispatching the click from inside the touch handler still clears the
     * gate.
     *
     * The click is dispatched straight at the button rather than left to hit
     * testing, because #movie_player is pinned over #player-control-overlay and
     * would otherwise swallow it. One-shot, and it suppresses the native click so
     * the button is not activated twice.
     */
    window.MyTubeFsArmPromote = function() {
        if (document.fullscreenElement) return 'already-fullscreen';
        var fired = false;
        function onTrusted(e) {
            if (fired) return;
            fired = true;
            window.removeEventListener('touchend', onTrusted, true);
            window.removeEventListener('click', onTrusted, true);
            // The button's own listener would otherwise run after ours and enter
            // fullscreen a second time.
            e.stopPropagation();
            e.preventDefault();
            if (document.fullscreenElement) return;
            var btns = findFsButtons();
            var btn = null;
            for (var i = 0; i < btns.length && !btn; i++) {
                var r = btns[i].getBoundingClientRect();
                if (r.width > 8 && r.height > 8) btn = btns[i];
            }
            try {
                if (btn) {
                    btn.click();
                } else {
                    document.documentElement.requestFullscreen();
                }
            } catch (err) {
                try { document.documentElement.requestFullscreen(); } catch (e2) {}
            }
        }
        window.addEventListener('touchend', onTrusted, true);
        window.addEventListener('click', onTrusted, true);
        return 'armed';
    };

    /**
     * Reports where YouTube's own fullscreen button is, in CSS pixels relative to the
     * viewport, so the Kotlin side can tap it. Returns an object literal because
     * evaluateJavascript hands the JSON text straight back.
     *
     * This is the whole auto-promote mechanism: requestFullscreen() refuses to run
     * without transient user activation, and only a touch through the real input
     * pipeline grants that. Reading the button's rect is what lets the app perform
     * that touch itself, which makes YouTube run its genuine fullscreen path and
     * therefore its genuine controls, seek bar and double-tap seeking. Nothing we
     * can inject produces those.
     */
    window.MyTubeFsButtonRect = function() {
        // YouTube autohides the controls, and a hidden control is not hit-testable,
        // so a tap at those coordinates falls through to the page and toggles playback
        // instead. YouTube restores the hidden state on the next interaction, which is
        // well before anyone could notice.
        var host = document.querySelector('#movie_player') ||
                   document.querySelector('.html5-video-player');
        if (host) {
            if (host.classList.contains('ytp-autohide')) window.MyTubeFsUnhidAutohide = true;
            host.classList.remove('ytp-autohide');
        }

        // Located by label rather than by class: the desktop player's
        // .ytp-fullscreen-button does not exist on the mobile-web player, which ships
        // an unprefixed "icon-button fullscreen-icon" instead.
        var nodes = findFsButtons();
        var out = [];
        var pick = null;
        for (var i = 0; i < nodes.length; i++) {
            var el = nodes[i];
            var r = el.getBoundingClientRect();
            if (r.width < 8 || r.height < 8) continue;
            var rec = {
                cls: typeof el.className === 'string' ? el.className : '',
                label: el.getAttribute('aria-label') || el.getAttribute('title') || '',
                x: r.left + r.width / 2,
                y: r.top + r.height / 2,
                w: Math.round(r.width),
                h: Math.round(r.height),
                blocked: blocker(el)
            };
            out.push(rec);
            if (!pick) pick = el;
        }
        if (!out.length) {
            return { found: false, reason: 'no fullscreen-labelled element', dump: dumpPlayer() };
        }

        // The one candidate is laid out but visibility:hidden while playing. Force
        // the chain visible, then re-measure, because the tap has to land on the
        // button rather than falling through to the video underneath it.
        forceHitTestable(pick);
        var pr = pick.getBoundingClientRect();
        var pc = getComputedStyle(pick);
        var cx = pr.left + pr.width / 2;
        var cy = pr.top + pr.height / 2;
        return {
            found: pr.width > 1 && pr.height > 1 && !blocker(pick),
            x: cx,
            y: cy,
            w: Math.round(pr.width),
            h: Math.round(pr.height),
            cls: typeof pick.className === 'string' ? pick.className : '',
            label: pick.getAttribute('aria-label') || pick.getAttribute('title') || '',
            vis: pc.visibility, disp: pc.display, pe: pc.pointerEvents,
            blocked: blocker(pick),
            // Not getScale(): with useWideViewPort the layout viewport is chosen so
            // that CSS px map to view px by devicePixelRatio, which is 2.625 on a
            // 1080p panel. getScale() reports page zoom and reads 1.0.
            dpr: window.devicePixelRatio,
            vw: window.innerWidth,
            vh: window.innerHeight,
            // What actually paints over the button, topmost first. Whether the button
            // appears here at all decides if a tap can reach it.
            stack: hitStack(cx, cy, pick),
            dump: dumpPlayer()
        };
    };

    function hitStack(x, y, target) {
        var stack = document.elementsFromPoint(Math.round(x), Math.round(y)) || [];
        var out = [];
        for (var i = 0; i < stack.length && i < 4; i++) {
            var e = stack[i];
            out.push(
                (e.id ? '#' + e.id : e.tagName.toLowerCase()) +
                '.' + (typeof e.className === 'string' ? e.className.split(/\s+/).join('.') : '') +
                (e === target ? ' <-TARGET' : '')
            );
        }
        return out.join(' | ');
    }

    /**
     * Makes [el] reachable by a real touch, remembering what it changed so
     * [restoreHitTestable] can put YouTube's own state back.
     */
    function forceHitTestable(el) {
        restoreHitTestable();
        window.MyTubeFsForced = [];
        var p = el;
        while (p && p !== document.documentElement) {
            var s = getComputedStyle(p);
            if (s.visibility === 'hidden') {
                window.MyTubeFsForced.push([p, 'visibility', p.style.getPropertyValue('visibility'), p.style.getPropertyPriority('visibility')]);
                p.style.setProperty('visibility', 'visible', 'important');
            }
            if (s.pointerEvents === 'none') {
                window.MyTubeFsForced.push([p, 'pointer-events', p.style.getPropertyValue('pointer-events'), p.style.getPropertyPriority('pointer-events')]);
                p.style.setProperty('pointer-events', 'auto', 'important');
            }
            p = p.parentElement;
        }
    }

    /**
     * What the player's control surface actually looks like right now. Written
     * because the mobile-web chrome is reached through four different class name
     * schemes depending on the build, and every attempt to guess at them has been
     * wrong.
     */
    window.MyTubeFsDumpChrome = function() {
        var out = [];
        function row(sel) {
            var e = document.querySelector(sel);
            if (!e) { out.push(sel + '=MISSING'); return; }
            var cs = getComputedStyle(e);
            var r = e.getBoundingClientRect();
            out.push(sel + '={cls:' + (typeof e.className === 'string' ? e.className : '') +
                ',disp:' + cs.display + ',op:' + cs.opacity + ',vis:' + cs.visibility +
                ',pe:' + cs.pointerEvents + ',z:' + cs.zIndex +
                ',rect:' + Math.round(r.left) + ',' + Math.round(r.top) + ',' +
                Math.round(r.width) + 'x' + Math.round(r.height) + '}');
        }
        ['#movie_player', '#player-control-overlay', '.player-controls-content',
         '.player-controls-top', '.player-controls-bottom', '.player-controls-bottom-left',
         '.player-controls-bottom-right', '.ytp-large-mode-buttons',
         '.ytp-chrome-bottom', '.ytp-chrome-top', '.ytp-progress-bar'
        ].forEach(row);
        // Whatever YouTube happens to be calling its seek bar and lock button this week.
        var probe = ['seek', 'scrub', 'progress', 'lock', 'timeline'];
        var found = {};
        var all = document.querySelectorAll('#player-control-overlay *, #movie_player *');
        for (var i = 0; i < all.length && i < 4000; i++) {
            var cn = typeof all[i].className === 'string' ? all[i].className : '';
            probe.forEach(function(k) {
                if (cn.toLowerCase().indexOf(k) !== -1 && !found[k + ':' + cn]) {
                    found[k + ':' + cn] = 1;
                    var cs2 = getComputedStyle(all[i]);
                    var r2 = all[i].getBoundingClientRect();
                    out.push('?' + k + ' {' + cn + '} disp:' + cs2.display + ' op:' +
                        cs2.opacity + ' vis:' + cs2.visibility + ' pe:' + cs2.pointerEvents +
                        ' rect:' + Math.round(r2.left) + ',' + Math.round(r2.top) + ',' +
                        Math.round(r2.width) + 'x' + Math.round(r2.height) + '}');
                }
            });
        }
        return out.join('\n');
    };

    window.MyTubeFsRestoreChrome = function() {
        restoreHitTestable();
        var host = document.querySelector('#movie_player') ||
                   document.querySelector('.html5-video-player');
        // Only re-hide if we were the ones who un-hid it. YouTube may have engaged
        // its own fullscreen chrome in the meantime, which must stay visible.
        if (host && window.MyTubeFsUnhidAutohide) host.classList.add('ytp-autohide');
        window.MyTubeFsUnhidAutohide = false;
    };

    /**
     * Records what the page receives from a touch, so a tap that silently fails can
     * be told apart from one the page ignored. isTrusted is the one that matters:
     * it is what decides whether Chromium treats the tap as user activation.
     *
     * Diagnostic only, installed on demand, never on the tap path itself - logging
     * every touch of a YouTube session is noise and not free.
     */
    window.MyTubeFsTapProbe = function() {
        if (window.MyTubeFsProbed) return true;
        window.MyTubeFsProbed = true;
        ['touchstart', 'touchend', 'click', 'pointerdown'].forEach(function(t) {
            window.addEventListener(t, function(e) {
                var el = e.target;
                var rec = {
                    type: t, trusted: e.isTrusted,
                    x: Math.round(e.clientX || 0), y: Math.round(e.clientY || 0),
                    target: el ? ((el.id ? '#' + el.id : '') +
                        (typeof el.className === 'string' && el.className
                            ? '.' + el.className.split(/\s+/).slice(0, 2).join('.') : '')) : 'null'
                };
                try {
                    if (window.Android && window.Android.onFullscreenCssAudit) {
                        window.Android.onFullscreenCssAudit('PROBE ' + JSON.stringify(rec));
                    }
                } catch (err) {}
            }, true);
        });
        return true;
    };

    function restoreHitTestable() {
        var l = window.MyTubeFsForced || [];
        for (var i = 0; i < l.length; i++) {
            var e = l[i];
            if (e[3]) e[0].style.setProperty(e[1], e[2], e[3]);
            else e[0].style[e[1]] = e[2];
        }
        window.MyTubeFsForced = [];
    }

    /** First ancestor that would stop the tap reaching [el]. */
    function blocker(el) {
        var p = el;
        while (p && p !== document.documentElement) {
            var s = getComputedStyle(p);
            if (s.visibility === 'hidden' || s.display === 'none' || s.pointerEvents === 'none') {
                var id = p.id ? '#' + p.id
                    : (typeof p.className === 'string' && p.className
                        ? '.' + p.className.split(/\s+/)[0]
                        : p.tagName.toLowerCase());
                return id + ' ' + s.visibility + '/' + s.display + '/' + s.pointerEvents;
            }
            p = p.parentElement;
        }
        return '';
    }

    /**
     * What the player actually contains. Diagnostic only, but guessing at YouTube's
     * control markup is how the previous selector list ended up matching nothing.
     */
    function dumpPlayer() {
        var host = document.querySelector('#movie_player') ||
                   document.querySelector('.html5-video-player');
        if (!host) return 'no #movie_player';
        var seen = {};
        var all = host.querySelectorAll('*');
        for (var i = 0; i < all.length && i < 3000; i++) {
            var cn = typeof all[i].className === 'string' ? all[i].className : '';
            cn.split(/\s+/).forEach(function(c) { if (c.indexOf('ytp-') === 0) seen[c] = 1; });
        }
        var bars = ['.ytp-chrome-bottom', '.ytp-chrome-top', '.ytp-left-controls',
                    '.ytp-right-controls', '.ytp-buttons'];
        var barDump = bars.map(function(sel) {
            var el = host.querySelector(sel);
            if (!el) return sel + ':-';
            var kids = [].map.call(el.children, function(c) {
                return (typeof c.className === 'string' ? c.className : c.tagName).split(/\s+/)[0];
            });
            return sel + ':' + kids.join(',');
        }).join(' ');
        return Object.keys(seen).join(' ') + ' || ' + barDump;
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

    /** Shortest gap between two audits. See [verify] for why there is one. */
    var VERIFY_MIN_INTERVAL_MS = 2000;

    function verify(tag, force) {
        if (!window.MyTubeFsCssOn) return;
        if (!window.Android || !window.Android.onFullscreenCssApplied) return;
        // The audit probes six points with elementsFromPoint and reads computed styles
        // off everything stacked there. Doing that on every play/pause/seek report -
        // which is every few seconds while watching - is real work for a diagnostic,
        // so it is rate limited. The entry-time re-checks pass force because catching
        // a late relayout is the one thing the audit exists for.
        var now = Date.now();
        if (!force && now - (window.MyTubeFsLastVerify || 0) < VERIFY_MIN_INTERVAL_MS) return;
        window.MyTubeFsLastVerify = now;
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
            verify('immediate', true);
            // YouTube re-lays-out for a while after a rotation, so verify late too.
            setTimeout(function() { if (window.MyTubeFsCssOn) { reassert(); verify('300ms', true); } }, 300);
            setTimeout(function() { if (window.MyTubeFsCssOn) { reassert(); verify('900ms', true); } }, 900);
        } else {
            restoreHiddenOutside();
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
        hideOutsidePlayer();
    }

    /**
     * Hides everything that is not the player, by display:none on the siblings of the
     * player chain.
     *
     * Hiding by position in the tree rather than by a list of selectors is what makes
     * this survive YouTube redesigns: it does not care what the masthead is called, or
     * how many of them there are. display:none rather than visibility:hidden because
     * YouTube fades its own control bar with visibility, and a blanket
     * `visibility: visible !important` over the player subtree defeats that outright -
     * which is exactly how the controls ended up never fading.
     */
    function hideOutsidePlayer() {
        restoreHiddenOutside();
        var roots = [];
        ['#player-control-overlay', '#movie_player'].forEach(function(sel) {
            var e = document.querySelector(sel);
            if (e) roots.push(e);
        });
        if (!roots.length || !document.body) return;

        var node = lowestCommonAncestor(roots);
        while (node && node !== document.body && node.parentElement) {
            var kids = node.children;
            for (var i = 0; i < kids.length; i++) {
                var child = kids[i];
                if (isOnChain(child, roots)) continue;
                window.MyTubeFsHidden.push([
                    child,
                    child.style.getPropertyValue('display'),
                    child.style.getPropertyPriority('display')
                ]);
                child.style.setProperty('display', 'none', 'important');
            }
            node = node.parentElement;
        }
    }

    function isOnChain(el, roots) {
        for (var i = 0; i < roots.length; i++) {
            if (el === roots[i] || el.contains(roots[i])) return true;
        }
        return false;
    }

    function lowestCommonAncestor(nodes) {
        var lca = nodes[0];
        for (var i = 1; i < nodes.length; i++) {
            var a = lca, b = nodes[i];
            while (a && a !== b) {
                if (!a.contains(b)) a = a.parentElement;
                else b = b.parentElement;
            }
            if (a) lca = a;
        }
        return lca;
    }

    function restoreHiddenOutside() {
        var list = window.MyTubeFsHidden || [];
        for (var i = 0; i < list.length; i++) {
            if (list[i][2]) list[i][0].style.setProperty('display', list[i][1], list[i][2]);
            else list[i][0].style.display = list[i][1];
        }
        window.MyTubeFsHidden = [];
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
        // playing is deliberately kept out of the payload and only used to key the
        // dedupe. It must not reach Kotlin: a pause that reports as "no longer
        // watchable" makes the resolver tear down fullscreen, which is exactly what
        // happened when tapping to pause while watching.
        var key = has + '|' + watch + '|' + playing + '|' + location.pathname;
        if (!force && key === lastKey) return;
        lastKey = key;
        try {
            window.Android.onVideoStateChanged(has, watch);
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