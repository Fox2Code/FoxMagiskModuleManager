package com.fox2code.mmm.androidacy;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.annotation.Nullable;

import com.fox2code.mmm.R;
import com.fox2code.mmm.compat.CompatActivity;
import com.fox2code.mmm.utils.Http;
import com.fox2code.mmm.utils.IntentHelper;

/**
 * Per Androidacy repo implementation agreement, no request of this WebView shall be modified.
 */
public class AndoridacyActivity extends CompatActivity {
    private WebView webView;

    @Override
    @SuppressLint("SetJavaScriptEnabled")
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent intent = this.getIntent();
        Uri uri;
        if (intent == null || (uri = intent.getData()) == null
                || !uri.getHost().endsWith(".androidacy.com")) {
            this.forceBackPressed();
            return;
        }
        this.setContentView(R.layout.webview);
        this.hideActionBar();
        this.webView = this.findViewById(R.id.webView);
        WebSettings webSettings = this.webView.getSettings();
        webSettings.setUserAgentString(Http.getAndroidacyUA());
        webSettings.setDomStorageEnabled(true);
        webSettings.setJavaScriptEnabled(true);
        this.webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                if (request.isForMainFrame() && // Don't open non andoridacy urls inside WebView
                        !request.getUrl().getHost().endsWith(".androidacy.com")) {
                    IntentHelper.openUrl(view.getContext(), request.getUrl().toString());
                    return true;
                }
                return false;
            }
        });
        this.webView.loadUrl(uri.toString());
    }

    @Override
    public void onBackPressed() {
        WebView webView = this.webView;
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }
}
