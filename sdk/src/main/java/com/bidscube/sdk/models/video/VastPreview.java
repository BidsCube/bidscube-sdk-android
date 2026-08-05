package com.bidscube.sdk.models.video;

/**
 * Companion / preview for end card. Prefer {@link #fromCompanion(VastCompanion)} for full
 * HTML / IFrame / Static support.
 */
public final class VastPreview {

    private final String imageUrl;
    private final String clickUrl;
    private final VastCompanion companion;

    public VastPreview(String imageUrl, String clickUrl) {
        this.imageUrl = imageUrl;
        this.clickUrl = clickUrl;
        this.companion = null;
    }

    public VastPreview(VastCompanion companion) {
        this.companion = companion;
        this.imageUrl = companion != null ? companion.getStaticImageUrl() : null;
        this.clickUrl = companion != null ? companion.getClickThroughUrl() : null;
    }

    public static VastPreview fromCompanion(VastCompanion companion) {
        if (companion == null || !companion.isRenderable()) {
            return null;
        }
        return new VastPreview(companion);
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public String getClickUrl() {
        return clickUrl;
    }

    public VastCompanion getCompanion() {
        return companion;
    }

    /** True when a static image URL is available. */
    public boolean hasImage() {
        return imageUrl != null && !imageUrl.trim().isEmpty();
    }

    /** True when any companion resource (HTML / IFrame / Static) can be shown. */
    public boolean hasRenderableCompanion() {
        if (companion != null) {
            return companion.isRenderable();
        }
        return hasImage();
    }
}
