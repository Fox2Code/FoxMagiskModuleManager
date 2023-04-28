package com.fox2code.mmm;

import static com.fox2code.mmm.MainApplication.Iof;
import static com.fox2code.mmm.manager.ModuleInfo.FLAG_MM_REMOTE_MODULE;

import android.Manifest;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.CheckBox;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.SearchView;
import androidx.cardview.widget.CardView;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.fox2code.foxcompat.app.FoxActivity;
import com.fox2code.foxcompat.view.FoxDisplay;
import com.fox2code.mmm.background.BackgroundUpdateChecker;
import com.fox2code.mmm.installer.InstallerInitializer;
import com.fox2code.mmm.manager.LocalModuleInfo;
import com.fox2code.mmm.manager.ModuleManager;
import com.fox2code.mmm.module.ModuleViewAdapter;
import com.fox2code.mmm.module.ModuleViewListBuilder;
import com.fox2code.mmm.repo.RepoManager;
import com.fox2code.mmm.settings.SettingsActivity;
import com.fox2code.mmm.utils.ExternalHelper;
import com.fox2code.mmm.utils.io.net.Http;
import com.fox2code.mmm.utils.realm.ReposList;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.snackbar.Snackbar;

import org.matomo.sdk.extra.TrackHelper;

import java.sql.Timestamp;
import java.util.Objects;

import io.realm.Realm;
import io.realm.RealmConfiguration;
import timber.log.Timber;

public class MainActivity extends FoxActivity implements SwipeRefreshLayout.OnRefreshListener, SearchView.OnQueryTextListener, SearchView.OnCloseListener, OverScrollManager.OverScrollHelper {
    private static final int PRECISION = 10000;
    public static boolean doSetupNowRunning = true;
    public static boolean doSetupRestarting = false;
    public final ModuleViewListBuilder moduleViewListBuilder;
    public final ModuleViewListBuilder moduleViewListBuilderOnline;
    public LinearProgressIndicator progressIndicator;
    private ModuleViewAdapter moduleViewAdapter;
    private ModuleViewAdapter moduleViewAdapterOnline;
    private SwipeRefreshLayout swipeRefreshLayout;
    private int swipeRefreshLayoutOrigStartOffset;
    private int swipeRefreshLayoutOrigEndOffset;
    private long swipeRefreshBlocker = 0;
    private int overScrollInsetTop;
    private int overScrollInsetBottom;
    private RecyclerView moduleList;
    private RecyclerView moduleListOnline;
    private CardView searchCard;
    private SearchView searchView;
    private boolean initMode;

    public MainActivity() {
        this.moduleViewListBuilder = new ModuleViewListBuilder(this);
        this.moduleViewListBuilderOnline = new ModuleViewListBuilder(this);
        this.moduleViewListBuilder.addNotification(NotificationType.INSTALL_FROM_STORAGE);
    }

    @Override
    protected void onResume() {
        BackgroundUpdateChecker.onMainActivityResume(this);
        super.onResume();
    }

