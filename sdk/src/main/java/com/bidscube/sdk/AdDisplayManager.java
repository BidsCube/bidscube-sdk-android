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

import com.bidscube.sdk.ads.AdType;
import com.bidscube.sdk.ads.VideoAdFormat;
import com.bidscube.sdk.ads.VideoAdType;
import com.bidscube.sdk.interfaces.AdCallback;
import com.bidscube.sdk.models.AdRenderContext;
import com.bidscube.sdk.models.enums.AdPosition;
import com.bidscube.sdk.httpProvider.HttpProvider;

import com.bidscube.sdk.models.DeviceInfo;
import com.bidscube.sdk.models.natives.NativeAd;
import com.bidscube.sdk.network.BidscubeCallback;
import com.bidscube.sdk.network.BidscubeResponse;
import com.bidscube.sdk.network.NativeAdParser;
import com.bidscube.sdk.utils.AdmPayloadUtils;
import com.bidscube.sdk.utils.VastParser;
import com.bidscube.sdk.models.video.VastCompanion;
import com.bidscube.sdk.models.video.VideoPlaybackPlan;
import com.bidscube.sdk.models.video.VideoPlaybackPlanType;
import com.bidscube.sdk.openrtb.VideoPodResponseResolver;
import com.bidscube.sdk.openrtb.VideoPodConfig;
import com.bidscube.sdk.utils.VideoPlaybackPlanBuilder;
import com.bidscube.sdk.config.VideoPlayerProvider;
import com.bidscube.sdk.utils.SDKLogger;
import com.bidscube.sdk.video.PostVideoPolicy;
import com.bidscube.sdk.video.RewardState;
import com.bidscube.sdk.video.VideoUiDefaults;
import com.bidscube.sdk.view.BannerViewFactory;
import com.bidscube.sdk.view.BidscubeVideoAdPlayer;
import com.bidscube.sdk.view.ImaNativeSkipBlocker;
import com.bidscube.sdk.view.VideoInterstitialOverlay;
import com.bidscube.sdk.view.VideoInterstitialUiHelper;
import com.bidscube.sdk.view.OutstreamAdViewFactory;
import com.bidscube.sdk.view.VideoAdPlayerFactory;
import com.bidscube.sdk.view.VideoExperienceHelper;
import com.bidscube.sdk.view.NativeAdView;
import com.bidscube.sdk.view.NativeAdBinder;
import com.bumptech.glide.Glide;
import com.google.android.material.imageview.ShapeableImageView;
import com.google.android.material.shape.CornerFamily;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
public class AdDisplayManager {

    private static final String TAG = "AdDisplayManager";

    private static final int ERROR_VIDEO_ADS_DISABLED = -2;
    private static final int ERROR_VIDEO_UNSUPPORTED = -3;

    private static final String MSG_VIDEO_ADS_DISABLED =
            "Video ads are disabled in SDKConfig (videoAdsEnabled=false). "
                    + "Use SDKConfig.Builder.videoAdsEnabled(true) to enable VAST/IMA playback.";
    private static final String MSG_VIDEO_UNSUPPORTED =
            "Video ads are not supported in liteNoVideo artifact";

    private enum VideoSessionEnd {
        NONE,
        COMPLETED,
        SKIPPED,
        FAILED
    }

    private final Context context;
    private final DeviceInfo deviceInfo;
    private final VideoPlayerProvider videoPlayerProvider;
    private final boolean videoAdsEnabled;
    private final boolean autoClose;

    private WebView currentBanner = null;
    private BidscubeVideoAdPlayer currentVideoPlayer = null;
    private NativeAdView currentNativeAd = null;

    private FrameLayout overlayContainer;

    private AdPosition currentAdPosition = AdPosition.FULL_SCREEN;
    private AdPosition responseAdPosition = AdPosition.UNKNOWN;
    private final VideoPodConfig videoPodConfig;

    public AdDisplayManager(Context context, DeviceInfo deviceInfo, VideoPlayerProvider videoPlayerProvider,
            boolean videoAdsEnabled, VideoPodConfig videoPodConfig) {
        this(context, deviceInfo, videoPlayerProvider, videoAdsEnabled, false, videoPodConfig);
    }

    public AdDisplayManager(Context context, DeviceInfo deviceInfo, VideoPlayerProvider videoPlayerProvider,
            boolean videoAdsEnabled, boolean autoClose, VideoPodConfig videoPodConfig) {
        this.context = context;
        this.deviceInfo = deviceInfo;
        this.videoPlayerProvider = videoPlayerProvider;
        this.videoAdsEnabled = videoAdsEnabled;
        this.autoClose = autoClose;
        this.videoPodConfig = videoPodConfig != null ? videoPodConfig : VideoPodConfig.defaults();
    }

