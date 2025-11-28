# Bidscube SDK for Android

A comprehensive Android SDK for displaying various types of ads including image ads, video ads, native ads, and skippable video ads with GDPR/CCPA compliance.

## 📱 Features

- **Multiple Ad Types**: Image, Video, Native, and Skippable Video ads
- **Display Modes**: Full-screen and windowed display options (auto-selected by server response, with manual override)
- **Ad Positioning**: Control ad placement (header, footer, sidebar, above/below fold)
- **Consent Management**: Built-in GDPR and CCPA compliance
-
## 🚀 Quick Start

### 1. Add Dependency

Add the SDK dependency to your `app/build.gradle` (Kotlin DSL shown):

```kotlin
dependencies {
    implementation("com.bidscube:bidscube-sdk:1.0.1")
}
```

Make sure you have repositories configured:

```kotlin
repositories {
    google()
    mavenCentral()
}
```

The SDK will bring required libraries transitively (Google UMP, Ads Identifier, Media3, etc.).

### 2. Initialize SDK

```java
import com.bidscube.sdk.BidscubeSDK;
import com.bidscube.sdk.config.SDKConfig;

SDKConfig config = new SDKConfig.Builder(this)
    .enableLogging(true)
    .enableDebugMode(false)
    .defaultAdTimeout(30000)
    .defaultAdPosition("UNKNOWN")
    .build();

BidscubeSDK.initialize(this, config);
```

**✨ Automatic App Detection**: The SDK automatically detects your app's ID, name, version, language, and user agent from the Android manifest and system.

### 3. Show Your First Ad

```java
import com.bidscube.sdk.interfaces.AdCallback;

AdCallback callback = new AdCallback() {
    @Override
    public void onAdLoaded(String placementId) {}

    @Override
    public void onAdFailed(String placementId, int errorCode, String errorMessage) {}
};

// Display mode is determined by server response position
BidscubeSDK.showImageAd("19481", callback);
```

## 📋 Ad Types

### Image Ads

```java
BidscubeSDK.showImageAd("19481", callback);

// Or get a View to embed in your layout
View imageView = BidscubeSDK.getImageAdView("19481", callback);
```

### Video Ads

```java
BidscubeSDK.showVideoAd("19483", callback);

// Skippable video with custom CTA text
BidscubeSDK.showSkippableVideoAd("19483", "Install Now", callback);

// Or get a View to embed
View videoView = BidscubeSDK.getVideoAdView("19483", callback);
```

### Native Ads

```java
BidscubeSDK.showNativeAd("19487", callback);

// Or get a View to embed
View nativeView = BidscubeSDK.getNativeAdView("19487", callback);
```

## 🎯 Ad Positioning

The SDK supports automatic ad positioning based on server response, with manual override capability:

### Response-Based Positioning (Automatic)

Ads are automatically positioned based on the `position` field in the server response:

```java
BidscubeSDK.showImageAd("19481", callback);
BidscubeSDK.showVideoAd("19483", callback);
BidscubeSDK.showNativeAd("19487", callback);
```

**Response Position Values:**
- `0` - Unknown (natural display)
- `1` - Above the fold (top portion of screen)
- `2` - Maybe depending on screen size (smart positioning)
- `3` - Below the fold (bottom portion of screen)
- `4` - Header (top of screen)
- `5` - Footer (bottom of screen)
- `6` - Sidebar (left/right side)
- `7` - Full screen

### Manual Override (Optional)

You can manually override the response position:

```java
import com.bidscube.sdk.models.enums.AdPosition;

BidscubeSDK.setAdPosition(AdPosition.HEADER);
BidscubeSDK.setAdPosition(AdPosition.FOOTER);
BidscubeSDK.setAdPosition(AdPosition.SIDEBAR);
BidscubeSDK.setAdPosition(AdPosition.ABOVE_THE_FOLD);
BidscubeSDK.setAdPosition(AdPosition.BELOW_THE_FOLD);
BidscubeSDK.setAdPosition(AdPosition.UNKNOWN);

BidscubeSDK.showImageAd("19481", callback);
```

