
package com.bidscube.sdk;

import android.app.Activity;
import android.content.Context;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import androidx.media3.common.util.UnstableApi;

import com.bidscube.sdk.config.SDKConfig;
import com.bidscube.sdk.consent.ConsentManager;
import com.bidscube.sdk.device.providers.DeviceInfoProvider;
import com.bidscube.sdk.interfaces.AdCallback;
import com.bidscube.sdk.interfaces.ConsentCallback;
import com.bidscube.sdk.interfaces.IBidscubeSDK;
import com.bidscube.sdk.models.DeviceInfo;
import com.bidscube.sdk.models.enums.AdPosition;
import com.bidscube.sdk.ads.ImageAdType;
import com.bidscube.sdk.ads.VideoAdType;
import com.bidscube.sdk.ads.NativeAdType;

/**
 * Main implementation of Bidscube SDK
 * This class handles all ad operations and provides a clean interface for external applications
 */
@UnstableApi
public class BidscubeSDKImpl implements IBidscubeSDK {

    private static final String TAG = "BidscubeSDKImpl";

    private Context context;
    private SDKConfig config;
    private DeviceInfo deviceInfo;
    private AdDisplayManager adDisplayManager;
    private DeviceInfoProvider deviceInfoProvider;
    private ConsentManager consentManager;
    private boolean isInitialized = false;

    @Override
    public void initialize(Context context, SDKConfig config) {
        if (isInitialized) {
            Log.w(TAG, "SDK already initialized");
            return;
        }

        this.context = context;
        this.config = config;

        try {

            deviceInfoProvider = new DeviceInfoProvider(context);
            consentManager = deviceInfoProvider.getConsentManager();

            deviceInfoProvider.getDeviceInfoAsync(deviceInfo -> {
                this.deviceInfo = deviceInfo;

                this.adDisplayManager = new AdDisplayManager(context, deviceInfo);

                this.isInitialized = true;
                Log.d(TAG, "SDK initialized successfully");

                if (config.getDefaultAdPosition() != null) {
                    AdPosition position = AdPosition.fromString(config.getDefaultAdPosition());
                    setAdPosition(position);
                }
            });

        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize SDK: " + e.getMessage());
            throw new RuntimeException("SDK initialization failed", e);
        }
    }

    @Override
    public void showImageAdFullScreen(String placementId, AdCallback callback) {
        checkInitialization();
        if (callback != null) callback.onAdLoading(placementId);

        try {

            ImageAdType imageAdType = new ImageAdType(placementId);
            String url = imageAdType.buildRequestUrl(deviceInfo).toString();

            adDisplayManager.showImageAdFullScreen(url);

            if (callback != null) {
                callback.onAdLoaded(placementId);
                callback.onAdDisplayed(placementId);
            }

        } catch (Exception e) {
            Log.e(TAG, "Failed to show image ad: " + e.getMessage());
            if (callback != null) {
                callback.onAdFailed(placementId, -1, "Failed to show image ad: " + e.getMessage());
            }
        }
    }

    @Override
    public void showImageAdWindowed(String placementId, AdCallback callback) {
        checkInitialization();
        if (callback != null) callback.onAdLoading(placementId);

        try {

            ImageAdType imageAdType = new ImageAdType(placementId);
            String url = imageAdType.buildRequestUrl(deviceInfo).toString();

            adDisplayManager.showImageAdWindowedInternal(url);

            if (callback != null) {
                callback.onAdLoaded(placementId);
                callback.onAdDisplayed(placementId);
            }

        } catch (Exception e) {
            Log.e(TAG, "Failed to show image ad: " + e.getMessage());
            if (callback != null) {
                callback.onAdFailed(placementId, -1, "Failed to show image ad: " + e.getMessage());
            }
        }
    }

    @Override
    public void showVideoAdFullScreen(String placementId, AdCallback callback) {
        checkInitialization();
        if (callback != null) callback.onAdLoading(placementId);

        try {

            VideoAdType videoAdType = new VideoAdType(placementId);
            String url = videoAdType.buildRequestUrl(deviceInfo).toString();

            adDisplayManager.showVideoAdFullScreen(url);

            if (callback != null) {
                callback.onAdLoaded(placementId);
                callback.onAdDisplayed(placementId);
                callback.onVideoAdStarted(placementId);
            }

        } catch (Exception e) {
            Log.e(TAG, "Failed to show video ad: " + e.getMessage());
            if (callback != null) {
                callback.onAdFailed(placementId, -1, "Failed to show video ad: " + e.getMessage());
            }
        }
    }

