package com.bidscube.sdk.openrtb;

import org.json.JSONObject;

/**
 * Locates the OpenRTB {@code video} object across supported SDK JSON response shapes.
 */
public final class OpenRtbVideoObjectParser {

    private OpenRtbVideoObjectParser() {
    }

    /**
     * Resolves pod-level video metadata from {@code openrtb.video}, {@code openRtb.video},
     * or root {@code video}.
     */
    public static JSONObject findVideoObject(JSONObject root) {
        if (root == null) {
            return null;
        }
        JSONObject openRtb = root.optJSONObject("openrtb");
        if (openRtb == null) {
            openRtb = root.optJSONObject("openRtb");
        }
        if (openRtb != null) {
            JSONObject video = openRtb.optJSONObject("video");
            if (video != null) {
                return video;
            }
        }
        return root.optJSONObject("video");
    }
}