### Smart Positioning (Position 2)

When the response indicates position `2` (maybe depending on screen size):
- **Portrait mode**: Center positioning
- **Landscape mode**: Right side positioning

### Get Current Position

```java
import com.bidscube.sdk.models.enums.AdPosition;

AdPosition effectivePosition = BidscubeSDK.getEffectiveAdPosition();
AdPosition manualPosition = BidscubeSDK.getCurrentAdPosition();
AdPosition responsePosition = BidscubeSDK.getResponseAdPosition();
```

## 🔒 Consent Management

### Basic Consent Handling

```java
import com.bidscube.sdk.interfaces.ConsentCallback;

BidscubeSDK.requestConsentInfoUpdate(new ConsentCallback() {
    @Override
    public void onConsentInfoUpdated() {
        if (BidscubeSDK.isConsentRequired()) {
            // Within this callback, `this` is a ConsentCallback
            BidscubeSDK.showConsentForm(this);
        } else {
            showAds();
        }
    }

    @Override
    public void onConsentGranted() {
        showAds();
    }

    @Override
    public void onConsentDenied() {
        showAlternativeContent();
    }
});
```

## Override SDK rendering

`AdCallback` exposes `onAdRenderOverride` which receives the ADM payload, resolved ad position, and the SDK render type. Return `true` to prevent the SDK from showing its default dialogs/views and handle rendering yourself:

```kotlin
val callback = object : AdCallback {
    override fun onAdRenderOverride(context: AdRenderContext): Boolean {
        if (context.renderType == AdRenderType.NATIVE) {
            renderMyNativeCard(context.adm, context.position)
            return true
        }
        return false
    }
}
```


### Check Consent Status

```java
boolean isRequired = BidscubeSDK.isConsentRequired();
boolean hasAdsConsent = BidscubeSDK.hasAdsConsent();
boolean hasAnalyticsConsent = BidscubeSDK.hasAnalyticsConsent();
String summary = BidscubeSDK.getConsentStatusSummary();
```

### Debug Mode

```java
BidscubeSDK.enableConsentDebugMode("your_test_device_id");
BidscubeSDK.resetConsent();
```

## 🎨 Customization

### Custom CTA Button Text (Skippable Video)

```java
BidscubeSDK.showSkippableVideoAd("19483", "Shop Now", callback);
BidscubeSDK.showSkippableVideoAd("19483", "Learn More", callback);
BidscubeSDK.showSkippableVideoAd("19483", "Get Started", callback);
```

### Native Ad Styling

```java
import com.bidscube.sdk.view.NativeAdView;

NativeAdView nativeAdView = new NativeAdView(context);

nativeAdView.setCTAText("Shop Now");

nativeAdView.setCustomStyle(
    Color.WHITE,
    Color.BLACK,
    Color.parseColor("#FF5722")
);

nativeAdView.setCTAButton("Install Now", Color.BLUE, Color.WHITE);
```

## 📱 Complete Example

```java
public class MainActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        SDKConfig config = new SDKConfig.Builder(this)
            .enableLogging(true)
            .enableDebugMode(false)
            .build();

        BidscubeSDK.initialize(this, config);
        setupConsent();
    }

    private void setupConsent() {
        if (!BidscubeSDK.isInitialized()) return;

        BidscubeSDK.requestConsentInfoUpdate(new ConsentCallback() {
            @Override
            public void onConsentInfoUpdated() {
                if (BidscubeSDK.isConsentRequired()) {
                    BidscubeSDK.showConsentForm(this);
                } else {
                    showAds();
                }
            }

            @Override
            public void onConsentGranted() {
                showAds();
            }

            @Override
            public void onConsentDenied() {
                showAlternativeContent();
            }
        });
    }

    private void showAds() {
        if (!BidscubeSDK.hasAdsConsent()) return;

        AdCallback callback = new AdCallback() {
            @Override public void onAdLoaded(String placementId) {}
            @Override public void onAdFailed(String placementId, int errorCode, String errorMessage) {}
        };

        BidscubeSDK.showImageAd("19481", callback);
        BidscubeSDK.showVideoAd("19483", callback);
        BidscubeSDK.showNativeAd("19487", callback);
    }

    private void showAlternativeContent() {}

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (BidscubeSDK.isInitialized()) {
            BidscubeSDK.cleanup();
        }
    }
}
```

