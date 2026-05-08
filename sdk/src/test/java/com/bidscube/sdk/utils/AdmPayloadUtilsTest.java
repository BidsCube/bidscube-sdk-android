package com.bidscube.sdk.utils;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class AdmPayloadUtilsTest {

    @Test
    public void unwrapJsonAdmEnvelope_lenientExtractsHtml() {
        // Intentionally invalid JSON after the adm string (trailing garbage) so JSONObject fails,
        // but lenient extraction still returns the HTML payload.
        String wrapped =
                "{ \"adm\": \"<div><img src=\\\"https://cdn.example.com/x.jpeg\\\"></div>\" , broken";
        String out = AdmPayloadUtils.unwrapJsonAdmEnvelope(wrapped);
        assertEquals("<div><img src=\"https://cdn.example.com/x.jpeg\"></div>", out);
    }

    @Test
    public void unwrapJsonAdmEnvelope_standardJsonStillWorks() {
        String wrapped = "{\"adm\":\"<a href=\\\"https://z\\\"><img src=\\\"https://z/i.png\\\"></a>\"}";
        String out = AdmPayloadUtils.unwrapJsonAdmEnvelope(wrapped);
        assertTrue(out.contains("<a href="));
        assertTrue(out.contains("i.png"));
    }

    @Test
    public void stripTrailingJsonCloseAfterHtml_removesQuoteBrace() {
        String html = "<div>x</div></script>\"}";
        assertEquals("<div>x</div></script>", AdmPayloadUtils.stripTrailingJsonCloseAfterHtml(html));
    }

    @Test
    public void stripTrailingJsonCloseAfterHtml_removesJunkAfterLastTag() {
        String html = "<div>ok</div>\"}\n}";
        assertEquals("<div>ok</div>", AdmPayloadUtils.stripTrailingJsonCloseAfterHtml(html));
    }

    @Test
    public void stripInterTagJsonTextJunk_removesQuoteBraceBetweenTags() {
        String html = "</script>\" }\n<script src=\"x\">";
        assertEquals("</script><script src=\"x\">", AdmPayloadUtils.stripInterTagJsonTextJunk(html));
    }

    @Test
    public void stripInterTagJsonTextJunk_removesOpenAdmJsonBeforeMarkup() {
        String html = "<span id=\"b\">{ \"adm\": \"<div>x</div>";
        assertEquals("<span id=\"b\"><div>x</div>", AdmPayloadUtils.stripInterTagJsonTextJunk(html));
    }

    @Test
    public void stripLooseJsonAdmPrefix_dropsBrokenEnvelopeBeforeDiv() {
        String withHtml =
                "{ \"adm\" : \"oops\" <div id=\"w\"><a href=\"https://x.com\"><img src=\"https://ftp.da-dsp.com/x.jpeg\"></a></div>";
        String out = AdmPayloadUtils.stripLooseJsonAdmPrefixToHtml(withHtml);
        assertTrue(out.startsWith("<div"));
        assertTrue(out.contains("ftp.da-dsp.com"));
    }
}
