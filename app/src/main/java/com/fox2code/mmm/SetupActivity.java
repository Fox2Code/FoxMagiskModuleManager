package com.fox2code.mmm;

import static com.fox2code.mmm.utils.IntentHelper.getActivity;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;

import androidx.appcompat.app.ActionBar;
import androidx.fragment.app.FragmentActivity;

import com.fox2code.foxcompat.app.FoxActivity;
import com.fox2code.mmm.databinding.ActivitySetupBinding;
import com.fox2code.rosettax.LanguageActivity;
import com.fox2code.rosettax.LanguageSwitcher;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.topjohnwu.superuser.internal.UiThreadHandler;

import java.util.Objects;

public class SetupActivity extends FoxActivity implements LanguageActivity {

    @SuppressLint({"ApplySharedPref", "RestrictedApi"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.setTitle(R.string.setup_title);
        // set action bar
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            // back button is close button
            actionBar.setDisplayOptions(ActionBar.DISPLAY_SHOW_TITLE | ActionBar.DISPLAY_USE_LOGO | ActionBar.DISPLAY_SHOW_HOME);
            actionBar.setLogo(R.drawable.ic_foreground);
            // set title
            actionBar.setTitle(R.string.setup_title);
            actionBar.show();
        }
        this.getWindow().setFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION, 0);
        // Set theme
        SharedPreferences prefs = MainApplication.getSharedPreferences();
        switch (prefs.getString("theme", "system")) {
            case "light":
                setTheme(R.style.Theme_MagiskModuleManager_Monet_Light);
                break;
            case "dark":
                setTheme(R.style.Theme_MagiskModuleManager_Monet_Dark);
                break;
            case "system":
                setTheme(R.style.Theme_MagiskModuleManager_Monet);
                break;
            case "black":
                setTheme(R.style.Theme_MagiskModuleManager_Monet_Black);
                break;
            case "transparent_light":
                setTheme(R.style.Theme_MagiskModuleManager_Transparent_Light);
                break;
        }

