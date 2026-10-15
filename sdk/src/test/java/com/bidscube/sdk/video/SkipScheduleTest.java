package com.bidscube.sdk.video;

import com.bidscube.sdk.ads.VideoAdFormat;
import com.bidscube.sdk.utils.VastSkipOffset;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SkipScheduleTest {

    @Test
    public void absent_usesPublisherDefault15() {
        VideoUiDefaults.SkipSchedule s =
                VideoUiDefaults.resolveSkipSchedule(VastSkipOffset.absent(), 30);
        assertTrue(s.skipAllowed);
        assertEquals(15, s.availableAfterSeconds);
    }

    @Test
    public void invalid_engineeringFallback_sameTimingAsAbsent_notReadmeClaim() {
        // INVALID is not README omit policy; engineering keeps interstitial usable.
        VideoUiDefaults.SkipSchedule s =
                VideoUiDefaults.resolveSkipSchedule(VastSkipOffset.invalid(), 10);
        assertTrue(s.skipAllowed);
        assertEquals(15, s.availableAfterSeconds);
    }

    @Test
    public void explicitZero_isImmediateSkip() {
        VideoUiDefaults.SkipSchedule s =
                VideoUiDefaults.resolveSkipSchedule(VastSkipOffset.timeSeconds(0), 30);
        assertTrue(s.skipAllowed);
        assertEquals(0, s.availableAfterSeconds);
    }

    @Test
    public void explicitFive_kept() {
        VideoUiDefaults.SkipSchedule s =
                VideoUiDefaults.resolveSkipSchedule(VastSkipOffset.timeSeconds(5), 30);
        assertEquals(5, s.availableAfterSeconds);
    }

    @Test
    public void percent_ofDuration() {
        VideoUiDefaults.SkipSchedule s =
                VideoUiDefaults.resolveSkipSchedule(VastSkipOffset.percent(25), 40);
        assertEquals(10, s.availableAfterSeconds);
    }

    @Test
    public void percent_withoutDuration_engineeringFallback15() {
        VideoUiDefaults.SkipSchedule s =
                VideoUiDefaults.resolveSkipSchedule(VastSkipOffset.percent(25), 0);
        assertTrue(s.skipAllowed);
        assertEquals(15, s.availableAfterSeconds);
    }

    @Test
    public void rewarded_neverAllowsSkip_evenWithVastZero() {
        VideoUiDefaults.SkipSchedule s = VideoUiDefaults.resolveSkipSchedule(
                VastSkipOffset.timeSeconds(0), 30, VideoAdFormat.REWARDED);
        assertFalse(s.skipAllowed);
        assertEquals(0, s.availableAfterSeconds);
    }

    @Test
    public void rewarded_ignoresAbsentDefault() {
        VideoUiDefaults.SkipSchedule s = VideoUiDefaults.resolveSkipSchedule(
                VastSkipOffset.absent(), 30, VideoAdFormat.REWARDED);
        assertFalse(s.skipAllowed);
    }

    @Test
    public void interstitial_formatKeepsVastSchedule() {
        VideoUiDefaults.SkipSchedule s = VideoUiDefaults.resolveSkipSchedule(
                VastSkipOffset.timeSeconds(5), 30, VideoAdFormat.INTERSTITIAL);
        assertTrue(s.skipAllowed);
        assertEquals(5, s.availableAfterSeconds);
    }

    @Test
    public void legacyResolve_zeroStillMapsTo15() {
        assertEquals(15, VideoUiDefaults.resolveSkipOffsetSeconds(0));
    }
}
