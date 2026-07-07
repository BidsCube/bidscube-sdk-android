package com.bidscube.sdk.utils;

import com.bidscube.sdk.models.video.VastMediaFile;

import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class VastMediaFileSelectorTest {

    @Before
    public void disableLogging() {
        SDKLogger.setLoggingEnabled(false);
    }

    @Test
    public void selectBest_prefersMp4Progressive() {
        List<VastMediaFile> files = Arrays.asList(
                new VastMediaFile("https://example.com/a.webm", "video/webm", 1920, 1080, 2000, "streaming"),
                new VastMediaFile("https://example.com/a.mp4", "video/mp4", 1280, 720, 1200, "progressive"),
                new VastMediaFile("https://example.com/b.mp4", "video/mp4", 3840, 2160, 8000, "progressive"));
        VastMediaFile selected = VastMediaFileSelector.selectBest(files);
        assertEquals("https://example.com/a.mp4", selected.getUrl());
    }

    @Test
    public void selectBest_returnsNullWhenEmpty() {
        assertNull(VastMediaFileSelector.selectBest(null));
        assertNull(VastMediaFileSelector.selectBest(List.of()));
    }
}