    // Try to resolve an Activity from the provided Context by unwrapping ContextWrappers.
    private Activity resolveActivityContext() {
        if (context == null) return null;
        if (context instanceof Activity) return (Activity) context;
        android.content.Context ctx = context;
        while (ctx instanceof android.content.ContextWrapper) {
            if (ctx instanceof Activity) return (Activity) ctx;
            ctx = ((android.content.ContextWrapper) ctx).getBaseContext();
            if (ctx == null) break;
        }
        return null;
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
     * Allows host app to intercept rendering if their callback opts-in.
     */
    private boolean handleRenderOverride(String placementId,
                                         String adm,
                                         AdPosition position,
                                         AdType.Type renderType,
                                         AdCallback callback) {
        if (callback == null || adm == null || adm.isEmpty()) {
            return false;
        }
        try {
            // sanitize ADM before passing to the host app so wrappers like document.write(...) are removed
            String cleanAdm = sanitizeAdm(adm);
            AdRenderContext context = new AdRenderContext(placementId, cleanAdm, position, renderType);
            boolean handled = callback.onAdRenderOverride(context);
            if (handled) {
                SDKLogger.d(TAG, "Render override accepted for placement " + placementId + " (" + renderType + ")");
            }
            return handled;
        } catch (Exception e) {
            SDKLogger.e(TAG, "Render override handler threw for placement " + placementId + ": " + e.getMessage());
            return false;
        }
    }

    // Helper to remove common JS wrappers around ADM responses, e.g. document.write('...');
    private String sanitizeAdm(String adm) {
        if (adm == null) return null;

        String current = AdmPayloadUtils.unwrapJsonAdmEnvelope(adm);
        current = AdmPayloadUtils.decodeLiteralUnicodeEscapes(current);
        // Try up to a few iterations to peel nested document.write wrappers
        for (int iter = 0; iter < 5; iter++) {
            String trimmed = current.trim();

            // Quick check
            String lower = trimmed.toLowerCase();
            if (!lower.contains("document.write") && !lower.contains("document.writeln")) {
                break;
            }

            // First attempt: regex match (handles many common cases)
            Pattern p = Pattern.compile("(?is).*document\\.writeln?\\s*\\((.*)\\)\\s*;?\\s*$");
            Matcher m = p.matcher(trimmed);
            String extracted = null;
            if (m.matches()) {
                extracted = m.group(1);
            } else {
                // Fallback: find the first document.write/writeln and extract balanced parentheses
                int docIdx = lower.indexOf("document.write");
                if (docIdx == -1) docIdx = lower.indexOf("document.writeln");
                if (docIdx != -1) {
                    int openIdx = trimmed.indexOf('(', docIdx);
                    if (openIdx >= 0) {
                        int depth = 0;
                        int closeIdx = -1;
                        for (int i = openIdx; i < trimmed.length(); i++) {
                            char c = trimmed.charAt(i);
                            if (c == '(') depth++;
                            else if (c == ')') {
                                depth--;
                                if (depth == 0) {
                                    closeIdx = i;
                                    break;
                                }
                            }
                        }
                        if (closeIdx > openIdx) {
                            extracted = trimmed.substring(openIdx + 1, closeIdx);
                        }
                    }
                }
            }

            if (extracted == null) {
                // nothing we can extract this iteration
                break;
            }

            String inner = extracted.trim();
            inner = unwrapFunctionWrapping(inner);

            // strip surrounding quotes/backticks if present
            if (inner.length() >= 2) {
                char start = inner.charAt(0);
                char end = inner.charAt(inner.length() - 1);
                if ((start == '\'' && end == '\'') || (start == '"' && end == '"') || (start == '`' && end == '`')) {
                    inner = inner.substring(1, inner.length() - 1);
                }
            }

            // Unescape common JS escape sequences
            inner = inner.replace("\\'", "'")
                    .replace("\\\"", "\"")
                    .replace("\\\\", "\\")
                    .replace("\\n", "\n")
                    .replace("\\r", "\r")
                    .replace("\\/", "/");

            // Try to URL-decode if it looks percent-encoded
            if (inner.contains("%3C") || inner.contains("%3c") || inner.contains("%22") || inner.contains("%7B")) {
                try {
                    inner = URLDecoder.decode(inner, StandardCharsets.UTF_8.name());
                } catch (Exception ignored) {
                }
            }

            // If we've extracted something different, assign and loop to remove further wrappers
            if (!inner.equals(current)) {
                current = inner;
                continue; // try another iteration
            } else {
                break;
            }
        }

        current = AdmPayloadUtils.extractEmbeddedAdmJsonFromCreative(current);
        current = AdmPayloadUtils.extractEmbeddedAdmJsonFromCreative(current);
        current = AdmPayloadUtils.decodeLiteralUnicodeEscapes(current);

        current = AdmPayloadUtils.stripInterTagJsonTextJunk(current);
        current = AdmPayloadUtils.stripTrailingJsonCloseAfterHtml(current);

        return current;
    }

    // Peel off common function wrappers like unescape(...), decodeURIComponent(...)
    private String unwrapFunctionWrapping(String s) {
        if (s == null) return null;
        String out = s.trim();
        Pattern pf = Pattern.compile("(?is)^(\\\\w+)\\\\s*\\\\((.*)\\\\)\\\\s*$");
        boolean peeled = true;
        while (peeled) {
            peeled = false;
            Matcher mf = pf.matcher(out);
            if (mf.matches()) {
                String fn = mf.group(1);
                String inner = Objects.requireNonNull(mf.group(2)).trim();
                assert fn != null;
                if (fn.equalsIgnoreCase("unescape") || fn.equalsIgnoreCase("decodeURIComponent") || fn.equalsIgnoreCase("decodeURI")) {
                    out = inner;
                    peeled = true;
                }
            }
        }
        return out;
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
    private void configureVideoPlayerForFullScreen(BidscubeVideoAdPlayer videoPlayer) {
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
    void showImageAdWithResponsePosition(String placementId, String url, AdCallback callback) {
        sendAdRequest(url, new BidscubeCallback() {
            @Override
            public void onSuccess(int responseCode, BidscubeResponse response) {
                ((Activity) context).runOnUiThread(() -> {
                    try {
                        setResponseAdPosition(response.getPosition());
                        AdPosition effectivePosition = getEffectiveAdPosition();

                        SDKLogger.d(TAG, "Image ad response position: " + response.getPosition() + " -> " + effectivePosition);

                        // Always render internally (no host render override). Sanitize ADM.
                        final String adm = sanitizeAdm(response.getAdm());
                        if (adm == null || adm.isEmpty()) {
                            SDKLogger.e(TAG, "Empty ADM for placement " + placementId);
                            if (callback != null) callback.onAdFailed(placementId, -1, "Empty ADM");
                            return;
                        }

                        // Clean up previous banner / overlay
                        try {
                            // Clear any attached native banners built via NativeAdBinder
                            try {
                                NativeAdBinder.clearAttachedBanner();
                            } catch (Throwable ignored) {
                            }

                            if (currentBanner != null) {
                                currentBanner.destroy();
                                currentBanner = null;
                            }
                            if (overlayContainer != null && overlayContainer.getParent() instanceof ViewGroup) {
                                ((ViewGroup) overlayContainer.getParent()).removeView(overlayContainer);
                            }
                            overlayContainer = null;
                        } catch (Exception ex) {
                            SDKLogger.d(TAG, "Error cleaning previous banner overlay: " + ex.getMessage());
                        }

                        // Allow host to intercept rendering via the generic onAdRenderOverride; if they handle it,
                        // skip internal rendering. Use the sanitized adm we computed earlier.
                        if (handleRenderOverride(placementId, adm, effectivePosition, AdType.Type.IMAGE, callback)) {
                            SDKLogger.d(TAG, "Image ad rendering overridden by host for placement " + placementId);
                            return;
                        }

                        // Create banner WebView via factory
                        currentBanner = BannerViewFactory.createBanner(context, adm);

                        // Determine an initial banner height so the WebView is visible immediately. Use 250dp for
                        // non-fullscreen banners; full screen uses MATCH_PARENT.
                        int defaultHeightPx = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 250, context.getResources().getDisplayMetrics());

                        // Prepare banner layout params and gravity according to position. Use explicit px height
                        // for non-fullscreen to avoid WRAP_CONTENT measuring to 0 before JS runs.
                        FrameLayout.LayoutParams bannerParams = new FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT);

                        if (effectivePosition == AdPosition.FULL_SCREEN) {
                            bannerParams.width = ViewGroup.LayoutParams.MATCH_PARENT;
                            bannerParams.height = ViewGroup.LayoutParams.MATCH_PARENT;
                            bannerParams.gravity = Gravity.CENTER;
                        } else {
                            switch (effectivePosition) {
                                case HEADER:
                                    bannerParams.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
                                    break;
                                case FOOTER:
                                    bannerParams.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
                                    break;
                                case SIDEBAR:
                                    bannerParams.gravity = Gravity.LEFT | Gravity.CENTER_VERTICAL;
                                    break;
                                default:
                                    bannerParams.gravity = Gravity.CENTER;
                                    break;
                            }
                        }

                        // Create a container sized like the banner area and add the banner inside it.
                        overlayContainer = new FrameLayout(context);
                        int overlayHeight = (bannerParams.height == ViewGroup.LayoutParams.WRAP_CONTENT) ? defaultHeightPx : bannerParams.height;
                        FrameLayout.LayoutParams overlayParams = new FrameLayout.LayoutParams(
                                bannerParams.width, overlayHeight, bannerParams.gravity);
                        overlayContainer.setLayoutParams(overlayParams);
                        overlayContainer.setBackgroundColor(Color.TRANSPARENT);
                        // add currentBanner with MATCH_PARENT width and either default height or MATCH_PARENT
                        FrameLayout.LayoutParams insideLp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                                (overlayHeight == ViewGroup.LayoutParams.MATCH_PARENT) ? ViewGroup.LayoutParams.MATCH_PARENT : ViewGroup.LayoutParams.MATCH_PARENT);
                        overlayContainer.addView(currentBanner, insideLp);

                        // Attach overlay to activity content root (the overlay only occupies the banner area)
                        Activity activity = resolveActivityContext();
                        if (activity != null) {
                            ViewGroup root = activity.findViewById(android.R.id.content);
                            if (root != null) {
                                // make overlay interactive and ensure it's on top
                                overlayContainer.setClickable(true);
                                root.addView(overlayContainer);
                                overlayContainer.bringToFront();
                                overlayContainer.requestLayout();
                                if (currentBanner != null) currentBanner.requestLayout();
                                SDKLogger.d(TAG, "Image ad overlay (sized) added to activity content for placement " + placementId);
                                if (callback != null) callback.onAdLoaded(placementId);
                            } else {
                                SDKLogger.e(TAG, "Activity root (android.R.id.content) not found");
                                if (callback != null)
                                    callback.onAdFailed(placementId, -1, "Activity root not found");
                            }
                        } else {
                            SDKLogger.e(TAG, "Could not resolve Activity from Context - ensure SDK initialized with an Activity context");
                            if (callback != null)
                                callback.onAdFailed(placementId, -1, "Could not resolve Activity from Context");
                        }
                    } catch (Exception e) {
                        SDKLogger.e(TAG, "Error rendering image ad: " + e.getMessage());
                        if (callback != null) callback.onAdFailed(placementId, -1, e.getMessage());
                    }
                });
            }

            @Override
            public void onFail(Exception e) {
                SDKLogger.e(TAG, "Error loading image ad: " + e.getMessage());
                ((Activity) context).runOnUiThread(() -> {
                    if (callback != null) callback.onAdFailed(placementId, -1, e.getMessage());
                });
            }
        });
    }


