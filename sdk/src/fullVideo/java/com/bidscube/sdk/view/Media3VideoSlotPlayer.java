package com.bidscube.sdk.view;

import android.content.Context;
import android.os.Build;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;

import androidx.annotation.Nullable;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.mediacodec.MediaCodecInfo;
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector;
import androidx.media3.ui.AspectRatioFrameLayout;
import androidx.media3.ui.PlayerView;

import com.bidscube.sdk.R;
import com.bidscube.sdk.models.video.VideoAdSlot;
import com.bidscube.sdk.utils.SDKLogger;
import com.bidscube.sdk.video.VideoUiDefaults;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Media3/ExoPlayer implementation of {@link VideoSlotPlayer} for the fullVideo flavor.
 */
public final class Media3VideoSlotPlayer implements VideoSlotPlayer {

    private static final String TAG = "Media3VideoSlotPlayer";
    private static final long PROGRESS_INTERVAL_MS = 250L;

    private final ExoPlayer player;
    private final PlayerView playerView;
    @Nullable
    private Listener listener;
    @Nullable
    private VideoAdSlot currentSlot;
    private boolean skipped;
    private boolean completed;
    private boolean slotStartNotified;
    private boolean firstFrameNotified;
    private final Runnable progressRunnable = this::emitProgress;
    private final Runnable startStallRunnable = this::onStartStall;