    @Override
    public void showVideoAdWindowed(String placementId, AdCallback callback) {
        checkInitialization();
        if (callback != null) callback.onAdLoading(placementId);

        try {

            VideoAdType videoAdType = new VideoAdType(placementId);
            String url = videoAdType.buildRequestUrl(deviceInfo).toString();

            adDisplayManager.showVideoAdWindowedInternal(url);

            if (callback != null) {
                callback.onAdLoaded(placementId);
                callback.onAdDisplayed(placementId);
                callback.onVideoAdStarted(placementId);
            }

        } catch (Exception e) {
            Log.e(TAG, "Failed to show video ad: " + e.getMessage());
            if (callback != null) {
                callback.onAdFailed(placementId, -1, "Failed to show video ad: " + e.getMessage());
            }
        }
    }

    @Override
    public void showSkippableVideoAdFullScreen(String placementId, String installButtonText, AdCallback callback) {
        checkInitialization();
        if (callback != null) callback.onAdLoading(placementId);

        try {

            adDisplayManager.showSkippableVideoAdFullScreen(installButtonText);

            if (callback != null) {
                callback.onAdLoaded(placementId);
                callback.onAdDisplayed(placementId);
                callback.onVideoAdStarted(placementId);
            }

        } catch (Exception e) {
            Log.e(TAG, "Failed to show skippable video ad: " + e.getMessage());
            if (callback != null) {
                callback.onAdFailed(placementId, -1, "Failed to show skippable video ad: " + e.getMessage());
            }
        }
    }

    @Override
    public void showSkippableVideoAdWindowed(String placementId, String installButtonText, AdCallback callback) {
        checkInitialization();
        if (callback != null) callback.onAdLoading(placementId);

        try {

            adDisplayManager.showSkippableVideoAdWindowed(installButtonText);

            if (callback != null) {
                callback.onAdLoaded(placementId);
                callback.onAdDisplayed(placementId);
                callback.onVideoAdStarted(placementId);
            }

        } catch (Exception e) {
            Log.e(TAG, "Failed to show skippable video ad: " + e.getMessage());
            if (callback != null) {
                callback.onAdFailed(placementId, -1, "Failed to show skippable video ad: " + e.getMessage());
            }
        }
    }

    @Override
    public void showNativeAdFullScreen(String placementId, AdCallback callback) {
        checkInitialization();
        if (callback != null) callback.onAdLoading(placementId);

        try {

            NativeAdType nativeAdType = new NativeAdType(placementId);
            String url = nativeAdType.buildRequestUrl(deviceInfo).toString();

            adDisplayManager.showAdNativeFullScreenFromUrl(url);

            if (callback != null) {
                callback.onAdLoaded(placementId);
                callback.onAdDisplayed(placementId);
            }

        } catch (Exception e) {
            Log.e(TAG, "Failed to show native ad: " + e.getMessage());
            if (callback != null) {
                callback.onAdFailed(placementId, -1, "Failed to show native ad: " + e.getMessage());
            }
        }
    }

    @Override
    public void showNativeAdWindowed(String placementId, AdCallback callback) {
        checkInitialization();
        if (callback != null) callback.onAdLoading(placementId);

        try {

            NativeAdType nativeAdType = new NativeAdType(placementId);
            String url = nativeAdType.buildRequestUrl(deviceInfo).toString();

            adDisplayManager.showAdNativeWindowedFromUrl(url);

            if (callback != null) {
                callback.onAdLoaded(placementId);
                callback.onAdDisplayed(placementId);
            }

        } catch (Exception e) {
            Log.e(TAG, "Failed to show native ad: " + e.getMessage());
            if (callback != null) {
                callback.onAdFailed(placementId, -1, "Failed to show native ad: " + e.getMessage());
            }
        }
    }

    @Override
    public View getImageAdView(String placementId, AdCallback callback) {
        checkInitialization();
        if (callback != null) callback.onAdLoading(placementId);

        try {
            ImageAdType imageAdType = new ImageAdType(placementId);
            String url = imageAdType.buildRequestUrl(deviceInfo).toString();

            View adView = adDisplayManager.getImageAdView(url, callback);
            
            if (callback != null) {
                callback.onAdLoaded(placementId);
                callback.onAdDisplayed(placementId);
            }

            return adView;

        } catch (Exception e) {
            Log.e(TAG, "Failed to get image ad view: " + e.getMessage());
            if (callback != null) {
                callback.onAdFailed(placementId, -1, "Failed to get image ad view: " + e.getMessage());
            }
            return createErrorView("Failed to load image ad: " + e.getMessage());
        }
    }

    @Override
    public View getVideoAdView(String placementId, AdCallback callback) {
        checkInitialization();
        if (callback != null) callback.onAdLoading(placementId);

        try {
            VideoAdType videoAdType = new VideoAdType(placementId);
            String url = videoAdType.buildRequestUrl(deviceInfo).toString();

            View adView = adDisplayManager.getVideoAdView(url, callback);
            
            if (callback != null) {
                callback.onAdLoaded(placementId);
                callback.onAdDisplayed(placementId);
                callback.onVideoAdStarted(placementId);
            }

            return adView;

        } catch (Exception e) {
            Log.e(TAG, "Failed to get video ad view: " + e.getMessage());
            if (callback != null) {
                callback.onAdFailed(placementId, -1, "Failed to get video ad view: " + e.getMessage());
            }
            return createErrorView("Failed to load video ad: " + e.getMessage());
        }
    }