    /**
     * Creates image ad view
     */
    private View createImageAdView(String responseBody) {

        // sanitize ADM wrappers if present
        responseBody = sanitizeAdm(responseBody);

        if (currentBanner != null) {
            currentBanner.destroy();
            currentBanner = null;
        }

        currentBanner = BannerViewFactory.createBanner(context, responseBody);

        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(50, 16, 16, 16);
        container.addView(currentBanner);

        return container;
    }

    /**
     * Creates image ad view with close button
     */
    private FrameLayout createImageAdViewWithCloseButton(String responseBody, Dialog dialog) {

        // sanitize ADM wrappers (document.write etc.) before creating banner view
        responseBody = sanitizeAdm(responseBody);

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

        //bannerParams.setMargins(16, 16, 16, 16);
        container.addView(currentBanner, bannerParams);

        SDKLogger.d(TAG, "Image ad positioned: " + getPositioningDescription());

        return container;
    }

    /**
     * Show video ad with display mode determined by response position
     * This method respects the position value from the ad response
     */
    void showVideoAdWithResponsePosition(String placementId, String url, VideoAdFormat format, AdCallback callback) {
        if (callback == null) {
            return;
        }
        Activity activity = resolveActivityContext();
        if (activity == null) {
            callback.onAdFailed(placementId, -1, "Context is not an Activity");
            return;
        }
        if (!videoAdsEnabled) {
            activity.runOnUiThread(() ->
                    callback.onAdFailed(placementId, ERROR_VIDEO_ADS_DISABLED, MSG_VIDEO_ADS_DISABLED));
            return;
        }
        if (!VideoAdPlayerFactory.isVideoSupported()) {
            activity.runOnUiThread(() ->
                    callback.onAdFailed(placementId, ERROR_VIDEO_UNSUPPORTED, MSG_VIDEO_UNSUPPORTED));
            return;
        }

        HttpProvider.sendGetRequest(url, new BidscubeCallback() {
            @Override
            public void onSuccess(int responseCode, BidscubeResponse responseBody) {
                activity.runOnUiThread(() -> handleVideoAdResponse(placementId, responseBody, format, callback));
            }

            @Override
            public void onFail(Exception e) {
                activity.runOnUiThread(() -> {
                    SDKLogger.e(TAG, "Error loading video ad: " + e.getMessage());
                    String msg = e.getMessage() != null ? e.getMessage() : "network error";
                    callback.onAdFailed(placementId, -1, msg);
                });
            }
        });
    }

