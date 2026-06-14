package com.bidscube.sdk.view;

import android.annotation.SuppressLint;
import android.content.Context;
import android.media.AudioManager;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.VideoView;

import androidx.media3.common.util.UnstableApi;

import com.bidscube.sdk.config.VideoPlayerProvider;
import com.bidscube.sdk.adapters.VideoAdPlayerAdapter;
import com.bidscube.sdk.utils.SDKLogger;
import com.google.ads.interactivemedia.v3.api.AdDisplayContainer;
import com.google.ads.interactivemedia.v3.api.AdsLoader;
import com.google.ads.interactivemedia.v3.api.AdsManager;
import com.google.ads.interactivemedia.v3.api.AdsRenderingSettings;
import com.google.ads.interactivemedia.v3.api.AdsRequest;
import com.google.ads.interactivemedia.v3.api.ImaSdkFactory;
import com.google.ads.interactivemedia.v3.api.ImaSdkSettings;
import com.google.ads.interactivemedia.v3.api.player.VideoAdPlayer;

import java.util.Collections;

@SuppressLint("ViewConstructor")
@UnstableApi
public class IMAPlayerHandler extends FrameLayout implements BidscubeVideoAdPlayer {

    private ImaSdkFactory sdkFactory;
    private AdsLoader adsLoader;
    private AdsManager adsManager;
    private VideoView videoView;
    private final String eventsTag = "IMAevent";
    private final String vastUrl;
    private final String redirectUrl;
    private BidscubeVideoAdPlayer.VideoCompletionListener completionListener;
    private boolean isVideoPlaying = false;

    private final VideoPlayerProvider videoPlayerProvider;

    public IMAPlayerHandler(String vastUrl, String redirectUrl, Context context) {
        this(vastUrl, redirectUrl, context, (VideoPlayerProvider) null);
    }

    public IMAPlayerHandler(String vastUrl, String redirectUrl, Context context, VideoPlayerProvider videoPlayerProvider) {
        super(context);
        this.vastUrl = vastUrl;
        this.redirectUrl = redirectUrl;
        this.videoPlayerProvider = videoPlayerProvider;
        init(vastUrl, redirectUrl, context);
    }

    public IMAPlayerHandler(String vastUrl, String redirectUrl, Context context, AttributeSet attrs) {
        this(vastUrl, redirectUrl, context, attrs, null);
    }

    public IMAPlayerHandler(String vastUrl, String redirectUrl, Context context, AttributeSet attrs,
            VideoPlayerProvider videoPlayerProvider) {
        super(context, attrs);
        this.vastUrl = vastUrl;
        this.redirectUrl = redirectUrl;
        this.videoPlayerProvider = videoPlayerProvider;
        init(vastUrl, redirectUrl, context);
    }

    private void init(String vastUrl, String redirectUrl, Context context) {

        AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        videoView = videoPlayerProvider != null ? videoPlayerProvider.createVideoView(context) : new VideoView(context);

        FrameLayout.LayoutParams videoParams = new FrameLayout.LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT);
        videoParams.gravity = Gravity.CENTER;
        videoView.setLayoutParams(videoParams);

        videoView.setScaleX(1.0f);
        videoView.setScaleY(1.0f);

        setBackgroundColor(android.graphics.Color.BLACK);

        addView(videoView);
        setOnHierarchyChangeListener(new OnHierarchyChangeListener() {
            @Override
            public void onChildViewAdded(View parent, View child) {
                if (isVideoPlaying) {
                    hideNativeSkipUiInTree(IMAPlayerHandler.this);
                }
            }

            @Override
            public void onChildViewRemoved(View parent, View child) {
                // No-op.
            }
        });

        VideoAdPlayer videoAdPlayerAdapter = new VideoAdPlayerAdapter(videoView, audioManager);
        AdDisplayContainer adDisplayContainer = ImaSdkFactory.createAdDisplayContainer(this, videoAdPlayerAdapter);

        sdkFactory = ImaSdkFactory.getInstance();
        ImaSdkBootstrap.initialize(context);

        ImaSdkSettings imaSdkSettings = sdkFactory.createImaSdkSettings();
        adsLoader = sdkFactory.createAdsLoader(context, imaSdkSettings, adDisplayContainer);

        adsLoader.addAdErrorListener(errorEvent -> {
            String msg = errorEvent.getError() != null ? errorEvent.getError().getMessage() : "unknown ad error";
            SDKLogger.e("IMAPlayerHandler", "AdsLoader error: " + msg);
            if (completionListener != null) {
                completionListener.onVideoError(msg);
            }
        });

