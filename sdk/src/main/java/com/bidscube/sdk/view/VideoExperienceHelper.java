package com.bidscube.sdk.view;

import android.app.Activity;
import android.app.Dialog;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import com.bidscube.sdk.ads.VideoAdFormat;
import com.bidscube.sdk.config.VideoPlayerProvider;
import com.bidscube.sdk.interfaces.AdCallback;
import com.bidscube.sdk.models.enums.AdPosition;
import com.bidscube.sdk.models.PlayableAdConfig;
import com.bidscube.sdk.models.video.GamifiedEndCardConfig;
import com.bidscube.sdk.models.video.VideoAdSlot;
import com.bidscube.sdk.models.video.VideoPlaybackPlan;
import com.bidscube.sdk.models.video.VideoPlaybackPlanType;
import com.bidscube.sdk.openrtb.PodDurationValidator;
import com.bidscube.sdk.openrtb.PoddedPlaybackPlanBuilder;
import com.bidscube.sdk.openrtb.VideoPodConfig;
import com.bidscube.sdk.utils.SDKLogger;
import com.bidscube.sdk.utils.VastParser;
import com.bidscube.sdk.utils.VideoPlaybackPlanBuilder;
import com.bidscube.sdk.video.RewardState;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Orchestrates advanced video experiences:
 * <ul>
 *   <li>VAST ad pod — multiple short clips in one placement ("Ad X of Y")</li>
 *   <li>Gamified triple-page — intro video → playable mini-game → optional end card</li>
 * </ul>
 */
public final class VideoExperienceHelper {

    private static final String TAG = "VideoExperienceHelper";

    private enum SessionEnd { NONE, COMPLETED, SKIPPED, FAILED }

    private VideoExperienceHelper() {
    }

    /**
     * Shows a VAST ad pod (sequential short videos) in a fullscreen dialog.
     */
    public static void showAdPodInDialog(
            Activity activity,
            String placementId,
            String adm,
            String clickUrl,
            AdPosition position,
            VideoAdFormat format,
            AdCallback callback,
            VideoPlayerProvider videoPlayerProvider) {

        VideoPlaybackPlan plan = VideoPlaybackPlanBuilder.build(adm);
        SDKLogger.d(TAG, "Ad pod plan type=" + plan.getType() + " ads=" + plan.getTotalAds());
        if (plan.isEmpty() || !plan.isPodPlayback()) {
            callback.onAdFailed(placementId, -1, "Not a VAST ad pod");
            return;
        }
        showAdPodWithPlan(activity, placementId, plan, clickUrl, position, format, callback, videoPlayerProvider);
    }

    /**
     * Shows a pre-built pod playback plan (VAST or OpenRTB-enriched) in a fullscreen dialog.
     */
    public static void showAdPodWithPlan(
            Activity activity,
            String placementId,
            VideoPlaybackPlan plan,
            String clickUrl,
            AdPosition position,
            VideoAdFormat format,
            AdCallback callback,
            VideoPlayerProvider videoPlayerProvider) {
        showAdPodWithPlan(activity, placementId, plan, clickUrl, position, format, callback,
                videoPlayerProvider, VideoPodConfig.defaults());
    }

