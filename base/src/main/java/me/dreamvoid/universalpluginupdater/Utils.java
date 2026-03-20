package me.dreamvoid.universalpluginupdater;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.util.logging.Logger;

public class Utils {
    private static Logger logger;

    public static void setLogger(Logger logger) {
        Utils.logger = logger;
    }

    public static Logger getLogger() {
        return logger;
    }

    public static class Http {
        private static final OkHttpClient client = new OkHttpClient.Builder()
                .followRedirects(true)
                .followSslRedirects(true)
                .build();

        /**
         * HTTP响应缓存对象，用于支持304 Not Modified
         */
        public static class CacheResponse {
            public int statusCode;
            public String content;
            public String lastModified;

            public CacheResponse(int statusCode, String content, String lastModified) {
                this.statusCode = statusCode;
                this.content = content;
                this.lastModified = lastModified;
            }

            public boolean isNotModified() {
                return statusCode == 304;
            }

            public boolean isSuccessful() {
                return statusCode == 200;
            }
        }

        /**
         * 发送HTTP GET请求并返回响应文本
         * @param url 请求URL
         * @return 响应文本（JSON格式）
         */
        public static String get(String url) {
            try {
                Request request = new Request.Builder()
                        .url(url)
                        .header("User-Agent", "UniversalPluginUpdater/1.0")
                        .build();

                try (Response response = client.newCall(request).execute()) {
                    if (response.isSuccessful() && response.body() != null) {
                        return response.body().string();
                    }
                    return null;
                }
            } catch (Exception e) {
                if (logger != null) {
                    logger.warning("HTTP GET request failed for URL: " + url);
                }
                return null;
            }
        }

        /**
         * 发送带有缓存支持的HTTP GET请求
         * 使用If-Modified-Since头检测304 Not Modified响应
         * @param url 请求URL
         * @param ifModifiedSince 上次修改时间戳（毫秒），-1表示无缓存
         * @return CacheResponse对象
         */
        public static CacheResponse getWithCache(String url, String ifModifiedSince) {
            try {
                Request.Builder requestBuilder = new Request.Builder()
                        .url(url)
                        .header("User-Agent", "UniversalPluginUpdater/1.0");

                // 如果有缓存时间戳，添加If-Modified-Since头
                if (ifModifiedSince != null) {
                    requestBuilder.header("If-Modified-Since", ifModifiedSince);
                }

                Request request = requestBuilder.build();

                try (Response response = client.newCall(request).execute()) {
                    int code = response.code();
                    
                    // 处理304 Not Modified
                    if (code == 304) {
                        return new CacheResponse(304, null, ifModifiedSince);
                    }
                    
                    // 处理200 OK
                    if (code == 200 && response.body() != null) {
                        String content = response.body().string();
                        
                        // 获取Last-Modified头中的时间
                        String newLastModified = response.header("Last-Modified");
                        return new CacheResponse(200, content, newLastModified);
                    }
                    
                    return new CacheResponse(code, null, ifModifiedSince);
                }
            } catch (Exception e) {
                if (logger != null) {
                    logger.warning("HTTP GET request failed for URL: " + url);
                }
                return new CacheResponse(0, null, ifModifiedSince);
            }
        }
    }
}
