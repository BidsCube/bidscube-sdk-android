package com.bidscube.sdk.view;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.VideoView;

import com.bidscube.sdk.config.VideoPlayerProvider;
import com.bidscube.sdk.utils.SDKLogger;
import com.bidscube.sdk.utils.VastParser;

/**
 * Plays inline progressive MP4 from VAST without Google IMA.
 */
public class NativeMp4VideoPlayer extends FrameLayout implements BidscubeVideoAdPlayer {

    private static final String TAG = "NativeMp4VideoPlayer";

    private final String mediaUrl;
    private final String redirectUrl;
    private final VideoView videoView;
    private VideoCompletionListener completionListener;
    private boolean skipped;

    public NativeMp4VideoPlayer(
            Context context,
            String mediaUrl,
            String redirectUrl,
            VideoPlayerProvider videoPlayerProvider) {
        super(context);
        this.mediaUrl = mediaUrl != null ? mediaUrl.trim() : null;
        this.redirectUrl = redirectUrl;

        videoView = videoPlayerProvider != null
                ? videoPlayerProvider.createVideoView(context)
                : new VideoView(context);

        FrameLayout.LayoutParams videoParams = new FrameLayout.LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT);
        videoParams.gravity = Gravity.CENTER;
        videoView.setLayoutParams(videoParams);
        setBackgroundColor(0xFF000000);
        addView(videoView);

        videoView.setOnClickListener(v -> openClickThrough());
    }

    @Override
    public void setLayoutParams(ViewGroup.LayoutParams params) {
        super.setLayoutParams(params);
    }

    @Override
    public void setOnVideoCompletionListener(VideoCompletionListener listener) {
        this.completionListener = listener;
    }

    @Override
    public void playVast(String vastTag, boolean isUrl) {
        skipped = false;
        String url = resolvePlaybackUrl(vastTag, isUrl);
        if (url == null || url.isEmpty()) {
            SDKLogger.e(TAG, "No MP4 URL available for playback");
            if (completionListener != null) {
                completionListener.onVideoError("No MP4 URL in VAST");
            }
            return;
        }

        SDKLogger.d(TAG, "Playing inline MP4: " + url);

        videoView.setOnPreparedListener(mp -> {
            mp.setLooping(false);
            if (completionListener != null) {
                completionListener.onVideoLoaded();
            }
            videoView.start();
            if (completionListener != null) {
                completionListener.onVideoStarted();
            }
        });

        videoView.setOnCompletionListener(mp -> {
            if (skipped) {
                return;
            }
            if (completionListener != null) {
                completionListener.onVideoCompleted();
            }
        });

        videoView.setOnErrorListener((mp, what, extra) -> {
            SDKLogger.e(TAG, "VideoView error what=" + what + " extra=" + extra);
            if (completionListener != null) {
                completionListener.onVideoError("Native MP4 playback error");
            }
            return true;
        });

        try {
            videoView.setVideoURI(Uri.parse(url));
        } catch (Exception e) {
            SDKLogger.e(TAG, "Failed to set video URI: " + e.getMessage());
            if (completionListener != null) {
                completionListener.onVideoError(e.getMessage() != null ? e.getMessage() : "Invalid MP4 URL");
            }
        }
    }

    @Override
    public void skipVideo() {
        if (skipped) {
            return;
        }
        skipped = true;
        try {
            videoView.stopPlayback();
        } catch (Throwable ignored) {
        }
        if (completionListener != null) {
            completionListener.onVideoSkipped();
        }
    }

    @Override
    public void release() {
        try {
            videoView.stopPlayback();
        } catch (Throwable ignored) {
        }
        completionListener = null;
    }

    @Override
    public ViewGroup asViewGroup() {
        return this;
    }

    private String resolvePlaybackUrl(String vastTag, boolean isUrl) {
        if (isUrl && vastTag != null && !vastTag.trim().isEmpty()) {
            return vastTag.trim();
        }
        if (mediaUrl != null && !mediaUrl.isEmpty()) {
            return mediaUrl;
        }
        if (vastTag != null && !vastTag.trim().isEmpty()) {
            return VastParser.getMediaFileUrl(vastTag.trim());
        }
        return null;
    }

    private void openClickThrough() {
        if (completionListener != null) {
            completionListener.onVideoClicked();
        }
        if (redirectUrl == null || redirectUrl.trim().isEmpty()) {
            return;
        }
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(redirectUrl.trim()));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            getContext().startActivity(intent);
        } catch (Exception e) {
            SDKLogger.e(TAG, "Failed to open click-through: " + e.getMessage());
        }
    }
}