    /**
     * Shows a pre-built pod playback plan (VAST or OpenRTB-enriched) in a fullscreen dialog.
     */
    public static void showAdPodWithPlan(
            Activity activity,
            String placementId,
            VideoPlaybackPlan plan,
            String clickUrl,
            AdPosition position,
            VideoAdFormat format,
            AdCallback callback,
            VideoPlayerProvider videoPlayerProvider,
            VideoPodConfig podConfig) {

        if (plan == null || plan.isEmpty() || !plan.isPodPlayback()) {
            callback.onAdFailed(placementId, -1, "Not a playable ad pod");
            return;
        }

        VideoPodConfig effectivePodConfig = podConfig != null ? podConfig : VideoPodConfig.defaults();
        PodDurationValidator.Result durationResult = PodDurationValidator.validatePod(
                plan.getOpenRtbPodContext(),
                plan.getSlots(),
                effectivePodConfig.getDurationValidationMode());
        if (plan.isOpenRtbPodded()) {
            PoddedPlaybackPlanBuilder.fireDurationMismatchIfNeeded(
                    callback, placementId, plan, durationResult);
        }

        AtomicReference<SessionEnd> sessionEnd = new AtomicReference<>(SessionEnd.NONE);
        AtomicBoolean adClosedEmitted = new AtomicBoolean(false);
        AtomicBoolean endCardShown = new AtomicBoolean(false);
        AtomicBoolean anyVideoPlayed = new AtomicBoolean(false);
        AtomicBoolean legacyCompletedEmitted = new AtomicBoolean(false);
        AtomicBoolean legacySkippedEmitted = new AtomicBoolean(false);
        AtomicBoolean legacyStartedEmitted = new AtomicBoolean(false);
        AtomicBoolean podStartedEmitted = new AtomicBoolean(false);
        RewardState rewardState = new RewardState();

        String podId = plan.getOpenRtbPodContext() != null
                ? plan.getOpenRtbPodContext().getPodId()
                : null;
        long expectedPodMs = plan.getExpectedPodDurationMs() != null
                ? plan.getExpectedPodDurationMs()
                : (plan.getTotalDurationMs() != null ? plan.getTotalDurationMs() : 0L);

        Runnable emitAdClosed = () -> {
            if (adClosedEmitted.compareAndSet(false, true)) {
                callback.onAdClosed(placementId);
            }
        };

        GamifiedEndCardConfig endCardConfig = GamifiedEndCardConfig.fromPreview(
                plan.getEndCardPreview(),
                "Install Now",
                firstNonEmpty(
                        plan.getEndCardPreview() != null ? plan.getEndCardPreview().getClickUrl() : null,
                        clickUrl));

        Dialog dialog = createDialog(activity, position);
        FrameLayout container = new FrameLayout(activity);
        container.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        dialog.setContentView(container);

        VideoInterstitialOverlay overlay = VideoInterstitialOverlay.attach(container);

        VastAdPodPlayer podPlayer = new VastAdPodPlayer(
                activity, clickUrl, videoPlayerProvider, effectivePodConfig.getSkipPolicy());
        podPlayer.getController().setContinueOnSlotError(effectivePodConfig.isContinueOnSlotError());
        podPlayer.setShowPodCounter(effectivePodConfig.isShowPodCounter());
        podPlayer.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        container.addView(podPlayer.asViewGroup(), 0);

        GamifiedEndCard gamifiedEndCard = new DefaultGamifiedEndCard(activity, container, podPlayer, overlay);

        Runnable showEndCard = () -> {
            if (!endCardShown.compareAndSet(false, true)) {
                return;
            }
            if (!anyVideoPlayed.get()) {
                SDKLogger.d(TAG, "Skipping end card — no video played successfully");
                dialog.dismiss();
                return;
            }
            if (!endCardConfig.hasPreviewImage()) {
                SDKLogger.d(TAG, "Skipping end card — no companion preview in VAST");
                try {
                    podPlayer.release();
                } catch (Throwable ignored) {
                }
                dialog.dismiss();
                return;
            }
            overlay.hide();
            gamifiedEndCard.show(endCardConfig, new GamifiedEndCard.Listener() {
                @Override
                public void onEndCardShown() {
                    callback.onEndCardShown(placementId);
                }

                @Override
                public void onCtaClicked(String ctaClickUrl) {
                    callback.onAdClicked(placementId);
                    callback.onInstallButtonClicked(placementId, endCardConfig.getCtaText());
                    openClickUrl(activity, ctaClickUrl);
                }

                @Override
                public void onCloseRequested() {
                    dialog.dismiss();
                }
            });
        };

        overlay.setListener(new VideoInterstitialOverlay.Listener() {
            @Override
            public void onSkipRequested() {
                podPlayer.skipVideo();
            }

            @Override
            public void onCloseRequested() {
                dialog.dismiss();
            }
        });

        boolean openRtbPodded = plan.isOpenRtbPodded();

        podPlayer.setPodProgressListener((current, total, slot) -> {
            SDKLogger.d(TAG, "Pod ad " + current + "/" + total + " adId=" + slot.getAdId()
                    + " openRtbSlot=" + slot.getOpenRtbSlotInPod()
                    + " ordering=" + slot.getOrderingSource());
            anyVideoPlayed.set(true);
            if (openRtbPodded && podStartedEmitted.compareAndSet(false, true)) {
                callback.onVideoPodStarted(placementId, podId, total, expectedPodMs);
            }
            if (legacyStartedEmitted.compareAndSet(false, true)) {
                callback.onVideoAdStarted(placementId);
            }
            if (openRtbPodded) {
                int slotInPod = slot.getOpenRtbSlotInPod() != null ? slot.getOpenRtbSlotInPod() : current;
                callback.onVideoPodSlotStarted(
                        placementId, podId, slot.getOpenRtbImpId(), slotInPod, total, slot.getAdId());
            }
            callback.onVideoAdStarted(placementId, current, total, slot.getAdId());
            int clipSkip = slot.getSkipOffsetSeconds() > 0 ? slot.getSkipOffsetSeconds() : 3;
            activity.runOnUiThread(() -> overlay.startSkipCountdown(clipSkip));
        });

        podPlayer.setSlotLifecycleListener(new VastAdPodPlayer.SlotLifecycleListener() {
            @Override
            public void onSlotCompleted(int adIndex, int total, String adId) {
                if (openRtbPodded) {
                    VideoAdSlot slot = findSlotByIndex(plan, adIndex);
                    int slotInPod = slot != null && slot.getOpenRtbSlotInPod() != null
                            ? slot.getOpenRtbSlotInPod()
                            : adIndex;
                    String impId = slot != null ? slot.getOpenRtbImpId() : null;
                    callback.onVideoPodSlotCompleted(placementId, podId, impId, slotInPod, total, adId);
                }
                callback.onVideoAdCompleted(placementId, adIndex, total, adId);
            }

            @Override
            public void onSlotSkipped(int adIndex, int total, String adId) {
                rewardState.markPodSlotSkipped();
                callback.onVideoAdSkipped(placementId, adIndex, total, adId);
            }
        });

        podPlayer.setOnVideoCompletionListener(new BidscubeVideoAdPlayer.VideoCompletionListener() {
            @Override
            public void onVideoCompleted() {
                sessionEnd.compareAndSet(SessionEnd.NONE, SessionEnd.COMPLETED);
                int total = plan.getTotalAds();
                if (openRtbPodded) {
                    callback.onVideoPodCompleted(placementId, total);
                }
                if (legacyCompletedEmitted.compareAndSet(false, true)) {
                    callback.onVideoAdCompleted(placementId);
                }
                rewardState.maybeRewardPodComplete(format, callback, placementId);
                activity.runOnUiThread(showEndCard);
            }

            @Override
            public void onVideoSkipped() {
                sessionEnd.compareAndSet(SessionEnd.NONE, SessionEnd.SKIPPED);
                int total = plan.getTotalAds();
                if (openRtbPodded) {
                    callback.onVideoPodCompleted(placementId, total);
                }
                if (legacySkippedEmitted.compareAndSet(false, true)) {
                    callback.onVideoAdSkipped(placementId);
                }
                activity.runOnUiThread(showEndCard);
            }

            @Override
            public void onVideoStarted() {
                // Legacy onVideoAdStarted is emitted once from podProgressListener.
            }

            @Override
            public void onVideoClicked() {
                callback.onAdClicked(placementId);
            }

            @Override
            public void onVideoError(String message) {
                sessionEnd.compareAndSet(SessionEnd.NONE, SessionEnd.FAILED);
                callback.onAdFailed(placementId, -1, message != null ? message : "ad pod error");
                dialog.dismiss();
            }
        });

        dialog.setOnShowListener(d -> {
            callback.onAdDisplayed(placementId);
            podPlayer.post(() -> podPlayer.playPlan(plan));
        });

        dialog.setOnDismissListener(d -> {
            if (sessionEnd.get() == SessionEnd.NONE) {
                sessionEnd.set(SessionEnd.SKIPPED);
                callback.onVideoAdSkipped(placementId);
            }
            gamifiedEndCard.destroy();
            cleanupOverlay(podPlayer, overlay, null);
            emitAdClosed.run();
        });

        dialog.show();
        if (position == AdPosition.FULL_SCREEN) {
            configureFullscreen(dialog);
        }
        SDKLogger.d(TAG, "Ad pod dialog shown, clips=" + plan.getTotalAds()
                + " openRtbPodded=" + plan.isOpenRtbPodded()
                + " podType=" + plan.getOpenRtbPodType());
    }

