package com.fox2code.mmm.compat;

import android.app.Application;
import android.content.res.Resources;
import android.os.Build;
import android.util.TypedValue;

import androidx.annotation.AttrRes;
import androidx.annotation.CallSuper;
import androidx.annotation.ColorInt;
import androidx.annotation.ColorRes;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;

import com.fox2code.mmm.R;

import java.lang.ref.WeakReference;

/**
 * I will probably outsource this to a separate library later
 */
public class CompatApplication extends Application implements CompatActivity.ApplicationCallbacks {
    private static final String TAG = "CompatApplication";
    private WeakReference<CompatActivity> lastCompatActivity;

    public CompatApplication() {}

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
    }

    @Override
    @CallSuper
    public void onRefreshUI(CompatActivity compatActivity) {
        this.lastCompatActivity = compatActivity.selfReference;
    }

    @Nullable
    public CompatActivity getLastCompatActivity() {
        return this.lastCompatActivity == null ?
                null : this.lastCompatActivity.get();
    }
}
