package com.bidscube.sdk.config;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;

/**
 * Configuration class for the Bidscube SDK
 */
public class SDKConfig {

    private final String appId;
    private final String appName;
    private final String appVersion;
    private final String language;
    private final String userAgent;
    private final boolean enableLogging;
    private final boolean enableDebugMode;
    private final int defaultAdTimeout;
    private final String defaultAdPosition;

    private SDKConfig(Builder builder) {
        this.appId = builder.appId;
        this.appName = builder.appName;
        this.appVersion = builder.appVersion;
        this.language = builder.language;
        this.userAgent = builder.userAgent;
        this.enableLogging = builder.enableLogging;
        this.enableDebugMode = builder.enableDebugMode;
        this.defaultAdTimeout = builder.defaultAdTimeout;
        this.defaultAdPosition = builder.defaultAdPosition;
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

    /**
     * Builder class for SDKConfig with automatic app detection
     */
    public static class Builder {
        private String appId;
        private String appName;
        private String appVersion;
        private String language = "en";
        private String userAgent;
        private boolean enableLogging = true;
        private boolean enableDebugMode = false;
        private int defaultAdTimeout = 15000;
        private String defaultAdPosition = "UNKNOWN";

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

                this.appVersion = packageInfo.versionName != null ? packageInfo.versionName : String.valueOf(packageInfo.versionCode);

                this.language = context.getResources().getConfiguration().getLocales().get(0).getLanguage();

                this.userAgent = "BidscubeSDK/" + this.appVersion + " (Android " + Build.VERSION.RELEASE + "; " + Build.MODEL + ")";

            } catch (Exception e) {

                this.appId = "unknown_app";
                this.appName = "Unknown App";
                this.appVersion = "1.0.0";
                this.language = "en";
                this.userAgent = "BidscubeSDK/1.0.0 (Android)";
            }
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
         * Set default ad position
         */
        public Builder defaultAdPosition(String position) {
            this.defaultAdPosition = position;
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

