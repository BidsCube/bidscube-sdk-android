package com.bidscube.sdk.network;

import com.bidscube.sdk.openrtb.OpenRtbTestVast;
import com.bidscube.sdk.openrtb.PodType;
import com.bidscube.sdk.utils.SDKLogger;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class BidscubeResponseParserTest {

    @Before
    public void disableLogging() {
        SDKLogger.setLoggingEnabled(false);
    }

    @Test
    public void parse_rawVast_returnsResponseWithoutPoddedMetadata() {
        String vast = OpenRtbTestVast.slotVast("solo", 1, 15);
        BidscubeResponse response = BidscubeResponseParser.parse(vast);

        assertNotNull(response);
        assertEquals(vast, response.getAdm());
        assertNull(response.getPoddedAdResponse());
        assertFalse(response.hasOpenRtbPodMetadata());
    }

    @Test
    public void parse_openRtbPodJson_attachesPoddedResponse() throws Exception {
        String json = ""
                + "{"
                + "  \"position\": 1,"
                + "  \"podid\": \"break-1\","
                + "  \"video\": { \"poddur\": 45, \"maxseq\": 2 },"
                + "  \"bids\": ["
                + "    { \"impid\": \"slot-1\", \"slotinpod\": 1, \"adm\": \"" + escape(OpenRtbTestVast.slotVast("a1", 1, 15)) + "\" },"
                + "    { \"impid\": \"slot-2\", \"slotinpod\": 2, \"adm\": \"" + escape(OpenRtbTestVast.slotVast("a2", 2, 15)) + "\" }"
                + "  ]"
                + "}";

        BidscubeResponse response = BidscubeResponseParser.parse(json);
        assertNotNull(response);
        assertTrue(response.hasOpenRtbPodMetadata());
        assertNotNull(response.getPoddedAdResponse());
        assertEquals("break-1", response.getPoddedAdResponse().getPodContext().getPodId());
        assertEquals(PodType.STRUCTURED, response.getPoddedAdResponse().getPodContext().getPodType());
        assertEquals(2, response.getPoddedAdResponse().getMarkups().size());
        assertEquals(1, response.getPosition());
    }

    private static String escape(String value) {
        return value.replace("\"", "\\\"");
    }
}
