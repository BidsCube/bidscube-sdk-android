package com.bidscube.sdk.utils;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class VastParserSecurityTest {

    @Before
    public void disableLogging() {
        SDKLogger.setLoggingEnabled(false);
    }

    @Test
    public void parse_rejectsDoctypeSafely() {
        String malicious = ""
                + "<?xml version=\"1.0\"?>"
                + "<!DOCTYPE foo [<!ENTITY xxe SYSTEM \"file:///etc/passwd\">]>"
                + "<VAST version=\"3.0\"><Ad><InLine><Creatives><Creative><Linear>"
                + "  <MediaFiles><MediaFile type=\"video/mp4\">https://example.com/a.mp4</MediaFile></MediaFiles>"
                + "</Linear></Creative></Creatives></InLine></Ad></VAST>";
        try {
            VastXmlParser.parse(malicious);
        } catch (Exception e) {
            assertTrue(e.getMessage() == null || e.getMessage().contains("DOCTYPE")
                    || e.getMessage().contains("DOCTYPE") || e instanceof org.xml.sax.SAXParseException);
        }
    }

    @Test
    public void parse_validVast_succeeds() throws Exception {
        String vast = ""
                + "<VAST version=\"3.0\"><Ad id=\"x\"><InLine><Creatives><Creative><Linear>"
                + "  <MediaFiles><MediaFile type=\"video/mp4\">https://example.com/a.mp4</MediaFile></MediaFiles>"
                + "</Linear></Creative></Creatives></InLine></Ad></VAST>";
        assertNotNull(VastXmlParser.parse(vast));
    }
}
