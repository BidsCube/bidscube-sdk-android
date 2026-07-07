package com.bidscube.sdk.models.video;

/**
 * Classification of a resolved VAST video playback plan.
 */
public enum VideoPlaybackPlanType {
    /** One playable ad. */
    SINGLE,
    /** Two or more sequenced ads played back-to-back. */
    POD,
    /** Multiple non-sequenced ads; one best ad is chosen (not a pod). */
    AD_BUFFET,
    /** No playable ads. */
    EMPTY
}
