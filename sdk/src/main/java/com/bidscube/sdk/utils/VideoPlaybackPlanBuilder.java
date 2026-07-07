package com.bidscube.sdk.utils;

import com.bidscube.sdk.models.video.VastMediaFile;
import com.bidscube.sdk.models.video.VastPreview;
import com.bidscube.sdk.models.video.VastTrackingEvents;
import com.bidscube.sdk.models.video.VideoAdSlot;
import com.bidscube.sdk.models.video.VideoPlaybackPlan;
import com.bidscube.sdk.models.video.VideoPlaybackPlanType;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Builds a {@link VideoPlaybackPlan} from inline VAST XML.
 */
public final class VideoPlaybackPlanBuilder {

    private static final String TAG = "VideoPlaybackPlanBuilder";

    private VideoPlaybackPlanBuilder() {
    }

    public static VideoPlaybackPlan build(String vastXml) {
        if (vastXml == null || vastXml.trim().isEmpty()) {
            SDKLogger.w(TAG, "Empty VAST XML");
            return VideoPlaybackPlan.empty();
        }
        try {
            Document doc = VastXmlParser.parse(vastXml);
            NodeList adNodes = doc.getElementsByTagName("Ad");
            if (adNodes.getLength() == 0) {
                SDKLogger.e(TAG, "No <Ad> elements in VAST");
                return VideoPlaybackPlan.empty();
            }

            List<AdParseResult> parsed = new ArrayList<>();
            for (int i = 0; i < adNodes.getLength(); i++) {
                Element ad = (Element) adNodes.item(i);
                String seqAttr = ad.getAttribute("sequence");
                VideoAdSlot slot = parseAdSlot(ad, i + 1, seqAttr);
                if (slot != null) {
                    parsed.add(new AdParseResult(slot, seqAttr, i));
                } else {
                    SDKLogger.w(TAG, "Skipping ad without playable Linear media: id=" + ad.getAttribute("id"));
                }
            }

            if (parsed.isEmpty()) {
                SDKLogger.e(TAG, "No playable ads after parsing");
                return VideoPlaybackPlan.empty();
            }

            List<AdParseResult> sequenced = new ArrayList<>();
            List<AdParseResult> nonSequenced = new ArrayList<>();
            for (AdParseResult result : parsed) {
                if (result.hasSequence()) {
                    sequenced.add(result);
                } else {
                    nonSequenced.add(result);
                }
            }

            VideoPlaybackPlanType type;
            List<VideoAdSlot> slots;

            if (sequenced.size() >= 2) {
                type = VideoPlaybackPlanType.POD;
                sortSequencedAds(sequenced);
                slots = toSlots(sequenced);
                if (!nonSequenced.isEmpty()) {
                    SDKLogger.w(TAG, "Ignoring " + nonSequenced.size()
                            + " non-sequenced ads in sequenced pod response");
                }
                SDKLogger.d(TAG, "Plan type=POD ads=" + slots.size());
            } else if (parsed.size() >= 2 && sequenced.isEmpty()) {
                type = VideoPlaybackPlanType.SINGLE;
                VideoAdSlot best = selectBestBuffetAd(parsed);
                slots = best != null ? Collections.singletonList(best) : Collections.emptyList();
                SDKLogger.w(TAG, "Ad buffet (" + parsed.size() + " ads) — selected one playable ad");
                if (slots.isEmpty()) {
                    return VideoPlaybackPlan.empty();
                }
            } else {
                type = VideoPlaybackPlanType.SINGLE;
                if (sequenced.size() == 1 && !nonSequenced.isEmpty()) {
                    SDKLogger.w(TAG, "Single sequenced ad with extra non-sequenced ads — playing sequenced ad only");
                    slots = Collections.singletonList(sequenced.get(0).slot);
                } else {
                    slots = toSlots(parsed);
                }
            }

            long totalDuration = 0L;
            for (VideoAdSlot slot : slots) {
                if (slot.getDurationMs() != null) {
                    totalDuration += slot.getDurationMs();
                }
            }

            VastPreview endCard = resolveEndCardPreview(doc);
            SDKLogger.d(TAG, "Built playback plan type=" + type + " ads=" + slots.size());
            return new VideoPlaybackPlan(
                    type,
                    slots,
                    !sequenced.isEmpty(),
                    totalDuration > 0 ? totalDuration : null,
                    endCard);
        } catch (Exception e) {
            SDKLogger.e(TAG, "Failed to build playback plan: " + e.getMessage());
            return VideoPlaybackPlan.empty();
        }
    }

