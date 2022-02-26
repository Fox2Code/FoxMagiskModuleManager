package com.fox2code.mmm.compat;

import android.app.Application;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;
import android.util.Log;
import android.util.TypedValue;

import androidx.annotation.AttrRes;
import androidx.annotation.CallSuper;
import androidx.annotation.ColorInt;
import androidx.annotation.ColorRes;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;

import com.fox2code.mmm.R;

import java.lang.ref.WeakReference;

/**
 * I will probably outsource this to a separate library later
 */
public class CompatApplication extends Application implements CompatActivity.ApplicationCallbacks {
    private static final String TAG = "CompatApplication";
    private final CompatConfigHelper compatConfigHelper = new CompatConfigHelper(this);
    private WeakReference<CompatActivity> lastCompatActivity;
    // CompatConfigHelper
    private boolean forceEnglish;
    private Boolean nightModeOverride;
    private boolean propagateOverrides;

    public CompatApplication() {}

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        this.compatConfigHelper.checkResourcesOverrides(newConfig,
                this.forceEnglish, this.nightModeOverride);
        super.onConfigurationChanged(newConfig);
    }

    public void setForceEnglish(boolean forceEnglish) {
        if (this.forceEnglish != forceEnglish) {
            this.forceEnglish = forceEnglish;
            this.checkResourcesOverrides(forceEnglish, this.nightModeOverride);
        }
        // Propagate even if local value didn't changed
        if (this.propagateOverrides && this.lastCompatActivity != null) {
            CompatActivity compatActivity = this.lastCompatActivity.get();
            if (compatActivity != null)
                compatActivity.setForceEnglish(forceEnglish);
        }
    }

    public void setNightModeOverride(Boolean nightModeOverride) {
        if (this.nightModeOverride != nightModeOverride) {
            this.nightModeOverride = nightModeOverride;
            this.checkResourcesOverrides(this.forceEnglish, nightModeOverride);
        }
        // Propagate even if local value didn't changed
        if (this.propagateOverrides && this.lastCompatActivity != null) {
            CompatActivity compatActivity = this.lastCompatActivity.get();
            if (compatActivity != null)
                compatActivity.setNightModeOverride(nightModeOverride);
        }
    }

    public boolean isPropagateOverrides() {
        return propagateOverrides;
    }

    public void setPropagateOverrides(boolean propagateOverrides) {
        this.propagateOverrides = propagateOverrides;
        WeakReference<CompatActivity> lastCompatActivity = this.lastCompatActivity;
        if (lastCompatActivity != null) {
            Log.d(TAG, "setPropagateOverrides(" + // This should be avoided
                    propagateOverrides + ") called after first activity created!");
            CompatActivity compatActivity = lastCompatActivity.get();
            if (compatActivity != null && propagateOverrides) {
                this.propagateOverrides(compatActivity);
            }
        }
    }

    private void checkResourcesOverrides(boolean forceEnglish, Boolean nightModeOverride) {
        this.compatConfigHelper.checkResourcesOverrides(forceEnglish, nightModeOverride);
    }

    public boolean isLightTheme() {
        Resources.Theme theme = this.getTheme();
        TypedValue typedValue = new TypedValue();
        theme.resolveAttribute(R.attr.isLightTheme, typedValue, true);
        if (typedValue.type == TypedValue.TYPE_INT_BOOLEAN) {
            return typedValue.data == 1;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            theme.resolveAttribute(android.R.attr.isLightTheme, typedValue, true);
            if (typedValue.type == TypedValue.TYPE_INT_BOOLEAN) {
                return typedValue.data == 1;
            }
        }
        theme.resolveAttribute(android.R.attr.background, typedValue, true);
        if (typedValue.type >= TypedValue.TYPE_FIRST_COLOR_INT &&
                typedValue.type <= TypedValue.TYPE_LAST_COLOR_INT) {
            return ColorUtils.calculateLuminance(typedValue.data) > 0.7D;
        }
        throw new IllegalStateException("Theme is not a valid theme!");
    }

    @ColorInt
    public final int getColorCompat(@ColorRes @AttrRes int color) {
        TypedValue typedValue = new TypedValue();
        this.getTheme().resolveAttribute(color, typedValue, true);
        if (typedValue.type >= TypedValue.TYPE_FIRST_COLOR_INT &&
                typedValue.type <= TypedValue.TYPE_LAST_COLOR_INT) {
            return typedValue.data;
        }
        return ContextCompat.getColor(this, color);
    }

    @Override
    @CallSuper
    public void onCreateCompatActivity(CompatActivity compatActivity) {
        this.lastCompatActivity = compatActivity.selfReference;
        if (this.propagateOverrides) {
            this.propagateOverrides(compatActivity);
        }
    }

    @Override
    @CallSuper
    public void onRefreshUI(CompatActivity compatActivity) {
        this.lastCompatActivity = compatActivity.selfReference;
        if (this.propagateOverrides) {
            this.propagateOverrides(compatActivity);
        }
    }

    private void propagateOverrides(CompatActivity compatActivity) {
        compatActivity.propagateResourcesOverride(
                this.forceEnglish, this.nightModeOverride);
    }
}
