package com.bidscube.sdk.view;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;

import com.bidscube.sdk.models.video.VastCompanion;
import com.bidscube.sdk.network.TrackerPinger;
import com.bidscube.sdk.utils.SDKLogger;
import com.bumptech.glide.Glide;

/**
 * Shared post-roll end card UI for video interstitials.
 * Supports VAST Companion StaticResource, HTMLResource, and IFrameResource.
 */
public final class VideoInterstitialUiHelper {

    private static final String TAG = "VideoInterstitialUi";
    private static final String DEFAULT_CTA = "Install Now";
    private static final String DEFAULT_TITLE = "Test game android";
    private static final String DEFAULT_DOWNLOADS = "10 k";
    private static final String DEFAULT_PRICE = "FREE";
    private static final float DEFAULT_RATING = 4.5f;
    private static final String DEFAULT_PREVIEW_IMAGE =
            "https://www.gstatic.com/webp/gallery/3.jpg";

    private static final int COLOR_BG = 0xFFF2F2F2;
    private static final int COLOR_TEXT_PRIMARY = 0xFF1A1A1A;
    private static final int COLOR_TEXT_SECONDARY = 0xFF9E9E9E;
    private static final int COLOR_CTA = 0xFF007AFF;
    private static final int COLOR_STAR = 0xFFFF9800;

    private VideoInterstitialUiHelper() {
    }

    public static void showEndCard(
            Context context,
            BidscubeVideoAdPlayer player,
            FrameLayout container,
            @Nullable VideoInterstitialOverlay overlay,
            @Nullable String imageUrl,
            @Nullable String clickUrl,
            @Nullable Runnable onEndCardShown,
            @Nullable Runnable onEndCardClicked,
            @Nullable Runnable onClose) {
        showEndCard(
                context,
                player,
                container,
                overlay,
                imageUrl,
                clickUrl,
                DEFAULT_CTA,
                onEndCardShown,
                onEndCardClicked,
                onClose);
    }

    public static void showEndCard(
            Context context,
            BidscubeVideoAdPlayer player,
            FrameLayout container,
            @Nullable VideoInterstitialOverlay overlay,
            @Nullable String imageUrl,
            @Nullable String clickUrl,
            @Nullable String ctaText,
            @Nullable Runnable onEndCardShown,
            @Nullable Runnable onEndCardClicked,
            @Nullable Runnable onClose) {
        showEndCard(
                context,
                player,
                container,
                overlay,
                imageUrl,
                clickUrl,
                ctaText,
                DEFAULT_TITLE,
                DEFAULT_RATING,
                DEFAULT_DOWNLOADS,
                DEFAULT_PRICE,
                onEndCardShown,
                onEndCardClicked,
                onClose);
    }

    public static void showEndCard(
            Context context,
            BidscubeVideoAdPlayer player,
            FrameLayout container,
            @Nullable VideoInterstitialOverlay overlay,
            @Nullable String imageUrl,
            @Nullable String clickUrl,
            @Nullable String ctaText,
            @Nullable String appTitle,
            float rating,
            @Nullable String downloadCount,
            @Nullable String priceText,
            @Nullable Runnable onEndCardShown,
            @Nullable Runnable onEndCardClicked,
            @Nullable Runnable onClose) {
        VastCompanion companion = null;
        if (imageUrl != null && !imageUrl.trim().isEmpty()) {
            companion = new VastCompanion(
                    VastCompanion.ResourceType.STATIC,
                    imageUrl.trim(),
                    clickUrl,
                    null,
                    null,
                    0,
                    0);
        }
        showCompanionEndCard(
                context,
                player,
                container,
                overlay,
                companion,
                clickUrl,
                ctaText,
                appTitle,
                rating,
                downloadCount,
                priceText,
                onEndCardShown,
                onEndCardClicked,
                onClose);
    }

    public static void showCompanionEndCard(
            Context context,
            @Nullable BidscubeVideoAdPlayer player,
            FrameLayout container,
            @Nullable VideoInterstitialOverlay overlay,
            @Nullable VastCompanion companion,
            @Nullable String fallbackClickUrl,
            @Nullable Runnable onEndCardShown,
            @Nullable Runnable onEndCardClicked,
            @Nullable Runnable onClose) {
        showCompanionEndCard(
                context,
                player,
                container,
                overlay,
                companion,
                fallbackClickUrl,
                DEFAULT_CTA,
                DEFAULT_TITLE,
                DEFAULT_RATING,
                DEFAULT_DOWNLOADS,
                DEFAULT_PRICE,
                onEndCardShown,
                onEndCardClicked,
                onClose);
    }

