package com.fox2code.mmm.utils.io;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.system.ErrnoException;
import android.system.Os;
import android.webkit.CookieManager;
import android.webkit.WebSettings;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.fox2code.mmm.BuildConfig;
import com.fox2code.mmm.MainActivity;
import com.fox2code.mmm.MainApplication;
import com.fox2code.mmm.androidacy.AndroidacyUtil;
import com.fox2code.mmm.installer.InstallerInitializer;
import com.google.net.cronet.okhttptransport.CronetInterceptor;

import org.chromium.net.CronetEngine;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.Proxy;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import io.sentry.android.okhttp.SentryOkHttpInterceptor;
import okhttp3.Cache;
import okhttp3.Cookie;
import okhttp3.CookieJar;
import okhttp3.Dns;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okhttp3.dnsoverhttps.DnsOverHttps;
import okio.BufferedSink;
import timber.log.Timber;

public enum Http {
    ;
    private static final OkHttpClient httpClient;
    private static final OkHttpClient httpClientDoH;
    private static final OkHttpClient httpClientWithCache;
    private static final OkHttpClient httpClientWithCacheDoH;
    private static final FallBackDNS fallbackDNS;
    private static final String androidacyUA;
    private static final boolean hasWebView;
    private static String needCaptchaAndroidacyHost;
    private static boolean doh;

