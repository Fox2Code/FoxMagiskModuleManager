package com.fox2code.mmm;

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;
import android.os.SystemClock;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.StyleRes;
import androidx.core.app.NotificationManagerCompat;
import androidx.emoji2.text.DefaultEmojiCompatConfig;
import androidx.emoji2.text.EmojiCompat;
import androidx.emoji2.text.FontRequestEmojiCompatConfig;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import com.fox2code.foxcompat.app.FoxActivity;
import com.fox2code.foxcompat.app.FoxApplication;
import com.fox2code.foxcompat.app.internal.FoxProcessExt;
import com.fox2code.foxcompat.view.FoxThemeWrapper;
import com.fox2code.mmm.installer.InstallerInitializer;
import com.fox2code.mmm.utils.io.GMSProviderInstaller;
import com.fox2code.mmm.utils.io.net.Http;
import com.fox2code.mmm.utils.sentry.SentryMain;
import com.fox2code.rosettax.LanguageSwitcher;
import com.google.common.hash.Hashing;
import com.topjohnwu.superuser.Shell;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SecureRandom;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Random;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.KeyGenerator;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;

import io.noties.markwon.Markwon;
import io.noties.markwon.html.HtmlPlugin;
import io.noties.markwon.image.ImagesPlugin;
import io.noties.markwon.image.network.OkHttpNetworkSchemeHandler;
import io.realm.Realm;
import io.sentry.Sentry;
import io.sentry.SentryLevel;
import io.sentry.android.timber.SentryTimberTree;
import timber.log.Timber;

@SuppressWarnings("CommentedOutCode")
public class MainApplication extends FoxApplication implements androidx.work.Configuration.Provider {
    // Warning! Locales that don't exist will crash the app
    // Anything that is commented out is supported but the translation is not complete to at least 60%
    public static final HashSet<String> supportedLocales = new HashSet<>();
    private static final String timeFormatString = "dd MMM yyyy"; // Example: 13 july 2001
    private static final Shell.Builder shellBuilder;
    @SuppressLint("RestrictedApi")
    // Use FoxProcess wrapper helper.
    private static final boolean wrapped = !FoxProcessExt.isRootLoader();
    public static boolean isOfficial = false;
    private static long secret;
    private static Locale timeFormatLocale = Resources.getSystem().getConfiguration().getLocales().get(0);
    private static SimpleDateFormat timeFormat = new SimpleDateFormat(timeFormatString, timeFormatLocale);
    private static String relPackageName = BuildConfig.APPLICATION_ID;
    @SuppressLint("StaticFieldLeak")
    private static MainApplication INSTANCE;
    private static boolean firstBoot;
    private static HashMap<Object, Object> mSharedPrefs;

    static {
        Shell.setDefaultBuilder(shellBuilder = Shell.Builder.create().setFlags(Shell.FLAG_REDIRECT_STDERR).setTimeout(10).setInitializers(InstallerInitializer.class));
        Random random = new Random();
        do {
            secret = random.nextLong();
        } while (secret == 0);
    }

    @StyleRes
    private int managerThemeResId = R.style.Theme_MagiskModuleManager;
    private FoxThemeWrapper markwonThemeContext;
    private Markwon markwon;
    private byte[] existingKey;

    public MainApplication() {
        if (INSTANCE != null && INSTANCE != this)
            throw new IllegalStateException("Duplicate application instance!");
        INSTANCE = this;
    }

    public static Shell build(String... command) {
        return shellBuilder.build(command);
    }

    public static void addSecret(Intent intent) {
        ComponentName componentName = intent.getComponent();
        String packageName = componentName != null ? componentName.getPackageName() : intent.getPackage();
        if (!BuildConfig.APPLICATION_ID.equalsIgnoreCase(packageName) && !relPackageName.equals(packageName)) {
            // Code safeguard, we should never reach here.
            throw new IllegalArgumentException("Can't add secret to outbound Intent");
        }
        intent.putExtra("secret", secret);
    }

