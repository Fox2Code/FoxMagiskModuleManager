package com.fox2code.mmm;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.CheckBox;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.SearchView;
import androidx.cardview.widget.CardView;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;
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
import com.fox2code.mmm.utils.BlurUtils;
import com.fox2code.mmm.utils.ExternalHelper;
import com.fox2code.mmm.utils.IntentHelper;
import com.fox2code.mmm.utils.io.Http;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import org.chromium.net.ExperimentalCronetEngine;
import org.chromium.net.urlconnection.CronetURLStreamHandlerFactory;

import java.net.URL;

import eightbitlab.com.blurview.BlurView;

public class MainActivity extends FoxActivity implements SwipeRefreshLayout.OnRefreshListener, SearchView.OnQueryTextListener, SearchView.OnCloseListener, OverScrollManager.OverScrollHelper {
    private static final String TAG = "MainActivity";
    private static final int PRECISION = 10000;
    public static boolean doSetupNowRunning = true;
    public final ModuleViewListBuilder moduleViewListBuilder;
    public LinearProgressIndicator progressIndicator;
    private ModuleViewAdapter moduleViewAdapter;
    private SwipeRefreshLayout swipeRefreshLayout;
    private int swipeRefreshLayoutOrigStartOffset;
    private int swipeRefreshLayoutOrigEndOffset;
    private long swipeRefreshBlocker = 0;
    private int overScrollInsetTop;
    private int overScrollInsetBottom;
    private TextView actionBarPadding;
    private BlurView actionBarBlur;
    private ColorDrawable actionBarBackground;
    private RecyclerView moduleList;
    private CardView searchCard;
    private SearchView searchView;
    private boolean initMode;
    public static boolean doSetupRestarting = false;
    private boolean urlFactoryInstalled = false;

    public MainActivity() {
        this.moduleViewListBuilder = new ModuleViewListBuilder(this);
        this.moduleViewListBuilder.addNotification(NotificationType.INSTALL_FROM_STORAGE);
    }

