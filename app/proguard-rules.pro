# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
-keepattributes SourceFile,LineNumberTable,Signature
-printmapping mapping.txt

# Optimisations
-repackageclasses ""
-overloadaggressively
-allowaccessmodification


# Markdown
-dontwarn org.commonmark.ext.gfm.strikethrough.**
-dontwarn pl.droidsonroids.gif.**
# OkHttp
-dontwarn org.bouncycastle.jsse.**
-dontwarn org.openjsse.**
-dontwarn org.conscrypt.**
# AndroidX
-dontwarn sun.misc.**


# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile


# Remove verbose and debug on release builds
-assumenosideeffects class android.util.Log {
    int v(java.lang.String, java.lang.String);
    int v(java.lang.String, java.lang.String, java.lang.Throwable);
    int d(java.lang.String, java.lang.String);
    int d(java.lang.String, java.lang.String, java.lang.Throwable);
}
-assumenosideeffects class androidx.loader.app.LoaderManager {
    static void enableDebugLogging(boolean);
}
-assumevalues class androidx.loader.app.LoaderManagerImpl {
    static boolean DEBUG;
}

# This is just some proguard rules testes, might do a separate lib after
# Made to help optimise the libraries and not the app directly
-assumenosideeffects class * extends android.content.res.Resources {
    android.content.res.AssetManager getAssets();
    android.graphics.drawable.Drawable getDrawable(int);
    android.graphics.drawable.Drawable getDrawable(int, android.content.res.Resources$Theme);
    java.lang.CharSequence getText(int);
    java.lang.CharSequence getText(int, java.lang.CharSequence);
    java.lang.String getString(int);
    java.lang.String getString(int, java.lang.Object[]);
    int getIdentifier(java.lang.String, java.lang.String, java.lang.String);
}
-assumenosideeffects class android.content.res.Resources$Theme {
    android.graphics.drawable.Drawable getDrawable(int);
    android.content.res.Resources getResources();
}
-assumenosideeffects class android.content.res.AssetManager {
    java.lang.String[] getLocales();
}
-assumenosideeffects class * extends android.content.Context {
    android.graphics.drawable.Drawable getWallpaper();
    android.graphics.drawable.Drawable getDrawable(int);
    java.lang.CharSequence getText(int);
    java.lang.String getString(int);
    java.lang.String getString(int, java.lang.Object[]);
    android.content.Context getApplicationContext();
    android.content.res.AssetManager getAssets();
    android.content.res.Resources getResources();
    android.content.res.Resources$Theme getTheme();
    java.lang.Object getSystemService(java.lang.String);
    java.lang.Object getSystemService(java.lang.Class);
    java.lang.String getSystemServiceName(java.lang.Class);
    android.view.Display getDisplay();
}
-assumenosideeffects class * extends android.content.ContextWrapper {
    android.content.Context getBaseContext();
}
-assumenosideeffects class * extends android.view.View {
    android.graphics.drawable.Drawable getBackground();
    android.graphics.drawable.Drawable getForeground();
    android.content.res.Resources getResources();
    android.content.Context getContext();
    android.view.ViewParent getParent();
    android.view.Display getDisplay();
    android.view.View findViewById(int);
    int getId();
    # Component attributes
    int getVisibility();
    int getX();
    int getY();
    int getWidth();
    int getHeight();
    int getBaseline();
    int getSystemUiVisibility();
    boolean isClickable();
    boolean isLongClickable();
    boolean isFocusable();
    boolean isFocusableInTouchMode();
    boolean isFocused();
    boolean isDirty();
    boolean isDrawingCacheEnabled();
    boolean hasFocus();
    boolean hasFocusable();
}
-assumenosideeffects class * extends android.view.ViewGroup {
    android.view.View getFocusedChild();
    android.view.View getChildAt(int);
    boolean isChildrenDrawnWithCacheEnabled();
    boolean isChildrenDrawingOrderEnabled();
    int getChildDrawingOrder(int);
    int getChildCount();
}
-assumenosideeffects class * extends android.content.Intent {
    java.lang.String getAction();
    android.net.Uri getData();
    int getFlags();
}
-assumenosideeffects class * extends android.app.Activity {
    android.view.View findViewById(int);
    android.content.Intent getIntent();
    android.view.Window getWindow();
    android.view.WindowManager getWindowManager();
    android.view.View getCurrentFocus();
    android.content.Intent getParentActivityIntent();
    android.app.Activity getParent();
    android.content.ComponentName getCallingActivity();
    java.lang.String getCallingPackage();
    android.app.Application getApplication();
}
-assumenosideeffects class * extends android.view.Window {
    android.view.WindowInsetsController getInsetsController();
    android.view.WindowManager getWindowManager();
    android.view.View findViewById(int);
    android.view.View getDecorView();
    android.content.Context getContext();
    android.view.View getCurrentFocus();
    android.view.Window getContainer();
    int getFeatures();
}
-assumenosideeffects class * extends android.view.WindowManager {
    android.view.WindowMetrics getMaximumWindowMetrics();
    android.view.WindowMetrics getCurrentWindowMetrics();
    android.view.Display getDefaultDisplay();
}
-assumenosideeffects class * extends android.graphics.drawable.Drawable {
    android.graphics.drawable.Drawable getCurrent();
    android.graphics.Insets getOpticalInsets();
    android.graphics.Rect getDirtyBounds();
    android.graphics.Rect getBounds();
    boolean isFilterBitmap();
    boolean isStateful();
    boolean isVisible();
}
-assumenosideeffects class android.view.Display {
    android.view.DisplayCutout getCutout();
    int getDisplayId();
    int getWidth();
    int getHeight();
    int getFlags();
    int getRotation();
}
-assumenosideeffects class android.view.DisplayCutout {
    android.graphics.Rect getBoundingRectBottom();
    android.graphics.Rect getBoundingRectLeft();
    android.graphics.Rect getBoundingRectRight();
    android.graphics.Rect getBoundingRectTop();
    java.util.List getBoundingRects();
    int getSafeInsetBottom();
    int getSafeInsetLeft();
    int getSafeInsetRight();
    int getSafeInsetTop();
    android.graphics.Insets getWaterfallInsets();
}

