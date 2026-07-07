package com.bidscube.sdk.openrtb;

import com.bidscube.sdk.models.video.VideoAdSlot;
import com.bidscube.sdk.models.video.VideoPlaybackPlan;
import com.bidscube.sdk.models.video.VideoPlaybackPlanType;
import com.bidscube.sdk.utils.SDKLogger;

import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class PoddedPlaybackPlanBuilderTest {

    @Before
    public void disableLogging() {
        SDKLogger.setLoggingEnabled(false);
    }

    @Test
    public void structuredPod_sortsBySlotInPod() throws Exception {
        PoddedAdResponse response = buildMultiBidResponse(
                "break-1",
                new int[] {3, 1, 2},
                new String[] {"a3", "a1", "a2"},
                new int[] {15, 15, 15},
                PodType.STRUCTURED,
                90,
                3,
                Arrays.asList(15, 15, 15));

        VideoPlaybackPlan plan = PoddedPlaybackPlanBuilder.build(response, VideoPodConfig.defaults());
        assertEquals(VideoPlaybackPlanType.POD, plan.getType());
        assertEquals(3, plan.getTotalAds());
        assertEquals("a1", plan.getSlots().get(0).getAdId());
        assertEquals("a2", plan.getSlots().get(1).getAdId());
        assertEquals("a3", plan.getSlots().get(2).getAdId());
        assertEquals(PodOrderingSource.OPENRTB_SLOT_IN_POD, plan.getSlots().get(0).getOrderingSource());
    }

    @Test
    public void openRtbSlotInPod_priorityOverVastSequence() throws Exception {
        PoddedAdResponse response = buildMultiBidResponse(
                "mismatch",
                new int[] {1},
                new String[] {"ad-m"},
                new int[] {15},
                PodType.STRUCTURED,
                null,
                null,
                null);
        // VAST sequence=3 but OpenRTB slotinpod=1
        PoddedAdMarkup markup = new PoddedAdMarkup(
                "slot-1",
                1,
                OpenRtbTestVast.slotVast("ad-m", 3, 15),
                new OpenRtbVideoSlotContext("slot-1", "mismatch", 1, null, null, null, null, null),
                0);
        OpenRtbVideoPodContext ctx = new OpenRtbVideoPodContext(
                "mismatch", null, null, null, null,
                List.of(), null, PodType.STRUCTURED, PodOrderingSource.OPENRTB_SLOT_IN_POD);
        response = new PoddedAdResponse(ctx, List.of(markup));

        VideoPlaybackPlan plan = PoddedPlaybackPlanBuilder.build(response, VideoPodConfig.defaults());
        assertEquals(1, plan.getTotalAds());
        assertEquals(Integer.valueOf(1), plan.getSlots().get(0).getOpenRtbSlotInPod());
        assertEquals(Integer.valueOf(3), plan.getSlots().get(0).getVastSequence());
    }

    @Test
    public void vastSequence_usedWhenOpenRtbMissing() throws Exception {
        String vast1 = OpenRtbTestVast.slotVast("v2", 2, 15);
        String vast2 = OpenRtbTestVast.slotVast("v1", 1, 15);
        OpenRtbVideoPodContext ctx = new OpenRtbVideoPodContext(
                "vast-only", null, null, null, null,
                List.of(), null, PodType.UNKNOWN, PodOrderingSource.VAST_SEQUENCE);
        PoddedAdResponse response = new PoddedAdResponse(ctx, Arrays.asList(
                new PoddedAdMarkup("i2", null, vast1, null, 0),
                new PoddedAdMarkup("i1", null, vast2, null, 1)));

        VideoPlaybackPlan plan = PoddedPlaybackPlanBuilder.build(response, VideoPodConfig.defaults());
        assertEquals("v1", plan.getSlots().get(0).getAdId());
        assertEquals("v2", plan.getSlots().get(1).getAdId());
    }

    @Test
    public void responseOrder_finalFallback() throws Exception {
        OpenRtbVideoPodContext ctx = new OpenRtbVideoPodContext(
                "order", null, null, null, null,
                List.of(), null, PodType.UNKNOWN, PodOrderingSource.RESPONSE_ORDER);
        PoddedAdResponse response = new PoddedAdResponse(ctx, Arrays.asList(
                new PoddedAdMarkup("b", null, OpenRtbTestVast.slotVast("b", null, 15), null, 1),
                new PoddedAdMarkup("a", null, OpenRtbTestVast.slotVast("a", null, 15), null, 0)));

        VideoPlaybackPlan plan = PoddedPlaybackPlanBuilder.build(response, VideoPodConfig.defaults());
        assertEquals("a", plan.getSlots().get(0).getAdId());
        assertEquals("b", plan.getSlots().get(1).getAdId());
        assertEquals(PodOrderingSource.RESPONSE_ORDER, plan.getSlots().get(0).getOrderingSource());
    }

    @Test
    public void dynamicPod_respectsPodDur() throws Exception {
        OpenRtbVideoPodContext ctx = new OpenRtbVideoPodContext(
                "dynamic", null, 30, 5, null,
                List.of(), null, PodType.DYNAMIC, PodOrderingSource.RESPONSE_ORDER);
        PoddedAdResponse response = new PoddedAdResponse(ctx, Arrays.asList(
                new PoddedAdMarkup("1", null, OpenRtbTestVast.slotVast("d1", null, 15), null, 0),
                new PoddedAdMarkup("2", null, OpenRtbTestVast.slotVast("d2", null, 15), null, 1),
                new PoddedAdMarkup("3", null, OpenRtbTestVast.slotVast("d3", null, 15), null, 2)));

        VideoPlaybackPlan plan = PoddedPlaybackPlanBuilder.build(response, VideoPodConfig.defaults());
        assertEquals(2, plan.getTotalAds());
    }

    @Test
    public void dynamicPod_firstSlotOverBudget_excluded() throws Exception {
        OpenRtbVideoPodContext ctx = new OpenRtbVideoPodContext(
                "dynamic", null, 10, 5, null,
                List.of(), null, PodType.DYNAMIC, PodOrderingSource.RESPONSE_ORDER);
        PoddedAdResponse response = new PoddedAdResponse(ctx, Arrays.asList(
                new PoddedAdMarkup("1", null, OpenRtbTestVast.slotVast("d1", null, 15), null, 0),
                new PoddedAdMarkup("2", null, OpenRtbTestVast.slotVast("d2", null, 5), null, 1)));

        VideoPlaybackPlan plan = PoddedPlaybackPlanBuilder.build(response, VideoPodConfig.defaults());
        assertEquals(1, plan.getTotalAds());
        assertEquals("d2", plan.getSlots().get(0).getAdId());
    }

    @Test
    public void hybridPod_fixedSlotsFirst() throws Exception {
        OpenRtbVideoPodContext ctx = new OpenRtbVideoPodContext(
                "hybrid", null, 60, 4, null,
                List.of(), null, PodType.HYBRID, PodOrderingSource.OPENRTB_SLOT_IN_POD);
        PoddedAdResponse response = new PoddedAdResponse(ctx, Arrays.asList(
                new PoddedAdMarkup("dyn", null, OpenRtbTestVast.slotVast("dyn", null, 15), null, 2),
                new PoddedAdMarkup("fix", 1, OpenRtbTestVast.slotVast("fix", null, 15), null, 0),
                new PoddedAdMarkup("dyn2", null, OpenRtbTestVast.slotVast("dyn2", null, 15), null, 3)));

        VideoPlaybackPlan plan = PoddedPlaybackPlanBuilder.build(response, VideoPodConfig.defaults());
        assertEquals("fix", plan.getSlots().get(0).getAdId());
    }

    @Test
    public void partialPod_brokenSlotSkipped() throws Exception {
        OpenRtbVideoPodContext ctx = new OpenRtbVideoPodContext(
                "partial", null, null, 3, null,
                List.of(), null, PodType.STRUCTURED, PodOrderingSource.OPENRTB_SLOT_IN_POD);
        PoddedAdResponse response = new PoddedAdResponse(ctx, Arrays.asList(
                new PoddedAdMarkup("s1", 1, OpenRtbTestVast.slotVast("ok1", 1, 15), null, 0),
                new PoddedAdMarkup("s2", 2, OpenRtbTestVast.brokenSlotVast("broken"), null, 1),
                new PoddedAdMarkup("s3", 3, OpenRtbTestVast.slotVast("ok3", 3, 15), null, 2)));

        VideoPlaybackPlan plan = PoddedPlaybackPlanBuilder.build(response, VideoPodConfig.defaults());
        assertEquals(2, plan.getTotalAds());
        assertEquals("ok1", plan.getSlots().get(0).getAdId());
        assertEquals("ok3", plan.getSlots().get(1).getAdId());
    }

    @Test
    public void missingPodId_doesNotCrash() {
        OpenRtbVideoPodContext ctx = new OpenRtbVideoPodContext(
                null, null, null, null, null,
                List.of(), null, PodType.NONE, PodOrderingSource.RESPONSE_ORDER);
        PoddedAdResponse response = new PoddedAdResponse(ctx, List.of(
                new PoddedAdMarkup("i1", 1, OpenRtbTestVast.slotVast("a1", 1, 15), null, 0)));

        VideoPlaybackPlan plan = PoddedPlaybackPlanBuilder.build(response, VideoPodConfig.defaults());
        assertFalse(plan.isEmpty());
    }

    @Test
    public void singleVastPod_withOpenRtbMetadata_enrichesAllSlots() throws Exception {
        String vast = ""
                + "<VAST version=\"3.0\">"
                + "  <Ad sequence=\"1\" id=\"p1\"><InLine><Creatives><Creative><Linear>"
                + "    <Duration>00:00:15</Duration>"
                + "    <MediaFiles><MediaFile type=\"video/mp4\">https://example.com/p1.mp4</MediaFile></MediaFiles>"
                + "  </Linear></Creative></Creatives></InLine></Ad>"
                + "  <Ad sequence=\"2\" id=\"p2\"><InLine><Creatives><Creative><Linear>"
                + "    <Duration>00:00:15</Duration>"
                + "    <MediaFiles><MediaFile type=\"video/mp4\">https://example.com/p2.mp4</MediaFile></MediaFiles>"
                + "  </Linear></Creative></Creatives></InLine></Ad>"
                + "</VAST>";
        JSONObject root = new JSONObject();
        root.put("adm", vast);
        JSONObject video = new JSONObject();
        video.put("podid", "inline-pod");
        video.put("poddur", 30);
        video.put("maxseq", 2);
        JSONObject openRtb = new JSONObject();
        openRtb.put("video", video);
        root.put("openrtb", openRtb);

        PoddedAdResponse response = OpenRtbPoddedResponseNormalizer.normalize(root, vast);
        assertNotNull(response);
        VideoPlaybackPlan plan = PoddedPlaybackPlanBuilder.build(response, VideoPodConfig.defaults());
        assertEquals(2, plan.getTotalAds());
        assertTrue(plan.isOpenRtbPodded());
    }

    @Test
    public void vastSequence_orderWhenNoSlotInPod() {
        OpenRtbVideoPodContext ctx = new OpenRtbVideoPodContext(
                null, null, null, null, null,
                java.util.Collections.<Integer>emptyList(), null,
                PodType.UNKNOWN, PodOrderingSource.VAST_SEQUENCE);
        PoddedAdResponse response = new PoddedAdResponse(ctx, java.util.Arrays.asList(
                new PoddedAdMarkup("v2", null, OpenRtbTestVast.slotVast("vs2", 2, 15), null, 0),
                new PoddedAdMarkup("v1", null, OpenRtbTestVast.slotVast("vs1", 1, 15), null, 1)));

        VideoPlaybackPlan plan = PoddedPlaybackPlanBuilder.build(response, VideoPodConfig.defaults());
        assertEquals(2, plan.getTotalAds());
        assertEquals("vs1", plan.getSlots().get(0).getAdId());
        assertEquals("vs2", plan.getSlots().get(1).getAdId());
        assertEquals(PodOrderingSource.VAST_SEQUENCE, plan.getSlots().get(0).getOrderingSource());
    }

    @Test
    public void strictDurationValidation_overfillReturnsEmpty() {
        OpenRtbVideoPodContext ctx = new OpenRtbVideoPodContext(
                "strict", null, 10, 2, null,
                List.of(), null, PodType.STRUCTURED, PodOrderingSource.OPENRTB_SLOT_IN_POD);
        PoddedAdResponse response = new PoddedAdResponse(ctx, Arrays.asList(
                new PoddedAdMarkup("s1", 1, OpenRtbTestVast.slotVast("a1", 1, 15), null, 0),
                new PoddedAdMarkup("s2", 2, OpenRtbTestVast.slotVast("a2", 2, 15), null, 1)));

        VideoPodConfig strict = new VideoPodConfig(
                true, true, PodDurationValidationMode.STRICT, null, true, true);
        VideoPlaybackPlan plan = PoddedPlaybackPlanBuilder.build(response, strict);
        assertTrue(plan.isEmpty());
    }

    @Test
    public void inlineMultiAdVast_doesNotApplySingleSlotInPodToAllSlots() throws Exception {
        String vast = ""
                + "<VAST version=\"3.0\">"
                + "  <Ad sequence=\"1\" id=\"p1\"><InLine><Creatives><Creative><Linear>"
                + "    <Duration>00:00:15</Duration>"
                + "    <MediaFiles><MediaFile type=\"video/mp4\">https://example.com/p1.mp4</MediaFile></MediaFiles>"
                + "  </Linear></Creative></Creatives></InLine></Ad>"
                + "  <Ad sequence=\"2\" id=\"p2\"><InLine><Creatives><Creative><Linear>"
                + "    <Duration>00:00:15</Duration>"
                + "    <MediaFiles><MediaFile type=\"video/mp4\">https://example.com/p2.mp4</MediaFile></MediaFiles>"
                + "  </Linear></Creative></Creatives></InLine></Ad>"
                + "</VAST>";
        JSONObject root = new JSONObject();
        root.put("adm", vast);
        JSONObject video = new JSONObject();
        video.put("podid", "inline-pod");
        video.put("slotinpod", 1);
        video.put("rqddurs", new org.json.JSONArray(new int[] {15, 15}));
        JSONObject openRtb = new JSONObject();
        openRtb.put("video", video);
        root.put("openrtb", openRtb);

        PoddedAdResponse response = OpenRtbPoddedResponseNormalizer.normalize(root, vast);
        VideoPlaybackPlan plan = PoddedPlaybackPlanBuilder.build(response, VideoPodConfig.defaults());

        assertEquals(2, plan.getTotalAds());
        assertEquals(Integer.valueOf(1), plan.getSlots().get(0).getOpenRtbSlotInPod());
        assertEquals(Integer.valueOf(2), plan.getSlots().get(1).getOpenRtbSlotInPod());
        assertEquals(PodOrderingSource.VAST_SEQUENCE, plan.getSlots().get(1).getOrderingSource());
    }

    @Test
    public void normalize_missingSlotInPod_staysNull() throws Exception {
        String json = ""
                + "{"
                + "  \"podid\": \"no-slot\","
                + "  \"bids\": ["
                + "    { \"impid\": \"b1\", \"adm\": \"" + escape(OpenRtbTestVast.slotVast("a1", 1, 15)) + "\" }"
                + "  ]"
                + "}";
        PoddedAdResponse response = OpenRtbPoddedResponseNormalizer.normalize(json, null);
        assertNotNull(response);
        assertNull(response.getMarkups().get(0).getSlotInPod());
    }

    private static String escape(String vast) {
        return vast.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static PoddedAdResponse buildMultiBidResponse(
            String podId,
            int[] slots,
            String[] adIds,
            int[] durations,
            PodType podType,
            Integer podDur,
            Integer maxSeq,
            List<Integer> rqddurs) throws Exception {
        JSONObject root = new JSONObject();
        root.put("podid", podId);
        JSONObject video = new JSONObject();
        if (podDur != null) {
            video.put("poddur", podDur);
        }
        if (maxSeq != null) {
            video.put("maxseq", maxSeq);
        }
        if (rqddurs != null) {
            video.put("rqddurs", new org.json.JSONArray(rqddurs));
        }
        root.put("video", video);

        org.json.JSONArray bids = new org.json.JSONArray();
        for (int i = 0; i < slots.length; i++) {
            JSONObject bid = new JSONObject();
            bid.put("impid", "slot-" + slots[i]);
            bid.put("slotinpod", slots[i]);
            bid.put("adm", OpenRtbTestVast.slotVast(adIds[i], slots[i], durations[i]));
            bids.put(bid);
        }
        root.put("bids", bids);

        PoddedAdResponse response = OpenRtbPoddedResponseNormalizer.normalize(root, null);
        assertNotNull(response);
        return response;
    }
}
