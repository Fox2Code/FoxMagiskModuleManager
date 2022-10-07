package com.fox2code.mmm.utils;

import android.app.Activity;
import android.os.Build;
import android.view.ViewGroup;

import androidx.annotation.IdRes;

import eightbitlab.com.blurview.BlurView;
import eightbitlab.com.blurview.RenderEffectBlur;
import eightbitlab.com.blurview.RenderScriptBlur;

public class BlurUtils {
    public static void setupBlur(BlurView blurView, Activity activity, @IdRes int viewId) {
        setupBlur(blurView, activity, activity.findViewById(viewId));
    }

    @SuppressWarnings("deprecation")
    public static void setupBlur(BlurView blurView, Activity activity, ViewGroup rootView) {
        blurView.setupWith(rootView, Build.VERSION.SDK_INT < Build.VERSION_CODES.S ?
                        new RenderScriptBlur(blurView.getContext()) : new RenderEffectBlur())
                .setFrameClearDrawable(activity.getWindow().getDecorView().getBackground())
                .setBlurRadius(4F).setBlurAutoUpdate(true);
    }
}
