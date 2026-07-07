package com.bidscube.sdk.view;

import android.content.Context;

/**
 * Creates {@link Media3VideoSlotPlayer} in the fullVideo flavor.
 */
public final class VideoSlotPlayerFactory {

    private VideoSlotPlayerFactory() {
    }

    public static VideoSlotPlayer create(Context context) {
        return new Media3VideoSlotPlayer(context);
    }
}
