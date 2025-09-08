package com.bidscube.sdk;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.VideoView;

import androidx.media3.common.util.UnstableApi;

import com.bidscube.sdk.ads.VideoAdType;
import com.bidscube.sdk.interfaces.AdCallback;
import com.bidscube.sdk.models.enums.AdPosition;
import com.bidscube.sdk.httpProvider.HttpProvider;

import com.bidscube.sdk.models.DeviceInfo;
import com.bidscube.sdk.models.natives.NativeAd;
import com.bidscube.sdk.network.BidscubeCallback;
import com.bidscube.sdk.network.BidscubeResponse;
import com.bidscube.sdk.network.NativeAdParser;
import com.bidscube.sdk.utils.VastParser;
import com.bidscube.sdk.utils.SDKLogger;
import com.bidscube.sdk.view.BannerViewFactory;
import com.bidscube.sdk.view.IMAPlayerHandler;
import com.bidscube.sdk.view.NativeAdView;
import com.bumptech.glide.Glide;
import com.google.android.material.imageview.ShapeableImageView;
import com.google.android.material.shape.CornerFamily;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;

import android.os.Handler;

/**
 * Manages the display of different ad types in both full screen and windowed
 * modes
 * <p>
 * Windowed Ad Positioning Behavior:
 * - When a specific position is selected from the spinner (ABOVE_THE_FOLD,
 * BELOW_THE_FOLD,
 * HEADER, FOOTER, SIDEBAR), the ad will be positioned accordingly
 * - When "Unknown" or nothing is selected, ads will display without any
 * alignment or
 * position regulation (natural display)
 * <p>
 * Supported Ad Types:
 * - Image Ads: createImageAdViewWithCloseButton()
 * - Video Ads: showVideoAdWindowedInternal()
 * - Native Ads: showNativeAdWindowed()
 */
@UnstableApi
public class AdDisplayManager {

    private static final String TAG = "AdDisplayManager";
    private final Context context;
    private final DeviceInfo deviceInfo;

    private WebView currentBanner = null;
    private IMAPlayerHandler currentVideoPlayer = null;
    private NativeAdView currentNativeAd = null;

    private FrameLayout overlayContainer;

    private AdPosition currentAdPosition = AdPosition.UNKNOWN;
    private AdPosition responseAdPosition = AdPosition.UNKNOWN;

    public AdDisplayManager(Context context, DeviceInfo deviceInfo) {
        this.context = context;
        this.deviceInfo = deviceInfo;
    }

    /**
     * Sets the current ad position for windowed ads (manual override)
     */
    public void setAdPosition(AdPosition position) {
        this.currentAdPosition = position;
        SDKLogger.d(TAG, "Manual ad position set to: " + position);
    }

    /**
     * Sets the ad position from response
     */
    public void setResponseAdPosition(int responsePosition) {
        this.responseAdPosition = AdPosition.fromResponseValue(responsePosition);
        SDKLogger.d(TAG,
                "Response ad position set to: " + this.responseAdPosition + " (value: " + responsePosition + ")");
    }

    /**
     * Gets the effective ad position (response position takes precedence)
     */
    public AdPosition getEffectiveAdPosition() {
        if (responseAdPosition != AdPosition.UNKNOWN) {
            return responseAdPosition;
        }
        return currentAdPosition;
    }

    /**
     * Gets the current ad position (manual override)
     */
    public AdPosition getCurrentAdPosition() {
        return currentAdPosition;
    }

    /**
     * Gets the response ad position
     */
    public AdPosition getResponseAdPosition() {
        return responseAdPosition;
    }

    /**
     * Helper method to send HTTP requests and parse responses into BidscubeResponse
     * Reduces code duplication across ad display methods
     */
    private void sendAdRequest(String url, BidscubeCallback callback) {
        HttpProvider.sendGetRequest(url, callback);
    }

    /**
     * Checks if positioning should be applied based on current selection
     *
     * @return true if positioning should be applied, false if no regulation needed
     */
    public boolean shouldApplyPositioning() {
        AdPosition effectivePosition = getEffectiveAdPosition();
        return effectivePosition != AdPosition.UNKNOWN && effectivePosition != AdPosition.FULL_SCREEN;
    }

    /**
     * Gets a human-readable description of the current positioning behavior
     *
     * @return String describing the current positioning behavior
     */
    public String getPositioningDescription() {
        AdPosition effectivePosition = getEffectiveAdPosition();
        if (shouldApplyPositioning()) {
            return "Positioning applied for: " + effectivePosition + " (from " +
                    (responseAdPosition != AdPosition.UNKNOWN ? "response" : "manual") + ")";
        } else {
            return "No position regulation - natural display";
        }
    }

    /**
     * Centers a full screen dialog content
     */
    private void centerFullScreenDialog(Dialog dialog, LinearLayout container) {

        container.setGravity(Gravity.CENTER);

        Window window = dialog.getWindow();
        if (window != null) {
            window.setGravity(Gravity.CENTER);
        }

        SDKLogger.d(TAG, "Centered full screen dialog content");
    }

    /**
     * Centers a full screen dialog content (FrameLayout version)
     */
    private void centerFullScreenDialog(Dialog dialog, FrameLayout container) {

        Window window = dialog.getWindow();
        if (window != null) {
            window.setGravity(Gravity.CENTER);
        }

        SDKLogger.d(TAG, "Centered full screen dialog content (FrameLayout)");
    }

