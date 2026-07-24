package com.bidscube.sdk.network;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.bidscube.sdk.models.DeviceInfo;

import org.junit.Test;

public class AdUrlUserIdTest {

    private static DeviceInfo deviceInfo(String userId) {
        return new DeviceInfo(
                "com.example.app",
                "Example",
                "https://play.google.com/store/apps/details?id=com.example.app",
                "en",
                1080,
                1920,
                "TestUA",
                "ifa-test",
                0,
                "1.0.0",
                userId,
                0,
                "",
                "",
                false);
    }

    @Test
    public void resolveUserId_returnsConfiguredValue() {
        assertEquals("pub-user-42", AdUrlParams.resolveUserId(deviceInfo("pub-user-42")));
    }

    @Test
    public void resolveUserId_returnsNull_whenMissing() {
        assertNull(AdUrlParams.resolveUserId(deviceInfo(null)));
        assertNull(AdUrlParams.resolveUserId(deviceInfo("")));
        assertNull(AdUrlParams.resolveUserId(null));
    }

    @Test
    public void deviceInfo_preservesUserId() {
        assertEquals("abc", deviceInfo("abc").getUserId());
        assertNull(deviceInfo(null).getUserId());
    }
}