    private void handleVideoAdResponse(
            String placementId,
            BidscubeResponse responseBody,
            VideoAdFormat format,
            AdCallback callback) {
        try {
            setResponseAdPosition(responseBody.getPosition());
            AdPosition effectivePosition = getEffectiveAdPosition();

            SDKLogger.d(TAG, "Video ad response position: " + responseBody.getPosition() + " -> "
                    + effectivePosition);

            final String adm = sanitizeAdm(responseBody.getAdm());

            if (handleRenderOverride(placementId, adm, effectivePosition, AdType.Type.VIDEO, callback)) {
                SDKLogger.d(TAG, "Video ad rendering overridden by host app");
                return;
            }

            com.bidscube.sdk.models.video.VideoPlaybackPlan playbackPlan =
                    VideoPodResponseResolver.resolve(responseBody, videoPodConfig);

            if (adm != null && !adm.trim().isEmpty()) {
                SDKLogger.v("VastResponse", adm);
                VastParser.analyzeVast(adm);
            }
            String vastRedirectUrl = resolveVideoClickThroughUrl(adm, playbackPlan);

            callback.onAdLoaded(placementId);

            Activity activity = resolveActivityContext();
            if (activity == null) {
                callback.onAdFailed(placementId, -1, "Context is not an Activity");
                return;
            }

            if (playbackPlan.isPodPlayback()) {
                SDKLogger.d(TAG, "Routing to ad pod playback slots=" + playbackPlan.getTotalAds()
                        + " openRtbPodded=" + playbackPlan.isOpenRtbPodded()
                        + " podType=" + playbackPlan.getOpenRtbPodType());
                VideoExperienceHelper.showAdPodWithPlan(
                        activity, placementId, playbackPlan, vastRedirectUrl, effectivePosition,
                        format, callback, videoPlayerProvider, videoPodConfig, autoClose);
                return;
            }
            if (adm == null || adm.trim().isEmpty()) {
                callback.onAdFailed(placementId, -1, "Empty video ad markup");
                return;
            }
            if (VastParser.hasPlayableExtension(adm)) {
                SDKLogger.d(TAG, "Routing to gamified playable experience");
                VideoExperienceHelper.showGamifiedInDialog(
                        activity, placementId, adm, vastRedirectUrl, effectivePosition, format,
                        callback, videoPlayerProvider, autoClose);
                return;
            }

            presentVideoVastInDialog(placementId, adm, vastRedirectUrl, effectivePosition, format, callback, activity);
        } catch (Exception e) {
            SDKLogger.e(TAG, "Video ad handling failed: " + e.getMessage());
            String msg = e.getMessage() != null ? e.getMessage() : "video error";
            callback.onAdFailed(placementId, -1, msg);
        }
    }