    public Media3VideoSlotPlayer(Context context) {
        // Decoder reordering is isolated to emulator builds only (goldfish/ranchu).
        // Not applied on phones / physical TV — black-screen workaround was not confirmed there.
        if (isEmulatorBuild()) {
            DefaultRenderersFactory renderersFactory = new DefaultRenderersFactory(context)
                    .setEnableDecoderFallback(true)
                    .setMediaCodecSelector(preferNonEmulatorDecoders());
            player = new ExoPlayer.Builder(context, renderersFactory).build();
            SDKLogger.d(TAG, "Using emulator decoder preference (non-goldfish first)");
        } else {
            player = new ExoPlayer.Builder(context).build();
        }
        playerView = (PlayerView) LayoutInflater.from(context)
                .inflate(R.layout.bidscube_media3_player_view, null, false);
        playerView.setPlayer(player);
        playerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT);
        playerView.setKeepContentOnPlayerReset(true);
        // Keep D-pad focus on Skip/Close overlays, not the player surface.
        playerView.setFocusable(false);
        playerView.setFocusableInTouchMode(false);
        playerView.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        player.addListener(new Player.Listener() {
            @Override
            public void onIsPlayingChanged(boolean isPlaying) {
                if (!isPlaying || currentSlot == null || slotStartNotified) {
                    return;
                }
                slotStartNotified = true;
                playerView.removeCallbacks(startStallRunnable);
                if (listener != null) {
                    listener.onPrepared(currentSlot);
                    listener.onStarted(currentSlot);
                }
                playerView.removeCallbacks(progressRunnable);
                playerView.post(progressRunnable);
            }

            @Override
            public void onRenderedFirstFrame() {
                SDKLogger.d(TAG, "Rendered first frame position=" + player.getCurrentPosition()
                        + " duration=" + player.getDuration()
                        + " videoSize=" + player.getVideoSize().width + "x" + player.getVideoSize().height);
                if (firstFrameNotified || currentSlot == null) {
                    return;
                }
                firstFrameNotified = true;
                if (listener != null) {
                    listener.onFirstFrame(currentSlot);
                }
            }

            @Override
            public void onPlaybackStateChanged(int playbackState) {
                if (playbackState == Player.STATE_ENDED && !skipped && !completed) {
                    completed = true;
                    playerView.removeCallbacks(progressRunnable);
                    SDKLogger.d(TAG, "STATE_ENDED position=" + player.getCurrentPosition()
                            + " duration=" + player.getDuration());
                    if (listener != null && currentSlot != null) {
                        listener.onCompleted(currentSlot);
                    }
                }
            }

            @Override
            public void onPlayerError(PlaybackException error) {
                playerView.removeCallbacks(progressRunnable);
                SDKLogger.e(TAG, "Player error: " + error.getErrorCodeName() + " " + error.getMessage());
                if (listener != null && currentSlot != null) {
                    listener.onError(currentSlot, error);
                }
            }
        });
    }

    private static boolean isEmulatorBuild() {
        String fingerprint = Build.FINGERPRINT != null ? Build.FINGERPRINT : "";
        String model = Build.MODEL != null ? Build.MODEL : "";
        String product = Build.PRODUCT != null ? Build.PRODUCT : "";
        String hardware = Build.HARDWARE != null ? Build.HARDWARE : "";
        String lower = (fingerprint + " " + model + " " + product + " " + hardware).toLowerCase(Locale.US);
        return lower.contains("generic")
                || lower.contains("emulator")
                || lower.contains("goldfish")
                || lower.contains("ranchu")
                || lower.contains("sdk_google")
                || lower.contains("sdk_gphone");
    }

    /**
     * Emulator-only: goldfish H.264 can decode without painting frames. Prefer other codecs first.
     */
    private static MediaCodecSelector preferNonEmulatorDecoders() {
        return (mimeType, requiresSecureDecoder, requiresTunnelingDecoder) -> {
            List<MediaCodecInfo> decoderInfos = MediaCodecSelector.DEFAULT.getDecoderInfos(
                    mimeType, requiresSecureDecoder, requiresTunnelingDecoder);
            List<MediaCodecInfo> preferred = new ArrayList<>(decoderInfos.size());
            List<MediaCodecInfo> emulator = new ArrayList<>();
            for (MediaCodecInfo info : decoderInfos) {
                String name = info.name != null ? info.name.toLowerCase(Locale.US) : "";
                if (name.contains("goldfish") || name.contains("ranchu")) {
                    emulator.add(info);
                } else {
                    preferred.add(info);
                }
            }
            preferred.addAll(emulator);
            if (!preferred.isEmpty()) {
                SDKLogger.d(TAG, "Decoder order for " + mimeType + ": first=" + preferred.get(0).name
                        + " count=" + preferred.size());
            }
            return preferred;
        };
    }

    @Override
    public View getView() {
        return playerView;
    }

    @Override
    public void attach(ViewGroup container) {
        if (playerView.getParent() instanceof ViewGroup) {
            ((ViewGroup) playerView.getParent()).removeView(playerView);
        }
        container.addView(playerView, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
    }

    @Override
    public void play(VideoAdSlot slot) {
        if (slot == null || slot.getMediaFile() == null) {
            if (listener != null) {
                listener.onError(slot, new IllegalArgumentException("Missing media file"));
            }
            return;
        }
        skipped = false;
        completed = false;
        slotStartNotified = false;
        firstFrameNotified = false;
        currentSlot = slot;
        playerView.removeCallbacks(progressRunnable);
        playerView.removeCallbacks(startStallRunnable);
        runWhenViewReady(() -> startPlayback(slot));
    }

    @Override
    public void pause() {
        player.setPlayWhenReady(false);
    }

    @Override
    public void resume() {
        player.setPlayWhenReady(true);
    }

    @Override
    public void skip() {
        if (skipped || completed) {
            return;
        }
        skipped = true;
        playerView.removeCallbacks(progressRunnable);
        playerView.removeCallbacks(startStallRunnable);
        player.stop();
        if (listener != null && currentSlot != null) {
            listener.onSkipped(currentSlot);
        }
    }

    @Override
    public void stop() {
        playerView.removeCallbacks(progressRunnable);
        playerView.removeCallbacks(startStallRunnable);
        player.stop();
        slotStartNotified = false;
    }

    @Override
    public void release() {
        playerView.removeCallbacks(progressRunnable);
        playerView.removeCallbacks(startStallRunnable);
        playerView.setPlayer(null);
        player.release();
        listener = null;
        currentSlot = null;
    }

    @Override
    public void setMuted(boolean muted) {
        player.setVolume(muted ? 0f : 1f);
    }

    @Override
    public void setListener(Listener listener) {
        this.listener = listener;
    }

    private void startPlayback(VideoAdSlot slot) {
        player.stop();
        player.clearMediaItems();
        player.setMediaItem(MediaItem.fromUri(slot.getMediaFile().getUrl()));
        player.prepare();
        player.setPlayWhenReady(true);
        SDKLogger.d(TAG, "Playing slot url=" + slot.getMediaFile().getUrl());
        // If playing never begins, non-cancelable interstitial would trap the user with no Skip.
        playerView.removeCallbacks(startStallRunnable);
        playerView.postDelayed(startStallRunnable, VideoUiDefaults.PLAYBACK_START_STALL_MS);
    }

    private void onStartStall() {
        if (slotStartNotified || skipped || completed || currentSlot == null) {
            return;
        }
        SDKLogger.e(TAG, "Playback start stall after "
                + VideoUiDefaults.PLAYBACK_START_STALL_MS + "ms — failing session");
        if (listener != null) {
            listener.onError(currentSlot,
                    new IllegalStateException("playback_start_stall_no_playing_state"));
        }
    }

    private void runWhenViewReady(Runnable action) {
        if (playerView.getWidth() > 0 && playerView.getHeight() > 0) {
            action.run();
            return;
        }
        playerView.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                if (playerView.getWidth() > 0 && playerView.getHeight() > 0) {
                    playerView.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                    action.run();
                }
            }
        });
    }

    private void emitProgress() {
        if (currentSlot == null || listener == null || skipped || completed) {
            return;
        }
        long duration = player.getDuration();
        long position = player.getCurrentPosition();
        if (duration > 0) {
            listener.onProgress(currentSlot, position, duration);
        }
        playerView.postDelayed(progressRunnable, PROGRESS_INTERVAL_MS);
    }
}
