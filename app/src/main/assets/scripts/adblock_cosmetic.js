(function() {
    function removeAds() {
        const selectors = [
            'ytd-ad-slot-renderer',
            'ytd-ad-slot-renderer[class*="ad"]',
            'ytd-in-feed-ad-layout-renderer',
            'ytd-banner-promo-renderer',
            'ytd-companion-slot-renderer',
            'ytd-statement-banner-renderer',
            'ytd-action-companion-ad-renderer',
            'ytd-display-ad-renderer',
            'ytd-video-masthead-ad-advertiser-info-renderer',
            'ytd-video-masthead-ad-v3-renderer',
            'ytd-search-pyv-renderer',
            'ytd-promoted-video-renderer',
            'ytd-promoted-sparkles-text-search-renderer',
            'ytd-player-legacy-desktop-ads-renderer',
            '.ytd-ad-slot-renderer',
            '#masthead-ad',
            '#player-ads',
            '#merch-shelf',
            '.ytp-ad-player-overlay',
            '.ytp-ad-text-overlay',
            '.ytp-ad-image-overlay',
            '.video-ads',
            '.ytp-ad-action-interstitial',
            '.ytp-ad-survey-interstitial',
            '.ytp-ad-skip-button-container',
            '.ytp-ad-overlay-container',
            '.ytp-ad-progress',
            '.ytp-ad-progress-list',
            'ytd-mealbar-promo-renderer',
            '.ytd-video-masthead-ad-v3-renderer',
            '.ytp-ad-message-overlay',
            '.ytp-ad-message-overlay-container',
            '.ytp-ad-overlay-slot',
            '.ytp-ad-overlay-image',
            '.ytp-ad-action-ad-badge',
            '.ytp-ad-player-overlay-flyout-cta',
            '.ytp-ad-skip-button-modern',
            '.ytp-ad-badge',
            '.ytp-ad-preview-container',
            '.ytp-ad-visit-advertiser-button',
            '.ytp-ad-action-interstitial-slot',
            '.ytp-ad-action-ad-container',
            '#ad-container',
            '.ad-container',
            '.ytp-ad-message-overlay-display',
        ];
        selectors.forEach(function(sel) {
            document.querySelectorAll(sel).forEach(function(el) { el.remove(); });
        });
    }

    removeAds();
    new MutationObserver(removeAds).observe(document.body, { childList: true, subtree: true });
})();
