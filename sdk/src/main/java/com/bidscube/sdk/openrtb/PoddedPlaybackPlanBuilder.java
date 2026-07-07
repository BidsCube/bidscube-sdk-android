package com.bidscube.sdk.openrtb;

import com.bidscube.sdk.interfaces.AdCallback;
import com.bidscube.sdk.models.video.VideoAdSlot;
import com.bidscube.sdk.models.video.VideoPlaybackPlan;
import com.bidscube.sdk.models.video.VideoPlaybackPlanType;
import com.bidscube.sdk.models.video.VastPreview;
import com.bidscube.sdk.utils.SDKLogger;
import com.bidscube.sdk.utils.VideoPlaybackPlanBuilder;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Builds {@link VideoPlaybackPlan} from OpenRTB pod metadata + VAST markups.
 */
public final class PoddedPlaybackPlanBuilder {

    private static final String TAG = "PoddedPlaybackPlanBuilder";

    private PoddedPlaybackPlanBuilder() {
    }

    public static VideoPlaybackPlan build(PoddedAdResponse response, VideoPodConfig config) {
        if (response == null || response.isEmpty()) {
            return VideoPlaybackPlan.empty();
        }
        VideoPodConfig cfg = config != null ? config : VideoPodConfig.defaults();
        if (!cfg.isEnabled()) {
            return VideoPlaybackPlan.empty();
        }

        OpenRtbVideoPodContext podContext = response.getPodContext();
        List<PoddedAdMarkup> markups = response.getMarkups();

        // Shape A: single adm containing a multi-ad VAST pod + OpenRTB metadata
        if (markups.size() == 1) {
            PoddedAdMarkup markup = markups.get(0);
            if (markup.isInlineVast()) {
                VideoPlaybackPlan vastPlan = VideoPlaybackPlanBuilder.build(markup.getVastXmlOrTagUrl());
                if (!vastPlan.isEmpty() && vastPlan.getTotalAds() > 1) {
                    return enrichVastPlanWithOpenRtb(vastPlan, podContext, markup, cfg);
                }
                if (!vastPlan.isEmpty() && podContext != null && podContext.hasPodMetadata()) {
                    return enrichVastPlanWithOpenRtb(vastPlan, podContext, markup, cfg);
                }
            }
        }

        List<SlotCandidate> candidates = buildCandidates(markups, podContext);
        if (candidates.isEmpty()) {
            return VideoPlaybackPlan.empty();
        }

        sortCandidates(candidates);
        List<VideoAdSlot> slots = applyPodTypeRules(candidates, podContext);
        List<VideoAdSlot> indexed = reindexSlots(slots);

        if (cfg.isOpenRtbPodMetadataEnabled()) {
            if (!validateSlotsOrFailStrict(indexed, cfg)) {
                return VideoPlaybackPlan.empty();
            }
        }

        PodDurationValidator.Result validation = PodDurationValidator.validatePod(
                podContext, indexed, cfg.getDurationValidationMode());
        if (!validation.isValid() && cfg.getDurationValidationMode() == PodDurationValidationMode.STRICT) {
            SDKLogger.e(TAG, "Strict pod duration validation failed: " + validation.getMessage());
            return VideoPlaybackPlan.empty();
        }

        long totalDuration = sumDuration(indexed);
        VastPreview endCard = resolveEndCard(markups);
        boolean vastPod = indexed.size() >= 2 && hasVastSequence(indexed);
        VideoPlaybackPlanType type = indexed.size() >= 2 ? VideoPlaybackPlanType.POD : VideoPlaybackPlanType.SINGLE;
        Long expectedMs = podContext != null ? podContext.getExpectedPodDurationMs() : null;

        SDKLogger.d(TAG, "OpenRTB pod plan type=" + type
                + " podType=" + (podContext != null ? podContext.getPodType() : PodType.NONE)
                + " slots=" + indexed.size());

        return new VideoPlaybackPlan(
                type,
                indexed,
                vastPod,
                totalDuration > 0 ? totalDuration : null,
                endCard,
                podContext,
                podContext != null && podContext.hasPodMetadata(),
                vastPod,
                expectedMs,
                podContext != null ? podContext.getPodType() : PodType.NONE);
    }