# Keep all of Cronet API and google's internal classes
-keep class org.chromium.net.** { *; }
-keep class org.chromium.** { *; }
-keep class com.google.** { *; }

# Silence some warnings
-dontwarn android.os.SystemProperties
-dontwarn android.view.ThreadedRenderer
-dontwarn cyanogenmod.providers.CMSettings$Secure
-dontwarn lineageos.providers.LineageSettings$System
-dontwarn lineageos.style.StyleInterface
-dontwarn me.weishu.reflection.Reflection
-dontwarn org.lsposed.hiddenapibypass.HiddenApiBypass
-dontwarn rikka.core.res.ResourcesCompatLayoutInflaterListener
-dontwarn rikka.core.util.ResourceUtils
-dontwarn com.afollestad.materialdialogs.MaterialDialog
-dontwarn com.afollestad.materialdialogs.WhichButton
-dontwarn com.afollestad.materialdialogs.actions.DialogActionExtKt
-dontwarn com.afollestad.materialdialogs.callbacks.DialogCallbackExtKt
-dontwarn com.afollestad.materialdialogs.internal.button.DialogActionButton
-dontwarn com.afollestad.materialdialogs.internal.button.DialogActionButtonLayout
-dontwarn com.afollestad.materialdialogs.internal.main.DialogLayout
-dontwarn com.afollestad.materialdialogs.internal.main.DialogTitleLayout
-dontwarn com.afollestad.materialdialogs.internal.message.DialogContentLayout
-dontwarn com.oracle.svm.core.annotate.AutomaticFeature
-dontwarn com.oracle.svm.core.annotate.Delete
-dontwarn com.oracle.svm.core.annotate.Substitute
-dontwarn com.oracle.svm.core.annotate.TargetClass
-dontwarn com.oracle.svm.core.configure.ResourcesRegistry
-dontwarn javax.lang.model.element.Modifier
-dontwarn org.graalvm.nativeimage.ImageSingletons
-dontwarn org.graalvm.nativeimage.hosted.Feature$BeforeAnalysisAccess
-dontwarn org.graalvm.nativeimage.hosted.Feature