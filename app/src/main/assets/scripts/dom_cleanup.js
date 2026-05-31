(function() {
    function cleanup() {
        const selectors = [
            'ytd-comments',
            '#comments',
            'ytd-reel-shelf-renderer',
            '#shorts-shelf',
            'ytd-watch-next-secondary-results-renderer',
            '#related',
            'ytd-rich-item-renderer:not([class*="video"])',
            'ytd-guide-renderer',
            '#guide',
            'ytd-notification-topbar-button-renderer',
        ];
        selectors.forEach(function(sel) {
            document.querySelectorAll(sel).forEach(function(el) { el.remove(); });
        });
    }

    cleanup();
    new MutationObserver(cleanup).observe(document.body, { childList: true, subtree: true });
})();
