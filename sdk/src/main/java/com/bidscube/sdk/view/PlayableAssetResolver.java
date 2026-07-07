package com.bidscube.sdk.view;

import android.content.Context;

/**
 * Resolves playable URLs from VAST: HTTPS CDN, SDK bundled assets, or file paths.
 */
public final class PlayableAssetResolver {

    private PlayableAssetResolver() {
    }

    public static String resolve(Context context, String url) {
        if (url == null || url.trim().isEmpty()) {
            return url;
        }
        String trimmed = url.trim();
        if (trimmed.startsWith("https://") || trimmed.startsWith("http://")
                || trimmed.startsWith("file://")) {
            return trimmed;
        }
        if (trimmed.startsWith("asset://")) {
            trimmed = trimmed.substring("asset://".length());
        }
        if (trimmed.startsWith("/")) {
            trimmed = trimmed.substring(1);
        }
        return "file:///android_asset/" + trimmed;
    }
}
