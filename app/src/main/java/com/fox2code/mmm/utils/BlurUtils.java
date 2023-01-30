package com.fox2code.mmm.utils;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Build;
import android.view.ViewGroup;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;

import eightbitlab.com.blurview.BlurAlgorithm;
import eightbitlab.com.blurview.BlurView;
import eightbitlab.com.blurview.RenderEffectBlur;
import eightbitlab.com.blurview.RenderScriptBlur;

public enum BlurUtils {
    ;

    public static void setupBlur(BlurView blurView, Activity activity, @IdRes int viewId) {
        setupBlur(blurView, activity, activity.findViewById(viewId));
    }

    @SuppressWarnings("deprecation")
    public static void setupBlur(BlurView blurView, Activity activity, ViewGroup rootView) {
        blurView.setupWith(rootView, new BlurAlgorithmWrapper(
                Build.VERSION.SDK_INT < Build.VERSION_CODES.S ?
                        new RenderScriptBlur(blurView.getContext()) : new RenderEffectBlur()))
                .setFrameClearDrawable(activity.getWindow().getDecorView().getBackground())
                .setBlurRadius(4F).setBlurAutoUpdate(true);
    }

    // Allow to have fancy blur, use more performance.
    private static final class BlurAlgorithmWrapper implements BlurAlgorithm {
        private final BlurAlgorithm algorithm;

        private BlurAlgorithmWrapper(BlurAlgorithm algorithm) {
            this.algorithm = algorithm;
        }

        @Override
        public Bitmap blur(Bitmap bitmap, float blurRadius) {
            return this.algorithm.blur(bitmap, blurRadius * 6f);
        }

        @Override
        public void destroy() {
            this.algorithm.destroy();
        }

        @Override
        public boolean canModifyBitmap() {
            return this.algorithm.canModifyBitmap();
        }

        @NonNull
        @Override
        public Bitmap.Config getSupportedBitmapConfig() {
            return this.algorithm.getSupportedBitmapConfig();
        }

        @Override
        public float scaleFactor() {
            return 1f;
        }

        @Override
        public void render(@NonNull Canvas canvas, @NonNull Bitmap bitmap) {
            this.algorithm.render(canvas, bitmap);
        }
    }
}
