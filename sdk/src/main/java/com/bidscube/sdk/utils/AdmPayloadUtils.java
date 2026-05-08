package com.bidscube.sdk.utils;

import androidx.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Normalizes ad markup payloads: unwraps JSON envelopes {@code {"adm":"..."}}, decodes literal
 * backslash-u plus four hex digit sequences when the server double-encodes, and extracts embedded
 * {@code {"adm":"..."}} blobs sometimes shipped inside HTML creatives.
 */
public final class AdmPayloadUtils {

    private static final Pattern UNICODE_ESCAPE = Pattern.compile("\\\\u([0-9a-fA-F]{4})");

    /**
     * Text nodes like {@code { "adm": "<div..."}} between tags (not at EOF). {@link #stripTrailingJsonCloseAfterHtml}
     * only trims the string tail, so these would still render as visible {@code "} {@code }} under the creative.
     */
    private static final Pattern INTER_TAG_OPEN_ADM_JSON = Pattern.compile(
            "(?<=>)(?:\\s|\u00a0)*\\{\\s*\"(?i:adm)\"\\s*:\\s*[\"\u201c\u201d\u201e]\\s*(?=<)");

    private static final Pattern INTER_TAG_CLOSE_QUOTE_BRACES = Pattern.compile(
            "(?<=>)(?:\\s|\u00a0)*[\"\u201c\u201d\u201e](?:\\s|\u00a0)*(?:\\}(?:\\s|\u00a0)*)+(?=<)");

    private AdmPayloadUtils() {
    }

    /**
     * If the whole body looks like {@code {"adm":"...","position":...}}, unwrap the {@code adm}
     * string (repeat for nested wrappers).
     */
    @Nullable
    public static String unwrapJsonAdmEnvelope(@Nullable String adm) {
        if (adm == null) return null;
        String s = adm.trim();
        for (int i = 0; i < 5; i++) {
            if (!s.startsWith("{")) break;
            try {
                JSONObject o = new JSONObject(s);
                String inner = o.optString("adm", "");
                if (inner.isEmpty() || inner.equals(s)) {
                    break;
                }
                s = inner;
            } catch (JSONException e) {
                String lenient = tryLenientExtractAdmString(s);
                if (lenient != null && !lenient.isEmpty() && !lenient.equals(s)) {
                    s = lenient;
                    continue;
                }
                break;
            }
        }
        String out = stripLooseJsonAdmPrefixToHtml(s);
        out = stripInterTagJsonTextJunk(out);
        return stripTrailingJsonCloseAfterHtml(out);
    }

    /**
     * Removes JSON envelope fragments that appear as raw text between HTML tags (common when a
     * {@code <span>} wraps {@code { "adm": "<markup...>" }}) so WebView does not paint {@code "} {@code }}.
     */
    @Nullable
    public static String stripInterTagJsonTextJunk(@Nullable String s) {
        if (s == null || s.isEmpty() || !s.contains("<")) {
            return s;
        }
        String t = s;
        for (int guard = 0; guard < 48; guard++) {
            String u = INTER_TAG_OPEN_ADM_JSON.matcher(t).replaceAll("");
            u = INTER_TAG_CLOSE_QUOTE_BRACES.matcher(u).replaceAll("");
            if (u.equals(t)) {
                break;
            }
            t = u;
        }
        return t;
    }

    /**
     * Strips trailing {@code "} {@code } from broken JSON wrappers left after HTML, e.g.
     * {@code ...</script>"}} so WebView does not show literal {@code "}}.
     */
    @Nullable
    public static String stripTrailingJsonCloseAfterHtml(@Nullable String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        if (t.isEmpty() || !t.contains("<")) {
            return t;
        }
        for (int guard = 0; guard < 24; guard++) {
            String u = t.replaceAll("\"\\s*\\}\\s*$", "").trim();
            u = u.replaceAll("\\\\\"\\s*\\}\\s*$", "").trim();
            if (!u.equals(t)) {
                t = u;
                continue;
            }
            int lastGt = t.lastIndexOf('>');
            if (lastGt < 0 || lastGt >= t.length() - 1) {
                break;
            }
            String tail = t.substring(lastGt + 1).trim();
            if (tail.isEmpty()) {
                break;
            }
            boolean onlyJsonJunk = true;
            for (int i = 0; i < tail.length(); i++) {
                char c = tail.charAt(i);
                if (!(c == '}' || c == '"' || Character.isWhitespace(c))) {
                    onlyJsonJunk = false;
                    break;
                }
            }
            if (onlyJsonJunk) {
                t = t.substring(0, lastGt + 1).trim();
                continue;
            }
            break;
        }
        return t;
    }

    /**
     * If the payload still begins with loose JSON text containing {@code "adm"} but real markup starts
     * at the first {@code <div>/<a>/<img>}, drop the JSON prefix (invalid/double-encoded envelopes).
     */
    @Nullable
    public static String stripLooseJsonAdmPrefixToHtml(@Nullable String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        if (t.isEmpty()) {
            return s;
        }
        if (t.startsWith("<")) {
            return t;
        }
        int htmlAt = indexOfFirstHtmlMarkup(t);
        if (htmlAt < 0) {
            return s;
        }
        String prefix = t.substring(0, htmlAt);
        String pl = prefix.toLowerCase(Locale.ROOT);
        if (!pl.contains("\"adm\"") && !pl.contains("'adm'")) {
            return s;
        }
        return t.substring(htmlAt).trim();
    }