    static {
        MainApplication mainApplication = MainApplication.getINSTANCE();
        if (mainApplication == null) {
            Error error = new Error("Initialized Http too soon!");
            error.fillInStackTrace();
            Timber.e(error, "Initialized Http too soon!");
            System.out.flush();
            System.err.flush();
            try {
                Os.kill(Os.getpid(), 9);
            } catch (
                    ErrnoException e) {
                System.exit(9);
            }
            throw error;
        }
        CookieManager cookieManager;
        try {
            cookieManager = CookieManager.getInstance();
            cookieManager.setAcceptCookie(true);
            cookieManager.flush(); // Make sure the instance work
        } catch (
                Exception t) {
            cookieManager = null;
            Timber.e(t, "No WebView support!");
        }
        hasWebView = cookieManager != null;
        OkHttpClient.Builder httpclientBuilder = new OkHttpClient.Builder();
        // Default is 10, extend it a bit for slow mobile connections.
        httpclientBuilder.connectTimeout(5, TimeUnit.SECONDS);
        httpclientBuilder.writeTimeout(10, TimeUnit.SECONDS);
        httpclientBuilder.readTimeout(15, TimeUnit.SECONDS);
        httpclientBuilder.proxy(Proxy.NO_PROXY); // Do not use system proxy
        Dns dns = Dns.SYSTEM;
        try {
            InetAddress[] cloudflareBootstrap = new InetAddress[]{InetAddress.getByName("162.159.36.1"), InetAddress.getByName("162.159.46.1"), InetAddress.getByName("1.1.1.1"), InetAddress.getByName("1.0.0.1"), InetAddress.getByName("162.159.132.53"), InetAddress.getByName("2606:4700:4700::1111"), InetAddress.getByName("2606:4700:4700::1001"), InetAddress.getByName("2606:4700:4700::0064"), InetAddress.getByName("2606:4700:4700::6400")};
            dns = s -> {
                if ("cloudflare-dns.com".equals(s)) {
                    return Arrays.asList(cloudflareBootstrap);
                }
                return Dns.SYSTEM.lookup(s);
            };
            httpclientBuilder.dns(dns);
            httpclientBuilder.cookieJar(new CDNCookieJar());
            dns = new DnsOverHttps.Builder().client(httpclientBuilder.build()).url(Objects.requireNonNull(HttpUrl.parse("https://cloudflare-dns.com/dns-query"))).bootstrapDnsHosts(cloudflareBootstrap).resolvePrivateAddresses(true).build();
        } catch (
                UnknownHostException |
                RuntimeException e) {
            Timber.e(e, "Failed to init DoH");
        }
        // Add cookie support.
        httpclientBuilder.addInterceptor(new AddCookiesInterceptor(MainApplication.getINSTANCE().getApplicationContext())); // VERY VERY IMPORTANT
        httpclientBuilder.addInterceptor(new ReceivedCookiesInterceptor(MainApplication.getINSTANCE().getApplicationContext())); // VERY VERY IMPORTANT
        // User-Agent format was agreed on telegram
        if (hasWebView) {
            androidacyUA = WebSettings.getDefaultUserAgent(mainApplication).replace("wv", "") + " FoxMMM/" + BuildConfig.VERSION_CODE;
        } else {
            androidacyUA = "Mozilla/5.0 (Linux; Android " + Build.VERSION.RELEASE + "; " + Build.DEVICE + ")" + " AppleWebKit/537.36 (KHTML, like Gecko) Chrome/92.0.4515.131 Mobile Safari/537.36" + " FoxMmm/" + BuildConfig.VERSION_CODE;
        }
        httpclientBuilder.addInterceptor(chain -> {
            Request.Builder request = chain.request().newBuilder();
            request.header("Upgrade-Insecure-Requests", "1");
            String host = chain.request().url().host();
            if (host.endsWith(".androidacy.com")) {
                request.header("User-Agent", androidacyUA);
            } else if (!(host.equals("github.com") || host.endsWith(".github.com") || host.endsWith(".jsdelivr.net") || host.endsWith(".githubusercontent.com"))) {
                if (InstallerInitializer.peekMagiskPath() != null) {
                    request.header("User-Agent", // Declare Magisk version to the server
                            "Magisk/" + InstallerInitializer.peekMagiskVersion());
                }
            }
            if (chain.request().header("Accept-Language") == null) {
                request.header("Accept-Language", // Send system language to the server
                        mainApplication.getResources().getConfiguration().getLocales().get(0).toLanguageTag());
            }
            return chain.proceed(request.build());
        });
        // Add cronet interceptor
        // init cronet
        try {
            // Load the cronet library
            CronetEngine.Builder builder = new CronetEngine.Builder(mainApplication);
            builder.enableBrotli(true);
            builder.enableHttp2(true);
            builder.enableQuic(true);
            // Cache size is 10MB
            // Make the directory if it does not exist
            File cacheDir = new File(mainApplication.getCacheDir(), "cronet");
            if (!cacheDir.exists()) {
                if (!cacheDir.mkdirs()) {
                    throw new IOException("Failed to create cronet cache directory");
                }
            }
            builder.setStoragePath(mainApplication.getCacheDir().getAbsolutePath() + "/cronet");
            builder.enableHttpCache(CronetEngine.Builder.HTTP_CACHE_DISK_NO_HTTP, 10 * 1024 * 1024);
            // Add quic hint
            builder.addQuicHint("github.com", 443, 443);
            builder.addQuicHint("githubusercontent.com", 443, 443);
            builder.addQuicHint("jsdelivr.net", 443, 443);
            builder.addQuicHint("androidacy.com", 443, 443);
            builder.addQuicHint("sentry.io", 443, 443);
            CronetEngine engine = builder.build();
            httpclientBuilder.addInterceptor(CronetInterceptor.newBuilder(engine).build());
        } catch (
                Exception e) {
            Timber.e(e, "Failed to init cronet");
            // Gracefully fallback to okhttp
        }
        // add sentry interceptor
        if (MainApplication.isCrashReportingEnabled()) {
            httpclientBuilder.addInterceptor(new SentryOkHttpInterceptor());
        }
        // Fallback DNS cache responses in case request fail but already succeeded once in the past
        fallbackDNS = new FallBackDNS(mainApplication, dns, "github.com", "api.github.com", "raw.githubusercontent.com", "camo.githubusercontent.com", "user-images.githubusercontent.com", "cdn.jsdelivr.net", "img.shields.io", "magisk-modules-repo.github.io", "www.androidacy.com", "api.androidacy.com", "production-api.androidacy.com");
        httpclientBuilder.cookieJar(new CDNCookieJar(cookieManager));
        httpclientBuilder.dns(Dns.SYSTEM);
        httpClient = followRedirects(httpclientBuilder, true).build();
        followRedirects(httpclientBuilder, false).build();
        httpclientBuilder.dns(fallbackDNS);
        httpClientDoH = followRedirects(httpclientBuilder, true).build();
        followRedirects(httpclientBuilder, false).build();
        httpclientBuilder.cache(new Cache(new File(mainApplication.getCacheDir(), "http_cache"), 16L * 1024L * 1024L)); // 16Mib of cache
        httpclientBuilder.dns(Dns.SYSTEM);
        httpClientWithCache = followRedirects(httpclientBuilder, true).build();
        httpclientBuilder.dns(fallbackDNS);
        httpClientWithCacheDoH = followRedirects(httpclientBuilder, true).build();
        Timber.i("Initialized Http successfully!");
        doh = MainApplication.isDohEnabled();
    }

