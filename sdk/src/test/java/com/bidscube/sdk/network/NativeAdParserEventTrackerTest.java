package com.bidscube.sdk.network;

import com.bidscube.sdk.models.natives.EventTracker;
import com.bidscube.sdk.models.natives.NativeAd;
import com.bidscube.sdk.utils.SDKLogger;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class NativeAdParserEventTrackerTest {

    @Before
    public void setUp() {
        SDKLogger.setLoggingEnabled(false);
    }

    @Test
    public void parseFromAdm_acceptsEventTrackerUrlAsString() {
        String adm = "{"
                + "\"native\":{"
                + "\"ver\":\"1.1\","
                + "\"assets\":[{\"id\":2,\"title\":{\"text\":\"Hello\"}}],"
                + "\"link\":{\"url\":\"https://example.com/click\"},"
                + "\"imptrackers\":[\"https://example.com/imp\"],"
                + "\"eventtrackers\":[{"
                + "\"event\":2,"
                + "\"method\":1,"
                + "\"url\":\"https://example.com/viewability\""
                + "}]"
                + "}}";

        NativeAd ad = NativeAdParser.parseFromAdm(adm);
        Assert.assertNotNull(ad);
        Assert.assertEquals(1, ad.assets.size());
        Assert.assertEquals("Hello", ad.assets.get(0).title.text);
        Assert.assertNotNull(ad.eventtrackers);
        Assert.assertEquals(1, ad.eventtrackers.size());
        EventTracker tracker = ad.eventtrackers.get(0);
        Assert.assertEquals(2, tracker.getEvent());
        Assert.assertEquals(1, tracker.getMethod());
        Assert.assertEquals(
                java.util.Collections.singletonList("https://example.com/viewability"),
                tracker.getUrl());
    }

    @Test
    public void parseFromAdm_acceptsEventTrackerUrlAsArray() {
        String adm = "{"
                + "\"native\":{"
                + "\"ver\":\"1.1\","
                + "\"assets\":[{\"id\":2,\"title\":{\"text\":\"Hello\"}}],"
                + "\"link\":{\"url\":\"https://example.com/click\"},"
                + "\"eventtrackers\":[{"
                + "\"event\":1,"
                + "\"method\":1,"
                + "\"url\":[\"https://example.com/a\",\"https://example.com/b\"]"
                + "}]"
                + "}}";

        NativeAd ad = NativeAdParser.parseFromAdm(adm);
        Assert.assertNotNull(ad);
        Assert.assertEquals(2, ad.eventtrackers.get(0).getUrl().size());
    }
}
