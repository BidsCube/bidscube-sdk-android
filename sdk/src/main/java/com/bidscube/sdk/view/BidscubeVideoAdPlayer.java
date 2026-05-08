package com.bidscube.sdk.view;

import android.view.ViewGroup;

/**
 * Video / VAST surface used by {@link com.bidscube.sdk.AdDisplayManager}.
 * Full implementation lives in the {@code fullVideo} flavor; lite ships a no-op stub.
 */
public interface BidscubeVideoAdPlayer {

    interface VideoCompletionListener {
        void onVideoCompleted();

        void onVideoSkipped();
    }

    void setLayoutParams(ViewGroup.LayoutParams params);

    void setOnVideoCompletionListener(VideoCompletionListener listener);

    void playVast(String vastTag, boolean isUrl);

    void skipVideo();

    void release();

    ViewGroup asViewGroup();
}