    public static void fireDurationMismatchIfNeeded(
            AdCallback callback,
            String placementId,
            VideoPlaybackPlan plan,
            PodDurationValidator.Result result) {
        if (callback == null || plan == null || result == null) {
            return;
        }
        if (result.isOverfill() || result.isUnderfill() || result.isDurationMismatch()) {
            String podId = plan.getOpenRtbPodContext() != null
                    ? plan.getOpenRtbPodContext().getPodId()
                    : null;
            callback.onVideoPodDurationMismatch(
                    placementId,
                    podId,
                    result.getExpectedMs(),
                    result.getActualMs());
        }
    }

    private static VideoPlaybackPlan enrichVastPlanWithOpenRtb(
            VideoPlaybackPlan vastPlan,
            OpenRtbVideoPodContext podContext,
            PoddedAdMarkup markup,
            VideoPodConfig cfg) {
        List<VideoAdSlot> enriched = new ArrayList<>();
        boolean multiAd = vastPlan.getTotalAds() > 1;
        int i = 0;
        for (VideoAdSlot slot : vastPlan.getSlots()) {
            Integer vastSeq = slot.getVastSequence() != null ? slot.getVastSequence() : slot.getSequence();
            if (multiAd) {
                enriched.add(enrichInlineVastPodSlot(slot, markup, podContext, ++i, vastSeq));
            } else {
                enriched.add(enrichSlot(slot, markup, podContext, ++i, vastSeq));
            }
        }

        if (podContext != null && cfg.isOpenRtbPodMetadataEnabled()) {
            if (!validateSlotsOrFailStrict(enriched, cfg)) {
                return VideoPlaybackPlan.empty();
            }
        }

        PodDurationValidator.Result validation = PodDurationValidator.validatePod(
                podContext, enriched, cfg.getDurationValidationMode());
        if (!validation.isValid() && cfg.getDurationValidationMode() == PodDurationValidationMode.STRICT) {
            SDKLogger.e(TAG, "Strict pod duration validation failed: " + validation.getMessage());
            return VideoPlaybackPlan.empty();
        }

        VideoPlaybackPlanType type = enriched.size() >= 2 ? VideoPlaybackPlanType.POD : vastPlan.getType();
        return new VideoPlaybackPlan(
                type,
                enriched,
                vastPlan.hasSequencedAds(),
                vastPlan.getTotalDurationMs(),
                vastPlan.getEndCardPreview(),
                podContext,
                podContext != null && podContext.hasPodMetadata(),
                vastPlan.isVastSequencedPod() || type == VideoPlaybackPlanType.POD,
                podContext != null ? podContext.getExpectedPodDurationMs() : null,
                podContext != null ? podContext.getPodType() : PodType.NONE);
    }

    private static List<SlotCandidate> buildCandidates(
            List<PoddedAdMarkup> markups,
            OpenRtbVideoPodContext podContext) {
        List<SlotCandidate> candidates = new ArrayList<>();
        for (PoddedAdMarkup markup : markups) {
            if (!markup.isInlineVast()) {
                SDKLogger.w(TAG, "Skipping non-inline VAST markup impId=" + markup.getImpId());
                continue;
            }
            VideoPlaybackPlan vastPlan = VideoPlaybackPlanBuilder.build(markup.getVastXmlOrTagUrl());
            if (vastPlan.isEmpty() || vastPlan.getFirstSlot() == null) {
                SDKLogger.w(TAG, "No playable VAST for impId=" + markup.getImpId());
                continue;
            }
            VideoAdSlot vastSlot = vastPlan.getFirstSlot();
            Integer openRtbSlot = resolveOpenRtbSlot(markup);
            Integer vastSeq = vastSlot.getVastSequence();
            VideoAdSlot enriched = enrichSlot(vastSlot, markup, podContext, 0, vastSeq);
            candidates.add(new SlotCandidate(enriched, openRtbSlot, vastSeq, markup.getResponseOrder()));
        }
        return candidates;
    }

