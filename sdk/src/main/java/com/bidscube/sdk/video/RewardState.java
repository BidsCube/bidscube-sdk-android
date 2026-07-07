package com.bidscube.sdk.video;

import com.bidscube.sdk.ads.VideoAdFormat;
import com.bidscube.sdk.interfaces.AdCallback;
import com.bidscube.sdk.utils.SDKLogger;

/**
 * Ensures {@link AdCallback#onUserRewarded(String)} fires at most once when eligible.
 */
public final class RewardState {

    private static final String TAG = "RewardState";

    private boolean rewardSent;
    private boolean podHadSkip;
    private boolean introSkipped;
    private boolean playableCompleted;

    public void markPodSlotSkipped() {
        podHadSkip = true;
    }

    public void markIntroSkipped() {
        introSkipped = true;
    }

    public void markPlayableCompleted() {
        playableCompleted = true;
    }

    public void maybeRewardStandardComplete(VideoAdFormat format, AdCallback callback, String placementId) {
        if (format != VideoAdFormat.REWARDED) {
            return;
        }
        sendReward(callback, placementId, "standard_complete");
    }

    public void maybeRewardPodComplete(VideoAdFormat format, AdCallback callback, String placementId) {
        if (format != VideoAdFormat.REWARDED) {
            return;
        }
        if (podHadSkip) {
            SDKLogger.d(TAG, "Pod reward withheld — slot was skipped");
            return;
        }
        sendReward(callback, placementId, "pod_complete");
    }

    public void maybeRewardGamifiedComplete(
            VideoAdFormat format,
            AdCallback callback,
            String placementId,
            boolean introCompleted) {
        if (format != VideoAdFormat.REWARDED) {
            return;
        }
        if (introSkipped || !introCompleted) {
            SDKLogger.d(TAG, "Gamified reward withheld — intro not completed");
            return;
        }
        if (!playableCompleted) {
            SDKLogger.d(TAG, "Gamified reward withheld — playable not completed");
            return;
        }
        sendReward(callback, placementId, "gamified_complete");
    }

    private void sendReward(AdCallback callback, String placementId, String reason) {
        if (rewardSent || callback == null) {
            return;
        }
        rewardSent = true;
        SDKLogger.d(TAG, "User rewarded placement=" + placementId + " reason=" + reason);
        callback.onUserRewarded(placementId);
    }

    public boolean isRewardSent() {
        return rewardSent;
    }
}
