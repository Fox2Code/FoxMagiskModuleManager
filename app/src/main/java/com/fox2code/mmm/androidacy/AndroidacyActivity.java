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

import com.fox2code.mmm.BuildConfig;
import com.fox2code.mmm.MainApplication;
import com.fox2code.mmm.R;
import com.fox2code.mmm.compat.CompatActivity;
import com.fox2code.mmm.utils.Http;
import com.fox2code.mmm.utils.IntentHelper;

/**
 * Per Androidacy repo implementation agreement, no request of this WebView shall be modified.
 */
public class AndroidacyActivity extends CompatActivity {
    static {
        if (BuildConfig.DEBUG) {
            WebView.setWebContentsDebuggingEnabled(true);
        }
    }

    private WebView webView;

    @Override
    @SuppressLint({"SetJavaScriptEnabled", "JavascriptInterface"})
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent intent = this.getIntent();
        Uri uri;
        if (!MainApplication.checkSecret(intent) ||
                (uri = intent.getData()) == null ||
                !uri.getHost().endsWith(".androidacy.com")) {
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
                // Don't open non andoridacy urls inside WebView
                if (request.isForMainFrame() && !(request.getUrl().getScheme().equals("intent") ||
                        request.getUrl().getHost().endsWith(".androidacy.com"))) {
                    IntentHelper.openUrl(view.getContext(), request.getUrl().toString());
                    return true;
                }
                return false;
            }
        });
        this.webView.addJavascriptInterface(new AndroidacyWebAPI(this), "mmm");
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
