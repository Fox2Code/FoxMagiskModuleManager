package com.fox2code.mmm;

import static com.fox2code.mmm.utils.IntentHelper.getActivity;

import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentActivity;

import com.fox2code.foxcompat.app.FoxActivity;
import com.fox2code.mmm.databinding.ActivitySetupBinding;
import com.fox2code.mmm.repo.RepoManager;
import com.fox2code.mmm.utils.realm.ReposList;
import com.fox2code.rosettax.LanguageActivity;
import com.fox2code.rosettax.LanguageSwitcher;
import com.google.android.material.bottomnavigation.BottomNavigationItemView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.topjohnwu.superuser.internal.UiThreadHandler;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.util.Objects;

import io.realm.Realm;
import io.realm.RealmConfiguration;
import timber.log.Timber;

public class SetupActivity extends FoxActivity implements LanguageActivity {
    private int cachedTheme;
    private boolean realmDatabasesCreated;

    @SuppressLint({"ApplySharedPref", "RestrictedApi"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.setTitle(R.string.setup_title);
        this.getWindow().setFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION, 0);
        createFiles();
        disableUpdateActivityForFdroidFlavor();
        // Set theme
        SharedPreferences prefs = MainApplication.getSharedPreferences("mmm");
        switch (prefs.getString("theme", "system")) {
            case "light" -> setTheme(R.style.Theme_MagiskModuleManager_Monet_Light);
            case "dark" -> setTheme(R.style.Theme_MagiskModuleManager_Monet_Dark);
            case "system" -> setTheme(R.style.Theme_MagiskModuleManager_Monet);
            case "black" -> setTheme(R.style.Theme_MagiskModuleManager_Monet_Black);
            case "transparent_light" ->
                    setTheme(R.style.Theme_MagiskModuleManager_Transparent_Light);
        }
        ActivitySetupBinding binding = ActivitySetupBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        View view = binding.getRoot();
        ((MaterialSwitch) Objects.requireNonNull(view.findViewById(R.id.setup_background_update_check))).setChecked(BuildConfig.ENABLE_AUTO_UPDATER);
        ((MaterialSwitch) Objects.requireNonNull(view.findViewById(R.id.setup_crash_reporting))).setChecked(BuildConfig.DEFAULT_ENABLE_CRASH_REPORTING);
        // pref_crash_reporting_pii
        ((MaterialSwitch) Objects.requireNonNull(view.findViewById(R.id.setup_crash_reporting_pii))).setChecked(BuildConfig.DEFAULT_ENABLE_CRASH_REPORTING_PII);
        // pref_analytics_enabled
        ((MaterialSwitch) Objects.requireNonNull(view.findViewById(R.id.setup_app_analytics))).setChecked(BuildConfig.DEFAULT_ENABLE_ANALYTICS);
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
            ((MaterialSwitch) Objects.requireNonNull(view.findViewById(R.id.setup_background_update_check))).setOnCheckedChangeListener((buttonView, isChecked) -> Timber.i("Automatic update Check: %s", isChecked));
            ((MaterialSwitch) Objects.requireNonNull(view.findViewById(R.id.setup_crash_reporting))).setOnCheckedChangeListener((buttonView, isChecked) -> Timber.i("Crash Reporting: %s", isChecked));
            ((MaterialSwitch) Objects.requireNonNull(view.findViewById(R.id.setup_crash_reporting_pii))).setOnCheckedChangeListener((buttonView, isChecked) -> Timber.i("Crash Reporting PII: %s", isChecked));
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
                        case "light" -> setTheme(R.style.Theme_MagiskModuleManager_Monet_Light);
                        case "dark" -> setTheme(R.style.Theme_MagiskModuleManager_Monet_Dark);
                        case "system" -> setTheme(R.style.Theme_MagiskModuleManager_Monet);
                        case "black" -> setTheme(R.style.Theme_MagiskModuleManager_Monet_Black);
                        case "transparent_light" ->
                                setTheme(R.style.Theme_MagiskModuleManager_Transparent_Light);
                    }
                    // restart the activity because switching to transparent pisses the rendering engine off
                    Intent intent = new Intent(this, SetupActivity.class);
                    finish();
                    // ensure intent originates from the same package
                    intent.setPackage(getPackageName());
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
        BottomNavigationItemView setupButton = view.findViewById(R.id.setup_finish);
        // enable finish button when user scrolls to the bottom
        findViewById(R.id.setupNestedScrollView).setOnScrollChangeListener((v, scrollX, scrollY, oldScrollX, oldScrollY) -> {
            if (scrollY > oldScrollY) {
                setupButton.setEnabled(true);
            }
        });
        setupButton.setOnClickListener(v -> {
            Timber.i("Setup button clicked");
            // get instance of editor
            Timber.d("Saving preferences");
            SharedPreferences.Editor editor = prefs.edit();
            Timber.d("Got editor: %s", editor);
            // Set the Automatic update check pref
            editor.putBoolean("pref_background_update_check", ((MaterialSwitch) Objects.requireNonNull(view.findViewById(R.id.setup_background_update_check))).isChecked());
            // require wifi pref
            editor.putBoolean("pref_background_update_check_wifi", ((MaterialSwitch) Objects.requireNonNull(view.findViewById(R.id.setup_background_update_check_require_wifi))).isChecked());
            // Set the crash reporting pref
            editor.putBoolean("pref_crash_reporting", ((MaterialSwitch) Objects.requireNonNull(view.findViewById(R.id.setup_crash_reporting))).isChecked());
            // Set the crash reporting PII pref
            editor.putBoolean("pref_crash_reporting_pii", ((MaterialSwitch) Objects.requireNonNull(view.findViewById(R.id.setup_crash_reporting_pii))).isChecked());
            editor.putBoolean("pref_analytics_enabled", ((MaterialSwitch) Objects.requireNonNull(view.findViewById(R.id.setup_app_analytics))).isChecked());
            Timber.d("Saving preferences");
            // Set the repos in the ReposList realm db
            RealmConfiguration realmConfig = new RealmConfiguration.Builder().name("ReposList.realm").encryptionKey(MainApplication.getINSTANCE().getKey()).directory(MainApplication.getINSTANCE().getDataDirWithPath("realms")).schemaVersion(1).build();
            boolean androidacyRepo = ((MaterialSwitch) Objects.requireNonNull(view.findViewById(R.id.setup_androidacy_repo))).isChecked();
            boolean magiskAltRepo = ((MaterialSwitch) Objects.requireNonNull(view.findViewById(R.id.setup_magisk_alt_repo))).isChecked();
            Realm realm = Realm.getInstance(realmConfig);
            Timber.d("Realm instance: %s", realm);
            if (realm.isInTransaction()) {
                realm.commitTransaction();
                Timber.d("Committed last unfinished transaction");
            }
            // check if instance has been closed
            if (realm.isClosed()) {
                Timber.d("Realm instance was closed, reopening");
                realm = Realm.getInstance(realmConfig);
            }
            realm.executeTransactionAsync(r -> {
                Timber.d("Realm transaction started");
                Objects.requireNonNull(r.where(ReposList.class).equalTo("id", "androidacy_repo").findFirst()).setEnabled(androidacyRepo);
                Objects.requireNonNull(r.where(ReposList.class).equalTo("id", "magisk_alt_repo").findFirst()).setEnabled(magiskAltRepo);
                Timber.d("Realm transaction committing");
                // commit the changes
                r.commitTransaction();
                r.close();
                Timber.d("Realm transaction committed");
            });
            editor.putString("last_shown_setup", "v1");
            // Commit the changes
            editor.commit();
            // sleep to allow the realm transaction to finish
            try {
                Thread.sleep(250);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            // Log the changes
            Timber.d("Setup finished. Preferences: %s", prefs.getAll());
            Timber.d("Androidacy repo: %s", androidacyRepo);
            Timber.d("Magisk Alt repo: %s", magiskAltRepo);
            // log last shown setup
            Timber.d("Last shown setup: %s", prefs.getString("last_shown_setup", "v0"));
            // Restart the activity
            MainActivity.doSetupRestarting = true;
            PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
            try {
                pendingIntent.send();
            } catch (PendingIntent.CanceledException e) {
                e.printStackTrace();
            }
            android.os.Process.killProcess(android.os.Process.myPid());
        });
        // Cancel button
        BottomNavigationItemView cancelButton = view.findViewById(R.id.cancel_setup);
        // unselect the cancel button because it's selected by default
        cancelButton.setSelected(false);
        cancelButton.setOnClickListener(v -> {
            Timber.i("Cancel button clicked");
            // close the app
            finish();
        });
    }

