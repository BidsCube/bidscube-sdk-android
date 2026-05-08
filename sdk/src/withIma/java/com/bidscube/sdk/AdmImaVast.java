package com.bidscube.sdk;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.bidscube.sdk.config.VideoPlayerProvider;
import com.bidscube.sdk.interfaces.AdCallback;
import com.bidscube.sdk.models.enums.AdPosition;
import com.bidscube.sdk.network.BidscubeResponse;
import com.bidscube.sdk.utils.VastParser;
import com.bidscube.sdk.utils.SDKLogger;
import com.bidscube.sdk.view.IMAPlayerHandler;
import com.google.android.material.imageview.ShapeableImageView;
import com.google.android.material.shape.CornerFamily;
import com.bumptech.glide.Glide;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import com.bidscube.sdk.ads.AdType;

/**
 * Google IMA / VAST implementation (only in {@code withIma} build variant).
 */
final class AdmImaVast {

    private AdmImaVast() {}

    static void onVideoAdResponse(AdDisplayManager m, String placementId, BidscubeResponse responseBody, AdCallback callback) {
        ((Activity) m.context).runOnUiThread(() -> {
            m.setResponseAdPosition(responseBody.getPosition());
            AdPosition effectivePosition = m.getEffectiveAdPosition();

            SDKLogger.d(AdDisplayManager.class.getSimpleName(), "Video ad response position: " + responseBody.getPosition() + " -> "
                    + effectivePosition);

            final String adm = m.sanitizeAdm(responseBody.getAdm());

            if (m.handleRenderOverride(placementId, adm, effectivePosition, AdType.Type.VIDEO, callback)) {
                SDKLogger.d(AdDisplayManager.class.getSimpleName(), "Video ad rendering overridden by host app");
                return;
            }

            SDKLogger.v("VastResponse", adm);
            VastParser.analyzeVast(adm);
            String vastRedirectUrl = VastParser.getClickThroughUrl(adm);
            VideoPlayerProvider vpp = m.videoPlayerProvider;

            if (effectivePosition == AdPosition.FULL_SCREEN) {
                SDKLogger.d(AdDisplayManager.class.getSimpleName(), "Response indicates full screen display for video ad");

                Dialog dialog = new Dialog(m.context, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
                FrameLayout frameContainer = new FrameLayout(m.context);
                frameContainer.setLayoutParams(
                        new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

                IMAPlayerHandler videoPlayer = new IMAPlayerHandler(adm, vastRedirectUrl, m.context, vpp);
                videoPlayer.setLayoutParams(
                        new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

                Button closeBtn = new Button(m.context);
                closeBtn.setText("✕");
                closeBtn.setTextSize(16);
                closeBtn.setBackgroundColor(0xCCF44336);
                closeBtn.setTextColor(Color.WHITE);
                closeBtn.setPadding(12, 6, 12, 6);
                closeBtn.setOnClickListener(
                        v -> {
                            videoPlayer.release();
                            dialog.dismiss();
                        });

                FrameLayout.LayoutParams closeBtnParams = new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                closeBtnParams.gravity = Gravity.TOP | Gravity.END;
                closeBtnParams.setMargins(0, 20, 20, 0);
                closeBtn.setLayoutParams(closeBtnParams);

                frameContainer.addView(videoPlayer);
                frameContainer.addView(closeBtn);
                dialog.setContentView(frameContainer);
                m.centerFullScreenDialog(dialog, frameContainer);
                dialog.show();
                videoPlayer.playVast(adm, false);
                m.currentVideoPlayer = videoPlayer;
                SDKLogger.d(AdDisplayManager.class.getSimpleName(), "Video ad displayed fullscreen with position: "
                        + responseBody.getPosition() + " -> " + effectivePosition);
            } else {
                SDKLogger.d(AdDisplayManager.class.getSimpleName(), "Response indicates windowed display for video ad");
                Dialog dialog = new Dialog(m.context);
                String positionName = m.getPositionDisplayName();
                dialog.setTitle("Video Ad - " + positionName);

                FrameLayout frameContainer = new FrameLayout(m.context);
                frameContainer.setLayoutParams(
                        new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

                IMAPlayerHandler videoPlayer = new IMAPlayerHandler(adm, vastRedirectUrl, m.context, vpp);
                int heightPx = (int) TypedValue.applyDimension(
                        TypedValue.COMPLEX_UNIT_DIP, 300, m.context.getResources().getDisplayMetrics());
                videoPlayer.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, heightPx));

                Button closeBtn = new Button(m.context);
                closeBtn.setText("✕");
                closeBtn.setTextSize(16);
                closeBtn.setBackgroundColor(0xCCF44336);
                closeBtn.setTextColor(Color.WHITE);
                closeBtn.setPadding(12, 6, 12, 6);
                closeBtn.setOnClickListener(
                        v -> {
                            videoPlayer.release();
                            dialog.dismiss();
                        });

                FrameLayout.LayoutParams closeBtnParams2 = new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                closeBtnParams2.gravity = Gravity.TOP | Gravity.END;
                closeBtnParams2.setMargins(0, 20, 20, 0);
                closeBtn.setLayoutParams(closeBtnParams2);

                frameContainer.addView(videoPlayer);
                frameContainer.addView(closeBtn);
                dialog.setContentView(frameContainer);

                Window window = dialog.getWindow();
                if (window != null) {
                    int dialogWidth = (int) (m.context.getResources().getDisplayMetrics().widthPixels * 0.8);
                    int dialogHeight = (int) (m.context.getResources().getDisplayMetrics().heightPixels * 0.7);
                    m.positionWindowedDialog(window, dialogWidth, dialogHeight);
                }

                dialog.show();
                videoPlayer.playVast(adm, false);
                m.currentVideoPlayer = videoPlayer;
                SDKLogger.d(AdDisplayManager.class.getSimpleName(), "Video ad displayed windowed with position: " + responseBody.getPosition() + " -> " + effectivePosition);
            }
        });
    }

    static void showSkippableFromFile(AdDisplayManager m, boolean isFullScreen) {
        String adm = loadVastFromRaw(m.context, R.raw.vast);
        SDKLogger.v("VastResponse", adm);
        VastParser.analyzeVast(adm);
        String vastRedirectUrl = VastParser.getClickThroughUrl(adm);
        String companionImageUrl = VastParser.getCompanionImageUrl(adm);

        Dialog dialog;
        if (isFullScreen) {
            dialog = new Dialog(m.context, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        } else {
            dialog = new Dialog(m.context);
            String positionName = m.getPositionDisplayName();
            dialog.setTitle("Skippable Video Ad - " + positionName);
        }

        FrameLayout mainContainer = new FrameLayout(m.context);
        mainContainer.setLayoutParams(
                new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        IMAPlayerHandler videoPlayer = new IMAPlayerHandler(adm, vastRedirectUrl, m.context, m.videoPlayerProvider);
        if (isFullScreen) {
            videoPlayer.setLayoutParams(
                    new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        } else {
            int heightPx = (int) TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP, 300, m.context.getResources().getDisplayMetrics());
            videoPlayer.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, heightPx));
        }

        mainContainer.addView(videoPlayer);
        dialog.setContentView(mainContainer);
        dialog.show();

        videoPlayer.setOnVideoCompletionListener(
                new IMAPlayerHandler.OnVideoCompletionListener() {
                    @Override
                    public void onVideoCompleted() {
                        showFinalAdScreen(m, videoPlayer, mainContainer, companionImageUrl, vastRedirectUrl);
                    }

                    @Override
                    public void onVideoSkipped() {
                        showFinalAdScreen(m, videoPlayer, mainContainer, companionImageUrl, vastRedirectUrl);
                    }
                });
        videoPlayer.playVast(adm, false);
    }

    private static void showFinalAdScreen(AdDisplayManager m, IMAPlayerHandler player, FrameLayout container, String imageUrl, String clickUrl) {
        player.release();

        final Dialog parentDialog = container.getParent() instanceof Dialog ? (Dialog) container.getParent() : null;

        container.removeAllViews();
        container.setBackgroundColor(Color.BLACK);

        LinearLayout layout = new LinearLayout(m.context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER_HORIZONTAL);
        FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        layoutParams.gravity = Gravity.CENTER;
        layout.setLayoutParams(layoutParams);

        ShapeableImageView adImage = new ShapeableImageView(m.context);
        int sizePx = (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 128, m.context.getResources().getDisplayMetrics());
        LinearLayout.LayoutParams imageParams = new LinearLayout.LayoutParams(sizePx, sizePx);
        imageParams.gravity = Gravity.CENTER_HORIZONTAL;
        adImage.setLayoutParams(imageParams);

        float cornerRadius = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 16, m.context.getResources().getDisplayMetrics());
        adImage.setShapeAppearanceModel(
                adImage.getShapeAppearanceModel().toBuilder().setAllCorners(CornerFamily.ROUNDED, cornerRadius).build());

        if (imageUrl != null && !imageUrl.isEmpty()) {
            Glide.with(m.context).load(imageUrl).into(adImage);
        } else {
            adImage.setImageResource(android.R.drawable.ic_menu_report_image);
        }

        Button installBtn = new Button(m.context);
        installBtn.setText("Install");
        installBtn.setTextSize(18);
        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        btnParams.gravity = Gravity.CENTER_HORIZONTAL;
        btnParams.topMargin = (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 16, m.context.getResources().getDisplayMetrics());
        installBtn.setLayoutParams(btnParams);

        installBtn.setOnClickListener(
                v -> {
                    if (clickUrl != null && !clickUrl.isEmpty()) {
                        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(clickUrl));
                        m.context.startActivity(intent);
                    }
                });

        layout.addView(adImage);
        layout.addView(installBtn);
        container.addView(layout);

        ImageButton closeBtn = new ImageButton(m.context);
        closeBtn.setImageResource(R.drawable.close_small_24);
        closeBtn.setBackgroundColor(Color.TRANSPARENT);
        FrameLayout.LayoutParams closeParams = new FrameLayout.LayoutParams(
                (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 24, m.context.getResources().getDisplayMetrics()),
                (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 24, m.context.getResources().getDisplayMetrics()));
        closeParams.gravity = Gravity.TOP | Gravity.END;
        closeParams.setMargins(
                0,
                (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 16, m.context.getResources().getDisplayMetrics()),
                (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 16, m.context.getResources().getDisplayMetrics()),
                0);
        closeBtn.setLayoutParams(closeParams);

        closeBtn.setOnClickListener(
                v -> {
                    if (parentDialog != null) {
                        parentDialog.dismiss();
                    } else {
                        container.removeAllViews();
                    }
                });

        container.addView(closeBtn);
    }

