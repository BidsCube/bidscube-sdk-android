package com.bidscube.sdk.config;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.webkit.WebSettings;

import com.bidscube.sdk.openrtb.PodDurationValidationMode;
import com.bidscube.sdk.video.PodSkipPolicy;

/**
 * Configuration class for the Bidscube SDK
 */
public class SDKConfig {

    private final String appId;
    private final String appName;
    private final String appVersion;
    private final String language;
    private final String userAgent;
    /** Publisher user ID sent to SSP as {@code user_id} for postbacks. */
    private final String userId;
    private final boolean enableLogging;
    private final boolean enableDebugMode;
    private final int defaultAdTimeout;
    private final String defaultAdPosition;

    // Consent parameters
    private final Integer gdpr;
    private final String gdprConsent;
    private final String usPrivacy;
    private final Boolean coppa;

    /** Optional; when null the SDK uses {@code new VideoView(context)} for IMA playback. */
    private final VideoPlayerProvider videoPlayerProvider;

    /**
     * When {@code false} (default), VAST/IMA video paths are not used. For image/native-only and minimal APK, keep
     * {@code false} and depend on {@code com.bidscube:bidscube-sdk-lite-no-video}.
     * Set {@code true} for video with {@code com.bidscube:bidscube-sdk-full-video} (Media3 + IMA for wrapper tags).
     */
    private final boolean videoAdsEnabled;

    /**
     * When {@code true}, fullscreen video ads close automatically after complete/skip
     * (no end card / last frame). Default {@code false}.
     */
    private final boolean autoClose;

    /** OpenRTB-like podded response parsing (response-side only; not an OpenRTB bid client). */
    private final boolean openRtbPodMetadataEnabled;
    private final PodDurationValidationMode videoPodDurationValidationMode;
    private final PodSkipPolicy videoPodSkipPolicy;
    private final boolean videoPodContinueOnSlotError;
    private final boolean videoPodShowCounter;

    private SDKConfig(Builder builder) {
        this.appId = builder.appId;
        this.appName = builder.appName;
        this.appVersion = builder.appVersion;
        this.language = builder.language;
        this.userAgent = builder.userAgent;
        this.userId = builder.userId;
        this.enableLogging = builder.enableLogging;
        this.enableDebugMode = builder.enableDebugMode;
        this.defaultAdTimeout = builder.defaultAdTimeout;
        this.defaultAdPosition = builder.defaultAdPosition;
        this.gdpr = builder.gdpr;
        this.gdprConsent = builder.gdprConsent;
        this.usPrivacy = builder.usPrivacy;
        this.coppa = builder.coppa;
        this.videoPlayerProvider = builder.videoPlayerProvider;
        this.videoAdsEnabled = builder.videoAdsEnabled;
        this.autoClose = builder.autoClose;
        this.openRtbPodMetadataEnabled = builder.openRtbPodMetadataEnabled;
        this.videoPodDurationValidationMode = builder.videoPodDurationValidationMode;
        this.videoPodSkipPolicy = builder.videoPodSkipPolicy;
        this.videoPodContinueOnSlotError = builder.videoPodContinueOnSlotError;
        this.videoPodShowCounter = builder.videoPodShowCounter;
    }

    public String getAppId() {
        return appId;
    }

    public String getAppName() {
        return appName;
    }

    public String getAppVersion() {
        return appVersion;
    }

    public String getLanguage() {
        return language;
    }

    public String getUserAgent() {
        return userAgent;
    }

    /**
     * Publisher-provided user ID (nullable). Sent as {@code user_id} on ad requests for server postbacks.
     */
    public String getUserId() {
        return userId;
    }

    public boolean isEnableLogging() {
        return enableLogging;
    }

    public boolean isEnableDebugMode() {
        return enableDebugMode;
    }

    public int getDefaultAdTimeout() {
        return defaultAdTimeout;
    }

    public String getDefaultAdPosition() {
        return defaultAdPosition;
    }

    public Integer getGdpr() {
        return gdpr;
    }

    public String getGdprConsent() {
        return gdprConsent;
    }

    public String getUsPrivacy() {
        return usPrivacy;
    }

    public Boolean getCoppa() {
        return coppa;
    }

    /**
     * Custom {@link VideoView} factory for IMA video ads, or null for the built-in player.
     */
    public VideoPlayerProvider getVideoPlayerProvider() {
        return videoPlayerProvider;
    }

    public boolean isVideoAdsEnabled() {
        return videoAdsEnabled;
    }

    /**
     * When {@code true}, fullscreen video closes automatically after complete/skip —
     * no end card or last-frame hold. Default {@code false}.
     */
    public boolean isAutoClose() {
        return autoClose;
    }

