package com.bidscube.sdk.ads;

import android.net.Uri;

import com.bidscube.sdk.models.DeviceInfo;

public interface AdType {

    enum Type {
        IMAGE,
        VIDEO,
        NATIVE,
        SKIP_VIDEO,
    }

    Uri buildRequestUrl(DeviceInfo deviceInfo);
}
