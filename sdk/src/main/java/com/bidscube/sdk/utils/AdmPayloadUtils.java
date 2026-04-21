package com.bidscube.sdk.utils;

import androidx.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Normalizes ad markup payloads: unwraps JSON envelopes {@code {"adm":"..."}}, decodes literal
 * backslash-u plus four hex digit sequences when the server double-encodes, and extracts embedded
 * {@code {"adm":"..."}} blobs sometimes shipped inside HTML creatives.
 */
public final class AdmPayloadUtils {

    private static final Pattern UNICODE_ESCAPE = Pattern.compile("\\\\u([0-9a-fA-F]{4})");

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
                break;
            }
        }
        return s;
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
            return html.substring(0, start) + inner + html.substring(end + 1);
        } catch (JSONException e) {
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
