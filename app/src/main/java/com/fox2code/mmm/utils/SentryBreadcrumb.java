package com.fox2code.mmm.utils;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

import io.sentry.Breadcrumb;
import io.sentry.SentryLevel;

public class SentryBreadcrumb {
    public final Breadcrumb breadcrumb;

    public SentryBreadcrumb() {
        breadcrumb = new Breadcrumb();
        breadcrumb.setLevel(SentryLevel.INFO);
    }

    public void setType(@Nullable String type) {
        breadcrumb.setType(type);
    }

    public void setData(@NotNull String key, @Nullable Object value) {
        if (value == null) value = "null";
        Objects.requireNonNull(key);
        breadcrumb.setData(key, value);
    }

    public void setCategory(@Nullable String category) {
        breadcrumb.setCategory(category);
    }
}
