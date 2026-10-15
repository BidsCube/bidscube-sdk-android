package com.bidscube.sdk.view;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.bidscube.sdk.video.VideoUiDefaults;

/**
 * Small top-right skip/close control for video interstitials (LevelPlay-style UX).
 * On TV/Leanback the control is larger and only focusable when an action is available.
 */
public final class VideoInterstitialOverlay {

    public interface Listener {
        void onSkipRequested();

        void onCloseRequested();
    }

    private static final int MIN_SIZE_DP = 32;
    private static final int TV_MIN_SIZE_DP = 48;
    private static final int EDGE_MARGIN_DP = 14;

    private final FrameLayout container;
    private final TextView actionButton;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private Listener listener;
    private Runnable countdownTask;
    private int remainingSeconds;
    private boolean skipEnabled;
    private boolean endCardMode;
    private boolean hasSkipOffset;
    /** When false, close advances (skip) instead of dismissing — used in gamified flows. */
    private boolean closeButtonDismissesAd = true;
    private boolean actionArmed;

    private VideoInterstitialOverlay(FrameLayout container, TextView actionButton) {
        this.container = container;
        this.actionButton = actionButton;
    }

    public static VideoInterstitialOverlay attach(@NonNull FrameLayout container) {
        Context context = container.getContext();
        boolean televisionUi = VideoUiDefaults.isTelevision(context);
        TextView button = new TextView(context);
        button.setTextColor(Color.WHITE);
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, televisionUi ? 16 : 13);
        button.setGravity(Gravity.CENTER);
        button.setSingleLine(true);
        button.setIncludeFontPadding(false);

        int horizontalPadding = dp(context, televisionUi ? 16 : 10);
        int verticalPadding = dp(context, televisionUi ? 10 : 6);
        button.setPadding(horizontalPadding, verticalPadding, horizontalPadding, verticalPadding);
        button.setBackground(createFocusBackground(context, televisionUi));

        int minSize = dp(context, televisionUi ? TV_MIN_SIZE_DP : MIN_SIZE_DP);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        params.gravity = Gravity.TOP | Gravity.END;
        int margin = dp(context, EDGE_MARGIN_DP);
        params.setMargins(margin, margin, margin, margin);
        button.setMinimumWidth(minSize);
        button.setMinimumHeight(minSize);
        button.setLayoutParams(params);
        button.setImportantForAccessibility(android.view.View.IMPORTANT_FOR_ACCESSIBILITY_YES);

