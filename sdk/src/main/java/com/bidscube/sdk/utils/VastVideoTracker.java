package com.bidscube.sdk.utils;

import com.bidscube.sdk.models.video.VideoAdSlot;
import com.bidscube.sdk.network.TrackerPinger;

import java.util.ArrayList;
import java.util.List;

/**
 * Fires VAST tracking beacons for one {@link VideoAdSlot}.
 */
public final class VastVideoTracker {

    private static final String TAG = "VastVideoTracker";

    private VastVideoTracker() {
    }

    public static void fireImpression(VideoAdSlot slot) {
        if (slot == null) {
            return;
        }
        int fired = TrackerPinger.pingUrls(source(slot, "impression"), slot.getTrackingEvents().getImpressions());
        SDKLogger.d(TAG, "impression adId=" + slot.getAdId() + " fired=" + fired);
    }

    public static void fireStart(VideoAdSlot slot) {
        ping(slot, "start", slot.getTrackingEvents().getStarts());
    }

    public static void fireFirstQuartile(VideoAdSlot slot) {
        ping(slot, "firstQuartile", slot.getTrackingEvents().getFirstQuartiles());
    }

    public static void fireMidpoint(VideoAdSlot slot) {
        ping(slot, "midpoint", slot.getTrackingEvents().getMidpoints());
    }

    public static void fireThirdQuartile(VideoAdSlot slot) {
        ping(slot, "thirdQuartile", slot.getTrackingEvents().getThirdQuartiles());
    }

    public static void fireComplete(VideoAdSlot slot) {
        ping(slot, "complete", slot.getTrackingEvents().getCompletes());
    }

    public static void fireSkip(VideoAdSlot slot) {
        ping(slot, "skip", slot.getTrackingEvents().getSkips());
    }

    public static void fireClick(VideoAdSlot slot) {
        ping(slot, "click", slot.getTrackingEvents().getClickTrackings());
    }

    public static void fireError(VideoAdSlot slot, String errorCode) {
        if (slot == null) {
            return;
        }
        List<String> urls = slot.getTrackingEvents().getErrors();
        if (urls == null || urls.isEmpty()) {
            return;
        }
        String code = errorCode != null && !errorCode.trim().isEmpty() ? errorCode.trim() : "900";
        List<String> resolved = new ArrayList<>(urls.size());
        for (String url : urls) {
            if (url == null || url.trim().isEmpty()) {
                continue;
            }
            resolved.add(replaceErrorCodeMacro(url.trim(), code));
        }
        int fired = TrackerPinger.pingUrls(source(slot, "error"), resolved);
        SDKLogger.w(TAG, "error adId=" + slot.getAdId() + " code=" + code + " fired=" + fired);
    }

    static String replaceErrorCodeMacro(String url, String errorCode) {
        return url
                .replace("[ERRORCODE]", errorCode)
                .replace("[errorcode]", errorCode)
                .replace("%5BERRORCODE%5D", errorCode)
                .replace("%5berrorcode%5d", errorCode);
    }

    private static void ping(VideoAdSlot slot, String event, java.util.List<String> urls) {
        if (slot == null) {
            return;
        }
        int fired = TrackerPinger.pingUrls(source(slot, event), urls);
        SDKLogger.d(TAG, event + " adId=" + slot.getAdId() + " fired=" + fired);
    }

    private static String source(VideoAdSlot slot, String event) {
        return "vast-" + event + "-" + (slot.getAdId() != null ? slot.getAdId() : slot.getSequence());
    }
}
