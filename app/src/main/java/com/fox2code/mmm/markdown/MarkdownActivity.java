package com.fox2code.mmm.markdown;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.graphics.ColorUtils;

import com.fox2code.mmm.Constants;
import com.fox2code.mmm.MainApplication;
import com.fox2code.mmm.R;
import com.fox2code.mmm.XHooks;
import com.fox2code.mmm.compat.CompatActivity;
import com.fox2code.mmm.compat.CompatDisplay;
import com.fox2code.mmm.utils.Http;
import com.fox2code.mmm.utils.IntentHelper;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.topjohnwu.superuser.Shell;
import com.topjohnwu.superuser.internal.UiThreadHandler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;

import eightbitlab.com.blurview.BlurView;
import eightbitlab.com.blurview.RenderScriptBlur;


public class MarkdownActivity extends CompatActivity {
    private static final String TAG = "MarkdownActivity";
    private static final HashMap<String, String> redirects = new HashMap<>(4);
    private static final String[] variants = new String[]{
            "readme.md", "README.MD", ".github/README.md"
    };
    private BlurView chipHolder;
    private TextView actionBarPadding;
    private BlurView actionBarBlur;
    private ColorDrawable anyBarBackground;
    private ScrollView scrollView;
    private LinearLayout md_layout;

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
                    } catch (IOException ignored) {
                    }
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
        boolean change_boot = intent.getExtras()
                .getBoolean(Constants.EXTRA_MARKDOWN_CHANGE_BOOT);
        boolean needs_ramdisk = intent.getExtras()
                .getBoolean(Constants.EXTRA_MARKDOWN_NEEDS_RAMDISK);
        int min_magisk = intent.getExtras()
                .getInt(Constants.EXTRA_MARKDOWN_MIN_MAGISK);
        int min_api = intent.getExtras()
                .getInt(Constants.EXTRA_MARKDOWN_MIN_API);
        int max_api = intent.getExtras()
                .getInt(Constants.EXTRA_MARKDOWN_MAX_API);
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
                XHooks.checkConfigTargetExists(this, configPkg, config);
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
        this.chipHolder = findViewById(R.id.chip_holder);
        this.anyBarBackground = new ColorDrawable(Color.TRANSPARENT);
        this.actionBarPadding = findViewById(R.id.markdown_action_bar_padding);
        this.actionBarBlur = findViewById(R.id.markdown_action_bar_blur);
        this.scrollView = findViewById(R.id.scrollView2);
        this.md_layout = findViewById(R.id.md_layout);
        final TextView footer = findViewById(R.id.markdownFooter);
        UiThreadHandler.handler.postDelayed(() -> // Fix footer height
                footer.setMinHeight(this.getNavigationBarHeight()), 1L);
        this.actionBarBlur.setBackground(this.anyBarBackground);
        this.setupBlurView(this.chipHolder, markdownBackground, this.anyBarBackground);
        this.setupBlurView(this.actionBarBlur, markdownBackground, this.anyBarBackground);
        this.updateScreenInsets();
        this.updateUI();

        // Really bad created (MSG by Der_Googler)
        if (MainApplication.isChipsDisabled()) {
            this.chipHolder.setVisibility(View.GONE);
        } else {
            this.chipHolder.setPadding(0,0,0,this.getNavigationBarHeight());
            // set "message" to null to disable dialog
            this.setChip(change_boot,
                    getString(R.string.module_can_change_boot),
                    "This module may change the boot image");
            this.setChip(needs_ramdisk,
                    getString(R.string.module_needs_ramdisk),
                    "This module need boot ramdisk to be installed");
            this.setChip(min_magisk, "Min. Magisk \"" + min_magisk + "\"",
                    null);
            this.setChip(min_api, "Min. Android " + min_api,
                    null);
            this.setChip(max_api, "Max. Android " + max_api,
                    null);
        }

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

   private void setupBlurView(BlurView view, ViewGroup setupWith, ColorDrawable background) {
       view.setBackground(background);
       view.setupWith(setupWith).setFrameClearDrawable(
                       this.getWindow().getDecorView().getBackground())
               .setBlurAlgorithm(new RenderScriptBlur(this))
               .setBlurRadius(4F).setBlurAutoUpdate(true)
               .setHasFixedTransformationMatrix(true);
   }

    private void updateScreenInsets() {
        this.runOnUiThread(() -> this.updateScreenInsets(
                this.getResources().getConfiguration()));
    }

    private void updateScreenInsets(Configuration configuration) {
        boolean landscape = configuration.orientation ==
                Configuration.ORIENTATION_LANDSCAPE;
        int bottomInset = (landscape ? 0 : this.getNavigationBarHeight());
        int statusBarHeight = getStatusBarHeight();
        int actionBarHeight = getActionBarHeight();
        int combinedBarsHeight = statusBarHeight + actionBarHeight;
        this.actionBarPadding.setMinHeight(combinedBarsHeight);
        //this.actionBarBlur.invalidate();
    }

    private void updateUI() {
        boolean isLightMode = this.isLightTheme();
        int colorBackground;
        try {
            colorBackground = this.getColorCompat(
                    android.R.attr.windowBackground);
        } catch (Resources.NotFoundException e) {
            colorBackground = this.getColorCompat(isLightMode ?
                    R.color.white : R.color.black);
        }
        this.md_layout.setPadding(0,this.getActionBarHeight(this) + this.getStatusBarHeight(),0,this.getNavigationBarHeight() + 56);
        if (MainApplication.isBlurEnabled()) {
            this.actionBarBlur.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, this.getActionBarHeight(this) + this.getStatusBarHeight()));
            this.chipHolder.setBlurEnabled(true);
            this.anyBarBackground.setColor(ColorUtils
                    .setAlphaComponent(colorBackground, 0x02));
            this.anyBarBackground.setColor(Color.TRANSPARENT);
            this.actionBarBlur.setBlurEnabled(true);
        } else {
            this.chipHolder.setBlurEnabled(false);
            this.chipHolder.setOverlayColor(Color.TRANSPARENT);
            this.anyBarBackground.setColor(colorBackground);
            this.actionBarBlur.setBlurEnabled(false);
            this.actionBarBlur.setOverlayColor(Color.TRANSPARENT);
        }
    }

    private void setChip(boolean bool, String title, String message) {
        if (bool) {
            this.makeChip(title, message);
        }
    }

    private void setChip(int i, String title, String message) {
        if (i != 0) {
            this.makeChip(title, message);
        }
    }

    private void makeChip(String title, String message) {
        final ChipGroup chip_group_holder = findViewById(R.id.chip_group_holder);
        Chip chip = new Chip(this);
        chip.setText(title);
        chip.setVisibility(View.VISIBLE);
        if (message != null) {
            chip.setOnClickListener(_view -> {
                MaterialAlertDialogBuilder builder =
                        new MaterialAlertDialogBuilder(this);

                builder
                        .setTitle(title)
                        .setMessage(message)
                        .setCancelable(true)
                        .setPositiveButton(R.string.ok, (x, y) -> {
                            x.dismiss();
                        }).show();

            });
        }
        chip_group_holder.addView(chip);
    }

    private String parseAndroidVersion(int version) {
        switch (version) {
            case Build.VERSION_CODES.LOLLIPOP:
                return "5.0";
            case Build.VERSION_CODES.LOLLIPOP_MR1:
                return "5.1";
            case Build.VERSION_CODES.M:
                return "6.0";
            case Build.VERSION_CODES.N:
                return "7.0";
            case Build.VERSION_CODES.N_MR1:
                return "7.1";
            case Build.VERSION_CODES.O:
                return "8.0";
            case Build.VERSION_CODES.O_MR1:
                return "8.1";
            case Build.VERSION_CODES.P:
                return "9.0 (P)";
            case Build.VERSION_CODES.Q:
                return "10 (Q)";
            case Build.VERSION_CODES.R:
                return "11 (R)";
            case Build.VERSION_CODES.S:
                return "12 (S)";
            case Build.VERSION_CODES.S_V2:
                return "12L";
            default:
                return "false";
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        View footer = findViewById(R.id.markdownFooter);
        if (footer != null) footer.setMinimumHeight(this.getNavigationBarHeight());
    }
}
