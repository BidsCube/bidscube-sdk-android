package com.bidscube.sdk.activities;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.bidscube.sdk.BidscubeSDK;
import com.bidscube.sdk.config.SDKConfig;
import com.bidscube.sdk.interfaces.AdCallback;
import com.bidscube.sdk.interfaces.ConsentCallback;

/**
 * Test activity demonstrating consent management features
 * This shows how to properly handle GDPR and CCPA compliance
 */
public class ConsentTestActivity extends Activity {

    private static final String TAG = "ConsentTestActivity";
    private TextView statusText;
    private LinearLayout buttonContainer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(32, 32, 32, 32);

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

        setContentView(layout);
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
                    setupConsentButtons();
                });
            }).start();

        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize SDK: " + e.getMessage());
            statusText.setText("SDK Status: Initialization Failed");
            Toast.makeText(this, "SDK initialization failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void setupConsentButtons() {
        buttonContainer.removeAllViews();

        addTextView("Consent Management:");
        addButton("Request Consent Info Update", v -> requestConsentInfoUpdate());
        addButton("Show Consent Form", v -> showConsentForm());
        addButton("Check if Consent Required", v -> checkConsentRequired());
        addButton("Check Ads Consent", v -> checkAdsConsent());
        addButton("Check Analytics Consent", v -> checkAnalyticsConsent());
        addButton("Get Consent Summary", v -> getConsentSummary());
        addButton("Enable Debug Mode", v -> enableDebugMode());
        addButton("Reset Consent", v -> resetConsent());

        addTextView("");

        addTextView("Ad Testing (requires consent):");
        addButton("Show Image Ad (if consent)", v -> showImageAdIfConsent());
        addButton("Show Video Ad (if consent)", v -> showVideoAdIfConsent());
        addButton("Show Native Ad (if consent)", v -> showNativeAdIfConsent());

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

    private void addButton(String text, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(text);
        button.setOnClickListener(listener);
        buttonContainer.addView(button);
    }

    private void requestConsentInfoUpdate() {
        if (!BidscubeSDK.isInitialized()) {
            Toast.makeText(this, "SDK not initialized", Toast.LENGTH_SHORT).show();
            return;
        }

        Toast.makeText(this, "Requesting consent info update...", Toast.LENGTH_SHORT).show();

        BidscubeSDK.requestConsentInfoUpdate(new ConsentCallback() {
            @Override
            public void onConsentInfoUpdated() {
                Log.d(TAG, "Consent info updated successfully");
                runOnUiThread(() -> {
                    Toast.makeText(ConsentTestActivity.this, "Consent info updated", Toast.LENGTH_SHORT).show();
                    updateConsentStatus();
                });
            }

            @Override
            public void onConsentInfoUpdateFailed(Exception error) {

            }

            @Override
            public void onConsentFormShown() {
                Log.d(TAG, "Consent form shown");
                runOnUiThread(() -> Toast.makeText(ConsentTestActivity.this, "Consent form shown", Toast.LENGTH_SHORT).show());
            }

            @Override
            public void onConsentFormError(Exception formError) {

            }

            @Override
            public void onConsentGranted() {
                Log.d(TAG, "Consent granted");
                runOnUiThread(() -> {
                    Toast.makeText(ConsentTestActivity.this, "Consent granted! Can show ads.", Toast.LENGTH_LONG).show();
                    updateConsentStatus();
                });
            }

            @Override
            public void onConsentDenied() {
                Log.d(TAG, "Consent denied");
                runOnUiThread(() -> {
                    Toast.makeText(ConsentTestActivity.this, "Consent denied. Cannot show ads.", Toast.LENGTH_LONG).show();
                    updateConsentStatus();
                });
            }

            @Override
            public void onConsentNotRequired() {
                Log.d(TAG, "Consent not required");
                runOnUiThread(() -> {
                    Toast.makeText(ConsentTestActivity.this, "Consent not required. Can show ads.", Toast.LENGTH_LONG).show();
                    updateConsentStatus();
                });
            }

            @Override
            public void onConsentStatusChanged(boolean hasConsent) {
                Log.d(TAG, "Consent status changed: " + hasConsent);
                runOnUiThread(() -> {
                    Toast.makeText(ConsentTestActivity.this, "Consent status: " + (hasConsent ? "Granted" : "Denied"), Toast.LENGTH_SHORT).show();
                    updateConsentStatus();
                });
            }
        });
    }

    private void showConsentForm() {
        if (!BidscubeSDK.isInitialized()) {
            Toast.makeText(this, "SDK not initialized", Toast.LENGTH_SHORT).show();
            return;
        }

        Toast.makeText(this, "Showing consent form...", Toast.LENGTH_SHORT).show();

        BidscubeSDK.showConsentForm(new ConsentCallback() {
            @Override
            public void onConsentInfoUpdated() {

            }

            @Override
            public void onConsentInfoUpdateFailed(Exception error) {

            }


            @Override
            public void onConsentFormShown() {
                Log.d(TAG, "Consent form displayed");
                runOnUiThread(() -> Toast.makeText(ConsentTestActivity.this, "Consent form displayed", Toast.LENGTH_SHORT).show());
            }

            @Override
            public void onConsentFormError(Exception formError) {

            }


            @Override
            public void onConsentGranted() {
                Log.d(TAG, "Consent granted through form");
                runOnUiThread(() -> {
                    Toast.makeText(ConsentTestActivity.this, "Consent granted through form!", Toast.LENGTH_LONG).show();
                    updateConsentStatus();
                });
            }

            @Override
            public void onConsentDenied() {
                Log.d(TAG, "Consent denied through form");
                runOnUiThread(() -> {
                    Toast.makeText(ConsentTestActivity.this, "Consent denied through form.", Toast.LENGTH_LONG).show();
                    updateConsentStatus();
                });
            }

            @Override
            public void onConsentNotRequired() {
                Log.d(TAG, "Consent not required");
                runOnUiThread(() -> {
                    Toast.makeText(ConsentTestActivity.this, "Consent not required", Toast.LENGTH_SHORT).show();
                    updateConsentStatus();
                });
            }

            @Override
            public void onConsentStatusChanged(boolean hasConsent) {
                Log.d(TAG, "Consent status changed: " + hasConsent);
                runOnUiThread(() -> {
                    Toast.makeText(ConsentTestActivity.this, "Consent status changed: " + (hasConsent ? "Granted" : "Denied"), Toast.LENGTH_SHORT).show();
                    updateConsentStatus();
                });
            }
        });
    }

    private void checkConsentRequired() {
        if (!BidscubeSDK.isInitialized()) {
            Toast.makeText(this, "SDK not initialized", Toast.LENGTH_SHORT).show();
            return;
        }

        boolean isRequired = BidscubeSDK.isConsentRequired();
        String message = "Consent required: " + isRequired;
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        Log.d(TAG, message);
    }

    private void checkAdsConsent() {
        if (!BidscubeSDK.isInitialized()) {
            Toast.makeText(this, "SDK not initialized", Toast.LENGTH_SHORT).show();
            return;
        }

        boolean hasConsent = BidscubeSDK.hasAdsConsent();
        String message = "Ads consent: " + hasConsent;
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        Log.d(TAG, message);
    }

    private void checkAnalyticsConsent() {
        if (!BidscubeSDK.isInitialized()) {
            Toast.makeText(this, "SDK not initialized", Toast.LENGTH_SHORT).show();
            return;
        }

        boolean hasConsent = BidscubeSDK.hasAnalyticsConsent();
        String message = "Analytics consent: " + hasConsent;
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        Log.d(TAG, message);
    }

    private void getConsentSummary() {
        if (!BidscubeSDK.isInitialized()) {
            Toast.makeText(this, "SDK not initialized", Toast.LENGTH_SHORT).show();
            return;
        }

        String summary = BidscubeSDK.getConsentStatusSummary();
        Log.d(TAG, "Consent Summary:\n" + summary);

        String shortSummary = summary.length() > 100 ? summary.substring(0, 100) + "..." : summary;
        Toast.makeText(this, "Consent Summary (see logs): " + shortSummary, Toast.LENGTH_LONG).show();
    }

    private void enableDebugMode() {
        if (!BidscubeSDK.isInitialized()) {
            Toast.makeText(this, "SDK not initialized", Toast.LENGTH_SHORT).show();
            return;
        }

        BidscubeSDK.enableConsentDebugMode("test_device_123");
        Toast.makeText(this, "Debug mode enabled for test device", Toast.LENGTH_SHORT).show();
        Log.d(TAG, "Consent debug mode enabled");
    }

    private void resetConsent() {
        if (!BidscubeSDK.isInitialized()) {
            Toast.makeText(this, "SDK not initialized", Toast.LENGTH_SHORT).show();
            return;
        }

        BidscubeSDK.resetConsent();
        Toast.makeText(this, "Consent information reset", Toast.LENGTH_SHORT).show();
        Log.d(TAG, "Consent information reset");

        updateConsentStatus();
    }

    private void showImageAdIfConsent() {
        if (!BidscubeSDK.isInitialized()) {
            Toast.makeText(this, "SDK not initialized", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!BidscubeSDK.hasAdsConsent()) {
            Toast.makeText(this, "No ads consent. Request consent first.", Toast.LENGTH_LONG).show();
            return;
        }

        Toast.makeText(this, "Showing image ad...", Toast.LENGTH_SHORT).show();

        AdCallback callback = new AdCallback() {
            @Override
            public void onAdLoading(String placementId) {
                Log.d(TAG, "Image ad loading: " + placementId);
            }

            @Override
            public void onAdLoaded(String placementId) {
                Log.d(TAG, "Image ad loaded: " + placementId);
                runOnUiThread(() -> Toast.makeText(ConsentTestActivity.this, "Image ad loaded successfully", Toast.LENGTH_SHORT).show());
            }

            @Override
            public void onAdDisplayed(String placementId) {
                Log.d(TAG, "Image ad displayed: " + placementId);
            }

            @Override
            public void onAdClicked(String placementId) {
                Log.d(TAG, "Image ad clicked: " + placementId);
            }

            @Override
            public void onAdClosed(String placementId) {
                Log.d(TAG, "Image ad closed: " + placementId);
            }

            @Override
            public void onAdFailed(String placementId, int errorCode, String errorMessage) {
                Log.e(TAG, "Image ad failed: " + placementId + " - " + errorMessage);
                runOnUiThread(() -> Toast.makeText(ConsentTestActivity.this, "Image ad failed: " + errorMessage, Toast.LENGTH_LONG).show());
            }
        };

        BidscubeSDK.showImageAdWindowed("19481", callback);
    }

    private void showVideoAdIfConsent() {
        if (!BidscubeSDK.isInitialized()) {
            Toast.makeText(this, "SDK not initialized", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!BidscubeSDK.hasAdsConsent()) {
            Toast.makeText(this, "No ads consent. Request consent first.", Toast.LENGTH_LONG).show();
            return;
        }

        Toast.makeText(this, "Showing video ad...", Toast.LENGTH_SHORT).show();

        AdCallback callback = new AdCallback() {
            @Override
            public void onAdLoading(String placementId) {
                Log.d(TAG, "Video ad loading: " + placementId);
            }

            @Override
            public void onAdLoaded(String placementId) {
                Log.d(TAG, "Video ad loaded: " + placementId);
                runOnUiThread(() -> Toast.makeText(ConsentTestActivity.this, "Video ad loaded successfully", Toast.LENGTH_SHORT).show());
            }

            @Override
            public void onAdDisplayed(String placementId) {
                Log.d(TAG, "Video ad displayed: " + placementId);
            }

            @Override
            public void onAdClicked(String placementId) {
                Log.d(TAG, "Video ad clicked: " + placementId);
            }

            @Override
            public void onAdClosed(String placementId) {
                Log.d(TAG, "Video ad closed: " + placementId);
            }

            @Override
            public void onAdFailed(String placementId, int errorCode, String errorMessage) {
                Log.e(TAG, "Video ad failed: " + placementId + " - " + errorMessage);
                runOnUiThread(() -> Toast.makeText(ConsentTestActivity.this, "Video ad failed: " + errorMessage, Toast.LENGTH_LONG).show());
            }
        };

        BidscubeSDK.showVideoAdWindowed("19483", callback);
    }

    private void showNativeAdIfConsent() {
        if (!BidscubeSDK.isInitialized()) {
            Toast.makeText(this, "SDK not initialized", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!BidscubeSDK.hasAdsConsent()) {
            Toast.makeText(this, "No ads consent. Request consent first.", Toast.LENGTH_LONG).show();
            return;
        }

        Toast.makeText(this, "Showing native ad...", Toast.LENGTH_SHORT).show();

        AdCallback callback = new AdCallback() {
            @Override
            public void onAdLoading(String placementId) {
                Log.d(TAG, "Native ad loading: " + placementId);
            }

            @Override
            public void onAdLoaded(String placementId) {
                Log.d(TAG, "Native ad loaded: " + placementId);
                runOnUiThread(() -> Toast.makeText(ConsentTestActivity.this, "Native ad loaded successfully", Toast.LENGTH_SHORT).show());
            }

            @Override
            public void onAdDisplayed(String placementId) {
                Log.d(TAG, "Native ad displayed: " + placementId);
            }

            @Override
            public void onAdClicked(String placementId) {
                Log.d(TAG, "Native ad clicked: " + placementId);
            }

            @Override
            public void onAdClosed(String placementId) {
                Log.d(TAG, "Native ad closed: " + placementId);
            }

            @Override
            public void onAdFailed(String placementId, int errorCode, String errorMessage) {
                Log.e(TAG, "Native ad failed: " + placementId + " - " + errorMessage);
                runOnUiThread(() -> Toast.makeText(ConsentTestActivity.this, "Native ad failed: " + errorMessage, Toast.LENGTH_LONG).show());
            }
        };

        BidscubeSDK.showNativeAdWindowed("19487", callback);
    }

    private void updateConsentStatus() {
        if (!BidscubeSDK.isInitialized()) {
            return;
        }

        StringBuilder status = new StringBuilder();
        status.append("SDK Status: Initialized\n");
        status.append("Consent Required: ").append(BidscubeSDK.isConsentRequired()).append("\n");
        status.append("Ads Consent: ").append(BidscubeSDK.hasAdsConsent()).append("\n");
        status.append("Analytics Consent: ").append(BidscubeSDK.hasAnalyticsConsent());

        statusText.setText(status.toString());
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


