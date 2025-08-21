package com.bidscube.sdk;

import android.content.Context;
import android.util.Log;

public class BidscubeSDK {
    // TODO : Implement the SDK initialization and ad loading logic.


    private static final String TAG = "BidscubeSDK";

    /**
     * Initializes the SDK if needed.
     * You can call this in Application class or before showing ads.
     */
    public static void initialize(Context context) {
        Log.d(TAG, "SDK Initialized with context: " + context.getPackageName());
        // Load configurations or initialize components if required
    }

    /**
     * Show Banner Ad
     */
    public static void showBannerAd(Context context) {
        Log.d(TAG, "Showing Banner Ad");
        // Your implementation to load and display banner ad
        // Example: open a WebView or use existing Ad Loader class
    }

    /**
     * Show Video Ad
     */
    public static void showVideoAd(Context context) {
        Log.d(TAG, "Showing Video Ad");
        // Your implementation to load and display video ad
    }

    /**
     * Show Native Ad
     */
    public static void showNativeAd(Context context) {
        Log.d(TAG, "Showing Native Ad");
        // Your implementation to load and display native ad
    }
}