    @Override
    public Resources.Theme getTheme() {
        Resources.Theme theme = super.getTheme();
        // try cached value
        if (cachedTheme != 0) {
            theme.applyStyle(cachedTheme, true);
            return theme;
        }
        // Set the theme
        SharedPreferences prefs = MainApplication.getSharedPreferences("mmm");
        String themePref = prefs.getString("pref_theme", "system");
        switch (themePref) {
            case "light" -> {
                theme.applyStyle(R.style.Theme_MagiskModuleManager_Monet_Light, true);
                cachedTheme = R.style.Theme_MagiskModuleManager_Monet_Light;
            }
            case "dark" -> {
                theme.applyStyle(R.style.Theme_MagiskModuleManager_Monet_Dark, true);
                cachedTheme = R.style.Theme_MagiskModuleManager_Monet_Dark;
            }
            case "system" -> {
                theme.applyStyle(R.style.Theme_MagiskModuleManager_Monet, true);
                cachedTheme = R.style.Theme_MagiskModuleManager_Monet;
            }
            case "black" -> {
                theme.applyStyle(R.style.Theme_MagiskModuleManager_Monet_Black, true);
                cachedTheme = R.style.Theme_MagiskModuleManager_Monet_Black;
            }
            case "transparent_light" -> {
                theme.applyStyle(R.style.Theme_MagiskModuleManager_Transparent_Light, true);
                cachedTheme = R.style.Theme_MagiskModuleManager_Transparent_Light;
            }
        }
        return theme;
    }