    private static int indexOfFirstHtmlMarkup(String t) {
        String lower = t.toLowerCase(Locale.ROOT);
        String[] needles = {"<div", "<a ", "<a\t", "<a\n", "<a\r", "<a>", "<img", "<html", "<span", "<iframe", "<ins ", "<ins>"};
        int best = -1;
        for (String n : needles) {
            int i = lower.indexOf(n);
            if (i >= 0 && (best < 0 || i < best)) {
                best = i;
            }
        }
        return best;
    }

    /**
     * When {@link JSONObject} rejects the wrapper (strict escaping, minor syntax issues), peel
     * {@code {"adm":"..."}} so UI never shows a leading {@code {"adm":} fragment.
     */
    @Nullable
    private static String tryLenientExtractAdmString(String s) {
        String t = s.trim();
        if (!t.startsWith("{")) {
            return null;
        }
        int admKey = indexOfAdmKey(t);
        if (admKey < 0) {
            return null;
        }
        int colon = t.indexOf(':', admKey + 5);
        if (colon < 0) {
            return null;
        }
        int p = colon + 1;
        while (p < t.length() && Character.isWhitespace(t.charAt(p))) {
            p++;
        }
        if (p >= t.length() || t.charAt(p) != '"') {
            return null;
        }
        p++;
        StringBuilder sb = new StringBuilder();
        while (p < t.length()) {
            char c = t.charAt(p);
            if (c == '\\' && p + 1 < t.length()) {
                char n = t.charAt(p + 1);
                switch (n) {
                    case '"':
                        sb.append('"');
                        p += 2;
                        continue;
                    case '\\':
                        sb.append('\\');
                        p += 2;
                        continue;
                    case '/':
                        sb.append('/');
                        p += 2;
                        continue;
                    case 'b':
                        sb.append('\b');
                        p += 2;
                        continue;
                    case 'f':
                        sb.append('\f');
                        p += 2;
                        continue;
                    case 'n':
                        sb.append('\n');
                        p += 2;
                        continue;
                    case 'r':
                        sb.append('\r');
                        p += 2;
                        continue;
                    case 't':
                        sb.append('\t');
                        p += 2;
                        continue;
                    case 'u':
                        if (p + 5 < t.length()) {
                            try {
                                String hex = t.substring(p + 2, p + 6);
                                sb.append((char) Integer.parseInt(hex, 16));
                                p += 6;
                                continue;
                            } catch (NumberFormatException ignored) {
                                return null;
                            }
                        }
                        return null;
                    default:
                        return null;
                }
            }
            if (c == '"') {
                return sb.toString();
            }
            sb.append(c);
            p++;
        }
        return null;
    }

    /**
     * Decodes literal {@code \u003c}-style sequences when they appear as six characters in the
     * string (e.g. server did not JSON-decode, or markup was concatenated as text).
     */
    @Nullable
    public static String decodeLiteralUnicodeEscapes(@Nullable String s) {
        if (s == null || !s.contains("\\u")) {
            return s;
        }
        Matcher m = UNICODE_ESCAPE.matcher(s);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            int code = Integer.parseInt(m.group(1), 16);
            m.appendReplacement(sb, Matcher.quoteReplacement(String.valueOf((char) code)));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /**
     * Some creatives embed a small JSON object in the HTML, e.g.
     * {@code <span>{\n  "adm": "<div>...</div>"\n}</span>}. Extract {@code adm} and splice it into
     * the surrounding markup so the WebView renders HTML instead of raw JSON text.
     */
    @Nullable
    public static String extractEmbeddedAdmJsonFromCreative(@Nullable String html) {
        if (html == null || !html.contains("\"adm\"")) {
            return html;
        }
        int admKey = indexOfAdmKey(html);
        if (admKey < 0) {
            return html;
        }
        int start = html.lastIndexOf('{', admKey);
        if (start < 0) {
            return html;
        }
        int end = findMatchingBrace(html, start);
        if (end < 0 || end <= start) {
            return html;
        }
        String jsonStr = html.substring(start, end + 1);
        try {
            JSONObject o = new JSONObject(jsonStr);
            String inner = o.optString("adm", "");
            if (inner.isEmpty()) {
                return html;
            }
            return stripInterTagJsonTextJunk(html.substring(0, start) + inner + html.substring(end + 1));
        } catch (JSONException e) {
            String merged = stripLooseJsonAdmPrefixToHtml(jsonStr);
            if (merged != null && merged.trim().startsWith("<")) {
                return stripInterTagJsonTextJunk(html.substring(0, start) + merged + html.substring(end + 1));
            }
            return html;
        }
    }

    /** Prefer a real JSON {@code "adm"} key (not substring inside a longer token). */
    private static int indexOfAdmKey(String html) {
        int i = 0;
        while (true) {
            int idx = html.indexOf("\"adm\"", i);
            if (idx < 0) return -1;
            if (idx > 0) {
                char before = html.charAt(idx - 1);
                if (before == '\\' || Character.isLetterOrDigit(before)) {
                    i = idx + 1;
                    continue;
                }
            }
            return idx;
        }
    }

    private static int findMatchingBrace(String s, int openIdx) {
        if (openIdx < 0 || openIdx >= s.length() || s.charAt(openIdx) != '{') {
            return -1;
        }
        int depth = 0;
        boolean inString = false;
        char stringQuote = 0;
        boolean escape = false;
        for (int i = openIdx; i < s.length(); i++) {
            char c = s.charAt(i);
            if (inString) {
                if (escape) {
                    escape = false;
                } else if (c == '\\') {
                    escape = true;
                } else if (c == stringQuote) {
                    inString = false;
                    stringQuote = 0;
                }
                continue;
            }
            if (c == '"') {
                inString = true;
                stringQuote = '"';
                continue;
            }
            if (c == '{') depth++;
            else if (c == '}') {
                depth--;
                if (depth == 0) return i;
            }
        }
        return -1;
    }
}
