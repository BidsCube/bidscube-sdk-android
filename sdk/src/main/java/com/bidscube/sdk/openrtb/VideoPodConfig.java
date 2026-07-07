package com.bidscube.sdk.openrtb;

import com.bidscube.sdk.config.SDKConfig;
import com.bidscube.sdk.video.PodSkipPolicy;

/**
 * Internal configuration for podded video playback.
 */
public final class VideoPodConfig {

    private static final VideoPodConfig DEFAULT = new VideoPodConfig(
            true,
            true,
            PodDurationValidationMode.LENIENT,
            PodSkipPolicy.SKIP_CURRENT_AND_CONTINUE,
            true,
            true);

    private final boolean enabled;
    private final boolean openRtbPodMetadataEnabled;
    private final PodDurationValidationMode durationValidationMode;
    private final PodSkipPolicy skipPolicy;
    private final boolean continueOnSlotError;
    private final boolean showPodCounter;

    public VideoPodConfig(
            boolean enabled,
            boolean openRtbPodMetadataEnabled,
            PodDurationValidationMode durationValidationMode,
            PodSkipPolicy skipPolicy,
            boolean continueOnSlotError,
            boolean showPodCounter) {
        this.enabled = enabled;
        this.openRtbPodMetadataEnabled = openRtbPodMetadataEnabled;
        this.durationValidationMode = durationValidationMode != null
                ? durationValidationMode
                : PodDurationValidationMode.LENIENT;
        this.skipPolicy = skipPolicy != null ? skipPolicy : PodSkipPolicy.SKIP_CURRENT_AND_CONTINUE;
        this.continueOnSlotError = continueOnSlotError;
        this.showPodCounter = showPodCounter;
    }

    public static VideoPodConfig defaults() {
        return DEFAULT;
    }

    /** Builds pod playback config from {@link SDKConfig} (falls back to defaults when null). */
    public static VideoPodConfig fromSdkConfig(SDKConfig config) {
        if (config == null) {
            return DEFAULT;
        }
        return new VideoPodConfig(
                config.isVideoAdsEnabled(),
                config.isOpenRtbPodMetadataEnabled(),
                config.getVideoPodDurationValidationMode(),
                config.getVideoPodSkipPolicy(),
                config.isVideoPodContinueOnSlotError(),
                config.isVideoPodShowCounter());
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isOpenRtbPodMetadataEnabled() {
        return openRtbPodMetadataEnabled;
    }

    public PodDurationValidationMode getDurationValidationMode() {
        return durationValidationMode;
    }

    public PodSkipPolicy getSkipPolicy() {
        return skipPolicy;
    }

    public boolean isContinueOnSlotError() {
        return continueOnSlotError;
    }

    public boolean isShowPodCounter() {
        return showPodCounter;
    }
}
