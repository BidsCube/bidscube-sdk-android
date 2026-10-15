package com.bidscube.sdk.view;

import android.app.Activity;
import android.app.AlertDialog;
import android.view.Window;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Confirms early exit from rewarded video (lose reward). Cancel restores focus to the ad UI.
 */
public final class RewardedExitConfirm {

    private RewardedExitConfirm() {
    }

    public static void show(
            @NonNull Activity activity,
            @NonNull Runnable onExitWithoutReward,
            @Nullable Runnable onStay) {
        if (activity.isFinishing()) {
            return;
        }
        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle("Leave video?")
                .setMessage("If you leave now, you will not receive a reward.")
                .setPositiveButton("Leave", (d, which) -> onExitWithoutReward.run())
                .setNegativeButton("Keep watching", (d, which) -> {
                    if (onStay != null) {
                        onStay.run();
                    }
                })
                .setOnCancelListener(d -> {
                    if (onStay != null) {
                        onStay.run();
                    }
                })
                .create();
        dialog.setCanceledOnTouchOutside(false);
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            Button negative = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
            if (negative != null) {
                negative.setFocusable(true);
                negative.requestFocus();
            }
        }
    }
}
