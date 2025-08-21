package com.bidscube.sdk.network;

import android.net.Uri;
import com.bidscube.sdk.device.models.DeviceInfo;

public class ImageAdUrlBuilder {
    private final String placementId;
    private final DeviceInfo deviceInfo;

    public ImageAdUrlBuilder(String placementId, DeviceInfo deviceInfo) {
        this.placementId = placementId;
        this.deviceInfo = deviceInfo;
    }

    public Uri build() {
        return new Uri.Builder()
                .scheme("https")
                .authority("ssp-bcc-ads.com")
                .appendPath("")
                .appendQueryParameter("placementId", placementId)
                .appendQueryParameter("c", "b")
                .appendQueryParameter("m", "api")
                .appendQueryParameter("res", "js")
                .appendQueryParameter("app", "1")
                .appendQueryParameter("bundle", deviceInfo.getBundle())
                .appendQueryParameter("name", deviceInfo.getAppName())
                .appendQueryParameter("app_store_url", deviceInfo.getAppStoreUrl())
                .appendQueryParameter("language", deviceInfo.getLanguage())
                .appendQueryParameter("deviceWidth", String.valueOf(deviceInfo.getDeviceWidth()))
                .appendQueryParameter("deviceHeight", String.valueOf(deviceInfo.getDeviceHeight()))
                .appendQueryParameter("ua", deviceInfo.getUserAgent())
                .appendQueryParameter("ifa", deviceInfo.getIfa())
                .appendQueryParameter("dnt", String.valueOf(deviceInfo.getDnt()))
                .build();
    }
}