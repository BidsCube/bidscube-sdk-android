package com.bidscube.sdk.view;

import android.content.Context;
import android.media.AudioManager;
import android.util.AttributeSet;
import android.widget.FrameLayout;
import android.widget.VideoView;

import androidx.media3.common.util.UnstableApi;

import com.bidscube.sdk.adapters.VideoAdPlayerAdapter;
import com.google.ads.interactivemedia.v3.api.AdDisplayContainer;
import com.google.ads.interactivemedia.v3.api.AdsLoader;
import com.google.ads.interactivemedia.v3.api.AdsManager;
import com.google.ads.interactivemedia.v3.api.AdsRequest;
import com.google.ads.interactivemedia.v3.api.ImaSdkFactory;
import com.google.ads.interactivemedia.v3.api.ImaSdkSettings;
import com.google.ads.interactivemedia.v3.api.player.VideoAdPlayer;

@UnstableApi
public class IMAPlayerHandler extends FrameLayout {

    private ImaSdkFactory sdkFactory;
    private AdsLoader adsLoader;
    private AdsManager adsManager;
    private VideoAdPlayer videoAdPlayerAdapter;
    private AdDisplayContainer adDisplayContainer;
    private ImaSdkSettings imaSdkSettings;
    private VideoView videoView;

    public IMAPlayerHandler(Context context) {
        super(context);
        init(context);
    }

    public IMAPlayerHandler(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private void init(Context context) {

        AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        videoView = new VideoView(context);
        addView(videoView, LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
        videoAdPlayerAdapter = new VideoAdPlayerAdapter(videoView, audioManager);
        adDisplayContainer = ImaSdkFactory.createAdDisplayContainer(this, videoAdPlayerAdapter);

        sdkFactory = ImaSdkFactory.getInstance();

        imaSdkSettings = sdkFactory.createImaSdkSettings();
        adsLoader = sdkFactory.createAdsLoader(context, imaSdkSettings, adDisplayContainer);
    }

    public void playVast(String vastTagUrl) {
        AdsRequest request = sdkFactory.createAdsRequest();
        request.setAdTagUrl(vastTagUrl);

        adsLoader.addAdsLoadedListener(adsManagerLoadedEvent -> {
            adsManager = adsManagerLoadedEvent.getAdsManager();
            adsManager.init();
            adsManager.start();
        });

        adsLoader.requestAds(request);
    }

    public void release() {
        if (adsManager != null) {
            adsManager.destroy();
            adsManager = null;
        }
        if (adsLoader != null) {
            adsLoader.release();
            adsLoader = null;
        }
    }
}