    @Override
    @SuppressLint({"InlinedApi", "RestrictedApi"})
    public void refreshRosettaX() {
        // refresh app language
        runOnUiThread(() -> {
            // refresh activity
            Intent intent = new Intent(this, SetupActivity.class);
            finish();
            startActivity(intent);
        });
    }

    // creates the realm database
    private void createRealmDatabase() {
        if (realmDatabasesCreated) {
            Timber.d("Realm databases already created");
            return;
        }
        Timber.d("Creating Realm databases");
        long startTime = System.currentTimeMillis();
        // create encryption key
        Timber.d("Creating encryption key");
        byte[] key = MainApplication.getINSTANCE().getKey();
        // create the realm database for ReposList
        // create the realm configuration
        RealmConfiguration config = new RealmConfiguration.Builder().name("ReposList.realm").directory(MainApplication.getINSTANCE().getDataDirWithPath("realms")).schemaVersion(1).encryptionKey(key).build();
        // get the instance
        Realm.getInstanceAsync(config, new Realm.Callback() {
            @Override
            public void onSuccess(@NonNull Realm realm) {
                Timber.d("Realm instance: %s", realm);
                realm.beginTransaction();
                // create the ReposList realm database
                Timber.d("Creating ReposList realm database");
                if (realm.where(ReposList.class).equalTo("id", "androidacy_repo").findFirst() == null) {
                    Timber.d("Creating androidacy_repo");
                    // create the androidacy_repo row
                    // cant use createObject because it crashes because reasons. use copyToRealm instead
                    ReposList androidacy_repo = realm.createObject(ReposList.class, "androidacy_repo");
                    Timber.d("Created androidacy_repo object");
                    androidacy_repo.setName("Androidacy Repo");
                    Timber.d("Set androidacy_repo name");
                    androidacy_repo.setDonate("https://www.androidacy.com/membership-account/membership-join/?utm_source=fox-app&utm_medium=app&utm_campaign=app");
                    Timber.d("Set androidacy_repo donate");
                    androidacy_repo.setSupport("https://t.me/androidacy_discussions");
                    Timber.d("Set androidacy_repo support");
                    androidacy_repo.setSubmitModule("https://www.androidacy.com/module-repository-applications/?utm_source=fox-app&utm_medium=app&utm_campaign=app");
                    Timber.d("Set androidacy_repo submit module");
                    androidacy_repo.setUrl(RepoManager.ANDROIDACY_MAGISK_REPO_ENDPOINT);
                    Timber.d("Set androidacy_repo url");
                    androidacy_repo.setEnabled(true);
                    Timber.d("Set androidacy_repo enabled");
                    androidacy_repo.setLastUpdate(0);
                    Timber.d("Set androidacy_repo last update");
                    androidacy_repo.setWebsite(RepoManager.ANDROIDACY_MAGISK_REPO_HOMEPAGE);
                    Timber.d("Set androidacy_repo website");
                    // now copy the data from the data class to the realm object using copyToRealmOrUpdate
                    Timber.d("Copying data to realm object");
                    realm.copyToRealmOrUpdate(androidacy_repo);
                    Timber.d("Created androidacy_repo");
                }
                // create magisk_alt_repo
                if (realm.where(ReposList.class).equalTo("id", "magisk_alt_repo").findFirst() == null) {
                    Timber.d("Creating magisk_alt_repo");
                    ReposList magisk_alt_repo = realm.createObject(ReposList.class, "magisk_alt_repo");
                    Timber.d("Created magisk_alt_repo object");
                    magisk_alt_repo.setName("Magisk Alt Repo");
                    magisk_alt_repo.setDonate(null);
                    magisk_alt_repo.setWebsite(RepoManager.MAGISK_ALT_REPO_HOMEPAGE);
                    magisk_alt_repo.setSupport(null);
                    magisk_alt_repo.setEnabled(true);
                    magisk_alt_repo.setUrl(RepoManager.MAGISK_ALT_REPO);
                    magisk_alt_repo.setSubmitModule(RepoManager.MAGISK_ALT_REPO_HOMEPAGE + "/submission");
                    magisk_alt_repo.setLastUpdate(0);
                    // commit the changes
                    Timber.d("Copying data to realm object");
                    realm.copyToRealmOrUpdate(magisk_alt_repo);
                    Timber.d("Created magisk_alt_repo");
                }
                realm.commitTransaction();
                realm.close();
                realmDatabasesCreated = true;
                Timber.d("Realm transaction finished");
                long endTime = System.currentTimeMillis();
                Timber.d("Realm databases created in %d ms", endTime - startTime);
            }
        });
    }

