package com.bidscube.sdk.network;

import com.bidscube.sdk.openrtb.OpenRtbPoddedResponseNormalizer;
import com.bidscube.sdk.openrtb.PoddedAdResponse;
import com.bidscube.sdk.utils.AdmPayloadUtils;
import com.bidscube.sdk.utils.SDKLogger;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Utility class to parse JSON responses into BidscubeResponse objects.
 */
public class BidscubeResponseParser {

    private static final String TAG = "BidscubeResponseParser";

    public static BidscubeResponse parse(String jsonString) {
        if (jsonString == null || jsonString.trim().isEmpty()) {
            return null;
        }
        String trimmed = jsonString.trim();
        if (trimmed.startsWith("<")) {
            return new BidscubeResponse(trimmed, 0);
        }
        try {
            JSONObject json = new JSONObject(trimmed);
            return parse(json, trimmed);
        } catch (JSONException e) {
            SDKLogger.e(TAG, "Failed to parse JSON response: " + e.getMessage());
            String repaired = AdmPayloadUtils.unwrapJsonAdmEnvelope(trimmed);
            if (repaired != null && repaired.trim().startsWith("<")) {
                return new BidscubeResponse(repaired, 0);
            }
            return null;
        }
    }

    public static BidscubeResponse parse(JSONObject json) {
        return parse(json, json != null ? json.toString() : null);
    }

    private static BidscubeResponse parse(JSONObject json, String rawJson) {
        try {
            String adm = json.optString("adm", "");
            int position = json.optInt("position", 0);

            PoddedAdResponse podded = OpenRtbPoddedResponseNormalizer.normalize(json, adm);
            if (podded != null && podded.isPodded()) {
                SDKLogger.d(TAG, "Parsed OpenRTB podded response slots=" + podded.getMarkups().size());
            }

            return new BidscubeResponse(adm, position, rawJson, podded);
        } catch (Exception e) {
            SDKLogger.e(TAG, "Failed to parse JSON object: " + e.getMessage());
            return null;
        }
    }
}
