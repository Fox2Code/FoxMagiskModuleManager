package com.fox2code.mmm;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.SearchView;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import android.content.res.Resources;
import android.os.Bundle;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.fox2code.mmm.compat.CompatActivity;
import com.fox2code.mmm.installer.InstallerInitializer;
import com.fox2code.mmm.manager.ModuleManager;
import com.fox2code.mmm.repo.RepoManager;
import com.fox2code.mmm.settings.SettingsActivity;
import com.fox2code.mmm.utils.Http;
import com.fox2code.mmm.utils.IntentHelper;
import com.google.android.material.progressindicator.LinearProgressIndicator;

public class MainActivity extends CompatActivity implements SwipeRefreshLayout.OnRefreshListener,
        SearchView.OnQueryTextListener, SearchView.OnCloseListener {
    private static final String TAG = "MainActivity";
    private static final int PRECISION = 10000;
    public final ModuleViewListBuilder moduleViewListBuilder;
    public LinearProgressIndicator progressIndicator;
    private ModuleViewAdapter moduleViewAdapter;
    private SwipeRefreshLayout swipeRefreshLayout;
    private RecyclerView moduleList;
    private CardView searchCard;
    private SearchView searchView;
    private boolean initMode;

    public MainActivity() {
        this.moduleViewListBuilder = new ModuleViewListBuilder(this);
        this.moduleViewListBuilder.addNotification(NotificationType.INSTALL_FROM_STORAGE);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        this.initMode = true;
        super.onCreate(savedInstanceState);
        this.setActionBarExtraMenuButton(R.drawable.ic_baseline_settings_24, v -> {
            IntentHelper.startActivity(this, SettingsActivity.class);
            return true;
        });
        setContentView(R.layout.activity_main);
        this.setTitle(R.string.app_name);
        this.progressIndicator = findViewById(R.id.progress_bar);
        this.swipeRefreshLayout = findViewById(R.id.swipe_refresh);
        this.moduleList = findViewById(R.id.module_list);
        this.searchCard = findViewById(R.id.search_card);
        this.searchView = findViewById(R.id.search_bar);
        this.moduleViewAdapter = new ModuleViewAdapter();
        this.moduleList.setAdapter(this.moduleViewAdapter);
        this.moduleList.setLayoutManager(new LinearLayoutManager(this));
        this.moduleList.setItemViewCacheSize(4); // Default is 2
        this.swipeRefreshLayout.setOnRefreshListener(this);
        this.moduleList.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                if (newState != RecyclerView.SCROLL_STATE_IDLE)
                    MainActivity.this.searchView.clearFocus();
            }
        });
        this.searchView.setImeOptions(EditorInfo.IME_ACTION_SEARCH |
                EditorInfo.IME_FLAG_NO_FULLSCREEN | EditorInfo.IME_FLAG_FORCE_ASCII);
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
        InstallerInitializer.tryGetMagiskPathAsync(new InstallerInitializer.Callback() {
            @Override
            public void onPathReceived(String path) {
                Log.i(TAG, "Got magisk path: " + path);
                if (InstallerInitializer.peekMagiskVersion() <
                        Constants.MAGISK_VER_CODE_INSTALL_COMMAND)
                    moduleViewListBuilder.addNotification(NotificationType.MAGISK_OUTDATED);
                if (!MainApplication.isShowcaseMode())
                    moduleViewListBuilder.addNotification(NotificationType.INSTALL_FROM_STORAGE);
                ModuleManager.getINSTANCE().scan();
                moduleViewListBuilder.appendInstalledModules();
                this.commonNext();
            }

            @Override
            public void onFailure(int error) {
                Log.i(TAG, "Failed to get magisk path!");
                moduleViewListBuilder.addNotification(NotificationType.NO_ROOT);
                this.commonNext();
            }

            public void commonNext() {
                if (MainApplication.isShowcaseMode())
                    moduleViewListBuilder.addNotification(NotificationType.SHOWCASE_MODE);
                moduleViewListBuilder.applyTo(moduleList, moduleViewAdapter);
                runOnUiThread(() -> {
                    progressIndicator.setIndeterminate(false);
                    progressIndicator.setMax(PRECISION);
                });
                Log.i(TAG, "Scanning for modules!");
                RepoManager.getINSTANCE().update(value -> runOnUiThread(() ->
                        progressIndicator.setProgressCompat((int) (value * PRECISION), true)));
                runOnUiThread(() -> {
                    progressIndicator.setVisibility(View.GONE);
                    searchView.setEnabled(true);
                });
                if (!RepoManager.getINSTANCE().hasConnectivity())
                    moduleViewListBuilder.addNotification(NotificationType.NO_INTERNET);
                else if (AppUpdateManager.getAppUpdateManager().checkUpdate(true))
                    moduleViewListBuilder.addNotification(NotificationType.UPDATE_AVAILABLE);
                moduleViewListBuilder.appendRemoteModules();
                moduleViewListBuilder.applyTo(moduleList, moduleViewAdapter);
                Log.i(TAG, "Finished app opening state!");
            }
        }, true);
        this.initMode = false;
    }

    private void cardIconifyUpdate() {
        boolean iconified = this.searchView.isIconified();
        int backgroundAttr = iconified ?
                R.attr.colorSecondary : R.attr.colorPrimarySurface;
        Resources.Theme theme = this.searchCard.getContext().getTheme();
        TypedValue value = new TypedValue();
        theme.resolveAttribute(backgroundAttr, value, true);
        this.searchCard.setCardBackgroundColor(value.data);
        this.searchCard.setAlpha(iconified ? 0.70F : 1F);
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
        this.moduleViewListBuilder.setQuery(null);
        Log.i(TAG, "Item After");
        InstallerInitializer.tryGetMagiskPathAsync(new InstallerInitializer.Callback() {
            @Override
            public void onPathReceived(String path) {
                if (InstallerInitializer.peekMagiskVersion() <
                        Constants.MAGISK_VER_CODE_INSTALL_COMMAND)
                    moduleViewListBuilder.addNotification(NotificationType.MAGISK_OUTDATED);
                if (!MainApplication.isShowcaseMode())
                    moduleViewListBuilder.addNotification(NotificationType.INSTALL_FROM_STORAGE);
                ModuleManager.getINSTANCE().scan();
                moduleViewListBuilder.appendInstalledModules();
                this.commonNext();
            }

            @Override
            public void onFailure(int error) {
                moduleViewListBuilder.addNotification(NotificationType.NO_ROOT);
                this.commonNext();
            }

            public void commonNext() {
                Log.i(TAG, "Common Before");
                if (MainApplication.isShowcaseMode())
                    moduleViewListBuilder.addNotification(NotificationType.SHOWCASE_MODE);
                if (!RepoManager.getINSTANCE().hasConnectivity())
                    moduleViewListBuilder.addNotification(NotificationType.NO_INTERNET);
                else if (AppUpdateManager.getAppUpdateManager().checkUpdate(false))
                    moduleViewListBuilder.addNotification(NotificationType.UPDATE_AVAILABLE);
                moduleViewListBuilder.appendRemoteModules();
                Log.i(TAG, "Common Before applyTo");
                moduleViewListBuilder.applyTo(moduleList, moduleViewAdapter);
                Log.i(TAG, "Common After");
            }
        });
        this.initMode = false;
    }

    @Override
    public void onRefresh() {
        if (this.initMode || this.progressIndicator == null ||
                this.progressIndicator.getVisibility() == View.VISIBLE) {
            return; // Do not double scan
        }
        this.progressIndicator.setVisibility(View.VISIBLE);
        this.progressIndicator.setProgressCompat(0, false);
        // this.swipeRefreshLayout.setRefreshing(true); ??
        new Thread(() -> {
            Http.cleanDnsCache(); // Allow DNS reload from network
            RepoManager.getINSTANCE().update(value -> runOnUiThread(() ->
                    this.progressIndicator.setProgressCompat(
                            (int) (value * PRECISION), true)));
            if (!RepoManager.getINSTANCE().hasConnectivity())
                moduleViewListBuilder.addNotification(NotificationType.NO_INTERNET);
            else if (AppUpdateManager.getAppUpdateManager().checkUpdate(true))
                moduleViewListBuilder.addNotification(NotificationType.UPDATE_AVAILABLE);
            runOnUiThread(() -> {
                this.progressIndicator.setVisibility(View.GONE);
                this.swipeRefreshLayout.setRefreshing(false);
            });
            if (!RepoManager.getINSTANCE().hasConnectivity()) {
                this.moduleViewListBuilder.addNotification(NotificationType.NO_INTERNET);
            }
            this.moduleViewListBuilder.appendRemoteModules();
            this.moduleViewListBuilder.applyTo(moduleList, moduleViewAdapter);
        },"Repo update thread").start();
    }

    @Override
    public boolean onQueryTextSubmit(final String query) {
        this.searchView.clearFocus();
        if (this.initMode) return false;
        if (this.moduleViewListBuilder.setQueryChange(query)) {
            new Thread(() -> {
                this.moduleViewListBuilder.applyTo(moduleList, moduleViewAdapter);
            }, "Query update thread").start();
        }
        return true;
    }

    @Override
    public boolean onQueryTextChange(String query) {
        if (this.initMode) return false;
        if (this.moduleViewListBuilder.setQueryChange(query)) {
            new Thread(() -> {
                this.moduleViewListBuilder.applyTo(moduleList, moduleViewAdapter);
            }, "Query update thread").start();
        }
        return false;
    }

    @Override
    public boolean onClose() {
        if (this.initMode) return false;
        if (this.moduleViewListBuilder.setQueryChange(null)) {
            new Thread(() -> {
                this.moduleViewListBuilder.applyTo(moduleList, moduleViewAdapter);
            }, "Query update thread").start();
        }
        return false;
    }
}