    private static OkHttpClient.Builder followRedirects(OkHttpClient.Builder builder, boolean followRedirects) {
        return builder.followRedirects(followRedirects).followSslRedirects(followRedirects);
    }

    public static OkHttpClient getHttpClient() {
        return doh ? httpClientDoH : httpClient;
    }

    public static OkHttpClient getHttpClientWithCache() {
        return doh ? httpClientWithCacheDoH : httpClientWithCache;
    }

    private static void checkNeedCaptchaAndroidacy(String url, int errorCode) {
        if (errorCode == 403 && AndroidacyUtil.isAndroidacyLink(url)) {
            needCaptchaAndroidacyHost = Uri.parse(url).getHost();
        }
    }

    public static boolean needCaptchaAndroidacy() {
        return needCaptchaAndroidacyHost != null;
    }

    public static String needCaptchaAndroidacyHost() {
        return needCaptchaAndroidacyHost;
    }

    public static void markCaptchaAndroidacySolved() {
        needCaptchaAndroidacyHost = null;
    }

    @SuppressLint("RestrictedApi")
    @SuppressWarnings("resource")
    public static byte[] doHttpGet(String url, boolean allowCache) throws IOException {
        if (BuildConfig.DEBUG_HTTP) {
            // Log, but set all query parameters values to "****" while keeping the keys
            Timber.d("doHttpGet: %s", url.replaceAll("=[^&]*", "=****"));
        }
        Response response;
        try {
            response = (allowCache ? getHttpClientWithCache() : getHttpClient()).newCall(new Request.Builder().url(url).get().build()).execute();
        } catch (IOException e) {
            Timber.e(e, "Failed to fetch %s", url.replaceAll("=[^&]*", "=****"));
            throw new HttpException(e.getMessage(), 0);
        }
        if (BuildConfig.DEBUG_HTTP) {
            Timber.d("doHttpGet: request executed");
        }
        // 200/204 == success, 304 == cache valid
        if (response.code() != 200 && response.code() != 204 && (response.code() != 304 || !allowCache)) {
            Timber.e("Failed to fetch " + url.replaceAll("=[^&]*", "=****") + " with code " + response.code());
            checkNeedCaptchaAndroidacy(url, response.code());
            // If it's a 401, and an androidacy link, it's probably an invalid token
            if (response.code() == 401 && AndroidacyUtil.isAndroidacyLink(url)) {
                // Regenerate the token
                throw new HttpException("Androidacy token is invalid", 401);
            }
            throw new HttpException(response.code());
        }
        if (BuildConfig.DEBUG_HTTP) {
            Timber.d("doHttpGet: " + url.replaceAll("=[^&]*", "=****") + " succeeded");
        }
        ResponseBody responseBody = response.body();
        // Use cache api if used cached response
        if (response.code() == 304) {
            response = response.cacheResponse();
            if (response != null)
                responseBody = response.body();
        }
        if (BuildConfig.DEBUG_HTTP) {
            Timber.d("doHttpGet: returning " + responseBody.contentLength() + " bytes");
        }
        return responseBody.bytes();
    }

    public static byte[] doHttpPost(String url, String data, boolean allowCache) throws IOException {
        return (byte[]) doHttpPostRaw(url, data, allowCache);
    }

    @SuppressWarnings("resource")
    private static Object doHttpPostRaw(String url, String data, boolean allowCache) throws IOException {
        if (BuildConfig.DEBUG)
            Timber.i("POST " + url + " " + data);
        Response response;
        response = (allowCache ? getHttpClientWithCache() : getHttpClient()).newCall(new Request.Builder().url(url).post(JsonRequestBody.from(data)).header("Content-Type", "application/json").build()).execute();
        if (response.isRedirect()) {
            return response.request().url().uri().toString();
        }
        // 200/204 == success, 304 == cache valid
        if (response.code() != 200 && response.code() != 204 && (response.code() != 304 || !allowCache)) {
            if (BuildConfig.DEBUG)
                Timber.e("Failed to fetch " + url + ", code: " + response.code() + ", body: " + response.body().string());
            checkNeedCaptchaAndroidacy(url, response.code());
            throw new HttpException(response.code());
        }
        ResponseBody responseBody = response.body();
        // Use cache api if used cached response
        if (response.code() == 304) {
            response = response.cacheResponse();
            if (response != null)
                responseBody = response.body();
        }
        return responseBody.bytes();
    }

