package com.bidscube.sdk.openrtb;

import com.bidscube.sdk.utils.SDKLogger;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Normalizes OpenRTB-like pod metadata and bid markups from SDK JSON responses.
 */
public final class OpenRtbPoddedResponseNormalizer {

    private static final String TAG = "OpenRtbPodNormalizer";

    private OpenRtbPoddedResponseNormalizer() {
    }

    /**
     * Attempts to parse podded response from raw JSON. Returns null when not podded.
     */
    public static PoddedAdResponse normalize(String jsonString, String fallbackAdm) {
        if (jsonString == null || jsonString.trim().isEmpty()) {
            return null;
        }
        try {
            JSONObject root = new JSONObject(jsonString.trim());
            return normalize(root, fallbackAdm);
        } catch (Exception e) {
            SDKLogger.d(TAG, "Not a JSON pod response: " + e.getMessage());
            return null;
        }
    }

    public static PoddedAdResponse normalize(JSONObject root, String fallbackAdm) {
        if (root == null) {
            return null;
        }

        // Shape B: multiple bids under podid / bids array
        JSONArray bids = root.optJSONArray("bids");
        String podId = firstNonEmpty(
                root.optString("podid", null),
                root.optString("podId", null));
        if (bids != null && bids.length() > 0) {
            return normalizeMultiBid(root, podId, bids);
        }

        // Shape A: single adm + openrtb.video metadata
        JSONObject video = OpenRtbVideoObjectParser.findVideoObject(root);

        String adm = firstNonEmpty(root.optString("adm", null), fallbackAdm);
        if (video != null && adm != null && !adm.isEmpty()) {
            OpenRtbVideoPodContext podContext = parsePodContext(video, podId);
            if (podContext.hasPodMetadata()) {
                Integer slotInPod = optSlotInPod(video);
                if (slotInPod == null) {
                    slotInPod = optSlotInPod(root);
                }
                OpenRtbVideoSlotContext slotContext = parseSlotContext(
                        video,
                        root.optString("impid", root.optString("impId", null)),
                        podContext.getPodId(),
                        slotInPod);
                PoddedAdMarkup markup = new PoddedAdMarkup(
                        slotContext.getImpressionId(),
                        slotInPod,
                        adm,
                        slotContext,
                        0);
                return new PoddedAdResponse(podContext, Collections.singletonList(markup));
            }
        }

        // seatbid[].bid[] OpenRTB shape
        JSONArray seatbid = root.optJSONArray("seatbid");
        if (seatbid != null) {
            return normalizeSeatBid(seatbid);
        }

        return null;
    }

    private static PoddedAdResponse normalizeMultiBid(JSONObject root, String podId, JSONArray bids) {
        JSONObject video = OpenRtbVideoObjectParser.findVideoObject(root);
        OpenRtbVideoPodContext podContext = parsePodContext(video, podId);
        if (podContext.getPodId() == null && podId != null) {
            podContext = new OpenRtbVideoPodContext(
                    podId,
                    podContext.getPodSequence(),
                    podContext.getPodDurationSec(),
                    podContext.getMaxSequence(),
                    null,
                    podContext.getRequiredDurationsSec(),
                    podContext.getMinCpmPerSec(),
                    classifyPod(video, podContext),
                    PodOrderingSource.OPENRTB_SLOT_IN_POD);
        }
        List<PoddedAdMarkup> markups = new ArrayList<>();
        for (int i = 0; i < bids.length(); i++) {
            JSONObject bid = bids.optJSONObject(i);
            if (bid == null) {
                continue;
            }
            String adm = bid.optString("adm", null);
            if (adm == null || adm.isEmpty()) {
                continue;
            }
            String impId = firstNonEmpty(bid.optString("impid", null), bid.optString("impId", null));
            Integer slotInPod = optSlotInPod(bid);
            OpenRtbVideoSlotContext slotContext = parseSlotContext(
                    bid,
                    impId,
                    podContext.getPodId(),
                    slotInPod);
            markups.add(new PoddedAdMarkup(impId, slotInPod, adm, slotContext, i));
        }
        if (markups.isEmpty()) {
            return null;
        }
        PodType type = classifyPodFromMarkups(markups, podContext, video);
        OpenRtbVideoPodContext enriched = new OpenRtbVideoPodContext(
                podContext.getPodId(),
                podContext.getPodSequence(),
                podContext.getPodDurationSec(),
                podContext.getMaxSequence(),
                null,
                podContext.getRequiredDurationsSec(),
                podContext.getMinCpmPerSec(),
                type,
                PodOrderingSource.OPENRTB_SLOT_IN_POD);
        SDKLogger.d(TAG, "Normalized multi-bid pod podId=" + enriched.getPodId() + " slots=" + markups.size());
        return new PoddedAdResponse(enriched, markups);
    }

