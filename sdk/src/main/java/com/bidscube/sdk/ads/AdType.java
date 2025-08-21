package com.bidscube.sdk.ads;

import android.net.Uri;

import com.bidscube.sdk.device.models.DeviceInfo;

public interface AdType {
    Uri buildRequestUrl(DeviceInfo deviceInfo);
}
