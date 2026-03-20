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

        private static final String DEFAULT_USER_AGENT = "UniversalPluginUpdater/1.0";

        /**
         * 发送HTTP GET请求并返回响应文本
         * @param url 请求URL
         * @return 响应文本（JSON格式）
         */
        public static String get(String url) {
            return get(url, DEFAULT_USER_AGENT);
        }

        /**
         * 发送HTTP GET请求并返回响应文本
         * @param url 请求URL
         * @param userAgent 自定义User-Agent
         * @return 响应文本（JSON格式）
         */
        public static String get(String url, String userAgent) {
            try {
                Request request = new Request.Builder()
                        .url(url)
                        .header("User-Agent", userAgent)
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
    }
}
