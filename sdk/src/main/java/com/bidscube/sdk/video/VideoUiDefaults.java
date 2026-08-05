package com.bidscube.sdk.video;

/**
 * Default UX values for fullscreen video interstitials when VAST / config omit them.
 */
public final class VideoUiDefaults {

    /** Default skip delay when VAST has no {@code skipoffset}. */
    public static final int DEFAULT_SKIP_OFFSET_SECONDS = 15;

    private VideoUiDefaults() {
    }

    /**
     * @param vastSkipOffsetSeconds value from VAST ({@code 0} if missing / not skippable)
     * @return VAST value when positive, otherwise {@link #DEFAULT_SKIP_OFFSET_SECONDS} (15s)
     */
    public static int resolveSkipOffsetSeconds(int vastSkipOffsetSeconds) {
        return vastSkipOffsetSeconds > 0 ? vastSkipOffsetSeconds : DEFAULT_SKIP_OFFSET_SECONDS;
    }
}
