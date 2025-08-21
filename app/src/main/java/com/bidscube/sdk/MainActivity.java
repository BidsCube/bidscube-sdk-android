package com.bidscube.sdk;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.net.Uri;
import android.os.Bundle;
import android.util.TypedValue;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.media3.common.util.UnstableApi;

import com.bidscube.sdk.ads.NativeAdType;
import com.bidscube.sdk.ads.VideoAdType;
import com.bidscube.sdk.device.providers.DeviceInfoProvider;
import com.bidscube.sdk.ads.ImageAdType;
import com.bidscube.sdk.ads.AdType;
import com.bidscube.sdk.device.models.DeviceInfo;
import com.bidscube.sdk.httpProvider.HttpProvider;
import com.bidscube.sdk.models.Callback;
import com.bidscube.sdk.view.BannerViewFactory;
import com.bidscube.sdk.view.IMAPlayerHandler;

@UnstableApi
public class MainActivity extends Activity {
    private IMAPlayerHandler imaPlayerHandler = null;
    private WebView currentBanner = null;
    private LinearLayout layout = null;

    @SuppressLint("SetTextI18n")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        DeviceInfoProvider provider = new DeviceInfoProvider(this);
        provider.getDeviceInfoAsync(deviceInfo -> runOnUiThread(() -> buildView(deviceInfo)));
    }

    @SuppressLint("SetTextI18n")
    private void buildView(DeviceInfo deviceInfo) {

        TextView usPrivacyTv = new TextView(this);
        TextView ccpaTv = new TextView(this);
        TextView coppaTv = new TextView(this);
        TextView gdprTv = new TextView(this);
        TextView gdprConsentTv = new TextView(this);
        ScrollView scrollView = new ScrollView(this);

        layout = new LinearLayout(this);
        layout.setPadding(16, 32, 16, 32);
        layout.setOrientation(LinearLayout.VERTICAL);
        scrollView.addView(layout);
        setContentView(scrollView);

        addTextView(layout, "Bundle: " + deviceInfo.getBundle());
        addTextView(layout, "App Name: " + deviceInfo.getAppName());
        addTextView(layout, "App Store URL: " + deviceInfo.getAppStoreUrl());
        addTextView(layout, "Language: " + deviceInfo.getLanguage());
        addTextView(layout, "Device Width: " + deviceInfo.getDeviceWidth());
        addTextView(layout, "Device Height: " + deviceInfo.getDeviceHeight());
        addTextView(layout, "User Agent: " + deviceInfo.getUserAgent());
        addTextView(layout, "IFA: " + deviceInfo.getIfa());
        addTextView(layout, "DNT: " + deviceInfo.getDnt());
        addTextView(layout, "App Version: " + deviceInfo.getAppVersion());
        TextView space = new TextView(this);
        LinearLayout.LayoutParams spaceParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_PX, 15, getResources().getDisplayMetrics())
        );
        space.setLayoutParams(spaceParams);
        layout.addView(space);

        TextView space1 = new TextView(this);
        LinearLayout.LayoutParams spaceParams1 = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_PX, 30, getResources().getDisplayMetrics())
        );
        space.setLayoutParams(spaceParams1);
        layout.addView(space1);

        // Request URL TextView
        TextView requestUrlText = new TextView(this);
        layout.addView(requestUrlText);

        Button adImageBtn = new Button(this);
        adImageBtn.setText("Ad Image Request");
        layout.addView(adImageBtn);

        Button adVideoBtn = new Button(this);
        adVideoBtn.setText("Ad Video Request");
        layout.addView(adVideoBtn);

        Button nativeVideoBtn = new Button(this);
        nativeVideoBtn.setText("Ad Native Request");
        layout.addView(nativeVideoBtn);

        // Response TextView
        TextView responseText = new TextView(this);
        layout.addView(responseText);

        // Image ad click
        adImageBtn.setOnClickListener(v -> {
            AdType imageAd = new ImageAdType("19481");
            String url = Uri.decode(imageAd.buildRequestUrl(deviceInfo).toString());
            requestUrlText.setText("Request URL: " + url);

            HttpProvider.sendRequest(url, "GET", new Callback() {
                @Override
                public void onSuccess(int responseCode, String responseBody) {
                    runOnUiThread(() -> {
                        // Remove previous banner if exists
                        if (currentBanner != null) {
                            layout.removeView(currentBanner);
                            currentBanner.destroy();
                            currentBanner = null;
                        }
                        // Create and add new banner above responseText
                        currentBanner = BannerViewFactory.createBanner(MainActivity.this, responseBody);
                        int responseTextIndex = layout.indexOfChild(responseText);
                        layout.addView(currentBanner, responseTextIndex);
                        responseText.setText("Response code: " + responseCode + "\nResponse: " + responseBody);
                    });
                }

                @Override
                public void onFail(Exception e) {
                    runOnUiThread(() -> responseText.setText("Error: " + e.getMessage()));
                }
            });
        });

        adVideoBtn.setOnClickListener(v -> {
            AdType videoAd = new VideoAdType("19483");
            String url = Uri.decode(videoAd.buildRequestUrl(deviceInfo).toString());
            requestUrlText.setText("Request URL: " + url);


            if (imaPlayerHandler != null) {
                layout.removeView(imaPlayerHandler);
                imaPlayerHandler.release();
                imaPlayerHandler = null;
            }

            if (imaPlayerHandler != null) {
                layout.removeView(imaPlayerHandler);
                imaPlayerHandler.release();
                imaPlayerHandler = null;
            }
            // Create and add new IMAPlayerHandler
            imaPlayerHandler = new IMAPlayerHandler(MainActivity.this);
            int heightPx = (int) TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP, 300, getResources().getDisplayMetrics());
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, heightPx);
            layout.addView(imaPlayerHandler, params);

            //String vastTagUrl = //"https://pubads.g.doubleclick.net/gampad/ads?sz=640x480&iu=/124319096/external/single_ad_samples&ciu_szs=300x250&impl=s&gdfp_req=1&env=vp&output=vast&unviewed_position_start=1&cust_params=deployment%3Ddevsite%26sample_ct%3Dlinear&correlator=";
            //String vastTagUrl = //"https://ssp-bcc-ads.com/?c=v&m=xml&id=19483&app=1&w=200&h=200&bundle=com.bidscube.testapp&name=testapp&app_version=1&ifa=AEBE52E7-03EE-455A-B3C4-E57283966239&dnt=0&app_store_url=https://play.google.com/store/apps/details?id=com.spotify.music%26hl=en&ua=Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/139.0.0.0 Safari/537.36&language=en&deviceWidth=1080&deviceHeight=1920";
            imaPlayerHandler.playVast(url);

        });

        nativeVideoBtn.setOnClickListener(v -> {
            AdType videoAd = new NativeAdType("19487");
            String url = Uri.decode(videoAd.buildRequestUrl(deviceInfo).toString());
            requestUrlText.setText("Request URL: " + url);

            HttpProvider.sendRequest(url, "GET", new Callback() {
                @Override
                public void onSuccess(int responseCode, String responseBody) {
                    runOnUiThread(() -> {
                        responseText.setText("Response code: " + responseCode + "\nResponse: " + responseBody);

                        usPrivacyTv.setText("us_privacy: " + deviceInfo.getUsPrivacy());
                        ccpaTv.setText("ccpa: " + deviceInfo.getUsPrivacy());
                        coppaTv.setText("coppa: " + deviceInfo.isCoppa());
                        gdprTv.setText("gdpr: " + deviceInfo.getGdpr());
                        gdprConsentTv.setText("gdpr_consent: " + deviceInfo.getGdprConsent());

                        if (usPrivacyTv.getParent() == null) layout.addView(usPrivacyTv);
                        if (ccpaTv.getParent() == null) layout.addView(ccpaTv);
                        if (coppaTv.getParent() == null) layout.addView(coppaTv);
                        if (gdprTv.getParent() == null) layout.addView(gdprTv);
                        if (gdprConsentTv.getParent() == null) layout.addView(gdprConsentTv);
                    });
                }

                @Override
                public void onFail(Exception e) {
                    runOnUiThread(() -> responseText.setText("Error: " + e.getMessage()));
                }
            });
        });
    }

    private void addTextView(LinearLayout layout, String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        layout.addView(tv);
    }
}