    public static void showCompanionEndCard(
            Context context,
            @Nullable BidscubeVideoAdPlayer player,
            FrameLayout container,
            @Nullable VideoInterstitialOverlay overlay,
            @Nullable VastCompanion companion,
            @Nullable String fallbackClickUrl,
            @Nullable String ctaText,
            @Nullable String appTitle,
            float rating,
            @Nullable String downloadCount,
            @Nullable String priceText,
            @Nullable Runnable onEndCardShown,
            @Nullable Runnable onEndCardClicked,
            @Nullable Runnable onClose) {

        if (player != null) {
            try {
                player.release();
            } catch (Throwable ignored) {
            }
        }

        container.removeAllViews();
        container.setBackgroundColor(COLOR_BG);

        final String clickUrl = resolveClickUrl(companion, fallbackClickUrl);

        Runnable openLanding = () -> {
            if (companion != null) {
                TrackerPinger.pingUrls("companion_click", companion.getClickTrackingUrls());
            }
            if (onEndCardClicked != null) {
                onEndCardClicked.run();
            }
            if (clickUrl != null && !clickUrl.isEmpty()) {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(clickUrl));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
            }
        };

        FrameLayout closeHost;
        if (companion != null && companion.isHtml()) {
            closeHost = container;
            showWebCompanion(context, container, companion, true);
        } else if (companion != null && companion.isIframe()) {
            closeHost = container;
            showWebCompanion(context, container, companion, false);
        } else {
            closeHost = showStaticAppStoreEndCard(
                    context,
                    container,
                    companion != null ? companion.getStaticImageUrl() : null,
                    ctaText,
                    appTitle,
                    rating,
                    downloadCount,
                    priceText,
                    openLanding);
        }

        VideoInterstitialOverlay endCardOverlay = VideoInterstitialOverlay.attach(closeHost);
        endCardOverlay.showEndCardClose();
        endCardOverlay.setListener(new VideoInterstitialOverlay.Listener() {
            @Override
            public void onSkipRequested() {
            }

            @Override
            public void onCloseRequested() {
                if (onClose != null) {
                    onClose.run();
                }
            }
        });

        if (overlay != null) {
            try {
                overlay.detach();
            } catch (Throwable ignored) {
            }
        }

