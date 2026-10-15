package com.bidscube.sdk.video;

import com.bidscube.sdk.models.enums.AdPosition;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class VideoUiDefaultsTest {

    @Before
    public void setUp() {
        VideoUiDefaults.resetFullscreenPolicy();
    }

    @After
    public void tearDown() {
        VideoUiDefaults.resetFullscreenPolicy();
    }

    @Test
    public void resolveSkipOffset_usesVastWhenPositive() {
        assertEquals(5, VideoUiDefaults.resolveSkipOffsetSeconds(5));
        assertEquals(20, VideoUiDefaults.resolveSkipOffsetSeconds(20));
    }

    @Test
    public void resolveSkipOffset_defaultsTo15WhenMissing() {
        assertEquals(15, VideoUiDefaults.resolveSkipOffsetSeconds(0));
        assertEquals(15, VideoUiDefaults.resolveSkipOffsetSeconds(-1));
    }

    @Test
    public void auto_unknownAndFullScreen_areFullscreen() {
        assertTrue(VideoUiDefaults.resolveAutoFullscreen(null, null));
        assertTrue(VideoUiDefaults.resolveAutoFullscreen(null, AdPosition.UNKNOWN));
        assertTrue(VideoUiDefaults.resolveAutoFullscreen(null, AdPosition.FULL_SCREEN));
    }

    @Test
    public void auto_headerFooterSidebar_areWindowedWithoutTvContext() {
        assertFalse(VideoUiDefaults.resolveAutoFullscreen(null, AdPosition.HEADER));
        assertFalse(VideoUiDefaults.resolveAutoFullscreen(null, AdPosition.FOOTER));
        assertFalse(VideoUiDefaults.resolveAutoFullscreen(null, AdPosition.SIDEBAR));
    }

    @Test
    public void policy_forceFullscreen_overridesHeader() {
        VideoUiDefaults.setFullscreenPolicy(VideoUiDefaults.FullscreenPolicy.FORCE_FULLSCREEN);
        assertTrue(VideoUiDefaults.shouldShowFullscreen(null, AdPosition.HEADER));
        assertTrue(VideoUiDefaults.shouldShowFullscreen(null, AdPosition.UNKNOWN));
    }

    @Test
    public void policy_forceWindowed_overridesFullScreen() {
        VideoUiDefaults.setFullscreenPolicy(VideoUiDefaults.FullscreenPolicy.FORCE_WINDOWED);
        assertFalse(VideoUiDefaults.shouldShowFullscreen(null, AdPosition.FULL_SCREEN));
        assertFalse(VideoUiDefaults.shouldShowFullscreen(null, AdPosition.UNKNOWN));
    }

    @Test
    public void policy_auto_matchesResolveAuto() {
        VideoUiDefaults.setFullscreenPolicy(VideoUiDefaults.FullscreenPolicy.AUTO);
        assertEquals(
                VideoUiDefaults.resolveAutoFullscreen(null, AdPosition.UNKNOWN),
                VideoUiDefaults.shouldShowFullscreen(null, AdPosition.UNKNOWN));
        assertEquals(
                VideoUiDefaults.resolveAutoFullscreen(null, AdPosition.HEADER),
                VideoUiDefaults.shouldShowFullscreen(null, AdPosition.HEADER));
    }

    @Test
    public void reset_restoresAuto() {
        VideoUiDefaults.setFullscreenPolicy(VideoUiDefaults.FullscreenPolicy.FORCE_WINDOWED);
        VideoUiDefaults.resetFullscreenPolicy();
        assertEquals(VideoUiDefaults.FullscreenPolicy.AUTO, VideoUiDefaults.getFullscreenPolicy());
        assertTrue(VideoUiDefaults.shouldShowFullscreen(null, AdPosition.UNKNOWN));
    }
}
