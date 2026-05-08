package com.bidscube.sdk.utils;

import androidx.annotation.Nullable;

import com.bidscube.sdk.models.enums.ImageType;
import com.bidscube.sdk.models.natives.Image;
import com.bidscube.sdk.models.natives.NativeAd;
import com.bidscube.sdk.models.natives.NativeAsset;
import com.bidscube.sdk.models.natives.NativeLink;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses HTML {@code adm} markup into {@link NativeAd}: one main visible image, optional click URL
 * from a wrapping {@code <a href>}, impression pixel URLs (1×1, {@code t=impr}, {@code t=check}, etc.),
 * and optional click-tracking URLs scraped from {@code <script>} blocks (native rendering — no JS execution).
 */
public final class HtmlAdmCreativeParser {

    private static final Pattern IMG_TAG =
            Pattern.compile("<img\\s+[^>]*?>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern SCRIPT_BLOCK =
            Pattern.compile("<script\\b[^>]*>(.*?)</script>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern URL_IN_TEXT =
            Pattern.compile("https?://[^\\s\"'<>\\\\]+", Pattern.CASE_INSENSITIVE);

    private HtmlAdmCreativeParser() {
    }

    public static boolean looksLikeHtmlAdm(@Nullable String adm) {
        if (adm == null || adm.isEmpty()) {
            return false;
        }
        String s = adm.toLowerCase(Locale.ROOT);
        return s.contains("<img") || (s.contains("<a") && s.contains("href"));
    }

    /**
     * @return {@link NativeAd} or {@code null} if no usable main image was found
     */
    @Nullable
    public static NativeAd parseToNativeAd(@Nullable String htmlAdm) {
        if (htmlAdm == null || htmlAdm.trim().isEmpty()) {
            return null;
        }
        String html = htmlAdm;

        List<ImgTag> imgs = findImgTags(html);
        if (imgs.isEmpty()) {
            return null;
        }

        LinkedHashSet<String> impressionTrackers = new LinkedHashSet<>();
        String mainSrc = null;
        int mainImgStart = -1;

        for (ImgTag tag : imgs) {
            String src = tag.src;
            if (src == null || src.trim().isEmpty()) {
                continue;
            }
            src = src.trim();

            if (isMainCreativeCandidate(src, tag.width, tag.height)) {
                mainSrc = src;
                mainImgStart = tag.start;
                break;
            }
        }

        if (mainSrc == null) {
            return null;
        }

        for (ImgTag tag : imgs) {
            String src = tag.src != null ? tag.src.trim() : "";
            if (src.isEmpty() || src.equals(mainSrc)) {
                continue;
            }
            if (isImpressionOrServicePixel(src, tag.width, tag.height)) {
                impressionTrackers.add(src);
            }
        }

        collectImpressionTrackerUrlsFromScripts(html, impressionTrackers);

        String clickUrl = findEnclosingAnchorHref(html, mainImgStart);

        LinkedHashSet<String> clickTrackers = new LinkedHashSet<>();
        collectClickTrackerUrlsFromScripts(html, clickTrackers);
        if (clickUrl != null) {
            clickTrackers.remove(clickUrl);
        }

        NativeAd ad = new NativeAd();
        NativeAsset asset = new NativeAsset();
        asset.id = 1;
        asset.img = new Image();
        asset.img.url = mainSrc;
        asset.img.type = ImageType.MAIN;
        ad.assets.add(asset);

        if (clickUrl != null || !clickTrackers.isEmpty()) {
            NativeLink link = new NativeLink();
            link.url = clickUrl;
            link.clicktrackers.addAll(clickTrackers);
            ad.link = link;
        }

        ad.imptrackers.addAll(impressionTrackers);
        return ad;
    }

    private static final class ImgTag {
        final int start;
        final String fullTag;
        @Nullable final String src;
        final int width;
        final int height;

        ImgTag(int start, String fullTag, @Nullable String src, int width, int height) {
            this.start = start;
            this.fullTag = fullTag;
            this.src = src;
            this.width = width;
            this.height = height;
        }
    }

    private static List<ImgTag> findImgTags(String html) {
        List<ImgTag> out = new ArrayList<>();
        Matcher m = IMG_TAG.matcher(html);
        while (m.find()) {
            String tag = m.group();
            int start = m.start();
            String src = extractAttribute(tag, "src");
            int w = parseDimAttribute(tag, "width");
            int h = parseDimAttribute(tag, "height");
            out.add(new ImgTag(start, tag, src, w, h));
        }
        return out;
    }

    @Nullable
    private static String extractAttribute(String tag, String name) {
        String p = "(?is)\\b" + Pattern.quote(name) + "\\s*=\\s*";
        Matcher mq = Pattern.compile(p + "\"([^\"]*)\"", Pattern.DOTALL).matcher(tag);
        if (mq.find()) {
            return mq.group(1).trim();
        }
        Matcher ms = Pattern.compile(p + "'([^']*)'", Pattern.DOTALL).matcher(tag);
        if (ms.find()) {
            return ms.group(1).trim();
        }
        Matcher mu = Pattern.compile(p + "([^\\s>]+)", Pattern.DOTALL).matcher(tag);
        if (mu.find()) {
            return mu.group(1).trim();
        }
        return null;
    }

    /** {@code -1} if missing or not a positive integer */
    private static int parseDimAttribute(String tag, String name) {
        String v = extractAttribute(tag, name);
        if (v == null || v.isEmpty()) {
            return -1;
        }
        try {
            int n = Integer.parseInt(v.replaceAll("[^0-9-]", ""));
            return n > 0 ? n : -1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    static boolean isTrackingPixelDimensions(int width, int height) {
        return width == 1 || height == 1;
    }

    static boolean isTrackingPixelUrl(@Nullable String src) {
        if (src == null) {
            return false;
        }
        String s = src.toLowerCase(Locale.ROOT);
        return s.contains("t=impr") || s.contains("t=check");
    }

    static boolean hasLikelyImageFileExtension(@Nullable String src) {
        if (src == null) {
            return false;
        }
        int q = src.indexOf('?');
        String path = q >= 0 ? src.substring(0, q) : src;
        int semi = path.indexOf(';');
        if (semi >= 0) {
            path = path.substring(0, semi);
        }
        return path.toLowerCase(Locale.ROOT).matches(".*\\.(jpe?g|png|gif|webp)$");
    }

    static boolean isImpressionOrServicePixel(@Nullable String src, int width, int height) {
        if (src == null) {
            return false;
        }
        if (isTrackingPixelUrl(src)) {
            return true;
        }
        return isTrackingPixelDimensions(width, height);
    }

    /**
     * Main creative: real image URL, not a tracker URL/dimensions.
     */
    static boolean isMainCreativeCandidate(@Nullable String src, int width, int height) {
        if (src == null || src.trim().isEmpty()) {
            return false;
        }
        if (!hasLikelyImageFileExtension(src)) {
            return false;
        }
        if (isTrackingPixelUrl(src)) {
            return false;
        }
        if (isTrackingPixelDimensions(width, height)) {
            return false;
        }
        return true;
    }

    @Nullable
    private static String findEnclosingAnchorHref(String html, int imgTagStart) {
        int searchEnd = imgTagStart;
        while (searchEnd > 0) {
            int aOpen = html.lastIndexOf("<a", searchEnd - 1);
            if (aOpen < 0) {
                return null;
            }
            int aTagEnd = html.indexOf('>', aOpen);
            if (aTagEnd < 0 || aTagEnd >= imgTagStart) {
                searchEnd = aOpen;
                continue;
            }
            int innerClose = html.indexOf("</a>", aOpen);
            if (innerClose >= 0 && innerClose < imgTagStart) {
                searchEnd = aOpen;
                continue;
            }
            String openTag = html.substring(aOpen, aTagEnd + 1);
            return extractHrefFromAnchorOpenTag(openTag);
        }
        return null;
    }

    @Nullable
    private static String extractHrefFromAnchorOpenTag(String anchorOpenTag) {
        Matcher m = Pattern.compile(
                        "(?is)\\bhref\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)'|([^\\s>]+))")
                .matcher(anchorOpenTag);
        if (!m.find()) {
            return null;
        }
        String href = m.group(1);
        if (href == null) {
            href = m.group(2);
        }
        if (href == null) {
            href = m.group(3);
        }
        if (href == null) {
            return null;
        }
        href = href.trim();
        return href.isEmpty() ? null : href;
    }

    private static void collectImpressionTrackerUrlsFromScripts(String html, Set<String> out) {
        Matcher sm = SCRIPT_BLOCK.matcher(html);
        while (sm.find()) {
            String body = sm.group(1);
            if (body == null) {
                continue;
            }
            Matcher um = URL_IN_TEXT.matcher(body);
            while (um.find()) {
                String url = um.group();
                String lower = url.toLowerCase(Locale.ROOT);
                if (lower.contains("t=impr") || lower.contains("t=check")) {
                    out.add(url);
                }
            }
        }
    }

    private static void collectClickTrackerUrlsFromScripts(String html, Set<String> out) {
        Matcher sm = SCRIPT_BLOCK.matcher(html);
        while (sm.find()) {
            String body = sm.group(1);
            if (body == null) {
                continue;
            }
            Matcher um = URL_IN_TEXT.matcher(body);
            while (um.find()) {
                String url = um.group();
                if (isLikelyClickTrackerUrl(url)) {
                    out.add(url);
                }
            }
        }
    }

    static boolean isLikelyClickTrackerUrl(String url) {
        String s = url.toLowerCase(Locale.ROOT);
        if (s.contains("t=click") || s.contains("evt=click") || s.contains("event=click")) {
            return true;
        }
        if (s.contains("clicktrack") || s.contains("click_track")) {
            return true;
        }
        return s.contains("/clk") || s.contains("clk=");
    }
}