    public void createFiles() {
        // use cookiemanager to create the cookie database
        try {
            CookieManager.getInstance();
        } catch (Exception e) {
            Timber.e(e);
            // show a toast
            runOnUiThread(() -> Toast.makeText(this, R.string.error_creating_cookie_database, Toast.LENGTH_LONG).show());
        }
        // we literally only use these to create the http cache folders
        try {
            FileUtils.forceMkdir(new File(MainApplication.getINSTANCE().getDataDir() + "/cache/cronet"));
            FileUtils.forceMkdir(new File(MainApplication.getINSTANCE().getDataDir() + "/cache/WebView/Default/HTTP Cache/Code Cache/wasm"));
            FileUtils.forceMkdir(new File(MainApplication.getINSTANCE().getDataDir() + "/cache/WebView/Default/HTTP Cache/Code Cache/js"));
            FileUtils.forceMkdir(new File(MainApplication.getINSTANCE().getDataDir() + "/repos/magisk_alt_repo"));
        } catch (IOException e) {
            Timber.e(e);
        }
        createRealmDatabase();
    }

    @SuppressWarnings("ConstantConditions")
    public void disableUpdateActivityForFdroidFlavor() {
        if (BuildConfig.FLAVOR.equals("fdroid")) {
            // check if the update activity is enabled
            PackageManager pm = getPackageManager();
            ComponentName componentName = new ComponentName(this, UpdateActivity.class);
            int componentEnabledSetting = pm.getComponentEnabledSetting(componentName);
            if (componentEnabledSetting == PackageManager.COMPONENT_ENABLED_STATE_ENABLED) {
                Timber.d("Disabling update activity for fdroid flavor");
                // disable update activity through package manager
                pm.setComponentEnabledSetting(componentName, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);
            }
        }
    }
}