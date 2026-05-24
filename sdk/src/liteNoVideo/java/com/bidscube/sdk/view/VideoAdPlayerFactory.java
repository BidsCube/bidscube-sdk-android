package com.bidscube.sdk.view;

import android.content.Context;

import com.bidscube.sdk.config.VideoPlayerProvider;

/**
 * Lite build: never loads IMA / Media3 types.
 */
public final class VideoAdPlayerFactory {

    private VideoAdPlayerFactory() {
    }

    public static boolean isVideoSupported() {
        return false;
    }

    public static BidscubeVideoAdPlayer create(Context context, String adm, String vastRedirectUrl,
            VideoPlayerProvider videoPlayerProvider) {
        return new LiteVideoAdStub(context);
    }
}