    /**
     * When {@code true}, the SDK parses OpenRTB-like pod fields from SSP JSON responses
     * ({@code slotinpod}, {@code poddur}, etc.). Does not enable OpenRTB bid requests.
     */
    public boolean isOpenRtbPodMetadataEnabled() {
        return openRtbPodMetadataEnabled;
    }

    public PodDurationValidationMode getVideoPodDurationValidationMode() {
        return videoPodDurationValidationMode;
    }

    public PodSkipPolicy getVideoPodSkipPolicy() {
        return videoPodSkipPolicy;
    }

    public boolean isVideoPodContinueOnSlotError() {
        return videoPodContinueOnSlotError;
    }

    public boolean isVideoPodShowCounter() {
        return videoPodShowCounter;
    }

    /**
     * Get the SDK version from environment variable or default to 1.2.10
     */
    private static String getSDKVersion() {
        return System.getenv().getOrDefault("BidscubeVersion", "1.2.10");
    }

    /**
     * Builder class for SDKConfig with automatic app detection
     */
    public static class Builder {
        private String appId;
        private String appName;
        private String appVersion;
        private String language = "en";
        private String userAgent;
        private String userId = null;
        private boolean enableLogging = true;
        private boolean enableDebugMode = false;
        private int defaultAdTimeout = 15000;
        private String defaultAdPosition = "FULL_SCREEN";

        // Consent parameters (null means use ConsentManager defaults)
        private Integer gdpr = null;
        private String gdprConsent = null;
        private String usPrivacy = null;
        private Boolean coppa = null;

        private VideoPlayerProvider videoPlayerProvider = null;

        private boolean videoAdsEnabled = false;

        private boolean autoClose = false;

        private boolean openRtbPodMetadataEnabled = true;
        private PodDurationValidationMode videoPodDurationValidationMode = PodDurationValidationMode.LENIENT;
        private PodSkipPolicy videoPodSkipPolicy = PodSkipPolicy.SKIP_CURRENT_AND_CONTINUE;
        private boolean videoPodContinueOnSlotError = true;
        private boolean videoPodShowCounter = true;

        /**
         * Create a new Builder with automatic app detection
         *
         * @param context Application context for automatic detection
         */
        public Builder(Context context) {
            autoDetectAppInfo(context);
        }

        /**
         * Automatically detect app information from Android manifest and system
         */
        private void autoDetectAppInfo(Context context) {
            try {
                PackageManager pm = context.getPackageManager();
                PackageInfo packageInfo = pm.getPackageInfo(context.getPackageName(), 0);

                this.appId = context.getPackageName();

                this.appName = pm.getApplicationLabel(pm.getApplicationInfo(context.getPackageName(), 0)).toString();

                this.appVersion = packageInfo.versionName != null ? packageInfo.versionName
                        : String.valueOf(packageInfo.versionCode);

                this.language = context.getResources().getConfiguration().getLocales().get(0).getLanguage();
                this.userAgent = buildDefaultUserAgent(context);

            } catch (Exception e) {

                this.appId = "unknown_app";
                this.appName = "Unknown App";
                this.appVersion = getSDKVersion();
                this.language = "en";
                this.userAgent = buildDefaultUserAgent(context);
            }
        }

        private String buildDefaultUserAgent(Context context) {
            try {
                String webViewUa = WebSettings.getDefaultUserAgent(context);
                if (webViewUa != null && !webViewUa.trim().isEmpty()) {
                    return webViewUa;
                }
            } catch (Throwable ignored) {
                // Fall through to secondary UA sources.
            }

            String httpAgent = System.getProperty("http.agent");
            if (httpAgent != null && !httpAgent.trim().isEmpty()) {
                return httpAgent;
            }

            return "Mozilla/5.0 (Linux; Android " + Build.VERSION.RELEASE + "; " + Build.MODEL
                    + ") AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/120.0.0.0 Mobile Safari/537.36";
        }

        /**
         * Override auto-detected app ID
         */
        public Builder appId(String appId) {
            this.appId = appId;
            return this;
        }

        /**
         * Override auto-detected app name
         */
        public Builder appName(String appName) {
            this.appName = appName;
            return this;
        }

        /**
         * Override auto-detected app version
         */
        public Builder appVersion(String appVersion) {
            this.appVersion = appVersion;
            return this;
        }

        /**
         * Override auto-detected language
         */
        public Builder language(String language) {
            this.language = language;
            return this;
        }

        /**
         * Override auto-detected user agent
         */
        public Builder userAgent(String userAgent) {
            this.userAgent = userAgent;
            return this;
        }

