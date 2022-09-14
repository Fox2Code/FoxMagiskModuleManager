package com.fox2code.mmm;

import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.SearchView;
import androidx.cardview.widget.CardView;
import androidx.core.graphics.ColorUtils;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.fox2code.foxcompat.FoxActivity;
import com.fox2code.foxcompat.FoxDisplay;
import com.fox2code.mmm.background.BackgroundUpdateChecker;
import com.fox2code.mmm.installer.InstallerInitializer;
import com.fox2code.mmm.manager.LocalModuleInfo;
import com.fox2code.mmm.manager.ModuleManager;
import com.fox2code.mmm.module.ModuleViewAdapter;
import com.fox2code.mmm.module.ModuleViewListBuilder;
import com.fox2code.mmm.repo.RepoManager;
import com.fox2code.mmm.settings.SettingsActivity;
import com.fox2code.mmm.utils.ExternalHelper;
import com.fox2code.mmm.utils.Http;
import com.fox2code.mmm.utils.IntentHelper;
import com.fox2code.mmm.utils.NoodleDebug;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import eightbitlab.com.blurview.BlurView;
import eightbitlab.com.blurview.RenderScriptBlur;

public class MainActivity extends FoxActivity implements SwipeRefreshLayout.OnRefreshListener,
        SearchView.OnQueryTextListener, SearchView.OnCloseListener,
        OverScrollManager.OverScrollHelper {
    private static final String TAG = "MainActivity";
    private static final int PRECISION = 10000;
    public static boolean noodleDebugState = BuildConfig.DEBUG;
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
    private NoodleDebug noodleDebug;
    private boolean initMode;

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
        noodleDebugState = MainApplication.isDeveloper();
        BackgroundUpdateChecker.onMainActivityCreate(this);
        super.onCreate(savedInstanceState);
         this.setActionBarExtraMenuButton(R.drawable.ic_baseline_settings_24, v -> {
            IntentHelper.startActivity(this, SettingsActivity.class);
         return true;
         }, R.string.pref_category_settings);
        setContentView(R.layout.activity_main);
        this.setTitle(R.string.app_name);
        this.getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION,
                WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION);
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
        this.swipeRefreshLayoutOrigStartOffset =
                this.swipeRefreshLayout.getProgressViewStartOffset();
        this.swipeRefreshLayoutOrigEndOffset =
                this.swipeRefreshLayout.getProgressViewEndOffset();
        this.swipeRefreshBlocker = Long.MAX_VALUE;
        this.moduleList = findViewById(R.id.module_list);
        this.searchCard = findViewById(R.id.search_card);
        this.searchView = findViewById(R.id.search_bar);
        this.noodleDebug = new NoodleDebug(this, R.id.noodle_debug);
        this.moduleViewAdapter = new ModuleViewAdapter();
        this.moduleList.setAdapter(this.moduleViewAdapter);
        this.moduleList.setLayoutManager(new LinearLayoutManager(this));
        this.moduleList.setItemViewCacheSize(4); // Default is 2
        this.swipeRefreshLayout.setOnRefreshListener(this);
        this.actionBarBlur.setBackground(this.actionBarBackground);
        this.actionBarBlur.setupWith(findViewById(R.id.blur_frame))
                .setFrameClearDrawable(this.getWindow().getDecorView().getBackground())
                .setBlurAlgorithm(new RenderScriptBlur(this)).setBlurRadius(4F)
                .setBlurAutoUpdate(true).setHasFixedTransformationMatrix(true);
        this.updateBlurState();
        this.moduleList.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                if (newState != RecyclerView.SCROLL_STATE_IDLE)
                    MainActivity.this.searchView.clearFocus();
            }
        });
        this.searchCard.setRadius(this.searchCard.getHeight() / 2F);
        this.searchView.setMinimumHeight(FoxDisplay.dpToPixel(16));
        this.searchView.setImeOptions(EditorInfo.IME_ACTION_SEARCH |
                EditorInfo.IME_FLAG_NO_FULLSCREEN);
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
                if (InstallerInitializer.peekMagiskVersion() <
                        Constants.MAGISK_VER_CODE_INSTALL_COMMAND)
                    moduleViewListBuilder.addNotification(NotificationType.MAGISK_OUTDATED);
                if (!MainApplication.isShowcaseMode())
                    moduleViewListBuilder.addNotification(NotificationType.INSTALL_FROM_STORAGE);
                noodleDebug.setEnabled(noodleDebugState);
                noodleDebug.bind();
                ModuleManager.getINSTANCE().scan();
                ModuleManager.getINSTANCE().runAfterScan(
                        moduleViewListBuilder::appendInstalledModules);
                this.commonNext();
            }

            @Override
            public void onFailure(int error) {
                Log.i(TAG, "Failed to get magisk path!");
                noodleDebug.setEnabled(noodleDebugState);
                noodleDebug.bind();
                moduleViewListBuilder.addNotification(
                        InstallerInitializer.getErrorNotification());
                this.commonNext();
            }

            public void commonNext() {
                NoodleDebug noodleDebug = NoodleDebug.getNoodleDebug();
                swipeRefreshBlocker = System.currentTimeMillis() + 5_000L;
                updateScreenInsets(); // Fix an edge case
                if (MainApplication.isShowcaseMode())
                    moduleViewListBuilder.addNotification(NotificationType.SHOWCASE_MODE);
                if (!Http.hasWebView()) // Check Http for WebView availability
                    moduleViewListBuilder.addNotification(NotificationType.NO_WEB_VIEW);
                noodleDebug.push("Apply");
                moduleViewListBuilder.applyTo(moduleList, moduleViewAdapter);
                runOnUiThread(() -> {
                    progressIndicator.setIndeterminate(false);
                    progressIndicator.setMax(PRECISION);
                    // Fix insets not being accounted for correctly
                    updateScreenInsets(getResources().getConfiguration());
                });
                Log.i(TAG, "Scanning for modules!");
                noodleDebug.replace("Initialize Update");
                final int max = ModuleManager.getINSTANCE().getUpdatableModuleCount();
                if (RepoManager.getINSTANCE().getCustomRepoManager().needUpdate()) {
                    Log.w(TAG, "Need update on create?");
                }
                noodleDebug.replace("Check Update Compat");
                AppUpdateManager.getAppUpdateManager().checkUpdateCompat();
                noodleDebug.replace("Check Update");
                RepoManager.getINSTANCE().update(value -> runOnUiThread(max == 0 ? () ->
                        progressIndicator.setProgressCompat(
                                (int) (value * PRECISION), true) :() ->
                        progressIndicator.setProgressCompat(
                                (int) (value * PRECISION * 0.75F), true)));
                if (!NotificationType.NO_INTERNET.shouldRemove()) {
                    moduleViewListBuilder.addNotification(NotificationType.NO_INTERNET);
                } else {
                    // Compatibility data still needs to be updated
                    AppUpdateManager appUpdateManager = AppUpdateManager.getAppUpdateManager();
                    noodleDebug.replace("Check App Update");
                    if (BuildConfig.ENABLE_AUTO_UPDATER && appUpdateManager.checkUpdate(true))
                        moduleViewListBuilder.addNotification(NotificationType.UPDATE_AVAILABLE);
                    noodleDebug.replace("Check Json Update");
                    if (max != 0) {
                        int current = 0;
                        noodleDebug.push("");
                        for (LocalModuleInfo localModuleInfo :
                                ModuleManager.getINSTANCE().getModules().values()) {
                            if (localModuleInfo.updateJson != null) {
                                noodleDebug.replace(localModuleInfo.id);
                                try {
                                    localModuleInfo.checkModuleUpdate();
                                } catch (Exception e) {
                                    Log.e("MainActivity", "Failed to fetch update of: "
                                            + localModuleInfo.id, e);
                                }
                                current++;
                                final int currentTmp = current;
                                runOnUiThread(() -> progressIndicator.setProgressCompat(
                                        (int) ((1F * currentTmp / max) * PRECISION * 0.25F
                                                + (PRECISION * 0.75F)), true));
                            }
                        }
                        noodleDebug.pop();
                    }
                }
                runOnUiThread(() -> {
                    progressIndicator.setProgressCompat(PRECISION, true);
                    progressIndicator.setVisibility(View.GONE);
                    searchView.setEnabled(true);
                    setActionBarBackground(null);
                    updateScreenInsets(getResources().getConfiguration());
                });
                noodleDebug.replace("Apply");
                RepoManager.getINSTANCE().runAfterUpdate(
                        moduleViewListBuilder::appendRemoteModules);
                moduleViewListBuilder.applyTo(moduleList, moduleViewAdapter);
                noodleDebug.pop();
                Log.i(TAG, "Finished app opening state!");
                noodleDebug.unbind();
            }
        }, true);
        ExternalHelper.INSTANCE.refreshHelper(this);
        this.initMode = false;
    }

    private void cardIconifyUpdate() {
        boolean iconified = this.searchView.isIconified();
        int backgroundAttr = iconified ? MainApplication.isMonetEnabled() ?
                R.attr.colorSecondaryContainer : // Monet is special...
                R.attr.colorSecondary : R.attr.colorPrimarySurface;
        Resources.Theme theme = this.searchCard.getContext().getTheme();
        TypedValue value = new TypedValue();
        theme.resolveAttribute(backgroundAttr, value, true);
        this.searchCard.setCardBackgroundColor(value.data);
        this.searchCard.setAlpha(iconified ? 0.80F : 1F);
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
        this.swipeRefreshLayout.setProgressViewOffset(false,
                swipeRefreshLayoutOrigStartOffset + combinedBarsHeight,
                swipeRefreshLayoutOrigEndOffset + combinedBarsHeight);
        this.moduleViewListBuilder.setHeaderPx(Math.max(statusBarHeight,
                combinedBarsHeight - FoxDisplay.dpToPixel(4)));
        this.moduleViewListBuilder.setFooterPx(FoxDisplay.dpToPixel(4) +
                bottomInset + this.searchCard.getHeight());
        this.searchCard.setRadius(this.searchCard.getHeight() / 2F);
        this.moduleViewListBuilder.updateInsets();
        //this.actionBarBlur.invalidate();
        this.overScrollInsetTop = combinedBarsHeight;
        this.overScrollInsetBottom = bottomInset;
        Log.d(TAG, "( " + bottomInset + ", " +
                this.searchCard.getHeight() + ")");
    }

    private void updateBlurState() {
        boolean isLightMode = this.isLightTheme();
        int colorBackground;
        try {
            colorBackground = this.getColorCompat(
                    android.R.attr.windowBackground);
        } catch (Resources.NotFoundException e) {
            colorBackground = this.getColorCompat(isLightMode ?
                    R.color.white : R.color.black);
        }
        if (MainApplication.isBlurEnabled()) {
            this.actionBarBlur.setBlurEnabled(true);
            this.actionBarBackground.setColor(ColorUtils
                    .setAlphaComponent(colorBackground, 0x02));
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
        if (this.initMode) return;
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
        noodleDebugState = MainApplication.isDeveloper();
        this.moduleViewListBuilder.refreshNotificationsUI(this.moduleViewAdapter);
        InstallerInitializer.tryGetMagiskPathAsync(new InstallerInitializer.Callback() {
            @Override
            public void onPathReceived(String path) {
                if (InstallerInitializer.peekMagiskVersion() <
                        Constants.MAGISK_VER_CODE_INSTALL_COMMAND)
                    moduleViewListBuilder.addNotification(NotificationType.MAGISK_OUTDATED);
                if (!MainApplication.isShowcaseMode())
                    moduleViewListBuilder.addNotification(NotificationType.INSTALL_FROM_STORAGE);
                noodleDebug.setEnabled(noodleDebugState);
                noodleDebug.bind();
                ModuleManager.getINSTANCE().scan();
                ModuleManager.getINSTANCE().runAfterScan(
                        moduleViewListBuilder::appendInstalledModules);
                this.commonNext();
            }

            @Override
            public void onFailure(int error) {
                moduleViewListBuilder.addNotification(
                        InstallerInitializer.getErrorNotification());
                noodleDebug.setEnabled(noodleDebugState);
                noodleDebug.bind();
                this.commonNext();
            }

            public void commonNext() {
                Log.i(TAG, "Common Before");
                NoodleDebug noodleDebug = NoodleDebug.getNoodleDebug();
                if (MainApplication.isShowcaseMode())
                    moduleViewListBuilder.addNotification(NotificationType.SHOWCASE_MODE);
                if (!NotificationType.NO_INTERNET.shouldRemove())
                    moduleViewListBuilder.addNotification(NotificationType.NO_INTERNET);
                else if (AppUpdateManager.getAppUpdateManager().checkUpdate(false))
                    moduleViewListBuilder.addNotification(NotificationType.UPDATE_AVAILABLE);
                RepoManager.getINSTANCE().updateEnabledStates();
                noodleDebug.push("");
                if (RepoManager.getINSTANCE().getCustomRepoManager().needUpdate()) {
                    runOnUiThread(() -> {
                        progressIndicator.setIndeterminate(false);
                        progressIndicator.setMax(PRECISION);
                    });
                    noodleDebug.replace("Check Update");
                    RepoManager.getINSTANCE().update(value -> runOnUiThread(() ->
                            progressIndicator.setProgressCompat(
                                    (int) (value * PRECISION), true)));
                    runOnUiThread(() -> {
                        progressIndicator.setProgressCompat(PRECISION, true);
                        progressIndicator.setVisibility(View.GONE);
                    });
                }
                noodleDebug.replace("Apply");
                RepoManager.getINSTANCE().runAfterUpdate(
                        moduleViewListBuilder::appendRemoteModules);
                Log.i(TAG, "Common Before applyTo");
                moduleViewListBuilder.applyTo(moduleList, moduleViewAdapter);
                noodleDebug.pop();
                Log.i(TAG, "Common After");
                noodleDebug.unbind();
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
        if (this.swipeRefreshBlocker > System.currentTimeMillis() ||
                this.initMode || this.progressIndicator == null ||
                this.progressIndicator.getVisibility() == View.VISIBLE) {
            this.swipeRefreshLayout.setRefreshing(false);
            return; // Do not double scan
        }
        this.progressIndicator.setVisibility(View.VISIBLE);
        this.progressIndicator.setProgressCompat(0, false);
        this.swipeRefreshBlocker = System.currentTimeMillis() + 5_000L;
        // this.swipeRefreshLayout.setRefreshing(true); ??
        new Thread(() -> {
            noodleDebug.setEnabled(noodleDebugState);
            NoodleDebug noodleDebug = this.noodleDebug.bind();
            Http.cleanDnsCache(); // Allow DNS reload from network
            noodleDebug.push("Check Update");
            final int max = ModuleManager.getINSTANCE().getUpdatableModuleCount();
            RepoManager.getINSTANCE().update(value -> runOnUiThread(max == 0 ? () ->
                    progressIndicator.setProgressCompat(
                            (int) (value * PRECISION), true) :() ->
                    progressIndicator.setProgressCompat(
                            (int) (value * PRECISION * 0.75F), true)));
            if (!NotificationType.NO_INTERNET.shouldRemove()) {
                moduleViewListBuilder.addNotification(NotificationType.NO_INTERNET);
            } else {
                // Compatibility data still needs to be updated
                AppUpdateManager appUpdateManager = AppUpdateManager.getAppUpdateManager();
                noodleDebug.replace("Check App Update");
                if (BuildConfig.ENABLE_AUTO_UPDATER && appUpdateManager.checkUpdate(true))
                    moduleViewListBuilder.addNotification(NotificationType.UPDATE_AVAILABLE);
                noodleDebug.replace("Check Json Update");
                if (max != 0) {
                    int current = 0;
                    noodleDebug.push("");
                    for (LocalModuleInfo localModuleInfo :
                            ModuleManager.getINSTANCE().getModules().values()) {
                        if (localModuleInfo.updateJson != null) {
                            noodleDebug.replace(localModuleInfo.id);
                            try {
                                localModuleInfo.checkModuleUpdate();
                            } catch (Exception e) {
                                Log.e("MainActivity", "Failed to fetch update of: "
                                        + localModuleInfo.id, e);
                            }
                            current++;
                            final int currentTmp = current;
                            runOnUiThread(() -> progressIndicator.setProgressCompat(
                                    (int) ((1F * currentTmp / max) * PRECISION * 0.25F
                                            + (PRECISION * 0.75F)), true));
                        }
                    }
                    noodleDebug.pop();
                }
            }
            noodleDebug.replace("Apply");
            runOnUiThread(() -> {
                this.progressIndicator.setVisibility(View.GONE);
                this.swipeRefreshLayout.setRefreshing(false);
            });
            if (!NotificationType.NO_INTERNET.shouldRemove()) {
                this.moduleViewListBuilder.addNotification(NotificationType.NO_INTERNET);
            }
            RepoManager.getINSTANCE().updateEnabledStates();
            RepoManager.getINSTANCE().runAfterUpdate(
                    moduleViewListBuilder::appendRemoteModules);
            this.moduleViewListBuilder.applyTo(moduleList, moduleViewAdapter);
            noodleDebug.pop();
            noodleDebug.unbind();
        },"Repo update thread").start();
    }

    @Override
    public boolean onQueryTextSubmit(final String query) {
        this.searchView.clearFocus();
        if (this.initMode) return false;
        if (this.moduleViewListBuilder.setQueryChange(query)) {
            new Thread(() -> this.moduleViewListBuilder.applyTo(
                    moduleList, moduleViewAdapter), "Query update thread").start();
        }
        return true;
    }

    @Override
    public boolean onQueryTextChange(String query) {
        if (this.initMode) return false;
        if (this.moduleViewListBuilder.setQueryChange(query)) {
            new Thread(() -> this.moduleViewListBuilder.applyTo(
                    moduleList, moduleViewAdapter), "Query update thread").start();
        }
        return false;
    }

    @Override
    public boolean onClose() {
        if (this.initMode) return false;
        if (this.moduleViewListBuilder.setQueryChange(null)) {
            new Thread(() -> this.moduleViewListBuilder.applyTo(
                    moduleList, moduleViewAdapter), "Query update thread").start();
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
}