    public static byte[] doHttpGet(String url, ProgressListener progressListener) throws IOException {
        if (BuildConfig.DEBUG)
            Timber.i("GET %s", url.split("\\?")[0]);
        Response response = getHttpClient().newCall(new Request.Builder().url(url).get().build()).execute();
        if (response.code() != 200 && response.code() != 204) {
            Timber.e("Failed to fetch " + url + ", code: " + response.code());
            checkNeedCaptchaAndroidacy(url, response.code());
            throw new HttpException(response.code());
        }
        ResponseBody responseBody = Objects.requireNonNull(response.body());
        InputStream inputStream = responseBody.byteStream();
        byte[] buff = new byte[1024 * 4];
        long downloaded = 0;
        long target = responseBody.contentLength();
        ByteArrayOutputStream byteArrayOutputStream = Files.makeBuffer(target);
        int divider = 1; // Make everything go in an int
        while ((target / divider) > (Integer.MAX_VALUE / 2)) {
            divider *= 2;
        }
        final long UPDATE_INTERVAL = 100;
        long nextUpdate = System.currentTimeMillis() + UPDATE_INTERVAL;
        long currentUpdate;
        Timber.i("Target: " + target + " Divider: " + divider);
        progressListener.onUpdate(0, (int) (target / divider), false);
        while (true) {
            int read = inputStream.read(buff);
            if (read == -1)
                break;
            byteArrayOutputStream.write(buff, 0, read);
            downloaded += read;
            currentUpdate = System.currentTimeMillis();
            if (nextUpdate < currentUpdate) {
                nextUpdate = currentUpdate + UPDATE_INTERVAL;
                progressListener.onUpdate((int) (downloaded / divider), (int) (target / divider), false);
            }
        }
        inputStream.close();
        progressListener.onUpdate((int) (downloaded / divider), (int) (target / divider), true);
        return byteArrayOutputStream.toByteArray();
    }

    public static void cleanDnsCache() {
        if (Http.fallbackDNS != null) {
            Http.fallbackDNS.cleanDnsCache();
        }
    }

    public static String getAndroidacyUA() {
        return androidacyUA;
    }

    public static void setDoh(boolean doh) {
        Timber.i("DoH: " + Http.doh + " -> " + doh);
        Http.doh = doh;
    }

    public static boolean hasWebView() {
        return hasWebView;
    }

    public static void ensureCacheDirs(MainActivity mainActivity) {
        // Recursively ensure cache dirs for webview exist under our cache dir
        File cacheDir = mainActivity.getCacheDir();
        File webviewCacheDir = new File(cacheDir, "WebView");
        if (!webviewCacheDir.exists()) {
            if (!webviewCacheDir.mkdirs()) {
                Timber.e("Failed to create webview cache dir");
            }
        }
        File webviewCacheDirCache = new File(webviewCacheDir, "Default");
        if (!webviewCacheDirCache.exists()) {
            if (!webviewCacheDirCache.mkdirs()) {
                Timber.e("Failed to create webview cache dir");
            }
        }
        File webviewCacheDirCacheCodeCache = new File(webviewCacheDirCache, "HTTP Cache");
        if (!webviewCacheDirCacheCodeCache.exists()) {
            if (!webviewCacheDirCacheCodeCache.mkdirs()) {
                Timber.e("Failed to create webview cache dir");
            }
        }
        File webviewCacheDirCacheCodeCacheIndex = new File(webviewCacheDirCacheCodeCache, "Code Cache");
        if (!webviewCacheDirCacheCodeCacheIndex.exists()) {
            if (!webviewCacheDirCacheCodeCacheIndex.mkdirs()) {
                Timber.e("Failed to create webview cache dir");
            }
        }
        File webviewCacheDirCacheCodeCacheIndexIndex = new File(webviewCacheDirCacheCodeCacheIndex, "Index");
        if (!webviewCacheDirCacheCodeCacheIndexIndex.exists()) {
            if (!webviewCacheDirCacheCodeCacheIndexIndex.mkdirs()) {
                Timber.e("Failed to create webview cache dir");
            }
        }
        // Create the js and wasm dirs
        File webviewCacheDirCacheCodeCacheIndexIndexJs = new File(webviewCacheDirCacheCodeCache, "js");
        if (!webviewCacheDirCacheCodeCacheIndexIndexJs.exists()) {
            if (!webviewCacheDirCacheCodeCacheIndexIndexJs.mkdirs()) {
                Timber.e("Failed to create webview cache dir");
            }
        }
        File webviewCacheDirCacheCodeCacheIndexIndexWasm = new File(webviewCacheDirCacheCodeCache, "wasm");
        if (!webviewCacheDirCacheCodeCacheIndexIndexWasm.exists()) {
            if (!webviewCacheDirCacheCodeCacheIndexIndexWasm.mkdirs()) {
                Timber.e("Failed to create webview cache dir");
            }
        }
    }

