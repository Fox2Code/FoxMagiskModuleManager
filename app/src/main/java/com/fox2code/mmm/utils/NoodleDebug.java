package com.fox2code.mmm.utils;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.util.Log;
import android.widget.TextView;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;

import java.lang.ref.WeakReference;
import java.util.LinkedList;
import java.util.Objects;

public class NoodleDebug {
    private static final String TAG = "NoodleDebug";
    private static final WeakReference<Thread> NULL_THREAD_REF = new WeakReference<>(null);
    private static final ThreadLocal<NoodleDebug> THREAD_NOODLE = new ThreadLocal<>();
    @SuppressLint("StaticFieldLeak") // <- Null initialized
    private static final NoodleDebug NULL = new NoodleDebug() {
        @Override
        public void setEnabled(boolean enabled) {}

        @Override
        protected void markDirty() {}
    };
    private final Activity activity;
    private final TextView textView;
    private final LinkedList<String> tokens;
    private final StringBuilder debug;
    private WeakReference<Thread> thread;
    private boolean enabled, updating;

    private NoodleDebug() {
        this.activity = null;
        this.textView = null;
        this.tokens = new LinkedList<>();
        this.debug = new StringBuilder(0);
        this.thread = NULL_THREAD_REF;
    }

    public NoodleDebug(Activity activity,@IdRes int textViewId) {
        this(activity, activity.findViewById(textViewId));
    }

    public NoodleDebug(Activity activity, TextView textView) {
        this.activity = Objects.requireNonNull(activity);
        this.textView = Objects.requireNonNull(textView);
        this.tokens = new LinkedList<>();
        this.debug = new StringBuilder(64);
        this.thread = NULL_THREAD_REF;
    }

    public NoodleDebug bind() {
        synchronized (this.tokens) {
            Thread thread;
            if ((thread = this.thread.get()) != null) {
                Log.e(TAG, "Trying to bind to thread \"" + Thread.currentThread().getName() +
                        "\" while already bound to \"" + thread.getName() + "\"");
                return NULL;
            }
            this.tokens.clear();
        }
        if (this.enabled) {
            this.thread = new WeakReference<>(Thread.currentThread());
            THREAD_NOODLE.set(this);
        } else {
            this.thread = NULL_THREAD_REF;
            THREAD_NOODLE.remove();
        }
        return this;
    }

    public void unbind() {
        this.thread = NULL_THREAD_REF;
        boolean markDirty;
        synchronized (this.tokens) {
            markDirty = !this.tokens.isEmpty();
            this.tokens.clear();
        }
        if (markDirty) this.markDirty();
    }

    public boolean isBound() {
        return this.thread.get() != null;
    }

    public void push(String token) {
        if (!this.enabled) return;
        synchronized (this.tokens) {
            this.tokens.add(token);
        }
        if (!token.isEmpty())
            this.markDirty();
    }

    public void pop() {
        if (!this.enabled) return;
        String last;
        synchronized (this.tokens) {
            last = this.tokens.removeLast();
        }
        if (!last.isEmpty())
            this.markDirty();
    }

    public void replace(String token) {
        if (!this.enabled) return;
        String last;
        synchronized (this.tokens) {
            last = this.tokens.removeLast();
            this.tokens.add(token);
        }
        if (!last.equals(token))
            this.markDirty();
    }

    public void setEnabled(boolean enabled) {
        if (this.enabled && !enabled) {
            this.thread = NULL_THREAD_REF;
            synchronized (this.tokens) {
                this.tokens.clear();
            }
            this.markDirty();
        }
        this.enabled = enabled;
    }

    protected void markDirty() {
        assert this.activity != null;
        assert this.textView != null;
        if (this.updating) return;
        this.updating = true;
        this.activity.runOnUiThread(() -> {
            String debugText;
            synchronized (this.tokens) {
                StringBuilder debug = this.debug;
                debug.setLength(0);
                boolean first = true;
                for (String text : this.tokens) {
                    if (text.isEmpty()) continue;
                    if (first) first = false;
                    else debug.append(" > ");
                    debug.append(text);
                }
                debugText = debug.toString();
            }
            this.updating = false;
            this.textView.setText(debugText);
        });
    }

    @NonNull
    public static NoodleDebug getNoodleDebug() {
        NoodleDebug noodleDebug = THREAD_NOODLE.get();
        if (noodleDebug == null) return NULL;
        if (noodleDebug.thread.get() != Thread.currentThread()) {
            THREAD_NOODLE.remove();
            return NULL;
        }
        return noodleDebug;
    }
}
