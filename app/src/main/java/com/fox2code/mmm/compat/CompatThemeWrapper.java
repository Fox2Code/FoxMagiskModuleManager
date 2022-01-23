package com.fox2code.mmm.compat;

import android.content.Context;
import android.content.res.Resources;

import androidx.annotation.StyleRes;
import androidx.appcompat.view.ContextThemeWrapper;

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
}
