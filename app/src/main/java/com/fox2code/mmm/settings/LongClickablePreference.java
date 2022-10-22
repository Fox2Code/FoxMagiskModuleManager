package com.fox2code.mmm.settings;

import android.content.Context;
import android.util.AttributeSet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

public class LongClickablePreference extends Preference {
    private OnPreferenceLongClickListener onPreferenceLongClickListener;

    public LongClickablePreference(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public LongClickablePreference(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public LongClickablePreference(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public LongClickablePreference(@NonNull Context context) {
        super(context);
    }

    @Override
    public void onBindViewHolder(@NonNull PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        holder.itemView.setOnLongClickListener(v -> performLongClick());
    }

    private boolean performLongClick() {
        if (!this.isEnabled() || !this.isSelectable()) {
            return false;
        }
        if (this.onPreferenceLongClickListener != null) {
            return this.onPreferenceLongClickListener.onPreferenceLongClick(this);
        }
        return false;
    }

    public void setOnPreferenceLongClickListener(OnPreferenceLongClickListener onPreferenceLongClickListener) {
        this.onPreferenceLongClickListener = onPreferenceLongClickListener;
    }

    public OnPreferenceLongClickListener getOnPreferenceLongClickListener() {
        return this.onPreferenceLongClickListener;
    }

    @FunctionalInterface
    public interface OnPreferenceLongClickListener {
        /**
         * Called when a preference has been clicked.
         *
         * @param preference The preference that was clicked
         * @return {@code true} if the click was handled
         */
        boolean onPreferenceLongClick(@NonNull Preference preference);
    }
}