    public interface ProgressListener {
        void onUpdate(int downloaded, int total, boolean done);
    }

    /**
     * Cookie jar that allow CDN cookies, reset on app relaunch
     * Note: An argument can be made that it allow tracking but
     * caching is a better attack vector for tracking, this system
     * only exist to improve CDN response time, any other cookies
     * that are not CDN related are just simply ignored.
     * <p>
     * Note: CDNCookies are only stored in RAM unlike https cache
     */
    private static class CDNCookieJar implements CookieJar {
        private final HashMap<String, Cookie> cookieMap = new HashMap<>();
        private final boolean androidacySupport;
        private final CookieManager cookieManager;
        private List<Cookie> androidacyCookies;

        private CDNCookieJar() {
            this.androidacySupport = false;
            this.cookieManager = null;
        }

        private CDNCookieJar(CookieManager cookieManager) {
            this.androidacySupport = true;
            this.cookieManager = cookieManager;
            if (cookieManager == null) {
                this.androidacyCookies = Collections.emptyList();
            }
        }

        @NonNull
        @Override
        public List<Cookie> loadForRequest(@NonNull HttpUrl httpUrl) {
            if (!httpUrl.isHttps())
                return Collections.emptyList();
            if (this.androidacySupport && httpUrl.host().endsWith(".androidacy.com")) {
                if (this.cookieManager == null)
                    return this.androidacyCookies;
                String cookies = this.cookieManager.getCookie(httpUrl.uri().toString());
                if (cookies == null || cookies.isEmpty())
                    return Collections.emptyList();
                String[] splitCookies = cookies.split(";");
                ArrayList<Cookie> cookieList = new ArrayList<>(splitCookies.length);
                for (String cookie : splitCookies) {
                    cookieList.add(Cookie.parse(httpUrl, cookie));
                }
                return cookieList;
            }
            Cookie cookies = cookieMap.get(httpUrl.url().getHost());
            return cookies == null || cookies.expiresAt() < System.currentTimeMillis() ? Collections.emptyList() : Collections.singletonList(cookies);
        }

        @Override
        public void saveFromResponse(@NonNull HttpUrl httpUrl, @NonNull List<Cookie> cookies) {
            if (!httpUrl.isHttps())
                return;
            if (this.androidacySupport && httpUrl.host().endsWith(".androidacy.com")) {
                if (this.cookieManager == null) {
                    if (httpUrl.host().equals(".androidacy.com") || !cookies.isEmpty())
                        this.androidacyCookies = cookies;
                    return;
                }
                for (Cookie cookie : cookies) {
                    this.cookieManager.setCookie(httpUrl.uri().toString(), cookie.toString());
                }
                return;
            }
            String host = httpUrl.url().getHost();
            Iterator<Cookie> cookieIterator = cookies.iterator();
            Cookie cdnCookie = cookieMap.get(host);
            while (cookieIterator.hasNext()) {
                Cookie cookie = cookieIterator.next();
                if (host.equals(cookie.domain()) && cookie.secure() && cookie.httpOnly() && cookie.expiresAt() < (System.currentTimeMillis() + 1000 * 60 * 60 * 48)) {
                    if (cdnCookie != null && !cdnCookie.name().equals(cookie.name())) {
                        cookieMap.remove(host);
                        cdnCookie = null;
                        break;
                    } else {
                        cdnCookie = cookie;
                    }
                }
            }
            if (cdnCookie == null) {
                cookieMap.remove(host);
            } else {
                cookieMap.put(host, cdnCookie);
            }
        }

    }

