package com.bidscube.sdk.models.video;

/**
 * Config for post-video gamified end card layer.
 */
public final class GamifiedEndCardConfig {

    private final String title;
    private final String description;
    private final String ctaText;
    private final String clickUrl;
    private final String previewImageUrl;
    private final String rewardLabel;
    private final boolean rewarded;
    private final VastCompanion companion;

    public GamifiedEndCardConfig(
            String title,
            String description,
            String ctaText,
            String clickUrl,
            String previewImageUrl,
            String rewardLabel,
            boolean rewarded) {
        this(title, description, ctaText, clickUrl, previewImageUrl, rewardLabel, rewarded, null);
    }

    public GamifiedEndCardConfig(
            String title,
            String description,
            String ctaText,
            String clickUrl,
            String previewImageUrl,
            String rewardLabel,
            boolean rewarded,
            VastCompanion companion) {
        this.title = title;
        this.description = description;
        this.ctaText = ctaText;
        this.clickUrl = clickUrl;
        this.previewImageUrl = previewImageUrl;
        this.rewardLabel = rewardLabel;
        this.rewarded = rewarded;
        this.companion = companion;
    }

    public static GamifiedEndCardConfig fromPreview(VastPreview preview, String ctaText, String fallbackClickUrl) {
        VastCompanion companion = preview != null ? preview.getCompanion() : null;
        String image = preview != null ? preview.getImageUrl() : null;
        if (image == null && companion != null) {
            image = companion.getStaticImageUrl();
        }
        String click = preview != null && preview.getClickUrl() != null && !preview.getClickUrl().isEmpty()
                ? preview.getClickUrl()
                : fallbackClickUrl;
        if ((click == null || click.isEmpty()) && companion != null) {
            click = companion.getClickThroughUrl();
        }
        if (click == null || click.isEmpty()) {
            click = fallbackClickUrl;
        }
        return new GamifiedEndCardConfig(
                null,
                null,
                ctaText != null ? ctaText : "Install Now",
                click,
                image,
                null,
                false,
                companion);
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public String getCtaText() {
        return ctaText;
    }

    public String getClickUrl() {
        return clickUrl;
    }

    public String getPreviewImageUrl() {
        return previewImageUrl;
    }

    public String getRewardLabel() {
        return rewardLabel;
    }

    public boolean isRewarded() {
        return rewarded;
    }

    public VastCompanion getCompanion() {
        return companion;
    }

    public boolean hasPreviewImage() {
        return previewImageUrl != null && !previewImageUrl.trim().isEmpty();
    }

    /** True when Static / HTML / IFrame companion can be shown. */
    public boolean hasRenderableCompanion() {
        if (companion != null && companion.isRenderable()) {
            return true;
        }
        return hasPreviewImage();
    }
}
