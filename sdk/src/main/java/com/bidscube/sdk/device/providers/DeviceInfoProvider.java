package com.bidscube.sdk.device.providers;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.WindowManager;

import androidx.core.util.Consumer;
import androidx.preference.PreferenceManager;

import com.bidscube.sdk.device.models.AdInfo;
import com.bidscube.sdk.device.models.DeviceInfo;
import com.google.android.gms.ads.identifier.AdvertisingIdClient;

import java.util.Locale;

public class DeviceInfoProvider {

    private final Context context;

    public DeviceInfoProvider(Context context) {
        this.context = context.getApplicationContext();
    }

    public void getDeviceInfoAsync(Consumer<DeviceInfo> callback) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);

        String bundle = context.getPackageName();
        String appName = getAppName();
        String appStoreUrl = "https://play.google.com/store/apps/details?id=" + bundle;
        String language = Locale.getDefault().getLanguage();
        DisplayMetrics metrics = getDisplayMetrics();
        int deviceWidth = metrics.widthPixels;
        int deviceHeight = metrics.heightPixels;
        String userAgent = System.getProperty("http.agent", "Android");
        String appVersion = getAppVersion();

        // GDPR Applies
        int gdprApplies = prefs.getInt("IABTCF_gdprApplies", -1);

        // Consent String
        String consentString = prefs.getString("IABTCF_TCString", "");

        // Additional Consent
        String addtlConsent = prefs.getString("IABTCF_AddtlConsent", "");

        // GPP String (if available)
        String gppString = prefs.getString("IABGPP_HDR_GppString", "");

        Log.d("GDPR", "gdprApplies: " + gdprApplies);
        Log.d("GDPR", "consentString: " + consentString);
        Log.d("GDPR", "addtlConsent: " + addtlConsent);
        Log.d("GDPR", "gppString: " + gppString);

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

            // Banner and Video device info
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
                    appVersion
            );

            DeviceInfo nativeDeviceInfo = new DeviceInfo(
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
                    0,
                    "",
                    "1YNN",
                    false
            );

            new Handler(Looper.getMainLooper()).post(() -> callback.accept(deviceInfo));
        }).start();
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
