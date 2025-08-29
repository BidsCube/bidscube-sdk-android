package com.bidscube.sdk.activities;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.bidscube.sdk.BidscubeSDK;
import com.bidscube.sdk.config.SDKConfig;
import com.bidscube.sdk.interfaces.AdCallback;

/**
 * Test activity demonstrating SDK usage
 * This shows how to properly initialize and use the SDK in a real application
 */
public class SDKTestActivity extends Activity {

    private static final String TAG = "SDKTestActivity";
    private TextView statusText;
    private LinearLayout buttonContainer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ScrollView scrollView = new ScrollView(this);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(32, 32, 32, 32);
        scrollView.addView(layout);

        statusText = new TextView(this);
        statusText.setText("SDK Status: Not Initialized");
        statusText.setTextSize(18);
        statusText.setPadding(0, 0, 0, 32);
        layout.addView(statusText);

        Button initButton = new Button(this);
        initButton.setText("Initialize SDK");
        initButton.setOnClickListener(v -> initializeSDK());
        layout.addView(initButton);

        buttonContainer = new LinearLayout(this);
        buttonContainer.setOrientation(LinearLayout.VERTICAL);
        layout.addView(buttonContainer);

        setContentView(scrollView);
    }

    private void initializeSDK() {
        try {

            SDKConfig config = new SDKConfig.Builder(this)
                    .enableLogging(true)
                    .enableDebugMode(true)
                    .defaultAdTimeout(30000)
                    .defaultAdPosition("UNKNOWN")
                    .build();

            BidscubeSDK.initialize(this, config);

            statusText.setText("SDK Status: Initializing...");

            new Thread(() -> {
                while (!BidscubeSDK.isInitialized()) {
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        break;
                    }
                }

                runOnUiThread(() -> {
                    statusText.setText("SDK Status: Initialized");
                    setupAdButtons();
                });
            }).start();

        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize SDK: " + e.getMessage());
            statusText.setText("SDK Status: Initialization Failed");
            Toast.makeText(this, "SDK initialization failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void setupAdButtons() {
        buttonContainer.removeAllViews();
        addTextView("");

        addTextView("Image Ads:");
        addAdButton("Image Full Screen", "19481", true, false, true, false);
        addAdButton("Image Windowed", "19481", false, false, true, false);

        addTextView("Video Ads:");
        addAdButton("Video Full Screen", "19483", true, true, false, false);
        addAdButton("Video Windowed", "19483", false, true, false, false);

        addTextView("Skippable Video Ads:");
        addSkippableVideoButton("Skippable Video Windowed", "19483", false, "Install Now");
        addSkippableVideoButton("Skippable Video Full Screen", "19483", true, "Get Started");

        addTextView("Native Ads:");
        addAdButton("Native Full Screen", "19487", true, false, false, true);
        addAdButton("Native Windowed", "19487", false, false, false, true);

        addTextView("");
        Button cleanupButton = new Button(this);
        cleanupButton.setText("Cleanup SDK");
        cleanupButton.setOnClickListener(v -> cleanupSDK());
        buttonContainer.addView(cleanupButton);
    }

    private void addTextView(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(16);
        tv.setPadding(0, 16, 0, 8);
        buttonContainer.addView(tv);
    }

    private void addAdButton(String text, String placementId, boolean fullScreen, boolean isVideo, boolean isImage, boolean isNative) {
        Button button = new Button(this);
        button.setText(text);
        button.setOnClickListener(v -> showAd(placementId, fullScreen, isVideo, isImage, isNative));
        buttonContainer.addView(button);
    }

    private void addSkippableVideoButton(String text, String placementId, boolean fullScreen, String installButtonText) {
        Button button = new Button(this);
        button.setText(text);
        button.setOnClickListener(v -> showSkippableVideoAd(placementId, fullScreen, installButtonText));
        buttonContainer.addView(button);
    }

    private void showAd(String placementId, boolean fullScreen, boolean isVideo, boolean isImage, boolean isNative) {
        if (!BidscubeSDK.isInitialized()) {
            Toast.makeText(this, "SDK not initialized", Toast.LENGTH_SHORT).show();
            return;
        }

        AdCallback callback = createAdCallback(placementId);

        try {
            if (isVideo) {
                if (fullScreen) {
                    BidscubeSDK.showVideoAdFullScreen(placementId, callback);
                } else {
                    BidscubeSDK.showVideoAdWindowed(placementId, callback);
                }
            } else if (isImage) {
                if (fullScreen) {
                    BidscubeSDK.showImageAdFullScreen(placementId, callback);
                } else {
                    BidscubeSDK.showImageAdWindowed(placementId, callback);
                }
            } else if (isNative) {
                if (fullScreen) {
                    BidscubeSDK.showNativeAdFullScreen(placementId, callback);
                } else {
                    BidscubeSDK.showNativeAdWindowed(placementId, callback);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to show ad: " + e.getMessage());
            Toast.makeText(this, "Failed to show ad: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void showSkippableVideoAd(String placementId, boolean fullScreen, String installButtonText) {
        if (!BidscubeSDK.isInitialized()) {
            Toast.makeText(this, "SDK not initialized", Toast.LENGTH_SHORT).show();
            return;
        }

        AdCallback callback = createAdCallback(placementId);

        try {
            if (fullScreen) {
                BidscubeSDK.showSkippableVideoAdFullScreen(placementId, installButtonText, callback);
            } else {
                BidscubeSDK.showSkippableVideoAdWindowed(placementId, installButtonText, callback);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to show skippable video ad: " + e.getMessage());
            Toast.makeText(this, "Failed to show skippable video ad: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private AdCallback createAdCallback(String placementId) {
        return new AdCallback() {
            @Override
            public void onAdLoading(String placementId) {
                Log.d(TAG, "Ad loading: " + placementId);
                Toast.makeText(SDKTestActivity.this, "Ad loading...", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onAdLoaded(String placementId) {
                Log.d(TAG, "Ad loaded: " + placementId);
                Toast.makeText(SDKTestActivity.this, "Ad loaded successfully", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onAdDisplayed(String placementId) {
                Log.d(TAG, "Ad displayed: " + placementId);
                Toast.makeText(SDKTestActivity.this, "Ad displayed", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onAdClicked(String placementId) {
                Log.d(TAG, "Ad clicked: " + placementId);
                Toast.makeText(SDKTestActivity.this, "Ad clicked", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onAdClosed(String placementId) {
                Log.d(TAG, "Ad closed: " + placementId);
                Toast.makeText(SDKTestActivity.this, "Ad closed", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onAdFailed(String placementId, int errorCode, String errorMessage) {
                Log.e(TAG, "Ad failed: " + placementId + " - " + errorMessage);
                Toast.makeText(SDKTestActivity.this, "Ad failed: " + errorMessage, Toast.LENGTH_LONG).show();
            }

            @Override
            public void onVideoAdStarted(String placementId) {
                Log.d(TAG, "Video ad started: " + placementId);
                Toast.makeText(SDKTestActivity.this, "Video started playing", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onVideoAdCompleted(String placementId) {
                Log.d(TAG, "Video ad completed: " + placementId);
                Toast.makeText(SDKTestActivity.this, "Video completed", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onVideoAdSkipped(String placementId) {
                Log.d(TAG, "Video ad skipped: " + placementId);
                Toast.makeText(SDKTestActivity.this, "Video skipped", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onVideoAdSkippable(String placementId) {
                Log.d(TAG, "Video ad skippable: " + placementId);
                Toast.makeText(SDKTestActivity.this, "Video can now be skipped", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onInstallButtonClicked(String placementId, String buttonText) {
                Log.d(TAG, "Install button clicked: " + placementId + " - " + buttonText);
                Toast.makeText(SDKTestActivity.this, "Install button clicked: " + buttonText, Toast.LENGTH_SHORT).show();
            }
        };
    }

    private void cleanupSDK() {
        if (BidscubeSDK.isInitialized()) {
            BidscubeSDK.cleanup();
            statusText.setText("SDK Status: Cleaned Up");
            buttonContainer.removeAllViews();
            Toast.makeText(this, "SDK cleaned up", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "SDK not initialized", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (BidscubeSDK.isInitialized()) {
            BidscubeSDK.cleanup();
        }
    }
}


