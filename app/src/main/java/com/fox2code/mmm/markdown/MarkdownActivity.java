package com.fox2code.mmm.markdown;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.fox2code.mmm.Constants;
import com.fox2code.mmm.MainApplication;
import com.fox2code.mmm.R;
import com.fox2code.mmm.compat.CompatActivity;
import com.fox2code.mmm.utils.Http;
import com.fox2code.mmm.utils.IntentHelper;
import com.topjohnwu.superuser.internal.UiThreadHandler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;


public class MarkdownActivity extends CompatActivity {
    private static final String TAG = "MarkdownActivity";
    private static final HashMap<String, String> redirects = new HashMap<>(4);
    private static final String[] variants = new String[]{
            "readme.md", "README.MD", ".github/README.md"
    };

    private static byte[] getRawMarkdown(String url) throws IOException {
        String newUrl = redirects.get(url);
        if (newUrl != null && !newUrl.equals(url)) {
            return Http.doHttpGet(newUrl, true);
        }
        try {
            return Http.doHttpGet(url, true);
        } catch (IOException e) {
            // Workaround GitHub README.md case sensitivity issue
            if (url.startsWith("https://raw.githubusercontent.com/") &&
                    url.endsWith("/README.md")) {
                String prefix = url.substring(0, url.length() - 9);
                for (String suffix : variants) {
                    newUrl = prefix + suffix;
                    try { // Try with lowercase version
                        byte[] rawMarkdown = Http.doHttpGet(prefix + suffix, true);
                        redirects.put(url, newUrl); // Avoid retries
                        return rawMarkdown;
                    } catch (IOException ignored) {}
                }
            }
            throw e;
        }
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.setDisplayHomeAsUpEnabled(true);
        Intent intent = this.getIntent();
        if (!MainApplication.checkSecret(intent)) {
            Log.e(TAG, "Impersonation detected!");
            this.forceBackPressed();
            return;
        }
        String url = intent.getExtras()
                .getString(Constants.EXTRA_MARKDOWN_URL);
        String title = intent.getExtras()
                .getString(Constants.EXTRA_MARKDOWN_TITLE);
        String config = intent.getExtras()
                .getString(Constants.EXTRA_MARKDOWN_CONFIG);
        if (title != null && !title.isEmpty()) {
            this.setTitle(title);
        }
        setActionBarBackground(null);
        this.getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION,
                WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION);
        if (config != null && !config.isEmpty()) {
            String configPkg = IntentHelper.getPackageOfConfig(config);
            try {
                this.getPackageManager().getPackageInfo(configPkg, 0);
                this.setActionBarExtraMenuButton(R.drawable.ic_baseline_app_settings_alt_24, menu -> {
                    IntentHelper.openConfig(this, config);
                    return true;
                });
            } catch (PackageManager.NameNotFoundException e) {
                Log.w(TAG, "Config package \"" +
                        configPkg + "\" missing for markdown view");
            }
        }
        Log.i(TAG, "Url for markdown " + url);
        setContentView(R.layout.markdown_view);
        final ViewGroup markdownBackground = findViewById(R.id.markdownBackground);
        final TextView textView = findViewById(R.id.markdownView);
        final TextView footer = findViewById(R.id.markdownFooter);
        UiThreadHandler.handler.postDelayed(() -> // Fix footer height
                footer.setMinHeight(this.getNavigationBarHeight()), 1L);
        new Thread(() -> {
            try {
                Log.d(TAG, "Downloading");
                byte[] rawMarkdown = getRawMarkdown(url);
                Log.d(TAG, "Encoding");
                String markdown = new String(rawMarkdown, StandardCharsets.UTF_8);
                Log.d(TAG, "Done!");
                runOnUiThread(() -> {
                    findViewById(R.id.markdownFooter)
                            .setMinimumHeight(this.getNavigationBarHeight());
                    MainApplication.getINSTANCE().getMarkwon().setMarkdown(
                            textView, MarkdownUrlLinker.urlLinkify(markdown));
                    if (markdownBackground != null) {
                        markdownBackground.setClickable(true);
                        markdownBackground.setOnClickListener(v -> this.onBackPressed());
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "Failed download", e);
                runOnUiThread(() -> {
                    Toast.makeText(this, R.string.failed_download,
                            Toast.LENGTH_SHORT).show();
                    this.onBackPressed();
                });
            }
        }, "Markdown load thread").start();
    }

    @Override
    protected void onResume() {
        super.onResume();
        View footer = findViewById(R.id.markdownFooter);
        if (footer != null) footer.setMinimumHeight(this.getNavigationBarHeight());
    }
}