    private static PoddedAdResponse normalizeSeatBid(JSONArray seatbid) {
        Map<String, List<PoddedAdMarkup>> byPod = new HashMap<>();
        Map<String, OpenRtbVideoPodContext> podContexts = new HashMap<>();
        int order = 0;
        for (int s = 0; s < seatbid.length(); s++) {
            JSONObject seat = seatbid.optJSONObject(s);
            if (seat == null) {
                continue;
            }
            JSONArray bidArr = seat.optJSONArray("bid");
            if (bidArr == null) {
                continue;
            }
            for (int b = 0; b < bidArr.length(); b++) {
                JSONObject bid = bidArr.optJSONObject(b);
                if (bid == null) {
                    continue;
                }
                String adm = bid.optString("adm", null);
                if (adm == null || adm.isEmpty()) {
                    continue;
                }
                JSONObject ext = bid.optJSONObject("ext");
                String podId = null;
                Integer slotInPod = null;
                if (ext != null) {
                    podId = firstNonEmpty(ext.optString("podid", null), ext.optString("podId", null));
                    slotInPod = optSlotInPod(ext);
                }
                if (slotInPod == null) {
                    slotInPod = optSlotInPod(bid);
                }
                String impId = bid.optString("impid", bid.optString("impId", null));
                if (podId == null) {
                    continue;
                }
                OpenRtbVideoSlotContext slotContext = parseSlotContext(bid, impId, podId, slotInPod);
                PoddedAdMarkup markup = new PoddedAdMarkup(
                        impId,
                        slotContext.getSlotInPod(),
                        adm,
                        slotContext,
                        order++);
                byPod.computeIfAbsent(podId, k -> new ArrayList<>()).add(markup);
                podContexts.putIfAbsent(podId, new OpenRtbVideoPodContext(
                        podId, null, null, null, null,
                        Collections.emptyList(), null, PodType.UNKNOWN, PodOrderingSource.OPENRTB_SLOT_IN_POD));
            }
        }
        if (byPod.isEmpty()) {
            return null;
        }
        if (byPod.size() > 1) {
            TreeMap<String, List<PoddedAdMarkup>> sortedPods = new TreeMap<>(byPod);
            String firstPodId = sortedPods.firstKey();
            SDKLogger.e(TAG, "Multiple seatbid pod groups (" + byPod.size()
                    + ") are not supported; playing podId=" + firstPodId + " only");
            return new PoddedAdResponse(podContexts.get(firstPodId), sortedPods.get(firstPodId));
        }
        TreeMap<String, List<PoddedAdMarkup>> sortedPods = new TreeMap<>(byPod);
        String firstPodId = sortedPods.firstKey();
        return new PoddedAdResponse(podContexts.get(firstPodId), sortedPods.get(firstPodId));
    }

    static OpenRtbVideoPodContext parsePodContext(JSONObject video, String fallbackPodId) {
        if (video == null) {
            String podId = fallbackPodId;
            return new OpenRtbVideoPodContext(
                    podId, null, null, null, null,
                    Collections.emptyList(), null, PodType.NONE, PodOrderingSource.RESPONSE_ORDER);
        }
        String podId = firstNonEmpty(
                video.optString("podid", null),
                video.optString("podId", null),
                fallbackPodId);
        Integer podSeq = optPositiveInt(video, "podseq", "podSeq");
        Integer podDur = optPositiveInt(video, "poddur", "podDur");
        Integer maxSeq = optPositiveInt(video, "maxseq", "maxSeq");
        Integer slotInPod = optPositiveInt(video, "slotinpod", "slotInPod");
        List<Integer> rqddurs = parseIntArray(video.optJSONArray("rqddurs"));
        if (rqddurs.isEmpty()) {
            rqddurs = parseIntArray(video.optJSONArray("rqdDurs"));
        }
        float minCpm = (float) video.optDouble("mincpmpersec", video.optDouble("minCpmPerSec", -1));
        Float minCpmPerSec = minCpm >= 0 ? minCpm : null;
        OpenRtbVideoPodContext ctx = new OpenRtbVideoPodContext(
                podId,
                podSeq,
                podDur,
                maxSeq,
                slotInPod,
                rqddurs,
                minCpmPerSec,
                PodType.NONE,
                PodOrderingSource.RESPONSE_ORDER);
        PodType type = classifyPod(video, ctx);
        return new OpenRtbVideoPodContext(
                podId, podSeq, podDur, maxSeq, slotInPod, rqddurs, minCpmPerSec, type,
                slotInPod != null || !rqddurs.isEmpty()
                        ? PodOrderingSource.OPENRTB_SLOT_IN_POD
                        : PodOrderingSource.RESPONSE_ORDER);
    }

