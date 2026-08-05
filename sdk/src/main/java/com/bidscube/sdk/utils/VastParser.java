package com.bidscube.sdk.utils;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import com.bidscube.sdk.models.PlayableAdConfig;
import com.bidscube.sdk.models.VastAdPodItem;
import com.bidscube.sdk.models.video.VastCompanion;
import com.bidscube.sdk.models.video.VastPreview;
import com.bidscube.sdk.models.video.VideoPlaybackPlanType;

public class VastParser {

    /**
     * Get ClickThrough URL from VAST XML
     * @param vastXml The VAST XML string to parse
     * @return ClickThrough URL if found, null otherwise
     */
    public static String getClickThroughUrl(String vastXml) {
        if (vastXml == null || vastXml.trim().isEmpty()) {
            System.err.println("VAST XML is null or empty");
            return null;
        }

        try {
            Document doc = parseDocument(vastXml);

            NodeList videoClicksNodes = doc.getElementsByTagName("VideoClicks");
            if (videoClicksNodes.getLength() > 0) {
                Element videoClicks = (Element) videoClicksNodes.item(0);
                NodeList clickThroughInVideoClicks = videoClicks.getElementsByTagName("ClickThrough");
                if (clickThroughInVideoClicks.getLength() > 0) {
                    String url = clickThroughInVideoClicks.item(0).getTextContent();
                    if (url != null && !url.trim().isEmpty()) {
                        System.out.println("Found ClickThrough URL in VideoClicks: " + url.trim());
                        return url.trim();
                    }
                }
            }

            NodeList clickThroughNodes = doc.getElementsByTagName("ClickThrough");
            if (clickThroughNodes.getLength() > 0) {
                String url = clickThroughNodes.item(0).getTextContent();
                if (url != null && !url.trim().isEmpty()) {
                    System.out.println("Found ClickThrough URL: " + url.trim());
                    return url.trim();
                }
            }

            NodeList clickTrackingNodes = doc.getElementsByTagName("ClickTracking");
            if (clickTrackingNodes.getLength() > 0) {
                String url = clickTrackingNodes.item(0).getTextContent();
                if (url != null && !url.trim().isEmpty()) {
                    System.out.println("Found ClickTracking URL: " + url.trim());
                    return url.trim();
                }
            }

            System.out.println("No ClickThrough tag found. Available tags:");
            printAvailableTags(doc);

        } catch (Exception e) {
            System.err.println("Error parsing VAST XML: " + e.getMessage());
            e.printStackTrace();
        }

        System.err.println("No ClickThrough tag found");
        return null;
    }

