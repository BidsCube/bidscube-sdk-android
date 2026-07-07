package com.bidscube.sdk.openrtb;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * OpenRTB 2.6 slot-level context for one impression/bid in a pod.
 */
public final class OpenRtbVideoSlotContext {

    private final String impressionId;
    private final String podId;
    private final Integer slotInPod;
    private final Integer requiredDurationSec;
    private final Integer minDurationSec;
    private final Integer maxDurationSec;
    private final Boolean rewarded;
    private final Map<String, Object> ext;

    public OpenRtbVideoSlotContext(
            String impressionId,
            String podId,
            Integer slotInPod,
            Integer requiredDurationSec,
            Integer minDurationSec,
            Integer maxDurationSec,
            Boolean rewarded,
            Map<String, Object> ext) {
        this.impressionId = impressionId;
        this.podId = podId;
        this.slotInPod = slotInPod;
        this.requiredDurationSec = requiredDurationSec;
        this.minDurationSec = minDurationSec;
        this.maxDurationSec = maxDurationSec;
        this.rewarded = rewarded;
        this.ext = ext == null ? Collections.emptyMap() : Collections.unmodifiableMap(new HashMap<>(ext));
    }

    public String getImpressionId() {
        return impressionId;
    }

    public String getPodId() {
        return podId;
    }

    public Integer getSlotInPod() {
        return slotInPod;
    }

    public Integer getRequiredDurationSec() {
        return requiredDurationSec;
    }

    public Integer getMinDurationSec() {
        return minDurationSec;
    }

    public Integer getMaxDurationSec() {
        return maxDurationSec;
    }

    public Boolean getRewarded() {
        return rewarded;
    }

    public Map<String, Object> getExt() {
        return ext;
    }
}
