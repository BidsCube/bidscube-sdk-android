package com.bidscube.sdk.view;

import android.content.Context;

import com.bidscube.sdk.config.VideoPlayerProvider;
import com.bidscube.sdk.utils.SDKLogger;
import com.bidscube.sdk.utils.VastParser;

/**
 * Full build: inline MP4 via {@link Media3VideoAdPlayer}, otherwise Google IMA.
 */
public final class VideoAdPlayerFactory {

    private static final String TAG = "VideoAdPlayerFactory";

    private VideoAdPlayerFactory() {
    }

    public static boolean isVideoSupported() {
        return true;
    }

    public static BidscubeVideoAdPlayer create(Context context, String adm, String vastRedirectUrl,
            VideoPlayerProvider videoPlayerProvider) {
        String inlineMediaUrl = resolveInlineMediaUrl(adm);
        if (inlineMediaUrl != null) {
            SDKLogger.d(TAG, "Selected Media3 player for inline MediaFile");
            return new Media3VideoAdPlayer(context, inlineMediaUrl, vastRedirectUrl, videoPlayerProvider);
        }
        SDKLogger.d(TAG, "Selected IMA player (no inline MediaFile in adm)");
        return new IMAPlayerHandler(adm, vastRedirectUrl, context, videoPlayerProvider);
    }

    /**
     * Returns progressive MP4 URL when {@code adm} is inline VAST XML with a {@code MediaFile}.
     */
    static String resolveInlineMediaUrl(String adm) {
        if (adm == null) {
            return null;
        }
        String trimmed = adm.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (!trimmed.startsWith("<")) {
            return null;
        }
        return VastParser.getMediaFileUrl(trimmed);
    }
}
