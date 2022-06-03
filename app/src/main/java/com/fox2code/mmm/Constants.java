package com.fox2code.mmm;

public class Constants {
    public static final int MAGISK_VER_CODE_FLAT_MODULES = 19000;
    public static final int MAGISK_VER_CODE_UTIL_INSTALL = 20400;
    public static final int MAGISK_VER_CODE_PATH_SUPPORT = 21000;
    public static final int MAGISK_VER_CODE_INSTALL_COMMAND = 21200;
    public static final int MAGISK_VER_CODE_MAGISK_ZYGOTE = 24000;
    public static final String INTENT_INSTALL_INTERNAL =
            BuildConfig.APPLICATION_ID + ".intent.action.INSTALL_MODULE_INTERNAL";
    public static final String INTENT_ANDROIDACY_INTERNAL =
            BuildConfig.APPLICATION_ID + ".intent.action.OPEN_ANDROIDACY_INTERNAL";
    public static final String EXTRA_INSTALL_PATH = "extra_install_path";
    public static final String EXTRA_INSTALL_NAME = "extra_install_name";
    public static final String EXTRA_INSTALL_CONFIG = "extra_install_config";
    public static final String EXTRA_INSTALL_CHECKSUM = "extra_install_checksum";
    public static final String EXTRA_INSTALL_NO_EXTENSIONS = "extra_install_no_extensions";
    public static final String EXTRA_INSTALL_TEST_ROOTLESS = "extra_install_test_rootless";
    public static final String EXTRA_ANDROIDACY_COMPAT_LEVEL = "extra_androidacy_compat_level";
    public static final String EXTRA_ANDROIDACY_ALLOW_INSTALL = "extra_androidacy_allow_install";
    public static final String EXTRA_ANDROIDACY_ACTIONBAR_TITLE = "extra_androidacy_actionbar_title";
    public static final String EXTRA_ANDROIDACY_ACTIONBAR_CONFIG = "extra_androidacy_actionbar_config";
    public static final String EXTRA_MARKDOWN_URL = "extra_markdown_url";
    public static final String EXTRA_MARKDOWN_TITLE = "extra_markdown_title";
    public static final String EXTRA_MARKDOWN_CONFIG = "extra_markdown_config";
    public static final String EXTRA_MARKDOWN_CHANGE_BOOT = "extra_markdown_change_boot";
    public static final String EXTRA_MARKDOWN_NEEDS_RAMDISK = "extra_markdown_needs_ramdisk";
    public static final String EXTRA_MARKDOWN_MIN_MAGISK = "extra_markdown_min_magisk";
    public static final String EXTRA_MARKDOWN_MIN_API = "extra_markdown_min_api";
    public static final String EXTRA_MARKDOWN_MAX_API = "extra_markdown_max_api";
    public static final String EXTRA_FADE_OUT = "extra_fade_out";
    public static final String EXTRA_FROM_MANAGER = "extra_from_manager";
}
