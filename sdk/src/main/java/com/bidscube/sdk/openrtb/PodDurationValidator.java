package com.bidscube.sdk.openrtb;

import com.bidscube.sdk.models.video.VideoAdSlot;
import com.bidscube.sdk.utils.SDKLogger;

import java.util.List;

/**
 * Validates OpenRTB pod duration constraints against resolved VAST slots.
 */
public final class PodDurationValidator {

    private static final String TAG = "PodDurationValidator";
    private static final long SLOT_DURATION_TOLERANCE_MS = 500L;
    private static final double UNDERFILL_RATIO = 0.8;

    public static final class Result {
        private final boolean valid;
        private final boolean overfill;
        private final boolean underfill;
        private final boolean durationMismatch;
        private final long expectedMs;
        private final long actualMs;
        private final String message;

        Result(boolean valid, boolean overfill, boolean underfill, boolean durationMismatch,
               long expectedMs, long actualMs, String message) {
            this.valid = valid;
            this.overfill = overfill;
            this.underfill = underfill;
            this.durationMismatch = durationMismatch;
            this.expectedMs = expectedMs;
            this.actualMs = actualMs;
            this.message = message;
        }

        public boolean isValid() {
            return valid;
        }

        public boolean isOverfill() {
            return overfill;
        }

        public boolean isUnderfill() {
            return underfill;
        }

        public boolean isDurationMismatch() {
            return durationMismatch;
        }

        public long getExpectedMs() {
            return expectedMs;
        }

        public long getActualMs() {
            return actualMs;
        }

        public String getMessage() {
            return message;
        }
    }

    private PodDurationValidator() {
    }

    public static Result validatePod(
            OpenRtbVideoPodContext podContext,
            List<VideoAdSlot> slots,
            PodDurationValidationMode mode) {
        if (podContext == null || slots == null || slots.isEmpty()) {
            return new Result(true, false, false, false, 0, 0, "no pod context");
        }

        long actualMs = 0;
        boolean slotMismatch = false;
        for (VideoAdSlot slot : slots) {
            if (slot.getDurationMs() != null) {
                actualMs += slot.getDurationMs();
            }
            if (slot.getOpenRtbRequiredDurationSec() != null && slot.getDurationMs() != null) {
                long requiredMs = slot.getOpenRtbRequiredDurationSec() * 1000L;
                if (Math.abs(slot.getDurationMs() - requiredMs) > SLOT_DURATION_TOLERANCE_MS) {
                    slotMismatch = true;
                    SDKLogger.w(TAG, "Duration mismatch slotInPod=" + slot.getOpenRtbSlotInPod()
                            + " requiredSec=" + slot.getOpenRtbRequiredDurationSec()
                            + " vastMs=" + slot.getDurationMs());
                }
            }
        }

        Long expectedPodMs = podContext.getExpectedPodDurationMs();
        boolean overfill = expectedPodMs != null && actualMs > expectedPodMs;
        boolean underfill = expectedPodMs != null && actualMs > 0 && actualMs < expectedPodMs * UNDERFILL_RATIO;

        if (overfill) {
            SDKLogger.w(TAG, "Pod overfill podId=" + podContext.getPodId()
                    + " expectedMs=" + expectedPodMs + " actualMs=" + actualMs);
        }
        if (underfill) {
            SDKLogger.w(TAG, "Pod underfill podId=" + podContext.getPodId()
                    + " expectedMs=" + expectedPodMs + " actualMs=" + actualMs);
        }

        List<Integer> rqddurs = podContext.getRequiredDurationsSec();
        if (!rqddurs.isEmpty()) {
            for (VideoAdSlot slot : slots) {
                if (slot.getDurationMs() == null) {
                    continue;
                }
                int sec = (int) (slot.getDurationMs() / 1000L);
                if (!rqddurs.contains(sec)) {
                    slotMismatch = true;
                    SDKLogger.w(TAG, "rqddurs mismatch slotInPod=" + slot.getOpenRtbSlotInPod()
                            + " vastSec=" + sec + " rqddurs=" + rqddurs);
                }
            }
        }

        boolean valid = mode == PodDurationValidationMode.LENIENT
                || (!overfill && !slotMismatch);
        String msg = "expected=" + (expectedPodMs != null ? expectedPodMs : 0)
                + " actual=" + actualMs;
        return new Result(valid, overfill, underfill, slotMismatch,
                expectedPodMs != null ? expectedPodMs : 0, actualMs, msg);
    }

    /**
     * @return {@code true} when the slot passes validation for the given mode.
     */
    public static boolean validateSlotDuration(VideoAdSlot slot, PodDurationValidationMode mode) {
        if (slot == null || slot.getOpenRtbRequiredDurationSec() == null || slot.getDurationMs() == null) {
            return true;
        }
        long requiredMs = slot.getOpenRtbRequiredDurationSec() * 1000L;
        if (Math.abs(slot.getDurationMs() - requiredMs) <= SLOT_DURATION_TOLERANCE_MS) {
            return true;
        }
        SDKLogger.w(TAG, "OpenRTB rqddurs mismatch impId=" + slot.getOpenRtbImpId()
                + " requiredSec=" + slot.getOpenRtbRequiredDurationSec()
                + " vastDurationMs=" + slot.getDurationMs());
        return mode != PodDurationValidationMode.STRICT;
    }
}
