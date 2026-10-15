package com.bidscube.sdk.video;

import android.app.UiModeManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.os.Build;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.bidscube.sdk.ads.VideoAdFormat;
import com.bidscube.sdk.models.enums.AdPosition;

/**
 * Default UX values for fullscreen video interstitials when VAST / config omit them.
 *
 * <p>Skip permission priority (highest → lowest):
 * <ol>
 *   <li>{@link VideoAdFormat#REWARDED} — Skip never offered (format policy)</li>
 *   <li>Publisher / BidsCube omit policy — when VAST omits {@code skipoffset}, interstitial is
 *       skippable after {@link #DEFAULT_SKIP_OFFSET_SECONDS} (README 1.2.9+)</li>
 *   <li>VAST {@code skipoffset} time / percent (interstitial only)</li>
 *   <li>IMA {@code onVideoSkippable} — may arm Skip earlier on interstitial IMA path only;
 *       must not override rewarded</li>
 * </ol>
 */
public final class VideoUiDefaults {

    /** Default skip delay when VAST has no {@code skipoffset} (interstitial publisher policy). */
    public static final int DEFAULT_SKIP_OFFSET_SECONDS = 15;

    /**
     * Playback must enter playing state within this window or the non-cancelable dialog is failed
     * closed. Aligned with {@link #DEFAULT_SKIP_OFFSET_SECONDS}: that is already the publisher
     * window after which an exit control would appear on interstitial; if playback never starts,
     * the user would otherwise remain locked with no Skip and no error.
     */
    public static final long PLAYBACK_START_STALL_MS = DEFAULT_SKIP_OFFSET_SECONDS * 1000L;

    /**
     * Controls whether video ads use a fullscreen dialog.
     * <ul>
     *   <li>{@link FullscreenPolicy#AUTO} — FULL_SCREEN/UNKNOWN → fullscreen; always on TV/Leanback</li>
     *   <li>{@link FullscreenPolicy#FORCE_FULLSCREEN} — always fullscreen</li>
     *   <li>{@link FullscreenPolicy#FORCE_WINDOWED} — always windowed (QA / compare)</li>
     * </ul>
     */
    public enum FullscreenPolicy {
        AUTO,
        FORCE_FULLSCREEN,
        FORCE_WINDOWED
    }

    private static volatile FullscreenPolicy fullscreenPolicy = FullscreenPolicy.AUTO;

    private VideoUiDefaults() {
    }

    public static void setFullscreenPolicy(FullscreenPolicy policy) {
        fullscreenPolicy = policy != null ? policy : FullscreenPolicy.AUTO;
    }

    public static FullscreenPolicy getFullscreenPolicy() {
        return fullscreenPolicy;
    }

    /** Reset QA override to production default. */
    public static void resetFullscreenPolicy() {
        fullscreenPolicy = FullscreenPolicy.AUTO;
    }

    /**
     * Skip permission + when the Skip control becomes available.
     * BidsCube publisher policy: when VAST omits {@code skipoffset}, the ad is still skippable
     * after {@link #DEFAULT_SKIP_OFFSET_SECONDS} (documented in README 1.2.9+).
     */
    public static final class SkipSchedule {
        /** Whether Skip may ever be offered for this linear creative. */
        public final boolean skipAllowed;
        /** Seconds from start until Skip is armed; {@code 0} = immediately. */
        public final int availableAfterSeconds;

        public SkipSchedule(boolean skipAllowed, int availableAfterSeconds) {
            this.skipAllowed = skipAllowed;
            this.availableAfterSeconds = Math.max(0, availableAfterSeconds);
        }
    }

    /**
     * Legacy helper used by older call sites: positive VAST seconds kept; {@code <=0} → default 15s.
     * Prefer {@link #resolveSkipSchedule(com.bidscube.sdk.utils.VastSkipOffset, int)}.
     * Note: cannot distinguish VAST {@code skipoffset="00:00:00"} from absent — use SkipSchedule API.
     */
    public static int resolveSkipOffsetSeconds(int vastSkipOffsetSeconds) {
        return vastSkipOffsetSeconds > 0 ? vastSkipOffsetSeconds : DEFAULT_SKIP_OFFSET_SECONDS;
    }

    /**
     * Format-aware skip schedule. Rewarded never allows Skip regardless of VAST / defaults.
     */
    public static SkipSchedule resolveSkipSchedule(
            @Nullable com.bidscube.sdk.utils.VastSkipOffset parsed,
            int creativeDurationSeconds,
            @Nullable VideoAdFormat format) {
        if (format == VideoAdFormat.REWARDED) {
            return new SkipSchedule(false, 0);
        }
        return resolveSkipSchedule(parsed, creativeDurationSeconds);
    }

