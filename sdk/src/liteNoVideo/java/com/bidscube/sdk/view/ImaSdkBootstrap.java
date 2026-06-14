package com.bidscube.sdk.view;

import android.content.Context;

/** No-op for lite artifact without IMA on the classpath. */
public final class ImaSdkBootstrap {

    private ImaSdkBootstrap() {
    }

    public static void initialize(Context context) {
        // no-op
    }
}
