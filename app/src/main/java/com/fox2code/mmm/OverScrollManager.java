package com.fox2code.mmm;

import android.util.Log;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fox2code.mmm.compat.CompatDisplay;
import com.mikepenz.aboutlibraries.LibsConfiguration;

public class OverScrollManager {
    private static final String TAG = "OverScrollManager";

    public interface OverScrollHelper {
        int getOverScrollInsetTop();

        int getOverScrollInsetBottom();
    }

    public static class LibsOverScroll implements LibsConfiguration.LibsUIListener {
        private final OverScrollHelper overScrollHelper;

        public LibsOverScroll() {
            this.overScrollHelper = null;
        }

        public LibsOverScroll(OverScrollHelper overScrollHelper) {
            this.overScrollHelper = overScrollHelper;
        }

        @NonNull
        @Override
        public View preOnCreateView(@NonNull View view) {
            return view;
        }

        @NonNull
        @Override
        public View postOnCreateView(@NonNull View view) {
            OverScrollManager.install(
                    view.findViewById(R.id.cardListView),
                    this.overScrollHelper);
            return view;
        }
    }

    public static void install(final RecyclerView recyclerView) {
        OverScrollManager.install(recyclerView, null);
    }

    public static void install(final RecyclerView recyclerView,
                               final OverScrollHelper overScrollHelper) {
        if (recyclerView == null) return;
        recyclerView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            int prevState = -1, lastTranslation = 0;

            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                if (newState == RecyclerView.SCROLL_STATE_IDLE && this.prevState != newState) {
                    if (recyclerView.getOverScrollMode() != View.OVER_SCROLL_NEVER)
                        recyclerView.setOverScrollMode(View.OVER_SCROLL_NEVER);
                    final int threshold = CompatDisplay.dpToPixel(16);
                    final int lastTranslation = this.lastTranslation;
                    if (lastTranslation < threshold) {
                        this.prevState = newState;
                        return;
                    }
                    final int insetTop;
                    final int insetBottom;
                    if (overScrollHelper == null) {
                        insetTop = 0;
                        insetBottom = 0;
                    } else {
                        insetTop = overScrollHelper.getOverScrollInsetTop();
                        insetBottom = overScrollHelper.getOverScrollInsetBottom();
                    }
                    Log.d(TAG, "Overscroll: " + lastTranslation + " -> ("
                            + insetTop + ", " + insetBottom + ")");
                    // TODO Overscroll effect for 5.0 (With settings toggle)
                }
                this.prevState = newState;
            }

            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                this.lastTranslation = dy;
            }
        });
    }
}
