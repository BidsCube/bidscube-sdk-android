package com.bidscube.sdk.device.providers;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.WindowManager;

import androidx.core.util.Consumer;

import com.bidscube.sdk.consent.ConsentManager;
import com.bidscube.sdk.models.AdInfo;
import com.bidscube.sdk.models.DeviceInfo;
import com.google.android.gms.ads.identifier.AdvertisingIdClient;

import java.util.Locale;

public class DeviceInfoProvider {

    private final Context context;
    private final ConsentManager consentManager;

    public DeviceInfoProvider(Context context) {
        this.context = context.getApplicationContext();
        this.consentManager = new ConsentManager(context);
    }

    public void getDeviceInfoAsync(Consumer<DeviceInfo> callback) {
        String bundle = context.getPackageName();
        String appName = getAppName();
        String appStoreUrl = "https://play.google.com/store/apps/details?id=" + bundle;
        String language = Locale.getDefault().getLanguage();
        DisplayMetrics metrics = getDisplayMetrics();
        int deviceWidth = metrics.widthPixels;
        int deviceHeight = metrics.heightPixels;
        String userAgent = System.getProperty("http.agent", "Android");
        String appVersion = getAppVersion();

        int gdprApplies = consentManager.getGdprApplies();
        String consentString = consentManager.getGdprConsentString();
        String addtlConsent = consentManager.getAdditionalConsent();
        String gppString = consentManager.getGppString();
        String usPrivacy = consentManager.getUsPrivacyString();

        Log.d("DeviceInfoProvider", "GDPR Applies: " + gdprApplies);
        Log.d("DeviceInfoProvider", "Consent String: " + consentString);
        Log.d("DeviceInfoProvider", "Additional Consent: " + addtlConsent);
        Log.d("DeviceInfoProvider", "GPP String: " + gppString);
        Log.d("DeviceInfoProvider", "US Privacy: " + usPrivacy);

        new Thread(() -> {
            AdInfo adInfo;
            try {
                AdvertisingIdClient.Info idInfo = AdvertisingIdClient.getAdvertisingIdInfo(context);
                adInfo = new AdInfo(idInfo.getId(), idInfo.isLimitAdTrackingEnabled());
            } catch (Exception e) {
                Log.e("DeviceInfoProvider", "Failed to get Advertising ID", e);
                adInfo = new AdInfo(null, false);
            }

            final int dnt = adInfo.isLimitAdTracking() ? 1 : 0;
            final String ifa = adInfo.getAdId();

            DeviceInfo deviceInfo = new DeviceInfo(
                    bundle,
                    appName,
                    appStoreUrl,
                    language,
                    deviceWidth,
                    deviceHeight,
                    userAgent,
                    ifa,
                    dnt,
                    appVersion,
                    gdprApplies,
                    consentString,
                    usPrivacy,
                    false
            );

            new Handler(Looper.getMainLooper()).post(() -> callback.accept(deviceInfo));
        }).start();
    }

    /**
     * Get device info with current consent status
     * This method can be called after consent has been updated
     */
    public void getDeviceInfoWithCurrentConsent(Consumer<DeviceInfo> callback) {

        getDeviceInfoAsync(callback);
    }

    /**
     * Get the ConsentManager instance for direct access
     * @return ConsentManager instance
     */
    public ConsentManager getConsentManager() {
        return consentManager;
    }

    private String getAppName() {
        try {
            PackageManager pm = context.getPackageManager();
            return pm.getApplicationLabel(pm.getApplicationInfo(context.getPackageName(), 0)).toString();
        } catch (PackageManager.NameNotFoundException e) {
            return "";
        }
    }

    private String getAppVersion() {
        try {
            PackageInfo pInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            return pInfo.versionName;
        } catch (PackageManager.NameNotFoundException e) {
            return "";
        }
    }

    private DisplayMetrics getDisplayMetrics() {
        WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        DisplayMetrics metrics = new DisplayMetrics();
        if (wm != null && wm.getDefaultDisplay() != null) {
            wm.getDefaultDisplay().getMetrics(metrics);
        }
        return metrics;
    }
}
