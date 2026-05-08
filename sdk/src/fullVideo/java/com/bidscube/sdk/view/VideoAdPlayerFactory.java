package com.bidscube.sdk.view;

import android.content.Context;

import com.bidscube.sdk.config.VideoPlayerProvider;

/**
 * Full build: Google IMA + Media3 video stack.
 */
public final class VideoAdPlayerFactory {

    private VideoAdPlayerFactory() {
    }

    public static BidscubeVideoAdPlayer create(Context context, String adm, String vastRedirectUrl,
            VideoPlayerProvider videoPlayerProvider) {
        return new IMAPlayerHandler(adm, vastRedirectUrl, context, videoPlayerProvider);
    }
}