## 🔧 Configuration Options

### SDKConfig Builder

```java
SDKConfig config = new SDKConfig.Builder(this)
    .enableLogging(true)
    .enableDebugMode(false)
    .defaultAdTimeout(30000)
    .defaultAdPosition("UNKNOWN")
    .build();
```

**✨ Automatic Detection**: The SDK automatically detects:
- **App ID**: Package name from manifest
- **App Name**: Application label from manifest
- **App Version**: Version name/code from manifest
- **Language**: Device language setting
- **User Agent**: SDK version + device info

### Ad Position Options

- `UNKNOWN` - No position regulation, natural display
- `ABOVE_THE_FOLD` - Position above the fold
- `DEPEND ON SCREEN SIZE` - Depending on the screen size
- `BELOW_THE_FOLD` - Position below the fold
- `HEADER` - Position at top of screen
- `FOOTER` - Position at bottom of screen
- `SIDEBAR` - Position on side of screen
- `FULL_SCREEN` - Full screen ad

## 📊 Callback Methods

### AdCallback Interface

```java
public interface AdCallback {
    void onAdLoading(String placementId);
    void onAdLoaded(String placementId);
    void onAdDisplayed(String placementId);
    void onAdClicked(String placementId);
    void onAdClosed(String placementId);
    void onAdFailed(String placementId, int errorCode, String errorMessage);

    default void onVideoAdStarted(String placementId) {}
    default void onVideoAdCompleted(String placementId) {}
    default void onVideoAdSkipped(String placementId) {}
    default void onVideoAdSkippable(String placementId) {}
    default void onInstallButtonClicked(String placementId, String buttonText) {}
}
```

### ConsentCallback Interface

```java
public interface ConsentCallback {
    void onConsentInfoUpdated();
    void onConsentInfoUpdateFailed(Exception error);
    void onConsentFormShown();
    void onConsentFormError(Exception formError);
    void onConsentGranted();
    void onConsentDenied();
    void onConsentNotRequired();
    void onConsentStatusChanged(boolean hasConsent);
}
```

## 🧪 Testing

### Test Activities

The SDK includes several test activities for development:

- `SDKTestActivity` - Basic SDK functionality testing
- `ConsentTestActivity` - Consent management testing
- `WindowedAdTestActivity` - Windowed ad positioning testing

### Debug Mode

```java
BidscubeSDK.enableConsentDebugMode("test_device_123");

boolean isReady = BidscubeSDK.isInitialized();

import com.bidscube.sdk.models.enums.AdPosition;
AdPosition position = BidscubeSDK.getCurrentAdPosition();
```

## 🚨 Error Handling

### Common Error Scenarios

```java
try {
    BidscubeSDK.showImageAd("19481", callback);
} catch (IllegalStateException e) {
    // SDK not initialized
} catch (Exception e) {
    // Unexpected error
}
```

### Error Codes

- `-1` - General failure
- `-2` - Network error
- `-3` - Parsing error
- `-4` - Invalid placement ID

## 📱 Platform Requirements

- **Minimum SDK**: API 24 (Android 7.0)
- **Target/Compile SDK**: API 35 (Android 15)
- **Java Version**: 11+
- **Kotlin**: 2.0+

## 🔒 Permissions

Add these permissions to your `AndroidManifest.xml`:

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
<uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />
```

## 📚 Additional Resources

- **Test Activities**: Use the test activities for development and debugging
- **VAST Support**: Full VAST XML parsing for video ads
- **Native Ad Models**: Complete OpenRTB Native Ads specification support

## 🤝 Support

For support and questions:
- Check the example code and test activities
- Review the comprehensive logging output
- Ensure proper initialization and consent handling

## 📄 License

This SDK is protected under the MIT License. For full license terms, please refer to the LICENSE file included with this distribution.

