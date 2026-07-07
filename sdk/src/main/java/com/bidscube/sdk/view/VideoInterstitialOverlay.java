package com.bidscube.sdk.view;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

/**
 * Small top-right skip/close control for video interstitials (LevelPlay-style UX).
 */
public final class VideoInterstitialOverlay {

    public interface Listener {
        void onSkipRequested();

        void onCloseRequested();
    }

    private static final int MIN_SIZE_DP = 32;
    private static final int MAX_SIZE_DP = 40;
    private static final int EDGE_MARGIN_DP = 14;

    private final Context context;
    private final FrameLayout container;
    private final TextView actionButton;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private Listener listener;
    private Runnable countdownTask;
    private int remainingSeconds;
    private boolean skipEnabled;
    private boolean endCardMode;
    private boolean hasSkipOffset;
    /** When false, ✕ advances (skip) instead of closing the ad — used in gamified flows. */
    private boolean closeButtonDismissesAd = true;

    private VideoInterstitialOverlay(Context context, FrameLayout container, TextView actionButton) {
        this.context = context;
        this.container = container;
        this.actionButton = actionButton;
    }

    public static VideoInterstitialOverlay attach(@NonNull FrameLayout container) {
        Context context = container.getContext();
        TextView button = new TextView(context);
        button.setTextColor(Color.WHITE);
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        button.setGravity(Gravity.CENTER);
        button.setSingleLine(true);
        button.setIncludeFontPadding(false);

        int horizontalPadding = dp(context, 10);
        int verticalPadding = dp(context, 6);
        button.setPadding(horizontalPadding, verticalPadding, horizontalPadding, verticalPadding);

        GradientDrawable background = new GradientDrawable();
        background.setColor(0x99000000);
        background.setCornerRadius(dp(context, 18));
        button.setBackground(background);

        int minSize = dp(context, MIN_SIZE_DP);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        params.gravity = Gravity.TOP | Gravity.END;
        int margin = dp(context, EDGE_MARGIN_DP);
        params.setMargins(margin, margin, margin, margin);
        button.setMinimumWidth(minSize);
        button.setMinimumHeight(minSize);
        button.setLayoutParams(params);

        container.addView(button);
        VideoInterstitialOverlay overlay = new VideoInterstitialOverlay(context, container, button);
        button.setOnClickListener(v -> overlay.handleClick());
        return overlay;
    }

    public void setCloseButtonDismissesAd(boolean dismisses) {
        this.closeButtonDismissesAd = dismisses;
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public void startSkipCountdown(int skipOffsetSeconds) {
        stopCountdown();
        hasSkipOffset = skipOffsetSeconds > 0;
        skipEnabled = false;
        endCardMode = false;
        remainingSeconds = Math.max(skipOffsetSeconds, 0);

        if (!hasSkipOffset) {
            showCloseOnly();
            return;
        }

        updateCountdownLabel();
        countdownTask = new Runnable() {
            @Override
            public void run() {
                remainingSeconds--;
                if (remainingSeconds > 0) {
                    updateCountdownLabel();
                    handler.postDelayed(this, 1000L);
                } else {
                    enableSkip();
                }
            }
        };
        handler.postDelayed(countdownTask, 1000L);
    }

    public void enableSkip() {
        stopCountdown();
        skipEnabled = true;
        endCardMode = false;
        actionButton.setText("Skip");
        actionButton.setVisibility(android.view.View.VISIBLE);
    }

    public void showCloseOnly() {
        stopCountdown();
        skipEnabled = false;
        endCardMode = false;
        actionButton.setText("\u2715");
        actionButton.setVisibility(android.view.View.VISIBLE);
    }

    public void showEndCardClose() {
        stopCountdown();
        skipEnabled = false;
        endCardMode = true;
        actionButton.setText("\u2715");
        actionButton.setVisibility(android.view.View.VISIBLE);
    }

    public void hide() {
        stopCountdown();
        actionButton.setVisibility(android.view.View.GONE);
    }

    public void detach() {
        stopCountdown();
        container.removeView(actionButton);
    }

    public void bringToFront() {
        actionButton.bringToFront();
    }

    public boolean isEndCardMode() {
        return endCardMode;
    }

    public boolean isSkipEnabled() {
        return skipEnabled;
    }

    private void handleClick() {
        if (listener == null) {
            return;
        }
        if (endCardMode || (!skipEnabled && !hasSkipOffset)) {
            if (closeButtonDismissesAd) {
                listener.onCloseRequested();
            } else {
                listener.onSkipRequested();
            }
        } else if (skipEnabled) {
            listener.onSkipRequested();
        }
    }

    private void updateCountdownLabel() {
        actionButton.setText("Skip in " + remainingSeconds);
        actionButton.setVisibility(android.view.View.VISIBLE);
    }

    private void stopCountdown() {
        if (countdownTask != null) {
            handler.removeCallbacks(countdownTask);
            countdownTask = null;
        }
    }

    private static int dp(Context context, int value) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, value, context.getResources().getDisplayMetrics());
    }
}
