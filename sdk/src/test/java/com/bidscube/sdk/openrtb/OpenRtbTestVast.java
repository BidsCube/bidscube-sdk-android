package com.bidscube.sdk.openrtb;

/**
 * Inline VAST snippets for OpenRTB pod unit tests.
 */
public final class OpenRtbTestVast {

    private OpenRtbTestVast() {
    }

    public static String slotVast(String adId, Integer sequence, int durationSec) {
        String seqAttr = sequence != null ? " sequence=\"" + sequence + "\"" : "";
        String duration = String.format("00:00:%02d", durationSec);
        return ""
                + "<VAST version=\"3.0\">"
                + "<Ad id=\"" + adId + "\"" + seqAttr + "><InLine>"
                + "<Creatives><Creative><Linear>"
                + "<Duration>" + duration + "</Duration>"
                + "<MediaFiles>"
                + "<MediaFile type=\"video/mp4\">https://example.com/" + adId + ".mp4</MediaFile>"
                + "</MediaFiles>"
                + "</Linear></Creative></Creatives>"
                + "</InLine></Ad></VAST>";
    }

    static String brokenSlotVast(String adId) {
        return ""
                + "<VAST version=\"3.0\">"
                + "<Ad id=\"" + adId + "\"><InLine>"
                + "<Creatives><Creative><Linear>"
                + "<MediaFiles></MediaFiles>"
                + "</Linear></Creative></Creatives>"
                + "</InLine></Ad></VAST>";
    }
}
