package com.fox2code.mmm;

import static com.fox2code.mmm.utils.IntentHelper.getActivity;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.fragment.app.FragmentActivity;

import com.fox2code.foxcompat.app.FoxActivity;
import com.fox2code.mmm.androidacy.AndroidacyRepoData;
import com.fox2code.mmm.databinding.ActivitySetupBinding;
import com.fox2code.mmm.repo.RepoManager;
import com.fox2code.mmm.utils.realm.ReposList;
import com.fox2code.rosettax.LanguageActivity;
import com.fox2code.rosettax.LanguageSwitcher;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.topjohnwu.superuser.internal.UiThreadHandler;

import java.util.Objects;

import io.realm.Realm;
import io.realm.RealmConfiguration;
import io.realm.RealmResults;
import timber.log.Timber;

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
        createRealmDatabase();
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

        ActivitySetupBinding binding = ActivitySetupBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        // Show setup box. Put the setup_box in the main activity layout
        View view = binding.setupBox;
        // Make the setup_box linear layout the sole child of the root_container constraint layout
        setContentView(view);
        ((MaterialSwitch) Objects.requireNonNull(view.findViewById(R.id.setup_background_update_check))).setChecked(BuildConfig.ENABLE_AUTO_UPDATER);
        ((MaterialSwitch) Objects.requireNonNull(view.findViewById(R.id.setup_crash_reporting))).setChecked(BuildConfig.DEFAULT_ENABLE_CRASH_REPORTING);
        // assert that both switches match the build config on debug builds
        if (BuildConfig.DEBUG) {
            assert ((MaterialSwitch) Objects.requireNonNull(view.findViewById(R.id.setup_background_update_check))).isChecked() == BuildConfig.ENABLE_AUTO_UPDATER;
            assert ((MaterialSwitch) Objects.requireNonNull(view.findViewById(R.id.setup_crash_reporting))).isChecked() == BuildConfig.DEFAULT_ENABLE_CRASH_REPORTING;
        }
        // Repos are a little harder, as the enabled_repos build config is an arraylist
        ((MaterialSwitch) Objects.requireNonNull(view.findViewById(R.id.setup_androidacy_repo))).setChecked(BuildConfig.ENABLED_REPOS.contains("androidacy_repo"));
        ((MaterialSwitch) Objects.requireNonNull(view.findViewById(R.id.setup_magisk_alt_repo))).setChecked(BuildConfig.ENABLED_REPOS.contains("magisk_alt_repo"));
        // On debug builds, log when a switch is toggled
        if (BuildConfig.DEBUG) {
            ((MaterialSwitch) Objects.requireNonNull(view.findViewById(R.id.setup_background_update_check))).setOnCheckedChangeListener((buttonView, isChecked) -> Timber.i("Background Update Check: %s", isChecked));
            ((MaterialSwitch) Objects.requireNonNull(view.findViewById(R.id.setup_crash_reporting))).setOnCheckedChangeListener((buttonView, isChecked) -> Timber.i("Crash Reporting: %s", isChecked));
            ((MaterialSwitch) Objects.requireNonNull(view.findViewById(R.id.setup_androidacy_repo))).setOnCheckedChangeListener((buttonView, isChecked) -> Timber.i("Androidacy Repo: %s", isChecked));
            ((MaterialSwitch) Objects.requireNonNull(view.findViewById(R.id.setup_magisk_alt_repo))).setOnCheckedChangeListener((buttonView, isChecked) -> Timber.i("Magisk Alt Repo: %s", isChecked));
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
            // Set the repos in the ReposList realm db
            RealmConfiguration realmConfig = new RealmConfiguration.Builder().name("ReposList.realm").directory(MainApplication.getINSTANCE().getDataDirWithPath("realms")).schemaVersion(1).allowQueriesOnUiThread(true).allowWritesOnUiThread(true).build();
            boolean androidacyRepo = ((MaterialSwitch) Objects.requireNonNull(view.findViewById(R.id.setup_androidacy_repo))).isChecked();
            boolean magiskAltRepo = ((MaterialSwitch) Objects.requireNonNull(view.findViewById(R.id.setup_magisk_alt_repo))).isChecked();
            Realm.getInstanceAsync(realmConfig, new Realm.Callback() {
                @Override
                public void onSuccess(@NonNull Realm realm) {
                    realm.executeTransaction(realm1 -> {
                        ReposList androidacyRepoDB = realm1.where(ReposList.class).equalTo("id", "androidacy_repo").findFirst();
                        if (androidacyRepoDB != null) {
                            androidacyRepoDB.setEnabled(androidacyRepo);
                        }
                        ReposList magiskAltRepoDB = realm1.where(ReposList.class).equalTo("id", "magisk_alt_repo").findFirst();
                        if (magiskAltRepoDB != null) {
                            magiskAltRepoDB.setEnabled(magiskAltRepo);
                        }
                        // commit the changes
                        realm1.commitTransaction();
                        realm1.close();
                    });
                    realm.commitTransaction();
                    realm.close();
                }
            });
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
                Timber.d("Background update check: %s", prefs.getBoolean("pref_background_update_check", false));
                Timber.i("Crash reporting: %s", prefs.getBoolean("pref_crash_reporting", false));
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

    // creates the realm database
    private void createRealmDatabase() {
        Timber.d("Creating Realm databases");
        // create the realm database for ReposList
        // next, create the realm database for ReposList
        RealmConfiguration config2 = new RealmConfiguration.Builder().name("ReposList.realm").directory(MainApplication.getINSTANCE().getDataDirWithPath("realms")).schemaVersion(1).build();
        // get the instance
        Realm.getInstanceAsync(config2, new Realm.Callback() {
            @Override
            public void onSuccess(@NonNull Realm realm1) {
                // drop the database if it exists
                realm1.executeTransactionAsync(realm2 -> realm2.delete(ReposList.class));
                // create androidacy_repo and magisk_alt_repo if they don't exist under ReposList
                // each has id, name, donate, website, support, enabled, and lastUpdate and name
                // create androidacy_repo
                realm1.beginTransaction();
                if (realm1.where(ReposList.class).equalTo("id", "androidacy_repo").findFirst() == null) {
                    // cant use createObject because it crashes because reasons. use copyToRealm instead
                    ReposList androidacy_repo = realm1.createObject(ReposList.class, "androidacy_repo");
                    androidacy_repo.setName("Androidacy Repo");
                    androidacy_repo.setDonate(AndroidacyRepoData.getInstance().getDonate());
                    androidacy_repo.setSupport(AndroidacyRepoData.getInstance().getSupport());
                    androidacy_repo.setSubmitModule(AndroidacyRepoData.getInstance().getSubmitModule());
                    androidacy_repo.setWebsite(AndroidacyRepoData.getInstance().getWebsite());
                    androidacy_repo.setUrl(AndroidacyRepoData.getInstance().getWebsite());
                    androidacy_repo.setEnabled(true);
                    androidacy_repo.setLastUpdate(0);
                    androidacy_repo.setWebsite(RepoManager.ANDROIDACY_MAGISK_REPO_HOMEPAGE);
                    // now copy the data from the data class to the realm object using copyToRealmOrUpdate
                    realm1.copyToRealmOrUpdate(androidacy_repo);
                }
                // create magisk_alt_repo
                if (realm1.where(ReposList.class).equalTo("id", "magisk_alt_repo").findFirst() == null) {
                    ReposList magisk_alt_repo = realm1.createObject(ReposList.class, "magisk_alt_repo");
                    magisk_alt_repo.setName("Magisk Alt Repo");
                    magisk_alt_repo.setDonate(null);
                    magisk_alt_repo.setWebsite(RepoManager.MAGISK_ALT_REPO_HOMEPAGE);
                    magisk_alt_repo.setSupport(null);
                    magisk_alt_repo.setEnabled(true);
                    magisk_alt_repo.setUrl(RepoManager.MAGISK_ALT_REPO_HOMEPAGE);
                    magisk_alt_repo.setSubmitModule(RepoManager.MAGISK_ALT_REPO_HOMEPAGE + "/submission");
                    magisk_alt_repo.setLastUpdate(0);
                    // commit the changes
                    realm1.copyToRealmOrUpdate(magisk_alt_repo);
                }
                realm1.commitTransaction();
                realm1.close();
                if (BuildConfig.DEBUG) {
                    Timber.d("Realm databases created");
                    Realm realm3 = Realm.getInstance(config2);
                    RealmResults<ReposList> reposLists = realm3.where(ReposList.class).findAll();
                    assert reposLists != null;
                    Timber.d("ReposList.realm");
                    for (ReposList reposList : reposLists) {
                        Timber.d(reposList.toString());
                    }
                    realm3.close();
                }
            }
        });
    }
}