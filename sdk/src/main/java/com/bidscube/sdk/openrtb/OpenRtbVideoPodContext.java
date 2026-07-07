package com.bidscube.sdk.openrtb;

import java.util.Collections;
import java.util.List;

/**
 * OpenRTB 2.6 video pod context from ad delivery metadata.
 */
public final class OpenRtbVideoPodContext {

    private final String podId;
    private final Integer podSequence;
    private final Integer podDurationSec;
    private final Integer maxSequence;
    private final Integer slotInPod;
    private final List<Integer> requiredDurationsSec;
    private final Float minCpmPerSec;
    private final PodType podType;
    private final PodOrderingSource orderingSource;

    public OpenRtbVideoPodContext(
            String podId,
            Integer podSequence,
            Integer podDurationSec,
            Integer maxSequence,
            Integer slotInPod,
            List<Integer> requiredDurationsSec,
            Float minCpmPerSec,
            PodType podType,
            PodOrderingSource orderingSource) {
        this.podId = podId;
        this.podSequence = podSequence;
        this.podDurationSec = podDurationSec;
        this.maxSequence = maxSequence;
        this.slotInPod = slotInPod;
        this.requiredDurationsSec = requiredDurationsSec == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(requiredDurationsSec);
        this.minCpmPerSec = minCpmPerSec;
        this.podType = podType != null ? podType : PodType.NONE;
        this.orderingSource = orderingSource != null ? orderingSource : PodOrderingSource.RESPONSE_ORDER;
    }

    public String getPodId() {
        return podId;
    }

    public Integer getPodSequence() {
        return podSequence;
    }

    public Integer getPodDurationSec() {
        return podDurationSec;
    }

    public Integer getMaxSequence() {
        return maxSequence;
    }

    public Integer getSlotInPod() {
        return slotInPod;
    }

    public List<Integer> getRequiredDurationsSec() {
        return requiredDurationsSec;
    }

    public Float getMinCpmPerSec() {
        return minCpmPerSec;
    }

    public PodType getPodType() {
        return podType;
    }

    public PodOrderingSource getOrderingSource() {
        return orderingSource;
    }

    public boolean hasPodMetadata() {
        return podId != null || podDurationSec != null || maxSequence != null
                || !requiredDurationsSec.isEmpty() || slotInPod != null;
    }

    public Long getExpectedPodDurationMs() {
        return podDurationSec != null ? podDurationSec * 1000L : null;
    }
}
