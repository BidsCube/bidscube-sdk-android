package com.bidscube.sdk.utils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * Parsed VAST {@code Linear/@skipoffset} with kind separated from countdown resolution.
 */
public final class VastSkipOffset {

    public enum Kind {
        /** Attribute missing or empty — BidsCube may apply publisher default timing. */
        ABSENT,
        /** Absolute time (HH:MM:SS / seconds), including {@code 0} = immediately skippable. */
        TIME_SECONDS,
        /** Percentage of creative duration (e.g. {@code 25%}). */
        PERCENT,
        /** Attribute present but not parseable. */
        INVALID
    }

    public final Kind kind;
    /** Seconds when {@link Kind#TIME_SECONDS}; percent 0–100 when {@link Kind#PERCENT}. */
    public final int value;

    private VastSkipOffset(Kind kind, int value) {
        this.kind = kind;
        this.value = value;
    }

    public static VastSkipOffset absent() {
        return new VastSkipOffset(Kind.ABSENT, 0);
    }

    public static VastSkipOffset timeSeconds(int seconds) {
        return new VastSkipOffset(Kind.TIME_SECONDS, Math.max(0, seconds));
    }

    public static VastSkipOffset percent(int percent) {
        return new VastSkipOffset(Kind.PERCENT, Math.max(0, Math.min(100, percent)));
    }

    public static VastSkipOffset invalid() {
        return new VastSkipOffset(Kind.INVALID, 0);
    }

    /**
     * Parses the first {@code Linear/@skipoffset} in a VAST document.
     */
    @NonNull
    public static VastSkipOffset parseFromVast(@Nullable String vastXml) {
        if (vastXml == null || vastXml.trim().isEmpty()) {
            return absent();
        }
        try {
            Document doc = VastXmlParser.parse(vastXml);
            NodeList linearNodes = doc.getElementsByTagName("Linear");
            if (linearNodes.getLength() == 0) {
                return absent();
            }
            Element linear = (Element) linearNodes.item(0);
            if (!linear.hasAttribute("skipoffset")) {
                return absent();
            }
            String raw = linear.getAttribute("skipoffset");
            if (raw == null || raw.trim().isEmpty()) {
                return absent();
            }
            return parseValue(raw.trim());
        } catch (Exception e) {
            return invalid();
        }
    }

    @NonNull
    public static VastSkipOffset parseValue(@NonNull String raw) {
        String value = raw.trim();
        if (value.isEmpty()) {
            return absent();
        }
        if (value.endsWith("%")) {
            try {
                int pct = (int) Math.floor(Double.parseDouble(value.substring(0, value.length() - 1).trim()));
                if (pct < 0) {
                    return invalid();
                }
                return percent(pct);
            } catch (NumberFormatException e) {
                return invalid();
            }
        }
        if (!looksLikeTimeOrNumber(value)) {
            return invalid();
        }
        int seconds = VastParser.getSkipOffsetSecondsFromValue(value);
        return timeSeconds(seconds);
    }

    private static boolean looksLikeTimeOrNumber(String value) {
        if (value.matches("\\d+(\\.\\d+)?")) {
            return true;
        }
        return value.contains(":");
    }

    /**
     * First {@code Duration} element, seconds; {@code 0} if missing.
     */
    public static int parseLinearDurationSeconds(@Nullable String vastXml) {
        if (vastXml == null || vastXml.trim().isEmpty()) {
            return 0;
        }
        try {
            Document doc = VastXmlParser.parse(vastXml);
            NodeList durationNodes = doc.getElementsByTagName("Duration");
            if (durationNodes.getLength() == 0) {
                return 0;
            }
            String text = durationNodes.item(0).getTextContent();
            return VastParser.getSkipOffsetSecondsFromValue(text != null ? text.trim() : "");
        } catch (Exception e) {
            return 0;
        }
    }
}