    private static VideoAdSlot findSlotByIndex(VideoPlaybackPlan plan, int adIndex) {
        if (plan == null || adIndex < 1 || adIndex > plan.getTotalAds()) {
            return null;
        }
        return plan.getSlots().get(adIndex - 1);
    }

    /**
     * Gamified experience: intro video → playable mini-game → end card (if companion in VAST).
     */
    public static void showGamifiedInDialog(
            Activity activity,
            String placementId,
            String adm,
            String clickUrl,
            AdPosition position,
            VideoAdFormat format,
            AdCallback callback,
            VideoPlayerProvider videoPlayerProvider) {

        PlayableAdConfig playableConfig = VastParser.parsePlayableConfig(adm);
        if (playableConfig == null) {
            callback.onAdFailed(placementId, -1, "Missing bidscubePlayable extension");
            return;
        }

        AtomicReference<SessionEnd> sessionEnd = new AtomicReference<>(SessionEnd.NONE);
        AtomicBoolean adClosedEmitted = new AtomicBoolean(false);
        AtomicBoolean endCardShown = new AtomicBoolean(false);
        AtomicBoolean playableStarted = new AtomicBoolean(false);
        AtomicBoolean introCompleted = new AtomicBoolean(false);
        RewardState rewardState = new RewardState();

        Runnable emitAdClosed = () -> {
            if (adClosedEmitted.compareAndSet(false, true)) {
                callback.onAdClosed(placementId);
            }
        };

        String companionImage = VastParser.getCompanionImageUrl(adm);
        String endCardClick = firstNonEmpty(VastParser.getCompanionClickThroughUrl(adm), clickUrl);
        int skipOffset = VastParser.getSkipOffsetSeconds(adm);
        GamifiedEndCardConfig endCardConfig = GamifiedEndCardConfig.fromPreview(
                companionImage != null ? new com.bidscube.sdk.models.video.VastPreview(companionImage, endCardClick) : null,
                "Install Now",
                endCardClick);

        Dialog dialog = createDialog(activity, position);
        FrameLayout container = new FrameLayout(activity);
        container.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        dialog.setContentView(container);

        VideoInterstitialOverlay overlay = VideoInterstitialOverlay.attach(container);
        overlay.setCloseButtonDismissesAd(false);
        BidscubeVideoAdPlayer[] playerRef = new BidscubeVideoAdPlayer[1];
        Runnable[] stopPlayableRef = new Runnable[1];
        GamifiedEndCard[] endCardRef = new GamifiedEndCard[1];

        Runnable showEndCard = () -> {
            if (!endCardShown.compareAndSet(false, true)) {
                return;
            }
            BidscubeVideoAdPlayer player = playerRef[0];
            if (player == null && !endCardConfig.hasPreviewImage()) {
                dialog.dismiss();
                return;
            }
            overlay.hide();
            endCardRef[0] = new DefaultGamifiedEndCard(activity, container, player, overlay);
            if (!endCardConfig.hasPreviewImage()) {
                SDKLogger.d(TAG, "Skipping end card — no companion preview in VAST");
                if (player != null) {
                    try {
                        player.release();
                    } catch (Throwable ignored) {
                    }
                }
                dialog.dismiss();
                return;
            }
            endCardRef[0].show(endCardConfig, new GamifiedEndCard.Listener() {
                @Override
                public void onEndCardShown() {
                    callback.onEndCardShown(placementId);
                }

                @Override
                public void onCtaClicked(String ctaClickUrl) {
                    callback.onAdClicked(placementId);
                    callback.onInstallButtonClicked(placementId, endCardConfig.getCtaText());
                    openClickUrl(activity, ctaClickUrl);
                }

                @Override
                public void onCloseRequested() {
                    dialog.dismiss();
                }
            });
        };

        Runnable startPlayable = () -> {
            if (playableStarted.compareAndSet(false, true)) {
                activity.runOnUiThread(() -> {
                    overlay.hide();
                    if (playerRef[0] != null) {
                        try {
                            playerRef[0].release();
                        } catch (Throwable ignored) {
                        }
                        container.removeView(playerRef[0].asViewGroup());
                        playerRef[0] = null;
                    }
                    Runnable onPlayableDone = showEndCard;
                    PlayableMiniGameView.Listener gameListener = new PlayableMiniGameView.Listener() {
                        @Override
                        public void onPlayableStarted() {
                            SDKLogger.d(TAG, "Playable phase started");
                        }

                        @Override
                        public void onPlayableCompleted(int score) {
                            SDKLogger.d(TAG, "Playable completed score=" + score);
                            rewardState.markPlayableCompleted();
                            rewardState.maybeRewardGamifiedComplete(
                                    format, callback, placementId, introCompleted.get());
                            onPlayableDone.run();
                        }

                        @Override
                        public void onPlayableSkipped() {
                            SDKLogger.d(TAG, "Playable skipped");
                            onPlayableDone.run();
                        }
                    };

                    if (playableConfig.hasWebPlayable()) {
                        PlayableWebView webPlayable = new PlayableWebView(activity, playableConfig);
                        webPlayable.setListener(new PlayableWebView.Listener() {
                            @Override
                            public void onPlayableStarted() {
                                gameListener.onPlayableStarted();
                            }

                            @Override
                            public void onPlayableCompleted(int score) {
                                gameListener.onPlayableCompleted(score);
                            }

                            @Override
                            public void onPlayableSkipped() {
                                gameListener.onPlayableSkipped();
                            }
                        });
                        stopPlayableRef[0] = webPlayable::stop;
                        container.addView(webPlayable, new FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT));
                        webPlayable.start();
                    } else {
                        PlayableMiniGameView game = new PlayableMiniGameView(activity, playableConfig, gameListener);
                        stopPlayableRef[0] = game::stop;
                        container.addView(game, new FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT));
                        game.start();
                    }
                });
            }
        };

        overlay.setListener(new VideoInterstitialOverlay.Listener() {
            @Override
            public void onSkipRequested() {
                if (playerRef[0] != null) {
                    playerRef[0].skipVideo();
                } else if (stopPlayableRef[0] != null) {
                    stopPlayableRef[0].run();
                    showEndCard.run();
                }
            }

            @Override
            public void onCloseRequested() {
                if (playerRef[0] != null) {
                    playerRef[0].skipVideo();
                } else if (stopPlayableRef[0] != null) {
                    stopPlayableRef[0].run();
                    showEndCard.run();
                }
            }
        });

        dialog.setOnShowListener(d -> {
            callback.onAdDisplayed(placementId);
            BidscubeVideoAdPlayer player = VideoAdPlayerFactory.create(activity, adm, clickUrl, videoPlayerProvider);
            playerRef[0] = player;
            player.setLayoutParams(new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));
            container.addView(player.asViewGroup(), 0);

            player.setOnVideoCompletionListener(new BidscubeVideoAdPlayer.VideoCompletionListener() {
                @Override
                public void onVideoCompleted() {
                    sessionEnd.compareAndSet(SessionEnd.NONE, SessionEnd.COMPLETED);
                    introCompleted.set(true);
                    callback.onVideoAdCompleted(placementId);
                    startPlayable.run();
                }

                @Override
                public void onVideoSkipped() {
                    sessionEnd.compareAndSet(SessionEnd.NONE, SessionEnd.SKIPPED);
                    rewardState.markIntroSkipped();
                    callback.onVideoAdSkipped(placementId);
                    startPlayable.run();
                }

                @Override
                public void onVideoStarted() {
                    callback.onVideoAdStarted(placementId);
                    int countdown = skipOffset > 0 ? skipOffset : 5;
                    activity.runOnUiThread(() -> overlay.startSkipCountdown(countdown));
                }

                @Override
                public void onVideoClicked() {
                    callback.onAdClicked(placementId);
                }

                @Override
                public void onVideoError(String message) {
                    sessionEnd.compareAndSet(SessionEnd.NONE, SessionEnd.FAILED);
                    callback.onAdFailed(placementId, -1, message != null ? message : "video error");
                    dialog.dismiss();
                }
            });
            player.playVast(adm, false);
        });

        dialog.setOnDismissListener(d -> {
            if (sessionEnd.get() == SessionEnd.NONE) {
                sessionEnd.set(SessionEnd.SKIPPED);
                callback.onVideoAdSkipped(placementId);
            }
            if (endCardRef[0] != null) {
                endCardRef[0].destroy();
            }
            if (stopPlayableRef[0] != null) {
                stopPlayableRef[0].run();
            }
            cleanupOverlay(playerRef[0], overlay, null);
            emitAdClosed.run();
        });

        dialog.show();
        if (position == AdPosition.FULL_SCREEN) {
            configureFullscreen(dialog);
        }
        SDKLogger.d(TAG, "Gamified video dialog shown");
    }

    private static void showEndCardInternal(
            Activity activity,
            BidscubeVideoAdPlayer player,
            FrameLayout container,
            VideoInterstitialOverlay overlay,
            String imageUrl,
            String clickUrl,
            Runnable onEndCardShown,
            Runnable onEndCardClicked,
            Runnable onClose) {
        if (imageUrl == null || imageUrl.trim().isEmpty()) {
            SDKLogger.d(TAG, "Skipping end card — no companion preview in VAST");
            try {
                player.release();
            } catch (Throwable ignored) {
            }
            if (onClose != null) {
                onClose.run();
            }
            return;
        }
        VideoInterstitialUiHelper.showEndCard(
                activity,
                player,
                container,
                overlay,
                imageUrl,
                clickUrl,
                onEndCardShown,
                onEndCardClicked,
                onClose);
    }

    private static Dialog createDialog(Activity activity, AdPosition position) {
        if (position == AdPosition.FULL_SCREEN) {
            return new Dialog(activity, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        }
        return new Dialog(activity);
    }

    private static void configureFullscreen(Dialog dialog) {
        Window window = dialog.getWindow();
        if (window == null) {
            return;
        }
        window.setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);
        window.setGravity(Gravity.CENTER);
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        window.setFlags(
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED);
    }

    private static void cleanupOverlay(
            BidscubeVideoAdPlayer player,
            VideoInterstitialOverlay overlay,
            ImaNativeSkipBlocker blocker) {
        try {
            overlay.detach();
        } catch (Throwable ignored) {
        }
        if (blocker != null) {
            try {
                blocker.detach();
            } catch (Throwable ignored) {
            }
        }
        if (player != null) {
            try {
                player.release();
            } catch (Throwable ignored) {
            }
        }
    }

    private static void openClickUrl(Activity activity, String clickUrl) {
        if (clickUrl == null || clickUrl.trim().isEmpty()) {
            return;
        }
        try {
            android.content.Intent intent = new android.content.Intent(
                    android.content.Intent.ACTION_VIEW,
                    android.net.Uri.parse(clickUrl.trim()));
            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
            activity.startActivity(intent);
        } catch (Exception e) {
            SDKLogger.e(TAG, "Click-through failed: " + e.getMessage());
        }
    }

    private static String firstNonEmpty(String primary, String fallback) {
        if (primary != null && !primary.trim().isEmpty()) {
            return primary.trim();
        }
        if (fallback != null && !fallback.trim().isEmpty()) {
            return fallback.trim();
        }
        return null;
    }
}
