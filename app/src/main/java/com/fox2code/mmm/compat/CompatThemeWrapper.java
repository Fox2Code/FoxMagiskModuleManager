package com.fox2code.mmm.compat;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Color;
import android.os.Build;
import android.util.TypedValue;

import androidx.annotation.AttrRes;
import androidx.annotation.ColorInt;
import androidx.annotation.ColorRes;
import androidx.annotation.StyleRes;
import androidx.appcompat.view.ContextThemeWrapper;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ComplexColorCompat;
import androidx.core.graphics.ColorUtils;

import com.fox2code.mmm.R;

/**
 * I will probably outsource this to a separate library later
 */
public class CompatThemeWrapper extends ContextThemeWrapper {
    private final CompatConfigHelper compatConfigHelper = new CompatConfigHelper(this);
    private boolean canReload;
    // CompatConfigHelper
    private boolean forceEnglish;
    private Boolean nightModeOverride;

    public CompatThemeWrapper(Context base, @StyleRes int themeResId) {
        super(base, themeResId);
        this.canReload = true;
        this.checkResourcesOverrides(
                this.forceEnglish, this.nightModeOverride);
    }

    @Override
    protected void onApplyThemeResource(Resources.Theme theme, int resid, boolean first) {
        boolean couldReload = this.canReload;
        if (couldReload) this.canReload = false;
        this.compatConfigHelper.checkResourcesOverrides(theme,
                this.forceEnglish, this.nightModeOverride);
        super.onApplyThemeResource(theme, resid, first);
        if (couldReload) this.canReload = true;
        // In case value change while reload, should have no effect
        this.compatConfigHelper.checkResourcesOverrides(theme,
                this.forceEnglish, this.nightModeOverride);
    }

    public void setForceEnglish(boolean forceEnglish) {
        if (this.forceEnglish == forceEnglish) return;
        this.forceEnglish = forceEnglish;
        this.checkResourcesOverrides(forceEnglish, this.nightModeOverride);
    }

    public void setNightModeOverride(Boolean nightModeOverride) {
        if (this.nightModeOverride == nightModeOverride) return;
        this.nightModeOverride = nightModeOverride;
        this.checkResourcesOverrides(this.forceEnglish, nightModeOverride);
    }

    private void checkResourcesOverrides(boolean forceEnglish,Boolean nightModeOverride) {
        if (!this.canReload) return; // Do not reload during theme reload
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
}
