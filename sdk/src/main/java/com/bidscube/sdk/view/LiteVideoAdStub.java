package com.bidscube.sdk.view;

import android.content.Context;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.bidscube.sdk.utils.SDKLogger;

/**
 * No-op video player for the {@code liteNoVideo} artifact (no IMA / Media3 on the classpath).
 */
public class LiteVideoAdStub extends FrameLayout implements BidscubeVideoAdPlayer {

    private static final String TAG = "LiteVideoAdStub";

    private BidscubeVideoAdPlayer.VideoCompletionListener completionListener;

    public LiteVideoAdStub(Context context) {
        super(context);
        setBackgroundColor(0xFF000000);
    }

    @Override
    public void setOnVideoCompletionListener(BidscubeVideoAdPlayer.VideoCompletionListener listener) {
        this.completionListener = listener;
    }

    @Override
    public void playVast(String vastTag, boolean isUrl) {
        SDKLogger.w(TAG, "Video ads are not supported in sdk-lite-no-video; skipping VAST playback.");
        if (completionListener != null) {
            completionListener.onVideoSkipped();
        }
    }

    @Override
    public void skipVideo() {
        if (completionListener != null) {
            completionListener.onVideoSkipped();
        }
    }

    @Override
    public void release() {
        completionListener = null;
    }

    @Override
    public ViewGroup asViewGroup() {
        return this;
    }
}