    static OpenRtbVideoSlotContext parseSlotContext(
            JSONObject source,
            String impId,
            String podId,
            Integer slotInPod) {
        Integer reqDur = optPositiveInt(source, "rqddurs", "requiredDurationSec");
        if (reqDur == null && source.has("rqddurs")) {
            List<Integer> durs = parseIntArray(source.optJSONArray("rqddurs"));
            if (!durs.isEmpty()) {
                reqDur = durs.get(0);
            }
        }
        Integer minDur = optPositiveInt(source, "minduration", "minDuration");
        Integer maxDur = optPositiveInt(source, "maxduration", "maxDuration");
        Boolean rewarded = source.has("rewarded") ? source.optBoolean("rewarded") : null;
        Map<String, Object> ext = jsonToMap(source.optJSONObject("ext"));
        return new OpenRtbVideoSlotContext(
                impId,
                podId,
                slotInPod,
                reqDur,
                minDur,
                maxDur,
                rewarded,
                ext);
    }

    static PodType classifyPod(JSONObject video, OpenRtbVideoPodContext ctx) {
        return classifyPodFromMarkups(null, ctx, video);
    }

    static PodType classifyPodFromMarkups(
            List<PoddedAdMarkup> markups,
            OpenRtbVideoPodContext ctx,
            JSONObject video) {
        int withSlot = 0;
        int markupCount = markups != null ? markups.size() : 0;
        if (markups != null) {
            for (PoddedAdMarkup markup : markups) {
                if (markup.getSlotInPod() != null) {
                    withSlot++;
                }
            }
        }

        boolean hasSlot = ctx.getSlotInPod() != null || withSlot > 0;
        boolean hasRqddurs = !ctx.getRequiredDurationsSec().isEmpty();
        boolean hasPodDur = ctx.getPodDurationSec() != null;
        boolean hasMaxSeq = ctx.getMaxSequence() != null;

        if (withSlot > 0 && hasRqddurs && hasMaxSeq) {
            return PodType.STRUCTURED;
        }
        if (withSlot > 0 && withSlot < markupCount && hasPodDur) {
            return PodType.HYBRID;
        }
        if (hasSlot && hasRqddurs) {
            return PodType.STRUCTURED;
        }
        if (hasSlot && hasPodDur && withSlot > 0 && withSlot < markupCount) {
            return PodType.HYBRID;
        }
        if (hasSlot || hasRqddurs) {
            return PodType.STRUCTURED;
        }

        if (video != null) {
            JSONArray bids = video.optJSONArray("bids");
            if (bids != null && bids.length() > 0) {
                int bidsWithSlot = 0;
                for (int i = 0; i < bids.length(); i++) {
                    JSONObject b = bids.optJSONObject(i);
                    if (b != null && (b.has("slotinpod") || b.has("slotInPod"))) {
                        bidsWithSlot++;
                    }
                }
                if (bidsWithSlot > 0 && hasPodDur) {
                    return PodType.HYBRID;
                }
                if (bidsWithSlot > 0) {
                    return PodType.STRUCTURED;
                }
            }
        }

        if (hasPodDur) {
            return PodType.DYNAMIC;
        }
        if (ctx.getPodId() != null) {
            return PodType.UNKNOWN;
        }
        return PodType.NONE;
    }

    private static List<Integer> parseIntArray(JSONArray array) {
        if (array == null) {
            return Collections.emptyList();
        }
        List<Integer> result = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            result.add(array.optInt(i));
        }
        return result;
    }

    private static Integer optPositiveInt(JSONObject obj, String... keys) {
        for (String key : keys) {
            if (obj.has(key)) {
                int v = obj.optInt(key, -1);
                if (v > 0) {
                    return v;
                }
            }
        }
        return null;
    }

    private static Map<String, Object> jsonToMap(JSONObject obj) {
        if (obj == null) {
            return Collections.emptyMap();
        }
        Map<String, Object> map = new HashMap<>();
        Iterator<String> keys = obj.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            map.put(key, obj.opt(key));
        }
        return map;
    }

    private static String firstNonEmpty(String... values) {
        for (String v : values) {
            if (v != null && !v.trim().isEmpty()) {
                return v.trim();
            }
        }
        return null;
    }

    /** Returns {@code null} when slotinpod is absent — never synthesize from array index. */
    static Integer optSlotInPod(JSONObject obj) {
        if (obj == null) {
            return null;
        }
        Integer fromObj = optPositiveInt(obj, "slotinpod", "slotInPod");
        if (fromObj != null) {
            return fromObj;
        }
        JSONObject ext = obj.optJSONObject("ext");
        if (ext != null) {
            return optPositiveInt(ext, "slotinpod", "slotInPod");
        }
        return null;
    }
}
