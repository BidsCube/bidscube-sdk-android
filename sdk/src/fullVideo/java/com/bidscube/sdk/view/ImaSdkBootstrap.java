package com.bidscube.sdk.view;

import android.content.Context;

import com.bidscube.sdk.utils.SDKLogger;
import com.google.ads.interactivemedia.v3.api.ImaSdkFactory;
import com.google.ads.interactivemedia.v3.api.ImaSdkSettings;

/**
 * Initializes Google IMA as early as possible (required for reliable ad playback).
 */
public final class ImaSdkBootstrap {

    private static volatile boolean initialized;

    private ImaSdkBootstrap() {
    }

    public static synchronized void initialize(Context context) {
        if (initialized || context == null) {
            return;
        }
        try {
            Context appContext = context.getApplicationContext();
            ImaSdkFactory factory = ImaSdkFactory.getInstance();
            ImaSdkSettings settings = factory.createImaSdkSettings();
            settings.setDebugMode(false);
            factory.initialize(appContext, settings);
            initialized = true;
            SDKLogger.d("ImaSdkBootstrap", "Google IMA SDK initialized");
        } catch (Throwable t) {
            SDKLogger.e("ImaSdkBootstrap", "Google IMA SDK init failed: " + t.getMessage());
        }
    }
}
