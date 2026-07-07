package com.bidscube.sdk.models.video;

/**
 * Companion / preview image for end card.
 */
public final class VastPreview {

    private final String imageUrl;
    private final String clickUrl;

    public VastPreview(String imageUrl, String clickUrl) {
        this.imageUrl = imageUrl;
        this.clickUrl = clickUrl;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public String getClickUrl() {
        return clickUrl;
    }

    public boolean hasImage() {
        return imageUrl != null && !imageUrl.trim().isEmpty();
    }
}
