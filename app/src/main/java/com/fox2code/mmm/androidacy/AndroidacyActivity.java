package com.fox2code.mmm.androidacy;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.webkit.ConsoleMessage;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.webkit.WebResourceErrorCompat;
import androidx.webkit.WebSettingsCompat;
import androidx.webkit.WebViewClientCompat;
import androidx.webkit.WebViewFeature;

import com.fox2code.foxcompat.FoxActivity;
import com.fox2code.mmm.BuildConfig;
import com.fox2code.mmm.Constants;
import com.fox2code.mmm.MainApplication;
import com.fox2code.mmm.R;
import com.fox2code.mmm.XHooks;
import com.fox2code.mmm.repo.RepoManager;
import com.fox2code.mmm.utils.Http;
import com.fox2code.mmm.utils.IntentHelper;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.HashMap;

/**
 * Per Androidacy repo implementation agreement, no request of this WebView shall be modified.
 */
public class AndroidacyActivity extends FoxActivity {
    private static final String TAG = "AndroidacyActivity";

    static {
        if (BuildConfig.DEBUG) {
            WebView.setWebContentsDebuggingEnabled(true);
        }
    }

    WebView webView;
    TextView webViewNote;
    AndroidacyWebAPI androidacyWebAPI;
    boolean backOnResume;

