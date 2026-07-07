package com.bidscube.sdk.view;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bidscube.sdk.models.PlayableAdConfig;
import com.bidscube.sdk.utils.SDKLogger;

/**
 * Fullscreen HTML playable loaded from URL or inline HTML (creative-owned gamification block).
 */
public class PlayableWebView extends FrameLayout {

    private static final String TAG = "PlayableWebView";

    public interface Listener {
        void onPlayableStarted();

        void onPlayableCompleted(int score);

        void onPlayableSkipped();
    }

    private final WebView webView;
    private final PlayableAdConfig config;
    private Listener listener;
    private boolean finished;

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    public PlayableWebView(@NonNull Context context, @NonNull PlayableAdConfig config) {
        super(context);
        this.config = config;
        setBackgroundColor(Color.BLACK);

        webView = new WebView(context);
        webView.setLayoutParams(new LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT));
        webView.setBackgroundColor(Color.TRANSPARENT);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);

        webView.addJavascriptInterface(new PlayableBridge(), "BidscubeAndroid");
        webView.setWebChromeClient(new WebChromeClient());
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return false;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                SDKLogger.d(TAG, "Playable page loaded: " + url);
            }
        });

        addView(webView);
    }

    public void setListener(@Nullable Listener listener) {
        this.listener = listener;
    }

    public void start() {
        finished = false;
        String query = buildQueryString();
        if (config.hasInlineHtml()) {
            webView.loadDataWithBaseURL(
                    "https://playable.bidscube.local/",
                    config.getPlayableHtml(),
                    "text/html",
                    "utf-8",
                    null);
        } else {
            String url = PlayableAssetResolver.resolve(getContext(), config.getPlayableUrl());
            if (query.length() > 0) {
                url += (url.contains("?") ? "&" : "?") + query;
            }
            SDKLogger.d(TAG, "Loading playable URL: " + url);
            webView.loadUrl(url);
        }
        if (listener != null) {
            listener.onPlayableStarted();
        }
    }

    public void stop() {
        finished = true;
        try {
            webView.stopLoading();
            webView.loadUrl("about:blank");
        } catch (Throwable ignored) {
        }
    }

    public void destroyView() {
        stop();
        removeView(webView);
        webView.destroy();
    }

    private String buildQueryString() {
        StringBuilder sb = new StringBuilder();
        appendParam(sb, "goal", String.valueOf(config.getGoalCollectibles()));
        appendParam(sb, "maxSec", String.valueOf(config.getMaxSeconds()));
        appendParam(sb, "skipAfter", String.valueOf(config.getSkipAfterSeconds()));
        if (config.getHintText() != null && !config.getHintText().isEmpty()) {
            appendParam(sb, "hint", config.getHintText());
        }
        if (config.getHeroEmoji() != null && !config.getHeroEmoji().isEmpty()) {
            appendParam(sb, "hero", config.getHeroEmoji());
        }
        return sb.toString();
    }

    private static void appendParam(StringBuilder sb, String key, String value) {
        if (value == null || value.isEmpty()) {
            return;
        }
        if (sb.length() > 0) {
            sb.append('&');
        }
        sb.append(key).append('=').append(android.net.Uri.encode(value));
    }

    private void emitCompleted(int score) {
        if (finished) {
            return;
        }
        finished = true;
        if (listener != null) {
            listener.onPlayableCompleted(score);
        }
    }

    private void emitSkipped() {
        if (finished) {
            return;
        }
        if (listener != null) {
            listener.onPlayableSkipped();
        }
    }

    private final class PlayableBridge {
        @JavascriptInterface
        public void onPlayableComplete(int score) {
            post(() -> emitCompleted(score));
        }

        @JavascriptInterface
        public void onPlayableSkipped() {
            post(() -> emitSkipped());
        }
    }
}
