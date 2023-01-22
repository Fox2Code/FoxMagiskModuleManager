package com.fox2code.mmm.utils;

import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityOptionsCompat;
import androidx.core.util.Supplier;

import com.fox2code.mmm.Constants;
import com.topjohnwu.superuser.internal.UiThreadHandler;

import java.util.List;

import timber.log.Timber;

public final class ExternalHelper {
    public static final ExternalHelper INSTANCE = new ExternalHelper();
    private static final boolean TEST_MODE = false;
    private static final String FOX_MMM_OPEN_EXTERNAL = "com.fox2code.mmm.utils.intent.action.OPEN_EXTERNAL";
    private static final String FOX_MMM_EXTRA_REPO_ID = "extra_repo_id";
    private ComponentName fallback;
    private CharSequence label;
    private boolean multi;

    private ExternalHelper() {
    }

    public void refreshHelper(Context context) {
        Intent intent = new Intent(FOX_MMM_OPEN_EXTERNAL, Uri.parse("https://fox2code.com/module.zip"));
        List<ResolveInfo> resolveInfos = context.getPackageManager().queryIntentActivities(intent, PackageManager.GET_RESOLVED_FILTER);
        if (resolveInfos == null || resolveInfos.isEmpty()) {
            Timber.i("No external provider installed!");
            label = TEST_MODE ? "External" : null;
            multi = TEST_MODE;
            fallback = null;
        } else {
            ResolveInfo resolveInfo = resolveInfos.get(0);
            Timber.i("Found external provider: %s", resolveInfo.activityInfo.packageName);
            fallback = new ComponentName(resolveInfo.activityInfo.packageName, resolveInfo.activityInfo.name);
            label = resolveInfo.loadLabel(context.getPackageManager());
            multi = resolveInfos.size() >= 2;
        }
    }

    public boolean openExternal(Context context, Uri uri, String repoId) {
        if (label == null)
            return false;
        Bundle param = ActivityOptionsCompat.makeCustomAnimation(context, android.R.anim.fade_in, android.R.anim.fade_out).toBundle();
        Intent intent = new Intent(FOX_MMM_OPEN_EXTERNAL, uri);
        intent.setFlags(IntentHelper.FLAG_GRANT_URI_PERMISSION);
        intent.putExtra(FOX_MMM_EXTRA_REPO_ID, repoId);
        if (multi) {
            intent = Intent.createChooser(intent, label);
        } else {
            intent.putExtra(Constants.EXTRA_FADE_OUT, true);
        }
        try {
            if (multi) {
                context.startActivity(intent);
            } else {
                context.startActivity(intent, param);
            }
            return true;
        } catch (
                ActivityNotFoundException e) {
            Timber.e(e);
        }
        if (fallback != null) {
            if (multi) {
                intent = new Intent(FOX_MMM_OPEN_EXTERNAL, uri);
                intent.putExtra(FOX_MMM_EXTRA_REPO_ID, repoId);
                intent.putExtra(Constants.EXTRA_FADE_OUT, true);
            }
            intent.setComponent(fallback);
            try {
                context.startActivity(intent, param);
                return true;
            } catch (
                    ActivityNotFoundException e) {
                Timber.e(e);
            }
        }
        return false;
    }

    public void injectButton(AlertDialog.Builder builder, Supplier<Uri> uriSupplier, String repoId) {
        if (label == null)
            return;
        builder.setNeutralButton(label, (dialog, button) -> {
            Context context = ((Dialog) dialog).getContext();
            new Thread("Async downloader") {
                @Override
                public void run() {
                    final Uri uri = uriSupplier.get();
                    if (uri == null)
                        return;
                    UiThreadHandler.run(() -> {
                        if (!openExternal(context, uri, repoId)) {
                            Toast.makeText(context, "Failed to launch external activity", Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }.start();
        });
    }
}