    private static void sortSequencedAds(List<AdParseResult> sequenced) {
        sequenced.sort(Comparator
                .comparingInt((AdParseResult r) -> r.slot.getVastSequence() != null
                        ? r.slot.getVastSequence()
                        : r.slot.getPlaybackIndex())
                .thenComparingInt(r -> r.xmlOrder));
        Set<Integer> seen = new HashSet<>();
        for (AdParseResult result : sequenced) {
            Integer seq = result.slot.getVastSequence();
            if (seq == null) {
                continue;
            }
            if (!seen.add(seq)) {
                SDKLogger.w(TAG, "Duplicate sequence=" + seq + " — keeping stable XML order for duplicates");
            }
        }
    }

    private static VideoAdSlot selectBestBuffetAd(List<AdParseResult> parsed) {
        VideoAdSlot best = null;
        int bestScore = Integer.MIN_VALUE;
        for (AdParseResult result : parsed) {
            VastMediaFile media = result.slot.getMediaFile();
            int score = scoreBuffetSlot(media);
            if (score > bestScore) {
                bestScore = score;
                best = result.slot;
            }
        }
        return best;
    }

    private static int scoreBuffetSlot(VastMediaFile media) {
        if (media == null) {
            return 0;
        }
        int score = 0;
        String mime = media.getMimeType() != null ? media.getMimeType().toLowerCase(Locale.US) : "";
        if (mime.contains("mp4")) {
            score += 100;
        }
        if (media.getBitrate() != null) {
            score += Math.min(media.getBitrate(), 5000);
        }
        return score;
    }

    private static List<VideoAdSlot> toSlots(List<AdParseResult> results) {
        List<VideoAdSlot> slots = new ArrayList<>(results.size());
        for (AdParseResult result : results) {
            slots.add(result.slot);
        }
        return slots;
    }

    private static VideoAdSlot parseAdSlot(Element ad, int fallbackSequence, String sequenceAttr) {
        Element linear = firstLinear(ad);
        if (linear == null) {
            return null;
        }
        List<VastMediaFile> mediaFiles = parseMediaFiles(linear);
        VastMediaFile selected = VastMediaFileSelector.selectBest(mediaFiles);
        if (selected == null) {
            return null;
        }

        int sequence = parseSequence(sequenceAttr, fallbackSequence);
        Integer vastSequence = (sequenceAttr == null || sequenceAttr.trim().isEmpty())
                ? null
                : sequence;
        String adId = emptyToNull(ad.getAttribute("id"));
        String title = firstText(ad, "AdTitle");
        Long durationMs = parseDurationMs(firstText(linear, "Duration"));
        Long skipOffsetMs = parseSkipOffsetMs(linear.getAttribute("skipoffset"));
        String clickThrough = firstClickThrough(ad);
        VastTrackingEvents tracking = parseTracking(ad, linear);
        VastPreview preview = parseCompanionPreview(ad);

        return new VideoAdSlot(
                adId,
                fallbackSequence,
                vastSequence,
                null,
                null,
                null,
                null,
                selected,
                durationMs,
                skipOffsetMs,
                clickThrough,
                tracking,
                preview,
                title,
                vastSequence != null
                        ? com.bidscube.sdk.openrtb.PodOrderingSource.VAST_SEQUENCE
                        : com.bidscube.sdk.openrtb.PodOrderingSource.RESPONSE_ORDER);
    }

    private static Element firstLinear(Element ad) {
        NodeList linearNodes = ad.getElementsByTagName("Linear");
        if (linearNodes.getLength() == 0) {
            return null;
        }
        return (Element) linearNodes.item(0);
    }

    private static List<VastMediaFile> parseMediaFiles(Element linear) {
        List<VastMediaFile> files = new ArrayList<>();
        NodeList mediaNodes = linear.getElementsByTagName("MediaFile");
        for (int i = 0; i < mediaNodes.getLength(); i++) {
            Element mf = (Element) mediaNodes.item(i);
            String url = mf.getTextContent();
            if (url == null || url.trim().isEmpty()) {
                continue;
            }
            String mime = emptyToNull(mf.getAttribute("type"));
            if (VastMediaFileSelector.isUnsupportedMime(mime)) {
                SDKLogger.d(TAG, "Skipping unsupported MediaFile mime=" + mime);
                continue;
            }
            files.add(new VastMediaFile(
                    url.trim(),
                    mime,
                    parseIntAttr(mf, "width"),
                    parseIntAttr(mf, "height"),
                    parseIntAttr(mf, "bitrate"),
                    emptyToNull(mf.getAttribute("delivery"))));
        }
        return files;
    }