    /**
     * FallBackDNS store successful DNS request to return them later
     * can help make the app to work later when the current DNS system
     * isn't functional or available.
     * <p>
     * Note: DNS Cache is stored in user data.
     */
    private static class FallBackDNS implements Dns {
        private final Dns parent;
        private final SharedPreferences sharedPreferences;
        private final HashSet<String> fallbacks;
        private final HashMap<String, List<InetAddress>> fallbackCache;

        public FallBackDNS(Context context, Dns parent, String... fallbacks) {
            this.sharedPreferences = context.getSharedPreferences("mmm_dns", Context.MODE_PRIVATE);
            this.parent = parent;
            this.fallbacks = new HashSet<>(Arrays.asList(fallbacks));
            this.fallbackCache = new HashMap<>();
        }

        @NonNull
        private static String toString(@NonNull List<InetAddress> inetAddresses) {
            if (inetAddresses.isEmpty())
                return "";
            Iterator<InetAddress> inetAddressIterator = inetAddresses.iterator();
            StringBuilder stringBuilder = new StringBuilder();
            while (true) {
                stringBuilder.append(inetAddressIterator.next().getHostAddress());
                if (!inetAddressIterator.hasNext())
                    return stringBuilder.toString();
                stringBuilder.append("|");
            }
        }

        @NonNull
        private static List<InetAddress> fromString(@NonNull String string) throws UnknownHostException {
            if (string.isEmpty())
                return Collections.emptyList();
            String[] strings = string.split("\\|");
            ArrayList<InetAddress> inetAddresses = new ArrayList<>(strings.length);
            for (String address : strings) {
                inetAddresses.add(InetAddress.getByName(address));
            }
            return inetAddresses;
        }

        @NonNull
        @Override
        public List<InetAddress> lookup(@NonNull String s) throws UnknownHostException {
            if (this.fallbacks.contains(s)) {
                List<InetAddress> addresses;
                synchronized (this.fallbackCache) {
                    addresses = this.fallbackCache.get(s);
                    if (addresses != null)
                        return addresses;
                    try {
                        addresses = this.parent.lookup(s);
                        if (addresses.isEmpty() || addresses.get(0).isLoopbackAddress())
                            throw new UnknownHostException(s);
                        this.fallbackCache.put(s, addresses);
                        this.sharedPreferences.edit().putString(s.replace('.', '_'), toString(addresses)).apply();
                    } catch (
                            UnknownHostException e) {
                        String key = this.sharedPreferences.getString(s.replace('.', '_'), "");
                        if (key.isEmpty())
                            throw e;
                        try {
                            addresses = fromString(key);
                            this.fallbackCache.put(s, addresses);
                        } catch (
                                UnknownHostException e2) {
                            this.sharedPreferences.edit().remove(s.replace('.', '_')).apply();
                            throw e;
                        }
                    }
                }
                return addresses;
            } else {
                return this.parent.lookup(s);
            }
        }

        void cleanDnsCache() {
            synchronized (this.fallbackCache) {
                this.fallbackCache.clear();
            }
        }
    }

    private static class JsonRequestBody extends RequestBody {
        private static final MediaType JSON_MEDIA_TYPE = MediaType.get("application/json");
        private static final JsonRequestBody EMPTY = new JsonRequestBody(new byte[0]);
        final byte[] data;

        private JsonRequestBody(byte[] data) {
            this.data = data;
        }

        static JsonRequestBody from(String data) {
            if (data == null || data.length() == 0) {
                return EMPTY;
            }
            return new JsonRequestBody(data.getBytes(StandardCharsets.UTF_8));
        }

        @Nullable
        @Override
        public MediaType contentType() {
            return JSON_MEDIA_TYPE;
        }

        @Override
        public long contentLength() {
            return this.data.length;
        }

        @Override
        public void writeTo(@NonNull BufferedSink bufferedSink) throws IOException {
            bufferedSink.write(this.data);
        }
    }
}
