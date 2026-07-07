package com.bidscube.sdk.view;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.bidscube.sdk.config.VideoPlayerProvider;
import com.bidscube.sdk.models.video.VideoAdSlot;
import com.bidscube.sdk.models.video.VideoPlaybackPlan;
import com.bidscube.sdk.utils.SDKLogger;
import com.bidscube.sdk.utils.VideoPlaybackPlanBuilder;
import com.bidscube.sdk.video.PodSkipPolicy;

/**
 * Plays a VAST ad pod or single-video plan sequentially with an "Ad X of Y" indicator.
 * Uses {@link VideoSlotPlayer} (Media3 in fullVideo flavor).
 */
public class VastAdPodPlayer extends FrameLayout implements BidscubeVideoAdPlayer {

    private static final String TAG = "VastAdPodPlayer";

    public interface PodProgressListener {
        void onPodAdStarted(int currentIndex, int totalAds, VideoAdSlot slot);
    }

    public interface SlotLifecycleListener {
        void onSlotCompleted(int adIndex, int totalAds, String adId);

        void onSlotSkipped(int adIndex, int totalAds, String adId);
    }

    private final TextView podBadge;
    private final String defaultClickUrl;
    private final VideoSlotPlayer slotPlayer;
    private final PodPlaybackController controller = new PodPlaybackController();
    private final PodSkipPolicy skipPolicy;

    private VideoCompletionListener completionListener;
    private PodProgressListener podProgressListener;
    private SlotLifecycleListener slotLifecycleListener;
    private String activeClickUrl;
    private VideoPlaybackPlan plan;
    private boolean podFinished;
    private boolean legacyStartedEmitted;
    private boolean showPodCounter = true;

    public VastAdPodPlayer(Context context, String defaultClickUrl, VideoPlayerProvider videoPlayerProvider) {
        this(context, defaultClickUrl, videoPlayerProvider, PodSkipPolicy.SKIP_CURRENT_AND_CONTINUE);
    }

    public VastAdPodPlayer(
            Context context,
            String defaultClickUrl,
            VideoPlayerProvider videoPlayerProvider,
            PodSkipPolicy skipPolicy) {
        super(context);
        this.defaultClickUrl = defaultClickUrl;
        this.skipPolicy = skipPolicy != null ? skipPolicy : PodSkipPolicy.SKIP_CURRENT_AND_CONTINUE;

        slotPlayer = VideoSlotPlayerFactory.create(context);
        if (slotPlayer == null) {
            throw new IllegalStateException("VideoSlotPlayer unavailable");
        }
        setBackgroundColor(Color.BLACK);
        slotPlayer.attach(this);

        podBadge = new TextView(context);
        podBadge.setTextColor(Color.WHITE);
        podBadge.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        podBadge.setTypeface(Typeface.DEFAULT_BOLD);
        podBadge.setPadding(dp(12), dp(8), dp(12), dp(8));
        podBadge.setBackgroundColor(0xCC000000);
        LayoutParams badgeParams = new LayoutParams(
                LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.START);
        badgeParams.setMargins(dp(14), dp(14), dp(14), dp(14));
        podBadge.setLayoutParams(badgeParams);
        addView(podBadge);
        podBadge.bringToFront();

        slotPlayer.getView().setOnClickListener(v -> openClickThrough());

        slotPlayer.setListener(new VideoSlotPlayer.Listener() {
            @Override
            public void onPrepared(VideoAdSlot slot) {
                controller.onSlotPlaybackSuccess();
                if (!legacyStartedEmitted && controller.getCurrentAdIndex() == 1 && completionListener != null) {
                    completionListener.onVideoLoaded();
                }
            }

            @Override
            public void onStarted(VideoAdSlot slot) {
                controller.onSlotPrepared();
            }

            @Override
            public void onProgress(VideoAdSlot slot, long positionMs, long durationMs) {
                if (durationMs > 0) {
                    controller.onQuartileProgress(positionMs / (float) durationMs);
                }
            }

            @Override
            public void onCompleted(VideoAdSlot slot) {
                controller.onSlotCompleted();
            }

            @Override
            public void onSkipped(VideoAdSlot slot) {
                controller.onSlotSkippedByUser();
            }

            @Override
            public void onError(VideoAdSlot slot, Throwable error) {
                controller.onSlotError(error != null ? error.getMessage() : "playback_error");
            }
        });

        controller.setListener(new PodPlaybackController.Listener() {
            @Override
            public void onRequestPlaySlot(VideoAdSlot slot, int adIndex, int totalAds) {
                playSlot(slot, adIndex, totalAds);
            }

            @Override
            public void onSlotStarted(int adIndex, int totalAds, String adId) {
                if (podProgressListener != null) {
                    podProgressListener.onPodAdStarted(adIndex, totalAds, controller.getActiveSlot());
                }
                if (!legacyStartedEmitted) {
                    legacyStartedEmitted = true;
                    if (completionListener != null) {
                        completionListener.onVideoStarted();
                    }
                }
            }

            @Override
            public void onSlotCompleted(int adIndex, int totalAds, String adId) {
                if (slotLifecycleListener != null) {
                    slotLifecycleListener.onSlotCompleted(adIndex, totalAds, adId);
                }
            }

            @Override
            public void onSlotSkipped(int adIndex, int totalAds, String adId) {
                if (slotLifecycleListener != null) {
                    slotLifecycleListener.onSlotSkipped(adIndex, totalAds, adId);
                }
            }

            @Override
            public void onPodCompleted(boolean anyVideoPlayed, boolean lastActionWasSkip) {
                finishPod(lastActionWasSkip);
            }

            @Override
            public void onPodFailed(String message) {
                finishWithError(message);
            }
        });
    }