    /**
     * Interstitial-oriented skip schedule from VAST skipoffset + optional duration.
     *
     * <ul>
     *   <li>{@code ABSENT} — BidsCube publisher policy (README): skippable after 15s</li>
     *   <li>{@code INVALID} — engineering fallback (same timing as ABSENT); <b>not</b> README policy</li>
     *   <li>{@code TIME_SECONDS} including 0 — skippable after that many seconds</li>
     *   <li>{@code PERCENT} with Duration — floor(duration * percent)</li>
     *   <li>{@code PERCENT} without Duration — engineering fallback: skippable after 15s</li>
     * </ul>
     */
    public static SkipSchedule resolveSkipSchedule(
            @Nullable com.bidscube.sdk.utils.VastSkipOffset parsed,
            int creativeDurationSeconds) {
        if (parsed == null || parsed.kind == com.bidscube.sdk.utils.VastSkipOffset.Kind.ABSENT) {
            // Explicit BidsCube / publisher default (README): omit → skippable after 15s.
            return new SkipSchedule(true, DEFAULT_SKIP_OFFSET_SECONDS);
        }
        if (parsed.kind == com.bidscube.sdk.utils.VastSkipOffset.Kind.INVALID) {
            // Not covered by README omit policy — keep interstitial usable with same timing fallback.
            return new SkipSchedule(true, DEFAULT_SKIP_OFFSET_SECONDS);
        }
        if (parsed.kind == com.bidscube.sdk.utils.VastSkipOffset.Kind.TIME_SECONDS) {
            return new SkipSchedule(true, parsed.value);
        }
        // PERCENT
        if (creativeDurationSeconds > 0) {
            int seconds = (int) Math.floor(creativeDurationSeconds * (parsed.value / 100.0d));
            return new SkipSchedule(true, Math.max(0, seconds));
        }
        // Percent without Duration: engineering fallback (not README omit policy).
        return new SkipSchedule(true, DEFAULT_SKIP_OFFSET_SECONDS);
    }

    /**
     * Whether video should use a fullscreen dialog for the given position / device.
     */
    public static boolean shouldShowFullscreen(@Nullable Context context, @Nullable AdPosition position) {
        FullscreenPolicy policy = fullscreenPolicy;
        if (policy == FullscreenPolicy.FORCE_FULLSCREEN) {
            return true;
        }
        if (policy == FullscreenPolicy.FORCE_WINDOWED) {
            return false;
        }
        return resolveAutoFullscreen(context, position);
    }

    /**
     * AUTO rules only (ignores {@link #setFullscreenPolicy}). Useful for unit tests.
     */
    public static boolean resolveAutoFullscreen(@Nullable Context context, @Nullable AdPosition position) {
        if (isTelevision(context)) {
            return true;
        }
        return position == null
                || position == AdPosition.FULL_SCREEN
                || position == AdPosition.UNKNOWN;
    }

    /**
     * Detects Android TV / Google TV / Leanback-class devices for display policy.
     * Fire TV is included only when Leanback / television UI mode / known Fire TV feature is present —
     * physical Fire TV acceptance is still NOT TESTED in this workspace.
     * Vega OS is out of scope for this Android SDK.
     */
    public static boolean isTelevision(@Nullable Context context) {
        if (context == null) {
            return false;
        }
        PackageManager pm = null;
        try {
            pm = context.getPackageManager();
        } catch (Throwable ignored) {
            // fall through
        }
        UiModeManager uiMode = null;
        try {
            uiMode = (UiModeManager) context.getSystemService(Context.UI_MODE_SERVICE);
        } catch (Throwable ignored) {
            // fall through
        }
        return detectTelevision(pm, uiMode, Build.MANUFACTURER, Build.MODEL);
    }

    @VisibleForTesting
    static boolean detectTelevision(
            @Nullable PackageManager pm,
            @Nullable UiModeManager uiMode,
            @Nullable String manufacturer,
            @Nullable String model) {
        if (pm != null) {
            try {
                if (pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
                        || pm.hasSystemFeature("android.software.leanback")
                        || pm.hasSystemFeature(PackageManager.FEATURE_TELEVISION)
                        || pm.hasSystemFeature("amazon.hardware.fire_tv")) {
                    return true;
                }
            } catch (Throwable ignored) {
                // continue to UiMode / heuristics
            }
        }
        try {
            if (uiMode != null
                    && uiMode.getCurrentModeType() == Configuration.UI_MODE_TYPE_TELEVISION) {
                return true;
            }
        } catch (Throwable ignored) {
            // fall through
        }
        // Conservative Fire TV heuristic: Amazon AFT* models (Fire TV sticks/boxes).
        // Not used alone for phones (Amazon Fire tablets are not AFT*).
        if (manufacturer != null && model != null
                && !manufacturer.trim().isEmpty()
                && !model.trim().isEmpty()
                && "amazon".equalsIgnoreCase(manufacturer.trim())
                && model.toUpperCase(java.util.Locale.US).startsWith("AFT")) {
            return true;
        }
        return false;
    }
}