    /**
     * Configures video player for full screen display
     */
    private void configureVideoPlayerForFullScreen(IMAPlayerHandler videoPlayer) {
        try {

            Field videoViewField = videoPlayer.getClass().getDeclaredField("videoView");
            videoViewField.setAccessible(true);
            VideoView videoView = (VideoView) videoViewField.get(videoPlayer);
            if (videoView != null) {

                videoView.setScaleX(1.0f);
                videoView.setScaleY(1.0f);

                FrameLayout.LayoutParams videoParams = new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT);
                videoParams.gravity = Gravity.CENTER;
                videoView.setLayoutParams(videoParams);

                SDKLogger.d(TAG, "Video player configured for full screen display");
            }
        } catch (Exception e) {
            SDKLogger.d(TAG, "Could not configure video player scaling: " + e.getMessage());
        }
    }

    /**
     * Gets the display name for the current position
     */
    private String getPositionDisplayName() {
        AdPosition effectivePosition = getEffectiveAdPosition();
        switch (effectivePosition) {
            case ABOVE_THE_FOLD:
                return "Above the Fold";
            case MAYBE_DEPENDING_ON_SCREEN_SIZE:
                return "Maybe Depending on Screen Size";
            case BELOW_THE_FOLD:
                return "Below the Fold";
            case HEADER:
                return "Header";
            case FOOTER:
                return "Footer";
            case SIDEBAR:
                return "Sidebar";
            case FULL_SCREEN:
                return "Full Screen";
            case UNKNOWN:
            default:
                return "Center";
        }
    }


    /**
     * Show image ad with display mode determined by response position
     * This method respects the position value from the ad response
     */
    void showImageAdWithResponsePosition(String url) {
        sendAdRequest(url, new BidscubeCallback() {
            @Override
            public void onSuccess(int responseCode, BidscubeResponse response) {
                ((Activity) context).runOnUiThread(() -> {

                    setResponseAdPosition(response.getPosition());
                    AdPosition effectivePosition = getEffectiveAdPosition();

                    SDKLogger.d(TAG,
                            "Image ad response position: " + response.getPosition() + " -> " + effectivePosition);

                    if (effectivePosition == AdPosition.FULL_SCREEN) {
                        SDKLogger.d(TAG, "Response indicates full screen display for image ad");


                        Dialog dialog = new Dialog(context, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
                        FrameLayout container = createImageAdViewWithCloseButton(response.getAdm(), dialog);
                        dialog.setContentView(container);
                        centerFullScreenDialog(dialog, container);
                        dialog.show();

                        SDKLogger.d(TAG,
                                "Image ad displayed fullscreen with position: " + response.getPosition() + " -> "
                                        + effectivePosition);
                    } else {
                        SDKLogger.d(TAG, "Response indicates windowed display for image ad");


                        Dialog dialog = new Dialog(context);
                        String positionName = getPositionDisplayName();
                        dialog.setTitle("Image Ad - " + positionName);

                        FrameLayout container = createImageAdViewWithCloseButton(response.getAdm(), dialog);
                        dialog.setContentView(container);

                        Window window = dialog.getWindow();
                        if (window != null) {
                            int dialogWidth = (int) (context.getResources().getDisplayMetrics().widthPixels * 0.8);
                            int dialogHeight = (int) (context.getResources().getDisplayMetrics().heightPixels * 0.6);
                            positionWindowedDialog(window, dialogWidth, dialogHeight);
                        }

                        dialog.show();

                        SDKLogger.d(TAG, "Image ad displayed windowed with position: " + response.getPosition() + " -> "
                                + effectivePosition);
                    }
                });
            }

            @Override
            public void onFail(Exception e) {
                SDKLogger.e(TAG, "Error loading image ad: " + e.getMessage());
            }
        });
    }

    /**
     * Creates image ad view
     */
    private View createImageAdView(String responseBody) {

        if (currentBanner != null) {
            currentBanner.destroy();
            currentBanner = null;
        }

        currentBanner = BannerViewFactory.createBanner(context, responseBody);

        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(16, 16, 16, 16);
        container.addView(currentBanner);

        return container;
    }

    /**
     * Creates image ad view with close button
     */
    private FrameLayout createImageAdViewWithCloseButton(String responseBody, Dialog dialog) {

        if (currentBanner != null) {
            currentBanner.destroy();
            currentBanner = null;
        }

        currentBanner = BannerViewFactory.createBanner(context, responseBody);

        FrameLayout container = new FrameLayout(context);
        container.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        final AdPosition position = getCurrentAdPosition();

        FrameLayout.LayoutParams bannerParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT);

        if (shouldApplyPositioning()) {
            switch (position) {
                case HEADER:
                    bannerParams.gravity = Gravity.TOP;
                    break;
                case FOOTER:
                    bannerParams.gravity = Gravity.BOTTOM;
                    break;
                default:
                    bannerParams.gravity = Gravity.CENTER;
                    break;
            }
        }

        bannerParams.setMargins(16, 16, 16, 16);
        container.addView(currentBanner, bannerParams);

        SDKLogger.d(TAG, "Image ad positioned: " + getPositioningDescription());

        return container;
    }

    /**
     * Show video ad with display mode determined by response position
     * This method respects the position value from the ad response
     */
    void showVideoAdWithResponsePosition(String url) {
        HttpProvider.sendGetRequest(url, new BidscubeCallback() {
            @Override
            public void onSuccess(int responseCode, BidscubeResponse responseBody) {
                ((Activity) context).runOnUiThread(() -> {
                    setResponseAdPosition(responseBody.getPosition());
                    AdPosition effectivePosition = getEffectiveAdPosition();

                    SDKLogger.d(TAG, "Video ad response position: " + responseBody.getPosition() + " -> "
                            + effectivePosition);

                    String adm = responseBody.getAdm();
                    SDKLogger.v("VastResponse", adm);
                    VastParser.analyzeVast(adm);
                    String vastRedirectUrl = VastParser.getClickThroughUrl(adm);

                    if (effectivePosition == AdPosition.FULL_SCREEN) {
                        SDKLogger.d(TAG, "Response indicates full screen display for video ad");


                        Dialog dialog = new Dialog(context, android.R.style.Theme_Black_NoTitleBar_Fullscreen);

                        FrameLayout frameContainer = new FrameLayout(context);
                        frameContainer.setLayoutParams(new LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT));

                        IMAPlayerHandler videoPlayer = new IMAPlayerHandler(adm, vastRedirectUrl, context);
                        videoPlayer.setLayoutParams(new FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT));

                        Button closeBtn = new Button(context);
                        closeBtn.setText("✕");
                        closeBtn.setTextSize(16);
                        closeBtn.setBackgroundColor(0xCCF44336);
                        closeBtn.setTextColor(Color.WHITE);
                        closeBtn.setPadding(12, 6, 12, 6);
                        closeBtn.setOnClickListener(v -> {
                            videoPlayer.release();
                            dialog.dismiss();
                        });

                        FrameLayout.LayoutParams closeBtnParams = new FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.WRAP_CONTENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT);
                        closeBtnParams.gravity = Gravity.TOP | Gravity.END;
                        closeBtnParams.setMargins(0, 20, 20, 0);
                        closeBtn.setLayoutParams(closeBtnParams);

                        frameContainer.addView(videoPlayer);
                        frameContainer.addView(closeBtn);
                        dialog.setContentView(frameContainer);
                        centerFullScreenDialog(dialog, frameContainer);
                        dialog.show();

                        videoPlayer.playVast(adm, false);
                        currentVideoPlayer = videoPlayer;

                        SDKLogger.d(TAG,
                                "Video ad displayed fullscreen with position: " + responseBody.getPosition()
                                        + " -> " + effectivePosition);
                    } else {
                        SDKLogger.d(TAG, "Response indicates windowed display for video ad");


                        Dialog dialog = new Dialog(context);
                        String positionName = getPositionDisplayName();
                        dialog.setTitle("Video Ad - " + positionName);

                        FrameLayout frameContainer = new FrameLayout(context);
                        frameContainer.setLayoutParams(new LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT));

                        IMAPlayerHandler videoPlayer = new IMAPlayerHandler(adm, vastRedirectUrl, context);
                        int heightPx = (int) TypedValue.applyDimension(
                                TypedValue.COMPLEX_UNIT_DIP, 300, context.getResources().getDisplayMetrics());
                        videoPlayer.setLayoutParams(new FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT, heightPx));

                        Button closeBtn = new Button(context);
                        closeBtn.setText("✕");
                        closeBtn.setTextSize(16);
                        closeBtn.setBackgroundColor(0xCCF44336);
                        closeBtn.setTextColor(Color.WHITE);
                        closeBtn.setPadding(12, 6, 12, 6);
                        closeBtn.setOnClickListener(v -> {
                            videoPlayer.release();
                            dialog.dismiss();
                        });

                        FrameLayout.LayoutParams closeBtnParams = new FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.WRAP_CONTENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT);
                        closeBtnParams.gravity = Gravity.TOP | Gravity.END;
                        closeBtnParams.setMargins(0, 20, 20, 0);
                        closeBtn.setLayoutParams(closeBtnParams);

                        frameContainer.addView(videoPlayer);
                        frameContainer.addView(closeBtn);
                        dialog.setContentView(frameContainer);

                        Window window = dialog.getWindow();
                        if (window != null) {
                            int dialogWidth = (int) (context.getResources().getDisplayMetrics().widthPixels * 0.8);
                            int dialogHeight = (int) (context.getResources().getDisplayMetrics().heightPixels
                                    * 0.7);
                            positionWindowedDialog(window, dialogWidth, dialogHeight);
                        }

                        dialog.show();
                        videoPlayer.playVast(adm, false);
                        currentVideoPlayer = videoPlayer;

                        SDKLogger.d(TAG, "Video ad displayed windowed with position: " + responseBody.getPosition()
                                + " -> " + effectivePosition);
                    }
                });
            }

            @Override
            public void onFail(Exception e) {
                SDKLogger.e(TAG, "Error loading video ad: " + e.getMessage());
            }
        });
    }

    /**
     * Shows skippable video ad in windowed mode with custom install button text
     *
     * @param installButtonText Custom text for the install button (e.g., "Install", "Shop Now", "Learn More")
     */
    public void showSkippableVideoAdWindowed(String installButtonText) {

        VideoAdType testAdType = new VideoAdType("19483");
        String url = testAdType.buildRequestUrl(deviceInfo).toString();
        showSkippableVideoAdFromFile(false);
    }

    /**
     * Shows skippable video ad in full screen mode with custom install button text
     *
     * @param installButtonText Custom text for the install button (e.g., "Install", "Shop Now", "Learn More")
     */
    public void showSkippableVideoAdFullScreen(String installButtonText) {

        VideoAdType testAdType = new VideoAdType("19483");
        String url = testAdType.buildRequestUrl(deviceInfo).toString();
        showSkippableVideoAdFromFile(true);
    }

