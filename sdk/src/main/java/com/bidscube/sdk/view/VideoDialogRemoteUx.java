package com.bidscube.sdk.view;

import android.app.Activity;
import android.app.Dialog;
import android.view.KeyEvent;
import android.view.Window;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

/**
 * Remote / Back key policy for video interstitial dialogs (phone + TV).
 *
 * <ul>
 *   <li>Back while locked: interstitial consumes; rewarded offers exit-without-reward confirm</li>
 *   <li>Back when Skip is enabled (interstitial only) → skip</li>
 *   <li>Rewarded never skips via Skip/Back — confirm → abandon without reward</li>
 *   <li>Back when Close / end-card Close is shown → close</li>
 * </ul>
 */
public final class VideoDialogRemoteUx {

    public interface BackActions {
        void onSkipFromBack();

        void onCloseFromBack();

        /** Early exit while Skip is still locked (rewarded abandon). */
        void onAbandonFromBack();
    }

    /** Optional pause/resume around rewarded exit confirm. */
    public interface PlaybackControl {
        void pauseForConfirm();

        void resumeAfterConfirm();
    }

    private VideoDialogRemoteUx() {
    }

    public static void install(
            @NonNull Dialog dialog,
            @NonNull VideoInterstitialOverlay overlay,
            @NonNull BackActions actions,
            boolean fullscreen) {
        install(dialog, overlay, actions, fullscreen, false, null, null);
    }

    public static void install(
            @NonNull Dialog dialog,
            @NonNull VideoInterstitialOverlay overlay,
            @NonNull BackActions actions,
            boolean fullscreen,
            boolean rewarded,
            @Nullable Activity activity) {
        install(dialog, overlay, actions, fullscreen, rewarded, activity, null);
    }

    public static void install(
            @NonNull Dialog dialog,
            @NonNull VideoInterstitialOverlay overlay,
            @NonNull BackActions actions,
            boolean fullscreen,
            boolean rewarded,
            @Nullable Activity activity,
            @Nullable PlaybackControl playback) {
        dialog.setCancelable(false);
        dialog.setCanceledOnTouchOutside(false);

        if (fullscreen) {
            Window window = dialog.getWindow();
            if (window != null) {
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                window.setFlags(
                        WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                        WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED);
            }
        }

        dialog.setOnKeyListener((d, keyCode, event) -> {
            if (keyCode != KeyEvent.KEYCODE_BACK) {
                return false;
            }
            if (event.getAction() != KeyEvent.ACTION_UP) {
                return true;
            }
            return dispatchBack(
                    overlay.canCloseViaRemote(),
                    overlay.canSkipViaRemote(),
                    rewarded,
                    actions,
                    activity,
                    overlay,
                    playback);
        });
    }

    @VisibleForTesting
    static boolean dispatchBack(
            boolean canClose,
            boolean canSkip,
            @Nullable BackActions actions) {
        return dispatchBack(canClose, canSkip, false, actions, null, null, null);
    }

    @VisibleForTesting
    static boolean dispatchBack(
            boolean canClose,
            boolean canSkip,
            boolean rewarded,
            @Nullable BackActions actions,
            @Nullable Activity activity,
            @Nullable VideoInterstitialOverlay overlay) {
        return dispatchBack(canClose, canSkip, rewarded, actions, activity, overlay, null);
    }

    @VisibleForTesting
    static boolean dispatchBack(
            boolean canClose,
            boolean canSkip,
            boolean rewarded,
            @Nullable BackActions actions,
            @Nullable Activity activity,
            @Nullable VideoInterstitialOverlay overlay,
            @Nullable PlaybackControl playback) {
        if (canClose) {
            if (actions != null) {
                actions.onCloseFromBack();
            }
            return true;
        }
        // Rewarded: never skip — locked and "skippable" both open abandon confirm.
        if (rewarded) {
            confirmRewardedAbandon(actions, activity, overlay, playback);
            return true;
        }
        if (canSkip) {
            requestSkip(false, actions, activity, overlay, playback);
            return true;
        }
        return true;
    }

    /**
     * Skip button entry. Rewarded never skips — confirms abandon without reward instead.
     */
    public static void requestSkip(
            boolean rewarded,
            @Nullable BackActions actions,
            @Nullable Activity activity,
            @Nullable VideoInterstitialOverlay overlay) {
        requestSkip(rewarded, actions, activity, overlay, null);
    }

    public static void requestSkip(
            boolean rewarded,
            @Nullable BackActions actions,
            @Nullable Activity activity,
            @Nullable VideoInterstitialOverlay overlay,
            @Nullable PlaybackControl playback) {
        if (actions == null) {
            return;
        }
        if (rewarded) {
            confirmRewardedAbandon(actions, activity, overlay, playback);
            return;
        }
        actions.onSkipFromBack();
    }

    private static void confirmRewardedAbandon(
            @Nullable BackActions actions,
            @Nullable Activity activity,
            @Nullable VideoInterstitialOverlay overlay,
            @Nullable PlaybackControl playback) {
        if (actions == null || activity == null) {
            return;
        }
        if (playback != null) {
            playback.pauseForConfirm();
        }
        RewardedExitConfirm.show(
                activity,
                () -> {
                    // Leave: do not resume — session is closing.
                    actions.onAbandonFromBack();
                },
                () -> {
                    if (playback != null) {
                        playback.resumeAfterConfirm();
                    }
                    if (overlay != null) {
                        overlay.restoreRemoteFocus();
                    }
                });
    }
}
