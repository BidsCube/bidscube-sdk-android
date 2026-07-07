package com.bidscube.sdk.view;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.Nullable;

import com.bidscube.sdk.config.VideoPlayerProvider;
import com.bidscube.sdk.models.video.VideoAdSlot;
import com.bidscube.sdk.models.video.VideoPlaybackPlan;
import com.bidscube.sdk.utils.SDKLogger;
import com.bidscube.sdk.utils.VideoPlaybackPlanBuilder;
import com.bidscube.sdk.utils.VastParser;

/**
 * Plays inline progressive MP4 from VAST using Media3 ({@link VideoSlotPlayer}).
 */
public class Media3VideoAdPlayer extends FrameLayout implements BidscubeVideoAdPlayer {

    private static final String TAG = "Media3VideoAdPlayer";

    private final String mediaUrl;
    private final String redirectUrl;
    private final VideoSlotPlayer slotPlayer;
    private VideoCompletionListener completionListener;
    private boolean skipped;

    public Media3VideoAdPlayer(
            Context context,
            String mediaUrl,
            String redirectUrl,
            VideoPlayerProvider videoPlayerProvider) {
        super(context);
        this.mediaUrl = mediaUrl != null ? mediaUrl.trim() : null;
        this.redirectUrl = redirectUrl;
        setBackgroundColor(Color.BLACK);

        slotPlayer = VideoSlotPlayerFactory.create(context);
        if (slotPlayer == null) {
            throw new IllegalStateException("VideoSlotPlayer unavailable in liteNoVideo flavor");
        }
        slotPlayer.attach(this);
        slotPlayer.setListener(new VideoSlotPlayer.Listener() {
            @Override
            public void onPrepared(VideoAdSlot slot) {
                if (completionListener != null) {
                    completionListener.onVideoLoaded();
                }
            }

            @Override
            public void onStarted(VideoAdSlot slot) {
                if (completionListener != null) {
                    completionListener.onVideoStarted();
                }
            }

            @Override
            public void onProgress(VideoAdSlot slot, long positionMs, long durationMs) {
            }

            @Override
            public void onCompleted(VideoAdSlot slot) {
                if (skipped) {
                    return;
                }
                if (completionListener != null) {
                    completionListener.onVideoCompleted();
                }
            }

            @Override
            public void onSkipped(VideoAdSlot slot) {
                if (completionListener != null) {
                    completionListener.onVideoSkipped();
                }
            }

            @Override
            public void onError(VideoAdSlot slot, Throwable error) {
                SDKLogger.e(TAG, "Playback error: " + error.getMessage());
                if (completionListener != null) {
                    completionListener.onVideoError(error.getMessage() != null
                            ? error.getMessage()
                            : "Media3 playback error");
                }
            }
        });

        slotPlayer.getView().setOnClickListener(v -> openClickThrough());
    }

    @Override
    public void setLayoutParams(ViewGroup.LayoutParams params) {
        super.setLayoutParams(params);
    }

    @Override
    public void setOnVideoCompletionListener(VideoCompletionListener listener) {
        this.completionListener = listener;
    }

    @Override
    public void playVast(String vastTag, boolean isUrl) {
        skipped = false;
        VideoAdSlot slot = resolveSlot(vastTag, isUrl);
        if (slot == null) {
            SDKLogger.e(TAG, "No MP4 URL available for playback");
            if (completionListener != null) {
                completionListener.onVideoError("No MP4 URL in VAST");
            }
            return;
        }
        slotPlayer.play(slot);
    }

    @Override
    public void skipVideo() {
        if (skipped) {
            return;
        }
        skipped = true;
        slotPlayer.skip();
    }

    @Override
    public void release() {
        slotPlayer.release();
        completionListener = null;
    }

    @Override
    public ViewGroup asViewGroup() {
        return this;
    }

    @Nullable
    private VideoAdSlot resolveSlot(String vastTag, boolean isUrl) {
        if (isUrl && vastTag != null && !vastTag.trim().isEmpty()) {
            return singleUrlSlot(vastTag.trim());
        }
        if (vastTag != null && vastTag.trim().startsWith("<")) {
            VideoPlaybackPlan plan = VideoPlaybackPlanBuilder.build(vastTag.trim());
            if (!plan.isEmpty()) {
                return plan.getFirstSlot();
            }
        }
        if (mediaUrl != null && !mediaUrl.isEmpty()) {
            return singleUrlSlot(mediaUrl);
        }
        String fallback = vastTag != null ? VastParser.getMediaFileUrl(vastTag.trim()) : null;
        return fallback != null ? singleUrlSlot(fallback) : null;
    }

    private static VideoAdSlot singleUrlSlot(String url) {
        return new VideoAdSlot(
                null,
                1,
                new com.bidscube.sdk.models.video.VastMediaFile(url, "video/mp4", null, null, null, "progressive"),
                null,
                null,
                null,
                com.bidscube.sdk.models.video.VastTrackingEvents.empty(),
                null,
                null);
    }

    private void openClickThrough() {
        if (completionListener != null) {
            completionListener.onVideoClicked();
        }
        if (redirectUrl == null || redirectUrl.trim().isEmpty()) {
            return;
        }
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(redirectUrl.trim()));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            getContext().startActivity(intent);
        } catch (Exception e) {
            SDKLogger.e(TAG, "Failed to open click-through: " + e.getMessage());
        }
    }
}
