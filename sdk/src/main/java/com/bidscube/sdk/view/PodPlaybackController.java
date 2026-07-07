package com.bidscube.sdk.view;

import com.bidscube.sdk.models.video.VideoAdSlot;
import com.bidscube.sdk.models.video.VideoPlaybackPlan;
import com.bidscube.sdk.models.video.VideoPlaybackState;
import com.bidscube.sdk.utils.SDKLogger;
import com.bidscube.sdk.utils.VastVideoTracker;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Plays a queue of {@link VideoAdSlot} items (single ad or pod) with explicit state transitions.
 */
public final class PodPlaybackController {

    private static final String TAG = "PodPlaybackController";

    public interface Listener {
        void onRequestPlaySlot(VideoAdSlot slot, int adIndex, int totalAds);

        void onSlotStarted(int adIndex, int totalAds, String adId);

        void onSlotCompleted(int adIndex, int totalAds, String adId);

        void onSlotSkipped(int adIndex, int totalAds, String adId);

        void onPodCompleted(boolean anyVideoPlayed, boolean lastActionWasSkip);

        void onPodFailed(String message);
    }

    private final AtomicReference<VideoPlaybackState> state =
            new AtomicReference<>(VideoPlaybackState.IDLE);

    private VideoPlaybackPlan plan;
    private Listener listener;
    private int currentIndex;
    private int clipsPlayedSuccessfully;
    private int clipErrors;
    private boolean lastActionWasSkip;
    private boolean destroyed;
    private boolean continueOnSlotError = true;
    private VideoAdSlot activeSlot;
    private boolean firstQuartileFired;
    private boolean midpointFired;
    private boolean thirdQuartileFired;

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public VideoPlaybackState getState() {
        return state.get();
    }

    public VideoAdSlot getActiveSlot() {
        return activeSlot;
    }

    public int getCurrentAdIndex() {
        return currentIndex + 1;
    }

    public int getTotalAds() {
        return plan != null ? plan.getTotalAds() : 0;
    }

    public void start(VideoPlaybackPlan playbackPlan) {
        if (destroyed) {
            return;
        }
        if (playbackPlan == null || playbackPlan.isEmpty()) {
            transition(VideoPlaybackState.ERROR);
            notifyFailed("No playable ads in VAST");
            return;
        }
        transition(VideoPlaybackState.LOADING_VAST);
        this.plan = playbackPlan;
        this.currentIndex = 0;
        this.clipsPlayedSuccessfully = 0;
        this.clipErrors = 0;
        this.lastActionWasSkip = false;
        transition(VideoPlaybackState.READY);
        SDKLogger.d(TAG, "Starting playback plan slots=" + playbackPlan.getTotalAds()
                + " isPod=" + playbackPlan.isPod());
        playCurrentSlot();
    }

    public void onSlotPrepared() {
        if (destroyed || activeSlot == null) {
            return;
        }
        transition(VideoPlaybackState.PLAYING_VIDEO);
        VastVideoTracker.fireStart(activeSlot);
        int adIndex = currentIndex + 1;
        if (listener != null) {
            listener.onSlotStarted(adIndex, plan.getTotalAds(), activeSlot.getAdId());
        }
    }

    public void onSlotPlaybackSuccess() {
        if (destroyed) {
            return;
        }
        clipsPlayedSuccessfully++;
    }

    public void onSlotCompleted() {
        if (destroyed || activeSlot == null) {
            return;
        }
        VastVideoTracker.fireComplete(activeSlot);
        int adIndex = currentIndex + 1;
        if (listener != null) {
            listener.onSlotCompleted(adIndex, plan.getTotalAds(), activeSlot.getAdId());
        }
        transition(VideoPlaybackState.VIDEO_COMPLETED);
        advance(false);
    }