    /**
     * Enriches one slot from inline multi-ad VAST. Pod-level {@code slotinpod} on the markup
     * is not copied to every VAST {@code <Ad>}; ordering comes from VAST {@code sequence},
     * and {@code rqddurs} map by sequence / playback index.
     */
    private static VideoAdSlot enrichInlineVastPodSlot(
            VideoAdSlot vastSlot,
            PoddedAdMarkup markup,
            OpenRtbVideoPodContext podContext,
            int playbackSequence,
            Integer vastSequence) {
        Integer markupSlot = resolveOpenRtbSlot(markup);
        Integer openRtbSlot = null;
        if (vastSequence != null) {
            openRtbSlot = vastSequence;
            if (markupSlot != null && markupSlot.equals(vastSequence)) {
                openRtbSlot = markupSlot;
            }
        }
        logOpenRtbVastMismatch(markup, openRtbSlot, vastSequence);
        PodOrderingSource ordering = openRtbSlot != null && markupSlot != null && openRtbSlot.equals(markupSlot)
                ? PodOrderingSource.OPENRTB_SLOT_IN_POD
                : (vastSequence != null ? PodOrderingSource.VAST_SEQUENCE : PodOrderingSource.RESPONSE_ORDER);
        int rqddursIndex = openRtbSlot != null ? openRtbSlot : playbackSequence;
        Integer reqDur = resolveRequiredDuration(podContext, rqddursIndex);
        return new VideoAdSlot(
                vastSlot.getAdId(),
                playbackSequence,
                vastSequence,
                openRtbSlot,
                markup.getImpId(),
                podContext != null ? podContext.getPodId() : null,
                reqDur,
                vastSlot.getMediaFile(),
                vastSlot.getDurationMs(),
                vastSlot.getSkipOffsetMs(),
                vastSlot.getClickThroughUrl(),
                vastSlot.getTrackingEvents(),
                vastSlot.getPreview(),
                vastSlot.getTitle(),
                ordering);
    }

    private static boolean validateSlotsOrFailStrict(List<VideoAdSlot> slots, VideoPodConfig cfg) {
        for (VideoAdSlot slot : slots) {
            if (!PodDurationValidator.validateSlotDuration(slot, cfg.getDurationValidationMode())) {
                SDKLogger.e(TAG, "Strict slot duration validation failed impId=" + slot.getOpenRtbImpId());
                return false;
            }
        }
        return true;
    }

    private static VideoAdSlot enrichSlot(
            VideoAdSlot vastSlot,
            PoddedAdMarkup markup,
            OpenRtbVideoPodContext podContext,
            int playbackSequence,
            Integer vastSequence) {
        Integer openRtbSlot = resolveOpenRtbSlot(markup);
        logOpenRtbVastMismatch(markup, openRtbSlot, vastSequence);
        PodOrderingSource ordering = resolveOrderingSource(openRtbSlot, vastSequence);
        Integer reqDur = markup.getSlotContext() != null
                ? markup.getSlotContext().getRequiredDurationSec()
                : resolveRequiredDuration(podContext, openRtbSlot);
        return new VideoAdSlot(
                vastSlot.getAdId(),
                playbackSequence,
                vastSequence,
                openRtbSlot,
                markup.getImpId(),
                podContext != null ? podContext.getPodId() : null,
                reqDur,
                vastSlot.getMediaFile(),
                vastSlot.getDurationMs(),
                vastSlot.getSkipOffsetMs(),
                vastSlot.getClickThroughUrl(),
                vastSlot.getTrackingEvents(),
                vastSlot.getPreview(),
                vastSlot.getTitle(),
                ordering);
    }

