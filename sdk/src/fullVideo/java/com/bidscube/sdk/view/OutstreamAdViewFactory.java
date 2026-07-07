package com.bidscube.sdk.view;

import android.content.Context;
import android.view.View;

import com.bidscube.sdk.interfaces.AdCallback;

/**
 * Creates outstream views in the fullVideo flavor.
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
        OutstreamVideoAdView view = new OutstreamVideoAdView(context);
        view.bindVast(placementId, vastXml, clickUrl, callback);
        return view;
    }
}