    @Override
    @SuppressLint({"SetJavaScriptEnabled", "JavascriptInterface"})
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent intent = this.getIntent();
        Uri uri;
        if (!MainApplication.checkSecret(intent) ||
                (uri = intent.getData()) == null) {
            Log.w(TAG, "Impersonation detected");
            this.forceBackPressed();
            return;
        }
        String url = uri.toString();
        if (!AndroidacyUtil.isAndroidacyLink(url, uri)) {
            Log.w(TAG, "Calling non androidacy link in secure WebView: " + url);
            this.forceBackPressed();
            return;
        }
        if (!Http.hasWebView()) {
            Log.w(TAG, "No WebView found to load url: " + url);
            this.forceBackPressed();
            return;
        }
        if (!url.contains(AndroidacyUtil.REFERRER)) {
            if (url.lastIndexOf('/') < url.lastIndexOf('?')) {
                url = url + '&' + AndroidacyUtil.REFERRER;
            } else {
                url = url + '?' + AndroidacyUtil.REFERRER;
            }
        }
        boolean allowInstall = intent.getBooleanExtra(
                Constants.EXTRA_ANDROIDACY_ALLOW_INSTALL, false);
        String title = intent.getStringExtra(Constants.EXTRA_ANDROIDACY_ACTIONBAR_TITLE);
        String config = intent.getStringExtra(Constants.EXTRA_ANDROIDACY_ACTIONBAR_CONFIG);
        int compatLevel = intent.getIntExtra(Constants.EXTRA_ANDROIDACY_COMPAT_LEVEL, 0);
        this.setContentView(R.layout.webview);
        setActionBarBackground(null);
        this.setDisplayHomeAsUpEnabled(true);
        if (title == null || title.isEmpty()) {
            this.setTitle("Androidacy");
        } else {
            this.setTitle(title);
        }
        if (allowInstall || title == null || title.isEmpty()) {
            this.hideActionBar();
        } else { // Only used for note section
            if (config != null && !config.isEmpty()) {
                String configPkg = IntentHelper.getPackageOfConfig(config);
                try {
                    XHooks.checkConfigTargetExists(this, configPkg, config);
                    this.setActionBarExtraMenuButton(R.drawable.ic_baseline_app_settings_alt_24,
                            menu -> {
                                IntentHelper.openConfig(this, config);
                                return true;
                            });
                } catch (PackageManager.NameNotFoundException ignored) {}
            }
        }
        this.webView = this.findViewById(R.id.webView);
        this.webViewNote = this.findViewById(R.id.webViewNote);
        WebSettings webSettings = this.webView.getSettings();
        webSettings.setUserAgentString(Http.getAndroidacyUA());
        webSettings.setDomStorageEnabled(true);
        webSettings.setJavaScriptEnabled(true);
        webSettings.setAllowFileAccess(false);
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
                // Don't open non Androidacy urls inside WebView
                if (request.isForMainFrame() &&
                        !AndroidacyUtil.isAndroidacyLink(request.getUrl())) {
                    Log.i(TAG, "Exiting WebView " + // hideToken in case isAndroidacyLink fail.
                            AndroidacyUtil.hideToken(request.getUrl().toString()));
                    IntentHelper.openUri(view.getContext(), request.getUrl().toString());
                    return true;
                }
                return false;
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                this.pageUrl = url;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                webViewNote.setVisibility(View.GONE);
            }

            private void onReceivedError(String url, int errorCode) {
                if ((url.startsWith("https://api.androidacy.com/magisk/") ||
                        url.startsWith("https://staging-api.androidacy.com/magisk/") ||
                        url.equals(pageUrl)) && (errorCode == 419 || errorCode == 429 || errorCode == 503)) {
                    Toast.makeText(AndroidacyActivity.this,
                            "Too many requests!", Toast.LENGTH_LONG).show();
                    AndroidacyActivity.this.runOnUiThread(AndroidacyActivity.this::onBackPressed);
                } else if (url.equals(this.pageUrl)) {
                    postOnUiThread(() ->
                            webViewNote.setVisibility(View.VISIBLE));
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
                FoxActivity.getFoxActivity(webView).startActivityForResult(
                        fileChooserParams.createIntent(), (code, data) ->
                                filePathCallback.onReceiveValue(
                                        FileChooserParams.parseResult(code, data)));
                return true;
            }

            @Override
            public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
                if (BuildConfig.DEBUG) {
                    switch (consoleMessage.messageLevel()) {
                        case TIP:
                            Log.v(TAG, consoleMessage.message());
                            break;
                        case LOG:
                            Log.i(TAG, consoleMessage.message());
                            break;
                        case WARNING:
                            Log.w(TAG, consoleMessage.message());
                            break;
                        case ERROR:
                            Log.e(TAG, consoleMessage.message());
                            break;
                        case DEBUG:
                            Log.d(TAG, consoleMessage.message());
                            break;
                    }
                }
                return super.onConsoleMessage(consoleMessage);
            }
        });
        this.webView.setDownloadListener((
                downloadUrl, userAgent, contentDisposition, mimetype, contentLength) -> {
            if (AndroidacyUtil.isAndroidacyLink(downloadUrl) && !this.backOnResume) {
                AndroidacyWebAPI androidacyWebAPI = this.androidacyWebAPI;
                if (androidacyWebAPI != null) {
                    if (!androidacyWebAPI.downloadMode) {
                        if (androidacyWebAPI.consumedAction)
                            return; // Native module popup may cause download after consumed action
                        int lenPrefix = 0;
                        // Workaround WebView/Chromium bug
                        for (String prefix : new String[]{
                                "https://api.androidacy.com/magisk/download/",
                                "https://staging-api.androidacy.com/magisk/download/"
                        }) { // Make both staging and non staging act the same
                            if (downloadUrl.startsWith(prefix)) lenPrefix = prefix.length();
                        }
                        if (lenPrefix != 0) {
                            final String moduleId = downloadUrl.substring(lenPrefix);
                            webView.evaluateJavascript("document.querySelector(" +
                                            "\"#download-form input[name=_token]\").value",
                                result -> new Thread("Androidacy popup workaround thread") {
                                    @Override
                                    public void run() {
                                        if (androidacyWebAPI.consumedAction) return;
                                        try {
                                            JSONObject jsonObject = new JSONObject();
                                            jsonObject.put("moduleId", moduleId);
                                            jsonObject.put("token", RepoManager.getINSTANCE()
                                                    .getAndroidacyRepoData().getToken());
                                            jsonObject.put("_token", result);
                                            String realUrl = Http.doHttpPostRedirect(downloadUrl,
                                                    jsonObject.toString(), true);
                                            if (downloadUrl.equals(realUrl)) {
                                                Log.e(TAG, "Failed to resolve URL");
                                                return;
                                            }
                                            Log.i(TAG, "Got url: " + realUrl);
                                            androidacyWebAPI.openNativeModuleDialogRaw(realUrl,
                                                    moduleId, "", androidacyWebAPI.canInstall());
                                        } catch (IOException | JSONException e) {
                                            Log.e(TAG, "Failed redirect intercept", e);
                                        }
                                    }
                                }.start());
                            return;
                        }
                    }
                    androidacyWebAPI.consumedAction = true;
                    androidacyWebAPI.downloadMode = false;
                }
                this.backOnResume = true;
                Log.i(TAG, "Exiting WebView " +
                        AndroidacyUtil.hideToken(downloadUrl));
                for (String prefix : new String[]{
                        "https://api.androidacy.com/magisk/download/",
                        "https://staging-api.androidacy.com/magisk/download/"
                }) {
                    if (downloadUrl.startsWith(prefix)) {
                        return;
                    }
                }
                IntentHelper.openCustomTab(this, downloadUrl);
            }
        });
        XHooks.onWebViewInitialize(this.webView, allowInstall);
        this.webView.addJavascriptInterface(androidacyWebAPI =
                new AndroidacyWebAPI(this, allowInstall), "mmm");
        if (compatLevel != 0) androidacyWebAPI.notifyCompatModeRaw(compatLevel);
        HashMap<String, String> headers = new HashMap<>();
        headers.put("Accept-Language", this.getResources()
                .getConfiguration().locale.toLanguageTag());
        this.webView.loadUrl(url, headers);
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
        } else if (this.androidacyWebAPI != null) {
            this.androidacyWebAPI.consumedAction = false;
        }
    }
}
