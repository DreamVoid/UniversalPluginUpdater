package me.dreamvoid.universalpluginupdater;

import lombok.Getter;
import lombok.Setter;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.logging.Logger;

/**
 * 实用工具类
 */
public class Utils {
    @Getter
    @Setter
    private static Logger logger;

    public static class Http {
        private static final OkHttpClient client = new OkHttpClient.Builder()
                .followRedirects(true)
                .followSslRedirects(true)
                .build();

        /**
         * HTTP响应缓存对象
         */
        public static class Response {
            public int statusCode;
            public String content;
            public String lastModified;

            public Response(int statusCode, String content, String lastModified) {
                this.statusCode = statusCode;
                this.content = content;
                this.lastModified = lastModified;
            }

            public boolean isNotModified() {
                return statusCode == 304;
            }

            public boolean isSuccess() {
                return statusCode == 200;
            }
        }

        /**
         * 发送HTTP GET请求并返回响应文本
         * @param url 请求URL
         * @return 响应文本（JSON格式）
         */
        public static String get(String url) throws IOException {
            Request request = new Request.Builder().url(url)
                    .header("User-Agent", "UniversalPluginUpdater/1.0")
                    .build();

            try (okhttp3.Response response = client.newCall(request).execute()) {
                return response.isSuccessful() && response.body() != null ? response.body().string() : null;
            }
        }

        /**
         * 发送带有缓存支持的HTTP GET请求
         * @param url 请求URL
         * @param ifModifiedSince 上次修改时间，null或空串表示无缓存，首次请求后可使用 {@link Response#lastModified} 传递
         * @return {@link Response}对象
         */
        public static Response get(String url, @Nullable String ifModifiedSince) throws IOException {
            Request.Builder requestBuilder = new Request.Builder().url(url)
                    .header("User-Agent", "UniversalPluginUpdater/1.0");

            // 如果有缓存时间戳，添加If-Modified-Since头
            if (ifModifiedSince != null && !ifModifiedSince.isEmpty()) {
                requestBuilder.header("If-Modified-Since", ifModifiedSince);
            }

            Request request = requestBuilder.build();

            try (okhttp3.Response response = client.newCall(request).execute()) {
                int code = response.code();

                // 处理304 Not Modified
                if (code == 304) {
                    return new Response(304, null, ifModifiedSince);
                }

                // 处理200 OK
                if (code == 200 && response.body() != null) {
                    String content = response.body().string();

                    // 获取Last-Modified头中的时间
                    String newLastModified = response.header("Last-Modified");
                    return new Response(200, content, newLastModified);
                }

                return new Response(code, null, ifModifiedSince);
            }
        }
    }
}
