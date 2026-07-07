package com.bidscube.sdk.models;

/**
 * One sequenced ad inside a VAST ad pod (multiple short clips in a single break).
 */
public final class VastAdPodItem {

    private final int sequence;
    private final String adId;
    private final String mediaUrl;
    private final String clickThroughUrl;
    private final String title;
    private final int skipOffsetSeconds;

    public VastAdPodItem(
            int sequence,
            String adId,
            String mediaUrl,
            String clickThroughUrl,
            String title,
            int skipOffsetSeconds) {
        this.sequence = sequence;
        this.adId = adId;
        this.mediaUrl = mediaUrl;
        this.clickThroughUrl = clickThroughUrl;
        this.title = title;
        this.skipOffsetSeconds = skipOffsetSeconds;
    }

    public int getSequence() {
        return sequence;
    }

    public String getAdId() {
        return adId;
    }

    public String getMediaUrl() {
        return mediaUrl;
    }

    public String getClickThroughUrl() {
        return clickThroughUrl;
    }

    public String getTitle() {
        return title;
    }

    public int getSkipOffsetSeconds() {
        return skipOffsetSeconds;
    }
}
