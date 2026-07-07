package com.bidscube.sdk.video;

import com.bidscube.sdk.ads.VideoAdFormat;
import com.bidscube.sdk.interfaces.AdCallback;

import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RewardStateTest {

    private RewardState rewardState;
    private AtomicInteger rewardCount;

    @Before
    public void setUp() {
        rewardState = new RewardState();
        rewardCount = new AtomicInteger();
    }

    @Test
    public void standardRewarded_rewardsOnComplete() {
        AdCallback callback = rewardCallback();
        rewardState.maybeRewardStandardComplete(VideoAdFormat.REWARDED, callback, "p1");
        assertEquals(1, rewardCount.get());
    }

    @Test
    public void standardRewarded_noRewardForInterstitial() {
        AdCallback callback = rewardCallback();
        rewardState.maybeRewardStandardComplete(VideoAdFormat.INTERSTITIAL, callback, "p1");
        assertEquals(0, rewardCount.get());
    }

    @Test
    public void podReward_withheldAfterSkip() {
        AdCallback callback = rewardCallback();
        rewardState.markPodSlotSkipped();
        rewardState.maybeRewardPodComplete(VideoAdFormat.REWARDED, callback, "p1");
        assertEquals(0, rewardCount.get());
    }

    @Test
    public void podReward_grantedWhenNoSkip() {
        AdCallback callback = rewardCallback();
        rewardState.maybeRewardPodComplete(VideoAdFormat.REWARDED, callback, "p1");
        assertEquals(1, rewardCount.get());
        assertTrue(rewardState.isRewardSent());
    }

    @Test
    public void gamifiedReward_requiresIntroAndPlayable() {
        AdCallback callback = rewardCallback();
        rewardState.markPlayableCompleted();
        rewardState.maybeRewardGamifiedComplete(VideoAdFormat.REWARDED, callback, "p1", false);
        assertEquals(0, rewardCount.get());

        rewardState.maybeRewardGamifiedComplete(VideoAdFormat.REWARDED, callback, "p1", true);
        assertEquals(1, rewardCount.get());
    }

    @Test
    public void rewardSentOnlyOnce() {
        AdCallback callback = rewardCallback();
        rewardState.maybeRewardStandardComplete(VideoAdFormat.REWARDED, callback, "p1");
        rewardState.maybeRewardStandardComplete(VideoAdFormat.REWARDED, callback, "p1");
        assertEquals(1, rewardCount.get());
    }

    private AdCallback rewardCallback() {
        return new AdCallback() {
            @Override
            public void onAdLoading(String placementId) {
            }

            @Override
            public void onAdLoaded(String placementId) {
            }

            @Override
            public void onAdDisplayed(String placementId) {
            }

            @Override
            public void onAdClicked(String placementId) {
            }

            @Override
            public void onAdClosed(String placementId) {
            }

            @Override
            public void onAdFailed(String placementId, int errorCode, String errorMessage) {
            }

            @Override
            public void onUserRewarded(String placementId) {
                rewardCount.incrementAndGet();
            }
        };
    }
}