    private void presentVideoVastInDialog(
            String placementId,
            String adm,
            String vastRedirectUrl,
            AdPosition effectivePosition,
            VideoAdFormat format,
            AdCallback callback,
            Activity activity) {

        final AtomicReference<VideoSessionEnd> sessionEnd = new AtomicReference<>(VideoSessionEnd.NONE);
        final AtomicBoolean adClosedEmitted = new AtomicBoolean(false);
        final AtomicBoolean endCardShown = new AtomicBoolean(false);
        final AtomicBoolean videoCompletedEmitted = new AtomicBoolean(false);
        final AtomicBoolean closing = new AtomicBoolean(false);
        final RewardState rewardState = new RewardState();

        Runnable emitAdClosed = () -> {
            if (adClosedEmitted.compareAndSet(false, true)) {
                callback.onAdClosed(placementId);
            }
        };

        final VastCompanion companion = VastParser.getBestCompanion(adm);
        final String endCardClickUrl = firstNonEmpty(
                companion != null ? companion.getClickThroughUrl() : null,
                firstNonEmpty(VastParser.getCompanionClickThroughUrl(adm), vastRedirectUrl));
        final int skipOffsetSeconds = VastParser.getSkipOffsetSeconds(adm);

        final boolean fullscreen = effectivePosition == AdPosition.FULL_SCREEN;
        final Dialog dialog;
        final FrameLayout frameContainer = new FrameLayout(activity);
        frameContainer.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        if (fullscreen) {
            dialog = new Dialog(activity, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
            SDKLogger.d(TAG, "Response indicates full screen display for video ad");
        } else {
            dialog = new Dialog(activity);
            String positionName = getPositionDisplayName();
            dialog.setTitle("Video Ad - " + positionName);
            SDKLogger.d(TAG, "Response indicates windowed display for video ad");
        }

        BidscubeVideoAdPlayer videoPlayer =
                VideoAdPlayerFactory.create(activity, adm, vastRedirectUrl, videoPlayerProvider);
        if (fullscreen) {
            videoPlayer.setLayoutParams(new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));
        } else {
            int heightPx = (int) TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP, 300, activity.getResources().getDisplayMetrics());
            videoPlayer.setLayoutParams(
                    new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, heightPx));
        }

        frameContainer.addView(videoPlayer.asViewGroup());
        dialog.setContentView(frameContainer);

        final VideoInterstitialOverlay overlay = VideoInterstitialOverlay.attach(frameContainer);
        final ImaNativeSkipBlocker imaSkipBlocker = ImaNativeSkipBlocker.attach(frameContainer);

        /** Centralized close: release player, remove overlays/end card, dismiss, close callback once. */
        Runnable closeAdSession = () -> {
            if (!closing.compareAndSet(false, true)) {
                return;
            }
            activity.runOnUiThread(() -> {
                try {
                    overlay.detach();
                } catch (Throwable ignored) {
                }
                try {
                    imaSkipBlocker.detach();
                } catch (Throwable ignored) {
                }
                try {
                    videoPlayer.release();
                } catch (Throwable ignored) {
                }
                currentVideoPlayer = null;
                if (dialog.isShowing()) {
                    dialog.dismiss();
                } else {
                    emitAdClosed.run();
                }
            });
        };

        Runnable handlePostVideo = () -> {
            PostVideoPolicy.Action action = PostVideoPolicy.resolve(
                    autoClose,
                    PostVideoPolicy.hasRenderableCompanion(companion),
                    false);
            SDKLogger.d(TAG, "Post-video action=" + action + " autoClose=" + autoClose);
            switch (action) {
                case AUTO_CLOSE:
                    closeAdSession.run();
                    break;
                case SHOW_END_CARD:
                    if (!endCardShown.compareAndSet(false, true)) {
                        return;
                    }
                    showVideoEndCard(
                            videoPlayer,
                            frameContainer,
                            overlay,
                            companion,
                            endCardClickUrl,
                            () -> callback.onEndCardShown(placementId),
                            () -> callback.onAdClicked(placementId),
                            closeAdSession);
                    break;
                case KEEP_LAST_FRAME:
                default:
                    VideoInterstitialUiHelper.showLastFrameClose(overlay, closeAdSession);
                    break;
            }
        };

        overlay.setListener(new VideoInterstitialOverlay.Listener() {
            @Override
            public void onSkipRequested() {
                videoPlayer.skipVideo();
            }

            @Override
            public void onCloseRequested() {
                closeAdSession.run();
            }
        });

        if (fullscreen) {
            centerFullScreenDialog(dialog, frameContainer);
        } else {
            Window window = dialog.getWindow();
            if (window != null) {
                int dialogWidth = (int) (activity.getResources().getDisplayMetrics().widthPixels * 0.8);
                int dialogHeight = (int) (activity.getResources().getDisplayMetrics().heightPixels * 0.7);
                positionWindowedDialog(window, dialogWidth, dialogHeight);
            }
        }

        videoPlayer.setOnVideoCompletionListener(new BidscubeVideoAdPlayer.VideoCompletionListener() {
            @Override
            public void onVideoCompleted() {
                if (sessionEnd.compareAndSet(VideoSessionEnd.NONE, VideoSessionEnd.COMPLETED)) {
                    if (videoCompletedEmitted.compareAndSet(false, true)) {
                        callback.onVideoAdCompleted(placementId);
                    }
                    rewardState.maybeRewardStandardComplete(format, callback, placementId);
                }
                activity.runOnUiThread(() -> {
                    imaSkipBlocker.hide();
                    handlePostVideo.run();
                });
            }

            @Override
            public void onVideoSkipped() {
                if (sessionEnd.compareAndSet(VideoSessionEnd.NONE, VideoSessionEnd.SKIPPED)) {
                    callback.onVideoAdSkipped(placementId);
                }
                activity.runOnUiThread(() -> {
                    imaSkipBlocker.hide();
                    handlePostVideo.run();
                });
            }

            @Override
            public void onVideoStarted() {
                callback.onVideoAdStarted(placementId);
                int countdownSeconds = VideoUiDefaults.resolveSkipOffsetSeconds(skipOffsetSeconds);
                activity.runOnUiThread(() -> {
                    overlay.startSkipCountdown(countdownSeconds);
                    imaSkipBlocker.show(overlay);
                });
            }

            @Override
            public void onVideoSkippable() {
                callback.onVideoAdSkippable(placementId);
                activity.runOnUiThread(() -> {
                    overlay.enableSkip();
                    imaSkipBlocker.show(overlay);
                });
            }

            @Override
            public void onVideoClicked() {
                callback.onAdClicked(placementId);
            }

            @Override
            public void onVideoError(String message) {
                String msg = message != null ? message : "video playback error";
                if (sessionEnd.compareAndSet(VideoSessionEnd.NONE, VideoSessionEnd.FAILED)) {
                    callback.onAdFailed(placementId, -1, msg);
                }
                closeAdSession.run();
            }
        });

        dialog.setOnShowListener(d -> {
            callback.onAdDisplayed(placementId);
            videoPlayer.playVast(adm, false);
        });

        dialog.setOnDismissListener(d -> {
            VideoSessionEnd end = sessionEnd.get();
            if (end == VideoSessionEnd.NONE) {
                sessionEnd.set(VideoSessionEnd.SKIPPED);
                callback.onVideoAdSkipped(placementId);
            }
            closing.set(true);
            try {
                overlay.detach();
            } catch (Throwable ignored) {
            }
            try {
                imaSkipBlocker.detach();
            } catch (Throwable ignored) {
            }
            try {
                videoPlayer.release();
            } catch (Throwable ignored) {
            }
            currentVideoPlayer = null;
            emitAdClosed.run();
        });

        dialog.show();
        currentVideoPlayer = videoPlayer;

        SDKLogger.d(TAG, "Video ad dialog shown (fullscreen=" + fullscreen
                + ", skipOffset=" + skipOffsetSeconds
                + ", autoClose=" + autoClose + ")");
    }

    private static String resolveVideoClickThroughUrl(
            String adm,
            com.bidscube.sdk.models.video.VideoPlaybackPlan playbackPlan) {
        String url = VastParser.getClickThroughUrl(adm);
        if (url != null && !url.trim().isEmpty()) {
            return url.trim();
        }
        if (playbackPlan == null || playbackPlan.isEmpty()) {
            return null;
        }
        for (com.bidscube.sdk.models.video.VideoAdSlot slot : playbackPlan.getSlots()) {
            String slotClick = slot.getClickThroughUrl();
            if (slotClick != null && !slotClick.trim().isEmpty()) {
                return slotClick.trim();
            }
        }
        return null;
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

    /**
     * Shows skippable video ad in windowed mode with custom install button text
     *
     * @param installButtonText Custom text for the install button (e.g., "Install", "Shop Now", "Learn More")
     */
    public void showSkippableVideoAdWindowed(String installButtonText) {

        VideoAdType testAdType = new VideoAdType("20213");
        String url = testAdType.buildRequestUrl(deviceInfo).toString();
        showSkippableVideoAdFromFile(false);
    }

    /**
     * Shows skippable video ad in full screen mode with custom install button text
     *
     * @param installButtonText Custom text for the install button (e.g., "Install", "Shop Now", "Learn More")
     */
    public void showSkippableVideoAdFullScreen(String installButtonText) {

        VideoAdType testAdType = new VideoAdType("20213");
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
        VastCompanion companion = VastParser.getBestCompanion(adm);
        String endCardClickUrl = firstNonEmpty(VastParser.getCompanionClickThroughUrl(adm), vastRedirectUrl);
        int skipOffsetSeconds = VastParser.getSkipOffsetSeconds(adm);

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

        BidscubeVideoAdPlayer videoPlayer = VideoAdPlayerFactory.create(context, adm, vastRedirectUrl, videoPlayerProvider);
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

        mainContainer.addView(videoPlayer.asViewGroup());
        dialog.setContentView(mainContainer);

        VideoInterstitialOverlay overlay = VideoInterstitialOverlay.attach(mainContainer);
        overlay.hide();

        overlay.setListener(new VideoInterstitialOverlay.Listener() {
            @Override
            public void onSkipRequested() {
                videoPlayer.skipVideo();
            }

            @Override
            public void onCloseRequested() {
                dialog.dismiss();
            }
        });

        dialog.show();

        videoPlayer.setOnVideoCompletionListener(new BidscubeVideoAdPlayer.VideoCompletionListener() {
            @Override
            public void onVideoCompleted() {
                PostVideoPolicy.Action action = PostVideoPolicy.resolve(
                        autoClose,
                        PostVideoPolicy.hasRenderableCompanion(companion),
                        false);
                if (action == PostVideoPolicy.Action.AUTO_CLOSE) {
                    dialog.dismiss();
                } else if (action == PostVideoPolicy.Action.SHOW_END_CARD) {
                    showVideoEndCard(videoPlayer, mainContainer, overlay, companion, endCardClickUrl,
                            null, null, dialog::dismiss);
                } else {
                    VideoInterstitialUiHelper.showLastFrameClose(overlay, dialog::dismiss);
                }
            }

            @Override
            public void onVideoSkipped() {
                PostVideoPolicy.Action action = PostVideoPolicy.resolve(
                        autoClose,
                        PostVideoPolicy.hasRenderableCompanion(companion),
                        false);
                if (action == PostVideoPolicy.Action.AUTO_CLOSE) {
                    dialog.dismiss();
                } else if (action == PostVideoPolicy.Action.SHOW_END_CARD) {
                    showVideoEndCard(videoPlayer, mainContainer, overlay, companion, endCardClickUrl,
                            null, null, dialog::dismiss);
                } else {
                    VideoInterstitialUiHelper.showLastFrameClose(overlay, dialog::dismiss);
                }
            }

        });
        videoPlayer.playVast(adm, false);
    }

    private void showVideoEndCard(
            BidscubeVideoAdPlayer player,
            FrameLayout container,
            VideoInterstitialOverlay overlay,
            VastCompanion companion,
            String clickUrl,
            Runnable onEndCardShown,
            Runnable onEndCardClicked,
            Runnable onClose) {
        if (!PostVideoPolicy.hasRenderableCompanion(companion)) {
            SDKLogger.d(TAG, "No companion — keeping last frame instead of end card");
            VideoInterstitialUiHelper.showLastFrameClose(overlay, onClose);
            return;
        }
        VideoInterstitialUiHelper.showCompanionEndCard(
                context,
                player,
                container,
                overlay,
                companion,
                clickUrl,
                onEndCardShown,
                onEndCardClicked,
                onClose);
    }

    /** @deprecated Use {@link #showVideoEndCard} via production video flow. */
    private void showFinalAdScreen(BidscubeVideoAdPlayer player, FrameLayout container, String imageUrl, String clickUrl) {
        VastCompanion companion = imageUrl != null && !imageUrl.trim().isEmpty()
                ? new VastCompanion(VastCompanion.ResourceType.STATIC, imageUrl, clickUrl, null, null, 0, 0)
                : null;
        showVideoEndCard(player, container, null, companion, clickUrl, null, null, null);
    }

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
    public void showAdNativeFullScreenFromUrl(String placementId, String url, AdCallback callback) {
        Log.d(TAG, "Loading native ad from URL: " + url);

        HttpProvider.sendGetRequest(url, new BidscubeCallback() {
            @Override
            public void onSuccess(int responseCode, BidscubeResponse responseBody) {
                ((Activity) context).runOnUiThread(() -> {
                    Log.d(TAG, "Native ad response received from URL: " + responseBody);
                    setResponseAdPosition(responseBody.getPosition());
                    AdPosition effectivePosition = getEffectiveAdPosition();

                    // Parse native ad and give host a typed model to render (backwards-compatible)
                    String sanitized = sanitizeAdm(responseBody.getAdm());
                    com.bidscube.sdk.models.natives.NativeAd nativeAd = null;
                    try {
                        nativeAd = com.bidscube.sdk.network.NativeAdParser.parseFromAdm(sanitized);
                    } catch (Exception e) {
                        SDKLogger.d(TAG, "Failed to parse native ad model: " + e.getMessage());
                    }

                    // First allow host to render using the generic onAdRenderOverride (preferred)
                    boolean hostRendered = false;
                    try {
                        if (callback != null) {
                            com.bidscube.sdk.models.AdRenderContext ctx = new com.bidscube.sdk.models.AdRenderContext(placementId, sanitized, effectivePosition, AdType.Type.NATIVE, nativeAd);
                            hostRendered = callback.onAdRenderOverride(ctx);
                        }
                    } catch (Exception e) {
                        SDKLogger.e(TAG, "Host onAdRenderOverride threw: " + e.getMessage());
                    }

                    // If host implements the generic onAdRenderOverride, call it (pass parsed nativeAd in context)
                    if (!hostRendered) {
                        try {
                            if (callback != null) {
                                com.bidscube.sdk.models.AdRenderContext ctx2 = new com.bidscube.sdk.models.AdRenderContext(placementId, sanitized, effectivePosition, AdType.Type.NATIVE, nativeAd);
                                hostRendered = callback.onAdRenderOverride(ctx2);
                            }
                        } catch (Exception e) {
                            SDKLogger.e(TAG, "Host onAdRenderOverride (fallback) threw: " + e.getMessage());
                        }
                    }

                    if (hostRendered) {
                        com.bidscube.sdk.network.NativeImpressionTracker.fireIfNeeded(nativeAd, "host_render_override_fullscreen_url");
                        SDKLogger.d(TAG, "Native full screen ad rendering handled by host");
                        return;
                    }

                    // Fallback to the legacy generic render-override hook (HTML-based) as last resort
                    if (handleRenderOverride(placementId, responseBody.getAdm(), effectivePosition, AdType.Type.NATIVE, callback)) {
                        SDKLogger.d(TAG, "Native full screen ad rendering overridden by host app (legacy)");
                        return;
                    }

                    showNativeAdInDialog(sanitized, true, "URL");
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
    public void showAdNativeWindowedFromUrl(String placementId, String url, AdCallback callback) {
        Log.d(TAG, "Loading native ad from URL (windowed): " + url);

        HttpProvider.sendGetRequest(url, new BidscubeCallback() {
            @Override
            public void onSuccess(int responseCode, BidscubeResponse responseBody) {
                ((Activity) context).runOnUiThread(() -> {
                    Log.d(TAG, "Native ad response received from URL (windowed): " + responseBody);
                    setResponseAdPosition(responseBody.getPosition());
                    AdPosition effectivePosition = getEffectiveAdPosition();

                    String sanitized = sanitizeAdm(responseBody.getAdm());
                    com.bidscube.sdk.models.natives.NativeAd nativeAd = null;
                    try {
                        nativeAd = com.bidscube.sdk.network.NativeAdParser.parseFromAdm(sanitized);
                    } catch (Exception e) {
                        SDKLogger.d(TAG, "Failed to parse native ad model: " + e.getMessage());
                    }

                    // Preferred: allow host to render via the generic onAdRenderOverride (pass parsed native model)
                    boolean hostRendered = false;
                    try {
                        if (callback != null) {
                            com.bidscube.sdk.models.AdRenderContext ctx = new com.bidscube.sdk.models.AdRenderContext(placementId, sanitized, effectivePosition, AdType.Type.NATIVE, nativeAd);
                            hostRendered = callback.onAdRenderOverride(ctx);
                        }
                    } catch (Exception e) {
                        SDKLogger.e(TAG, "Host onAdRenderOverride threw: " + e.getMessage());
                    }

                    // If host implements the generic onAdRenderOverride, call it (pass parsed nativeAd in context)
                    if (!hostRendered) {
                        try {
                            if (callback != null) {
                                com.bidscube.sdk.models.AdRenderContext ctx2 = new com.bidscube.sdk.models.AdRenderContext(placementId, sanitized, effectivePosition, AdType.Type.NATIVE, nativeAd);
                                hostRendered = callback.onAdRenderOverride(ctx2);
                            }
                        } catch (Exception e) {
                            SDKLogger.e(TAG, "Host onAdRenderOverride (fallback) threw: " + e.getMessage());
                        }
                    }

                    if (hostRendered) {
                        com.bidscube.sdk.network.NativeImpressionTracker.fireIfNeeded(nativeAd, "host_render_override_windowed_url");
                        SDKLogger.d(TAG, "Native windowed ad rendering handled by host");
                        return;
                    }

                    if (handleRenderOverride(placementId, responseBody.getAdm(), effectivePosition, AdType.Type.NATIVE, callback)) {
                        SDKLogger.d(TAG, "Native windowed ad rendering overridden by host app (legacy)");
                        return;
                    }

                    showNativeAdInDialog(sanitized, false, "URL");
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
    public View getImageAdView(String placementId, String url, AdCallback callback) {
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

                    if (handleRenderOverride(placementId, response.getAdm(), getEffectiveAdPosition(), AdType.Type.IMAGE, callback)) {
                        adContainer.removeView(loadingText);
                        SDKLogger.d(TAG, "Image ad view rendering overridden by host app");
                        return;
                    }

                    View adView = createImageAdView(response.getAdm());
                    adContainer.addView(adView);

                    if (callback != null) {
                        callback.onAdLoaded(placementId);
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
                        callback.onAdFailed(placementId, -1, e.getMessage());
                        callback.onAdFailed(placementId, -1, e.getMessage());
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
    public View getVideoAdView(String placementId, String url, VideoAdFormat format, AdCallback callback) {
        SDKLogger.d(TAG, "Getting video ad view for integration: " + url);

        Activity activity = resolveActivityContext();
        if (activity == null) {
            if (callback != null) {
                callback.onAdFailed(placementId, -1, "Context is not an Activity");
            }
            return buildSdkErrorTextView("Context is not an Activity");
        }
        if (!videoAdsEnabled) {
            if (callback != null) {
                callback.onAdFailed(placementId, ERROR_VIDEO_ADS_DISABLED, MSG_VIDEO_ADS_DISABLED);
            }
            return buildSdkErrorTextView(MSG_VIDEO_ADS_DISABLED);
        }
        if (!VideoAdPlayerFactory.isVideoSupported()) {
            if (callback != null) {
                callback.onAdFailed(placementId, ERROR_VIDEO_UNSUPPORTED, MSG_VIDEO_UNSUPPORTED);
            }
            return buildSdkErrorTextView(MSG_VIDEO_UNSUPPORTED);
        }

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
                activity.runOnUiThread(() -> {

                    setResponseAdPosition(responseBody.getPosition());

                    adContainer.removeView(loadingText);

                    if (callback == null) {
                        return;
                    }

                    try {
                        String sanitizedForOverride = sanitizeAdm(responseBody.getAdm());
                        if (handleRenderOverride(placementId, sanitizedForOverride, getEffectiveAdPosition(),
                                AdType.Type.VIDEO, callback)) {
                            SDKLogger.d(TAG, "Video ad view rendering overridden by host app");
                            return;
                        }

                        final String adm = sanitizeAdm(responseBody.getAdm());
                        SDKLogger.v("VastResponse", adm);
                        VastParser.analyzeVast(adm);
                        String vastRedirectUrl = VastParser.getClickThroughUrl(adm);

                        final AtomicReference<VideoSessionEnd> sessionEnd =
                                new AtomicReference<>(VideoSessionEnd.NONE);

                        BidscubeVideoAdPlayer videoPlayer =
                                VideoAdPlayerFactory.create(context, adm, vastRedirectUrl, videoPlayerProvider);
                        int heightPx = (int) TypedValue.applyDimension(
                                TypedValue.COMPLEX_UNIT_DIP, 300, context.getResources().getDisplayMetrics());
                        videoPlayer.setLayoutParams(new FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT, heightPx));

                        videoPlayer.setOnVideoCompletionListener(new BidscubeVideoAdPlayer.VideoCompletionListener() {
                            @Override
                            public void onVideoCompleted() {
                                if (sessionEnd.compareAndSet(VideoSessionEnd.NONE, VideoSessionEnd.COMPLETED)) {
                                    callback.onVideoAdCompleted(placementId);
                                    if (format == VideoAdFormat.REWARDED) {
                                        callback.onUserRewarded(placementId);
                                    }
                                }
                            }

                            @Override
                            public void onVideoSkipped() {
                                if (sessionEnd.compareAndSet(VideoSessionEnd.NONE, VideoSessionEnd.SKIPPED)) {
                                    callback.onVideoAdSkipped(placementId);
                                }
                            }

                            @Override
                            public void onVideoStarted() {
                                callback.onVideoAdStarted(placementId);
                            }

                            @Override
                            public void onVideoClicked() {
                                callback.onAdClicked(placementId);
                            }

                            @Override
                            public void onVideoError(String message) {
                                String msg = message != null ? message : "video playback error";
                                if (sessionEnd.compareAndSet(VideoSessionEnd.NONE, VideoSessionEnd.FAILED)) {
                                    callback.onAdFailed(placementId, -1, msg);
                                }
                            }
                        });

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

                        adContainer.addView(videoPlayer.asViewGroup());
                        adContainer.addView(playButton);

                        callback.onAdLoaded(placementId);
                        adContainer.post(() -> callback.onAdDisplayed(placementId));

                        final boolean[] detachHandled = {false};
                        adContainer.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
                            @Override
                            public void onViewAttachedToWindow(View v) {
                            }

                            @Override
                            public void onViewDetachedFromWindow(View v) {
                                if (detachHandled[0]) {
                                    return;
                                }
                                detachHandled[0] = true;
                                try {
                                    videoPlayer.release();
                                } catch (Throwable ignored) {
                                }
                                VideoSessionEnd end = sessionEnd.get();
                                if (end == VideoSessionEnd.NONE) {
                                    sessionEnd.set(VideoSessionEnd.SKIPPED);
                                    callback.onVideoAdSkipped(placementId);
                                }
                                callback.onAdClosed(placementId);
                            }
                        });

                        SDKLogger.d(TAG, "Video ad view created and integrated into container");

                    } catch (Exception e) {
                        SDKLogger.e(TAG, "Error creating video ad view: " + e.getMessage());
                        TextView errorText = new TextView(context);
                        errorText.setText("Failed to create video ad: " + e.getMessage());
                        errorText.setTextColor(Color.WHITE);
                        errorText.setTextSize(14);
                        errorText.setGravity(Gravity.CENTER);
                        adContainer.addView(errorText);

                        callback.onAdFailed(placementId, -1, e.getMessage());
                    }
                });
            }

            @Override
            public void onFail(Exception e) {
                activity.runOnUiThread(() -> {

                    adContainer.removeView(loadingText);

                    TextView errorText = new TextView(context);
                    errorText.setText("Failed to load ad: " + e.getMessage());
                    errorText.setTextColor(Color.WHITE);
                    errorText.setTextSize(14);
                    errorText.setGravity(Gravity.CENTER);
                    adContainer.addView(errorText);

                    if (callback != null) {
                        callback.onAdFailed(placementId, -1, e.getMessage());
                    }

                    SDKLogger.e(TAG, "Failed to get video ad view: " + e.getMessage());
                });
            }
        });

        return adContainer;
    }

    /**
     * Outstream / in-feed video view with viewability-based playback.
     */
    public View getOutstreamVideoAdView(String placementId, String url, AdCallback callback) {
        SDKLogger.d(TAG, "Getting outstream video ad view: " + url);
        Activity activity = resolveActivityContext();
        if (activity == null) {
            if (callback != null) {
                callback.onAdFailed(placementId, -1, "Context is not an Activity");
            }
            return buildSdkErrorTextView("Context is not an Activity");
        }
        if (!videoAdsEnabled || !VideoAdPlayerFactory.isVideoSupported()) {
            if (callback != null) {
                callback.onAdFailed(placementId, ERROR_VIDEO_UNSUPPORTED, MSG_VIDEO_UNSUPPORTED);
            }
            return buildSdkErrorTextView(MSG_VIDEO_UNSUPPORTED);
        }

        FrameLayout placeholder = new FrameLayout(context);
        placeholder.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        sendAdRequest(url, new BidscubeCallback() {
            @Override
            public void onSuccess(int responseCode, BidscubeResponse responseBody) {
                activity.runOnUiThread(() -> {
                    try {
                        String adm = sanitizeAdm(responseBody.getAdm());
                        String clickUrl = VastParser.getClickThroughUrl(adm);
                        View outstream = OutstreamAdViewFactory.createBoundView(
                                context, placementId, adm, clickUrl, callback);
                        placeholder.removeAllViews();
                        placeholder.addView(outstream);
                    } catch (Exception e) {
                        if (callback != null) {
                            callback.onAdFailed(placementId, -1, e.getMessage());
                        }
                    }
                });
            }

            @Override
            public void onFail(Exception e) {
                activity.runOnUiThread(() -> {
                    if (callback != null) {
                        callback.onAdFailed(placementId, -1, e.getMessage());
                    }
                });
            }
        });
        return placeholder;
    }

    private TextView buildSdkErrorTextView(String message) {
        TextView tv = new TextView(context);
        tv.setText(message);
        tv.setTextColor(Color.WHITE);
        tv.setPadding(16, 16, 16, 16);
        tv.setBackgroundColor(Color.parseColor("#B00020"));
        return tv;
    }

    /**
     * Get native ad view for integration into layouts (no dialog)
     *
     * @param url      Ad request URL
     * @param callback Callback for ad events
     * @return View that can be added to any layout
     */
    public View getNativeAdView(String placementId, String url, AdCallback callback) {
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

                        String sanitized = sanitizeAdm(responseBody.getAdm());
                        com.bidscube.sdk.models.natives.NativeAd nativeAd = null;
                        try {
                            nativeAd = NativeAdParser.parseFromAdm(sanitized);
                        } catch (Exception e) {
                            SDKLogger.d(TAG, "Failed to parse native ad model: " + e.getMessage());
                        }

                        // Preferred: allow host to render via the generic onAdRenderOverride (pass parsed native model)
                        boolean hostRendered = false;
                        try {
                            if (callback != null) {
                                AdRenderContext ctx = new AdRenderContext(placementId, sanitized, getEffectiveAdPosition(), AdType.Type.NATIVE, nativeAd);
                                hostRendered = callback.onAdRenderOverride(ctx);
                            }
                        } catch (Exception e) {
                            SDKLogger.e(TAG, "Host onAdRenderOverride threw: " + e.getMessage());
                        }

                        if (hostRendered) {
                            com.bidscube.sdk.network.NativeImpressionTracker.fireIfNeeded(nativeAd, "host_render_override_embedded_view");
                            SDKLogger.d(TAG, "Native ad view handled by host");
                            return;
                        }

                        if (handleRenderOverride(placementId, responseBody.getAdm(), getEffectiveAdPosition(), AdType.Type.NATIVE, callback)) {
                            adContainer.removeView(loadingText);
                            SDKLogger.d(TAG, "Native ad view rendering overridden by host app (legacy)");
                            return;
                        }

                        if (nativeAd != null) {

                            NativeAd nativeAdLocal = nativeAd;
                            NativeAdView nativeAdView = new NativeAdView(context);
                            nativeAdView.setNativeAd(nativeAdLocal);
                            nativeAdView.setLayoutParams(new LinearLayout.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.WRAP_CONTENT));

                            adContainer.addView(nativeAdView);

                            if (callback != null) {
                                callback.onAdLoaded(placementId);
                            }

                            SDKLogger.d(TAG, "Native ad view created and integrated into container with " +
                                    (nativeAdLocal.assets != null ? nativeAdLocal.assets.size() : 0) + " assets");
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
                            callback.onAdFailed(placementId, -1, e.getMessage());
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
                        callback.onAdFailed(placementId, -1, e.getMessage());
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
        // Clear banners attached by NativeAdBinder
        try {
            NativeAdBinder.clearAttachedBanner();
        } catch (Throwable ignored) {
        }

        if (currentBanner != null) {
            try {
                currentBanner.destroy();
            } catch (Throwable ignored) {
            }
            currentBanner = null;
        }

        if (currentVideoPlayer != null) {
            try {
                currentVideoPlayer.release();
            } catch (Throwable ignored) {
            }
            currentVideoPlayer = null;
        }

        if (currentNativeAd != null) {
            currentNativeAd = null;
        }

        // Remove any overlay container we added to the activity
        try {
            if (overlayContainer != null && overlayContainer.getParent() instanceof ViewGroup) {
                ((ViewGroup) overlayContainer.getParent()).removeView(overlayContainer);
            }
        } catch (Throwable ignored) {
        }
        overlayContainer = null;

        if (context instanceof Activity) {
            Activity activity = (Activity) context;
            activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            activity.getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_VISIBLE);
        }
    }
}