    /**
     * Get Video ClickThrough URL from VAST XML (specifically for video ads)
     * @param vastXml The VAST XML string to parse
     * @return Video ClickThrough URL if found, null otherwise
     */
    public static String getVideoClickThroughUrl(String vastXml) {
        if (vastXml == null || vastXml.trim().isEmpty()) {
            System.err.println("VAST XML is null or empty");
            return null;
        }

        try {
            Document doc = parseDocument(vastXml);

            NodeList videoClicksNodes = doc.getElementsByTagName("VideoClicks");
            if (videoClicksNodes.getLength() > 0) {
                Element videoClicks = (Element) videoClicksNodes.item(0);
                NodeList clickThroughNodes = videoClicks.getElementsByTagName("ClickThrough");
                if (clickThroughNodes.getLength() > 0) {
                    String url = clickThroughNodes.item(0).getTextContent();
                    if (url != null && !url.trim().isEmpty()) {
                        System.out.println("Found Video ClickThrough URL: " + url.trim());
                        return url.trim();
                    }
                }
            }

            NodeList clickThroughNodes = doc.getElementsByTagName("ClickThrough");
            if (clickThroughNodes.getLength() > 0) {
                String url = clickThroughNodes.item(0).getTextContent();
                if (url != null && !url.trim().isEmpty()) {
                    System.out.println("Found general ClickThrough URL: " + url.trim());
                    return url.trim();
                }
            }

            System.err.println("No Video ClickThrough URL found");
            return null;

        } catch (Exception e) {
            System.err.println("Error parsing VAST XML for video click-through: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Validate VAST XML structure
     * @param vastXml The VAST XML string to validate
     * @return true if valid, false otherwise
     */
    public static boolean validateVastStructure(String vastXml) {
        if (vastXml == null || vastXml.trim().isEmpty()) {
            return false;
        }

        try {
            Document doc = parseDocument(vastXml);

            if (!"VAST".equals(doc.getDocumentElement().getTagName())) {
                System.err.println("Root element is not VAST");
                return false;
            }

            NodeList adNodes = doc.getElementsByTagName("Ad");
            if (adNodes.getLength() == 0) {
                System.err.println("No Ad element found");
                return false;
            }

            NodeList creativeNodes = doc.getElementsByTagName("Creative");
            if (creativeNodes.getLength() == 0) {
                System.err.println("No Creative element found");
                return false;
            }

            NodeList mediaFileNodes = doc.getElementsByTagName("MediaFile");
            if (mediaFileNodes.getLength() == 0) {
                System.err.println("No MediaFile element found");
                return false;
            }

            System.out.println("VAST structure validation passed");
            return true;

        } catch (Exception e) {
            System.err.println("Error validating VAST structure: " + e.getMessage());
            return false;
        }
    }

    /**
     * Get MediaFile URL from VAST XML
     * @param vastXml The VAST XML string to parse
     * @return MediaFile URL if found, null otherwise
     */
    public static String getMediaFileUrl(String vastXml) {
        if (vastXml == null || vastXml.trim().isEmpty()) {
            return null;
        }

        try {
            Document doc = parseDocument(vastXml);

            NodeList mediaFileNodes = doc.getElementsByTagName("MediaFile");
            if (mediaFileNodes.getLength() > 0) {
                String url = mediaFileNodes.item(0).getTextContent();
                if (url != null && !url.trim().isEmpty()) {
                    System.out.println("Found MediaFile URL: " + url.trim());
                    return url.trim();
                }
            }

        } catch (Exception e) {
            System.err.println("Error getting MediaFile URL: " + e.getMessage());
        }

        return null;
    }

    /**
     * Print all available tags in the VAST XML for debugging
     * @param doc The parsed XML document
     */
    private static void printAvailableTags(Document doc) {
        try {
            NodeList allElements = doc.getElementsByTagName("*");
            System.out.println("Available tags in VAST:");
            for (int i = 0; i < allElements.getLength(); i++) {
                Element element = (Element) allElements.item(i);
                String tagName = element.getTagName();
                String content = element.getTextContent();
                if (content != null && !content.trim().isEmpty() && content.length() < 100) {
                    System.out.println("  " + tagName + ": " + content.trim());
                } else {
                    System.out.println("  " + tagName + ": [content too long or empty]");
                }
            }
        } catch (Exception e) {
            System.err.println("Error printing available tags: " + e.getMessage());
        }
    }

    /**
     * Parse and display VAST structure information
     * @param vastXml The VAST XML string to analyze
     */
    public static void analyzeVast(String vastXml) {
        System.out.println("=== VAST Analysis ===");

        if (!validateVastStructure(vastXml)) {
            System.err.println("VAST structure validation failed");
            return;
        }

        String clickThroughUrl = getClickThroughUrl(vastXml);
        String videoClickThroughUrl = getVideoClickThroughUrl(vastXml);
        String mediaFileUrl = getMediaFileUrl(vastXml);

        System.out.println("General ClickThrough URL: " + (clickThroughUrl != null ? clickThroughUrl : "NOT FOUND"));
        System.out.println("Video ClickThrough URL: " + (videoClickThroughUrl != null ? videoClickThroughUrl : "NOT FOUND"));
        System.out.println("MediaFile URL: " + (mediaFileUrl != null ? mediaFileUrl : "NOT FOUND"));

        if (videoClickThroughUrl != null) {
            System.out.println("✅ RECOMMENDED: Use Video ClickThrough URL for video ads");
        } else if (clickThroughUrl != null) {
            System.out.println("⚠️  FALLBACK: Use general ClickThrough URL");
        } else {
            System.out.println("❌ ERROR: No click-through URL found");
        }

        System.out.println("=== End Analysis ===");
    }

    public static String getCompanionImageUrl(String vastXml) {
        VastCompanion companion = getBestCompanion(vastXml);
        return companion != null ? companion.getStaticImageUrl() : null;
    }

    /**
     * Best Companion for end card. Priority across resources: HTML → IFrame → Static.
     */
    public static VastCompanion getBestCompanion(String vastXml) {
        if (vastXml == null || vastXml.trim().isEmpty()) {
            return null;
        }
        try {
            Document doc = parseDocument(vastXml);
            return selectBestCompanion(doc.getElementsByTagName("Companion"));
        } catch (Exception e) {
            System.err.println("Error parsing Companion: " + e.getMessage());
            return null;
        }
    }

    /**
     * Companion preview for end card (HTML / IFrame / Static).
     */
    public static VastPreview getCompanionPreview(String vastXml) {
        return VastPreview.fromCompanion(getBestCompanion(vastXml));
    }

    /**
     * Parses the best Companion under an {@code Ad} element.
     */
    public static VastCompanion getBestCompanionFromAd(Element ad) {
        if (ad == null) {
            return null;
        }
        return selectBestCompanion(ad.getElementsByTagName("Companion"));
    }

    /**
     * Returns companion/end-card click-through URL, falling back to video click-through.
     */
    public static String getCompanionClickThroughUrl(String vastXml) {
        VastCompanion companion = getBestCompanion(vastXml);
        if (companion != null
                && companion.getClickThroughUrl() != null
                && !companion.getClickThroughUrl().trim().isEmpty()) {
            return companion.getClickThroughUrl().trim();
        }
        return getClickThroughUrl(vastXml);
    }

    private static VastCompanion selectBestCompanion(NodeList companionList) {
        if (companionList == null || companionList.getLength() == 0) {
            return null;
        }
        VastCompanion bestHtml = null;
        VastCompanion bestIframe = null;
        VastCompanion bestStatic = null;
        for (int i = 0; i < companionList.getLength(); i++) {
            if (!(companionList.item(i) instanceof Element)) {
                continue;
            }
            VastCompanion parsed = parseCompanionElement((Element) companionList.item(i));
            if (parsed == null || !parsed.isRenderable()) {
                continue;
            }
            switch (parsed.getResourceType()) {
                case HTML:
                    if (bestHtml == null) {
                        bestHtml = parsed;
                    }
                    break;
                case IFRAME:
                    if (bestIframe == null) {
                        bestIframe = parsed;
                    }
                    break;
                case STATIC:
                    if (bestStatic == null) {
                        bestStatic = parsed;
                    }
                    break;
                default:
                    break;
            }
        }
        if (bestHtml != null) {
            return bestHtml;
        }
        if (bestIframe != null) {
            return bestIframe;
        }
        return bestStatic;
    }

    /**
     * Parses one Companion. Within a single Companion that has multiple resource types,
     * prefers HTML → IFrame → Static.
     */
    static VastCompanion parseCompanionElement(Element companion) {
        if (companion == null) {
            return null;
        }
        int width = parseIntAttr(companion, "width");
        int height = parseIntAttr(companion, "height");
        String clickThrough = firstTextInTag(companion, "CompanionClickThrough");
        List<String> clickTracking = collectTagUrls(companion, "CompanionClickTracking");
        List<String> viewTracking = collectCompanionViewTrackers(companion);

        String html = firstTextInTag(companion, "HTMLResource");
        if (html != null) {
            return new VastCompanion(
                    VastCompanion.ResourceType.HTML, html, clickThrough, clickTracking, viewTracking, width, height);
        }
        String iframe = firstTextInTag(companion, "IFrameResource");
        if (iframe != null) {
            return new VastCompanion(
                    VastCompanion.ResourceType.IFRAME, iframe, clickThrough, clickTracking, viewTracking, width, height);
        }
        String staticRes = firstTextInTag(companion, "StaticResource");
        if (staticRes != null) {
            return new VastCompanion(
                    VastCompanion.ResourceType.STATIC, staticRes, clickThrough, clickTracking, viewTracking, width, height);
        }
        return null;
    }

    private static List<String> collectTagUrls(Element root, String tag) {
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

    private static List<String> collectCompanionViewTrackers(Element companion) {
        List<String> urls = new ArrayList<>();
        NodeList tracking = companion.getElementsByTagName("Tracking");
        for (int i = 0; i < tracking.getLength(); i++) {
            if (!(tracking.item(i) instanceof Element)) {
                continue;
            }
            Element el = (Element) tracking.item(i);
            String event = el.getAttribute("event");
            if (event == null) {
                continue;
            }
            String normalized = event.trim().toLowerCase();
            if (!"creativeview".equals(normalized) && !"companionview".equals(normalized)) {
                continue;
            }
            String url = el.getTextContent();
            if (url != null && !url.trim().isEmpty()) {
                urls.add(url.trim());
            }
        }
        return urls;
    }

    private static int parseIntAttr(Element element, String name) {
        try {
            String value = element.getAttribute(name);
            if (value == null || value.trim().isEmpty()) {
                return 0;
            }
            return Integer.parseInt(value.trim());
        } catch (Exception ignored) {
            return 0;
        }
    }

    /**
     * Parses VAST {@code skipoffset} on the first {@code Linear} element.
     *
     * @return skip delay in seconds, or {@code 0} when the ad is not skippable
     */
    public static int getSkipOffsetSeconds(String vastXml) {
        if (vastXml == null || vastXml.trim().isEmpty()) {
            return 0;
        }
        try {
            Document doc = parseDocument(vastXml);
            NodeList linearNodes = doc.getElementsByTagName("Linear");
            if (linearNodes.getLength() == 0) {
                return 0;
            }
            Element linear = (Element) linearNodes.item(0);
            String skipOffset = linear.getAttribute("skipoffset");
            if (skipOffset == null || skipOffset.trim().isEmpty()) {
                return 0;
            }
            return parseVastDurationToSeconds(skipOffset.trim());
        } catch (Exception e) {
            System.err.println("Error parsing skipoffset: " + e.getMessage());
            return 0;
        }
    }

    /**
     * Parses a VAST duration or skipoffset value (HH:MM:SS, MM:SS, or seconds) to whole seconds.
     */
    public static int getSkipOffsetSecondsFromValue(String value) {
        if (value == null || value.trim().isEmpty()) {
            return 0;
        }
        return parseVastDurationToSeconds(value.trim());
    }

    private static int parseVastDurationToSeconds(String value) {
        if (value.contains(":")) {
            String[] parts = value.split(":");
            try {
                if (parts.length == 3) {
                    int hours = Integer.parseInt(parts[0]);
                    int minutes = Integer.parseInt(parts[1]);
                    int seconds = (int) Math.floor(Double.parseDouble(parts[2]));
                    return hours * 3600 + minutes * 60 + seconds;
                }
                if (parts.length == 2) {
                    int minutes = Integer.parseInt(parts[0]);
                    int seconds = (int) Math.floor(Double.parseDouble(parts[1]));
                    return minutes * 60 + seconds;
                }
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }
        try {
            return (int) Math.floor(Double.parseDouble(value));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    /**
     * Returns true when the VAST response contains more than one {@code Ad} with an inline {@code MediaFile}.
     * Used for short-form ad pods (VAST 3.0+ {@code sequence} on {@code Ad} elements).
     */
    public static boolean isAdPod(String vastXml) {
        return VideoPlaybackPlanBuilder.build(vastXml).getType() == VideoPlaybackPlanType.POD;
    }

    /**
     * Parses a VAST ad pod: multiple top-level {@code Ad} elements, each with its own inline MP4.
     * Ads are sorted by {@code sequence} attribute (default 1 when missing).
     */
    public static List<VastAdPodItem> parseAdPod(String vastXml) {
        if (vastXml == null || vastXml.trim().isEmpty()) {
            return Collections.emptyList();
        }
        List<VastAdPodItem> items = new ArrayList<>();
        try {
            Document doc = parseDocument(vastXml);
            NodeList adNodes = doc.getElementsByTagName("Ad");
            for (int i = 0; i < adNodes.getLength(); i++) {
                Element ad = (Element) adNodes.item(i);
                String mediaUrl = firstMediaFileUrlInElement(ad);
                if (mediaUrl == null || mediaUrl.isEmpty()) {
                    continue;
                }
                int sequence = parseSequenceAttribute(ad.getAttribute("sequence"), items.size() + 1);
                String adId = ad.getAttribute("id");
                String clickUrl = firstClickThroughInElement(ad);
                String title = firstTextInTag(ad, "AdTitle");
                int skipOffset = skipOffsetInElement(ad);
                items.add(new VastAdPodItem(sequence, adId, mediaUrl, clickUrl, title, skipOffset));
            }
            items.sort(Comparator.comparingInt(VastAdPodItem::getSequence));
        } catch (Exception e) {
            System.err.println("Error parsing VAST ad pod: " + e.getMessage());
        }
        return items;
    }

    /**
     * Detects Bidscube playable extension (video → interactive mini-game → end card).
     */
    public static boolean hasPlayableExtension(String vastXml) {
        return parsePlayableConfig(vastXml) != null;
    }

    /**
     * Reads {@code Extension type="bidscubePlayable"} / {@code BidscubePlayable} from VAST.
     */
    public static PlayableAdConfig parsePlayableConfig(String vastXml) {
        if (vastXml == null || vastXml.trim().isEmpty()) {
            return null;
        }
        try {
            Document doc = parseDocument(vastXml);
            NodeList extensions = doc.getElementsByTagName("Extension");
            for (int i = 0; i < extensions.getLength(); i++) {
                Element extension = (Element) extensions.item(i);
                if (!"bidscubePlayable".equalsIgnoreCase(extension.getAttribute("type"))) {
                    continue;
                }
                NodeList playableNodes = extension.getElementsByTagName("BidscubePlayable");
                Element playable = playableNodes.getLength() > 0
                        ? (Element) playableNodes.item(0)
                        : extension;

                String playableUrl = firstNonEmptyAttribute(playable, "playableUrl", "url");
                String playableHtml = null;
                if (playableUrl == null || playableUrl.isEmpty()) {
                    playableUrl = resolvePlayableFromCompanion(doc);
                }
                if (playableUrl == null || playableUrl.isEmpty()) {
                    NodeList htmlNodes = playable.getElementsByTagName("HTMLResource");
                    if (htmlNodes.getLength() > 0) {
                        String html = htmlNodes.item(0).getTextContent();
                        if (html != null && !html.trim().isEmpty()) {
                            String trimmed = html.trim();
                            if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
                                playableUrl = trimmed;
                            } else {
                                playableHtml = trimmed;
                            }
                        }
                    }
                }
                if ((playableUrl == null || playableUrl.isEmpty())
                        && (playableHtml == null || playableHtml.isEmpty())) {
                    playableUrl = PlayableAdConfig.DEFAULT_PLAYABLE_ASSET;
                }

                int goal = parseIntAttribute(playable, "goalCollectibles", PlayableAdConfig.DEFAULT_GOAL);
                int maxSec = parseIntAttribute(playable, "maxSeconds", PlayableAdConfig.DEFAULT_MAX_SECONDS);
                int skipAfter = parseIntAttribute(playable, "skipAfterSeconds", PlayableAdConfig.DEFAULT_SKIP_AFTER_SECONDS);
                String hint = playable.getAttribute("hint");
                if (hint == null || hint.trim().isEmpty()) {
                    hint = "Drag the hero • collect stars";
                }
                String hero = playable.getAttribute("heroEmoji");
                if (hero == null || hero.trim().isEmpty()) {
                    hero = "🎮";
                }
                return new PlayableAdConfig(
                        playableUrl,
                        playableHtml,
                        goal,
                        maxSec,
                        skipAfter,
                        hint.trim(),
                        hero.trim());
            }
        } catch (Exception e) {
            System.err.println("Error parsing playable extension: " + e.getMessage());
        }
        return null;
    }

    /**
     * Returns companion {@code HTMLResource} URL when used as playable (not inline HTML).
     */
    public static String getPlayableHtmlResourceUrl(String vastXml) {
        PlayableAdConfig config = parsePlayableConfig(vastXml);
        if (config == null) {
            return null;
        }
        if (config.hasRemoteOrAssetUrl()) {
            return config.getPlayableUrl();
        }
        return null;
    }

    private static String resolvePlayableFromCompanion(Document doc) {
        NodeList companionList = doc.getElementsByTagName("Companion");
        for (int i = 0; i < companionList.getLength(); i++) {
            Element companion = (Element) companionList.item(i);
            NodeList htmlNodes = companion.getElementsByTagName("HTMLResource");
            if (htmlNodes.getLength() == 0) {
                continue;
            }
            String html = htmlNodes.item(0).getTextContent();
            if (html == null || html.trim().isEmpty()) {
                continue;
            }
            String trimmed = html.trim();
            if (trimmed.startsWith("http://") || trimmed.startsWith("https://")
                    || trimmed.startsWith("playables/") || trimmed.startsWith("asset://")) {
                return trimmed;
            }
        }
        return null;
    }

    private static String firstNonEmptyAttribute(Element element, String... names) {
        for (String name : names) {
            String value = element.getAttribute(name);
            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
        }
        return null;
    }

    private static Document parseDocument(String vastXml) throws Exception {
        return VastXmlParser.parse(vastXml);
    }

    private static String firstMediaFileUrlInElement(Element root) {
        NodeList mediaFiles = root.getElementsByTagName("MediaFile");
        for (int i = 0; i < mediaFiles.getLength(); i++) {
            String url = mediaFiles.item(i).getTextContent();
            if (url != null && !url.trim().isEmpty()) {
                return url.trim();
            }
        }
        return null;
    }

    private static String firstClickThroughInElement(Element root) {
        NodeList videoClicks = root.getElementsByTagName("VideoClicks");
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
        NodeList clickThrough = root.getElementsByTagName("ClickThrough");
        if (clickThrough.getLength() > 0) {
            String url = clickThrough.item(0).getTextContent();
            if (url != null && !url.trim().isEmpty()) {
                return url.trim();
            }
        }
        return null;
    }

    private static String firstTextInTag(Element root, String tag) {
        NodeList nodes = root.getElementsByTagName(tag);
        if (nodes.getLength() > 0) {
            String text = nodes.item(0).getTextContent();
            if (text != null && !text.trim().isEmpty()) {
                return text.trim();
            }
        }
        return null;
    }

    private static int skipOffsetInElement(Element root) {
        NodeList linearNodes = root.getElementsByTagName("Linear");
        if (linearNodes.getLength() == 0) {
            return 0;
        }
        Element linear = (Element) linearNodes.item(0);
        String skipOffset = linear.getAttribute("skipoffset");
        if (skipOffset == null || skipOffset.trim().isEmpty()) {
            return 0;
        }
        return parseVastDurationToSeconds(skipOffset.trim());
    }

    private static int parseSequenceAttribute(String value, int fallback) {
        if (value == null || value.trim().isEmpty()) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static int parseIntAttribute(Element element, String name, int fallback) {
        String value = element.getAttribute(name);
        if (value == null || value.trim().isEmpty()) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

}
