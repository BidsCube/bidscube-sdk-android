package com.bidscube.sdk.adapters;

import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.widget.VideoView;
import com.bidscube.sdk.utils.SDKLogger;

import androidx.annotation.NonNull;

import com.google.ads.interactivemedia.v3.api.AdPodInfo;
import com.google.ads.interactivemedia.v3.api.player.AdMediaInfo;
import com.google.ads.interactivemedia.v3.api.player.VideoAdPlayer;
import com.google.ads.interactivemedia.v3.api.player.VideoProgressUpdate;

import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class VideoAdPlayerAdapter implements VideoAdPlayer {

    private static final String LOGTAG = "VideoAdPlayerAdapter";
    private static final long POLLING_TIME_MS = 250;

    private final VideoView videoPlayer;
    private final AudioManager audioManager;
    private final List<VideoAdPlayerCallback> videoAdPlayerCallbacks = new ArrayList<>();

    private Timer timer;
    private int adDuration;
    private int savedAdPosition;
    private AdMediaInfo loadedAdMediaInfo;

    private boolean isAdPlaying = false;
    private boolean isAdPaused = false;
    private boolean isReleased = false;

    public VideoAdPlayerAdapter(VideoView videoPlayer, AudioManager audioManager) {
        this.videoPlayer = videoPlayer;
        this.audioManager = audioManager;
    }

    @Override
    public void addCallback(@NonNull VideoAdPlayerCallback videoAdPlayerCallback) {
        if (!videoAdPlayerCallbacks.contains(videoAdPlayerCallback)) {
            videoAdPlayerCallbacks.add(videoAdPlayerCallback);
        }
    }

    @Override
    public void loadAd(@NonNull AdMediaInfo adMediaInfo, @NonNull AdPodInfo adPodInfo) {
        SDKLogger.i(LOGTAG, "loadAd: " + adMediaInfo.getUrl());
        loadedAdMediaInfo = adMediaInfo;
        isAdLoaded = false;
        isAdPlaying = false;
        isAdPaused = false;

        String url = adMediaInfo.getUrl();
        if (url == null || url.trim().isEmpty()) {
            SDKLogger.w(LOGTAG, "loadAd called with empty URL");
            return;
        }

        try {
            setVideoSource(url);
        } catch (Exception e) {
            SDKLogger.e(LOGTAG, "Error preloading ad media: " + e.getMessage(), e);
        }
    }

    @Override
    public void pauseAd(@NonNull AdMediaInfo adMediaInfo) {
        SDKLogger.i(LOGTAG, "pauseAd");
        if (videoPlayer != null && isAdPlaying) {
            try {
                savedAdPosition = videoPlayer.getCurrentPosition();
                videoPlayer.pause();
                isAdPlaying = false;
                isAdPaused = true;
                stopAdTracking();
            } catch (Exception e) {
                SDKLogger.e(LOGTAG, "Error pausing ad: " + e.getMessage(), e);
            }
        }
    }

    @Override
    public void playAd(AdMediaInfo adMediaInfo) {
        SDKLogger.i(LOGTAG, "playAd: " + (adMediaInfo != null ? adMediaInfo.getUrl() : "null"));

        if (videoPlayer == null) {
            SDKLogger.e(LOGTAG, "VideoPlayer is null, cannot play ad");
            notifyImaSdkAboutAdError(MediaPlayer.MEDIA_ERROR_UNSUPPORTED);
            return;
        }

        if (adMediaInfo == null) {
            SDKLogger.e(LOGTAG, "AdMediaInfo is null, cannot play ad");
            notifyImaSdkAboutAdError(MediaPlayer.MEDIA_ERROR_UNSUPPORTED);
            return;
        }

        loadedAdMediaInfo = adMediaInfo;

        if (isAdPaused && savedAdPosition > 0) {
            try {
                videoPlayer.seekTo(savedAdPosition);
                videoPlayer.start();
                isAdPlaying = true;
                isAdPaused = false;
                startAdTracking();
                SDKLogger.d(LOGTAG, "Resumed ad at position " + savedAdPosition);
                return;
            } catch (Exception e) {
                SDKLogger.e(LOGTAG, "Error resuming ad: " + e.getMessage(), e);
            }
        }

        String videoUrl = adMediaInfo.getUrl();
        if (videoUrl == null || videoUrl.trim().isEmpty()) {
            SDKLogger.e(LOGTAG, "Video URL is null or empty");
            notifyImaSdkAboutAdError(MediaPlayer.MEDIA_ERROR_UNSUPPORTED);
            return;
        }

        isAdPlaying = false;
        isAdPaused = false;
        isAdLoaded = false;

        videoPlayer.setOnPreparedListener(mediaPlayer -> {
            SDKLogger.d(LOGTAG, "Video prepared, starting playback");
            isAdLoaded = true;
            adDuration = mediaPlayer.getDuration();
            if (savedAdPosition > 0) {
                mediaPlayer.seekTo(savedAdPosition);
            }
            try {
                mediaPlayer.start();
                isAdPlaying = true;
                isAdPaused = false;
                startAdTracking();
            } catch (Exception e) {
                SDKLogger.e(LOGTAG, "Error starting video playback: " + e.getMessage(), e);
                notifyImaSdkAboutAdError(MediaPlayer.MEDIA_ERROR_UNSUPPORTED);
            }
        });

        videoPlayer.setOnErrorListener((mediaPlayer, errorType, extra) -> {
            SDKLogger.e(LOGTAG, "Video error: " + errorType + ", extra: " + extra);
            isAdPlaying = false;
            isAdPaused = false;
            isAdLoaded = false;
            return notifyImaSdkAboutAdError(errorType);
        });

        videoPlayer.setOnCompletionListener(mediaPlayer -> {
            SDKLogger.d(LOGTAG, "Video completed");
            isAdPlaying = false;
            isAdPaused = false;
            isAdLoaded = false;
            savedAdPosition = 0;
            stopAdTracking();
            notifyImaSdkAboutAdEnded();
        });

        try {
            setVideoSource(videoUrl);
        } catch (Exception e) {
            SDKLogger.e(LOGTAG, "Error setting video source: " + e.getMessage(), e);
            notifyImaSdkAboutAdError(MediaPlayer.MEDIA_ERROR_UNSUPPORTED);
        }
    }

    private void setVideoSource(String videoUrl) {
        if (videoUrl.startsWith("http://") || videoUrl.startsWith("https://")) {
            videoPlayer.setVideoPath(videoUrl);
        } else {
            videoPlayer.setVideoURI(Uri.parse(videoUrl));
        }
    }

    @Override
    public void release() {
        try {
            isReleased = true;
            stopAdTracking();
            if (videoPlayer != null) {
                videoPlayer.stopPlayback();
            }
            isAdPlaying = false;
            isAdPaused = false;
            isAdLoaded = false;
            savedAdPosition = 0;
            SDKLogger.d(LOGTAG, "VideoAdPlayerAdapter released");
        } catch (Exception e) {
            SDKLogger.e(LOGTAG, "Error releasing VideoAdPlayerAdapter: " + e.getMessage(), e);
        }
    }

    @Override
    public void removeCallback(VideoAdPlayerCallback videoAdPlayerCallback) {
        videoAdPlayerCallbacks.remove(videoAdPlayerCallback);
    }

    @Override
    public void stopAd(AdMediaInfo adMediaInfo) {
        SDKLogger.i(LOGTAG, "stopAd");
        if (videoPlayer != null) {
            try {
                videoPlayer.stopPlayback();
                isAdPlaying = false;
                isAdPaused = false;
                isAdLoaded = false;
                savedAdPosition = 0;
                stopAdTracking();
            } catch (Exception e) {
                SDKLogger.e(LOGTAG, "Error stopping ad: " + e.getMessage(), e);
            }
        }
    }

    @Override
    public int getVolume() {
        try {
            if (audioManager != null) {
                return (audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) * 100)
                        / audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
            }
        } catch (Exception e) {
            SDKLogger.e(LOGTAG, "Error getting volume: " + e.getMessage(), e);
        }
        return 0;
    }

    private boolean isAdLoaded = false;

    private void startAdTracking() {
        SDKLogger.d(LOGTAG, "Starting ad tracking");
        stopAdTracking();
        timer = new Timer();
        TimerTask updateTimerTask = new TimerTask() {
            @Override
            public void run() {
                try {
                    VideoProgressUpdate progressUpdate = getAdProgress();
                    notifyImaSdkAboutAdProgress(progressUpdate);
                } catch (Exception e) {
                    SDKLogger.e(LOGTAG, "Error in ad tracking timer: " + e.getMessage(), e);
                }
            }
        };
        timer.schedule(updateTimerTask, POLLING_TIME_MS, POLLING_TIME_MS);
    }

    private void notifyImaSdkAboutAdEnded() {
        if (isReleased) {
            return;
        }
        SDKLogger.i(LOGTAG, "Notifying IMA SDK about ad ended");
        savedAdPosition = 0;
        for (VideoAdPlayer.VideoAdPlayerCallback callback : videoAdPlayerCallbacks) {
            try {
                callback.onEnded(loadedAdMediaInfo);
            } catch (Exception e) {
                SDKLogger.e(LOGTAG, "Error notifying callback about ad ended: " + e.getMessage(), e);
            }
        }
    }

    private void notifyImaSdkAboutAdProgress(VideoProgressUpdate adProgress) {
        if (isReleased) {
            return;
        }
        for (VideoAdPlayer.VideoAdPlayerCallback callback : videoAdPlayerCallbacks) {
            try {
                callback.onAdProgress(loadedAdMediaInfo, adProgress);
            } catch (Exception e) {
                SDKLogger.e(LOGTAG, "Error notifying callback about ad progress: " + e.getMessage(), e);
            }
        }
    }

    private boolean notifyImaSdkAboutAdError(int errorType) {
        if (isReleased) {
            return true;
        }
        SDKLogger.e(LOGTAG, "Notifying IMA SDK about ad error: " + errorType);
        for (VideoAdPlayer.VideoAdPlayerCallback callback : videoAdPlayerCallbacks) {
            try {
                callback.onError(loadedAdMediaInfo);
            } catch (Exception e) {
                SDKLogger.e(LOGTAG, "Error notifying callback about ad error: " + e.getMessage(), e);
            }
        }
        return true;
    }

    public void notifyImaOnContentCompleted() {
        if (isReleased) {
            return;
        }
        SDKLogger.i(LOGTAG, "Notifying IMA SDK about content completed");
        for (VideoAdPlayer.VideoAdPlayerCallback callback : videoAdPlayerCallbacks) {
            try {
                callback.onContentComplete();
            } catch (Exception e) {
                SDKLogger.e(LOGTAG, "Error notifying callback about content completed: " + e.getMessage(), e);
            }
        }
    }

    private void stopAdTracking() {
        if (timer != null) {
            timer.cancel();
            timer.purge();
            timer = null;
        }
    }

    @Override
    public VideoProgressUpdate getAdProgress() {
        try {
            if (!isAdPlaying || videoPlayer == null || adDuration <= 0) {
                return VideoProgressUpdate.VIDEO_TIME_NOT_READY;
            }
            long adPosition = videoPlayer.getCurrentPosition();
            return new VideoProgressUpdate(adPosition, adDuration);
        } catch (Exception e) {
            SDKLogger.e(LOGTAG, "Error getting ad progress: " + e.getMessage(), e);
            return VideoProgressUpdate.VIDEO_TIME_NOT_READY;
        }
    }
}