        adsLoader.addAdsLoadedListener(adsManagerLoadedEvent -> {
            adsManager = adsManagerLoadedEvent.getAdsManager();
            adsManager.addAdErrorListener(errorEvent -> {
                String msg = errorEvent.getError() != null ? errorEvent.getError().getMessage() : "unknown ad error";
                SDKLogger.e("IMAPlayerHandler", "AdsManager error: " + msg);
                if (completionListener != null) {
                    completionListener.onVideoError(msg);
                }
            });
            AdsRenderingSettings renderingSettings = sdkFactory.createAdsRenderingSettings();
            renderingSettings.setUiElements(Collections.emptySet());
            renderingSettings.setDisableUi(true);
            adsManager.init(renderingSettings);
            adsManager.addAdEventListener(this::handleAdEvent);
            adsManager.start();
        });
    }

    private void handleAdEvent(com.google.ads.interactivemedia.v3.api.AdEvent adEvent) {
        SDKLogger.d("IMAPlayerHandler", "Ad event: " + adEvent.getType());

        switch (adEvent.getType()) {
            case LOADED:
                SDKLogger.d(eventsTag, "Ad loaded");
                isVideoPlaying = true;
                if (completionListener != null) {
                    completionListener.onVideoLoaded();
                }
                break;

            case STARTED:
                SDKLogger.d(eventsTag, "Ad started");
                isVideoPlaying = true;
                hideNativeSkipUiInTree(this);
                if (completionListener != null) {
                    completionListener.onVideoStarted();
                }
                break;

            case CLICKED:
                SDKLogger.d(eventsTag, "Ad click-through URL clicked");
                if (completionListener != null) {
                    completionListener.onVideoClicked();
                }
                if (redirectUrl != null && !redirectUrl.isEmpty()) {
                    openUrlInBrowser(redirectUrl);
                }
                break;

            case COMPLETED:
                SDKLogger.d(eventsTag, "Ad completed");
                isVideoPlaying = false;
                if (completionListener != null) {
                    completionListener.onVideoCompleted();
                }
                break;

            case SKIPPED:
                SDKLogger.d(eventsTag, "Ad skipped");
                isVideoPlaying = false;
                if (completionListener != null) {
                    completionListener.onVideoSkipped();
                }
                break;

            case SKIPPABLE_STATE_CHANGED:
                SDKLogger.d(eventsTag, "Ad skippable state changed");
                hideNativeSkipUiInTree(this);
                if (completionListener != null) {
                    completionListener.onVideoSkippable();
                }
                break;

            default:
                SDKLogger.d(eventsTag, "Other ad event: " + adEvent.getType());
                break;
        }
    }

    private static void hideNativeSkipUiInTree(View root) {
        if (!(root instanceof ViewGroup)) {
            return;
        }
        ViewGroup group = (ViewGroup) root;
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (child instanceof TextView) {
                CharSequence text = ((TextView) child).getText();
                if (text != null && text.toString().toLowerCase().contains("skip")) {
                    child.setVisibility(GONE);
                }
            }
            String resourceName = null;
            try {
                if (child.getId() != View.NO_ID) {
                    resourceName = child.getResources().getResourceEntryName(child.getId()).toLowerCase();
                }
            } catch (Throwable ignored) {
            }
            if (resourceName != null && resourceName.contains("skip")) {
                child.setVisibility(GONE);
            }
            hideNativeSkipUiInTree(child);
        }
    }

    /**
     * Opens URL in external browser
     */
    private void openUrlInBrowser(String url) {
        try {
            android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_VIEW);
            intent.setData(android.net.Uri.parse(url));
            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);

            android.content.Context context = getContext();
            if (context != null) {
                context.startActivity(intent);
                SDKLogger.d("IMAPlayerHandler", "Opened URL in browser: " + url);
            } else {
                SDKLogger.e("IMAPlayerHandler", "Context is null, cannot open URL");
            }
        } catch (Exception e) {
            SDKLogger.e("IMAPlayerHandler", "Error opening URL in browser: " + e.getMessage());
        }
    }

    @Override
    public void playVast(String vastTag, boolean isUrl) {
        if (adsLoader == null || sdkFactory == null) {
            SDKLogger.e("IMAPlayerHandler", "playVast called before IMA init completed");
            if (completionListener != null) {
                completionListener.onVideoError("IMA not initialized");
            }
            return;
        }

        if (adsManager != null) {
            try {
                adsManager.destroy();
            } catch (Throwable ignored) {
            }
            adsManager = null;
        }

        AdsRequest request = sdkFactory.createAdsRequest();

        if (isUrl) {
            request.setAdTagUrl(vastTag);
            Log.i("VASTTag", "Playing VAST from URL: " + vastTag);
        } else {
            request.setAdsResponse(vastTag);
            Log.i("VASTTag", "Playing VAST from String response " + vastTag);
        }

        adsLoader.requestAds(request);
    }

    /**
     * Skips the current video ad
     */
    @Override
    public void skipVideo() {
        if (adsManager != null && isVideoPlaying) {
            try {
                SDKLogger.d("IMAPlayerHandler", "Attempting to skip video ad");
                adsManager.skip();
                isVideoPlaying = false;
                SDKLogger.d("IMAPlayerHandler", "Video ad skip requested");
            } catch (Exception e) {
                SDKLogger.e("IMAPlayerHandler", "Error skipping video ad: " + e.getMessage());
                if (completionListener != null) {
                    completionListener.onVideoSkipped();
                }
            }
        } else {
            Log.w("IMAPlayerHandler", "Cannot skip video - adsManager is null or video not playing");
        }
    }

    /**
     * Sets the video completion listener
     *
     * @param listener The listener to be called when video completes
     */
    @Override
    public void setOnVideoCompletionListener(BidscubeVideoAdPlayer.VideoCompletionListener listener) {
        this.completionListener = listener;
    }

    @Override
    public ViewGroup asViewGroup() {
        return this;
    }

    @Override
    public void release() {
        if (adsManager != null) {
            adsManager.destroy();
            adsManager = null;
        }
        if (adsLoader != null) {
            adsLoader.release();
            adsLoader = null;
        }
        isVideoPlaying = false;
    }
}