    static void appendGetVideoAdView(AdDisplayManager m, String placementId, String adm, LinearLayout adContainer, AdCallback callback) {
        SDKLogger.v("VastResponse", adm);
        VastParser.analyzeVast(adm);
        String vastRedirectUrl = VastParser.getClickThroughUrl(adm);

        IMAPlayerHandler videoPlayer = new IMAPlayerHandler(adm, vastRedirectUrl, m.context, m.videoPlayerProvider);
        int heightPx = (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 300, m.context.getResources().getDisplayMetrics());
        videoPlayer.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, heightPx));

        Button playButton = new Button(m.context);
        playButton.setText("▶ PLAY VIDEO AD");
        playButton.setTextSize(16);
        playButton.setBackgroundColor(Color.parseColor("#FF5722"));
        playButton.setTextColor(Color.WHITE);
        playButton.setPadding(16, 8, 16, 8);
        playButton.setOnClickListener(
                v -> {
                    videoPlayer.playVast(adm, false);
                    playButton.setVisibility(View.GONE);
                });

        adContainer.addView(videoPlayer);
        adContainer.addView(playButton);

        if (callback != null) {
            callback.onAdLoaded(placementId);
        }
        SDKLogger.d(AdDisplayManager.class.getSimpleName(), "Video ad view created and integrated into container");
    }

    private static String loadVastFromRaw(Context context, int resId) {
        StringBuilder builder = new StringBuilder();
        try (InputStream inputStream = context.getResources().openRawResource(resId);
                BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line).append("\n");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return builder.toString();
    }
}
