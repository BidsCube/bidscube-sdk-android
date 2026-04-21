package com.bidscube.sdk.config;

import android.content.Context;
import android.widget.VideoView;

/**
 * Supplies the {@link VideoView} used for IMA video ads inside {@link com.bidscube.sdk.view.IMAPlayerHandler}.
 * When no provider is set on {@link SDKConfig}, the SDK uses {@code new VideoView(context)}.
 */
public interface VideoPlayerProvider {

    /**
     * @param context Context for the view hierarchy (typically the activity hosting the ad).
     * @return A {@link VideoView} instance compatible with the Google IMA {@code VideoAdPlayer} adapter.
     */
    VideoView createVideoView(Context context);
}
