package com.bidscube.sdk.video;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class VideoUiDefaultsTest {

    @Test
    public void resolveSkipOffset_usesVastWhenPositive() {
        assertEquals(7, VideoUiDefaults.resolveSkipOffsetSeconds(7));
    }

    @Test
    public void resolveSkipOffset_defaultsTo15WhenMissing() {
        assertEquals(15, VideoUiDefaults.resolveSkipOffsetSeconds(0));
        assertEquals(15, VideoUiDefaults.resolveSkipOffsetSeconds(-1));
    }
}
