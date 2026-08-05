package com.bidscube.sdk.utils;

import com.bidscube.sdk.models.video.VastCompanion;
import com.bidscube.sdk.models.video.VastPreview;
import com.bidscube.sdk.models.video.VideoPlaybackPlan;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class VastCompanionParserTest {

    @Before
    public void disableLogging() {
        SDKLogger.setLoggingEnabled(false);
    }

    private static final String STATIC_COMPANION = ""
            + "<VAST version=\"3.0\"><Ad id=\"1\"><InLine><Creatives>"
            + "<Creative><Linear><MediaFiles>"
            + "<MediaFile type=\"video/mp4\">https://example.com/v.mp4</MediaFile>"
            + "</MediaFiles></Linear></Creative>"
            + "<Creative><CompanionAds>"
            + "<Companion width=\"1080\" height=\"1920\">"
            + "<StaticResource creativeType=\"image/jpeg\"><![CDATA[https://example.com/end-screen.jpg]]></StaticResource>"
            + "<CompanionClickThrough><![CDATA[https://example.com/click]]></CompanionClickThrough>"
            + "<CompanionClickTracking><![CDATA[https://example.com/click-track]]></CompanionClickTracking>"
            + "<TrackingEvents>"
            + "<Tracking event=\"creativeView\"><![CDATA[https://example.com/view-track]]></Tracking>"
            + "</TrackingEvents>"
            + "</Companion>"
            + "</CompanionAds></Creative>"
            + "</Creatives></InLine></Ad></VAST>";

    private static final String HTML_AND_STATIC = ""
            + "<VAST version=\"3.0\"><Ad id=\"1\"><InLine><Creatives>"
            + "<Creative><Linear><MediaFiles>"
            + "<MediaFile type=\"video/mp4\">https://example.com/v.mp4</MediaFile>"
            + "</MediaFiles></Linear></Creative>"
            + "<Creative><CompanionAds>"
            + "<Companion width=\"320\" height=\"480\">"
            + "<StaticResource creativeType=\"image/jpeg\">https://example.com/static.jpg</StaticResource>"
            + "<HTMLResource><![CDATA[<html><body>End</body></html>]]></HTMLResource>"
            + "<IFrameResource><![CDATA[https://example.com/iframe.html]]></IFrameResource>"
            + "</Companion>"
            + "</CompanionAds></Creative>"
            + "</Creatives></InLine></Ad></VAST>";

    private static final String IFRAME_ONLY = ""
            + "<VAST version=\"3.0\"><Ad id=\"1\"><InLine><Creatives>"
            + "<Creative><Linear><MediaFiles>"
            + "<MediaFile type=\"video/mp4\">https://example.com/v.mp4</MediaFile>"
            + "</MediaFiles></Linear></Creative>"
            + "<Creative><CompanionAds>"
            + "<Companion width=\"320\" height=\"480\">"
            + "<IFrameResource>https://example.com/iframe.html</IFrameResource>"
            + "<StaticResource creativeType=\"image/jpeg\">https://example.com/static.jpg</StaticResource>"
            + "</Companion>"
            + "</CompanionAds></Creative>"
            + "</Creatives></InLine></Ad></VAST>";

    private static final String NO_COMPANION = ""
            + "<VAST version=\"3.0\"><Ad id=\"1\"><InLine><Creatives>"
            + "<Creative><Linear><MediaFiles>"
            + "<MediaFile type=\"video/mp4\">https://example.com/v.mp4</MediaFile>"
            + "</MediaFiles></Linear></Creative>"
            + "</Creatives></InLine></Ad></VAST>";

    @Test
    public void parsesStaticCompanion_withClickAndViewTracking() {
        VastCompanion companion = VastParser.getBestCompanion(STATIC_COMPANION);
        assertNotNull(companion);
        assertEquals(VastCompanion.ResourceType.STATIC, companion.getResourceType());
        assertEquals("https://example.com/end-screen.jpg", companion.getStaticImageUrl());
        assertEquals("https://example.com/click", companion.getClickThroughUrl());
        assertEquals(1, companion.getClickTrackingUrls().size());
        assertEquals("https://example.com/click-track", companion.getClickTrackingUrls().get(0));
        assertEquals(1, companion.getViewTrackingUrls().size());
        assertEquals("https://example.com/view-track", companion.getViewTrackingUrls().get(0));
    }

    @Test
    public void prefersHtmlOverIframeAndStatic() {
        VastCompanion companion = VastParser.getBestCompanion(HTML_AND_STATIC);
        assertNotNull(companion);
        assertEquals(VastCompanion.ResourceType.HTML, companion.getResourceType());
        assertTrue(companion.getContent().contains("End"));
    }

    @Test
    public void prefersIframeOverStatic() {
        VastCompanion companion = VastParser.getBestCompanion(IFRAME_ONLY);
        assertNotNull(companion);
        assertEquals(VastCompanion.ResourceType.IFRAME, companion.getResourceType());
        assertEquals("https://example.com/iframe.html", companion.getContent());
    }

    @Test
    public void noCompanion_returnsNullPreview() {
        assertNull(VastParser.getBestCompanion(NO_COMPANION));
        assertNull(VastParser.getCompanionPreview(NO_COMPANION));
        assertNull(VastParser.getCompanionImageUrl(NO_COMPANION));
    }

    @Test
    public void playbackPlan_endCardUsesHtmlCompanion() {
        VideoPlaybackPlan plan = VideoPlaybackPlanBuilder.build(HTML_AND_STATIC);
        VastPreview preview = plan.getEndCardPreview();
        assertNotNull(preview);
        assertTrue(preview.hasRenderableCompanion());
        assertEquals(VastCompanion.ResourceType.HTML, preview.getCompanion().getResourceType());
    }

    @Test
    public void playbackPlan_noCompanion_hasNoEndCard() {
        VideoPlaybackPlan plan = VideoPlaybackPlanBuilder.build(NO_COMPANION);
        assertNull(plan.getEndCardPreview());
        assertFalse(PostVideoPolicyHelper.hasCompanion(plan));
    }

    /** Tiny local helper to avoid importing PostVideoPolicy in every assert. */
    private static final class PostVideoPolicyHelper {
        static boolean hasCompanion(VideoPlaybackPlan plan) {
            return plan.getEndCardPreview() != null && plan.getEndCardPreview().hasRenderableCompanion();
        }
    }
}
