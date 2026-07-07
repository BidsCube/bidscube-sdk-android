package com.bidscube.sdk.models.video;

import java.util.Collections;
import java.util.List;

public final class VastLinear {

    private final Long durationMs;
    private final Long skipOffsetMs;
    private final List<VastMediaFile> mediaFiles;

    public VastLinear(Long durationMs, Long skipOffsetMs, List<VastMediaFile> mediaFiles) {
        this.durationMs = durationMs;
        this.skipOffsetMs = skipOffsetMs;
        this.mediaFiles = mediaFiles == null ? Collections.emptyList() : mediaFiles;
    }

    public Long getDurationMs() {
        return durationMs;
    }

    public Long getSkipOffsetMs() {
        return skipOffsetMs;
    }

    public List<VastMediaFile> getMediaFiles() {
        return mediaFiles;
    }
}
