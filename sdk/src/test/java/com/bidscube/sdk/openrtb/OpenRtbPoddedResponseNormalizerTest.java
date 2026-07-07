package com.bidscube.sdk.openrtb;

import com.bidscube.sdk.models.video.VideoAdSlot;
import com.bidscube.sdk.models.video.VideoPlaybackPlan;
import com.bidscube.sdk.models.video.VideoPlaybackPlanType;
import com.bidscube.sdk.utils.SDKLogger;

import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class OpenRtbPoddedResponseNormalizerTest {

    @Before
    public void disableLogging() {
        SDKLogger.setLoggingEnabled(false);
    }

    @Test
    public void normalize_multiBid_structuredPod() throws Exception {
        String json = ""
                + "{"
                + "  \"podid\": \"break-1\","
                + "  \"video\": { \"poddur\": 90, \"maxseq\": 3, \"rqddurs\": [15, 30, 15] },"
                + "  \"bids\": ["
                + "    { \"impid\": \"slot-3\", \"slotinpod\": 3, \"adm\": \"" + escape(OpenRtbTestVast.slotVast("a3", 3, 15)) + "\" },"
                + "    { \"impid\": \"slot-1\", \"slotinpod\": 1, \"adm\": \"" + escape(OpenRtbTestVast.slotVast("a1", 1, 15)) + "\" },"
                + "    { \"impid\": \"slot-2\", \"slotinpod\": 2, \"adm\": \"" + escape(OpenRtbTestVast.slotVast("a2", 2, 30)) + "\" }"
                + "  ]"
                + "}";

        PoddedAdResponse response = OpenRtbPoddedResponseNormalizer.normalize(json, null);
        assertNotNull(response);
        assertTrue(response.isPodded());
        assertEquals("break-1", response.getPodContext().getPodId());
        assertEquals(PodType.STRUCTURED, response.getPodContext().getPodType());
        assertEquals(3, response.getMarkups().size());
        assertEquals("slot-3", response.getMarkups().get(0).getImpId());
    }

    @Test
    public void normalize_singleAdm_openRtbVideo() throws Exception {
        String vast = OpenRtbTestVast.slotVast("solo", 1, 15);
        JSONObject root = new JSONObject();
        root.put("type", "video");
        root.put("adm", vast);
        JSONObject openRtb = new JSONObject();
        JSONObject video = new JSONObject();
        video.put("podid", "break-solo");
        video.put("poddur", 15);
        video.put("podseq", 1);
        openRtb.put("video", video);
        root.put("openrtb", openRtb);

        PoddedAdResponse response = OpenRtbPoddedResponseNormalizer.normalize(root, vast);
        assertNotNull(response);
        assertEquals("break-solo", response.getPodContext().getPodId());
        assertEquals(1, response.getMarkups().size());
    }

    @Test
    public void normalize_nonJson_returnsNull() {
        assertEquals(null, OpenRtbPoddedResponseNormalizer.normalize("<VAST></VAST>", null));
    }

    @Test
    public void normalize_multiBid_openRtbVideoObject() throws Exception {
        String json = ""
                + "{"
                + "  \"openrtb\": {"
                + "    \"video\": { \"podid\": \"break-1\", \"poddur\": 60, \"maxseq\": 2 }"
                + "  },"
                + "  \"bids\": ["
                + "    { \"impid\": \"slot-1\", \"slotinpod\": 1, \"adm\": \"" + escape(OpenRtbTestVast.slotVast("a1", 1, 15)) + "\" },"
                + "    { \"impid\": \"slot-2\", \"slotinpod\": 2, \"adm\": \"" + escape(OpenRtbTestVast.slotVast("a2", 2, 15)) + "\" }"
                + "  ]"
                + "}";

        PoddedAdResponse response = OpenRtbPoddedResponseNormalizer.normalize(json, null);
        assertNotNull(response);
        assertEquals("break-1", response.getPodContext().getPodId());
        assertEquals(Integer.valueOf(60), response.getPodContext().getPodDurationSec());
        assertEquals(2, response.getMarkups().size());
    }

    @Test
    public void normalize_duplicateSlotInPod_doesNotCrash() throws Exception {
        String json = ""
                + "{"
                + "  \"podid\": \"dup\","
                + "  \"bids\": ["
                + "    { \"impid\": \"s1\", \"slotinpod\": 1, \"adm\": \"" + escape(OpenRtbTestVast.slotVast("a1", 1, 15)) + "\" },"
                + "    { \"impid\": \"s2\", \"slotinpod\": 1, \"adm\": \"" + escape(OpenRtbTestVast.slotVast("a2", 2, 15)) + "\" }"
                + "  ]"
                + "}";

        PoddedAdResponse response = OpenRtbPoddedResponseNormalizer.normalize(json, null);
        assertNotNull(response);
        assertEquals(2, response.getMarkups().size());
    }

    private static String escape(String vast) {
        return vast.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