    private static Integer resolveOpenRtbSlot(PoddedAdMarkup markup) {
        Integer openRtbSlot = markup.getSlotInPod();
        if (openRtbSlot == null && markup.getSlotContext() != null) {
            openRtbSlot = markup.getSlotContext().getSlotInPod();
        }
        return openRtbSlot;
    }

    private static PodOrderingSource resolveOrderingSource(Integer openRtbSlot, Integer vastSeq) {
        if (openRtbSlot != null) {
            return PodOrderingSource.OPENRTB_SLOT_IN_POD;
        }
        if (vastSeq != null) {
            return PodOrderingSource.VAST_SEQUENCE;
        }
        return PodOrderingSource.RESPONSE_ORDER;
    }

    private static void logOpenRtbVastMismatch(
            PoddedAdMarkup markup,
            Integer openRtbSlot,
            Integer vastSeq) {
        if (openRtbSlot != null && vastSeq != null && !openRtbSlot.equals(vastSeq)) {
            SDKLogger.w(TAG, "OpenRTB/VAST pod position mismatch: impId=" + markup.getImpId()
                    + " openRtbSlotInPod=" + openRtbSlot
                    + " vastSequence=" + vastSeq
                    + " — using OpenRTB slotinpod");
        }
    }

    private static Integer resolveRequiredDuration(OpenRtbVideoPodContext podContext, Integer slotInPod) {
        if (podContext == null || slotInPod == null) {
            return null;
        }
        List<Integer> rqddurs = podContext.getRequiredDurationsSec();
        if (rqddurs.isEmpty()) {
            return null;
        }
        int index = slotInPod - 1;
        if (index >= 0 && index < rqddurs.size()) {
            return rqddurs.get(index);
        }
        return null;
    }

    private static List<VideoAdSlot> reindexSlots(List<VideoAdSlot> slots) {
        List<VideoAdSlot> indexed = new ArrayList<>();
        for (int i = 0; i < slots.size(); i++) {
            VideoAdSlot s = slots.get(i);
            indexed.add(new VideoAdSlot(
                    s.getAdId(),
                    i + 1,
                    s.getVastSequence(),
                    s.getOpenRtbSlotInPod(),
                    s.getOpenRtbImpId(),
                    s.getOpenRtbPodId(),
                    s.getOpenRtbRequiredDurationSec(),
                    s.getMediaFile(),
                    s.getDurationMs(),
                    s.getSkipOffsetMs(),
                    s.getClickThroughUrl(),
                    s.getTrackingEvents(),
                    s.getPreview(),
                    s.getTitle(),
                    s.getOrderingSource()));
        }
        return indexed;
    }

    private static long sumDuration(List<VideoAdSlot> slots) {
        long total = 0;
        for (VideoAdSlot slot : slots) {
            if (slot.getDurationMs() != null) {
                total += slot.getDurationMs();
            }
        }
        return total;
    }

    private static VastPreview resolveEndCard(List<PoddedAdMarkup> markups) {
        for (PoddedAdMarkup markup : markups) {
            if (markup.isInlineVast()) {
                VideoPlaybackPlan p = VideoPlaybackPlanBuilder.build(markup.getVastXmlOrTagUrl());
                if (p.getEndCardPreview() != null) {
                    return p.getEndCardPreview();
                }
            }
        }
        return null;
    }

