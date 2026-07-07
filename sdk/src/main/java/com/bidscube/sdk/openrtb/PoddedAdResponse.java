package com.bidscube.sdk.openrtb;

import java.util.Collections;
import java.util.List;

/**
 * Normalized OpenRTB podded ad delivery (one pod, multiple markups).
 */
public final class PoddedAdResponse {

    private final OpenRtbVideoPodContext podContext;
    private final List<PoddedAdMarkup> markups;

    public PoddedAdResponse(OpenRtbVideoPodContext podContext, List<PoddedAdMarkup> markups) {
        this.podContext = podContext;
        this.markups = markups == null ? Collections.emptyList() : Collections.unmodifiableList(markups);
    }

    public OpenRtbVideoPodContext getPodContext() {
        return podContext;
    }

    public List<PoddedAdMarkup> getMarkups() {
        return markups;
    }

    public boolean isEmpty() {
        return markups.isEmpty();
    }

    public boolean isPodded() {
        return !markups.isEmpty() && podContext != null && podContext.hasPodMetadata();
    }
}