        if (companion != null) {
            TrackerPinger.pingUrls("companion_view", companion.getViewTrackingUrls());
        }
        if (onEndCardShown != null) {
            onEndCardShown.run();
        }
        SDKLogger.d(TAG, "Video end card displayed type="
                + (companion != null ? companion.getResourceType() : "none"));
    }

    /**
     * Keeps the last video frame visible and shows a manual close control.
     * Does not release the player.
     */
    public static void showLastFrameClose(
            @Nullable VideoInterstitialOverlay overlay,
            @Nullable Runnable onClose) {
        if (overlay == null) {
            return;
        }
        overlay.showCloseOnly();
        overlay.setListener(new VideoInterstitialOverlay.Listener() {
            @Override
            public void onSkipRequested() {
            }

            @Override
            public void onCloseRequested() {
                if (onClose != null) {
                    onClose.run();
                }
            }
        });
        overlay.bringToFront();
        SDKLogger.d(TAG, "Last-frame close control shown");
    }

    private static String resolveClickUrl(
            @Nullable VastCompanion companion,
            @Nullable String fallbackClickUrl) {
        if (companion != null
                && companion.getClickThroughUrl() != null
                && !companion.getClickThroughUrl().trim().isEmpty()) {
            return companion.getClickThroughUrl().trim();
        }
        if (fallbackClickUrl != null && !fallbackClickUrl.trim().isEmpty()) {
            return fallbackClickUrl.trim();
        }
        return null;
    }

    private static void showWebCompanion(
            Context context,
            FrameLayout container,
            VastCompanion companion,
            boolean inlineHtml) {
        WebView webView = new WebView(context);
        webView.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        }
        webView.setWebViewClient(new WebViewClient());
        container.addView(webView);

        String content = companion.getContent().trim();
        if (inlineHtml) {
            if (content.startsWith("http://") || content.startsWith("https://")) {
                webView.loadUrl(content);
            } else {
                webView.loadDataWithBaseURL(null, content, "text/html", "utf-8", null);
            }
        } else {
            webView.loadUrl(content);
        }
    }

    private static FrameLayout showStaticAppStoreEndCard(
            Context context,
            FrameLayout container,
            @Nullable String imageUrl,
            @Nullable String ctaText,
            @Nullable String appTitle,
            float rating,
            @Nullable String downloadCount,
            @Nullable String priceText,
            Runnable openLanding) {

        int screenHeight = context.getResources().getDisplayMetrics().heightPixels;
        int horizontal = dp(context, 20);
        int imageCorner = dp(context, 28);

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(COLOR_BG);
        root.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        root.setPadding(horizontal, dp(context, 44), horizontal, dp(context, 16));

        FrameLayout imageWrapper = new FrameLayout(context);
        LinearLayout.LayoutParams imageWrapperParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (int) (screenHeight * 0.64f));
        imageWrapper.setLayoutParams(imageWrapperParams);

        CardView imageCard = new CardView(context);
        imageCard.setRadius(imageCorner);
        imageCard.setCardElevation(0f);
        imageCard.setPreventCornerOverlap(false);
        imageCard.setUseCompatPadding(false);
        imageCard.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        ImageView preview = new ImageView(context);
        preview.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        preview.setScaleType(ImageView.ScaleType.CENTER_CROP);
        preview.setBackgroundColor(0xFF5C4B8A);

        String resolvedImage = (imageUrl != null && !imageUrl.isEmpty()) ? imageUrl : DEFAULT_PREVIEW_IMAGE;
        Glide.with(context).load(resolvedImage).into(preview);
        preview.setOnClickListener(v -> openLanding.run());

        imageCard.addView(preview);
        imageWrapper.addView(imageCard);

        LinearLayout infoSection = new LinearLayout(context);
        infoSection.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams infoParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        infoParams.topMargin = dp(context, 14);
        infoSection.setLayoutParams(infoParams);

        TextView titleView = new TextView(context);
        titleView.setText(appTitle != null && !appTitle.isEmpty() ? appTitle : DEFAULT_TITLE);
        titleView.setTextColor(COLOR_TEXT_PRIMARY);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        titleView.setTypeface(Typeface.DEFAULT_BOLD);
        infoSection.addView(titleView);

        LinearLayout ratingRow = createRatingRow(context, rating);
        LinearLayout.LayoutParams ratingParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        ratingParams.topMargin = dp(context, 8);
        ratingRow.setLayoutParams(ratingParams);
        infoSection.addView(ratingRow);

        TextView downloadsView = new TextView(context);
        downloadsView.setText(downloadCount != null && !downloadCount.isEmpty() ? downloadCount : DEFAULT_DOWNLOADS);
        downloadsView.setTextColor(COLOR_TEXT_SECONDARY);
        downloadsView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        LinearLayout.LayoutParams downloadsParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        downloadsParams.topMargin = dp(context, 4);
        downloadsView.setLayoutParams(downloadsParams);
        infoSection.addView(downloadsView);

        LinearLayout bottomRow = new LinearLayout(context);
        bottomRow.setOrientation(LinearLayout.HORIZONTAL);
        bottomRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams bottomRowParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        bottomRowParams.topMargin = dp(context, 40);
        bottomRow.setLayoutParams(bottomRowParams);

        LinearLayout priceColumn = new LinearLayout(context);
        priceColumn.setOrientation(LinearLayout.VERTICAL);

        TextView priceLabel = new TextView(context);
        priceLabel.setText("Price");
        priceLabel.setTextColor(COLOR_TEXT_SECONDARY);
        priceLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        priceColumn.addView(priceLabel);

        TextView priceValue = new TextView(context);
        priceValue.setText(priceText != null && !priceText.isEmpty() ? priceText : DEFAULT_PRICE);
        priceValue.setTextColor(COLOR_TEXT_PRIMARY);
        priceValue.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        priceValue.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams priceValueParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        priceValueParams.topMargin = dp(context, 2);
        priceValue.setLayoutParams(priceValueParams);
        priceColumn.addView(priceValue);

        bottomRow.addView(priceColumn, new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f));

        TextView ctaButton = createCtaButton(context, ctaText != null ? ctaText : DEFAULT_CTA);
        ctaButton.setOnClickListener(v -> openLanding.run());
        bottomRow.addView(ctaButton, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        root.addView(imageWrapper);
        root.addView(infoSection);
        root.addView(bottomRow);
        container.addView(root);
        return imageWrapper;
    }

    private static LinearLayout createRatingRow(Context context, float rating) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        float clamped = Math.max(0f, Math.min(rating, 5f));
        for (int i = 1; i <= 5; i++) {
            TextView star = new TextView(context);
            if (clamped >= i) {
                star.setText("\u2605");
                star.setTextColor(COLOR_STAR);
            } else if (clamped >= i - 0.5f) {
                star.setText("\u2605");
                star.setTextColor(0x66FF9800);
            } else {
                star.setText("\u2605");
                star.setTextColor(0x33FF9800);
            }
            star.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
            star.setPadding(0, 0, dp(context, 2), 0);
            row.addView(star);
        }
        return row;
    }

    private static TextView createCtaButton(Context context, String text) {
        TextView button = new TextView(context);
        button.setText(text);
        button.setTextColor(Color.WHITE);
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setGravity(Gravity.CENTER);
        button.setAllCaps(false);
        int horizontal = dp(context, 36);
        int vertical = dp(context, 14);
        button.setPadding(horizontal, vertical, horizontal, vertical);

        GradientDrawable background = new GradientDrawable();
        background.setColor(COLOR_CTA);
        background.setCornerRadius(dp(context, 28));
        button.setBackground(background);
        button.setElevation(dp(context, 2));
        return button;
    }

    private static int dp(Context context, int value) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, value, context.getResources().getDisplayMetrics());
    }
}
