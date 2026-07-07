package com.bidscube.sdk.utils;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class VastVideoTrackerTest {

    @Test
    public void replaceErrorCodeMacro_substitutesPlaceholder() {
        String url = "https://example.com/track?code=[ERRORCODE]";
        assertEquals("https://example.com/track?code=405", VastVideoTracker.replaceErrorCodeMacro(url, "405"));
    }

    @Test
    public void replaceErrorCodeMacro_lowercasePlaceholder() {
        String url = "https://example.com/track?code=[errorcode]";
        assertEquals("https://example.com/track?code=900", VastVideoTracker.replaceErrorCodeMacro(url, "900"));
    }

    @Test
    public void replaceErrorCodeMacro_noPlaceholder_unchanged() {
        String url = "https://example.com/track?code=400";
        assertEquals(url, VastVideoTracker.replaceErrorCodeMacro(url, "405"));
    }
}