    @Override
    protected void onResume() {
        BackgroundUpdateChecker.onMainActivityResume(this);
        super.onResume();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        this.initMode = true;
        // Ensure HTTP Cache directories are created
        Http.ensureCacheDirs(this);
        if (!urlFactoryInstalled) {
            try {
                ExperimentalCronetEngine cronetEngine = new ExperimentalCronetEngine.Builder(this).build();
                CronetURLStreamHandlerFactory cronetURLStreamHandlerFactory = new CronetURLStreamHandlerFactory(cronetEngine);
                try {
                    URL.setURLStreamHandlerFactory(cronetURLStreamHandlerFactory);
                } catch (
                        Error e) {
                    Log.e(TAG, "Failed to install Cronet URLStreamHandlerFactory");
                }
                urlFactoryInstalled = true;
            } catch (
                    Throwable t) {
                Log.e(TAG, "Failed to install CronetURLStreamHandlerFactory - other");
            }
        }
        if (doSetupRestarting) {
            doSetupRestarting = false;
        }
        BackgroundUpdateChecker.onMainActivityCreate(this);
        super.onCreate(savedInstanceState);
        if (!MainApplication.getSharedPreferences().getBoolean("first_time_user", true)) {
            this.setActionBarExtraMenuButton(R.drawable.ic_baseline_settings_24, v -> {
                IntentHelper.startActivity(this, SettingsActivity.class);
                return true;
            }, R.string.pref_category_settings);
        }
        setContentView(R.layout.activity_main);
        this.setTitle(R.string.app_name);
        this.getWindow().setFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION, 0);
        setActionBarBackground(null);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            WindowManager.LayoutParams layoutParams = this.getWindow().getAttributes();
            layoutParams.layoutInDisplayCutoutMode = // Support cutout in Android 9
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            this.getWindow().setAttributes(layoutParams);
        }
        this.actionBarPadding = findViewById(R.id.action_bar_padding);
        this.actionBarBlur = findViewById(R.id.action_bar_blur);
        this.actionBarBackground = new ColorDrawable(Color.TRANSPARENT);
        this.progressIndicator = findViewById(R.id.progress_bar);
        this.swipeRefreshLayout = findViewById(R.id.swipe_refresh);
        this.swipeRefreshLayoutOrigStartOffset = this.swipeRefreshLayout.getProgressViewStartOffset();
        this.swipeRefreshLayoutOrigEndOffset = this.swipeRefreshLayout.getProgressViewEndOffset();
        this.swipeRefreshBlocker = Long.MAX_VALUE;
        this.moduleList = findViewById(R.id.module_list);
        this.searchCard = findViewById(R.id.search_card);
        this.searchView = findViewById(R.id.search_bar);
        this.moduleViewAdapter = new ModuleViewAdapter();
        this.moduleList.setAdapter(this.moduleViewAdapter);
        this.moduleList.setLayoutManager(new LinearLayoutManager(this));
        this.moduleList.setItemViewCacheSize(4); // Default is 2
        this.swipeRefreshLayout.setOnRefreshListener(this);
        this.actionBarBlur.setBackground(this.actionBarBackground);
        BlurUtils.setupBlur(this.actionBarBlur, this, R.id.blur_frame);
        this.updateBlurState();
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

        InstallerInitializer.tryGetMagiskPathAsync(new InstallerInitializer.Callback() {
            @Override
            public void onPathReceived(String path) {
                Log.i(TAG, "Got magisk path: " + path);
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
                Log.i(TAG, "Failed to get magisk path!");
                moduleViewListBuilder.addNotification(InstallerInitializer.getErrorNotification());
                this.commonNext();
            }

            public void commonNext() {
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "Common next");
                    moduleViewListBuilder.addNotification(NotificationType.DEBUG);
                }
                updateScreenInsets(); // Fix an edge case
                if (waitInitialSetupFinished()) {
                    if (BuildConfig.DEBUG) {
                        Log.d(TAG, "Initial setup not finished, waiting...");
                    }
                    return;
                }
                swipeRefreshBlocker = System.currentTimeMillis() + 5_000L;
                if (MainApplication.isShowcaseMode())
                    moduleViewListBuilder.addNotification(NotificationType.SHOWCASE_MODE);
                if (!Http.hasWebView()) // Check Http for WebView availability
                    moduleViewListBuilder.addNotification(NotificationType.NO_WEB_VIEW);
                moduleViewListBuilder.applyTo(moduleList, moduleViewAdapter);
                runOnUiThread(() -> {
                    progressIndicator.setIndeterminate(false);
                    progressIndicator.setMax(PRECISION);
                    // Fix insets not being accounted for correctly
                    updateScreenInsets(getResources().getConfiguration());
                });

                // On every preferences change, log the change if debug is enabled
                if (BuildConfig.DEBUG) {
                    Log.d("PrefsListener", "onCreate: Preferences: " + MainApplication.getSharedPreferences().getAll());
                    // Log all preferences changes
                    MainApplication.getSharedPreferences().registerOnSharedPreferenceChangeListener((prefs, key) -> Log.i("PrefsListener", "onSharedPreferenceChanged: " + key + " = " + prefs.getAll().get(key)));
                }
                Log.i(TAG, "Scanning for modules!");
                if (BuildConfig.DEBUG)
                    Log.i("NoodleDebug", "Initialize Update");
                final int max = ModuleManager.getINSTANCE().getUpdatableModuleCount();
                if (RepoManager.getINSTANCE().getCustomRepoManager().needUpdate()) {
                    Log.w(TAG, "Need update on create?");
                }
                if (BuildConfig.DEBUG)
                    Log.i("NoodleDebug", "Check Update Compat");
                AppUpdateManager.getAppUpdateManager().checkUpdateCompat();
                if (BuildConfig.DEBUG)
                    Log.i("NoodleDebug", "Check Update");
                RepoManager.getINSTANCE().update(value -> runOnUiThread(max == 0 ? () -> progressIndicator.setProgressCompat((int) (value * PRECISION), true) : () -> progressIndicator.setProgressCompat((int) (value * PRECISION * 0.75F), true)));
                NotificationType.NEED_CAPTCHA_ANDROIDACY.autoAdd(moduleViewListBuilder);
                // Add debug notification for debug builds
                if (!NotificationType.DEBUG.shouldRemove()) {
                    moduleViewListBuilder.addNotification(NotificationType.DEBUG);
                }
                if (!NotificationType.NO_INTERNET.shouldRemove()) {
                    moduleViewListBuilder.addNotification(NotificationType.NO_INTERNET);
                } else if (!NotificationType.REPO_UPDATE_FAILED.shouldRemove()) {
                    moduleViewListBuilder.addNotification(NotificationType.REPO_UPDATE_FAILED);
                } else {
                    // Compatibility data still needs to be updated
                    AppUpdateManager appUpdateManager = AppUpdateManager.getAppUpdateManager();
                    if (BuildConfig.DEBUG)
                        Log.i("NoodleDebug", "Check App Update");
                    if (BuildConfig.ENABLE_AUTO_UPDATER && appUpdateManager.checkUpdate(true))
                        moduleViewListBuilder.addNotification(NotificationType.UPDATE_AVAILABLE);
                    if (BuildConfig.DEBUG)
                        Log.i("NoodleDebug", "Check Json Update");
                    if (max != 0) {

                        int current = 0;
                        // noodleDebug.push("");
                        for (LocalModuleInfo localModuleInfo : ModuleManager.getINSTANCE().getModules().values()) {
                            if (localModuleInfo.updateJson != null) {
                                if (BuildConfig.DEBUG)
                                    Log.i("NoodleDebug", localModuleInfo.id);
                                try {
                                    localModuleInfo.checkModuleUpdate();
                                } catch (
                                        Exception e) {
                                    Log.e("MainActivity", "Failed to fetch update of: " + localModuleInfo.id, e);
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
                    setActionBarBackground(null);
                    updateScreenInsets(getResources().getConfiguration());
                });
                if (BuildConfig.DEBUG)
                    Log.i("NoodleDebug", "Apply");
                RepoManager.getINSTANCE().runAfterUpdate(moduleViewListBuilder::appendRemoteModules);

                moduleViewListBuilder.applyTo(moduleList, moduleViewAdapter);
                Log.i(TAG, "Finished app opening state!");
                // noodleDebug.unbind();
            }
        }, true);
        ExternalHelper.INSTANCE.refreshHelper(this);
        this.initMode = false;
    }

    private void cardIconifyUpdate() {
        boolean iconified = this.searchView.isIconified();
        int backgroundAttr = iconified ? MainApplication.isMonetEnabled() ? R.attr.colorSecondaryContainer : // Monet is special...
                R.attr.colorSecondary : R.attr.colorPrimarySurface;
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
        int statusBarHeight = getStatusBarHeight();
        int actionBarHeight = getActionBarHeight();
        int combinedBarsHeight = statusBarHeight + actionBarHeight;
        this.actionBarPadding.setMinHeight(combinedBarsHeight);
        this.swipeRefreshLayout.setProgressViewOffset(false, swipeRefreshLayoutOrigStartOffset + combinedBarsHeight, swipeRefreshLayoutOrigEndOffset + combinedBarsHeight);
        this.moduleViewListBuilder.setHeaderPx(Math.max(statusBarHeight, combinedBarsHeight - FoxDisplay.dpToPixel(4)));
        this.moduleViewListBuilder.setFooterPx(FoxDisplay.dpToPixel(4) + bottomInset + this.searchCard.getHeight());
        this.searchCard.setRadius(this.searchCard.getHeight() / 2F);
        this.moduleViewListBuilder.updateInsets();
        //this.actionBarBlur.invalidate();
        this.overScrollInsetTop = combinedBarsHeight;
        this.overScrollInsetBottom = bottomInset;
        Log.i(TAG, "( " + bottomInset + ", " + this.searchCard.getHeight() + ")");
    }

    private void updateBlurState() {
        boolean isLightMode = this.isLightTheme();
        int colorBackground;
        try {
            colorBackground = this.getColorCompat(android.R.attr.windowBackground);
        } catch (
                Resources.NotFoundException e) {
            colorBackground = this.getColorCompat(isLightMode ? R.color.white : R.color.black);
        }
        if (MainApplication.isBlurEnabled()) {
            this.actionBarBlur.setBlurEnabled(true);
            this.actionBarBackground.setColor(ColorUtils.setAlphaComponent(colorBackground, 0x02));
            this.actionBarBackground.setColor(Color.TRANSPARENT);
        } else {
            this.actionBarBlur.setBlurEnabled(false);
            this.actionBarBlur.setOverlayColor(Color.TRANSPARENT);
            this.actionBarBackground.setColor(colorBackground);
        }
    }

    @Override
    public void refreshUI() {
        super.refreshUI();
        if (this.initMode)
            return;
        this.initMode = true;
        Log.i(TAG, "Item Before");
        this.searchView.setQuery("", false);
        this.searchView.clearFocus();
        this.searchView.setIconified(true);
        this.cardIconifyUpdate();
        this.updateScreenInsets();
        this.updateBlurState();
        this.moduleViewListBuilder.setQuery(null);
        Log.i(TAG, "Item After");
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
                    } catch (
                            InterruptedException ignored) {
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
                moduleViewListBuilder.addNotification(InstallerInitializer.getErrorNotification());
                this.commonNext();
            }

            public void commonNext() {
                Log.i(TAG, "Common Before");
                if (MainApplication.isShowcaseMode())
                    moduleViewListBuilder.addNotification(NotificationType.SHOWCASE_MODE);
                NotificationType.NEED_CAPTCHA_ANDROIDACY.autoAdd(moduleViewListBuilder);
                if (!NotificationType.NO_INTERNET.shouldRemove())
                    moduleViewListBuilder.addNotification(NotificationType.NO_INTERNET);
                else if (AppUpdateManager.getAppUpdateManager().checkUpdate(false))
                    moduleViewListBuilder.addNotification(NotificationType.UPDATE_AVAILABLE);
                RepoManager.getINSTANCE().updateEnabledStates();
                if (RepoManager.getINSTANCE().getCustomRepoManager().needUpdate()) {
                    runOnUiThread(() -> {
                        progressIndicator.setIndeterminate(false);
                        progressIndicator.setMax(PRECISION);
                    });
                    if (BuildConfig.DEBUG)
                        Log.i("NoodleDebug", "Check Update");
                    RepoManager.getINSTANCE().update(value -> runOnUiThread(() -> progressIndicator.setProgressCompat((int) (value * PRECISION), true)));
                    runOnUiThread(() -> {
                        progressIndicator.setProgressCompat(PRECISION, true);
                        progressIndicator.setVisibility(View.GONE);
                    });
                }
                if (BuildConfig.DEBUG)
                    Log.i("NoodleDebug", "Apply");
                RepoManager.getINSTANCE().runAfterUpdate(moduleViewListBuilder::appendRemoteModules);
                Log.i(TAG, "Common Before applyTo");
                moduleViewListBuilder.applyTo(moduleList, moduleViewAdapter);
                Log.i(TAG, "Common After");
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
        if (BuildConfig.DEBUG)
            Log.i("NoodleDebug", "Refresh");
        this.progressIndicator.setVisibility(View.VISIBLE);
        this.progressIndicator.setProgressCompat(0, false);
        this.swipeRefreshBlocker = System.currentTimeMillis() + 5_000L;
        // this.swipeRefreshLayout.setRefreshing(true); ??
        new Thread(() -> {
            Http.cleanDnsCache(); // Allow DNS reload from network
            // noodleDebug.push("Check Update");
            final int max = ModuleManager.getINSTANCE().getUpdatableModuleCount();
            RepoManager.getINSTANCE().update(value -> runOnUiThread(max == 0 ? () -> progressIndicator.setProgressCompat((int) (value * PRECISION), true) : () -> progressIndicator.setProgressCompat((int) (value * PRECISION * 0.75F), true)));
            NotificationType.NEED_CAPTCHA_ANDROIDACY.autoAdd(moduleViewListBuilder);
            if (!NotificationType.NO_INTERNET.shouldRemove()) {
                moduleViewListBuilder.addNotification(NotificationType.NO_INTERNET);
            } else if (!NotificationType.REPO_UPDATE_FAILED.shouldRemove()) {
                moduleViewListBuilder.addNotification(NotificationType.REPO_UPDATE_FAILED);
            } else {
                // Compatibility data still needs to be updated
                AppUpdateManager appUpdateManager = AppUpdateManager.getAppUpdateManager();
                if (BuildConfig.DEBUG)
                    Log.i("NoodleDebug", "Check App Update");
                if (BuildConfig.ENABLE_AUTO_UPDATER && appUpdateManager.checkUpdate(true))
                    moduleViewListBuilder.addNotification(NotificationType.UPDATE_AVAILABLE);
                if (BuildConfig.DEBUG)
                    Log.i("NoodleDebug", "Check Json Update");
                if (max != 0) {
                    int current = 0;
                    for (LocalModuleInfo localModuleInfo : ModuleManager.getINSTANCE().getModules().values()) {
                        if (localModuleInfo.updateJson != null) {
                            if (BuildConfig.DEBUG)
                                Log.i("NoodleDebug", localModuleInfo.id);
                            try {
                                localModuleInfo.checkModuleUpdate();
                            } catch (
                                    Exception e) {
                                Log.e("MainActivity", "Failed to fetch update of: " + localModuleInfo.id, e);
                            }
                            current++;
                            final int currentTmp = current;
                            runOnUiThread(() -> progressIndicator.setProgressCompat((int) ((1F * currentTmp / max) * PRECISION * 0.25F + (PRECISION * 0.75F)), true));
                        }
                    }
                }
            }
            if (BuildConfig.DEBUG)
                Log.i("NoodleDebug", "Apply");
            runOnUiThread(() -> {
                this.progressIndicator.setVisibility(View.GONE);
                this.swipeRefreshLayout.setRefreshing(false);
            });
            NotificationType.NEED_CAPTCHA_ANDROIDACY.autoAdd(moduleViewListBuilder);
            RepoManager.getINSTANCE().updateEnabledStates();
            RepoManager.getINSTANCE().runAfterUpdate(moduleViewListBuilder::appendRemoteModules);
            this.moduleViewListBuilder.applyTo(moduleList, moduleViewAdapter);
            /*
             noodleDebug.unbind();
            */
        }, "Repo update thread").start();
    }

    @Override
    public boolean onQueryTextSubmit(final String query) {
        this.searchView.clearFocus();
        if (this.initMode)
            return false;
        if (this.moduleViewListBuilder.setQueryChange(query)) {
            new Thread(() -> this.moduleViewListBuilder.applyTo(moduleList, moduleViewAdapter), "Query update thread").start();
        }
        return true;
    }

    @Override
    public boolean onQueryTextChange(String query) {
        if (this.initMode)
            return false;
        if (this.moduleViewListBuilder.setQueryChange(query)) {
            new Thread(() -> this.moduleViewListBuilder.applyTo(moduleList, moduleViewAdapter), "Query update thread").start();
        }
        return false;
    }

    @Override
    public boolean onClose() {
        if (this.initMode)
            return false;
        if (this.moduleViewListBuilder.setQueryChange(null)) {
            new Thread(() -> this.moduleViewListBuilder.applyTo(moduleList, moduleViewAdapter), "Query update thread").start();
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

    @SuppressLint("RestrictedApi")
    private void ensurePermissions() {
        if (BuildConfig.DEBUG)
            Log.i("NoodleDebug", "Ensure Permissions");
        // First, check if user has said don't ask again by checking if pref_dont_ask_again_notification_permission is true
        if (!PreferenceManager.getDefaultSharedPreferences(this).getBoolean("pref_dont_ask_again_notification_permission", false)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                if (BuildConfig.DEBUG)
                    Log.i("NoodleDebug", "Request Notification Permission");
                if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.POST_NOTIFICATIONS)) {
                    // Show a dialog explaining why we need this permission, which is to show
                    // notifications for updates
                    runOnUiThread(() -> {
                        if (BuildConfig.DEBUG)
                            Log.i("NoodleDebug", "Show Notification Permission Dialog");
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
                        if (BuildConfig.DEBUG)
                            Log.i("NoodleDebug", "Show Notification Permission Dialog Done");
                    });
                } else {
                    // Request the permission
                    if (BuildConfig.DEBUG)
                        Log.i("NoodleDebug", "Request Notification Permission");
                    this.requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 0);
                    if (BuildConfig.DEBUG) {
                        // Log if granted via onRequestPermissionsResult
                        Log.i("NoodleDebug", "Request Notification Permission Done. Result: " + (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED));
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
                Log.i("NoodleDebug", "Notification Permission Already Granted or Don't Ask Again");
            doSetupNowRunning = false;
        }
    }

    // Method to show a setup box on first launch
    @SuppressLint({"InflateParams", "RestrictedApi", "UnspecifiedImmutableFlag", "ApplySharedPref"})
    private void checkShowInitialSetup() {
        if (BuildConfig.DEBUG)
            Log.i("SetupWizard", "Checking if we need to run setup");
        // Check if this is the first launch
        SharedPreferences prefs = MainApplication.getSharedPreferences();
        boolean firstLaunch = prefs.getBoolean("first_time_user", true);
        if (BuildConfig.DEBUG)
            Log.i("SetupWizard", "First launch: " + firstLaunch);
        if (firstLaunch) {
            doSetupNowRunning = true;
            // Launch setup wizard
            if (BuildConfig.DEBUG)
                Log.i("SetupWizard", "Launching setup wizard");
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
        if (BuildConfig.DEBUG)
            Log.i("SetupWizard", "waitInitialSetupFinished");
        if (doSetupNowRunning)
            updateScreenInsets(); // Fix an edge case
        try {
            // Wait for doSetupNow to finish
            while (doSetupNowRunning) {
                //noinspection BusyWait
                Thread.sleep(50);
            }
        } catch (
                InterruptedException e) {
            return true;
        }
        return doSetupRestarting;
    }
}