    public void setPodProgressListener(PodProgressListener listener) {
        this.podProgressListener = listener;
    }

    public void setSlotLifecycleListener(SlotLifecycleListener listener) {
        this.slotLifecycleListener = listener;
    }

    public void setShowPodCounter(boolean showPodCounter) {
        this.showPodCounter = showPodCounter;
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
        if (isUrl) {
            finishWithError("Ad pod requires inline VAST XML");
            return;
        }
        playPlan(VideoPlaybackPlanBuilder.build(vastTag));
    }

    /** Play a pre-built playback plan (VAST-only or OpenRTB-enriched). */
    public void playPlan(VideoPlaybackPlan playbackPlan) {
        plan = playbackPlan;
        if (plan == null || plan.isEmpty()) {
            finishWithError("No playable ads in VAST");
            return;
        }
        podFinished = false;
        legacyStartedEmitted = false;
        startPlaybackWhenReady(() -> controller.start(plan));
    }

    @Override
    public void skipVideo() {
        if (podFinished) {
            return;
        }
        if (skipPolicy == PodSkipPolicy.SKIP_ENTIRE_POD) {
            slotPlayer.stop();
            finishPod(true);
            return;
        }
        slotPlayer.skip();
    }

    @Override
    public void release() {
        slotPlayer.release();
        controller.destroy();
        completionListener = null;
        podProgressListener = null;
        slotLifecycleListener = null;
    }

    @Override
    public ViewGroup asViewGroup() {
        return this;
    }

    public VideoPlaybackPlan getPlan() {
        return plan;
    }

    public PodPlaybackController getController() {
        return controller;
    }

    private void playSlot(VideoAdSlot slot, int adIndex, int totalAds) {
        activeClickUrl = firstNonEmpty(slot.getClickThroughUrl(), defaultClickUrl);
        updateBadge(adIndex, totalAds, slot.getTitle());
        slotPlayer.stop();
        slotPlayer.play(slot);
        SDKLogger.d(TAG, "Playing slot " + adIndex + "/" + totalAds + " url=" + slot.getMediaFile().getUrl());
    }

    private void finishPod(boolean userSkipped) {
        if (podFinished) {
            return;
        }
        podFinished = true;
        if (completionListener == null) {
            return;
        }
        if (userSkipped) {
            completionListener.onVideoSkipped();
        } else {
            completionListener.onVideoCompleted();
        }
    }

    private void finishWithError(String message) {
        if (completionListener != null) {
            completionListener.onVideoError(message);
        }
    }

    private void startPlaybackWhenReady(Runnable start) {
        if (getWidth() > 0 && getHeight() > 0) {
            start.run();
            return;
        }
        getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                if (getWidth() > 0 && getHeight() > 0) {
                    getViewTreeObserver().removeOnGlobalLayoutListener(this);
                    start.run();
                }
            }
        });
    }

    private void updateBadge(int current, int total, String title) {
        if (!showPodCounter || total <= 1) {
            podBadge.setVisibility(View.GONE);
            return;
        }
        String label = "Ad " + current + " of " + total;
        if (title != null && !title.isEmpty()) {
            label += " • " + title;
        }
        podBadge.setText(label);
        podBadge.setVisibility(View.VISIBLE);
    }

    private void openClickThrough() {
        controller.onSlotClicked();
        if (completionListener != null) {
            completionListener.onVideoClicked();
        }
        if (activeClickUrl == null || activeClickUrl.isEmpty()) {
            return;
        }
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(activeClickUrl));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            getContext().startActivity(intent);
        } catch (Exception e) {
            SDKLogger.e(TAG, "Click-through failed: " + e.getMessage());
        }
    }

    private static String firstNonEmpty(String primary, String fallback) {
        if (primary != null && !primary.trim().isEmpty()) {
            return primary.trim();
        }
        if (fallback != null && !fallback.trim().isEmpty()) {
            return fallback.trim();
        }
        return null;
    }

    private int dp(int value) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, value, getResources().getDisplayMetrics());
    }
}
