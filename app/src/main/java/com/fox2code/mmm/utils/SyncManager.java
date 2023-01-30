package com.fox2code.mmm.utils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Manager that want both to be thread safe and not to worry about thread safety
 * {@link #scan()} and {@link #update(UpdateListener)} can be called from multiple
 * thread at the same time, {@link #scanInternal(UpdateListener)} will only be
 * called from one thread at a time only.
 */
public abstract class SyncManager {
    private static final UpdateListener NO_OP = value -> {};
    protected final Object syncLock = new Object();
    private boolean syncing;
    private long lastSync;

    public final void scanAsync() {
        if (!this.syncing) {
            new Thread(this::scan, "Scan Thread").start();
        }
    }

    public final void scan() {
        this.update(null);
    }

    // MultiThread friendly method
    public final void update(@Nullable UpdateListener updateListener) {
        if (updateListener == null) updateListener = NO_OP;
        if (!this.syncing) {
            // Do scan
            synchronized (this.syncLock) {
                if (System.currentTimeMillis() < this.lastSync + 50L)
                    return; // Skip sync if it was synced too recently
                this.syncing = true;
                try {
                    this.scanInternal(updateListener);
                } finally {
                    this.lastSync = System.currentTimeMillis();
                    this.syncing = false;
                }
            }
        } else {
            // Wait for current scan
            synchronized (this.syncLock) {
                Thread.yield();
            }
        }
    }

    // Pause execution until the scan is completed if one is currently running
    public final void afterScan() {
        if (this.syncing) synchronized (this.syncLock) { Thread.yield(); }
    }

    public final void runAfterScan(Runnable runnable) {
        synchronized (this.syncLock) {
            runnable.run();
        }
    }

    public final void afterUpdate() {
        if (this.syncing) synchronized (this.syncLock) { Thread.yield(); }
    }

    public final void runAfterUpdate(Runnable runnable) {
        synchronized (this.syncLock) {
            runnable.run();
        }
    }

    // This method can't be called twice at the same time.
    protected abstract void scanInternal(@NonNull UpdateListener updateListener);

    public interface UpdateListener {
        void update(double value);
    }
}
