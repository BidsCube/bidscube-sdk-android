package com.bidscube.sdk.video;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class VideoTelevisionDetectionTest {

    @Test
    public void detect_nullInputs_isNotTelevision() {
        assertFalse(VideoUiDefaults.detectTelevision(null, null, null, null));
    }

    @Test
    public void detect_amazonAftModel_isTelevisionHeuristic() {
        assertTrue(VideoUiDefaults.detectTelevision(null, null, "Amazon", "AFTMM"));
        assertTrue(VideoUiDefaults.detectTelevision(null, null, "amazon", "aftss"));
    }

    @Test
    public void detect_amazonPhoneTablet_isNotTelevision() {
        assertFalse(VideoUiDefaults.detectTelevision(null, null, "Amazon", "KFSUWI"));
        assertFalse(VideoUiDefaults.detectTelevision(null, null, "Google", "Pixel 8"));
        assertFalse(VideoUiDefaults.detectTelevision(null, null, "samsung", "SM-G991B"));
    }

    @Test
    public void auto_headerBecomesFullscreenWhenTelevisionHeuristic() {
        // resolveAutoFullscreen uses isTelevision(Context); without Context, HEADER stays windowed.
        assertFalse(VideoUiDefaults.resolveAutoFullscreen(null, com.bidscube.sdk.models.enums.AdPosition.HEADER));
    }
}
