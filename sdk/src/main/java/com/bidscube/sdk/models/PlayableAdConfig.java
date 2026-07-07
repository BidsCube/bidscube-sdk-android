package com.bidscube.sdk.models;

/**
 * Gamified / playable phase after intro video (triple-page style: video → playable → end card).
 * Playable content comes from {@code playableUrl} / HTML in VAST (creative-owned), with native demo fallback.
 */
public final class PlayableAdConfig {

    public static final int DEFAULT_GOAL = 5;
    public static final int DEFAULT_MAX_SECONDS = 20;
    public static final int DEFAULT_SKIP_AFTER_SECONDS = 5;
    public static final String DEFAULT_PLAYABLE_ASSET = "playables/hero-runner/index.html";

    private final String playableUrl;
    private final String playableHtml;
    private final int goalCollectibles;
    private final int maxSeconds;
    private final int skipAfterSeconds;
    private final String hintText;
    private final String heroEmoji;

    public PlayableAdConfig(
            String playableUrl,
            String playableHtml,
            int goalCollectibles,
            int maxSeconds,
            int skipAfterSeconds,
            String hintText,
            String heroEmoji) {
        this.playableUrl = playableUrl;
        this.playableHtml = playableHtml;
        this.goalCollectibles = goalCollectibles;
        this.maxSeconds = maxSeconds;
        this.skipAfterSeconds = skipAfterSeconds;
        this.hintText = hintText;
        this.heroEmoji = heroEmoji;
    }

    public String getPlayableUrl() {
        return playableUrl;
    }

    public String getPlayableHtml() {
        return playableHtml;
    }

    public boolean hasWebPlayable() {
        return hasRemoteOrAssetUrl() || hasInlineHtml();
    }

    public boolean hasRemoteOrAssetUrl() {
        return playableUrl != null && !playableUrl.trim().isEmpty();
    }

    public boolean hasInlineHtml() {
        if (playableHtml == null || playableHtml.trim().isEmpty()) {
            return false;
        }
        String trimmed = playableHtml.trim();
        return trimmed.startsWith("<") || trimmed.startsWith("<!");
    }

    public int getGoalCollectibles() {
        return goalCollectibles;
    }

    public int getMaxSeconds() {
        return maxSeconds;
    }

    public int getSkipAfterSeconds() {
        return skipAfterSeconds;
    }

    public String getHintText() {
        return hintText;
    }

    public String getHeroEmoji() {
        return heroEmoji;
    }
}
