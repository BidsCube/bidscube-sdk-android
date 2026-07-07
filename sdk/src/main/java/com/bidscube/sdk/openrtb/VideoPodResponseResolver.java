package com.bidscube.sdk.openrtb;

import com.bidscube.sdk.models.video.VideoPlaybackPlan;
import com.bidscube.sdk.network.BidscubeResponse;
import com.bidscube.sdk.utils.SDKLogger;
import com.bidscube.sdk.utils.VideoPlaybackPlanBuilder;

/**
 * Resolves a {@link VideoPlaybackPlan} from a Bidscube ad response,
 * preferring OpenRTB-like pod metadata when present.
 *
 * <p>This is <strong>response-side</strong> support only. The SDK does not build or POST
 * OpenRTB bid requests; it parses pod fields from the SSP JSON response.</p>
 */
public final class VideoPodResponseResolver {

    private static final String TAG = "VideoPodResponseResolver";

    private VideoPodResponseResolver() {
    }

    public static VideoPlaybackPlan resolve(BidscubeResponse response) {
        return resolve(response, VideoPodConfig.defaults());
    }

    public static VideoPlaybackPlan resolve(BidscubeResponse response, VideoPodConfig config) {
        if (response == null) {
            return VideoPlaybackPlan.empty();
        }
        VideoPodConfig cfg = config != null ? config : VideoPodConfig.defaults();

        PoddedAdResponse podded = response.getPoddedAdResponse();
        if (podded == null && response.getRawJson() != null) {
            podded = OpenRtbPoddedResponseNormalizer.normalize(response.getRawJson(), response.getAdm());
        }

        if (cfg.isEnabled() && cfg.isOpenRtbPodMetadataEnabled() && shouldUsePoddedBuilder(podded)) {
            VideoPlaybackPlan plan = PoddedPlaybackPlanBuilder.build(podded, cfg);
            if (!plan.isEmpty()) {
                SDKLogger.d(TAG, "Resolved pod plan slots=" + plan.getTotalAds()
                        + " openRtbPodded=" + plan.isOpenRtbPodded()
                        + " podType=" + plan.getOpenRtbPodType());
                return plan;
            }
            SDKLogger.w(TAG, "Pod builder returned empty plan; falling back to root adm if present");
        }

        return resolveFromRootAdm(response.getAdm());
    }

    static VideoPlaybackPlan resolveFromRootAdm(String adm) {
        if (adm == null || adm.trim().isEmpty()) {
            return VideoPlaybackPlan.empty();
        }
        return VideoPlaybackPlanBuilder.build(adm);
    }

    /**
     * Uses the pod builder when there are multiple bid markups or explicit OpenRTB pod metadata.
     */
    static boolean shouldUsePoddedBuilder(PoddedAdResponse podded) {
        if (podded == null || podded.isEmpty()) {
            return false;
        }
        return podded.isPodded() || podded.getMarkups().size() > 1;
    }
}