    private static void sortCandidates(List<SlotCandidate> candidates) {
        boolean anyOpenRtb = false;
        for (SlotCandidate c : candidates) {
            if (c.openRtbSlot != null) {
                anyOpenRtb = true;
                break;
            }
        }
        if (anyOpenRtb) {
            candidates.sort(Comparator
                    .comparingInt((SlotCandidate c) -> c.openRtbSlot != null ? c.openRtbSlot : Integer.MAX_VALUE)
                    .thenComparingInt(c -> c.vastSequence != null ? c.vastSequence : Integer.MAX_VALUE)
                    .thenComparingInt(c -> c.responseOrder));
            return;
        }
        boolean anyVast = false;
        for (SlotCandidate c : candidates) {
            if (c.vastSequence != null) {
                anyVast = true;
                break;
            }
        }
        if (anyVast) {
            candidates.sort(Comparator
                    .comparingInt((SlotCandidate c) -> c.vastSequence != null ? c.vastSequence : Integer.MAX_VALUE)
                    .thenComparingInt(c -> c.responseOrder));
        } else {
            candidates.sort(Comparator.comparingInt(c -> c.responseOrder));
        }
    }

    private static List<VideoAdSlot> applyPodTypeRules(
            List<SlotCandidate> candidates,
            OpenRtbVideoPodContext ctx) {
        List<VideoAdSlot> slots = new ArrayList<>();
        for (SlotCandidate c : candidates) {
            slots.add(c.slot);
        }
        if (ctx == null) {
            return slots;
        }
        PodType type = ctx.getPodType();

        if (type == PodType.DYNAMIC && ctx.getPodDurationSec() != null) {
            long budgetMs = ctx.getPodDurationSec() * 1000L;
            List<VideoAdSlot> trimmed = new ArrayList<>();
            long used = 0;
            int maxSeq = ctx.getMaxSequence() != null ? ctx.getMaxSequence() : Integer.MAX_VALUE;
            for (VideoAdSlot slot : slots) {
                if (trimmed.size() >= maxSeq) {
                    break;
                }
                long dur = slot.getDurationMs() != null ? slot.getDurationMs() : 0;
                if (dur > budgetMs) {
                    SDKLogger.w(TAG, "Dynamic pod: skipping slot over budgetMs=" + budgetMs
                            + " slotMs=" + dur + " adId=" + slot.getAdId());
                    continue;
                }
                if (used + dur > budgetMs && !trimmed.isEmpty()) {
                    SDKLogger.w(TAG, "Dynamic pod: stopping at budgetMs=" + budgetMs + " usedMs=" + used);
                    break;
                }
                trimmed.add(slot);
                used += dur;
            }
            return trimmed;
        }

        if (type == PodType.HYBRID) {
            List<VideoAdSlot> fixed = new ArrayList<>();
            List<VideoAdSlot> dynamic = new ArrayList<>();
            for (VideoAdSlot slot : slots) {
                if (slot.getOpenRtbSlotInPod() != null) {
                    fixed.add(slot);
                } else {
                    dynamic.add(slot);
                }
            }
            fixed.sort(Comparator.comparingInt(s -> s.getOpenRtbSlotInPod() != null ? s.getOpenRtbSlotInPod() : 0));
            fixed.addAll(dynamic);
            return fixed;
        }

        if (ctx.getMaxSequence() != null && slots.size() > ctx.getMaxSequence()) {
            SDKLogger.w(TAG, "Trimming pod to maxseq=" + ctx.getMaxSequence());
            return slots.subList(0, ctx.getMaxSequence());
        }
        return slots;
    }

    private static boolean hasVastSequence(List<VideoAdSlot> slots) {
        Set<Integer> seen = new HashSet<>();
        for (VideoAdSlot slot : slots) {
            if (slot.getVastSequence() != null) {
                seen.add(slot.getVastSequence());
            }
        }
        return seen.size() >= 2;
    }

    private static final class SlotCandidate {
        final VideoAdSlot slot;
        final Integer openRtbSlot;
        final Integer vastSequence;
        final int responseOrder;

        SlotCandidate(VideoAdSlot slot, Integer openRtbSlot, Integer vastSequence, int responseOrder) {
            this.slot = slot;
            this.openRtbSlot = openRtbSlot;
            this.vastSequence = vastSequence;
            this.responseOrder = responseOrder;
        }
    }
}
