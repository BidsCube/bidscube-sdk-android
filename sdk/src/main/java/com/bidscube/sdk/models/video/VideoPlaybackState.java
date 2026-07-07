package com.bidscube.sdk.models.video;

/**
 * Explicit playback state for video pod / interstitial sessions.
 */
public enum VideoPlaybackState {
    IDLE,
    LOADING_VAST,
    READY,
    SHOWING_FULLSCREEN,
    PLAYING_VIDEO,
    VIDEO_COMPLETED,
    VIDEO_SKIPPED,
    PLAYING_NEXT_POD_VIDEO,
    SHOWING_GAMIFIED_END_CARD,
    CLICKED,
    CLOSED,
    ERROR,
    DESTROYED
}