    @SuppressLint("RestrictedApi")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        this.initMode = true;
        if (doSetupRestarting) {
            doSetupRestarting = false;
        }
        BackgroundUpdateChecker.onMainActivityCreate(this);
        super.onCreate(savedInstanceState);
        TrackHelper.track().screen(this).with(MainApplication.getINSTANCE().getTracker());
        // track enabled repos
        RealmConfiguration realmConfig = new RealmConfiguration.Builder().name("ReposList.realm").encryptionKey(MainApplication.getINSTANCE().getKey()).directory(MainApplication.getINSTANCE().getDataDirWithPath("realms")).schemaVersion(1).allowQueriesOnUiThread(true).allowWritesOnUiThread(true).build();
        Realm realm = Realm.getInstance(realmConfig);
        StringBuilder enabledRepos = new StringBuilder();
        realm.executeTransaction(r -> {
            for (ReposList r2 : r.where(ReposList.class).equalTo("enabled", true).findAll()) {
                enabledRepos.append(r2.getUrl()).append(":").append(r2.getName()).append(",");
            }
        });
        if (enabledRepos.length() > 0) {
            enabledRepos.setLength(enabledRepos.length() - 1);
        }
        TrackHelper.track().event("enabled_repos", enabledRepos.toString()).with(MainApplication.getINSTANCE().getTracker());
        realm.close();
        // hide this behind a buildconfig flag for now, but crash the app if it's not an official build and not debug
        if (BuildConfig.ENABLE_PROTECTION && !Iof && !BuildConfig.DEBUG) {
            throw new RuntimeException("This is not an official build of FoxMMM");
        } else if (!Iof && !BuildConfig.DEBUG) {
            Timber.w("You may be running an untrusted build.");
            // Show a toast to warn the user
            Toast.makeText(this, R.string.not_official_build, Toast.LENGTH_LONG).show();
        }
        Timestamp ts = new Timestamp(System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000));
        // check if this build has expired
        Timestamp buildTime = new Timestamp(BuildConfig.BUILD_TIME);
        // if the build time is more than 30 days ago, throw an exception
        if (ts.getTime() >= buildTime.getTime()) {
            throw new IllegalStateException("This build has expired. Please download a stable build or update to the latest version.");
        }
        setContentView(R.layout.activity_main);
        this.setTitle(R.string.app_name);
        // set window flags to ignore status bar
        this.getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            WindowManager.LayoutParams layoutParams = this.getWindow().getAttributes();
            layoutParams.layoutInDisplayCutoutMode = // Support cutout in Android 9
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            this.getWindow().setAttributes(layoutParams);
        }
        this.progressIndicator = findViewById(R.id.progress_bar);
        this.swipeRefreshLayout = findViewById(R.id.swipe_refresh);
        this.swipeRefreshLayoutOrigStartOffset = this.swipeRefreshLayout.getProgressViewStartOffset();
        this.swipeRefreshLayoutOrigEndOffset = this.swipeRefreshLayout.getProgressViewEndOffset();
        this.swipeRefreshBlocker = Long.MAX_VALUE;
        this.moduleList = findViewById(R.id.module_list);
        this.moduleListOnline = findViewById(R.id.module_list_online);
        this.searchCard = findViewById(R.id.search_card);
        this.searchView = findViewById(R.id.search_bar);
        this.searchView.setIconified(true);
        this.moduleViewAdapter = new ModuleViewAdapter();
        this.moduleViewAdapterOnline = new ModuleViewAdapter();
        this.moduleList.setAdapter(this.moduleViewAdapter);
        this.moduleListOnline.setAdapter(this.moduleViewAdapterOnline);
        this.moduleList.setLayoutManager(new LinearLayoutManager(this));
        this.moduleListOnline.setLayoutManager(new LinearLayoutManager(this));
        this.moduleList.setItemViewCacheSize(4); // Default is 2
        this.swipeRefreshLayout.setOnRefreshListener(this);
        // add background blur if enabled
        this.updateBlurState();
        //hideActionBar();
        checkShowInitialSetup();
        this.moduleList.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                if (newState != RecyclerView.SCROLL_STATE_IDLE)
                    MainActivity.this.searchView.clearFocus();
            }
        });
        this.searchCard.setRadius(this.searchCard.getHeight() / 2F);
        this.searchView.setMinimumHeight(FoxDisplay.dpToPixel(16));
        this.searchView.setImeOptions(EditorInfo.IME_ACTION_SEARCH | EditorInfo.IME_FLAG_NO_FULLSCREEN);
        this.searchView.setOnQueryTextListener(this);
        this.searchView.setOnCloseListener(this);
        this.searchView.setOnQueryTextFocusChangeListener((v, h) -> {
            if (!h) {
                String query = this.searchView.getQuery().toString();
                if (query.isEmpty()) {
                    this.searchView.setIconified(true);
                }
            }
            this.cardIconifyUpdate();
        });
        this.searchView.setEnabled(false); // Enabled later
        this.cardIconifyUpdate();
        this.updateScreenInsets(this.getResources().getConfiguration());

        // on the bottom nav, there's a settings item. open the settings activity when it's clicked.
        BottomNavigationView bottomNavigationView = findViewById(R.id.bottom_navigation);
        bottomNavigationView.setOnItemSelectedListener(item -> {
            if (item.getItemId() == R.id.settings_menu_item) {
                TrackHelper.track().event("view_list", "settings").with(MainApplication.getINSTANCE().getTracker());
                startActivity(new Intent(MainActivity.this, SettingsActivity.class));
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
                finish();
            } else if (item.getItemId() == R.id.online_menu_item) {
                TrackHelper.track().event("view_list", "online_modules").with(MainApplication.getINSTANCE().getTracker());
                // set module_list_online as visible and module_list as gone. fade in/out
                this.moduleListOnline.setAlpha(0F);
                this.moduleListOnline.setVisibility(View.VISIBLE);
                this.moduleListOnline.animate().alpha(1F).setDuration(300).setListener(null);
                this.moduleList.animate().alpha(0F).setDuration(300).setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        MainActivity.this.moduleList.setVisibility(View.GONE);
                    }
                });
                // clear search view
                this.searchView.setQuery("", false);
                this.searchView.clearFocus();
            } else if (item.getItemId() == R.id.installed_menu_item) {
                TrackHelper.track().event("view_list", "installed_modules").with(MainApplication.getINSTANCE().getTracker());
                // set module_list_online as gone and module_list as visible. fade in/out
                this.moduleList.setAlpha(0F);
                this.moduleList.setVisibility(View.VISIBLE);
                this.moduleList.animate().alpha(1F).setDuration(300).setListener(null);
                this.moduleListOnline.animate().alpha(0F).setDuration(300).setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        MainActivity.this.moduleListOnline.setVisibility(View.GONE);
                    }
                });
                // set search view to cleared
                this.searchView.setQuery("", false);
                this.searchView.clearFocus();
            }
            return true;
        });
        // update the padding of blur_frame to match the new bottom nav height
        View blurFrame = findViewById(R.id.blur_frame);
        blurFrame.post(() -> blurFrame.setPadding(blurFrame.getPaddingLeft(), blurFrame.getPaddingTop(), blurFrame.getPaddingRight(), bottomNavigationView.getHeight()));
        // for some reason, root_container has a margin at the top. remove it.
        View rootContainer = findViewById(R.id.root_container);
        rootContainer.post(() -> {
            ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) rootContainer.getLayoutParams();
            params.topMargin = 0;
            rootContainer.setLayoutParams(params);
            rootContainer.setY(0F);
        });
        // reset update module and update module count in main application
        MainApplication.getINSTANCE().resetUpdateModule();
        InstallerInitializer.tryGetMagiskPathAsync(new InstallerInitializer.Callback() {
            @Override
            public void onPathReceived(String path) {
                Timber.i("Got magisk path: %s", path);
                if (InstallerInitializer.peekMagiskVersion() < Constants.MAGISK_VER_CODE_INSTALL_COMMAND)
                    moduleViewListBuilder.addNotification(NotificationType.MAGISK_OUTDATED);
                if (!MainApplication.isShowcaseMode())
                    moduleViewListBuilder.addNotification(NotificationType.INSTALL_FROM_STORAGE);
                ModuleManager.getINSTANCE().scan();
                ModuleManager.getINSTANCE().runAfterScan(moduleViewListBuilder::appendInstalledModules);
                this.commonNext();
            }

            @Override
            public void onFailure(int error) {
                Timber.e("Failed to get magisk path!");
                moduleViewListBuilder.addNotification(InstallerInitializer.getErrorNotification());
                moduleViewListBuilderOnline.addNotification(InstallerInitializer.getErrorNotification());
                this.commonNext();
            }

            public void commonNext() {
                if (BuildConfig.DEBUG) {
                    Timber.d("Common next");
                    moduleViewListBuilder.addNotification(NotificationType.DEBUG);
                }
                NotificationType.NO_INTERNET.autoAdd(moduleViewListBuilderOnline);
                // hide progress bar is repo-manager says we have no internet
                if (!RepoManager.getINSTANCE().hasConnectivity()) {
                    runOnUiThread(() -> {
                        progressIndicator.setVisibility(View.GONE);
                        progressIndicator.setIndeterminate(false);
                        progressIndicator.setMax(PRECISION);
                    });
                }
                updateScreenInsets(); // Fix an edge case
                if (waitInitialSetupFinished()) {
                    Timber.d("waiting...");
                    return;
                }
                swipeRefreshBlocker = System.currentTimeMillis() + 5_000L;
                if (MainApplication.isShowcaseMode())
                    moduleViewListBuilder.addNotification(NotificationType.SHOWCASE_MODE);
                if (!Http.hasWebView()) {
                    // Check Http for WebView availability
                    moduleViewListBuilder.addNotification(NotificationType.NO_WEB_VIEW);
                    // disable online tab
                    runOnUiThread(() -> {
                        bottomNavigationView.getMenu().getItem(1).setEnabled(false);
                        bottomNavigationView.setSelectedItemId(R.id.installed_menu_item);
                    });
                }
                moduleViewListBuilder.applyTo(moduleList, moduleViewAdapter);
                runOnUiThread(() -> {
                    progressIndicator.setIndeterminate(false);
                    progressIndicator.setMax(PRECISION);
                    // Fix insets not being accounted for correctly
                    updateScreenInsets(getResources().getConfiguration());
                });

                Timber.i("Scanning for modules!");
                if (BuildConfig.DEBUG) Timber.i("Initialize Update");
                final int max = ModuleManager.getINSTANCE().getUpdatableModuleCount();
                if (RepoManager.getINSTANCE().getCustomRepoManager() != null && RepoManager.getINSTANCE().getCustomRepoManager().needUpdate()) {
                    Timber.w("Need update on create");
                } else if (RepoManager.getINSTANCE().getCustomRepoManager() == null) {
                    Timber.w("CustomRepoManager is null");
                }
                // update compat metadata
                if (BuildConfig.DEBUG) Timber.i("Check Update Compat");
                AppUpdateManager.getAppUpdateManager().checkUpdateCompat();
                if (BuildConfig.DEBUG) Timber.i("Check Update");
                // update repos
                if (Http.hasWebView()) {
                    RepoManager.getINSTANCE().update(value -> runOnUiThread(max == 0 ? () -> progressIndicator.setProgressCompat((int) (value * PRECISION), true) : () -> progressIndicator.setProgressCompat((int) (value * PRECISION * 0.75F), true)));
                }
                // various notifications
                NotificationType.NEED_CAPTCHA_ANDROIDACY.autoAdd(moduleViewListBuilder);
                NotificationType.NEED_CAPTCHA_ANDROIDACY.autoAdd(moduleViewListBuilderOnline);
                NotificationType.DEBUG.autoAdd(moduleViewListBuilder);
                NotificationType.DEBUG.autoAdd(moduleViewListBuilderOnline);
                if (Http.hasWebView() && !NotificationType.REPO_UPDATE_FAILED.shouldRemove()) {
                    moduleViewListBuilder.addNotification(NotificationType.REPO_UPDATE_FAILED);
                } else {
                    if (!Http.hasWebView()) {
                        runOnUiThread(() -> {
                            progressIndicator.setProgressCompat(PRECISION, true);
                            progressIndicator.setVisibility(View.GONE);
                            searchView.setEnabled(false);
                            updateScreenInsets(getResources().getConfiguration());
                        });
                        return;
                    }
                    // Compatibility data still needs to be updated
                    AppUpdateManager appUpdateManager = AppUpdateManager.getAppUpdateManager();
                    if (BuildConfig.DEBUG) Timber.i("Check App Update");
                    if (BuildConfig.ENABLE_AUTO_UPDATER && appUpdateManager.checkUpdate(true))
                        moduleViewListBuilder.addNotification(NotificationType.UPDATE_AVAILABLE);
                    if (BuildConfig.DEBUG) Timber.i("Check Json Update");
                    if (max != 0) {
                        int current = 0;
                        for (LocalModuleInfo localModuleInfo : ModuleManager.getINSTANCE().getModules().values()) {
                            // if it has updateJson and FLAG_MM_REMOTE_MODULE is not set on flags, check for json update
                            // this is a dirty hack until we better store if it's a remote module
                            // the reasoning is that remote repos are considered "validated" while local modules are not
                            // for instance, a potential attacker could hijack a perfectly legitimate module and inject an updateJson with a malicious update - thereby bypassing any checks repos may have, without anyone noticing until it's too late
                            if (localModuleInfo.updateJson != null && (localModuleInfo.flags & FLAG_MM_REMOTE_MODULE) == 0) {
                                if (BuildConfig.DEBUG) Timber.i(localModuleInfo.id);
                                try {
                                    localModuleInfo.checkModuleUpdate();
                                } catch (Exception e) {
                                    Timber.e(e);
                                }
                                current++;
                                final int currentTmp = current;
                                runOnUiThread(() -> progressIndicator.setProgressCompat((int) ((1F * currentTmp / max) * PRECISION * 0.25F + (PRECISION * 0.75F)), true));
                            }
                        }
                    }
                }
                runOnUiThread(() -> {
                    progressIndicator.setProgressCompat(PRECISION, true);
                    progressIndicator.setVisibility(View.GONE);
                    searchView.setEnabled(true);
                    updateScreenInsets(getResources().getConfiguration());
                });
                if (BuildConfig.DEBUG) Timber.i("Apply");
                RepoManager.getINSTANCE().runAfterUpdate(moduleViewListBuilderOnline::appendRemoteModules);
                // logic to handle updateable modules
                moduleViewListBuilder.applyTo(moduleListOnline, moduleViewAdapterOnline);
                moduleViewListBuilderOnline.applyTo(moduleListOnline, moduleViewAdapterOnline);
                // if moduleViewListBuilderOnline has the upgradeable notification, show a badge on the online repo nav item
                if (MainApplication.getINSTANCE().modulesHaveUpdates) {
                    Timber.i("Applying badge");
                    new Handler(Looper.getMainLooper()).post(() -> {
                        final var badge = bottomNavigationView.getOrCreateBadge(R.id.online_menu_item);
                        badge.setVisible(true);
                        badge.setNumber(MainApplication.getINSTANCE().updateModuleCount);
                        badge.applyTheme(MainApplication.getInitialApplication().getTheme());
                        Timber.i("Badge applied");
                    });
                }
                Timber.i("Finished app opening state!");
            }
        }, true);
        // if system lang is not in MainApplication.supportedLocales, show a snackbar to ask user to help translate
        if (!MainApplication.supportedLocales.contains(this.getResources().getConfiguration().getLocales().get(0).getLanguage())) {
            // call showWeblateSnackbar() with language code and language name
            showWeblateSnackbar(this.getResources().getConfiguration().getLocales().get(0).getLanguage(), this.getResources().getConfiguration().getLocales().get(0).getDisplayLanguage());
        }
        ExternalHelper.INSTANCE.refreshHelper(this);
        this.initMode = false;
        // add preference listener to set isMatomoAllowed
        SharedPreferences.OnSharedPreferenceChangeListener listener = (sharedPreferences, key) -> {
            if (key.equals("pref_analytics_enabled")) {
                MainApplication.getINSTANCE().isMatomoAllowed = sharedPreferences.getBoolean(key, false);
                MainApplication.getINSTANCE().getTracker().setOptOut(MainApplication.getINSTANCE().isMatomoAllowed);
                Timber.d("Matomo is allowed change: %s", MainApplication.getINSTANCE().isMatomoAllowed);
            }
            if (MainApplication.getINSTANCE().isMatomoAllowed) {
                String value = sharedPreferences.getString(key, null);
                // then log
                if (value != null) {
                    TrackHelper.track().event("pref_changed", key + "=" + value).with(MainApplication.getINSTANCE().getTracker());
                }
            }
            Timber.d("Preference changed: %s", key);
        };
        MainApplication.getPreferences("mmm").registerOnSharedPreferenceChangeListener(listener);
    }

    private void cardIconifyUpdate() {
        boolean iconified = this.searchView.isIconified();
        int backgroundAttr = iconified ? MainApplication.isMonetEnabled() ? com.google.android.material.R.attr.colorSecondaryContainer : // Monet is special...
                com.google.android.material.R.attr.colorSecondary : com.google.android.material.R.attr.colorPrimarySurface;
        Resources.Theme theme = this.searchCard.getContext().getTheme();
        TypedValue value = new TypedValue();
        theme.resolveAttribute(backgroundAttr, value, true);
        this.searchCard.setCardBackgroundColor(value.data);
        this.searchCard.setAlpha(iconified ? 0.80F : 1F);
    }

    private void updateScreenInsets() {
        this.runOnUiThread(() -> this.updateScreenInsets(this.getResources().getConfiguration()));
    }

    private void updateScreenInsets(Configuration configuration) {
        boolean landscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE;
        int bottomInset = (landscape ? 0 : this.getNavigationBarHeight());
        int statusBarHeight = getStatusBarHeight() + FoxDisplay.dpToPixel(2);
        this.swipeRefreshLayout.setProgressViewOffset(false, swipeRefreshLayoutOrigStartOffset + statusBarHeight, swipeRefreshLayoutOrigEndOffset + statusBarHeight);
        this.moduleViewListBuilder.setHeaderPx(statusBarHeight);
        this.moduleViewListBuilderOnline.setHeaderPx(statusBarHeight);
        this.moduleViewListBuilder.setFooterPx(FoxDisplay.dpToPixel(4) + bottomInset + this.searchCard.getHeight());
        this.moduleViewListBuilderOnline.setFooterPx(FoxDisplay.dpToPixel(4) + bottomInset + this.searchCard.getHeight());
        this.searchCard.setRadius(this.searchCard.getHeight() / 2F);
        this.moduleViewListBuilder.updateInsets();
        //this.actionBarBlur.invalidate();
        this.overScrollInsetTop = statusBarHeight;
        this.overScrollInsetBottom = bottomInset;
        // set root_container to have zero padding
        findViewById(R.id.root_container).setPadding(0, statusBarHeight, 0, 0);
    }

    private void updateBlurState() {
        if (MainApplication.isBlurEnabled()) {
            // set bottom navigation bar color to transparent blur
            BottomNavigationView bottomNavigationView = findViewById(R.id.bottom_navigation);
            bottomNavigationView.setBackgroundColor(Color.TRANSPARENT);
            bottomNavigationView.setAlpha(0.8F);
            // set dialogs to have transparent blur
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND);
        }
    }

    @Override
    public void refreshUI() {
        super.refreshUI();
        if (this.initMode) return;
        this.initMode = true;
        Timber.i("Item Before");
        this.searchView.setQuery("", false);
        this.searchView.clearFocus();
        this.searchView.setIconified(true);
        this.cardIconifyUpdate();
        this.updateScreenInsets();
        this.updateBlurState();
        this.moduleViewListBuilder.setQuery(null);
        Timber.i("Item After");
        this.moduleViewListBuilder.refreshNotificationsUI(this.moduleViewAdapter);
        InstallerInitializer.tryGetMagiskPathAsync(new InstallerInitializer.Callback() {
            @Override
            public void onPathReceived(String path) {
                checkShowInitialSetup();
                // Wait for doSetupNow to finish
                while (doSetupNowRunning) {
                    try {
                        //noinspection BusyWait
                        Thread.sleep(100);
                    } catch (InterruptedException ignored) {
                        Thread.currentThread().interrupt();
                    }
                }
                if (InstallerInitializer.peekMagiskVersion() < Constants.MAGISK_VER_CODE_INSTALL_COMMAND)
                    moduleViewListBuilder.addNotification(NotificationType.MAGISK_OUTDATED);
                if (!MainApplication.isShowcaseMode())
                    moduleViewListBuilder.addNotification(NotificationType.INSTALL_FROM_STORAGE);
                ModuleManager.getINSTANCE().scan();
                ModuleManager.getINSTANCE().runAfterScan(moduleViewListBuilder::appendInstalledModules);
                this.commonNext();
            }

            @Override
            public void onFailure(int error) {
                Timber.e("Error: %s", error);
                moduleViewListBuilder.addNotification(InstallerInitializer.getErrorNotification());
                moduleViewListBuilderOnline.addNotification(InstallerInitializer.getErrorNotification());
                this.commonNext();
            }

            public void commonNext() {
                Timber.i("Common Before");
                if (MainApplication.isShowcaseMode())
                    moduleViewListBuilder.addNotification(NotificationType.SHOWCASE_MODE);
                NotificationType.NEED_CAPTCHA_ANDROIDACY.autoAdd(moduleViewListBuilderOnline);
                NotificationType.NO_INTERNET.autoAdd(moduleViewListBuilderOnline);
                if (AppUpdateManager.getAppUpdateManager().checkUpdate(false))
                    moduleViewListBuilder.addNotification(NotificationType.UPDATE_AVAILABLE);
                RepoManager.getINSTANCE().updateEnabledStates();
                if (RepoManager.getINSTANCE().getCustomRepoManager().needUpdate()) {
                    runOnUiThread(() -> {
                        progressIndicator.setIndeterminate(false);
                        progressIndicator.setMax(PRECISION);
                    });
                    if (BuildConfig.DEBUG) Timber.i("Check Update");
                    RepoManager.getINSTANCE().update(value -> runOnUiThread(() -> progressIndicator.setProgressCompat((int) (value * PRECISION), true)));
                    runOnUiThread(() -> {
                        progressIndicator.setProgressCompat(PRECISION, true);
                        progressIndicator.setVisibility(View.GONE);
                    });
                }
                if (BuildConfig.DEBUG) Timber.i("Apply");
                RepoManager.getINSTANCE().runAfterUpdate(moduleViewListBuilderOnline::appendRemoteModules);
                Timber.i("Common Before applyTo");
                moduleViewListBuilderOnline.applyTo(moduleListOnline, moduleViewAdapterOnline);
                Timber.i("Common After");
            }
        });
        this.initMode = false;
    }

    @Override
    protected void onWindowUpdated() {
        this.updateScreenInsets();
    }

    @Override
    public void onRefresh() {
        if (this.swipeRefreshBlocker > System.currentTimeMillis() || this.initMode || this.progressIndicator == null || this.progressIndicator.getVisibility() == View.VISIBLE || doSetupNowRunning) {
            this.swipeRefreshLayout.setRefreshing(false);
            return; // Do not double scan
        }
        if (BuildConfig.DEBUG) Timber.i("Refresh");
        this.progressIndicator.setVisibility(View.VISIBLE);
        this.progressIndicator.setProgressCompat(0, false);
        this.swipeRefreshBlocker = System.currentTimeMillis() + 5_000L;
        // this.swipeRefreshLayout.setRefreshing(true); ??
        new Thread(() -> {
            Http.cleanDnsCache(); // Allow DNS reload from network
            final int max = ModuleManager.getINSTANCE().getUpdatableModuleCount();
            RepoManager.getINSTANCE().update(value -> runOnUiThread(max == 0 ? () -> progressIndicator.setProgressCompat((int) (value * PRECISION), true) : () -> progressIndicator.setProgressCompat((int) (value * PRECISION * 0.75F), true)));
            NotificationType.NEED_CAPTCHA_ANDROIDACY.autoAdd(moduleViewListBuilder);
            if (!NotificationType.NO_INTERNET.shouldRemove()) {
                moduleViewListBuilderOnline.addNotification(NotificationType.NO_INTERNET);
            } else if (!NotificationType.REPO_UPDATE_FAILED.shouldRemove()) {
                moduleViewListBuilder.addNotification(NotificationType.REPO_UPDATE_FAILED);
            } else {
                // Compatibility data still needs to be updated
                AppUpdateManager appUpdateManager = AppUpdateManager.getAppUpdateManager();
                if (BuildConfig.DEBUG) Timber.i("Check App Update");
                if (BuildConfig.ENABLE_AUTO_UPDATER && appUpdateManager.checkUpdate(true))
                    moduleViewListBuilder.addNotification(NotificationType.UPDATE_AVAILABLE);
                if (BuildConfig.DEBUG) Timber.i("Check Json Update");
                if (max != 0) {
                    int current = 0;
                    for (LocalModuleInfo localModuleInfo : ModuleManager.getINSTANCE().getModules().values()) {
                        if (localModuleInfo.updateJson != null && (localModuleInfo.flags & FLAG_MM_REMOTE_MODULE) == 0) {
                            if (BuildConfig.DEBUG) Timber.i(localModuleInfo.id);
                            try {
                                localModuleInfo.checkModuleUpdate();
                            } catch (Exception e) {
                                Timber.e(e);
                            }
                            current++;
                            final int currentTmp = current;
                            runOnUiThread(() -> progressIndicator.setProgressCompat((int) ((1F * currentTmp / max) * PRECISION * 0.25F + (PRECISION * 0.75F)), true));
                        }
                    }
                }
            }
            if (BuildConfig.DEBUG) Timber.i("Apply");
            runOnUiThread(() -> {
                this.progressIndicator.setVisibility(View.GONE);
                this.swipeRefreshLayout.setRefreshing(false);
            });
            NotificationType.NEED_CAPTCHA_ANDROIDACY.autoAdd(moduleViewListBuilder);
            RepoManager.getINSTANCE().updateEnabledStates();
            RepoManager.getINSTANCE().runAfterUpdate(moduleViewListBuilderOnline::appendRemoteModules);
            this.moduleViewListBuilderOnline.applyTo(moduleListOnline, moduleViewAdapterOnline);
            this.moduleViewListBuilder.applyTo(moduleList, moduleViewAdapter);
        }, "Repo update thread").start();
    }

    @Override
    public boolean onQueryTextSubmit(final String query) {
        this.searchView.clearFocus();
        if (this.initMode) return false;
        TrackHelper.track().search(query).with(MainApplication.getINSTANCE().getTracker());
        if (this.moduleViewListBuilder.setQueryChange(query)) {
            Timber.i("Query submit: %s on offline list", query);
            new Thread(() -> this.moduleViewListBuilder.applyTo(moduleList, moduleViewAdapter), "Query update thread").start();
        }
        // same for online list
        if (this.moduleViewListBuilderOnline.setQueryChange(query)) {
            Timber.i("Query submit: %s on online list", query);
            new Thread(() -> this.moduleViewListBuilderOnline.applyTo(moduleListOnline, moduleViewAdapterOnline), "Query update thread").start();
        }
        return true;
    }

    @Override
    public boolean onQueryTextChange(String query) {
        if (this.initMode) return false;
        TrackHelper.track().search(query).with(MainApplication.getINSTANCE().getTracker());
        if (this.moduleViewListBuilder.setQueryChange(query)) {
            Timber.i("Query submit: %s on offline list", query);
            new Thread(() -> this.moduleViewListBuilder.applyTo(moduleList, moduleViewAdapter), "Query update thread").start();
        }
        // same for online list
        if (this.moduleViewListBuilderOnline.setQueryChange(query)) {
            Timber.i("Query submit: %s on online list", query);
            new Thread(() -> this.moduleViewListBuilderOnline.applyTo(moduleListOnline, moduleViewAdapterOnline), "Query update thread").start();
        }
        return false;
    }

    @Override
    public boolean onClose() {
        if (this.initMode) return false;
        if (this.moduleViewListBuilder.setQueryChange(null)) {
            new Thread(() -> this.moduleViewListBuilder.applyTo(moduleList, moduleViewAdapter), "Query update thread").start();
        }
        // same for online list
        if (this.moduleViewListBuilderOnline.setQueryChange(null)) {
            new Thread(() -> this.moduleViewListBuilderOnline.applyTo(moduleListOnline, moduleViewAdapterOnline), "Query update thread").start();
        }
        return false;
    }

    @Override
    public int getOverScrollInsetTop() {
        return this.overScrollInsetTop;
    }

    @Override
    public int getOverScrollInsetBottom() {
        return this.overScrollInsetBottom;
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        this.updateScreenInsets();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        this.updateScreenInsets();
    }

    @SuppressLint("RestrictedApi")
    private void ensurePermissions() {
        if (BuildConfig.DEBUG) Timber.i("Ensure Permissions");
        // First, check if user has said don't ask again by checking if pref_dont_ask_again_notification_permission is true
        if (!PreferenceManager.getDefaultSharedPreferences(this).getBoolean("pref_dont_ask_again_notification_permission", false)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                if (BuildConfig.DEBUG) Timber.i("Request Notification Permission");
                if (FoxActivity.getFoxActivity(this).shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
                    // Show a dialog explaining why we need this permission, which is to show
                    // notifications for updates
                    runOnUiThread(() -> {
                        if (BuildConfig.DEBUG) Timber.i("Show Notification Permission Dialog");
                        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this);
                        builder.setTitle(R.string.permission_notification_title);
                        builder.setMessage(R.string.permission_notification_message);
                        // Don't ask again checkbox
                        View view = getLayoutInflater().inflate(R.layout.dialog_checkbox, null);
                        CheckBox checkBox = view.findViewById(R.id.checkbox);
                        checkBox.setText(R.string.dont_ask_again);
                        checkBox.setOnCheckedChangeListener((buttonView, isChecked) -> PreferenceManager.getDefaultSharedPreferences(this).edit().putBoolean("pref_dont_ask_again_notification_permission", isChecked).apply());
                        builder.setView(view);
                        builder.setPositiveButton(R.string.permission_notification_grant, (dialog, which) -> {
                            // Request the permission
                            this.requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 0);
                            doSetupNowRunning = false;
                        });
                        builder.setNegativeButton(R.string.cancel, (dialog, which) -> {
                            // Set pref_background_update_check to false and dismiss dialog
                            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
                            prefs.edit().putBoolean("pref_background_update_check", false).apply();
                            dialog.dismiss();
                            doSetupNowRunning = false;
                        });
                        builder.show();
                        if (BuildConfig.DEBUG) Timber.i("Show Notification Permission Dialog Done");
                    });
                } else {
                    // Request the permission
                    if (BuildConfig.DEBUG) Timber.i("Request Notification Permission");
                    this.requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 0);
                    if (BuildConfig.DEBUG) {
                        // Log if granted via onRequestPermissionsResult
                        boolean granted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
                        Timber.i("Request Notification Permission Done. Result: %s", granted);
                    }
                    doSetupNowRunning = false;
                }
                // Next branch is for < android 13 and user has blocked notifications
            } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU && !NotificationManagerCompat.from(this).areNotificationsEnabled()) {
                runOnUiThread(() -> {
                    MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this);
                    builder.setTitle(R.string.permission_notification_title);
                    builder.setMessage(R.string.permission_notification_message);
                    // Don't ask again checkbox
                    View view = getLayoutInflater().inflate(R.layout.dialog_checkbox, null);
                    CheckBox checkBox = view.findViewById(R.id.checkbox);
                    checkBox.setText(R.string.dont_ask_again);
                    checkBox.setOnCheckedChangeListener((buttonView, isChecked) -> PreferenceManager.getDefaultSharedPreferences(this).edit().putBoolean("pref_dont_ask_again_notification_permission", isChecked).apply());
                    builder.setView(view);
                    builder.setPositiveButton(R.string.permission_notification_grant, (dialog, which) -> {
                        // Open notification settings
                        Intent intent = new Intent();
                        intent.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                        Uri uri = Uri.fromParts("package", getPackageName(), null);
                        intent.setData(uri);
                        startActivity(intent);
                        doSetupNowRunning = false;
                    });
                    builder.setNegativeButton(R.string.cancel, (dialog, which) -> {
                        // Set pref_background_update_check to false and dismiss dialog
                        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
                        prefs.edit().putBoolean("pref_background_update_check", false).apply();
                        dialog.dismiss();
                        doSetupNowRunning = false;
                    });
                    builder.show();
                });
            } else {
                doSetupNowRunning = false;
            }
        } else {
            if (BuildConfig.DEBUG)
                Timber.i("Notification Permission Already Granted or Don't Ask Again");
            doSetupNowRunning = false;
        }
    }

    // Method to show a setup box on first launch
    @SuppressLint({"InflateParams", "RestrictedApi", "UnspecifiedImmutableFlag", "ApplySharedPref"})
    private void checkShowInitialSetup() {
        if (BuildConfig.DEBUG) Timber.i("Checking if we need to run setup");
        // Check if this is the first launch using prefs and if doSetupRestarting was passed in the intent
        SharedPreferences prefs = MainApplication.getPreferences("mmm");
        boolean firstLaunch = !Objects.equals(prefs.getString("last_shown_setup", null), "v1");
        // First launch
        // this is intentionally separate from the above if statement, because it needs to be checked even if the first launch check is true due to some weird edge cases
        if (getIntent().getBooleanExtra("doSetupRestarting", false)) {
            // Restarting setup
            firstLaunch = false;
        }
        if (BuildConfig.DEBUG) {
            Timber.i("First launch: %s, pref value: %s", firstLaunch, prefs.getString("last_shown_setup", null));
        }
        if (firstLaunch) {
            doSetupNowRunning = true;
            // Launch setup wizard
            if (BuildConfig.DEBUG) Timber.i("Launching setup wizard");
            // Show setup activity
            Intent intent = new Intent(this, SetupActivity.class);
            finish();
            startActivity(intent);
        } else {
            ensurePermissions();
        }
    }

    /**
     * @return true if the load workflow must be stopped.
     */
    private boolean waitInitialSetupFinished() {
        if (BuildConfig.DEBUG) Timber.i("waitInitialSetupFinished");
        if (doSetupNowRunning) updateScreenInsets(); // Fix an edge case
        try {
            // Wait for doSetupNow to finish
            while (doSetupNowRunning) {
                //noinspection BusyWait
                Thread.sleep(50);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return true;
        }
        return doSetupRestarting;
    }

    /**
     * Shows a snackbar offering to take users to Weblate if their language is not available.
     *
     * @param language     The language code.
     * @param languageName The language name.
     */
    @SuppressLint("RestrictedApi")
    private void showWeblateSnackbar(String language, String languageName) {
        Snackbar snackbar = Snackbar.make(findViewById(R.id.root_container), getString(R.string.language_not_available, languageName), Snackbar.LENGTH_LONG);
        snackbar.setAction(R.string.ok, v -> {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setData(Uri.parse("https://translate.nift4.org/engage/foxmmm/?language=" + language));
            startActivity(intent);
        });
        snackbar.show();
    }
}