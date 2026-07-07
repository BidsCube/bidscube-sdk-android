package com.bidscube.sdk.view;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;

import androidx.annotation.Nullable;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.AspectRatioFrameLayout;
import androidx.media3.ui.PlayerView;

import com.bidscube.sdk.R;
import com.bidscube.sdk.models.video.VideoAdSlot;
import com.bidscube.sdk.utils.SDKLogger;

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
    private final Runnable progressRunnable = this::emitProgress;

    public Media3VideoSlotPlayer(Context context) {
        player = new ExoPlayer.Builder(context).build();
        playerView = (PlayerView) LayoutInflater.from(context)
                .inflate(R.layout.bidscube_media3_player_view, null, false);
        playerView.setPlayer(player);
        playerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT);
        playerView.setKeepContentOnPlayerReset(true);
        player.addListener(new Player.Listener() {
            @Override
            public void onIsPlayingChanged(boolean isPlaying) {
                if (!isPlaying || currentSlot == null || slotStartNotified) {
                    return;
                }
                slotStartNotified = true;
                if (listener != null) {
                    listener.onPrepared(currentSlot);
                    listener.onStarted(currentSlot);
                }
                playerView.removeCallbacks(progressRunnable);
                playerView.post(progressRunnable);
            }

            @Override
            public void onPlaybackStateChanged(int playbackState) {
                if (playbackState == Player.STATE_ENDED && !skipped && !completed) {
                    completed = true;
                    playerView.removeCallbacks(progressRunnable);
                    if (listener != null && currentSlot != null) {
                        listener.onCompleted(currentSlot);
                    }
                }
            }

            @Override
            public void onPlayerError(PlaybackException error) {
                playerView.removeCallbacks(progressRunnable);
                if (listener != null && currentSlot != null) {
                    listener.onError(currentSlot, error);
                }
            }
        });
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
        currentSlot = slot;
        playerView.removeCallbacks(progressRunnable);
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
        player.stop();
        if (listener != null && currentSlot != null) {
            listener.onSkipped(currentSlot);
        }
    }

    @Override
    public void stop() {
        playerView.removeCallbacks(progressRunnable);
        player.stop();
        slotStartNotified = false;
    }

    @Override
    public void release() {
        playerView.removeCallbacks(progressRunnable);
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
