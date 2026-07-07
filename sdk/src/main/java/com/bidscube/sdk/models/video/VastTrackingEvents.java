package com.bidscube.sdk.models.video;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * VAST tracking URLs grouped by event type for one ad.
 */
public final class VastTrackingEvents {

    private final List<String> impressions;
    private final List<String> starts;
    private final List<String> firstQuartiles;
    private final List<String> midpoints;
    private final List<String> thirdQuartiles;
    private final List<String> completes;
    private final List<String> skips;
    private final List<String> clickTrackings;
    private final List<String> errors;
    private final List<String> pauses;
    private final List<String> resumes;
    private final List<String> mutes;
    private final List<String> unmutes;
    private final List<String> closes;

    public VastTrackingEvents(
            List<String> impressions,
            List<String> starts,
            List<String> firstQuartiles,
            List<String> midpoints,
            List<String> thirdQuartiles,
            List<String> completes,
            List<String> skips,
            List<String> clickTrackings,
            List<String> errors) {
        this(impressions, starts, firstQuartiles, midpoints, thirdQuartiles, completes, skips,
                clickTrackings, errors, null, null, null, null, null);
    }

    public VastTrackingEvents(
            List<String> impressions,
            List<String> starts,
            List<String> firstQuartiles,
            List<String> midpoints,
            List<String> thirdQuartiles,
            List<String> completes,
            List<String> skips,
            List<String> clickTrackings,
            List<String> errors,
            List<String> pauses,
            List<String> resumes,
            List<String> mutes,
            List<String> unmutes,
            List<String> closes) {
        this.impressions = copy(impressions);
        this.starts = copy(starts);
        this.firstQuartiles = copy(firstQuartiles);
        this.midpoints = copy(midpoints);
        this.thirdQuartiles = copy(thirdQuartiles);
        this.completes = copy(completes);
        this.skips = copy(skips);
        this.clickTrackings = copy(clickTrackings);
        this.errors = copy(errors);
        this.pauses = copy(pauses);
        this.resumes = copy(resumes);
        this.mutes = copy(mutes);
        this.unmutes = copy(unmutes);
        this.closes = copy(closes);
    }

    public static VastTrackingEvents empty() {
        return new VastTrackingEvents(null, null, null, null, null, null, null, null, null);
    }

    private static List<String> copy(List<String> source) {
        if (source == null || source.isEmpty()) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(new ArrayList<>(source));
    }

    public List<String> getImpressions() {
        return impressions;
    }

    public List<String> getStarts() {
        return starts;
    }

    public List<String> getFirstQuartiles() {
        return firstQuartiles;
    }

    public List<String> getMidpoints() {
        return midpoints;
    }

    public List<String> getThirdQuartiles() {
        return thirdQuartiles;
    }

    public List<String> getCompletes() {
        return completes;
    }

    public List<String> getSkips() {
        return skips;
    }

    public List<String> getClickTrackings() {
        return clickTrackings;
    }

    public List<String> getErrors() {
        return errors;
    }

    public List<String> getPauses() {
        return pauses;
    }

    public List<String> getResumes() {
        return resumes;
    }

    public List<String> getMutes() {
        return mutes;
    }

    public List<String> getUnmutes() {
        return unmutes;
    }

    public List<String> getCloses() {
        return closes;
    }
}
