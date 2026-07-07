package com.bidscube.sdk.video;

/**
 * Policy when user taps skip during pod playback.
 */
public enum PodSkipPolicy {
    /** Skip current clip and continue to the next ad in the pod. */
    SKIP_CURRENT_AND_CONTINUE,
    /** Skip the entire pod immediately. */
    SKIP_ENTIRE_POD
}
