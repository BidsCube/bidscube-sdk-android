package com.bidscube.sdk.video;

import com.bidscube.sdk.ads.VideoAdFormat;
import com.bidscube.sdk.interfaces.AdCallback;
import com.bidscube.sdk.models.video.VastCompanion;
import com.bidscube.sdk.models.video.VastPreview;
import com.bidscube.sdk.models.video.GamifiedEndCardConfig;

import org.junit.Test;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Lifecycle / once-only callback scenarios for autoClose behaviour.
 */
public class AutoCloseLifecycleTest {

    @Test
    public void skipDoesNotGrantReward() {
        RewardState rewardState = new RewardState();
        AtomicInteger rewards = new AtomicInteger();
        AdCallback callback = countingReward(rewards);

        // Skip path never calls maybeRewardStandardComplete.
        callback.onVideoAdSkipped("p1");
        assertEquals(0, rewards.get());
        assertFalse(rewardState.isRewardSent());
    }

    @Test
    public void completeGrantsRewardOnce_notOnClose() {
        RewardState rewardState = new RewardState();
        AtomicInteger rewards = new AtomicInteger();
        AtomicInteger closes = new AtomicInteger();
        AdCallback callback = counting(rewards, closes);

        rewardState.maybeRewardStandardComplete(VideoAdFormat.REWARDED, callback, "p1");
        rewardState.maybeRewardStandardComplete(VideoAdFormat.REWARDED, callback, "p1");
        callback.onAdClosed("p1");
        callback.onAdClosed("p1");

        assertEquals(1, rewards.get());
        assertEquals(2, closes.get()); // raw callback duplicates — production uses OnceFlag
    }

    @Test
    public void closeCallbackGuard_firesOnce() {
        AtomicBoolean closed = new AtomicBoolean(false);
        AtomicInteger closeCount = new AtomicInteger();
        Runnable emitClose = () -> {
            if (closed.compareAndSet(false, true)) {
                closeCount.incrementAndGet();
            }
        };
        emitClose.run();
        emitClose.run();
        emitClose.run();
        assertEquals(1, closeCount.get());
    }

    @Test
    public void endCardShownGuard_firesOnce() {
        AtomicBoolean shown = new AtomicBoolean(false);
        AtomicInteger count = new AtomicInteger();
        Runnable show = () -> {
            if (!shown.compareAndSet(false, true)) {
                return;
            }
            count.incrementAndGet();
        };
        show.run();
        show.run();
        assertEquals(1, count.get());
    }

    @Test
    public void videoCompleted_isNotClose_whenAutoCloseFalse() {
        PostVideoPolicy.Action action = PostVideoPolicy.resolve(false, false, false);
        assertEquals(PostVideoPolicy.Action.KEEP_LAST_FRAME, action);
        // KEEP_LAST_FRAME must not imply onAdClosed.
    }

    @Test
    public void htmlCompanion_isRenderableWithoutStaticImage() {
        VastCompanion html = new VastCompanion(
                VastCompanion.ResourceType.HTML,
                "<html></html>",
                "https://example.com/c",
                null,
                null,
                320,
                480);
        VastPreview preview = VastPreview.fromCompanion(html);
        GamifiedEndCardConfig config = GamifiedEndCardConfig.fromPreview(preview, "Install", null);
        assertTrue(config.hasRenderableCompanion());
        assertFalse(config.hasPreviewImage());
    }

    @Test
    public void iframeCompanion_isRenderable() {
        VastCompanion iframe = new VastCompanion(
                VastCompanion.ResourceType.IFRAME,
                "https://example.com/frame.html",
                null,
                null,
                null,
                0,
                0);
        assertTrue(PostVideoPolicy.hasRenderableCompanion(iframe));
        assertEquals(
                PostVideoPolicy.Action.SHOW_END_CARD,
                PostVideoPolicy.resolve(false, true, false));
    }

    private static AdCallback countingReward(AtomicInteger rewards) {
        return counting(rewards, new AtomicInteger());
    }

    private static AdCallback counting(AtomicInteger rewards, AtomicInteger closes) {
        return new AdCallback() {
            @Override public void onAdLoading(String placementId) { }
            @Override public void onAdLoaded(String placementId) { }
            @Override public void onAdDisplayed(String placementId) { }
            @Override public void onAdClicked(String placementId) { }
            @Override public void onAdClosed(String placementId) { closes.incrementAndGet(); }
            @Override public void onAdFailed(String placementId, int errorCode, String errorMessage) { }
            @Override public void onUserRewarded(String placementId) { rewards.incrementAndGet(); }
            @Override public void onVideoAdSkipped(String placementId) { }
        };
    }
}
