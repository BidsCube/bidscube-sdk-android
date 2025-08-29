package com.bidscube.sdk.consent;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.provider.Settings;
import android.util.Log;

import com.google.android.ump.ConsentDebugSettings;
import com.google.android.ump.ConsentForm;
import com.google.android.ump.ConsentInformation;
import com.google.android.ump.ConsentRequestParameters;
import com.google.android.ump.FormError;
import com.google.android.ump.UserMessagingPlatform;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Manages user consent for GDPR and CCPA compliance using Google's User Messaging Platform (UMP)
 * This class handles consent collection, storage, and retrieval for privacy compliance
 */
public class ConsentManager {

    private static final String TAG = "ConsentManager";

    private static final String PREF_IABTCF_TCSTRING = "IABTCF_TCString";
    private static final String PREF_IABTCF_GDPRAPPLIES = "IABTCF_gdprApplies";
    private static final String PREF_IABTCF_ADDTLCONSENT = "IABTCF_AddtlConsent";
    private static final String PREF_IABGPP_HDR_GPPSTRING = "IABGPP_HDR_GppString";
    private static final String PREF_US_PRIVACY_STRING = "USPrivacy_String";

    private final Context context;
    private final ConsentInformation consentInformation;
    private final SharedPreferences sharedPreferences;

    private ConsentForm consentForm;
    private final AtomicBoolean isConsentFormAvailable = new AtomicBoolean(false);

    public ConsentManager(Context context) {
        this.context = context.getApplicationContext();
        this.consentInformation = UserMessagingPlatform.getConsentInformation(context);
        this.sharedPreferences = context.getSharedPreferences("ConsentManager", Context.MODE_PRIVATE);
    }

    /**
     * Check if consent form is available
     *
     * @return true if consent form is available, false otherwise
     */
    public boolean isConsentFormAvailable() {
        return isConsentFormAvailable.get();
    }

    /**
     * Check if user is in EEA or UK (GDPR applies)
     *
     * @return true if GDPR applies, false otherwise
     */
    public boolean isGdprApplicable() {
        return consentInformation.getConsentStatus() == ConsentInformation.ConsentStatus.REQUIRED ||
                consentInformation.getConsentStatus() == ConsentInformation.ConsentStatus.OBTAINED;
    }

    /**
     * Check if user is in California (CCPA applies)
     *
     * @return true if CCPA applies, false otherwise
     */
    public boolean isCcpaApplicable() {


        String language = context.getResources().getConfiguration().locale.getCountry();
        return "US".equals(language) || "CA".equals(language);
    }

    /**
     * Request consent information update
     *
     * @param activity Current activity
     * @param callback Callback for consent information update
     */
    public void requestConsentInfoUpdate(Activity activity, ConsentInfoUpdateCallback callback) {

        String androidId = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);


        ConsentDebugSettings debugSettings = new ConsentDebugSettings.Builder(context)
                .setDebugGeography(ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_EEA)
                .addTestDeviceHashedId(androidId)//"1234567890ABCDEF1234567890ABCDEF")
                .build();

        ConsentRequestParameters params = new ConsentRequestParameters.Builder()
                .setTagForUnderAgeOfConsent(false)
                .setConsentDebugSettings(debugSettings)
                .build();

        Log.d(TAG, "Hash id " + androidId);
        consentInformation.requestConsentInfoUpdate(
                activity,
                params,
                () -> {
                    Log.d(TAG, "Consent info updated successfully");
                    isConsentFormAvailable.set(consentInformation.isConsentFormAvailable());
                    callback.onConsentInfoUpdated();
                },
                requestConsentError -> {
                    Log.e(TAG, "Failed to update consent info: " + requestConsentError.getMessage());
                }
        );
    }

    /**
     * Load and show consent form
     *
     * @param activity Current activity
     * @param callback Callback for consent form events
     */
    public void loadAndShowConsentForm(Activity activity, ConsentFormCallback callback) {
        if (!isConsentFormAvailable.get()) {
            Log.w(TAG, "Consent form not available");
            callback.onConsentFormError(new FormError(1, "Consent form not available"));
            return;
        }

        UserMessagingPlatform.loadConsentForm(
                context,
                consentForm -> {
                    this.consentForm = consentForm;
                },
                loadError -> {
                    Log.e(TAG, "Failed to load consent form: " + loadError.getMessage());
                    callback.onConsentFormError(new FormError(1, "Failed to load consent form"));
                }
        );
    }

    /**
     * Get GDPR consent string
     *
     * @return GDPR consent string or empty string if not available
     */
    public String getGdprConsentString() {
        return sharedPreferences.getString(PREF_IABTCF_TCSTRING, "");
    }

    /**
     * Get GDPR applies flag
     *
     * @return 1 if GDPR applies, 0 if not, -1 if unknown
     */
    public int getGdprApplies() {
        return sharedPreferences.getInt(PREF_IABTCF_GDPRAPPLIES, -1);
    }

    /**
     * Get additional consent string
     *
     * @return Additional consent string or empty string if not available
     */
    public String getAdditionalConsent() {
        return sharedPreferences.getString(PREF_IABTCF_ADDTLCONSENT, "");
    }

    /**
     * Get GPP string
     *
     * @return GPP string or empty string if not available
     */
    public String getGppString() {
        return sharedPreferences.getString(PREF_IABGPP_HDR_GPPSTRING, "");
    }

    /**
     * Get US Privacy string (CCPA)
     *
     * @return US Privacy string or default value if not available
     */
    public String getUsPrivacyString() {
        return sharedPreferences.getString(PREF_US_PRIVACY_STRING, "");
    }

    /**
     * Reset consent information (for testing purposes)
     */
    public void resetConsent() {
        consentInformation.reset();
        sharedPreferences.edit().clear().apply();
        isConsentFormAvailable.set(false);
        Log.d(TAG, "Consent information reset");
    }

    /**
     * Store consent data in SharedPreferences
     * This method should be called when consent is obtained
     */
    private void storeConsentData() {
        SharedPreferences.Editor editor = sharedPreferences.edit();

        editor.putString(PREF_IABTCF_TCSTRING, getGdprConsentString());
        editor.putInt(PREF_IABTCF_GDPRAPPLIES, getGdprApplies());
        editor.putString(PREF_IABTCF_ADDTLCONSENT, getAdditionalConsent());
        editor.putString(PREF_IABGPP_HDR_GPPSTRING, getGppString());

        editor.putString(PREF_US_PRIVACY_STRING, getUsPrivacyString());

        editor.apply();
        Log.d(TAG, "Consent data stored in SharedPreferences");
    }

    /**
     * Callback for consent information updates
     */
    public interface ConsentInfoUpdateCallback {
        void onConsentInfoUpdated();
    }

    /**
     * Callback for consent form events
     */
    public interface ConsentFormCallback {
        void onConsentFormError(FormError formError);
    }

    /**
     * Get a summary of current consent status for debugging
     *
     * @return String representation of consent status
     */
    public String getConsentSummary() {
        StringBuilder summary = new StringBuilder();
        summary.append("GDPR Applies: ").append(getGdprApplies()).append("\n");
        summary.append("GDPR Consent: ").append(getGdprConsentString()).append("\n");
        summary.append("Additional Consent: ").append(getAdditionalConsent()).append("\n");
        summary.append("GPP String: ").append(getGppString()).append("\n");
        summary.append("US Privacy: ").append(getUsPrivacyString()).append("\n");
        summary.append("Form Available: ").append(isConsentFormAvailable.get());

        return summary.toString();
    }
}

