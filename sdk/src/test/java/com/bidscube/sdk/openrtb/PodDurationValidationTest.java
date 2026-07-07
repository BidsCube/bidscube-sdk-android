package com.bidscube.sdk.openrtb;

import com.bidscube.sdk.models.video.VideoAdSlot;
import com.bidscube.sdk.utils.SDKLogger;

import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PodDurationValidationTest {

    @Before
    public void disableLogging() {
        SDKLogger.setLoggingEnabled(false);
    }

    @Test
    public void validatePod_overfill_detectedLenient() {
        OpenRtbVideoPodContext ctx = new OpenRtbVideoPodContext(
                "of", null, 30, null, null,
                List.of(), null, PodType.DYNAMIC, PodOrderingSource.RESPONSE_ORDER);
        List<VideoAdSlot> slots = Arrays.asList(
                slot("a", 20_000L, null),
                slot("b", 20_000L, null));

        PodDurationValidator.Result result = PodDurationValidator.validatePod(
                ctx, slots, PodDurationValidationMode.LENIENT);
        assertTrue(result.isOverfill());
        assertTrue(result.isValid());
    }

    @Test
    public void validatePod_underfill_detected() {
        OpenRtbVideoPodContext ctx = new OpenRtbVideoPodContext(
                "uf", null, 90, null, null,
                List.of(), null, PodType.DYNAMIC, PodOrderingSource.RESPONSE_ORDER);
        List<VideoAdSlot> slots = List.of(slot("a", 15_000L, null));

        PodDurationValidator.Result result = PodDurationValidator.validatePod(
                ctx, slots, PodDurationValidationMode.LENIENT);
        assertTrue(result.isUnderfill());
    }

    @Test
    public void validatePod_rqddursMismatch_logged() {
        OpenRtbVideoPodContext ctx = new OpenRtbVideoPodContext(
                "dm", null, null, null, null,
                List.of(30), null, PodType.STRUCTURED, PodOrderingSource.OPENRTB_SLOT_IN_POD);
        List<VideoAdSlot> slots = List.of(slot("a", 15_000L, 30));

        PodDurationValidator.Result result = PodDurationValidator.validatePod(
                ctx, slots, PodDurationValidationMode.LENIENT);
        assertTrue(result.isDurationMismatch());
        assertTrue(result.isValid());
    }

    @Test
    public void validatePod_strictMode_invalidOnOverfill() {
        OpenRtbVideoPodContext ctx = new OpenRtbVideoPodContext(
                "st", null, 10, null, null,
                List.of(), null, PodType.DYNAMIC, PodOrderingSource.RESPONSE_ORDER);
        List<VideoAdSlot> slots = List.of(slot("a", 30_000L, null));

        PodDurationValidator.Result result = PodDurationValidator.validatePod(
                ctx, slots, PodDurationValidationMode.STRICT);
        assertFalse(result.isValid());
    }

    @Test
    public void validatePod_strictMode_invalidOnRqddursMismatch() {
        OpenRtbVideoPodContext ctx = new OpenRtbVideoPodContext(
                "st", null, null, null, null,
                List.of(30), null, PodType.STRUCTURED, PodOrderingSource.OPENRTB_SLOT_IN_POD);
        List<VideoAdSlot> slots = List.of(slot("a", 15_000L, 30));

        PodDurationValidator.Result result = PodDurationValidator.validatePod(
                ctx, slots, PodDurationValidationMode.STRICT);
        assertTrue(result.isDurationMismatch());
        assertFalse(result.isValid());
    }

    @Test
    public void validateSlotDuration_strict_returnsFalseWithoutThrowing() {
        VideoAdSlot slot = slot("a", 15_000L, 30);
        assertFalse(PodDurationValidator.validateSlotDuration(slot, PodDurationValidationMode.STRICT));
        assertTrue(PodDurationValidator.validateSlotDuration(slot, PodDurationValidationMode.LENIENT));
    }

    private static VideoAdSlot slot(String adId, long durationMs, Integer requiredSec) {
        return new VideoAdSlot(
                adId,
                1,
                1,
                1,
                "imp-" + adId,
                "pod",
                requiredSec,
                null,
                durationMs,
                null,
                null,
                null,
                null,
                null,
                PodOrderingSource.OPENRTB_SLOT_IN_POD);
    }
}