    @Override
    public View getNativeAdView(String placementId, AdCallback callback) {
        checkInitialization();
        if (callback != null) callback.onAdLoading(placementId);

        try {
            NativeAdType nativeAdType = new NativeAdType(placementId);
            String url = nativeAdType.buildRequestUrl(deviceInfo).toString();

            View adView = adDisplayManager.getNativeAdView(url, callback);
            
            if (callback != null) {
                callback.onAdLoaded(placementId);
                callback.onAdDisplayed(placementId);
            }

            return adView;

        } catch (Exception e) {
            Log.e(TAG, "Failed to get native ad view: " + e.getMessage());
            if (callback != null) {
                callback.onAdFailed(placementId, -1, "Failed to get native ad view: " + e.getMessage());
            }
            return createErrorView("Failed to load native ad: " + e.getMessage());
        }
    }

    @Override
    public void setAdPosition(AdPosition position) {
        checkInitialization();
        if (adDisplayManager != null) {
            adDisplayManager.setAdPosition(position);
            Log.d(TAG, "Ad position set to: " + position.getDisplayName());
        }
    }

    @Override
    public AdPosition getCurrentAdPosition() {
        checkInitialization();
        if (adDisplayManager != null) {
            return adDisplayManager.getCurrentAdPosition();
        }
        return AdPosition.UNKNOWN;
    }

    @Override
    public AdPosition getEffectiveAdPosition() {
        if (adDisplayManager != null) {
            return adDisplayManager.getEffectiveAdPosition();
        }
        return AdPosition.UNKNOWN;
    }

    @Override
    public AdPosition getResponseAdPosition() {
        if (adDisplayManager != null) {
            return adDisplayManager.getResponseAdPosition();
        }
        return AdPosition.UNKNOWN;
    }

    @Override
    public void cleanup() {
        if (adDisplayManager != null) {
            adDisplayManager.cleanup();
        }
        isInitialized = false;
        Log.d(TAG, "SDK cleaned up");
    }

    @Override
    public boolean isInitialized() {
        return isInitialized;
    }


    @Override
    public void requestConsentInfoUpdate(ConsentCallback callback) {
        checkInitialization();
        if (context instanceof Activity) {
            consentManager.requestConsentInfoUpdate(
                    (Activity) context,
                    new ConsentManager.ConsentInfoUpdateCallback() {
                        @Override
                        public void onConsentInfoUpdated() {

                            deviceInfoProvider.getDeviceInfoWithCurrentConsent(newDeviceInfo -> {
                                deviceInfo = newDeviceInfo;
                                if (callback != null) {
                                    callback.onConsentInfoUpdated();
                                }
                            });
                        }

                    }
            );
        } else {
            Log.e(TAG, "Context is not an Activity, cannot request consent info update");
            if (callback != null) {
                callback.onConsentInfoUpdateFailed(new Exception("Context is not an Activity"));
            }
        }
    }

    @Override
    public void showConsentForm(ConsentCallback callback) {
        checkInitialization();
        if (context instanceof Activity) {
            consentManager.loadAndShowConsentForm(
                    (Activity) context,
                    formError -> {
                        Log.e(TAG, "Consent form error: " + formError.getMessage());
                        if (callback != null) {
                            callback.onConsentFormError(new Exception(formError.getMessage()));
                        }
                    }
            );
        } else {
            Log.e(TAG, "Context is not an Activity, cannot show consent form");
            if (callback != null) {
                callback.onConsentFormError(new Exception("Context is not an Activity"));
            }
        }
    }

    @Override
    public boolean isConsentRequired() {
        return false;
    }

    @Override
    public boolean hasAdsConsent() {
        return false;
    }

    @Override
    public boolean hasAnalyticsConsent() {
        return false;
    }

    @Override
    public String getConsentStatusSummary() {
        checkInitialization();
        return consentManager.getConsentSummary();
    }

    @Override
    public void resetConsent() {
        checkInitialization();
        consentManager.resetConsent();
        Log.d(TAG, "Consent information reset");
    }

    @Override
    public void enableConsentDebugMode(String deviceId) {

    }

    private void checkInitialization() {
        if (!isInitialized) {
            throw new IllegalStateException("SDK not initialized. Call initialize() first.");
        }
    }

    private View createErrorView(String errorMessage) {
        TextView errorView = new TextView(context);
        errorView.setText(errorMessage);
        errorView.setTextColor(0xFFFF0000);
        errorView.setPadding(16, 16, 16, 16);
        errorView.setBackgroundColor(0xFFFFE0E0);
        return errorView;
    }
}