    public static SharedPreferences getPreferences(String name) {
        // encryptedSharedPreferences is used
        Context mContext = getINSTANCE();
        if (mSharedPrefs == null) {
            Timber.d("Creating shared prefs map");
            mSharedPrefs = new HashMap<>();
        }
        if (mSharedPrefs.containsKey(name)) {
            Timber.d("Returning cached shared prefs");
            return (SharedPreferences) mSharedPrefs.get(name);
        }
        try {
            Timber.d("Creating encrypted shared prefs");
            MasterKey masterKey = new MasterKey.Builder(mContext).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build();
            SharedPreferences sharedPreferences = EncryptedSharedPreferences.create(mContext, name, masterKey, EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV, EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM);
            mSharedPrefs.put(name, sharedPreferences);
            return sharedPreferences;
        } catch (Exception e) {
            Timber.e(e, "Failed to create encrypted shared preferences");
            return mContext.getSharedPreferences(name, Context.MODE_PRIVATE);
        }
    }

    // Is application wrapped, and therefore must reduce it's feature set.
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public static boolean isWrapped() {
        return wrapped;
    }

    public static boolean checkSecret(Intent intent) {
        return intent != null && intent.getLongExtra("secret", ~secret) == secret;
    }

    public static boolean isShowcaseMode() {
        return getPreferences("mmm").getBoolean("pref_showcase_mode", false);
    }

    public static boolean shouldPreventReboot() {
        return getPreferences("mmm").getBoolean("pref_prevent_reboot", true);
    }

    public static boolean isShowIncompatibleModules() {
        return getPreferences("mmm").getBoolean("pref_show_incompatible", false);
    }

    public static boolean isForceDarkTerminal() {
        return getPreferences("mmm").getBoolean("pref_force_dark_terminal", false);
    }

    public static boolean isTextWrapEnabled() {
        return getPreferences("mmm").getBoolean("pref_wrap_text", false);
    }

    public static boolean isDohEnabled() {
        return getPreferences("mmm").getBoolean("pref_dns_over_https", true);
    }

