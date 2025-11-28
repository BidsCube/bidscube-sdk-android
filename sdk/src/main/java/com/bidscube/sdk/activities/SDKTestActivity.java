package com.bidscube.sdk.activities;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.bidscube.sdk.BidscubeSDK;
import com.bidscube.sdk.ads.AdType;
import com.bidscube.sdk.config.SDKConfig;
import com.bidscube.sdk.interfaces.AdCallback;
import com.bidscube.sdk.models.enums.AdPosition;
import com.bidscube.sdk.utils.SDKLogger;

/**
 * Test activity demonstrating SDK usage
 * This shows how to properly initialize and use the SDK in a real application
 */
public class SDKTestActivity extends Activity {

    private static final String TAG = "SDKTestActivity";
    private TextView statusText;
    private LinearLayout buttonContainer;
    private EditText placementIdInput;
    private TextView currentAdPositionText;

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


        addTextView("Placement ID Input:", layout);
        placementIdInput = new EditText(this);
        placementIdInput.setHint("Enter placement ID (e.g., 19481)");
        placementIdInput.setText("19481");
        placementIdInput.setPadding(16, 16, 16, 16);
        layout.addView(placementIdInput);


        currentAdPositionText = new TextView(this);
        currentAdPositionText.setText("Current Ad Position: Not Set");
        currentAdPositionText.setTextSize(16);
        currentAdPositionText.setPadding(0, 16, 0, 16);
        currentAdPositionText.setTextColor(0xFF666666);
        layout.addView(currentAdPositionText);

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
            SDKLogger.e(TAG, "Failed to initialize SDK: " + e.getMessage());
            statusText.setText("SDK Status: Initialization Failed");
            Toast.makeText(this, "SDK initialization failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void setupAdButtons() {
        buttonContainer.removeAllViews();
        addTextView("");


        Button imageAdButton = new Button(this);
        imageAdButton.setText("Image Ads");
        imageAdButton.setOnClickListener(v -> showAd(AdType.Type.IMAGE));
        buttonContainer.addView(imageAdButton);


        Button videoAdButton = new Button(this);
        videoAdButton.setText("Video Ads");
        videoAdButton.setOnClickListener(v -> showAd(AdType.Type.VIDEO));
        buttonContainer.addView(videoAdButton);


        Button nativeAdButton = new Button(this);
        nativeAdButton.setText("Native Ads");
        nativeAdButton.setOnClickListener(v -> showAd(AdType.Type.NATIVE));
        buttonContainer.addView(nativeAdButton);

        addTextView("");


        addTextView("Logging Control:");
        Button enableLoggingButton = new Button(this);
        enableLoggingButton.setText("Enable Logging");
        enableLoggingButton.setOnClickListener(v -> enableLogging(true));
        buttonContainer.addView(enableLoggingButton);

        Button disableLoggingButton = new Button(this);
        disableLoggingButton.setText("Disable Logging");
        disableLoggingButton.setOnClickListener(v -> enableLogging(false));
        buttonContainer.addView(disableLoggingButton);

        Button testLoggingButton = new Button(this);
        testLoggingButton.setText("Test Logging");
        testLoggingButton.setOnClickListener(v -> testLogging());
        buttonContainer.addView(testLoggingButton);

        addTextView("");


        Button cleanupButton = new Button(this);
        cleanupButton.setText("Cleanup SDK");
        cleanupButton.setOnClickListener(v -> cleanupSDK());
        buttonContainer.addView(cleanupButton);
    }

    private void addTextView(String text) {
        addTextView(text, buttonContainer);
    }

