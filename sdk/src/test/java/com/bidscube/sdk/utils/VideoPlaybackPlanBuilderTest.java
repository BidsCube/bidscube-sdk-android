package com.bidscube.sdk.utils;

import com.bidscube.sdk.models.video.VideoPlaybackPlan;
import com.bidscube.sdk.models.video.VideoPlaybackPlanType;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class VideoPlaybackPlanBuilderTest {

    @Before
    public void disableLogging() {
        SDKLogger.setLoggingEnabled(false);
    }

    private static final String SINGLE_AD_VAST = ""
            + "<VAST version=\"3.0\"><Ad id=\"single\"><InLine>"
            + "  <Creatives><Creative><Linear>"
            + "    <MediaFiles>"
            + "      <MediaFile type=\"video/webm\" delivery=\"streaming\">https://example.com/a.webm</MediaFile>"
            + "      <MediaFile type=\"video/mp4\" delivery=\"progressive\" width=\"1280\" height=\"720\" bitrate=\"1200\">"
            + "        https://example.com/a.mp4</MediaFile>"
            + "    </MediaFiles>"
            + "  </Linear></Creative></Creatives>"
            + "</InLine></Ad></VAST>";

    private static final String POD_VAST = ""
            + "<VAST version=\"3.0\">"
            + "  <Ad sequence=\"3\" id=\"video-3\"><InLine>"
            + "    <Creatives><Creative><Linear>"
            + "      <MediaFiles><MediaFile type=\"video/mp4\">https://example.com/c.mp4</MediaFile></MediaFiles>"
            + "    </Linear></Creative></Creatives></InLine></Ad>"
            + "  <Ad sequence=\"1\" id=\"video-1\"><InLine>"
            + "    <Creatives><Creative><Linear skipoffset=\"00:00:05\">"
            + "      <Impression>https://example.com/imp1</Impression>"
            + "      <MediaFiles><MediaFile type=\"video/mp4\">https://example.com/a.mp4</MediaFile></MediaFiles>"
            + "    </Linear></Creative></Creatives></InLine></Ad>"
            + "  <Ad sequence=\"2\" id=\"video-2\"><InLine>"
            + "    <Creatives><Creative><Linear>"
            + "      <MediaFiles><MediaFile type=\"video/mp4\">https://example.com/b.mp4</MediaFile></MediaFiles>"
            + "    </Linear></Creative></Creatives></InLine></Ad>"
            + "</VAST>";

    private static final String BUFFET_VAST = ""
            + "<VAST version=\"3.0\">"
            + "  <Ad id=\"a1\"><InLine><Creatives><Creative><Linear>"
            + "    <MediaFiles><MediaFile type=\"video/mp4\">https://example.com/a.mp4</MediaFile></MediaFiles>"
            + "  </Linear></Creative></Creatives></InLine></Ad>"
            + "  <Ad id=\"a2\"><InLine><Creatives><Creative><Linear>"
            + "    <MediaFiles><MediaFile type=\"video/mp4\">https://example.com/b.mp4</MediaFile></MediaFiles>"
            + "  </Linear></Creative></Creatives></InLine></Ad>"
            + "</VAST>";

    @Test
    public void build_singleAd_selectsBestMp4() {
        VideoPlaybackPlan plan = VideoPlaybackPlanBuilder.build(SINGLE_AD_VAST);
        assertEquals(VideoPlaybackPlanType.SINGLE, plan.getType());
        assertFalse(plan.isEmpty());
        assertFalse(plan.isPod());
        assertEquals(1, plan.getTotalAds());
        assertEquals("https://example.com/a.mp4", plan.getFirstSlot().getMediaFile().getUrl());
    }

    @Test
    public void build_pod_sortedBySequence() {
        VideoPlaybackPlan plan = VideoPlaybackPlanBuilder.build(POD_VAST);
        assertEquals(VideoPlaybackPlanType.POD, plan.getType());
        assertTrue(plan.isPod());
        assertEquals(3, plan.getTotalAds());
        assertEquals("video-1", plan.getSlots().get(0).getAdId());
        assertEquals(5000L, (long) plan.getSlots().get(0).getSkipOffsetMs());
    }

    @Test
    public void build_buffet_selectsOneAd() {
        VideoPlaybackPlan plan = VideoPlaybackPlanBuilder.build(BUFFET_VAST);
        assertEquals(VideoPlaybackPlanType.SINGLE, plan.getType());
        assertEquals(1, plan.getTotalAds());
        assertFalse(plan.isPod());
    }

    @Test
    public void build_emptyForInvalidXml() {
        VideoPlaybackPlan plan = VideoPlaybackPlanBuilder.build("<not-vast>");
        assertEquals(VideoPlaybackPlanType.EMPTY, plan.getType());
        assertTrue(plan.isEmpty());
    }
}
