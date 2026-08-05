package com.bidscube.sdk.video;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class VideoUiDefaultsTest {

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
}
