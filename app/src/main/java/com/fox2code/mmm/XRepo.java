package com.fox2code.mmm;

import androidx.annotation.Keep;

/**
 * Class made to expose some repo functions to xposed modules.
 * It will not be obfuscated on release builds
 */
@Keep
public abstract class XRepo {
    @Keep
    public abstract boolean isEnabledByDefault();

    @Keep
    public abstract boolean isEnabled();

    @Keep
    public abstract void setEnabled(boolean enabled);

    @Keep
    public abstract String getName();
}