//    /**
//     * Shows skippable video ad with skip button, exit button, and install button
//     *
//     * @param url          Video ad URL
//     * @param isFullScreen Whether to show in full screen mode
//     */
//    private void showSkippableVideoAd(String url, boolean isFullScreen) {
//        showSkippableVideoAd(url, isFullScreen);
//    }

//    /**
//     * Shows skippable video ad with custom install button text
//     *
//     * @param url               Video ad URL
//     * @param isFullScreen      Whether to show in full screen mode
//     * @param installButtonText Custom text for the install button
//     */

    private String loadVastFromRaw(Context context, int resId) {
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


    public void showSkippableVideoAdFromFile(boolean isFullScreen) {
        String adm = loadVastFromRaw(context, R.raw.vast);
        SDKLogger.v("VastResponse", adm);
        VastParser.analyzeVast(adm);
        String vastRedirectUrl = VastParser.getClickThroughUrl(adm);
        String companionImageUrl = VastParser.getCompanionImageUrl(adm);

        Dialog dialog;
        if (isFullScreen) {
            dialog = new Dialog(context, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        } else {
            dialog = new Dialog(context);
            String positionName = getPositionDisplayName();
            dialog.setTitle("Skippable Video Ad - " + positionName);
        }

        FrameLayout mainContainer = new FrameLayout(context);
        mainContainer.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        IMAPlayerHandler videoPlayer = new IMAPlayerHandler(adm, vastRedirectUrl, context);
        if (isFullScreen) {
            videoPlayer.setLayoutParams(new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));
        } else {
            int heightPx = (int) TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP, 300, context.getResources().getDisplayMetrics());
            videoPlayer.setLayoutParams(new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, heightPx));
        }

        mainContainer.addView(videoPlayer);
        dialog.setContentView(mainContainer);
        dialog.show();

        videoPlayer.setOnVideoCompletionListener(new IMAPlayerHandler.OnVideoCompletionListener() {
            @Override
            public void onVideoCompleted() {
                showFinalAdScreen(videoPlayer, mainContainer, companionImageUrl, vastRedirectUrl);
            }

            @Override
            public void onVideoSkipped() {
                showFinalAdScreen(videoPlayer, mainContainer, companionImageUrl, vastRedirectUrl);
            }
        });
        videoPlayer.playVast(adm, false);
    }

    private void showFinalAdScreen(IMAPlayerHandler player, FrameLayout container, String imageUrl, String clickUrl) {
        player.release();

        final Dialog parentDialog = container.getParent() instanceof Dialog ? (Dialog) container.getParent() : null;

        container.removeAllViews();
        container.setBackgroundColor(Color.BLACK);

        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER_HORIZONTAL);
        FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        layoutParams.gravity = Gravity.CENTER;
        layout.setLayoutParams(layoutParams);

        ShapeableImageView adImage = new ShapeableImageView(context);
        int sizePx = (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 128, context.getResources().getDisplayMetrics());
        LinearLayout.LayoutParams imageParams = new LinearLayout.LayoutParams(sizePx, sizePx);
        imageParams.gravity = Gravity.CENTER_HORIZONTAL;
        adImage.setLayoutParams(imageParams);

        float cornerRadius = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 16, context.getResources().getDisplayMetrics());
        adImage.setShapeAppearanceModel(
                adImage.getShapeAppearanceModel()
                        .toBuilder()
                        .setAllCorners(CornerFamily.ROUNDED, cornerRadius)
                        .build()
        );

        if (imageUrl != null && !imageUrl.isEmpty()) {
            Glide.with(context).load(imageUrl).into(adImage);
        } else {
            adImage.setImageResource(android.R.drawable.ic_menu_report_image);
        }

        Button installBtn = new Button(context);
        installBtn.setText("Install");
        installBtn.setTextSize(18);
        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        btnParams.gravity = Gravity.CENTER_HORIZONTAL;
        btnParams.topMargin = (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 16, context.getResources().getDisplayMetrics());
        installBtn.setLayoutParams(btnParams);

        installBtn.setOnClickListener(v -> {
            if (clickUrl != null && !clickUrl.isEmpty()) {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(clickUrl));
                context.startActivity(intent);
            }
        });

        layout.addView(adImage);
        layout.addView(installBtn);
        container.addView(layout);

        ImageButton closeBtn = new ImageButton(context);
        closeBtn.setImageResource(R.drawable.close_small_24);
        closeBtn.setBackgroundColor(Color.TRANSPARENT);
        FrameLayout.LayoutParams closeParams = new FrameLayout.LayoutParams(
                (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 24, context.getResources().getDisplayMetrics()),
                (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 24, context.getResources().getDisplayMetrics())
        );
        closeParams.gravity = Gravity.TOP | Gravity.END;
        closeParams.setMargins(0, (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 16, context.getResources().getDisplayMetrics()),
                (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 16, context.getResources().getDisplayMetrics()), 0);
        closeBtn.setLayoutParams(closeParams);

        closeBtn.setOnClickListener(v -> {
            if (parentDialog != null) {
                parentDialog.dismiss();
            } else {
                container.removeAllViews();
            }
        });

        container.addView(closeBtn);
    }


    /*private void showSkippableVideoAd(String url, boolean isFullScreen, String installButtonText) {
        HttpProvider.sendGetRequest(url, new BidscubeCallback() {
            @Override
            public void onSuccess(int responseCode, BidscubeResponse responseBody) {
                ((Activity) context).runOnUiThread(() -> {
                    if (context instanceof Activity) {
                        String adm = responseBody.getAdm();
                        SDKLogger.v("VastResponse", adm);
                        VastParser.analyzeVast(adm);
                        String vastRedirectUrl = VastParser.getClickThroughUrl(adm);

                        Dialog dialog;
                        if (isFullScreen) {
                            dialog = new Dialog(context, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
                        } else {
                            dialog = new Dialog(context);
                            String positionName = getPositionDisplayName();
                            dialog.setTitle("Skippable Video Ad - " + positionName);
                        }

                        FrameLayout mainContainer = new FrameLayout(context);
                        mainContainer.setLayoutParams(new FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT));

                        IMAPlayerHandler videoPlayer = new IMAPlayerHandler(adm, vastRedirectUrl, context);
                        if (isFullScreen) {
                            videoPlayer.setLayoutParams(new FrameLayout.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT));
                        } else {
                            int heightPx = (int) TypedValue.applyDimension(
                                    TypedValue.COMPLEX_UNIT_DIP, 300, context.getResources().getDisplayMetrics());
                            videoPlayer.setLayoutParams(new FrameLayout.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT, heightPx));
                        }

                        Button skipBtn = new Button(context);
                        skipBtn.setText("Skip Ad");
                        skipBtn.setTextSize(16);
                        skipBtn.setBackgroundColor(0x80FFFFFF);
                        skipBtn.setTextColor(Color.BLACK);
                        skipBtn.setPadding(16, 8, 16, 8);
                        skipBtn.setEnabled(false);
                        skipBtn.setAlpha(0.5f);

                        FrameLayout.LayoutParams skipBtnParams = new FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.WRAP_CONTENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT);
                        skipBtnParams.gravity = Gravity.BOTTOM | Gravity.END;
                        skipBtnParams.setMargins(0, 0, 20, 20);
                        skipBtn.setLayoutParams(skipBtnParams);

                        Button exitBtn = new Button(context);
                        exitBtn.setText("✕");
                        exitBtn.setTextSize(18);
                        exitBtn.setBackgroundColor(0xFFCCCCCC);
                        exitBtn.setTextColor(Color.BLACK);
                        exitBtn.setPadding(12, 8, 12, 8);
                        exitBtn.setVisibility(View.GONE);

                        FrameLayout.LayoutParams exitBtnParams = new FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.WRAP_CONTENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT);
                        exitBtnParams.gravity = Gravity.TOP | Gravity.END;
                        exitBtnParams.setMargins(0, 20, 20, 0);
                        exitBtn.setLayoutParams(exitBtnParams);

                        Button installBtn = new Button(context);
                        installBtn.setText(installButtonText);
                        installBtn.setTextSize(18);
                        installBtn.setBackgroundColor(Color.TRANSPARENT);
                        installBtn.setTextColor(Color.WHITE);
                        installBtn.setPadding(24, 12, 24, 12);
                        installBtn.setVisibility(View.GONE);

                        installBtn.setBackgroundResource(android.R.drawable.btn_default);
                        installBtn.setBackgroundColor(Color.TRANSPARENT);
                        installBtn.setBackgroundTintList(null);

                        FrameLayout.LayoutParams installBtnParams = new FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.WRAP_CONTENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT);
                        installBtnParams.gravity = Gravity.CENTER;
                        installBtn.setLayoutParams(installBtnParams);

                        mainContainer.addView(videoPlayer);
                        mainContainer.addView(skipBtn);
                        mainContainer.addView(exitBtn);
                        mainContainer.addView(installBtn);

                        dialog.setContentView(mainContainer);

                        if (!isFullScreen) {
                            Window window = dialog.getWindow();
                            if (window != null) {
                                int dialogWidth = (int) (context.getResources().getDisplayMetrics().widthPixels * 0.8);
                                int dialogHeight = (int) (context.getResources().getDisplayMetrics().heightPixels
                                        * 0.7);

                                SDKLogger.d(TAG,
                                        "Skippable video ad windowed - Current position: " + currentAdPosition);

                                if (shouldApplyPositioning()) {
                                    positionWindowedDialog(window, dialogWidth, dialogHeight);
                                    SDKLogger.d(TAG, "Skippable video ad: " + getPositioningDescription());
                                } else {
                                    window.setLayout(dialogWidth, dialogHeight);
                                    SDKLogger.d(TAG, "Skippable video ad: " + getPositioningDescription());
                                }
                            }
                        }

                        Handler handler = new Handler();
                        Runnable enableSkipRunnable = () -> {
                            skipBtn.setEnabled(true);
                            skipBtn.setAlpha(1.0f);
                            skipBtn.setBackgroundColor(0xFFFF5722);
                            SDKLogger.d(TAG, "Skip button activated after 5 seconds");
                        };
                        handler.postDelayed(enableSkipRunnable, 5000);

                        skipBtn.setOnClickListener(v -> {
                            SDKLogger.d(TAG, "Video ad skipped by user");
                            showPostVideoButtons(exitBtn, installBtn, skipBtn);
                            handler.removeCallbacks(enableSkipRunnable);
                        });

                        exitBtn.setOnClickListener(v -> {
                            videoPlayer.release();
                            dialog.dismiss();
                            currentVideoPlayer = null;
                            SDKLogger.d(TAG, "Video ad exited by user");
                        });

                        installBtn.setOnClickListener(v -> {
                            SDKLogger.d(TAG, "Install button clicked");

                            videoPlayer.release();
                            dialog.dismiss();
                            currentVideoPlayer = null;
                        });

                        videoPlayer.setOnVideoCompletionListener(new IMAPlayerHandler.OnVideoCompletionListener() {
                            @Override
                            public void onVideoCompleted() {
                                SDKLogger.d(TAG, "Video ad completed");
                                showPostVideoButtons(exitBtn, installBtn, skipBtn);
                                handler.removeCallbacks(enableSkipRunnable);
                            }

                            @Override
                            public void onVideoSkipped() {
                                SDKLogger.d(TAG, "Video ad skipped");
                                showPostVideoButtons(exitBtn, installBtn, skipBtn);
                                handler.removeCallbacks(enableSkipRunnable);
                            }
                        });

                        dialog.show();

                        videoPlayer.playVast(adm, false);
                        currentVideoPlayer = videoPlayer;

                        SDKLogger.d(TAG, "Skippable video ad displayed successfully");
                    } else {
                        SDKLogger.e(TAG, "Context is not an Activity, cannot show skippable video ad");
                    }
                });
            }

            @Override
            public void onFail(Exception e) {
                SDKLogger.e(TAG, "Error loading skippable video ad: " + e.getMessage());
            }
        });
    }
    */

    /**
     * Shows post-video buttons (exit and install) and hides skip button
     */
    private void showPostVideoButtons(Button exitBtn, Button installBtn, Button skipBtn) {
        exitBtn.setVisibility(View.VISIBLE);
        installBtn.setVisibility(View.VISIBLE);
        skipBtn.setVisibility(View.GONE);
        SDKLogger.d(TAG, "Post-video buttons displayed");
    }

    /**
     * Shows native ad in full screen
     */
    private void showNativeAdFullScreen(String url) {

        HttpProvider.sendGetRequest(url, new BidscubeCallback() {
            @Override
            public void onSuccess(int responseCode, BidscubeResponse responseBody) {
                ((Activity) context).runOnUiThread(() -> {

                    setResponseAdPosition(responseBody.getPosition());

                    if (getEffectiveAdPosition() == AdPosition.FULL_SCREEN) {
                        SDKLogger.d(TAG, "Response indicates full screen display for native ad");
                    }

                    SDKLogger.d(TAG, "Native ad response received: " + responseBody);

                    Dialog dialog = new Dialog(context, android.R.style.Theme_Black_NoTitleBar_Fullscreen);

                    NativeAd nativeAd = NativeAdParser.parseFromAdm(responseBody.getAdm());
                    if (nativeAd != null) {

                        NativeAdView nativeAdView = new NativeAdView(context);

                        nativeAdView.setNativeAd(nativeAd);

                        nativeAdView.setLayoutParams(new LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT));

                        Button closeBtn = new Button(context);
                        closeBtn.setText("Close");
                        closeBtn.setBackgroundColor(0xFFF44336);
                        closeBtn.setTextColor(Color.WHITE);
                        closeBtn.setOnClickListener(v -> dialog.dismiss());

                        LinearLayout container = new LinearLayout(context);
                        container.setOrientation(LinearLayout.VERTICAL);
                        container.setPadding(32, 32, 32, 32);
                        container.setGravity(Gravity.CENTER);
                        container.addView(nativeAdView);
                        container.addView(closeBtn);

                        dialog.setContentView(container);

                        Window window = dialog.getWindow();
                        if (window != null) {
                            window.setGravity(Gravity.CENTER);
                        }

                        dialog.show();

                        currentNativeAd = nativeAdView;

                        SDKLogger.d(TAG, "Native ad displayed successfully in full screen with " +
                                (nativeAd.assets != null ? nativeAd.assets.size() : 0) + " assets");
                    } else {

                        showNativeAdErrorDialog(dialog, "Failed to parse native ad from JSON response");
                    }
                });
            }

            @Override
            public void onFail(Exception e) {
                SDKLogger.e(TAG, "Error loading native ad: " + e.getMessage());
                ((Activity) context).runOnUiThread(() -> {

                    Dialog errorDialog = new Dialog(context);
                    showNativeAdErrorDialog(errorDialog, "Error loading native ad: " + e.getMessage());
                });
            }
        });
    }

    /**
     * Shows error dialog for native ad failures
     */
    private void showNativeAdErrorDialog(Dialog dialog, String errorMessage) {
        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(32, 32, 32, 32);
        container.setGravity(Gravity.CENTER);

        TextView errorText = new TextView(context);
        errorText.setText("Native Ad Error");
        errorText.setTextSize(20);
        errorText.setTextColor(0xFFF44336);
        errorText.setGravity(Gravity.CENTER);
        errorText.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView messageText = new TextView(context);
        messageText.setText(errorMessage);
        messageText.setTextSize(16);
        messageText.setTextColor(Color.BLACK);
        messageText.setGravity(Gravity.CENTER);
        messageText.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        Button closeBtn = new Button(context);
        closeBtn.setText("Close");
        closeBtn.setBackgroundColor(0xFFF44336);
        closeBtn.setTextColor(Color.WHITE);
        closeBtn.setOnClickListener(v -> dialog.dismiss());
        closeBtn.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        container.addView(errorText);
        container.addView(messageText);
        container.addView(closeBtn);

        dialog.setContentView(container);
        dialog.show();
    }

    /**
     * Shows native ad in full screen mode from a URL
     */
    public void showAdNativeFullScreenFromUrl(String url) {
        Log.d(TAG, "Loading native ad from URL: " + url);

        HttpProvider.sendGetRequest(url, new BidscubeCallback() {
            @Override
            public void onSuccess(int responseCode, BidscubeResponse responseBody) {
                ((Activity) context).runOnUiThread(() -> {
                    Log.d(TAG, "Native ad response received from URL: " + responseBody);
                    showNativeAdInDialog(responseBody.getAdm(), true, "URL");
                });
            }

            @Override
            public void onFail(Exception e) {
                Log.e(TAG, "Error loading native ad from URL: " + e.getMessage());
                ((Activity) context).runOnUiThread(() -> {
                    Dialog errorDialog = new Dialog(context);
                    showNativeAdErrorDialog(errorDialog, "Error loading native ad from URL: " + e.getMessage());
                });
            }
        });
    }

    /**
     * Shows native ad in windowed mode from a URL
     */
    public void showAdNativeWindowedFromUrl(String url) {
        Log.d(TAG, "Loading native ad from URL (windowed): " + url);

        HttpProvider.sendGetRequest(url, new BidscubeCallback() {
            @Override
            public void onSuccess(int responseCode, BidscubeResponse responseBody) {
                ((Activity) context).runOnUiThread(() -> {
                    Log.d(TAG, "Native ad response received from URL (windowed): " + responseBody);
                    showNativeAdInDialog(responseBody.getAdm(), false, "URL");
                });
            }

            @Override
            public void onFail(Exception e) {
                Log.e(TAG, "Error loading native ad from URL (windowed): " + e.getMessage());
                ((Activity) context).runOnUiThread(() -> {
                    Dialog errorDialog = new Dialog(context);
                    showNativeAdErrorDialog(errorDialog, "Error loading native ad from URL (windowed): " + e.getMessage());
                });
            }
        });
    }

    /**
     * Get image ad view for integration into layouts (no dialog)
     *
     * @param url      Ad request URL
     * @param callback Callback for ad events
     * @return View that can be added to any layout
     */
    public View getImageAdView(String url, AdCallback callback) {
        SDKLogger.d(TAG, "Getting image ad view for integration: " + url);

        LinearLayout adContainer = new LinearLayout(context);
        adContainer.setOrientation(LinearLayout.VERTICAL);
        adContainer.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        adContainer.setBackgroundColor(Color.parseColor("#4CAF50"));
        adContainer.setPadding(16, 16, 16, 16);

        TextView loadingText = new TextView(context);
        loadingText.setText("Loading image ad...");
        loadingText.setTextColor(Color.WHITE);
        loadingText.setTextSize(14);
        loadingText.setGravity(Gravity.CENTER);
        adContainer.addView(loadingText);

        sendAdRequest(url, new BidscubeCallback() {
            @Override
            public void onSuccess(int responseCode, BidscubeResponse response) {
                ((Activity) context).runOnUiThread(() -> {

                    setResponseAdPosition(response.getPosition());

                    adContainer.removeView(loadingText);

                    View adView = createImageAdView(response.getAdm());
                    adContainer.addView(adView);

                    if (callback != null) {
                        callback.onAdLoaded("image_ad");
                    }

                    SDKLogger.d(TAG, "Image ad view created and integrated into container");
                });
            }

            @Override
            public void onFail(Exception e) {
                ((Activity) context).runOnUiThread(() -> {

                    adContainer.removeView(loadingText);

                    TextView errorText = new TextView(context);
                    errorText.setText("Failed to load ad: " + e.getMessage());
                    errorText.setTextColor(Color.WHITE);
                    errorText.setTextSize(14);
                    errorText.setGravity(Gravity.CENTER);
                    adContainer.addView(errorText);

                    if (callback != null) {
                        callback.onAdFailed("image_ad", -1, e.getMessage());
                    }

                    SDKLogger.e(TAG, "Failed to get image ad view: " + e.getMessage());
                });
            }
        });

        return adContainer;
    }

    /**
     * Get video ad view for integration into layouts (no dialog)
     *
     * @param url      Ad request URL
     * @param callback Callback for ad events
     * @return View that can be added to any layout
     */
    public View getVideoAdView(String url, AdCallback callback) {
        SDKLogger.d(TAG, "Getting video ad view for integration: " + url);

        LinearLayout adContainer = new LinearLayout(context);
        adContainer.setOrientation(LinearLayout.VERTICAL);
        adContainer.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        adContainer.setBackgroundColor(Color.parseColor("#1976D2"));
        adContainer.setPadding(16, 16, 16, 16);

        TextView loadingText = new TextView(context);
        loadingText.setText("Loading video ad...");
        loadingText.setTextColor(Color.WHITE);
        loadingText.setTextSize(14);
        loadingText.setGravity(Gravity.CENTER);
        adContainer.addView(loadingText);

        sendAdRequest(url, new BidscubeCallback() {
            @Override
            public void onSuccess(int responseCode, BidscubeResponse responseBody) {
                ((Activity) context).runOnUiThread(() -> {

                    setResponseAdPosition(responseBody.getPosition());

                    adContainer.removeView(loadingText);

                    try {
                        String adm = responseBody.getAdm();
                        SDKLogger.v("VastResponse", adm);
                        VastParser.analyzeVast(adm);
                        String vastRedirectUrl = VastParser.getClickThroughUrl(adm);

                        IMAPlayerHandler videoPlayer = new IMAPlayerHandler(adm, vastRedirectUrl, context);
                        int heightPx = (int) TypedValue.applyDimension(
                                TypedValue.COMPLEX_UNIT_DIP, 300, context.getResources().getDisplayMetrics());
                        videoPlayer.setLayoutParams(new FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT, heightPx));

                        Button playButton = new Button(context);
                        playButton.setText("▶ PLAY VIDEO AD");
                        playButton.setTextSize(16);
                        playButton.setBackgroundColor(Color.parseColor("#FF5722"));
                        playButton.setTextColor(Color.WHITE);
                        playButton.setPadding(16, 8, 16, 8);
                        playButton.setOnClickListener(v -> {
                            videoPlayer.playVast(adm, false);
                            playButton.setVisibility(View.GONE);
                        });

                        adContainer.addView(videoPlayer);
                        adContainer.addView(playButton);

                        if (callback != null) {
                            callback.onAdLoaded("video_ad");
                        }

                        SDKLogger.d(TAG, "Video ad view created and integrated into container");

                    } catch (Exception e) {
                        SDKLogger.e(TAG, "Error creating video ad view: " + e.getMessage());
                        TextView errorText = new TextView(context);
                        errorText.setText("Failed to create video ad: " + e.getMessage());
                        errorText.setTextColor(Color.WHITE);
                        errorText.setTextSize(14);
                        errorText.setGravity(Gravity.CENTER);
                        adContainer.addView(errorText);

                        if (callback != null) {
                            callback.onAdFailed("video_ad", -1, e.getMessage());
                        }
                    }
                });
            }

            @Override
            public void onFail(Exception e) {
                ((Activity) context).runOnUiThread(() -> {

                    adContainer.removeView(loadingText);

                    TextView errorText = new TextView(context);
                    errorText.setText("Failed to load ad: " + e.getMessage());
                    errorText.setTextColor(Color.WHITE);
                    errorText.setTextSize(14);
                    errorText.setGravity(Gravity.CENTER);
                    adContainer.addView(errorText);

                    if (callback != null) {
                        callback.onAdFailed("video_ad", -1, e.getMessage());
                    }

                    SDKLogger.e(TAG, "Failed to get video ad view: " + e.getMessage());
                });
            }
        });

        return adContainer;
    }

    /**
     * Get native ad view for integration into layouts (no dialog)
     *
     * @param url      Ad request URL
     * @param callback Callback for ad events
     * @return View that can be added to any layout
     */
    public View getNativeAdView(String url, AdCallback callback) {
        SDKLogger.d(TAG, "Getting native ad view for integration: " + url);

        LinearLayout adContainer = new LinearLayout(context);
        adContainer.setOrientation(LinearLayout.VERTICAL);
        adContainer.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        adContainer.setBackgroundColor(Color.parseColor("#FF9800"));
        adContainer.setPadding(16, 16, 16, 16);

        TextView loadingText = new TextView(context);
        loadingText.setText("Loading native ad...");
        loadingText.setTextColor(Color.WHITE);
        loadingText.setTextSize(14);
        loadingText.setGravity(Gravity.CENTER);
        adContainer.addView(loadingText);

        sendAdRequest(url, new BidscubeCallback() {
            @Override
            public void onSuccess(int responseCode, BidscubeResponse responseBody) {
                ((Activity) context).runOnUiThread(() -> {

                    setResponseAdPosition(responseBody.getPosition());

                    adContainer.removeView(loadingText);

                    try {
                        SDKLogger.d(TAG, "Native ad response received: " + responseBody);

                        NativeAd nativeAd = NativeAdParser.parseFromAdm(responseBody.getAdm());
                        if (nativeAd != null) {

                            NativeAdView nativeAdView = new NativeAdView(context);
                            nativeAdView.setNativeAd(nativeAd);
                            nativeAdView.setLayoutParams(new LinearLayout.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.WRAP_CONTENT));

                            adContainer.addView(nativeAdView);

                            if (callback != null) {
                                callback.onAdLoaded("native_ad");
                            }

                            SDKLogger.d(TAG, "Native ad view created and integrated into container with " +
                                    (nativeAd.assets != null ? nativeAd.assets.size() : 0) + " assets");
                        } else {
                            throw new Exception("Failed to parse native ad from response");
                        }

                    } catch (Exception e) {
                        SDKLogger.e(TAG, "Error creating native ad view: " + e.getMessage());
                        TextView errorText = new TextView(context);
                        errorText.setText("Failed to create native ad: " + e.getMessage());
                        errorText.setTextColor(Color.WHITE);
                        errorText.setTextSize(14);
                        errorText.setGravity(Gravity.CENTER);
                        adContainer.addView(errorText);

                        if (callback != null) {
                            callback.onAdFailed("native_ad", -1, e.getMessage());
                        }
                    }
                });
            }

            @Override
            public void onFail(Exception e) {
                ((Activity) context).runOnUiThread(() -> {

                    adContainer.removeView(loadingText);

                    TextView errorText = new TextView(context);
                    errorText.setText("Failed to load ad: " + e.getMessage());
                    errorText.setTextColor(Color.WHITE);
                    errorText.setTextSize(14);
                    errorText.setGravity(Gravity.CENTER);
                    adContainer.addView(errorText);

                    if (callback != null) {
                        callback.onAdFailed("native_ad", -1, e.getMessage());
                    }

                    SDKLogger.e(TAG, "Failed to get native ad view: " + e.getMessage());
                });
            }
        });

        return adContainer;
    }

    /**
     * Positions a windowed dialog based on the current ad position setting
     */
    public void positionWindowedDialog(Window window, int dialogWidth, int dialogHeight) {
        if (window == null)
            return;

        int screenWidth = context.getResources().getDisplayMetrics().widthPixels;
        int screenHeight = context.getResources().getDisplayMetrics().heightPixels;

        window.setLayout(dialogWidth, dialogHeight);

        AdPosition effectivePosition = getEffectiveAdPosition();

        switch (effectivePosition) {
            case ABOVE_THE_FOLD:

                window.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
                SDKLogger.d(TAG, "Positioned dialog above the fold");
                break;

            case BELOW_THE_FOLD:

                window.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
                SDKLogger.d(TAG, "Positioned dialog below the fold");
                break;

            case HEADER:

                window.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
                SDKLogger.d(TAG, "Positioned dialog at header");
                break;

            case FOOTER:

                window.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
                SDKLogger.d(TAG, "Positioned dialog at footer");
                break;

            case SIDEBAR:

                window.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
                SDKLogger.d(TAG, "Positioned dialog at sidebar");
                break;

            case MAYBE_DEPENDING_ON_SCREEN_SIZE:

                if (screenHeight > screenWidth) {

                    window.setGravity(Gravity.CENTER);
                    SDKLogger.d(TAG, "Portrait mode - positioned dialog in center");
                } else {

                    window.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
                    SDKLogger.d(TAG, "Landscape mode - positioned dialog on right side");
                }
                break;

            case FULL_SCREEN:

                SDKLogger.w(TAG, "Full screen position requested for windowed ad - using center");
                window.setGravity(Gravity.CENTER);
                break;

            case UNKNOWN:
            default:

                SDKLogger.d(TAG, "No position regulation applied - dialog will display naturally");
                break;
        }

        if (shouldApplyPositioning()) {
            SDKLogger.d(TAG, "Positioned windowed dialog with gravity for position: " + effectivePosition);
            SDKLogger.d(TAG, "Dialog size: " + dialogWidth + "x" + dialogHeight);
            SDKLogger.d(TAG, "Screen size: " + screenWidth + "x" + screenHeight);
            SDKLogger.d(TAG, "Gravity set to: " + window.getAttributes().gravity);
        } else {
            SDKLogger.d(TAG, "No positioning applied - dialog will display naturally");
        }
    }

    /**
     * Helper method to show native ad in dialog (full screen or windowed)
     */
    private void showNativeAdInDialog(String jsonData, boolean isFullScreen, String source) {
        try {

            NativeAd nativeAd = NativeAdParser.parseFromAdm(jsonData);
            if (nativeAd != null) {

                NativeAdView nativeAdView = new NativeAdView(context);

                nativeAdView.setNativeAd(nativeAd);

                Dialog dialog;
                if (isFullScreen) {
                    dialog = new Dialog(context, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
                } else {
                    dialog = new Dialog(context);
                }

                if (isFullScreen) {
                    nativeAdView.setLayoutParams(new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT));
                }

                Button closeBtn = new Button(context);
                closeBtn.setText("Close");
                closeBtn.setBackgroundColor(0xFFF44336);
                closeBtn.setTextColor(Color.WHITE);
                closeBtn.setOnClickListener(v -> dialog.dismiss());

                LinearLayout container = new LinearLayout(context);
                container.setOrientation(LinearLayout.VERTICAL);
                container.setPadding(isFullScreen ? 32 : 16, isFullScreen ? 32 : 16,
                        isFullScreen ? 32 : 16, isFullScreen ? 32 : 16);
                container.addView(nativeAdView);
                container.addView(closeBtn);

                dialog.setContentView(container);

                if (isFullScreen) {
                    centerFullScreenDialog(dialog, container);
                }

                if (!isFullScreen) {
                    Window window = dialog.getWindow();
                    if (window != null) {
                        int dialogWidth = (int) (context.getResources().getDisplayMetrics().widthPixels * 0.8);
                        int dialogHeight = (int) (context.getResources().getDisplayMetrics().heightPixels * 0.6);

                        SDKLogger.d(TAG, "Native ad windowed (flexible) - Current position: " + currentAdPosition);

                    }
                }

                dialog.show();

                currentNativeAd = nativeAdView;

                SDKLogger.d(TAG, "Native ad displayed successfully from " + source + " in " +
                        (isFullScreen ? "full screen" : "windowed") + " mode");

            } else {
                SDKLogger.e(TAG, "Failed to parse native ad from " + source);
                Dialog errorDialog = new Dialog(context);
                showNativeAdErrorDialog(errorDialog, "Failed to parse native ad from " + source);
            }
        } catch (Exception e) {
            SDKLogger.e(TAG, "Error showing native ad from " + source + ": " + e.getMessage());
            Dialog errorDialog = new Dialog(context);
            showNativeAdErrorDialog(errorDialog, "Error showing native ad from " + source + ": " + e.getMessage());
        }
    }

    /**
     * Cleans up resources
     */
    public void cleanup() {
        if (currentBanner != null) {
            currentBanner.destroy();
            currentBanner = null;
        }

        if (currentVideoPlayer != null) {
            currentVideoPlayer.release();
            currentVideoPlayer = null;
        }

        if (currentNativeAd != null) {
            currentNativeAd = null;
        }

        if (context instanceof Activity) {
            Activity activity = (Activity) context;
            activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            activity.getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_VISIBLE);
        }
    }
}
