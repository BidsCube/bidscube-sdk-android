package com.bidscube.sdk.openrtb;

import com.bidscube.sdk.models.video.VideoPlaybackPlan;
import com.bidscube.sdk.models.video.VideoPlaybackPlanType;
import com.bidscube.sdk.network.BidscubeResponse;
import com.bidscube.sdk.network.BidscubeResponseParser;
import com.bidscube.sdk.utils.SDKLogger;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class VideoPodResponseResolverTest {

    @Before
    public void disableLogging() {
        SDKLogger.setLoggingEnabled(false);
    }

    @Test
    public void resolve_multiBidWithoutRootAdm_buildsPodPlan() throws Exception {
        String json = ""
                + "{"
                + "  \"bids\": ["
                + "    { \"impid\": \"s1\", \"adm\": \"" + escape(OpenRtbTestVast.slotVast("a1", 1, 15)) + "\" },"
                + "    { \"impid\": \"s2\", \"adm\": \"" + escape(OpenRtbTestVast.slotVast("a2", 2, 15)) + "\" }"
                + "  ]"
                + "}";

        BidscubeResponse response = BidscubeResponseParser.parse(json);
        assertNotNull(response);

        VideoPlaybackPlan plan = VideoPodResponseResolver.resolve(response);
        assertFalse(plan.isEmpty());
        assertTrue(plan.isPodPlayback());
        assertEquals(2, plan.getTotalAds());
        assertEquals("a1", plan.getSlots().get(0).getAdId());
        assertEquals("a2", plan.getSlots().get(1).getAdId());
    }

    @Test
    public void resolve_structuredOpenRtbPod_isOpenRtbPodded() throws Exception {
        String json = ""
                + "{"
                + "  \"podid\": \"break-1\","
                + "  \"video\": { \"poddur\": 90, \"maxseq\": 3 },"
                + "  \"bids\": ["
                + "    { \"impid\": \"slot-1\", \"slotinpod\": 1, \"adm\": \"" + escape(OpenRtbTestVast.slotVast("a1", 1, 15)) + "\" },"
                + "    { \"impid\": \"slot-2\", \"slotinpod\": 2, \"adm\": \"" + escape(OpenRtbTestVast.slotVast("a2", 2, 15)) + "\" }"
                + "  ]"
                + "}";

        BidscubeResponse response = BidscubeResponseParser.parse(json);
        VideoPlaybackPlan plan = VideoPodResponseResolver.resolve(response);

        assertTrue(plan.isOpenRtbPodded());
        assertEquals(PodType.STRUCTURED, plan.getOpenRtbPodType());
        assertEquals(2, plan.getTotalAds());
    }

    @Test
    public void resolve_inlineVastPod_fallsBackToVastBuilder() {
        String vast = OpenRtbTestVast.slotVast("solo", 1, 15);
        BidscubeResponse response = new BidscubeResponse(vast, 0);

        VideoPlaybackPlan plan = VideoPodResponseResolver.resolve(response);
        assertFalse(plan.isEmpty());
        assertEquals(VideoPlaybackPlanType.SINGLE, plan.getType());
        assertFalse(plan.isOpenRtbPodded());
    }

    @Test
    public void resolve_disabledConfig_fallsBackToRootAdm() throws Exception {
        String vast = OpenRtbTestVast.slotVast("solo", 1, 15);
        String json = "{ \"adm\": \"" + escape(vast) + "\" }";
        BidscubeResponse response = BidscubeResponseParser.parse(json);

        VideoPodConfig disabled = new VideoPodConfig(
                false, true, PodDurationValidationMode.LENIENT,
                null, true, true);
        VideoPlaybackPlan plan = VideoPodResponseResolver.resolve(response, disabled);

        assertFalse(plan.isEmpty());
        assertEquals("solo", plan.getFirstSlot().getAdId());
    }

    @Test
    public void shouldUsePoddedBuilder_singleMarkupWithMetadata_true() throws Exception {
        String json = ""
                + "{"
                + "  \"adm\": \"" + escape(OpenRtbTestVast.slotVast("solo", 1, 15)) + "\","
                + "  \"openrtb\": { \"video\": { \"podid\": \"break-1\", \"poddur\": 30 } }"
                + "}";
        PoddedAdResponse podded = OpenRtbPoddedResponseNormalizer.normalize(json, null);
        assertTrue(VideoPodResponseResolver.shouldUsePoddedBuilder(podded));
    }

    @Test
    public void shouldUsePoddedBuilder_singleBidNoMetadata_false() throws Exception {
        String json = "{ \"bids\": [ { \"adm\": \"" + escape(OpenRtbTestVast.slotVast("solo", 1, 15)) + "\" } ] }";
        PoddedAdResponse podded = OpenRtbPoddedResponseNormalizer.normalize(json, null);
        assertFalse(VideoPodResponseResolver.shouldUsePoddedBuilder(podded));
    }

    @Test
    public void resolve_podBuilderEmpty_fallsBackToRootAdm() throws Exception {
        String vast = OpenRtbTestVast.slotVast("solo", 1, 15);
        String json = "{ \"adm\": \"" + escape(vast) + "\" }";
        BidscubeResponse response = BidscubeResponseParser.parse(json);

        VideoPodConfig strict = new VideoPodConfig(
                true, true, PodDurationValidationMode.STRICT,
                null, true, true);
        // Single adm without pod metadata — pod builder not used; root adm plays.
        VideoPlaybackPlan plan = VideoPodResponseResolver.resolve(response, strict);
        assertFalse(plan.isEmpty());
        assertEquals("solo", plan.getFirstSlot().getAdId());
    }

    private static String escape(String value) {
        return value.replace("\"", "\\\"");
    }
}