    private static VastTrackingEvents parseTracking(Element ad, Element linear) {
        List<String> impressions = collectUrls(ad, "Impression");
        Map<String, List<String>> tracking = new HashMap<>();
        NodeList trackingNodes = linear.getElementsByTagName("Tracking");
        for (int i = 0; i < trackingNodes.getLength(); i++) {
            Element node = (Element) trackingNodes.item(i);
            String event = node.getAttribute("event");
            if (event == null || event.isEmpty()) {
                continue;
            }
            String url = node.getTextContent();
            if (url == null || url.trim().isEmpty()) {
                continue;
            }
            String key = event.toLowerCase(Locale.US);
            tracking.computeIfAbsent(key, k -> new ArrayList<>()).add(url.trim());
        }
        List<String> clickTrackings = collectUrls(linear, "ClickTracking");
        return new VastTrackingEvents(
                impressions,
                tracking.get("start"),
                tracking.get("firstquartile"),
                tracking.get("midpoint"),
                tracking.get("thirdquartile"),
                tracking.get("complete"),
                tracking.get("skip"),
                clickTrackings,
                tracking.get("error"),
                tracking.get("pause"),
                tracking.get("resume"),
                tracking.get("mute"),
                tracking.get("unmute"),
                tracking.get("close"));
    }

    private static List<String> collectUrls(Element root, String tag) {
        List<String> urls = new ArrayList<>();
        NodeList nodes = root.getElementsByTagName(tag);
        for (int i = 0; i < nodes.getLength(); i++) {
            String url = nodes.item(i).getTextContent();
            if (url != null && !url.trim().isEmpty()) {
                urls.add(url.trim());
            }
        }
        return urls;
    }

    private static VastPreview parseCompanionPreview(Element ad) {
        NodeList companions = ad.getElementsByTagName("Companion");
        if (companions.getLength() == 0) {
            return null;
        }
        Element companion = (Element) companions.item(0);
        String image = null;
        NodeList staticResources = companion.getElementsByTagName("StaticResource");
        if (staticResources.getLength() > 0) {
            image = staticResources.item(0).getTextContent();
        }
        String click = null;
        NodeList clickThrough = companion.getElementsByTagName("CompanionClickThrough");
        if (clickThrough.getLength() > 0) {
            click = clickThrough.item(0).getTextContent();
        }
        if (image == null || image.trim().isEmpty()) {
            return null;
        }
        return new VastPreview(image.trim(), click != null ? click.trim() : null);
    }

    private static VastPreview resolveEndCardPreview(Document doc) {
        NodeList ads = doc.getElementsByTagName("Ad");
        for (int i = ads.getLength() - 1; i >= 0; i--) {
            VastPreview preview = parseCompanionPreview((Element) ads.item(i));
            if (preview != null && preview.hasImage()) {
                return preview;
            }
        }
        return null;
    }

    private static String firstClickThrough(Element ad) {
        NodeList videoClicks = ad.getElementsByTagName("VideoClicks");
        if (videoClicks.getLength() > 0) {
            Element vc = (Element) videoClicks.item(0);
            NodeList ct = vc.getElementsByTagName("ClickThrough");
            if (ct.getLength() > 0) {
                String url = ct.item(0).getTextContent();
                if (url != null && !url.trim().isEmpty()) {
                    return url.trim();
                }
            }
        }
        NodeList clickThrough = ad.getElementsByTagName("ClickThrough");
        if (clickThrough.getLength() > 0) {
            String url = clickThrough.item(0).getTextContent();
            if (url != null && !url.trim().isEmpty()) {
                return url.trim();
            }
        }
        return null;
    }

    private static String firstText(Element root, String tag) {
        NodeList nodes = root.getElementsByTagName(tag);
        if (nodes.getLength() == 0) {
            return null;
        }
        String text = nodes.item(0).getTextContent();
        return text != null && !text.trim().isEmpty() ? text.trim() : null;
    }

    private static Long parseDurationMs(String duration) {
        if (duration == null || duration.trim().isEmpty()) {
            return null;
        }
        try {
            String[] parts = duration.trim().split(":");
            if (parts.length == 3) {
                int h = Integer.parseInt(parts[0]);
                int m = Integer.parseInt(parts[1]);
                double s = Double.parseDouble(parts[2]);
                return (long) ((h * 3600 + m * 60 + s) * 1000);
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static Long parseSkipOffsetMs(String skipOffset) {
        if (skipOffset == null || skipOffset.trim().isEmpty()) {
            return null;
        }
        int seconds = VastParser.getSkipOffsetSecondsFromValue(skipOffset.trim());
        return seconds > 0 ? seconds * 1000L : null;
    }

    private static int parseSequence(String value, int fallback) {
        if (value == null || value.trim().isEmpty()) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            SDKLogger.w(TAG, "Invalid sequence value: " + value);
            return fallback;
        }
    }

    private static Integer parseIntAttr(Element element, String name) {
        String value = element.getAttribute(name);
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static String emptyToNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }

    private static final class AdParseResult {
        final VideoAdSlot slot;
        final String sequenceAttr;
        final int xmlOrder;

        AdParseResult(VideoAdSlot slot, String sequenceAttr, int xmlOrder) {
            this.slot = slot;
            this.sequenceAttr = sequenceAttr;
            this.xmlOrder = xmlOrder;
        }

        boolean hasSequence() {
            return sequenceAttr != null && !sequenceAttr.trim().isEmpty();
        }
    }
}
