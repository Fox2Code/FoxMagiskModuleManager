package com.fox2code.mmm.sentry;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

import io.sentry.Breadcrumb;
import io.sentry.SentryLevel;

public class SentryBreadcrumb {
    final Breadcrumb breadcrumb;

    public SentryBreadcrumb() {
        breadcrumb = new Breadcrumb();
        breadcrumb.setLevel(SentryLevel.INFO);
    }

    public void setType(@Nullable String type) {
        breadcrumb.setType(type);
    }

    public void setData(@NotNull String key, @NotNull Object value) {
        Objects.requireNonNull(key);
        Objects.requireNonNull(value);
        breadcrumb.setData(key, value);
    }

    public void setCategory(@Nullable String category) {
        breadcrumb.setCategory(category);
    }
}
