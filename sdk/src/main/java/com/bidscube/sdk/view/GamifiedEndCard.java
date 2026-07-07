package com.bidscube.sdk.view;

import android.content.Context;
import android.widget.FrameLayout;

import com.bidscube.sdk.models.video.GamifiedEndCardConfig;

/**
 * Interactive post-video end card layer (independent from the video player).
 */
public interface GamifiedEndCard {

    interface Listener {
        void onEndCardShown();

        void onCtaClicked(String clickUrl);

        void onCloseRequested();
    }

    void show(GamifiedEndCardConfig config, Listener listener);

    void hide();

    void destroy();
}