    public static boolean isMonetEnabled() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && getPreferences("mmm").getBoolean("pref_enable_monet", true);
    }

    public static boolean isBlurEnabled() {
        return getPreferences("mmm").getBoolean("pref_enable_blur", false);
    }

    public static boolean isDeveloper() {
        if (BuildConfig.DEBUG) return true;
        return getPreferences("mmm").getBoolean("developer", false);
    }

    public static boolean isDisableLowQualityModuleFilter() {
        return getPreferences("mmm").getBoolean("pref_disable_low_quality_module_filter", false) && isDeveloper();
    }

    public static boolean isUsingMagiskCommand() {
        return InstallerInitializer.peekMagiskVersion() >= Constants.MAGISK_VER_CODE_INSTALL_COMMAND && getPreferences("mmm").getBoolean("pref_use_magisk_install_command", false) && isDeveloper();
    }

    public static boolean isBackgroundUpdateCheckEnabled() {
        return !wrapped && getPreferences("mmm").getBoolean("pref_background_update_check", true);
    }

    public static boolean isAndroidacyTestMode() {
        return isDeveloper() && getPreferences("mmm").getBoolean("pref_androidacy_test_mode", false);
    }

    public static boolean isFirstBoot() {
        return firstBoot;
    }

    public static void setHasGottenRootAccess(boolean bool) {
        getPreferences("mmm").edit().putBoolean("has_root_access", bool).apply();
    }

    public static boolean isCrashReportingEnabled() {
        return SentryMain.IS_SENTRY_INSTALLED && getPreferences("mmm").getBoolean("pref_crash_reporting", BuildConfig.DEFAULT_ENABLE_CRASH_REPORTING);
    }

    public static SharedPreferences getBootSharedPreferences() {
        return getPreferences("mmm_boot");
    }

    public static MainApplication getINSTANCE() {
        return INSTANCE;
    }

    public static String formatTime(long timeStamp) {
        // new Date(x) also get the local timestamp for format
        return timeFormat.format(new Date(timeStamp));
    }

    public static boolean isNotificationPermissionGranted() {
        return NotificationManagerCompat.from(INSTANCE).areNotificationsEnabled();
    }

    public Markwon getMarkwon() {
        if (this.markwon != null) return this.markwon;
        FoxThemeWrapper contextThemeWrapper = this.markwonThemeContext;
        if (contextThemeWrapper == null) {
            contextThemeWrapper = this.markwonThemeContext = new FoxThemeWrapper(this, this.managerThemeResId);
        }
        Markwon markwon = Markwon.builder(contextThemeWrapper).usePlugin(HtmlPlugin.create()).usePlugin(ImagesPlugin.create().addSchemeHandler(OkHttpNetworkSchemeHandler.create(Http.getHttpClientWithCache()))).build();
        return this.markwon = markwon;
    }

    @NonNull
    @Override
    public androidx.work.Configuration getWorkManagerConfiguration() {
        return new androidx.work.Configuration.Builder().build();
    }

    public void updateTheme() {
        @StyleRes int themeResId;
        String theme;
        boolean monet = isMonetEnabled();
        switch (theme = getPreferences("mmm").getString("pref_theme", "system")) {
            default:
                Timber.w("Unknown theme id: %s", theme);
            case "system":
                themeResId = monet ? R.style.Theme_MagiskModuleManager_Monet : R.style.Theme_MagiskModuleManager;
                break;
            case "dark":
                themeResId = monet ? R.style.Theme_MagiskModuleManager_Monet_Dark : R.style.Theme_MagiskModuleManager_Dark;
                break;
            case "black":
                themeResId = monet ? R.style.Theme_MagiskModuleManager_Monet_Black : R.style.Theme_MagiskModuleManager_Black;
                break;
            case "light":
                themeResId = monet ? R.style.Theme_MagiskModuleManager_Monet_Light : R.style.Theme_MagiskModuleManager_Light;
                break;
            case "transparent_light":
                if (monet) {
                    Timber.tag("MainApplication").w("Monet is not supported for transparent theme");
                }
                themeResId = R.style.Theme_MagiskModuleManager_Transparent_Light;
                break;
        }
        this.setManagerThemeResId(themeResId);
    }

    @StyleRes
    public int getManagerThemeResId() {
        return managerThemeResId;
    }

    @SuppressLint("NonConstantResourceId")
    public void setManagerThemeResId(@StyleRes int resId) {
        this.managerThemeResId = resId;
        if (this.markwonThemeContext != null) {
            this.markwonThemeContext.setTheme(resId);
        }
        this.markwon = null;
    }

    @SuppressLint("NonConstantResourceId")
    public boolean isLightTheme() {
        return switch (getPreferences("mmm").getString("pref_theme", "system")) {
            case "system" -> this.isSystemLightTheme();
            case "dark", "black" -> false;
            default -> true;
        };
    }

    private boolean isSystemLightTheme() {
        return (this.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) != Configuration.UI_MODE_NIGHT_YES;
    }

    @SuppressWarnings("unused")
    public boolean isDarkTheme() {
        return !this.isLightTheme();
    }

    @Override
    public void onCreate() {
        supportedLocales.add("ar");
        // supportedLocales.add("ar_SA");
        supportedLocales.add("bs");
        supportedLocales.add("cs");
        supportedLocales.add("de");
        // supportedLocales.add("el");
        // supportedLocales.add("es");
        // supportedLocales.add("es-rMX");
        // supportedLocales.add("et");
        supportedLocales.add("fr");
        supportedLocales.add("hu");
        supportedLocales.add("id");
        supportedLocales.add("it");
        supportedLocales.add("ja");
        supportedLocales.add("ko");
        // supportedLocales.add("nb-rNO");
        supportedLocales.add("pl");
        supportedLocales.add("pt-rBR");
        supportedLocales.add("ro");
        supportedLocales.add("ru");
        supportedLocales.add("sk");
        supportedLocales.add("tr");
        // supportedLocales.add("th");
        supportedLocales.add("uk");
        // supportedLocales.add("vi");
        supportedLocales.add("zh-rCN");
        supportedLocales.add("zh-rTW");
        supportedLocales.add("en");
        if (INSTANCE == null) INSTANCE = this;
        relPackageName = this.getPackageName();
        super.onCreate();
        SentryMain.initialize(this);
        // init timber
        if (BuildConfig.DEBUG) {
            Timber.plant(new Timber.DebugTree());
        } else {
            if (isCrashReportingEnabled()) {
                //noinspection UnstableApiUsage
                Timber.plant(new SentryTimberTree(Sentry.getCurrentHub(), SentryLevel.ERROR, SentryLevel.ERROR));
            } else {
                Timber.plant(new ReleaseTree());
            }
        }
        Timber.i("Starting FoxMMM version %s (%d) - commit %s", BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE, BuildConfig.COMMIT_HASH);
        // Update SSL Ciphers if update is possible
        GMSProviderInstaller.installIfNeeded(this);
        Timber.d("Initializing FoxMMM");
        Timber.d("Started from background: %s", !isInForeground());
        Timber.d("FoxMMM is running in debug mode");
        Timber.d("Initializing Realm");
        Realm.init(this);
        Timber.d("Initialized Realm");
        // Determine if this is an official build based on the signature
        try {
            // Get the signature of the key used to sign the app
            @SuppressLint("PackageManagerGetSignatures") Signature[] s = this.getPackageManager().getPackageInfo(this.getPackageName(), PackageManager.GET_SIGNATURES).signatures;
            @SuppressWarnings("SpellCheckingInspection") String[] osh = new String[]{"7bec7c4462f4aac616612d9f56a023ee3046e83afa956463b5fab547fd0a0be6", "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"};
            String oosh = Hashing.sha256().hashBytes(s[0].toByteArray()).toString();
            isOfficial = Arrays.asList(osh).contains(oosh);
        } catch (PackageManager.NameNotFoundException ignored) {
        }
        // hide this behind a buildconfig flag for now, but crash the app if it's not an official build and not debug
        if (BuildConfig.ENABLE_PROTECTION && !isOfficial && !BuildConfig.DEBUG) {
            throw new RuntimeException("This is not an official build of FoxMMM");
        }
        SharedPreferences sharedPreferences = MainApplication.getPreferences("mmm");
        // We are only one process so it's ok to do this
        SharedPreferences bootPrefs = MainApplication.getPreferences("mmm_boot");
        long lastBoot = System.currentTimeMillis() - SystemClock.elapsedRealtime();
        long lastBootPrefs = bootPrefs.getLong("last_boot", 0);
        if (lastBootPrefs == 0 || Math.abs(lastBoot - lastBootPrefs) > 100) {
            boolean firstBoot = sharedPreferences.getBoolean("first_boot", true);
            bootPrefs.edit().clear().putLong("last_boot", lastBoot).putBoolean("first_boot", firstBoot).apply();
            if (firstBoot) {
                sharedPreferences.edit().putBoolean("first_boot", false).apply();
            }
            MainApplication.firstBoot = firstBoot;
        } else {
            MainApplication.firstBoot = bootPrefs.getBoolean("first_boot", false);
        }
        // Force initialize language early.
        new LanguageSwitcher(this);
        this.updateTheme();
        // Update emoji config
        FontRequestEmojiCompatConfig fontRequestEmojiCompatConfig = DefaultEmojiCompatConfig.create(this);
        if (fontRequestEmojiCompatConfig != null) {
            fontRequestEmojiCompatConfig.setReplaceAll(true);
            fontRequestEmojiCompatConfig.setMetadataLoadStrategy(EmojiCompat.LOAD_STRATEGY_MANUAL);
            EmojiCompat emojiCompat = EmojiCompat.init(fontRequestEmojiCompatConfig);
            new Thread(() -> {
                Timber.i("Loading emoji compat...");
                emojiCompat.load();
                Timber.i("Emoji compat loaded!");
            }, "Emoji compat init.").start();
        }
        if (Objects.equals(BuildConfig.ANDROIDACY_CLIENT_ID, "")) {
            Timber.w("Androidacy client id is empty! Please set it in androidacy.properties. Will not enable Androidacy.");
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putBoolean("pref_androidacy_repo_enabled", false);
            editor.apply();
        }
    }

    @SuppressWarnings("unused")
    private Intent getIntent() {
        return this.getPackageManager().getLaunchIntentForPackage(this.getPackageName());
    }

    @Override
    public void onCreateFoxActivity(FoxActivity compatActivity) {
        super.onCreateFoxActivity(compatActivity);
        compatActivity.setTheme(this.managerThemeResId);
    }

    @Override
    public void onRefreshUI(FoxActivity compatActivity) {
        super.onRefreshUI(compatActivity);
        compatActivity.setThemeRecreate(this.managerThemeResId);
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        Locale newTimeFormatLocale = newConfig.getLocales().get(0);
        if (timeFormatLocale != newTimeFormatLocale) {
            timeFormatLocale = newTimeFormatLocale;
            timeFormat = new SimpleDateFormat(timeFormatString, timeFormatLocale);
        }
        super.onConfigurationChanged(newConfig);
    }

    // getDataDir wrapper with optional path parameter
    public File getDataDirWithPath(String path) {
        File dataDir = this.getDataDir();
        // for path with / somewhere in the middle, its a subdirectory
        if (path != null) {
            if (path.startsWith("/")) path = path.substring(1);
            if (path.endsWith("/")) path = path.substring(0, path.length() - 1);
            if (path.contains("/")) {
                String[] dirs = path.split("/");
                for (String dir : dirs) {
                    dataDir = new File(dataDir, dir);
                    // make sure the directory exists
                    if (!dataDir.exists()) {
                        if (!dataDir.mkdirs()) {
                            if (BuildConfig.DEBUG)
                                Timber.w("Failed to create directory %s", dataDir);
                        }
                    }
                }
            } else {
                dataDir = new File(dataDir, path);
                // create the directory if it doesn't exist
                if (!dataDir.exists()) {
                    if (!dataDir.mkdirs()) {
                        if (BuildConfig.DEBUG) Timber.w("Failed to create directory %s", dataDir);
                    }
                }
            }
            return dataDir;
        } else {
            throw new IllegalArgumentException("Path cannot be null");
        }
    }

    @SuppressLint("RestrictedApi")
    // view is nullable because it's called from xml
    public void resetApp() {
        // cant show a dialog because android is throwing a fit so here's hoping anybody who calls this method is otherwise confirming that the user wants to reset the app
        Timber.w("Resetting app...");
        // recursively delete the app's data
        ((ActivityManager) this.getSystemService(Context.ACTIVITY_SERVICE)).clearApplicationUserData();
    }

    public boolean isInForeground() {
        // determine if the app is in the foreground
        ActivityManager activityManager = (ActivityManager) this.getSystemService(Context.ACTIVITY_SERVICE);
        List<ActivityManager.RunningAppProcessInfo> appProcesses = activityManager.getRunningAppProcesses();
        if (appProcesses == null) {
            Timber.d("appProcesses is null");
            return false;
        }
        final String packageName = this.getPackageName();
        for (ActivityManager.RunningAppProcessInfo appProcess : appProcesses) {
            Timber.d("Process: %s, Importance: %d", appProcess.processName, appProcess.importance);
            if (appProcess.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND && appProcess.processName.equals(packageName)) {
                return true;
            }
        }
        return false;
    }

    // returns if background execution is restricted
    @SuppressWarnings("unused")
    public boolean isBackgroundRestricted() {
        ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return am.isBackgroundRestricted();
        } else {
            return false;
        }
    }

    // Create a key to encrypt a realm and save it securely in the keystore
    public byte[] getNewKey() {
        Timber.d("Creating a new key.");
        // check if we have a key already
        SharedPreferences sharedPreferences = MainApplication.getPreferences("realm_key");
        if (sharedPreferences.contains("iv_and_encrypted_key")) {
            Timber.v("Found a key in the keystore.");
            return getExistingKey();
        }
        // open a connection to the android keystore
        KeyStore keyStore;
        try {
            keyStore = KeyStore.getInstance("AndroidKeyStore");
            keyStore.load(null);
        } catch (KeyStoreException | NoSuchAlgorithmException | CertificateException |
                 IOException e) {
            Timber.v("Failed to open the keystore.");
            throw new RuntimeException(e);
        }
        // create a securely generated random asymmetric RSA key
        byte[] realmKey = new byte[Realm.ENCRYPTION_KEY_LENGTH];
        new SecureRandom().nextBytes(realmKey);
        // create a cipher that uses AES encryption -- we'll use this to encrypt our key
        Cipher cipher;
        try {
            cipher = Cipher.getInstance(KeyProperties.KEY_ALGORITHM_AES + "/" + KeyProperties.BLOCK_MODE_CBC + "/" + KeyProperties.ENCRYPTION_PADDING_PKCS7);
        } catch (NoSuchAlgorithmException | NoSuchPaddingException e) {
            Timber.e("Failed to create a cipher.");
            throw new RuntimeException(e);
        }
        Timber.v("Cipher created.");
        // generate secret key
        KeyGenerator keyGenerator;
        try {
            keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        } catch (NoSuchAlgorithmException | NoSuchProviderException e) {
            Timber.e("Failed to access the key generator.");
            throw new RuntimeException(e);
        }
        KeyGenParameterSpec keySpec = new KeyGenParameterSpec.Builder("realm_key", KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_CBC).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_PKCS7).build();
        try {
            keyGenerator.init(keySpec);
        } catch (InvalidAlgorithmParameterException e) {
            Timber.e("Failed to generate a secret key.");
            throw new RuntimeException(e);
        }
        Timber.v("Secret key generated.");
        keyGenerator.generateKey();
        Timber.v("Secret key stored in the keystore.");
        // access the generated key in the android keystore, then
        // use the cipher to create an encrypted version of the key
        byte[] initializationVector;
        byte[] encryptedKeyForRealm;
        try {
            SecretKey secretKey = (SecretKey) keyStore.getKey("realm_key", null);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey);
            encryptedKeyForRealm = cipher.doFinal(realmKey);
            initializationVector = cipher.getIV();
        } catch (InvalidKeyException | UnrecoverableKeyException | NoSuchAlgorithmException |
                 KeyStoreException | BadPaddingException | IllegalBlockSizeException e) {
            Timber.e("Failed encrypting the key with the secret key.");
            throw new RuntimeException(e);
        }
        // keep the encrypted key in shared preferences
        // to persist it across application runs
        byte[] initializationVectorAndEncryptedKey = new byte[Integer.BYTES + initializationVector.length + encryptedKeyForRealm.length];
        ByteBuffer buffer = ByteBuffer.wrap(initializationVectorAndEncryptedKey);
        buffer.order(ByteOrder.BIG_ENDIAN);
        buffer.putInt(initializationVector.length);
        buffer.put(initializationVector);
        buffer.put(encryptedKeyForRealm);
        Timber.d("Created all keys successfully.");
        MainApplication.getPreferences("realm_key").edit().putString("iv_and_encrypted_key", Base64.encodeToString(initializationVectorAndEncryptedKey, Base64.NO_WRAP)).apply();
        Timber.d("Saved the encrypted key in shared preferences.");
        return realmKey; // pass to a realm configuration via encryptionKey()
    }

    // Access the encrypted key in the keystore, decrypt it with the secret,
    // and use it to open and read from the realm again
    public byte[] getExistingKey() {
        Timber.d("Accessing the existing key.");
        // attempt to read the existingKey property
        if (existingKey != null) {
            Timber.v("Found an existing key in memory.");
            return existingKey;
        }
        // open a connection to the android keystore
        KeyStore keyStore;
        try {
            keyStore = KeyStore.getInstance("AndroidKeyStore");
            keyStore.load(null);
        } catch (KeyStoreException | NoSuchAlgorithmException | CertificateException |
                 IOException e) {
            Timber.e("Failed to open the keystore.");
            throw new RuntimeException(e);
        }
        Timber.v("Keystore opened.");
        // access the encrypted key that's stored in shared preferences
        byte[] initializationVectorAndEncryptedKey = Base64.decode(MainApplication.getPreferences("realm_key").getString("iv_and_encrypted_key", null), Base64.DEFAULT);
        Timber.d("Retrieved the encrypted key from shared preferences. Key length: %d", initializationVectorAndEncryptedKey.length);
        ByteBuffer buffer = ByteBuffer.wrap(initializationVectorAndEncryptedKey);
        buffer.order(ByteOrder.BIG_ENDIAN);
        // extract the length of the initialization vector from the buffer
        int initializationVectorLength = buffer.getInt();
        // extract the initialization vector based on that length
        byte[] initializationVector = new byte[initializationVectorLength];
        buffer.get(initializationVector);
        // extract the encrypted key
        byte[] encryptedKey = new byte[initializationVectorAndEncryptedKey.length - Integer.BYTES - initializationVectorLength];
        buffer.get(encryptedKey);
        Timber.d("Got key from shared preferences.");
        // create a cipher that uses AES encryption to decrypt our key
        Cipher cipher;
        try {
            cipher = Cipher.getInstance(KeyProperties.KEY_ALGORITHM_AES + "/" + KeyProperties.BLOCK_MODE_CBC + "/" + KeyProperties.ENCRYPTION_PADDING_PKCS7);
        } catch (NoSuchAlgorithmException | NoSuchPaddingException e) {
            Timber.e("Failed to create cipher.");
            throw new RuntimeException(e);
        }
        // decrypt the encrypted key with the secret key stored in the keystore
        byte[] decryptedKey;
        try {
            final SecretKey secretKey = (SecretKey) keyStore.getKey("realm_key", null);
            final IvParameterSpec initializationVectorSpec = new IvParameterSpec(initializationVector);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, initializationVectorSpec);
            decryptedKey = cipher.doFinal(encryptedKey);
        } catch (InvalidKeyException e) {
            Timber.e("Failed to decrypt. Invalid key.");
            throw new RuntimeException(e);
        } catch (UnrecoverableKeyException | NoSuchAlgorithmException | BadPaddingException |
                 KeyStoreException | IllegalBlockSizeException |
                 InvalidAlgorithmParameterException e) {
            Timber.e("Failed to decrypt the encrypted realm key with the secret key.");
            throw new RuntimeException(e);
        }
        // set property on MainApplication to indicate that the key has been accessed
        existingKey = decryptedKey;
        return decryptedKey; // pass to a realm configuration via encryptionKey()
    }

    private static class ReleaseTree extends Timber.Tree {
        @Override
        protected void log(int priority, String tag, @NonNull String message, Throwable t) {
            // basically silently drop all logs below error, and write the rest to logcat
            if (priority >= Log.ERROR) {
                if (t != null) {
                    Log.println(priority, tag, message);
                    t.printStackTrace();
                } else {
                    Log.println(priority, tag, message);
                }
            }
        }
    }
}