        /**
         * Publisher user ID forwarded to the SSP as query parameter {@code user_id}
         * (used for server-side postbacks). Optional; omit or pass null/blank to skip.
         */
        public Builder userId(String userId) {
            if (userId == null || userId.trim().isEmpty()) {
                this.userId = null;
            } else {
                this.userId = userId.trim();
            }
            return this;
        }

        /**
         * Enable or disable logging
         */
        public Builder enableLogging(boolean enableLogging) {
            this.enableLogging = enableLogging;
            return this;
        }

        /**
         * Enable or disable debug mode
         */
        public Builder enableDebugMode(boolean enableDebugMode) {
            this.enableDebugMode = enableDebugMode;
            return this;
        }

        /**
         * Set default ad timeout in milliseconds
         */
        public Builder defaultAdTimeout(int timeoutMs) {
            this.defaultAdTimeout = timeoutMs;
            return this;
        }

        /**
         * Set default ad position. Default {@code FULL_SCREEN} (video interstitials open fullscreen).
         */
        public Builder defaultAdPosition(String position) {
            this.defaultAdPosition = position;
            return this;
        }

        /**
         * Set GDPR applies value (0 = no, 1 = yes, null = use ConsentManager)
         */
        public Builder gdpr(Integer gdpr) {
            this.gdpr = gdpr;
            return this;
        }

        /**
         * Set GDPR consent string (null = use ConsentManager)
         */
        public Builder gdprConsent(String gdprConsent) {
            this.gdprConsent = gdprConsent;
            return this;
        }

        /**
         * Set US Privacy string (null = use ConsentManager)
         */
        public Builder usPrivacy(String usPrivacy) {
            this.usPrivacy = usPrivacy;
            return this;
        }

        /**
         * Set COPPA compliance (null = use ConsentManager)
         */
        public Builder coppa(Boolean coppa) {
            this.coppa = coppa;
            return this;
        }

        /**
         * Supply a custom {@link VideoView} implementation for IMA video ads (e.g. subclass with app-specific
         * buffering or analytics). When omitted, the SDK creates a standard {@link android.widget.VideoView}.
         * Ignored when {@link #videoAdsEnabled(boolean)} is {@code false}.
         */
        public Builder videoPlayerProvider(VideoPlayerProvider videoPlayerProvider) {
            this.videoPlayerProvider = videoPlayerProvider;
            return this;
        }

        /**
         * When {@code false} (the default), the SDK does not create IMA video players or run VAST playback; video APIs fail fast
         * with {@link com.bidscube.sdk.interfaces.AdCallback#onAdFailed}. Use {@code true} with
         * {@code com.bidscube:bidscube-sdk-full-video}; use {@code com.bidscube:bidscube-sdk-lite-no-video} for banner/image/native only.
         * Image, native, and banner flows are unchanged.
         */
        public Builder videoAdsEnabled(boolean videoAdsEnabled) {
            this.videoAdsEnabled = videoAdsEnabled;
            return this;
        }

        /**
         * When {@code true}, after video complete/skip the SDK auto-closes the fullscreen ad
         * (releases player, no end card / last frame, fires {@code onAdClosed} once).
         * When {@code false} (default): show Companion end card if present, otherwise keep the
         * last video frame with a manual close button. Mini-game / post-video phases are not
         * interrupted by linear video completion.
         */
        public Builder autoClose(boolean autoClose) {
            this.autoClose = autoClose;
            return this;
        }

        /**
         * Enables parsing of OpenRTB-like pod metadata in SSP JSON responses. Default {@code true}.
         * This does not send OpenRTB bid requests.
         */
        public Builder openRtbPodMetadataEnabled(boolean enabled) {
            this.openRtbPodMetadataEnabled = enabled;
            return this;
        }

        public Builder videoPodDurationValidationMode(PodDurationValidationMode mode) {
            this.videoPodDurationValidationMode = mode != null
                    ? mode
                    : PodDurationValidationMode.LENIENT;
            return this;
        }

        public Builder videoPodSkipPolicy(PodSkipPolicy policy) {
            this.videoPodSkipPolicy = policy != null
                    ? policy
                    : PodSkipPolicy.SKIP_CURRENT_AND_CONTINUE;
            return this;
        }

        public Builder videoPodContinueOnSlotError(boolean continueOnSlotError) {
            this.videoPodContinueOnSlotError = continueOnSlotError;
            return this;
        }

        public Builder videoPodShowCounter(boolean showPodCounter) {
            this.videoPodShowCounter = showPodCounter;
            return this;
        }

        /**
         * Build the SDKConfig
         */
        public SDKConfig build() {
            return new SDKConfig(this);
        }
    }
}
