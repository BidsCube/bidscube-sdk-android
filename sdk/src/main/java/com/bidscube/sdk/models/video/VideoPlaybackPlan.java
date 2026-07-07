package com.bidscube.sdk.models.video;

import com.bidscube.sdk.openrtb.OpenRtbVideoPodContext;
import com.bidscube.sdk.openrtb.PodType;

import java.util.Collections;
import java.util.List;

/**
 * Resolved playback queue for inline VAST video (single ad or pod).
 */
public final class VideoPlaybackPlan {

    private final VideoPlaybackPlanType type;
    private final List<VideoAdSlot> slots;
    private final boolean hasSequencedAds;
    private final Long totalDurationMs;
    private final VastPreview endCardPreview;
    private final OpenRtbVideoPodContext openRtbPodContext;
    private final boolean openRtbPodded;
    private final boolean vastSequencedPod;
    private final Long expectedPodDurationMs;
    private final PodType openRtbPodType;

    public VideoPlaybackPlan(
            VideoPlaybackPlanType type,
            List<VideoAdSlot> slots,
            boolean hasSequencedAds,
            Long totalDurationMs,
            VastPreview endCardPreview) {
        this(type, slots, hasSequencedAds, totalDurationMs, endCardPreview, null, false, false, null, PodType.NONE);
    }

    public VideoPlaybackPlan(
            VideoPlaybackPlanType type,
            List<VideoAdSlot> slots,
            boolean hasSequencedAds,
            Long totalDurationMs,
            VastPreview endCardPreview,
            OpenRtbVideoPodContext openRtbPodContext,
            boolean openRtbPodded,
            boolean vastSequencedPod,
            Long expectedPodDurationMs,
            PodType openRtbPodType) {
        this.type = type != null ? type : VideoPlaybackPlanType.EMPTY;
        this.slots = slots == null ? Collections.emptyList() : Collections.unmodifiableList(slots);
        this.hasSequencedAds = hasSequencedAds;
        this.totalDurationMs = totalDurationMs;
        this.endCardPreview = endCardPreview;
        this.openRtbPodContext = openRtbPodContext;
        this.openRtbPodded = openRtbPodded;
        this.vastSequencedPod = vastSequencedPod;
        this.expectedPodDurationMs = expectedPodDurationMs;
        this.openRtbPodType = openRtbPodType != null ? openRtbPodType : PodType.NONE;
    }

    public static VideoPlaybackPlan empty() {
        return new VideoPlaybackPlan(
                VideoPlaybackPlanType.EMPTY,
                Collections.emptyList(),
                false,
                null,
                null);
    }

    public VideoPlaybackPlanType getType() {
        return type;
    }

    public List<VideoAdSlot> getSlots() {
        return slots;
    }

    public boolean hasSequencedAds() {
        return hasSequencedAds;
    }

    /** @deprecated use {@link #getType()} == {@link VideoPlaybackPlanType#POD} */
    @Deprecated
    public boolean isPod() {
        return type == VideoPlaybackPlanType.POD;
    }

    public Long getTotalDurationMs() {
        return totalDurationMs;
    }

    public VastPreview getEndCardPreview() {
        return endCardPreview;
    }

    public OpenRtbVideoPodContext getOpenRtbPodContext() {
        return openRtbPodContext;
    }

    public boolean isOpenRtbPodded() {
        return openRtbPodded;
    }

    public boolean isVastSequencedPod() {
        return vastSequencedPod;
    }

    public Long getExpectedPodDurationMs() {
        return expectedPodDurationMs;
    }

    public PodType getOpenRtbPodType() {
        return openRtbPodType;
    }

    public boolean isEmpty() {
        return type == VideoPlaybackPlanType.EMPTY || slots.isEmpty();
    }

    public int getTotalAds() {
        return slots.size();
    }

    public VideoAdSlot getFirstSlot() {
        return slots.isEmpty() ? null : slots.get(0);
    }

    /** True when this plan should play as a multi-slot pod experience. */
    public boolean isPodPlayback() {
        return type == VideoPlaybackPlanType.POD || openRtbPodded;
    }
}
