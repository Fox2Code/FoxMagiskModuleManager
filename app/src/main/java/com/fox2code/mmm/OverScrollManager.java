package com.fox2code.mmm;

public class OverScrollManager {
    private static final String TAG = "OverScrollManager";

    public interface OverScrollHelper {
        int getOverScrollInsetTop();

        int getOverScrollInsetBottom();
    }
}