        com.fox2code.mmm.databinding.ActivitySetupBinding binding = ActivitySetupBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        // Show setup box. Put the setup_box in the main activity layout
        View view = binding.setupBox;
        // Make the setup_box linear layout the sole child of the root_container constraint layout
        setContentView(view);
        ((MaterialSwitch) Objects.requireNonNull(view.findViewById(R.id.setup_background_update_check))).setChecked(BuildConfig.ENABLE_AUTO_UPDATER);
        ((MaterialSwitch) Objects.requireNonNull(view.findViewById(R.id.setup_crash_reporting))).setChecked(BuildConfig.DEFAULT_ENABLE_CRASH_REPORTING);
        // Repos are a little harder, as the enabled_repos build config is an arraylist
        ((MaterialSwitch) Objects.requireNonNull(view.findViewById(R.id.setup_androidacy_repo))).setChecked(BuildConfig.ENABLED_REPOS.contains("androidacy_repo"));
        ((MaterialSwitch) Objects.requireNonNull(view.findViewById(R.id.setup_magisk_alt_repo))).setChecked(BuildConfig.ENABLED_REPOS.contains("magisk_alt_repo"));
        // On debug builds, log when a switch is toggled
        if (BuildConfig.DEBUG) {
            ((MaterialSwitch) Objects.requireNonNull(view.findViewById(R.id.setup_background_update_check))).setOnCheckedChangeListener((buttonView, isChecked) -> Log.i("SetupWizard", "Background Update Check: " + isChecked));
            ((MaterialSwitch) Objects.requireNonNull(view.findViewById(R.id.setup_crash_reporting))).setOnCheckedChangeListener((buttonView, isChecked) -> Log.i("SetupWizard", "Crash Reporting: " + isChecked));
            ((MaterialSwitch) Objects.requireNonNull(view.findViewById(R.id.setup_androidacy_repo))).setOnCheckedChangeListener((buttonView, isChecked) -> Log.i("SetupWizard", "Androidacy Repo: " + isChecked));
            ((MaterialSwitch) Objects.requireNonNull(view.findViewById(R.id.setup_magisk_alt_repo))).setOnCheckedChangeListener((buttonView, isChecked) -> Log.i("SetupWizard", "Magisk Alt Repo: " + isChecked));
        }
        // Setup popup dialogue for the setup_theme_button
        MaterialButton themeButton = view.findViewById(R.id.setup_theme_button);
        themeButton.setOnClickListener(v -> {
            // Create a new dialog for the theme picker
            MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this);
            builder.setTitle(R.string.setup_theme_title);
            // Create a new array of theme names (system, light, dark, black, transparent light)
            String[] themeNames = new String[]{getString(R.string.theme_system), getString(R.string.theme_light), getString(R.string.theme_dark), getString(R.string.theme_black), getString(R.string.theme_transparent_light)};
            // Create a new array of theme values (system, light, dark, black, transparent_light)
            String[] themeValues = new String[]{"system", "light", "dark", "black", "transparent_light"};
            // if pref_theme is set, check the relevant theme_* menu item, otherwise check the default (theme_system)
            String prefTheme = prefs.getString("pref_theme", "system");
            int checkedItem = 0;
            switch (prefTheme) {
                case "system":
                    break;
                case "light":
                    checkedItem = 1;
                    break;
                case "dark":
                    checkedItem = 2;
                    break;
                case "black":
                    checkedItem = 3;
                    break;
                case "transparent_light":
                    checkedItem = 4;
                    break;
            }
            builder.setCancelable(true);
            // Create the dialog
            builder.setSingleChoiceItems(themeNames, checkedItem, (dialog, which) -> {
                // Set the theme
                prefs.edit().putString("pref_theme", themeValues[which]).commit();
                // Dismiss the dialog
                dialog.dismiss();
                // Set the theme
                UiThreadHandler.handler.postDelayed(() -> {
                    switch (prefs.getString("pref_theme", "system")) {
                        case "light":
                            setTheme(R.style.Theme_MagiskModuleManager_Monet_Light);
                            break;
                        case "dark":
                            setTheme(R.style.Theme_MagiskModuleManager_Monet_Dark);
                            break;
                        case "system":
                            setTheme(R.style.Theme_MagiskModuleManager_Monet);
                            break;
                        case "black":
                            setTheme(R.style.Theme_MagiskModuleManager_Monet_Black);
                            break;
                        case "transparent_light":
                            setTheme(R.style.Theme_MagiskModuleManager_Transparent_Light);
                            break;
                    }
                    // restart the activity because switching to transparent pisses the rendering engine off
                    Intent intent = getIntent();
                    finish();
                    startActivity(intent);
                }, 100);
            });
            builder.show();
        });
        // Setup language selector
        MaterialButton languageSelector = view.findViewById(R.id.setup_language_button);
        languageSelector.setOnClickListener(preference -> {
            LanguageSwitcher ls = new LanguageSwitcher(Objects.requireNonNull(getActivity(this)));
            ls.setSupportedStringLocales(MainApplication.supportedLocales);
            ls.showChangeLanguageDialog((FragmentActivity) getActivity(this));
        });
        // Set up the buttons
        // Setup button
        MaterialButton setupButton = view.findViewById(R.id.setup_continue);
        setupButton.setOnClickListener(v -> {
            // Set first launch to false
            // get instance of editor
            SharedPreferences.Editor editor = prefs.edit();
            editor.putBoolean("first_time_user", false);
            // Set the background update check pref
            editor.putBoolean("pref_background_update_check", ((MaterialSwitch) Objects.requireNonNull(view.findViewById(R.id.setup_background_update_check))).isChecked());
            // Set the crash reporting pref
            editor.putBoolean("pref_crash_reporting", ((MaterialSwitch) Objects.requireNonNull(view.findViewById(R.id.setup_crash_reporting))).isChecked());
            // Set the repos
            // first pref_magisk_alt_repo_enabled then pref_androidacy_repo_enabled
            editor.putBoolean("pref_magisk_alt_repo_enabled", ((MaterialSwitch) Objects.requireNonNull(view.findViewById(R.id.setup_magisk_alt_repo))).isChecked());
            editor.putBoolean("pref_androidacy_repo_enabled", ((MaterialSwitch) Objects.requireNonNull(view.findViewById(R.id.setup_androidacy_repo))).isChecked());
            // Commit the changes
            editor.commit();
            // Sleep for 1 second to allow the user to see the changes
            try {
                Thread.sleep(500);
            } catch (
                    InterruptedException e) {
                e.printStackTrace();
            }
            // Log the changes if debug
            if (BuildConfig.DEBUG) {
                Log.d("SetupWizard", "Background update check: " + prefs.getBoolean("pref_background_update_check", false));
                Log.i("SetupWizard", "Crash reporting: " + prefs.getBoolean("pref_crash_reporting", false));
                Log.i("SetupWizard", "Magisk Alt Repo: " + prefs.getBoolean("pref_magisk_alt_repo_enabled", false));
                Log.i("SetupWizard", "Androidacy Repo: " + prefs.getBoolean("pref_androidacy_repo_enabled", false));
            }
            // Restart the activity
            MainActivity.doSetupRestarting = true;
            Intent intent = new Intent(this, MainActivity.class);
            startActivity(intent);
            finish();
        });
        // Cancel button
        MaterialButton cancelButton = view.findViewById(R.id.setup_cancel);
        cancelButton.setText(R.string.cancel);
        cancelButton.setOnClickListener(v -> {
            // Set first launch to false and restart the activity
            prefs.edit().putBoolean("first_time_user", false).commit();
            MainActivity.doSetupRestarting = true;
            Intent intent = new Intent(this, MainActivity.class);
            startActivity(intent);
            finish();
        });
    }

    @Override
    public Resources.Theme getTheme() {
        Resources.Theme theme = super.getTheme();
        // Set the theme
        SharedPreferences prefs = MainApplication.getSharedPreferences();
        switch (prefs.getString("pref_theme", "system")) {
            case "light":
                theme.applyStyle(R.style.Theme_MagiskModuleManager_Monet_Light, true);
                break;
            case "dark":
                theme.applyStyle(R.style.Theme_MagiskModuleManager_Monet_Dark, true);
                break;
            case "system":
                theme.applyStyle(R.style.Theme_MagiskModuleManager_Monet, true);
                break;
            case "black":
                theme.applyStyle(R.style.Theme_MagiskModuleManager_Monet_Black, true);
                break;
            case "transparent_light":
                theme.applyStyle(R.style.Theme_MagiskModuleManager_Transparent_Light, true);
                break;
        }
        return theme;
    }

    @Override
    @SuppressLint({"InlinedApi", "RestrictedApi"})
    public void refreshRosettaX() {
        // refresh app language
        runOnUiThread(() -> {
            // refresh activity
            Intent intent = getIntent();
            finish();
            startActivity(intent);
        });
    }
}