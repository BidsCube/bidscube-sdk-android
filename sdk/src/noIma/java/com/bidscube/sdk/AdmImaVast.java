package com.bidscube.sdk;

import android.graphics.Color;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.bidscube.sdk.interfaces.AdCallback;
import com.bidscube.sdk.network.BidscubeResponse;
import com.bidscube.sdk.utils.SDKLogger;

/**
 * VAST / Google IMA is not on the classpath in the {@code noIma} build variant. Use
 * {@code com.bidscube:bidscube-sdk} (full) with IMA, or a {@code withIma} build of this module, and
 * {@code SDKConfig.Builder#videoAdsEnabled(true)} to enable video.
 */
final class AdmImaVast {

    private static final String T = "AdmImaVast";
    private static final int ERR = -3;
    private static final String NO_IMA =
            "VAST/IMA (Google IMA) is not included in this AAR. Add dependency "
                    + "com.bidscube:bidscube-sdk (full) or a withIma build, then videoAdsEnabled(true).";

    private AdmImaVast() {}

    static void onVideoAdResponse(AdDisplayManager m, String placementId, BidscubeResponse responseBody, AdCallback callback) {
        SDKLogger.w(T, NO_IMA);
        if (callback != null) {
            callback.onAdFailed(placementId, ERR, NO_IMA);
        }
    }

    static void showSkippableFromFile(AdDisplayManager m, boolean isFullScreen) {
        SDKLogger.w(T, NO_IMA);
    }

    static void appendGetVideoAdView(AdDisplayManager m, String placementId, String adm, LinearLayout adContainer, AdCallback callback) {
        SDKLogger.w(T, NO_IMA);
        TextView err = new TextView(m.context);
        err.setText(NO_IMA);
        err.setTextColor(Color.WHITE);
        err.setPadding(16, 16, 16, 16);
        err.setBackgroundColor(0xFF455A64);
        adContainer.addView(err);
        if (callback != null) {
            callback.onAdFailed(placementId, ERR, NO_IMA);
        }
    }
}
