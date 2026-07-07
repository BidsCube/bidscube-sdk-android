package com.bidscube.sdk.view;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Rect;
import android.net.Uri;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.bidscube.sdk.ads.VideoAdFormat;
import com.bidscube.sdk.interfaces.AdCallback;
import com.bidscube.sdk.models.video.VideoAdSlot;
import com.bidscube.sdk.models.video.VideoPlaybackPlan;
import com.bidscube.sdk.utils.SDKLogger;
import com.bidscube.sdk.utils.VastVideoTracker;
import com.bidscube.sdk.utils.VideoPlaybackPlanBuilder;

/**
 * In-feed / outstream video ad view: autoplay muted when ≥50% visible, pause when off-screen.
 */
public class OutstreamVideoAdView extends FrameLayout {

    private static final String TAG = "OutstreamVideoAdView";
    private static final float VIEWABILITY_THRESHOLD = 0.5f;

    private final VideoSlotPlayer slotPlayer;
    private final TextView ctaView;
    @Nullable
    private AdCallback callback;
    @Nullable
    private String placementId;
    @Nullable
    private VideoAdSlot slot;
    private boolean impressionFired;
    private boolean started;
    private boolean completed;
    private boolean firstQuartileFired;
    private boolean midpointFired;
    private boolean thirdQuartileFired;
    private final ViewTreeObserver.OnScrollChangedListener scrollListener = this::checkVisibility;
    private final ViewTreeObserver.OnGlobalLayoutListener layoutListener = this::checkVisibility;

    public OutstreamVideoAdView(Context context) {
        super(context);
        setBackgroundColor(Color.BLACK);
        int heightPx = (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 220, context.getResources().getDisplayMetrics());
        setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, heightPx));

        slotPlayer = VideoSlotPlayerFactory.create(context);
        if (slotPlayer == null) {
            throw new IllegalStateException("Outstream requires fullVideo flavor");
        }
        slotPlayer.setMuted(true);
        slotPlayer.attach(this);

        ctaView = new TextView(context);
        ctaView.setText("Learn More");
        ctaView.setTextColor(Color.WHITE);
        ctaView.setBackgroundColor(0xCC007AFF);
        ctaView.setPadding(24, 12, 24, 12);
        ctaView.setVisibility(GONE);
        LayoutParams ctaParams = new LayoutParams(
                LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.END);
        ctaParams.setMargins(16, 16, 16, 16);
        ctaView.setLayoutParams(ctaParams);
        addView(ctaView);

        slotPlayer.setListener(new VideoSlotPlayer.Listener() {
            @Override
            public void onPrepared(VideoAdSlot s) {
            }

            @Override
            public void onStarted(VideoAdSlot s) {
                if (!started && callback != null && placementId != null) {
                    started = true;
                    callback.onVideoAdStarted(placementId);
                    VastVideoTracker.fireStart(s);
                }
            }

            @Override
            public void onProgress(VideoAdSlot s, long positionMs, long durationMs) {
                if (durationMs > 0) {
                    float p = positionMs / (float) durationMs;
                    if (!firstQuartileFired && p >= 0.25f) {
                        firstQuartileFired = true;
                        VastVideoTracker.fireFirstQuartile(s);
                    }
                    if (!midpointFired && p >= 0.5f) {
                        midpointFired = true;
                        VastVideoTracker.fireMidpoint(s);
                    }
                    if (!thirdQuartileFired && p >= 0.75f) {
                        thirdQuartileFired = true;
                        VastVideoTracker.fireThirdQuartile(s);
                    }
                }
            }

            @Override
            public void onCompleted(VideoAdSlot s) {
                completed = true;
                slotPlayer.pause();
                ctaView.setVisibility(VISIBLE);
                VastVideoTracker.fireComplete(s);
                if (callback != null && placementId != null) {
                    callback.onVideoAdCompleted(placementId);
                }
            }

            @Override
            public void onSkipped(VideoAdSlot s) {
            }

            @Override
            public void onError(VideoAdSlot s, Throwable error) {
                if (callback != null && placementId != null) {
                    callback.onAdFailed(placementId, -1,
                            error != null ? error.getMessage() : "outstream error");
                }
            }
        });
    }

    public void bindVast(String placementId, String vastXml, String clickUrl, AdCallback callback) {
        this.placementId = placementId;
        this.callback = callback;
        VideoPlaybackPlan plan = VideoPlaybackPlanBuilder.build(vastXml);
        if (plan.isEmpty()) {
            if (callback != null) {
                callback.onAdFailed(placementId, -1, "No playable VAST video");
            }
            return;
        }
        slot = plan.getFirstSlot();
        firstQuartileFired = false;
        midpointFired = false;
        thirdQuartileFired = false;
        started = false;
        completed = false;
        impressionFired = false;
        if (callback != null) {
            callback.onAdLoaded(placementId);
        }
        ctaView.setOnClickListener(v -> {
            if (callback != null) {
                callback.onAdClicked(placementId);
            }
            VastVideoTracker.fireClick(slot);
            openUrl(clickUrl != null ? clickUrl : slot.getClickThroughUrl());
        });
        slotPlayer.getView().setOnClickListener(v -> ctaView.performClick());
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        getViewTreeObserver().addOnScrollChangedListener(scrollListener);
        getViewTreeObserver().addOnGlobalLayoutListener(layoutListener);
        checkVisibility();
    }

    @Override
    protected void onDetachedFromWindow() {
        getViewTreeObserver().removeOnScrollChangedListener(scrollListener);
        getViewTreeObserver().removeOnGlobalLayoutListener(layoutListener);
        slotPlayer.release();
        super.onDetachedFromWindow();
    }

    private void checkVisibility() {
        if (slot == null || completed) {
            return;
        }
        float visible = visibleFraction();
        if (visible >= VIEWABILITY_THRESHOLD) {
            if (!impressionFired) {
                impressionFired = true;
                VastVideoTracker.fireImpression(slot);
                if (callback != null && placementId != null) {
                    callback.onAdDisplayed(placementId);
                }
                slotPlayer.play(slot);
            } else if (!completed) {
                slotPlayer.resume();
            }
        } else {
            slotPlayer.pause();
        }
    }

    private float visibleFraction() {
        if (!isShown()) {
            return 0f;
        }
        Rect rect = new Rect();
        if (!getGlobalVisibleRect(rect)) {
            return 0f;
        }
        int visibleArea = rect.width() * rect.height();
        int totalArea = getWidth() * getHeight();
        if (totalArea <= 0) {
            return 0f;
        }
        return visibleArea / (float) totalArea;
    }

    private void openUrl(@Nullable String url) {
        if (url == null || url.trim().isEmpty()) {
            return;
        }
        Context ctx = getContext();
        if (!(ctx instanceof Activity)) {
            return;
        }
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url.trim()));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(intent);
        } catch (Exception e) {
            SDKLogger.e(TAG, "Click-through failed: " + e.getMessage());
        }
    }
}
