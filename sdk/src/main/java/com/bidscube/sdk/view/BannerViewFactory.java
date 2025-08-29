package com.bidscube.sdk.view;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

public class BannerViewFactory {

    @SuppressLint("SetJavaScriptEnabled")
    public static WebView createBanner(Context context, String adHtml) {
        WebView webView = new WebView(context);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);

        webView.setBackgroundColor(Color.TRANSPARENT);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                view.getContext().startActivity(intent);
                return true;
            }
        });

        webView.post(() -> {
            String html = "<html><head><style>body{margin:0;padding:0;}</style></head><body>"
                    + adHtml
                    + "</body></html>";
            webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null);
        });

        return webView;
    }
}
