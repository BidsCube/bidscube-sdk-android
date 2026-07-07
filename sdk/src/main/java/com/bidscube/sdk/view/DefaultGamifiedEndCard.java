package com.bidscube.sdk.view;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.bidscube.sdk.models.video.GamifiedEndCardConfig;
import com.bidscube.sdk.utils.SDKLogger;

/**
 * Default gamified end card: preview via {@link VideoInterstitialUiHelper} when available,
 * otherwise a simple reward claim screen.
 */
public final class DefaultGamifiedEndCard implements GamifiedEndCard {

    private static final String TAG = "GamifiedEndCard";

    private final Context context;
    private final FrameLayout container;
    @Nullable
    private final BidscubeVideoAdPlayer player;
    @Nullable
    private final VideoInterstitialOverlay overlay;
    @Nullable
    private View rewardView;
    private Listener listener;

    public DefaultGamifiedEndCard(
            Context context,
            FrameLayout container,
            @Nullable BidscubeVideoAdPlayer player,
            @Nullable VideoInterstitialOverlay overlay) {
        this.context = context;
        this.container = container;
        this.player = player;
        this.overlay = overlay;
    }

    @Override
    public void show(GamifiedEndCardConfig config, Listener listener) {
        this.listener = listener;
        if (config == null) {
            SDKLogger.d(TAG, "No end card config");
            if (listener != null) {
                listener.onCloseRequested();
            }
            return;
        }
        if (config.hasPreviewImage()) {
            VideoInterstitialUiHelper.showEndCard(
                    context,
                    player,
                    container,
                    overlay,
                    config.getPreviewImageUrl(),
                    config.getClickUrl(),
                    config.getCtaText(),
                    config.getTitle(),
                    4.5f,
                    "10 k",
                    "FREE",
                    () -> {
                        if (this.listener != null) {
                            this.listener.onEndCardShown();
                        }
                    },
                    () -> {
                        if (this.listener != null) {
                            this.listener.onCtaClicked(config.getClickUrl());
                        }
                    },
                    () -> {
                        if (this.listener != null) {
                            this.listener.onCloseRequested();
                        }
                    });
            return;
        }
        showSimpleRewardCard(config);
    }

    @Override
    public void hide() {
        if (rewardView != null) {
            container.removeView(rewardView);
            rewardView = null;
        }
    }

    @Override
    public void destroy() {
        hide();
        listener = null;
    }

    private void showSimpleRewardCard(GamifiedEndCardConfig config) {
        if (player != null) {
            try {
                player.release();
            } catch (Throwable ignored) {
            }
        }
        container.removeAllViews();
        container.setBackgroundColor(0xFF1A1A2E);

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(dp(24), dp(48), dp(24), dp(24));
        root.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        TextView title = new TextView(context);
        title.setText(config.getTitle() != null ? config.getTitle() : "Reward unlocked!");
        title.setTextColor(Color.WHITE);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER);
        root.addView(title);

        if (config.getDescription() != null && !config.getDescription().isEmpty()) {
            TextView desc = new TextView(context);
            desc.setText(config.getDescription());
            desc.setTextColor(0xCCFFFFFF);
            desc.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            desc.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams descParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            descParams.topMargin = dp(12);
            desc.setLayoutParams(descParams);
            root.addView(desc);
        }

        ProgressBar progress = new ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal);
        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(8));
        progressParams.topMargin = dp(32);
        progress.setMax(100);
        progress.setProgress(100);
        progress.setLayoutParams(progressParams);
        root.addView(progress);

        TextView rewardLabel = new TextView(context);
        rewardLabel.setText(config.getRewardLabel() != null ? config.getRewardLabel() : "Tap to claim your reward");
        rewardLabel.setTextColor(0xFFFFD54F);
        rewardLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        rewardLabel.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams rewardParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        rewardParams.topMargin = dp(16);
        rewardLabel.setLayoutParams(rewardParams);
        root.addView(rewardLabel);

        TextView cta = new TextView(context);
        String ctaText = config.getCtaText() != null ? config.getCtaText() : "Claim reward";
        cta.setText(ctaText);
        cta.setTextColor(Color.WHITE);
        cta.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        cta.setTypeface(Typeface.DEFAULT_BOLD);
        cta.setGravity(Gravity.CENTER);
        cta.setPadding(dp(36), dp(14), dp(36), dp(14));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFF6C63FF);
        bg.setCornerRadius(dp(28));
        cta.setBackground(bg);
        LinearLayout.LayoutParams ctaParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        ctaParams.topMargin = dp(28);
        cta.setLayoutParams(ctaParams);
        cta.setOnClickListener(v -> {
            if (listener != null) {
                listener.onCtaClicked(config.getClickUrl());
            }
        });
        root.addView(cta);

        rewardView = root;
        container.addView(root);

        VideoInterstitialOverlay endOverlay = VideoInterstitialOverlay.attach(container);
        endOverlay.showEndCardClose();
        endOverlay.setListener(new VideoInterstitialOverlay.Listener() {
            @Override
            public void onSkipRequested() {
            }

            @Override
            public void onCloseRequested() {
                if (DefaultGamifiedEndCard.this.listener != null) {
                    DefaultGamifiedEndCard.this.listener.onCloseRequested();
                }
            }
        });

        if (overlay != null) {
            try {
                overlay.detach();
            } catch (Throwable ignored) {
            }
        }

        if (listener != null) {
            listener.onEndCardShown();
        }
        SDKLogger.d(TAG, "Simple gamified end card shown");
    }

    private int dp(int value) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, value, context.getResources().getDisplayMetrics());
    }
}
