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

    public GamifiedEndCardConfig(
            String title,
            String description,
            String ctaText,
            String clickUrl,
            String previewImageUrl,
            String rewardLabel,
            boolean rewarded) {
        this.title = title;
        this.description = description;
        this.ctaText = ctaText;
        this.clickUrl = clickUrl;
        this.previewImageUrl = previewImageUrl;
        this.rewardLabel = rewardLabel;
        this.rewarded = rewarded;
    }

    public static GamifiedEndCardConfig fromPreview(VastPreview preview, String ctaText, String fallbackClickUrl) {
        String image = preview != null ? preview.getImageUrl() : null;
        String click = preview != null && preview.getClickUrl() != null && !preview.getClickUrl().isEmpty()
                ? preview.getClickUrl()
                : fallbackClickUrl;
        return new GamifiedEndCardConfig(
                null,
                null,
                ctaText != null ? ctaText : "Install Now",
                click,
                image,
                null,
                false);
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

    public boolean hasPreviewImage() {
        return previewImageUrl != null && !previewImageUrl.trim().isEmpty();
    }
}
