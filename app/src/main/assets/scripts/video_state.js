(function() {
    if (window.MyTubeVideoStateInj) return;
    window.MyTubeVideoStateInj = true;

    var STYLE_ID = 'mytube-fs';

    // Marks the container pinned to the viewport, the ancestors that must not
    // create a containing block, the boxes between the container and the video,
    // and the chrome that has to stay on top.
    var CONTAINER_ATTR = 'data-mytube-container';
    var ANC_ATTR = 'data-mytube-anc';
    var FILL_ATTR = 'data-mytube-fill';
    var KEEP_ATTR = 'data-mytube-keep';
    var RAISE_ATTR = 'data-mytube-raise';
    var CHROME_OUT_ATTR = 'data-mytube-chrome-out';

    // The elements the sweep has hidden, with the inline display they had before.
    window.MyTubeFsHidden = [];

    /**
     * The player chrome, by every name it has shipped under.
     *
     * The mobile web player used to draw its control bar in #player-control-overlay,
     * a sibling of #movie_player. The current one draws it in #player-control-container
     * and in custom elements that live under the app tree, outside the player
     * container entirely, with their content inside a shadow root. A list that only
     * knows the first of those matches nothing, and the hide-the-page sweep then
     * takes the control bar - and the quality sheet inside it - with it.
     */
    var CHROME_SELECTORS = [
        '#player-control-overlay',
        '#player-control-container',
        'ytm-watch-player-controls',
        'ytm-custom-control',
        'player-top-controls',
        'player-bottom-controls',
        'player-middle-controls',
        'player-fullscreen-controls',
        '.player-controls',
        '.ytp-chrome-top',
        '.ytp-chrome-bottom'
    ];

    // Overlays YouTube opens on top of the player. They are kept out of the sweep
    // and lifted above the pinned player, because the sweep is what used to
    // swallow the quality sheet.
    var OVERLAY_SELECTORS = [
        '[role="dialog"]',
        '[role="menu"]',
        '[role="listbox"]',
        'bottom-sheet-container',
        '.ytp-settings-menu',
        '.player-settings-overlay',
        '.ytp-popup',
        'tp-yt-paper-dialog'
    ];

    // ---------------------------------------------------------------- styles ---

    var CSS = [
        'html.mytube-fs-css, html.mytube-fs-css body { background:#000 !important; overflow:hidden !important; }',
        'html.mytube-fs-pip, html.mytube-fs-pip body { background:#000 !important; overflow:hidden !important; }',

        // The container holds the video AND the chrome, so pinning it moves both
        // together. Pinning the video alone left the control bar where it was,
        // which is what made the picture miss the screen after a rotation.
        'html.mytube-fs-css [data-mytube-container], html.mytube-fs-pip [data-mytube-container] {',
        '  position: fixed !important; top: 0 !important; left: 0 !important;',
        '  right: 0 !important; bottom: 0 !important;',
        '  width: 100vw !important; height: 100vh !important;',
        '  max-width: none !important; max-height: none !important;',
        '  margin: 0 !important; padding: 0 !important; border: 0 !important;',
        '  background: #000 !important; overflow: hidden !important;',
        '  z-index: 2147483645 !important;',
        '  transform: none !important; filter: none !important; contain: none !important;',
        '  will-change: auto !important; perspective: none !important;',
        '  backdrop-filter: none !important;',
        '}',

        // position:fixed resolves against the nearest ancestor with a transform,
        // filter, contain or will-change. YouTube puts those on the player
        // container chain, which is why the pin "worked sometimes" before.
        'html.mytube-fs-css [data-mytube-anc], html.mytube-fs-pip [data-mytube-anc] {',
        '  transform: none !important; filter: none !important; contain: none !important;',
        '  will-change: auto !important; perspective: none !important;',
        '  backdrop-filter: none !important; content-visibility: visible !important;',
        '}',

        // The boxes between the container and the video must not be positioned,
        // or the video would be laid out against the wrong one and end up
        // smaller than the screen - or offset out of it.
        'html.mytube-fs-css [data-mytube-fill], html.mytube-fs-pip [data-mytube-fill] {',
        '  position: static !important; width: 100% !important; height: 100% !important;',
        '  max-width: none !important; max-height: none !important;',
        '  margin: 0 !important; padding: 0 !important;',
        '}',

        // The video fills the container.
        'html.mytube-fs-css [data-mytube-container] .html5-video-player,',
        'html.mytube-fs-pip [data-mytube-container] .html5-video-player {',
        '  position: absolute !important; top: 0 !important; left: 0 !important;',
        '  right: 0 !important; bottom: 0 !important;',
        '  width: 100% !important; height: 100% !important;',
        '  max-width: none !important; max-height: none !important;',
        '  margin: 0 !important; background: #000 !important; z-index: auto !important;',
        '}',
        'html.mytube-fs-css [data-mytube-container] .html5-video-container,',
        'html.mytube-fs-pip [data-mytube-container] .html5-video-container {',
        '  position: absolute !important; top: 0 !important; left: 0 !important;',
        '  right: 0 !important; bottom: 0 !important;',
        '  width: 100% !important; height: 100% !important;',
        '}',
        // YouTube offsets the video inside its box - left: 85px on a 400px wide
        // player. The offsets have to go, or the picture is cut off on one side
        // and does not fill the screen.
        'html.mytube-fs-css [data-mytube-container] video,',
        'html.mytube-fs-pip [data-mytube-container] video {',
        '  position: absolute !important; top: 0 !important; left: 0 !important;',
        '  right: 0 !important; bottom: 0 !important;',
        '  width: 100% !important; height: 100% !important; object-fit: contain !important;',
        '}',

        // Chrome sits above the video. It has to be positioned for the z-index
        // to mean anything, and YouTube draws it unpositioned.
        'html.mytube-fs-css [data-mytube-raise], html.mytube-fs-pip [data-mytube-raise] {',
        '  position: relative !important; z-index: 2147483647 !important;',
        '}',
        'html.mytube-fs-css [data-mytube-keep], html.mytube-fs-pip [data-mytube-keep] {',
        '  z-index: 2147483647 !important;',
        '}',
        // Chrome that lives outside the container is pinned over the video, so it
        // must not swallow the taps meant for it.
        'html.mytube-fs-css [data-mytube-chrome-out], html.mytube-fs-pip [data-mytube-chrome-out] {',
        '  position: fixed !important; top: 0 !important; left: 0 !important;',
        '  right: 0 !important; bottom: 0 !important;',
        '  width: 100vw !important; height: 100vh !important;',
        '  pointer-events: none !important;',
        '  z-index: 2147483647 !important;',
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

    // ------------------------------------------------------------ player model ---

    function firstMatch(sel) {
        try { return document.querySelector(sel); } catch (e) { return null; }
    }

    function allMatch(sel) {
        var root = (this && this.querySelectorAll) ? this : document;
        try { return Array.prototype.slice.call(root.querySelectorAll(sel)); } catch (e) { return []; }
    }

    // The overlays YouTube has opened. They are kept out of the sweep and
    // lifted above the pinned player.
    var extraKeep = [];

    /**
     * One combined selector each, so a mutation costs one query and not a dozen.
     */
    var CHROME_SELECTOR = CHROME_SELECTORS.join(',');
    var OVERLAY_SELECTOR = OVERLAY_SELECTORS.join(',');

    function chromeHosts() {
        return allMatch(CHROME_SELECTOR);
    }

    function playerEl() {
        var p = firstMatch('.html5-video-player');
        if (p) return p;
        var v = firstMatch('video');
        if (v && v.closest) return v.closest('.html5-video-player');
        return null;
    }

    function containsChrome(node, chrome) {
        for (var i = 0; i < chrome.length; i++) {
            if (node.contains(chrome[i])) return true;
        }
        return false;
    }

    /**
     * The container is the outermost ancestor of the player that also holds the
     * chrome. Pinning it moves the video and the control bar together, which is
     * the only way they stay lined up.
     */
    function containerFor(player, chrome) {
        if (!player) return null;
        var node = player;
        var best = null;
        while (node && node !== document.body) {
            if (containsChrome(node, chrome)) best = node;
            node = node.parentElement;
        }
        return best || firstMatch('#player-container-id') || firstMatch('#player') || player;
    }

    function model() {
        var player = playerEl();
        var chrome = chromeHosts();
        var container = containerFor(player, chrome);
        var keep = [];
        if (container) keep.push(container);
        chrome.forEach(function (c) {
            if (keep.indexOf(c) === -1) keep.push(c);
        });
        // Overlays that YouTube has opened are kept too, or the sweep would take
        // them away again the moment the page adds one.
        extraKeep.forEach(function (e) {
            if (keep.indexOf(e) === -1) keep.push(e);
        });
        return { player: player, container: container, chrome: chrome, keep: keep };
    }

    /**
     * The boxes between the container and the video. They are made static and
     * full size so that the video is laid out against the container and not
     * against whichever one of them happens to be positioned.
     */
    function fillChain(player, container) {
        var chain = [];
        if (!player || !container) return chain;
        var node = player.parentElement;
        while (node && node !== container && node !== document.body && node !== document.documentElement) {
            chain.push(node);
            node = node.parentElement;
        }
        return chain;
    }

    // -------------------------------------------------------------- the sweep ---

    /**
     * Hides everything that is not the player and its chrome.
     *
     * Hiding by position in the tree rather than by a list of selectors is what
     * makes this survive YouTube redesigns: it does not care what the masthead is
     * called, or how many of them there are.
     *
     * Only the ancestors of the kept elements are swept. The kept elements
     * themselves are left alone, because the sweep starts at the video element in
     * the current player - and the element that holds the video is its child, so
     * hiding that child hid the video with it.
     *
     * display:none rather than visibility:hidden because YouTube fades its own
     * control bar with visibility, and a blanket `visibility: visible !important`
     * over the player subtree defeats that outright - which is exactly how the
     * controls ended up never fading.
     */
    function sweepAll(keep, container) {
        restoreHidden();
        if (!document.body || !keep.length) return;

        keep.forEach(function (root) {
            var chain = ancestorChain(root);
            for (var i = 1; i < chain.length; i++) {
                var node = chain[i];
                // Nothing inside the container is swept. That is the player area,
                // and it holds the things the player itself raises - the end
                // screen, the settings sheet - and hiding them takes the
                // player's own UI with them.
                if (container && node === container) break;
                var onPath = chain[i - 1];
                var kids = node.children;
                for (var j = 0; j < kids.length; j++) {
                    var child = kids[j];
                    if (child === onPath) continue;
                    if (isKeepish(child, keep)) continue;
                    if (isSkippable(child)) continue;
                    hide(child);
                }
            }
        });
    }

    /**
     * Hides what was just added outside the player.
     *
     * The full sweep restores and re-applies display on every element it touches,
     * which on a page that rewrites itself several times a second is a lot of
     * style work for nothing - and it is audible in the PiP audio. The sweep
     * establishes the invariant once; this keeps it true as the page changes.
     */
    function hideAdded(nodes, keep) {
        if (!nodes) return;
        for (var i = 0; i < nodes.length; i++) {
            var n = nodes[i];
            if (n.nodeType !== 1) continue;
            if (isKeepish(n, keep)) continue;
            if (isSkippable(n)) continue;
            hide(n);
            // The new node may carry children of its own.
            var kids = n.children;
            for (var j = 0; j < kids.length; j++) {
                if (isKeepish(kids[j], keep)) continue;
                hide(kids[j]);
            }
        }
    }

    function ancestorChain(el) {
        var chain = [];
        var node = el;
        while (node && node !== document.body && node !== document.documentElement) {
            chain.push(node);
            node = node.parentElement;
        }
        if (document.body) chain.push(document.body);
        return chain;
    }

    function isKeepish(el, keep) {
        for (var i = 0; i < keep.length; i++) {
            if (el === keep[i] || el.contains(keep[i])) return true;
        }
        return false;
    }

    function isSkippable(el) {
        var tag = el.tagName;
        return tag === 'SCRIPT' || tag === 'STYLE' || tag === 'LINK' || tag === 'TEMPLATE';
    }

    function hide(el) {
        if (isHiddenByUs(el)) return;
        window.MyTubeFsHidden.push([
            el,
            el.style.getPropertyValue('display'),
            el.style.getPropertyPriority('display')
        ]);
        el.style.setProperty('display', 'none', 'important');
    }

    function restoreHidden() {
        var list = window.MyTubeFsHidden || [];
        for (var i = 0; i < list.length; i++) {
            var e = list[i];
            if (!e[0]) continue;
            if (e[2]) e[0].style.setProperty('display', e[1], e[2]);
            else e[0].style.display = e[1];
        }
        window.MyTubeFsHidden = [];
    }

    function isHiddenByUs(el) {
        var list = window.MyTubeFsHidden || [];
        for (var i = 0; i < list.length; i++) if (list[i][0] === el) return true;
        return false;
    }

    // ------------------------------------------------------------- overlays ---

    /**
     * Keeps YouTube's own overlays - the quality sheet above all - alive.
     *
     * The sweep hides everything around the player, and the quality sheet is
     * part of that: it lives in the player's own full-screen host, next to the
     * page, not inside the player. Hiding it is what made the settings overlay
     * never appear, and it is also why the panel could not be reached in
     * fullscreen.
     *
     * An overlay is recognised by its role, and it is only kept once YouTube has
     * actually opened it: a container carrying the `hidden` attribute is still
     * closed and must stay out of the way.
     */
    function keepOverlays() {
        var cands = allMatch(OVERLAY_SELECTOR);
        var m = model();
        var needSweep = false;
        for (var i = 0; i < cands.length; i++) {
            var el = cands[i];
            if (el.hasAttribute('hidden')) continue;
            if (el.getAttribute('aria-hidden') === 'true') continue;

            // An overlay that the sweep took with it has to be put back, and so
            // do the elements it lives in.
            if (isHiddenByUs(el) || hasHiddenAncestor(el)) {
                if (extraKeep.indexOf(el) === -1) {
                    extraKeep.push(el);
                    needSweep = true;
                }
            }

            // The pinned player covers the viewport and the sheet has to be
            // painted over it, so every positioned box it sits in is raised.
            raiseChain(el);
        }
        if (needSweep) sweepAll(m.keep.concat(extraKeep), m.container);
    }

    function hasHiddenAncestor(el) {
        var anc = el.parentElement;
        while (anc && anc !== document.body) {
            if (isHiddenByUs(anc)) return true;
            anc = anc.parentElement;
        }
        return false;
    }

    function raiseChain(el) {
        el.style.setProperty('z-index', '2147483647', 'important');
        var n = el.parentElement;
        while (n && n !== document.body) {
            var cs = getComputedStyle(n);
            if (cs.position !== 'static') {
                n.style.setProperty('z-index', '2147483647', 'important');
            }
            n = n.parentElement;
        }
    }

    // --------------------------------------------------------------- pinning ---

    function clearPins() {
        var marked = document.querySelectorAll(
            '[' + CONTAINER_ATTR + '],[' + ANC_ATTR + '],[' + FILL_ATTR + '],' +
            '[' + KEEP_ATTR + '],[' + RAISE_ATTR + '],[' + CHROME_OUT_ATTR + ']'
        );
        for (var i = 0; i < marked.length; i++) {
            marked[i].removeAttribute(CONTAINER_ATTR);
            marked[i].removeAttribute(ANC_ATTR);
            marked[i].removeAttribute(FILL_ATTR);
            marked[i].removeAttribute(KEEP_ATTR);
            marked[i].removeAttribute(RAISE_ATTR);
            marked[i].removeAttribute(CHROME_OUT_ATTR);
        }
    }

    function markAncestors(el) {
        var anc = el.parentElement;
        while (anc && anc !== document.documentElement) {
            anc.setAttribute(ANC_ATTR, '1');
            anc = anc.parentElement;
        }
    }

    /** The player and container the pins are on, so a rebuild can be noticed. */
    var lastPlayer = null;
    var lastContainer = null;
    var lastChrome = [];

    function applyModel(force) {
        var m = model();
        if (!m.container) return false;

        var chromeChanged = m.chrome.length !== lastChrome.length ||
            (m.chrome.length && m.chrome[0] !== lastChrome[0]);
        var changed = force ||
            chromeChanged ||
            m.player !== lastPlayer ||
            m.container !== lastContainer;
        lastPlayer = m.player;
        lastContainer = m.container;
        lastChrome = m.chrome;

        m.container.setAttribute(CONTAINER_ATTR, '1');
        markAncestors(m.container);

        // Everything between the container and the video becomes static and full
        // size, so the video is laid out against the container.
        fillChain(m.player, m.container).forEach(function (el) {
            el.setAttribute(FILL_ATTR, '1');
        });

        if (changed) {
            m.chrome.forEach(function (c) {
                if (m.container.contains(c)) {
                    // Inside the container: it moves with the video already. It only
                    // has to end up above it, which needs a z-index, which needs it
                    // to be positioned.
                    c.setAttribute(KEEP_ATTR, '1');
                    if (getComputedStyle(c).position === 'static') c.setAttribute(RAISE_ATTR, '1');
                } else {
                    // Outside the container: pinned over the video. It must not
                    // swallow the taps meant for the video.
                    c.setAttribute(CHROME_OUT_ATTR, '1');
                    c.setAttribute(KEEP_ATTR, '1');
                    markAncestors(c);
                }
            });

            // A rebuilt player means the page moved things around: sweep again.
            sweepAll(m.keep, m.container);

            // The poster sits on top of the video until playback starts. Hidden
            // after the sweep, because the sweep rebuilds the registry and would
            // undo it.
            var poster = firstMatch('#player-thumbnail-overlay');
            if (poster && !isKeepish(poster, m.keep)) hide(poster);
        }

        keepOverlays();
        return true;
    }

    // ------------------------------------------------------------- reporting ---

    function isFsActive() {
        return !!(window.MyTubeFsCssOn || window.MyTubePipOn);
    }

    function reassert() {
        if (!isFsActive()) return;
        var root = document.documentElement;
        if (!root) return;
        var want = window.MyTubeFsCssOn ? 'mytube-fs-css' : 'mytube-fs-pip';
        var other = window.MyTubeFsCssOn ? 'mytube-fs-pip' : 'mytube-fs-css';
        styleEl();
        root.classList.add(want);
        root.classList.remove(other);
        applyModel(true);
        if (window.MyTubeFsCssOn) verify('reassert', true);
    }

    /**
     * What the PiP window is actually doing, for the audio stutter.
     *
     * Dropped frames are the number that says whether the renderer is keeping up
     * in the small window, and the rectangle says whether the video is filling it
     * or sitting in a corner of a wider page.
     */
    var pipDiagLast = 0;
    function pipDiag() {
        if (!window.MyTubePipOn) return;
        var now = Date.now();
        if (now - pipDiagLast < 2000) return;
        pipDiagLast = now;
        try {
            var v = document.querySelector('video');
            if (!v) return;
            var q = v.getVideoPlaybackQuality ? v.getVideoPlaybackQuality() : null;
            var r = v.getBoundingClientRect();
            var msg = 'PIP dropped=' + (q ? q.droppedVideoFrames : '?') +
                '/' + (q ? q.totalVideoFrames : '?') +
                ' rect=' + [Math.round(r.left), Math.round(r.top), Math.round(r.width), Math.round(r.height)].join(',') +
                ' vp=' + window.innerWidth + 'x' + window.innerHeight +
                ' scale=' + (window.visualViewport ? window.visualViewport.scale : '?') +
                ' paused=' + v.paused;
            if (window.Android && window.Android.onFullscreenCssAudit) {
                window.Android.onFullscreenCssAudit(msg);
            }
        } catch (e) {}
    }

    function startObserver() {
        if (window.MyTubeFsObserver) return;
        window.MyTubeFsObserver = new MutationObserver(function (records) {
            if (!isFsActive()) return;
            var added = [];
            for (var i = 0; i < records.length; i++) {
                var r = records[i];
                if (r.type !== 'childList') continue;
                for (var j = 0; j < r.addedNodes.length; j++) added.push(r.addedNodes[j]);
            }
            var m = model();
            // Hide what was added outside the player. The full sweep is not run
            // again here: it restores and re-applies display on every element it
            // touches, and a page that rewrites itself several times a second
            // would pay for that over and over - which is audible in the PiP audio.
            hideAdded(added, m.keep);
            applyModel(false);
            pipDiag();
        });
        window.MyTubeFsObserver.observe(document.documentElement, {
            childList: true, subtree: true
        });
    }

    // The observer keeps the page stripped and the player's own overlays alive
    // while it is fullscreen. It is started once the document is there.
    if (document.documentElement) startObserver();

    // ------------------------------------------------------- fullscreen entry ---

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
     * testing, because the player is pinned over the control surface and would
     * otherwise swallow it. One-shot, and it suppresses the native click so the
     * button is not activated twice.
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

    /**
     * Located by label rather than by class: the desktop player's
     * .ytp-fullscreen-button does not exist on the mobile-web player, which ships
     * an unprefixed "icon-button fullscreen-icon" instead. Any attempt to name it
     * by class has matched nothing.
     *
     * Open shadow roots are searched too, because the current player draws its
     * chrome in one. A closed shadow root is unreachable and the button simply is
     * not found - in that case the app stays on the CSS path, which is a working
     * fullscreen rather than a broken one.
     */
    function findFsButtons() {
        var sel = '[aria-label*="full screen" i],[aria-label*="fullscreen" i],' +
                  '[title*="full screen" i],[title*="fullscreen" i]';
        return deepQuery(document, sel);
    }

    function deepQuery(root, sel) {
        var found = allMatch.call(root, sel);
        var all = allMatch.call(root, '*');
        for (var i = 0; i < all.length; i++) {
            if (all[i].shadowRoot) found = found.concat(deepQuery(all[i].shadowRoot, sel));
        }
        return found;
    }

    /**
     * First ancestor that would stop the tap reaching [el].
     */
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

    function restoreHitTestable() {
        var l = window.MyTubeFsForced || [];
        for (var i = 0; i < l.length; i++) {
            var e = l[i];
            if (e[3]) e[0].style.setProperty(e[1], e[2], e[3]);
            else e[0].style[e[1]] = e[2];
        }
        window.MyTubeFsForced = [];
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
        ['#movie_player', '#player-control-overlay', '#player-control-container',
         '#player-container-id', 'ytm-watch-player-controls',
         '.player-controls-content', '.player-controls-top', '.player-controls-bottom',
         '.ytp-large-mode-buttons', '.ytp-chrome-bottom', '.ytp-chrome-top',
         '.ytp-progress-bar'
        ].forEach(row);
        // Whatever YouTube happens to be calling its seek bar and lock button this week.
        var probe = ['seek', 'scrub', 'progress', 'lock', 'timeline', 'quality', 'setting'];
        var found = {};
        var all = document.querySelectorAll('#movie_player *, #player-control-container *, #player-control-overlay *');
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

    // ------------------------------------------------------------ verification ---

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

    /**
     * Describes the discovered player model, so a device log says what the page
     * actually looks like instead of what the app assumed.
     */
    window.MyTubeFsModel = function() {
        var m = model();
        var v = document.querySelector('video');
        var vr = v ? v.getBoundingClientRect() : null;
        var playerRect = m.player ? m.player.getBoundingClientRect() : null;
        var containerRect = m.container ? m.container.getBoundingClientRect() : null;
        return {
            player: m.player ? (m.player.id || m.player.className) : null,
            container: m.container ? (m.container.id || m.container.className || m.container.tagName) : null,
            chrome: m.chrome.map(function (c) { return c.tagName + '#' + (c.id || ''); }).join(','),
            hidden: (window.MyTubeFsHidden || []).length,
            video: vr ? [Math.round(vr.left), Math.round(vr.top), Math.round(vr.width), Math.round(vr.height)] : null,
            playerRect: playerRect ? [Math.round(playerRect.left), Math.round(playerRect.top), Math.round(playerRect.width), Math.round(playerRect.height)] : null,
            containerRect: containerRect ? [Math.round(containerRect.left), Math.round(containerRect.top), Math.round(containerRect.width), Math.round(containerRect.height)] : null,
            viewport: [window.innerWidth, window.innerHeight],
            visual: window.visualViewport ? [Math.round(window.visualViewport.width), Math.round(window.visualViewport.height), window.visualViewport.scale] : null,
            fsButton: findFsButtons().length
        };
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
        // The stylesheet hides everything that is not the player, so if the player
        // is not in the DOM yet this would paint the page black. Wait for it instead.
        if (on && !document.querySelector('.html5-video-player')) {
            Logless('defer: no .html5-video-player yet');
            return;
        }
        if (window.MyTubeFsCssOn === on) {
            if (on) reassert();
            return;
        }
        window.MyTubeFsCssOn = on;
        if (!on) {
            toggleClass('mytube-fs-css', false);
            if (!window.MyTubePipOn) teardown();
            verify('off');
            return;
        }
        window.MyTubePipOn = false;
        toggleClass('mytube-fs-pip', false);
        toggleClass('mytube-fs-css', true);
        reassert();
        verify('immediate', true);
        // YouTube re-lays-out for a while after a rotation, so verify late too.
        setTimeout(function() { if (window.MyTubeFsCssOn) { reassert(); verify('300ms', true); } }, 300);
        setTimeout(function() { if (window.MyTubeFsCssOn) { reassert(); verify('900ms', true); } }, 900);
    };

    window.MyTubeSetPipMode = function(on) {
        on = !!on;
        if (window.MyTubePipOn === on) {
            if (on) reassert();
            return;
        }
        window.MyTubePipOn = on;
        if (on) {
            window.MyTubeFsCssOn = false;
            toggleClass('mytube-fs-css', false);
            toggleClass('mytube-fs-pip', true);
            reassert();
            // The window changes size on the way into PiP and again on the way out.
            // Re-asserting after it settles is what keeps the player filling it.
            setTimeout(function() { if (window.MyTubePipOn) reassert(); }, 250);
            setTimeout(function() { if (window.MyTubePipOn) reassert(); }, 900);
        } else {
            toggleClass('mytube-fs-pip', false);
            if (!window.MyTubeFsCssOn) teardown();
        }
    };

    /** Puts the page back the way it was: no pins, nothing hidden. */
    function teardown() {
        clearPins();
        restoreHidden();
        extraKeep = [];
        var overlays = document.querySelectorAll(OVERLAY_SELECTOR);
        for (var i = 0; i < overlays.length; i++) {
            overlays[i].style.removeProperty('z-index');
        }
    }

    /**
     * Forces the layout to be recomputed after the window changed shape.
     *
     * Chromium does not always re-layout the page when the WebView is resized,
     * and a page that still believes it is in landscape keeps the landscape
     * picture after a rotation back to portrait. A synthetic resize event is the
     * one thing the page listens to for exactly this.
     */
    window.MyTubeFsRefresh = function() {
        try {
            window.dispatchEvent(new Event('resize'));
        } catch (e) {}
        try {
            var vv = window.visualViewport;
            if (vv && vv.dispatchEvent) vv.dispatchEvent(new Event('resize'));
        } catch (e) {}
        reassert();
        return true;
    };

    function Logless(msg) {
        try {
            if (window.Android && window.Android.onFullscreenCssAudit) {
                window.Android.onFullscreenCssAudit(msg);
            }
        } catch (e) {}
    }

    // ------------------------------------------------------------ video state ---

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
