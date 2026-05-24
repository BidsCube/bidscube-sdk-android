package com.bidscube.sdk.config;

import android.content.Context;
import android.widget.VideoView;

/**
 * Supplies the {@link VideoView} used for Google IMA playback in the {@code fullVideo} SDK artifact
 * (see {@link com.bidscube.sdk.view.BidscubeVideoAdPlayer}).
 * When no provider is set on {@link SDKConfig}, the SDK uses {@code new VideoView(context)}.
 * Only consulted when {@link SDKConfig#isVideoAdsEnabled()} is {@code true}; you can omit a provider entirely when
 * {@link SDKConfig.Builder#videoAdsEnabled(boolean)} is {@code false}.
 */
public interface VideoPlayerProvider {

    /**
     * @param context Context for the view hierarchy (typically the activity hosting the ad).
     * @return A {@link VideoView} instance compatible with the Google IMA {@code VideoAdPlayer} adapter.
     */
    VideoView createVideoView(Context context);
}
