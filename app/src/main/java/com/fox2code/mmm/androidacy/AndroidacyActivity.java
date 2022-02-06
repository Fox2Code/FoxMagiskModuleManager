package com.fox2code.mmm.androidacy;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.webkit.WebResourceErrorCompat;
import androidx.webkit.WebSettingsCompat;
import androidx.webkit.WebViewClientCompat;
import androidx.webkit.WebViewFeature;

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
    private static final String REFERRER = "utm_source=FoxMMM&utm_medium=app";

    static {
        if (BuildConfig.DEBUG) {
            WebView.setWebContentsDebuggingEnabled(true);
        }
    }

    WebView webView;
    boolean backOnResume;

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
        String url = uri.toString();
        int i;
        if (!url.startsWith("https://") || // Checking twice
                (i = url.indexOf("/", 8)) == -1 ||
                !url.substring(8, i).endsWith(".androidacy.com")) {
            this.forceBackPressed();
            return;
        }
        if (!url.endsWith(REFERRER) && (url.startsWith("https://www.androidacy.com/") ||
                url.startsWith("https://api.androidacy.com/magisk/"))) {
            if (url.lastIndexOf('/') < url.lastIndexOf('?')) {
                url = url + '&' + REFERRER;
            } else {
                url = url + '?' + REFERRER;
            }
        }
        boolean allowInstall = intent.getBooleanExtra(
                Constants.EXTRA_ANDROIDACY_ALLOW_INSTALL, false);
        String title = intent.getStringExtra(Constants.EXTRA_ANDROIDACY_ACTIONBAR_TITLE);
        String config = intent.getStringExtra(Constants.EXTRA_ANDROIDACY_ACTIONBAR_CONFIG);
        this.setContentView(R.layout.webview);
        if (allowInstall || title == null || title.isEmpty()) {
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
        } else if (WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK)) {
            WebSettingsCompat.setForceDark(webSettings, MainApplication.getINSTANCE().isLightTheme() ?
                    WebSettingsCompat.FORCE_DARK_OFF : WebSettingsCompat.FORCE_DARK_ON);
        }
        this.webView.setWebViewClient(new WebViewClientCompat() {
            private String pageUrl;

            @Override
            public boolean shouldOverrideUrlLoading(
                    @NonNull WebView view, @NonNull WebResourceRequest request) {
                // Don't open non andoridacy urls inside WebView
                if (request.isForMainFrame() && !(request.getUrl().getScheme().equals("intent") ||
                        request.getUrl().getHost().endsWith(".androidacy.com"))) {
                    IntentHelper.openUrl(view.getContext(), request.getUrl().toString());
                    return true;
                }
                return false;
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                this.pageUrl = url;
            }

            private void onReceivedError(String url,int errorCode) {
                if ((url.startsWith("https://api.androidacy.com/magisk/") ||
                        url.equals(pageUrl)) && (errorCode == 419 || errorCode == 429)) {
                    Toast.makeText(AndroidacyActivity.this,
                            "Too many requests!", Toast.LENGTH_LONG).show();
                    AndroidacyActivity.this.runOnUiThread(AndroidacyActivity.this::onBackPressed);
                }
            }

            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                this.onReceivedError(failingUrl, errorCode);
            }

            @Override
            public void onReceivedError(@NonNull WebView view, @NonNull WebResourceRequest request,
                                        @NonNull WebResourceErrorCompat error) {
                if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_RESOURCE_ERROR_GET_CODE)) {
                    this.onReceivedError(request.getUrl().toString(), error.getErrorCode());
                }
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
        this.webView.loadUrl(url);
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

    @Override
    protected void onResume() {
        super.onResume();
        if (this.backOnResume) {
            this.backOnResume = false;
            this.forceBackPressed();
        }
    }
}
