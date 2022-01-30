package com.fox2code.mmm.androidacy;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.annotation.Nullable;

import com.fox2code.mmm.BuildConfig;
import com.fox2code.mmm.Constants;
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
        boolean allowInstall = intent.getBooleanExtra(
                Constants.EXTRA_ANDROIDACY_ALLOW_INSTALL, false);
        String title = intent.getStringExtra(Constants.EXTRA_ANDROIDACY_ACTIONBAR_TITLE);
        String config = intent.getStringExtra(Constants.EXTRA_ANDROIDACY_ACTIONBAR_CONFIG);
        this.setContentView(R.layout.webview);
        if (title == null || title.isEmpty()) {
            this.hideActionBar();
        } else { // Only used for note section
            this.setTitle(title);
            this.setDisplayHomeAsUpEnabled(true);
            if (config != null && !config.isEmpty()) {
                String configPkg = IntentHelper.getPackageOfConfig(config);
                try {
                    this.getPackageManager().getPackageInfo(configPkg, 0);
                    this.setActionBarExtraMenuButton(R.drawable.ic_baseline_app_settings_alt_24,
                            menu -> {
                                IntentHelper.openConfig(this, config);
                                return true;
                            });
                } catch (PackageManager.NameNotFoundException ignored) {}
            }
        }
        this.webView = this.findViewById(R.id.webView);
        WebSettings webSettings = this.webView.getSettings();
        webSettings.setUserAgentString(Http.getAndroidacyUA());
        webSettings.setDomStorageEnabled(true);
        webSettings.setJavaScriptEnabled(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) { // Make website follow app theme
            webSettings.setForceDark(MainApplication.getINSTANCE().isLightTheme() ?
                    WebSettings.FORCE_DARK_OFF : WebSettings.FORCE_DARK_ON);
        }
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
        this.webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback,
                                             FileChooserParams fileChooserParams) {
                CompatActivity.getCompatActivity(webView).startActivityForResult(
                        fileChooserParams.createIntent(), (code, data) ->
                                filePathCallback.onReceiveValue(
                                        FileChooserParams.parseResult(code, data)));
                return true;
            }
        });
        this.webView.addJavascriptInterface(
                new AndroidacyWebAPI(this, allowInstall), "mmm");
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
