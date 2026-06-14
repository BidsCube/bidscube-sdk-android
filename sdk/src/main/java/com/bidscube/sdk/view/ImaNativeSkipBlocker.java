package com.bidscube.sdk.view;

import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Covers IMA's native "Skip Ad" control rendered in the bottom corner during playback.
 * Must be attached to the same parent as the player so it sits above IMA UI layers.
 */
public final class ImaNativeSkipBlocker {

    private static final int BLOCKER_WIDTH_DP = 240;
    private static final int BLOCKER_HEIGHT_DP = 96;

    private final FrameLayout container;
    private final View blocker;
    private final Handler handler = new Handler(Looper.getMainLooper());
    @Nullable
    private VideoInterstitialOverlay overlayRef;
    private final Runnable keepOnTopTask = new Runnable() {
        @Override
        public void run() {
            if (blocker.getVisibility() != View.VISIBLE) {
                return;
            }
            blocker.bringToFront();
            if (overlayRef != null) {
                overlayRef.bringToFront();
            }
            handler.postDelayed(this, 200L);
        }
    };

    private ImaNativeSkipBlocker(FrameLayout container, View blocker) {
        this.container = container;
        this.blocker = blocker;
    }

    public static ImaNativeSkipBlocker attach(@NonNull FrameLayout container) {
        View blocker = new View(container.getContext());
        blocker.setBackgroundColor(0xFF000000);
        blocker.setClickable(true);
        blocker.setFocusable(true);
        blocker.setElevation(dp(container, 24));
        blocker.setVisibility(View.GONE);

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                dp(container, BLOCKER_WIDTH_DP),
                dp(container, BLOCKER_HEIGHT_DP));
        params.gravity = Gravity.BOTTOM | Gravity.END;
        blocker.setLayoutParams(params);

        container.addView(blocker);
        return new ImaNativeSkipBlocker(container, blocker);
    }

    public void show(@NonNull VideoInterstitialOverlay overlay) {
        overlayRef = overlay;
        blocker.setVisibility(View.VISIBLE);
        blocker.bringToFront();
        overlay.bringToFront();
        handler.removeCallbacks(keepOnTopTask);
        handler.post(keepOnTopTask);
    }

    public void hide() {
        handler.removeCallbacks(keepOnTopTask);
        blocker.setVisibility(View.GONE);
        overlayRef = null;
    }

    public void detach() {
        hide();
        container.removeView(blocker);
    }

    private static int dp(View view, int value) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                value,
                view.getResources().getDisplayMetrics());
    }
}
