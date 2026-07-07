package com.bidscube.sdk.models.video;

import com.bidscube.sdk.openrtb.OpenRtbVideoPodContext;
import com.bidscube.sdk.openrtb.PodOrderingSource;
import com.bidscube.sdk.openrtb.PodType;

/**
 * One playable unit in a single-video or podded playback plan.
 */
public final class VideoAdSlot {

    private final String adId;
    private final int playbackIndex;
    private final Integer vastSequence;
    private final Integer openRtbSlotInPod;
    private final String openRtbImpId;
    private final String openRtbPodId;
    private final Integer openRtbRequiredDurationSec;
    private final VastMediaFile mediaFile;
    private final Long durationMs;
    private final Long skipOffsetMs;
    private final String clickThroughUrl;
    private final VastTrackingEvents trackingEvents;
    private final VastPreview preview;
    private final String title;
    private final PodOrderingSource orderingSource;

    /** Legacy constructor (VAST-only). */
    public VideoAdSlot(
            String adId,
            int sequence,
            VastMediaFile mediaFile,
            Long durationMs,
            Long skipOffsetMs,
            String clickThroughUrl,
            VastTrackingEvents trackingEvents,
            VastPreview preview,
            String title) {
        this(
                adId,
                sequence,
                sequence,
                null,
                null,
                null,
                null,
                mediaFile,
                durationMs,
                skipOffsetMs,
                clickThroughUrl,
                trackingEvents,
                preview,
                title,
                PodOrderingSource.VAST_SEQUENCE);
    }

    public VideoAdSlot(
            String adId,
            int playbackIndex,
            Integer vastSequence,
            Integer openRtbSlotInPod,
            String openRtbImpId,
            String openRtbPodId,
            Integer openRtbRequiredDurationSec,
            VastMediaFile mediaFile,
            Long durationMs,
            Long skipOffsetMs,
            String clickThroughUrl,
            VastTrackingEvents trackingEvents,
            VastPreview preview,
            String title,
            PodOrderingSource orderingSource) {
        this.adId = adId;
        this.playbackIndex = playbackIndex;
        this.vastSequence = vastSequence;
        this.openRtbSlotInPod = openRtbSlotInPod;
        this.openRtbImpId = openRtbImpId;
        this.openRtbPodId = openRtbPodId;
        this.openRtbRequiredDurationSec = openRtbRequiredDurationSec;
        this.mediaFile = mediaFile;
        this.durationMs = durationMs;
        this.skipOffsetMs = skipOffsetMs;
        this.clickThroughUrl = clickThroughUrl;
        this.trackingEvents = trackingEvents != null ? trackingEvents : VastTrackingEvents.empty();
        this.preview = preview;
        this.title = title;
        this.orderingSource = orderingSource != null ? orderingSource : PodOrderingSource.RESPONSE_ORDER;
    }

    public String getAdId() {
        return adId;
    }

    /** @deprecated use {@link #getPlaybackIndex()} or {@link #getVastSequence()} */
    public int getSequence() {
        return vastSequence != null ? vastSequence : playbackIndex;
    }

    public int getPlaybackIndex() {
        return playbackIndex;
    }

    public Integer getVastSequence() {
        return vastSequence;
    }

    public Integer getOpenRtbSlotInPod() {
        return openRtbSlotInPod;
    }

    public String getOpenRtbImpId() {
        return openRtbImpId;
    }

    public String getOpenRtbPodId() {
        return openRtbPodId;
    }

    public Integer getOpenRtbRequiredDurationSec() {
        return openRtbRequiredDurationSec;
    }

    public PodOrderingSource getOrderingSource() {
        return orderingSource;
    }

    public VastMediaFile getMediaFile() {
        return mediaFile;
    }

    public Long getDurationMs() {
        return durationMs;
    }

    public Long getSkipOffsetMs() {
        return skipOffsetMs;
    }

    public String getClickThroughUrl() {
        return clickThroughUrl;
    }

    public VastTrackingEvents getTrackingEvents() {
        return trackingEvents;
    }

    public VastPreview getPreview() {
        return preview;
    }

    public String getTitle() {
        return title;
    }

    public int getSkipOffsetSeconds() {
        if (skipOffsetMs == null || skipOffsetMs <= 0) {
            return 0;
        }
        return (int) (skipOffsetMs / 1000L);
    }
}
