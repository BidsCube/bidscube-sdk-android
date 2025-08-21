package com.bidscube.sdk.network;

import android.net.Uri;
import com.bidscube.sdk.device.models.DeviceInfo;

public class NativeAdUrlBuilder {
    private final String placementId;
    private final DeviceInfo deviceInfo;

    private double adWidth = 320.0;
    private double adHeight = 50.0;

    public NativeAdUrlBuilder(String placementId, DeviceInfo deviceInfo, double adWidth, double adHeight) {
        this.placementId = placementId;
        this.deviceInfo = deviceInfo;
        this.adWidth = adWidth;
        this.adHeight = adHeight;
    }

    public Uri build() {
        return new Uri.Builder()
                .scheme("https")
                .authority("ssp-bcc-ads.com")
                .appendQueryParameter("c", "n")
                .appendQueryParameter("m", "s")
                .appendQueryParameter("placement", placementId)
                .appendQueryParameter("app", "1")
                .appendQueryParameter("bundle", deviceInfo.getBundle())
                .appendQueryParameter("name", deviceInfo.getAppName())
                .appendQueryParameter("app_version", deviceInfo.getAppVersion())
                .appendQueryParameter("ifa", deviceInfo.getIfa())
                .appendQueryParameter("dnt", String.valueOf(deviceInfo.getDnt()))
                .appendQueryParameter("app_store_url", deviceInfo.getAppStoreUrl())
                .appendQueryParameter("ua", deviceInfo.getUserAgent())
                .appendQueryParameter("gdpr", deviceInfo.getGdpr())
                .appendQueryParameter("gdpr_consent", deviceInfo.getGdprConsent())
                .appendQueryParameter("us_privacy", deviceInfo.getUsPrivacy())
                .appendQueryParameter("ccpa", deviceInfo.getUsPrivacy())
                .appendQueryParameter("coppa", deviceInfo.isCoppa())
                .appendQueryParameter("language", deviceInfo.getLanguage())
                .appendQueryParameter("deviceWidth", String.valueOf(deviceInfo.getDeviceWidth()))
                .appendQueryParameter("deviceHeight", String.valueOf(deviceInfo.getDeviceHeight()))
                .appendQueryParameter("w", String.valueOf(adWidth))
                .appendQueryParameter("h", String.valueOf(adHeight))
                .build();
    }
}