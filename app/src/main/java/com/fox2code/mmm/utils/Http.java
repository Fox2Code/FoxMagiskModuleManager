package com.fox2code.mmm.utils;

import android.util.Log;

import androidx.annotation.NonNull;

import com.fox2code.mmm.MainApplication;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import okhttp3.Cache;
import okhttp3.Cookie;
import okhttp3.CookieJar;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okhttp3.dnsoverhttps.DnsOverHttps;

public class Http {
    private static final OkHttpClient httpClient;
    private static final OkHttpClient httpClientCachable;

    static {
        OkHttpClient.Builder httpclientBuilder = new OkHttpClient.Builder();
        httpclientBuilder.connectTimeout(11, TimeUnit.SECONDS);
        try {
            httpclientBuilder.dns(new DnsOverHttps.Builder().client(httpclientBuilder.build()).url(
                    Objects.requireNonNull(HttpUrl.parse("https://cloudflare-dns.com/dns-query")))
                    .bootstrapDnsHosts(
                            InetAddress.getByName("162.159.36.1"),
                            InetAddress.getByName("162.159.46.1"),
                            InetAddress.getByName("1.1.1.1"),
                            InetAddress.getByName("1.0.0.1"),
                            InetAddress.getByName("162.159.132.53"),
                            InetAddress.getByName("2606:4700:4700::1111"),
                            InetAddress.getByName("2606:4700:4700::1001"),
                            InetAddress.getByName("2606:4700:4700::0064"),
                            InetAddress.getByName("2606:4700:4700::6400")
                    ).resolvePrivateAddresses(true).build());
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }
        httpClient = httpclientBuilder.build();
        MainApplication mainApplication = MainApplication.getINSTANCE();
        if (mainApplication != null) {
            httpclientBuilder.cache(new Cache(
                    new File(mainApplication.getCacheDir(), "http_cache"),
                    1024L * 1024L)); // 1Mo of cache
            httpclientBuilder.cookieJar(new CDNCookieJar());
            httpClientCachable = httpclientBuilder.build();
        } else {
            httpClientCachable = httpClient;
        }
    }

    public static OkHttpClient getHttpclientNoCache() {
        return httpClient;
    }

    public static OkHttpClient getHttpclientWithCache() {
        return httpClientCachable;
    }

    private static Request.Builder makeRequestBuilder() {
        return new Request.Builder().header("Connection", "keep-alive")
                .header("Upgrade-Insecure-Requests", "1");
    }

    public static byte[] doHttpGet(String url,boolean allowCache) throws IOException {
        Response response = (allowCache ? httpClientCachable : httpClient).newCall(
                makeRequestBuilder().url(url).get().build()
        ).execute();
        // 200 == success, 304 == cache valid
        if (response.code() != 200 && (response.code() != 304 || !allowCache)) {
            throw new IOException("Received error code: "+ response.code());
        }
        ResponseBody responseBody = response.body();
        // Use cache api if used cached response
        if (responseBody == null && response.code() == 304) {
            response = response.cacheResponse();
            if (response != null)
                responseBody = response.body();
        }
        return responseBody == null ? new byte[0] : responseBody.bytes();
    }

    public static byte[] doHttpGet(String url,ProgressListener progressListener) throws IOException {
        Log.d("Http", "Progress URL: " + url);
        Response response = httpClient.newCall(makeRequestBuilder().url(url).get().build()).execute();
        if (response.code() != 200) {
            throw new IOException("Received error code: "+ response.code());
        }
        ResponseBody responseBody = Objects.requireNonNull(response.body());
        InputStream inputStream = responseBody.byteStream();
        byte[] buff = new byte[1024 * 4];
        long downloaded = 0;
        long target = responseBody.contentLength();
        ByteArrayOutputStream byteArrayOutputStream =
                new ByteArrayOutputStream();
        int divider = 1; // Make everything go in an int
        while ((target / divider) > (Integer.MAX_VALUE / 2)) {
            divider *= 2;
        }
        final long UPDATE_INTERVAL = 100;
        long nextUpdate = System.currentTimeMillis() + UPDATE_INTERVAL;
        long currentUpdate;
        Log.d("Http", "Target: " + target + " Divider: " + divider);
        progressListener.onUpdate(0, (int) (target / divider), false);
        while (true) {
            int read = inputStream.read(buff);
            if(read == -1) break;
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

    /**
     * Cookie jar that allow CDN cookies, reset on app relaunch
     * Note: An argument can be made that it allow tracking but
     * caching is a better attack vector for tracking, this system
     * only exist to help CDN and cache to work together.
     * */
    private static class CDNCookieJar implements CookieJar {
        private final HashMap<String, Cookie> cookieMap = new HashMap<>();

        @NonNull
        @Override
        public List<Cookie> loadForRequest(@NonNull HttpUrl httpUrl) {
            if (!httpUrl.isHttps()) return Collections.emptyList();
            Cookie cookies = cookieMap.get(httpUrl.url().getHost());
            return cookies == null || cookies.expiresAt() < System.currentTimeMillis() ?
                    Collections.emptyList() : Collections.singletonList(cookies);
        }

        @Override
        public void saveFromResponse(@NonNull HttpUrl httpUrl, @NonNull List<Cookie> cookies) {
            if (!httpUrl.isHttps()) return;
            String host = httpUrl.url().getHost();
            Iterator<Cookie> cookieIterator = cookies.iterator();
            Cookie cdnCookie = cookieMap.get(host);
            while (cookieIterator.hasNext()) {
                Cookie cookie = cookieIterator.next();
                if (host.equals(cookie.domain()) && cookie.secure() && cookie.httpOnly() &&
                        cookie.expiresAt() < (System.currentTimeMillis() + 1000 * 60 * 60 * 48)) {
                    if (cdnCookie != null &&
                            !cdnCookie.name().equals(cookie.name())) {
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

    public interface ProgressListener {
        void onUpdate(int downloaded,int total, boolean done);
    }
}