        container.addView(button);
        VideoInterstitialOverlay overlay = new VideoInterstitialOverlay(container, button);
        button.setOnClickListener(v -> overlay.handleClick());
        button.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() != android.view.KeyEvent.ACTION_UP) {
                return false;
            }
            if (keyCode == android.view.KeyEvent.KEYCODE_DPAD_CENTER
                    || keyCode == android.view.KeyEvent.KEYCODE_ENTER
                    || keyCode == android.view.KeyEvent.KEYCODE_NUMPAD_ENTER) {
                overlay.handleClick();
                return true;
            }
            return false;
        });
        overlay.setActionArmed(false);
        return overlay;
    }

    public void setCloseButtonDismissesAd(boolean dismisses) {
        this.closeButtonDismissesAd = dismisses;
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    /**
     * Applies BidsCube skip schedule: permission is separate from availability time.
     * {@code availableAfterSeconds == 0} arms Skip immediately (VAST {@code skipoffset="00:00:00"}).
     */
    public void applySkipSchedule(boolean skipAllowed, int availableAfterSeconds) {
        if (!skipAllowed) {
            hide();
            return;
        }
        if (availableAfterSeconds <= 0) {
            enableSkip();
            return;
        }
        startSkipCountdown(availableAfterSeconds);
    }

    public void startSkipCountdown(int skipOffsetSeconds) {
        stopCountdown();
        skipEnabled = false;
        endCardMode = false;
        remainingSeconds = Math.max(skipOffsetSeconds, 0);
        // Countdown mode always means skip will be offered (hasSkipOffset=true).
        // availableAfterSeconds==0 is handled by applySkipSchedule → enableSkip().
        if (remainingSeconds <= 0) {
            enableSkip();
            return;
        }
        hasSkipOffset = true;
        updateCountdownLabel();
        setActionArmed(false);
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
        setActionArmed(true);
        requestActionFocus();
    }

    public void showCloseOnly() {
        stopCountdown();
        skipEnabled = false;
        endCardMode = false;
        hasSkipOffset = false;
        actionButton.setText("\u2715");
        actionButton.setVisibility(android.view.View.VISIBLE);
        setActionArmed(true);
        requestActionFocus();
    }

    public void showEndCardClose() {
        stopCountdown();
        skipEnabled = false;
        endCardMode = true;
        hasSkipOffset = false;
        actionButton.setText("\u2715");
        actionButton.setVisibility(android.view.View.VISIBLE);
        setActionArmed(true);
        requestActionFocus();
    }

    public void hide() {
        stopCountdown();
        actionButton.setVisibility(android.view.View.GONE);
        setActionArmed(false);
    }

    public void detach() {
        stopCountdown();
        container.removeView(actionButton);
    }

    public void bringToFront() {
        actionButton.bringToFront();
    }

    /** Re-request focus after a nested confirm dialog is cancelled. */
    public void restoreRemoteFocus() {
        bringToFront();
        if (actionArmed) {
            requestActionFocus();
        }
    }

    public boolean isEndCardMode() {
        return endCardMode;
    }

    public boolean isSkipEnabled() {
        return skipEnabled;
    }

    /** True when Back / OK may close the ad (close-only or end-card close). */
    public boolean canCloseViaRemote() {
        if (!actionArmed || actionButton.getVisibility() != android.view.View.VISIBLE) {
            return false;
        }
        if (endCardMode) {
            return closeButtonDismissesAd;
        }
        // Close-only mode: ✕ closes only when configured to dismiss (not gamified advance-as-skip).
        return !skipEnabled && !hasSkipOffset && closeButtonDismissesAd;
    }

    /** True when Back / OK may skip the current linear creative. */
    public boolean canSkipViaRemote() {
        if (!actionArmed || actionButton.getVisibility() != android.view.View.VISIBLE) {
            return false;
        }
        if (skipEnabled) {
            return true;
        }
        // Gamified intro: ✕ advances via skip when close does not dismiss the ad.
        return !hasSkipOffset && !skipEnabled && !endCardMode && !closeButtonDismissesAd;
    }

    private void handleClick() {
        if (listener == null || !actionArmed) {
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

    private void setActionArmed(boolean armed) {
        actionArmed = armed;
        actionButton.setClickable(armed);
        actionButton.setFocusable(armed);
        actionButton.setFocusableInTouchMode(armed);
        if (!armed && actionButton.isFocused()) {
            actionButton.clearFocus();
        }
    }

    private void requestActionFocus() {
        actionButton.post(() -> {
            actionButton.bringToFront();
            if (actionArmed && !actionButton.isFocused()) {
                actionButton.requestFocus();
            }
        });
    }

    private void stopCountdown() {
        if (countdownTask != null) {
            handler.removeCallbacks(countdownTask);
            countdownTask = null;
        }
    }

    private static StateListDrawable createFocusBackground(Context context, boolean televisionUi) {
        GradientDrawable focused = new GradientDrawable();
        focused.setColor(0xCC1565C0);
        focused.setCornerRadius(dp(context, televisionUi ? 24 : 18));
        focused.setStroke(dp(context, televisionUi ? 3 : 2), Color.WHITE);

        GradientDrawable normal = new GradientDrawable();
        normal.setColor(0x99000000);
        normal.setCornerRadius(dp(context, televisionUi ? 24 : 18));

        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_focused}, focused);
        states.addState(new int[]{android.R.attr.state_pressed}, focused);
        states.addState(new int[]{}, normal);
        return states;
    }

    private static int dp(Context context, int value) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, value, context.getResources().getDisplayMetrics());
    }
}
