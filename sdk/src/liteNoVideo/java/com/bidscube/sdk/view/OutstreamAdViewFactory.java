package com.bidscube.sdk.view;

import android.content.Context;
import android.graphics.Color;
import android.view.View;
import android.widget.TextView;

import com.bidscube.sdk.interfaces.AdCallback;

/**
 * Lite flavor: outstream is unavailable.
 */
public final class OutstreamAdViewFactory {

    private OutstreamAdViewFactory() {
    }

    public static View createBoundView(
            Context context,
            String placementId,
            String vastXml,
            String clickUrl,
            AdCallback callback) {
        if (callback != null) {
            callback.onAdFailed(placementId, -3, "Outstream requires fullVideo SDK artifact");
        }
        TextView error = new TextView(context);
        error.setText("Outstream video unavailable (liteNoVideo)");
        error.setTextColor(Color.WHITE);
        error.setBackgroundColor(Color.DKGRAY);
        error.setPadding(24, 24, 24, 24);
        return error;
    }
}
