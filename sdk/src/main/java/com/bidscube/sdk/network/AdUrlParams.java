package com.bidscube.sdk.network;

import android.net.Uri;

import com.bidscube.sdk.models.DeviceInfo;

/**
 * Shared query-parameter helpers for SSP ad request URLs.
 */
final class AdUrlParams {
    private AdUrlParams() {
    }

    /**
     * Publisher {@code user_id} when configured; otherwise {@code null}.
     */
    static String resolveUserId(DeviceInfo deviceInfo) {
        if (deviceInfo == null) {
            return null;
        }
        String userId = deviceInfo.getUserId();
        if (userId == null || userId.isEmpty()) {
            return null;
        }
        return userId;
    }

    /**
     * Appends {@code user_id} when the publisher set a non-empty value on {@link DeviceInfo}.
     */
    static void appendUserId(Uri.Builder builder, DeviceInfo deviceInfo) {
        if (builder == null) {
            return;
        }
        String userId = resolveUserId(deviceInfo);
        if (userId != null) {
            builder.appendQueryParameter("user_id", userId);
        }
    }
}
