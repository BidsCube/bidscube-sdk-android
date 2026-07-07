package com.bidscube.sdk.view;

import android.view.View;
import android.view.ViewGroup;

import com.bidscube.sdk.models.video.VideoAdSlot;

/**
 * Plays one {@link VideoAdSlot} (no pod logic).
 */
public interface VideoSlotPlayer {

    interface Listener {
        void onPrepared(VideoAdSlot slot);

        void onStarted(VideoAdSlot slot);

        void onProgress(VideoAdSlot slot, long positionMs, long durationMs);

        void onCompleted(VideoAdSlot slot);

        void onSkipped(VideoAdSlot slot);

        void onError(VideoAdSlot slot, Throwable error);
    }

    View getView();

    void attach(ViewGroup container);

    void play(VideoAdSlot slot);

    void pause();

    void resume();

    void skip();

    void stop();

    void release();

    void setMuted(boolean muted);

    void setListener(Listener listener);
}