    public void onSlotSkippedByUser() {
        if (destroyed || activeSlot == null) {
            return;
        }
        lastActionWasSkip = true;
        VastVideoTracker.fireSkip(activeSlot);
        int adIndex = currentIndex + 1;
        if (listener != null) {
            listener.onSlotSkipped(adIndex, plan.getTotalAds(), activeSlot.getAdId());
        }
        transition(VideoPlaybackState.VIDEO_SKIPPED);
        advance(true);
    }

    public void onSlotError(String message) {
        if (destroyed || activeSlot == null) {
            return;
        }
        clipErrors++;
        VastVideoTracker.fireError(activeSlot, message != null ? message : "playback_error");
        SDKLogger.e(TAG, "Slot error index=" + currentIndex + " adId=" + activeSlot.getAdId()
                + " msg=" + message);
        if (!continueOnSlotError) {
            transition(VideoPlaybackState.ERROR);
            notifyFailed("Pod stopped due to slot error: " + message);
            return;
        }
        advance(false);
    }

    public void setContinueOnSlotError(boolean continueOnSlotError) {
        this.continueOnSlotError = continueOnSlotError;
    }

    public void onQuartileProgress(float progress) {
        if (destroyed || activeSlot == null || progress < 0f) {
            return;
        }
        if (!firstQuartileFired && progress >= 0.25f) {
            firstQuartileFired = true;
            VastVideoTracker.fireFirstQuartile(activeSlot);
        }
        if (!midpointFired && progress >= 0.5f) {
            midpointFired = true;
            VastVideoTracker.fireMidpoint(activeSlot);
        }
        if (!thirdQuartileFired && progress >= 0.75f) {
            thirdQuartileFired = true;
            VastVideoTracker.fireThirdQuartile(activeSlot);
        }
    }

    public void onSlotClicked() {
        if (activeSlot != null) {
            VastVideoTracker.fireClick(activeSlot);
        }
    }

    public void destroy() {
        destroyed = true;
        transition(VideoPlaybackState.DESTROYED);
        listener = null;
        activeSlot = null;
    }

    private void playCurrentSlot() {
        if (destroyed || plan == null) {
            return;
        }
        if (currentIndex >= plan.getTotalAds()) {
            finishPod();
            return;
        }
        activeSlot = plan.getSlots().get(currentIndex);
        firstQuartileFired = false;
        midpointFired = false;
        thirdQuartileFired = false;
        VastVideoTracker.fireImpression(activeSlot);
        if (currentIndex > 0) {
            transition(VideoPlaybackState.PLAYING_NEXT_POD_VIDEO);
        } else {
            transition(VideoPlaybackState.SHOWING_FULLSCREEN);
        }
        SDKLogger.d(TAG, "Playing slot " + (currentIndex + 1) + "/" + plan.getTotalAds()
                + " adId=" + activeSlot.getAdId()
                + " url=" + activeSlot.getMediaFile().getUrl());
        if (listener != null) {
            listener.onRequestPlaySlot(activeSlot, currentIndex + 1, plan.getTotalAds());
        }
    }

    private void advance(boolean userSkipped) {
        currentIndex++;
        activeSlot = null;
        if (currentIndex >= plan.getTotalAds()) {
            finishPod();
        } else {
            playCurrentSlot();
        }
    }

    private void finishPod() {
        if (clipsPlayedSuccessfully == 0 && clipErrors > 0) {
            transition(VideoPlaybackState.ERROR);
            notifyFailed("All ad pod clips failed to load");
            return;
        }
        transition(VideoPlaybackState.VIDEO_COMPLETED);
        if (listener != null) {
            listener.onPodCompleted(clipsPlayedSuccessfully > 0, lastActionWasSkip);
        }
    }

    private void notifyFailed(String message) {
        if (listener != null) {
            listener.onPodFailed(message);
        }
    }

    private void transition(VideoPlaybackState next) {
        VideoPlaybackState prev = state.getAndSet(next);
        SDKLogger.d(TAG, "State " + prev + " -> " + next);
    }
}
