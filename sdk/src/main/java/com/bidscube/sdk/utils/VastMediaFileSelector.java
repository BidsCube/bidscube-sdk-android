package com.bidscube.sdk.utils;

import com.bidscube.sdk.models.video.VastMediaFile;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Selects one best {@link VastMediaFile} per Linear creative (not all renditions).
 */
public final class VastMediaFileSelector {

    private VastMediaFileSelector() {
    }

    public static VastMediaFile selectBest(List<VastMediaFile> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return null;
        }
        List<VastMediaFile> playable = new ArrayList<>();
        for (VastMediaFile file : candidates) {
            if (file == null || file.getUrl() == null || file.getUrl().trim().isEmpty()) {
                continue;
            }
            if (isUnsupportedMime(file.getMimeType())) {
                continue;
            }
            String mime = file.getMimeType() != null ? file.getMimeType().toLowerCase(Locale.US) : "";
            if (mime.isEmpty() || mime.contains("mp4") || mime.contains("webm") || mime.startsWith("video/")) {
                playable.add(file);
            }
        }
        if (playable.isEmpty()) {
            return null;
        }
        playable.sort((a, b) -> {
            int cmp = Integer.compare(mimeScore(b), mimeScore(a));
            if (cmp != 0) {
                return cmp;
            }
            cmp = Integer.compare(deliveryScore(b), deliveryScore(a));
            if (cmp != 0) {
                return cmp;
            }
            cmp = Integer.compare(resolutionScore(b), resolutionScore(a));
            if (cmp != 0) {
                return cmp;
            }
            int brA = a.getBitrate() != null ? a.getBitrate() : 0;
            int brB = b.getBitrate() != null ? b.getBitrate() : 0;
            return Integer.compare(brB, brA);
        });
        VastMediaFile selected = playable.get(0);
        SDKLogger.d("VastMediaFileSelector", "Selected media file: " + selected.getUrl()
                + " mime=" + selected.getMimeType() + " delivery=" + selected.getDelivery());
        return selected;
    }

    private static int deliveryScore(VastMediaFile file) {
        String delivery = file.getDelivery();
        if (delivery == null) {
            return 1;
        }
        return delivery.toLowerCase(Locale.US).contains("progressive") ? 2 : 1;
    }

    private static int mimeScore(VastMediaFile file) {
        String mime = file.getMimeType() != null ? file.getMimeType().toLowerCase(Locale.US) : "";
        if (mime.contains("mp4")) {
            return 3;
        }
        if (mime.contains("webm")) {
            return 2;
        }
        return 1;
    }

    private static int resolutionScore(VastMediaFile file) {
        if (file.getWidth() == null || file.getHeight() == null) {
            return 500;
        }
        int pixels = file.getWidth() * file.getHeight();
        if (pixels > 1920 * 1080) {
            return 400;
        }
        if (pixels > 1280 * 720) {
            return 700;
        }
        if (pixels >= 640 * 360) {
            return 900;
        }
        return 300;
    }

    /** Rejects VPAID, JS, and other non-progressive video types this SDK does not execute. */
    public static boolean isUnsupportedMime(String mimeType) {
        if (mimeType == null || mimeType.trim().isEmpty()) {
            return false;
        }
        String mime = mimeType.toLowerCase(Locale.US);
        return mime.contains("javascript")
                || mime.contains("vpaid")
                || mime.contains("flash")
                || mime.contains("x-shockwave");
    }
}
