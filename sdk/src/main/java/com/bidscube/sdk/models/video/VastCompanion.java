package com.bidscube.sdk.models.video;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Parsed VAST Companion creative used for post-roll end cards.
 * Priority when multiple resource types exist: HTML → IFrame → Static image.
 */
public final class VastCompanion {

    public enum ResourceType {
        HTML,
        IFRAME,
        STATIC
    }

    private final ResourceType resourceType;
    /** Image/IFrame URL, or HTML markup / HTML resource URL. */
    private final String content;
    private final String clickThroughUrl;
    private final List<String> clickTrackingUrls;
    private final List<String> viewTrackingUrls;
    private final int width;
    private final int height;

    public VastCompanion(
            ResourceType resourceType,
            String content,
            String clickThroughUrl,
            List<String> clickTrackingUrls,
            List<String> viewTrackingUrls,
            int width,
            int height) {
        this.resourceType = resourceType;
        this.content = content;
        this.clickThroughUrl = clickThroughUrl;
        this.clickTrackingUrls = clickTrackingUrls != null
                ? Collections.unmodifiableList(new ArrayList<>(clickTrackingUrls))
                : Collections.emptyList();
        this.viewTrackingUrls = viewTrackingUrls != null
                ? Collections.unmodifiableList(new ArrayList<>(viewTrackingUrls))
                : Collections.emptyList();
        this.width = width;
        this.height = height;
    }

    public ResourceType getResourceType() {
        return resourceType;
    }

    public String getContent() {
        return content;
    }

    public String getClickThroughUrl() {
        return clickThroughUrl;
    }

    public List<String> getClickTrackingUrls() {
        return clickTrackingUrls;
    }

    public List<String> getViewTrackingUrls() {
        return viewTrackingUrls;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public boolean isRenderable() {
        return resourceType != null && content != null && !content.trim().isEmpty();
    }

    public boolean isStaticImage() {
        return resourceType == ResourceType.STATIC && isRenderable();
    }

    public boolean isHtml() {
        return resourceType == ResourceType.HTML && isRenderable();
    }

    public boolean isIframe() {
        return resourceType == ResourceType.IFRAME && isRenderable();
    }

    /** Static image URL when type is STATIC; otherwise null. */
    public String getStaticImageUrl() {
        return isStaticImage() ? content.trim() : null;
    }
}
