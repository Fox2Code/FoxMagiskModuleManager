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

import java.io.IOException;
import java.nio.charset.StandardCharsets;


public class MarkdownActivity extends CompatActivity {
    private static final String TAG = "MarkdownActivity";

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
        ViewGroup markdownBackground = findViewById(R.id.markdownBackground);
        TextView textView = findViewById(R.id.markdownView);
        new Thread(() -> {
            try {
                Log.d(TAG, "Downloading");
                byte[] rawMarkdown;
                try {
                    rawMarkdown = Http.doHttpGet(url, true);
                } catch (IOException e) {
                    // Workaround GitHub README.md case sensitivity issue
                    if (url.startsWith("https://raw.githubusercontent.com/") &&
                            url.endsWith("/README.md")) { // Try with lowercase version
                        rawMarkdown = Http.doHttpGet(url.substring(0,
                                url.length() - 9) + "readme.md", true);
                    } else throw e;
                }
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