    private void addTextView(String text, LinearLayout parent) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(16);
        tv.setPadding(0, 16, 0, 8);
        parent.addView(tv);
    }

    private String getPlacementId() {
        String placementId = placementIdInput.getText().toString().trim();
        return placementId.isEmpty() ? null : placementId;
    }

    private void updatePositionAfterResponse(String placementId) {
        new Thread(() -> {
            int attempts = 0;
            int maxAttempts = 20;

            while (attempts < maxAttempts) {
                try {
                    AdPosition responsePosition = BidscubeSDK.getResponseAdPosition();


                    if (responsePosition != AdPosition.UNKNOWN) {
                        runOnUiThread(() -> {
                            currentAdPositionText.setText("Current Ad Position: " +
                                    responsePosition.getResponseValue() + " - " + responsePosition.getDisplayName());
                            currentAdPositionText.setTextColor(0xFF2196F3);
                            SDKLogger.d(TAG, "Ad position updated from response: " +
                                    responsePosition.getResponseValue() + " - " + responsePosition.getDisplayName());
                        });
                        return;
                    }


                    Thread.sleep(100);
                    attempts++;

                } catch (Exception e) {
                    SDKLogger.e(TAG, "Error while polling for position: " + e.getMessage());
                    break;
                }
            }


            Log.w(TAG, "Position polling timed out after " + maxAttempts + " attempts");
            runOnUiThread(() -> {
                currentAdPositionText.setText("Current Ad Position: 0 - UNKNOWN (timeout)");
                currentAdPositionText.setTextColor(0xFF666666);
            });
        }).start();
    }

    private void updateAdPositionFromResponse(String placementId) {
        try {

            AdPosition responsePosition = BidscubeSDK.getResponseAdPosition();

            runOnUiThread(() -> {
                currentAdPositionText.setText("Current Ad Position: " +
                        responsePosition.getResponseValue() + " - " + responsePosition.getDisplayName());
                currentAdPositionText.setTextColor(0xFF2196F3);
                SDKLogger.d(TAG, "Ad position updated from response: " +
                        responsePosition.getResponseValue() + " - " + responsePosition.getDisplayName());
            });
        } catch (Exception e) {
            SDKLogger.e(TAG, "Failed to update ad position from response: " + e.getMessage());

            runOnUiThread(() -> {
                currentAdPositionText.setText("Current Ad Position: 0 - UNKNOWN");
                currentAdPositionText.setTextColor(0xFF666666);
            });
        }
    }

    private void showAd(AdType.Type adType) {
        if (!BidscubeSDK.isInitialized()) {
            Toast.makeText(this, "SDK not initialized", Toast.LENGTH_SHORT).show();
            return;
        }

        String placementId = getPlacementId();
        if (placementId == null || placementId.trim().isEmpty()) {
            showPlacementIdErrorDialog();
            return;
        }

        showAds(placementId, adType);
    }

    private void showPlacementIdErrorDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Error")
                .setMessage("Placement ID is required. Please enter a valid placement ID.")
                .setPositiveButton("OK", (dialog, which) -> dialog.dismiss())
                .show();
    }

    private void showAds(String placementId, AdType.Type adType) {
        AdCallback callback = createAdCallback(placementId);

        try {
            switch (adType) {
                case IMAGE:
                    BidscubeSDK.showImageAd(placementId, callback);
                    break;
                case VIDEO:
                    BidscubeSDK.showVideoAd(placementId, callback);
                    break;
                case SKIP_VIDEO:
                    BidscubeSDK.showSkippableVideoAd(placementId, "Install Now", callback);
                    break;
                case NATIVE:
                    BidscubeSDK.showNativeAd(placementId, callback);
                    break;
                default:
                    SDKLogger.e(TAG, "Unknown ad type: " + adType);
                    Toast.makeText(this, "Unknown ad type: " + adType, Toast.LENGTH_SHORT).show();
                    break;
            }


            updatePositionAfterResponse(placementId);

        } catch (Exception e) {
            SDKLogger.e(TAG, "Failed to show ad: " + e.getMessage());
            Toast.makeText(this, "Failed to show ad: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private AdCallback createAdCallback(String placementId) {
        return new AdCallback() {
            @Override
            public void onAdLoading(String placementId) {
                SDKLogger.d(TAG, "Ad loading: " + placementId);
                Toast.makeText(SDKTestActivity.this, "Ad loading...", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onAdLoaded(String placementId) {
                SDKLogger.d(TAG, "Ad loaded: " + placementId);
                Toast.makeText(SDKTestActivity.this, "Ad loaded successfully", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onAdDisplayed(String placementId) {
                SDKLogger.d(TAG, "Ad displayed: " + placementId);
                Toast.makeText(SDKTestActivity.this, "Ad displayed", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onAdClicked(String placementId) {
                SDKLogger.d(TAG, "Ad clicked: " + placementId);
                Toast.makeText(SDKTestActivity.this, "Ad clicked", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onAdClosed(String placementId) {
                SDKLogger.d(TAG, "Ad closed: " + placementId);
                Toast.makeText(SDKTestActivity.this, "Ad closed", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onAdFailed(String placementId, int errorCode, String errorMessage) {
                SDKLogger.e(TAG, "Ad failed: " + placementId + " - " + errorMessage);
                Toast.makeText(SDKTestActivity.this, "Ad failed: " + errorMessage, Toast.LENGTH_LONG).show();
            }

            @Override
            public void onVideoAdStarted(String placementId) {
                SDKLogger.d(TAG, "Video ad started: " + placementId);
                Toast.makeText(SDKTestActivity.this, "Video started playing", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onVideoAdCompleted(String placementId) {
                SDKLogger.d(TAG, "Video ad completed: " + placementId);
                Toast.makeText(SDKTestActivity.this, "Video completed", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onVideoAdSkipped(String placementId) {
                SDKLogger.d(TAG, "Video ad skipped: " + placementId);
                Toast.makeText(SDKTestActivity.this, "Video skipped", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onVideoAdSkippable(String placementId) {
                SDKLogger.d(TAG, "Video ad skippable: " + placementId);
                Toast.makeText(SDKTestActivity.this, "Video can now be skipped", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onInstallButtonClicked(String placementId, String buttonText) {
                SDKLogger.d(TAG, "Install button clicked: " + placementId + " - " + buttonText);
                Toast.makeText(SDKTestActivity.this, "Install button clicked: " + buttonText, Toast.LENGTH_SHORT)
                        .show();
            }
        };
    }

    private void cleanupSDK() {
        if (BidscubeSDK.isInitialized()) {
            BidscubeSDK.cleanup();
            statusText.setText("SDK Status: Cleaned Up");
            buttonContainer.removeAllViews();


            currentAdPositionText.setText("Current Ad Position: Not Set");
            currentAdPositionText.setTextColor(0xFF666666);

            Toast.makeText(this, "SDK cleaned up", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "SDK not initialized", Toast.LENGTH_SHORT).show();
        }
    }

    private void enableLogging(boolean enabled) {
        SDKLogger.setLoggingEnabled(enabled);
        String message = "Logging " + (enabled ? "enabled" : "disabled");
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        SDKLogger.d(TAG, "Logging control: " + message);
    }

    private void testLogging() {
        SDKLogger.d(TAG, "=== LOGGING TEST ===");
        Log.i(TAG, "This is an INFO message");
        Log.w(TAG, "This is a WARNING message");
        SDKLogger.e(TAG, "This is an ERROR message");
        Log.v(TAG, "This is a VERBOSE message");

        SDKLogger.d(TAG, "=== SDK LOGGER TEST ===");
        SDKLogger.i(TAG, "This is an SDK INFO message");
        SDKLogger.w(TAG, "This is an SDK WARNING message");
        SDKLogger.e(TAG, "This is an SDK ERROR message");
        SDKLogger.v(TAG, "This is an SDK VERBOSE message");

        String message = "Logging test completed. Check logs to see which messages appear.";
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        SDKLogger.d(TAG, "Logging test completed - check if SDKLogger messages are filtered");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (BidscubeSDK.isInitialized()) {
            BidscubeSDK.cleanup();
        }
    }
}
