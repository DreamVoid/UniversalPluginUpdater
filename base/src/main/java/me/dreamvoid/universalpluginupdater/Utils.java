package me.dreamvoid.universalpluginupdater;

import com.google.gson.Gson;
import lombok.Getter;
import lombok.Setter;
import me.dreamvoid.universalpluginupdater.update.UpdateType;
import okhttp3.Authenticator;
import okhttp3.Credentials;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.text.MessageFormat;
import java.util.Locale;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 实用工具类
 */
public final class Utils {
    private Utils() {}

    @Getter
    @Setter
    private static Logger logger = Logger.getLogger("UPU");

    @Getter
    private static final Gson gson = new Gson();

    @Nullable
    public static String parseFileName(String pluginId, @Nullable UpdateType channel) {
        String template = Config.Updater_Filename;
        if (template == null) {
            return null;
        }

        String filename = template.trim();
        if (filename.isEmpty()) {
            return null;
        }

        if (filename.contains("${originName}")) {
            return null;
        }

        String channelValue = channel == null ? "" : channel.name().toLowerCase();
        String timestamp = String.valueOf(System.currentTimeMillis());

        filename = filename
                .replace("${pluginId}", pluginId == null ? "" : pluginId)
                .replace("${channel}", channelValue)
                .replace("{$timestamp}", timestamp)
                .replace("${timestamp}", timestamp)
                .trim();

        return filename.isEmpty() ? null : filename;
    }

    public static class Http {
        private static final OkHttpClient defaultClient = new OkHttpClient.Builder()
                .followRedirects(true)
                .followSslRedirects(true)
                .build();
        private static volatile OkHttpClient client = defaultClient;
        private static volatile String clientProxyUri = "";
        private static volatile String clientProxyUsername = "";
        private static volatile String clientProxyPassword = "";

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
            OkHttpClient httpClient = getClient();
            Request request = new Request.Builder().url(url)
                    .header("User-Agent", "UniversalPluginUpdater/1.0")
                    .build();

            try (okhttp3.Response response = httpClient.newCall(request).execute()) {
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
            OkHttpClient httpClient = getClient();
            Request.Builder requestBuilder = new Request.Builder().url(url)
                    .header("User-Agent", "UniversalPluginUpdater/1.0");

            // 如果有缓存时间戳，添加If-Modified-Since头
            if (ifModifiedSince != null && !ifModifiedSince.isEmpty()) {
                requestBuilder.header("If-Modified-Since", ifModifiedSince);
            }

            Request request = requestBuilder.build();

            try (okhttp3.Response response = httpClient.newCall(request).execute()) {
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

        /**
         * 下载文件到指定目录
         * @param url 文件URL
         * @param saveDir 目标目录
         * @param filename 期望的文件名，如果为null则尝试从服务器获取文件名
         * @return {@link DownloadResult}对象
         */
        public static DownloadResult download(String url, Path saveDir, @Nullable String filename) throws IOException {
            // 确保目标目录存在
            Files.createDirectories(saveDir);
            OkHttpClient httpClient = getClient();

            Request request = new Request.Builder().url(url)
                    .header("User-Agent", "UniversalPluginUpdater/1.0")
                    .build();

            try (okhttp3.Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) {
                    return new DownloadResult(false, null, "HTTP " + response.code());
                }

                // 确定文件名
                if (filename == null || filename.isEmpty()) {
                    // 尝试从Content-Disposition头获取文件名
                    String contentDisposition = response.header("Content-Disposition");
                    if (contentDisposition != null && contentDisposition.contains("filename=")) {
                        filename = extractFilenameFromContentDisposition(contentDisposition);
                    }

                    // 如果无法从头获取，尝试从URL路径提取
                    if (filename == null || filename.isEmpty()) {
                        filename = extractFilenameFromUrl(url);
                    }

                    // 如果仍然无法获取文件名，使用兜底名称
                    if (filename == null || filename.isEmpty()) {
                        filename = "download-" + System.currentTimeMillis();
                    }
                }

                filename = filename.trim();

                // 构建完整的文件路径
                Path filePath = saveDir.resolve(filename);

                // 下载文件
                try (InputStream inputStream = response.body().byteStream()) {
                    Files.copy(inputStream, filePath);
                }

                return new DownloadResult(true, filename, null);
            }
        }

        private static OkHttpClient getClient() {
            String proxyUri = trimToEmpty(Config.Updater_Proxy_Uri);
            String username = trimToEmpty(Config.Updater_Proxy_Username);
            String password = trimToEmpty(Config.Updater_Proxy_Password);

            if (proxyUri.isEmpty()) {
                client = defaultClient;
                clientProxyUri = "";
                clientProxyUsername = "";
                clientProxyPassword = "";
                return client;
            }

            if (Objects.equals(proxyUri, clientProxyUri)
                    && Objects.equals(username, clientProxyUsername)
                    && Objects.equals(password, clientProxyPassword)) {
                return client;
            }

            Proxy proxy = createProxy(proxyUri);
            if (proxy == null) {
                client = defaultClient;
                clientProxyUri = "";
                clientProxyUsername = "";
                clientProxyPassword = "";
                return client;
            }

            OkHttpClient.Builder builder = defaultClient.newBuilder().proxy(proxy);
            if (!username.isEmpty() && !password.isEmpty()) {
                builder.proxyAuthenticator(proxyAuthenticator(username, password));
            }

            client = builder.build();
            clientProxyUri = proxyUri;
            clientProxyUsername = username;
            clientProxyPassword = password;
            return client;
        }

        private static Authenticator proxyAuthenticator(String username, String password) {
            return (route, response) -> {
                String credential = Credentials.basic(username, password);
                return response.request().newBuilder()
                        .header("Proxy-Authorization", credential)
                        .build();
            };
        }

        @Nullable
        private static Proxy createProxy(String proxyUri) {
            try {
                URI uri = URI.create(proxyUri);
                String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase();
                String host = uri.getHost();
                int port = uri.getPort();

                if ((host == null || host.isBlank()) && uri.getAuthority() != null) {
                    String authority = uri.getAuthority();
                    int split = authority.lastIndexOf(':');
                    if (split > 0) {
                        host = authority.substring(0, split);
                        port = Integer.parseInt(authority.substring(split + 1));
                    } else {
                        host = authority;
                    }
                }

                if (host == null || host.isBlank()) {
                    return null;
                }

                if (port <= 0) {
                    return null;
                }

                Proxy.Type type = switch (scheme) {
                    case "http", "https" -> Proxy.Type.HTTP;
                    case "socks4", "socks4a", "socks5", "socks" -> Proxy.Type.SOCKS;
                    default -> null;
                };

                if (type == null) {
                    return null;
                }

                return new Proxy(type, new InetSocketAddress(host, port));
            } catch (Exception e) {
                return null;
            }
        }

        private static String trimToEmpty(@Nullable String text) {
            return text == null ? "" : text.trim();
        }

        /**
         * 从Content-Disposition头提取文件名
         */
        private static String extractFilenameFromContentDisposition(String contentDisposition) {
            // 处理 filename*=UTF-8''filename 和 filename="filename" 的格式
            int filenameIndex = contentDisposition.indexOf("filename");
            if (filenameIndex == -1) {
                return null;
            }

            String filename = contentDisposition.substring(filenameIndex);
            // 移除 filename= 或 filename*= 的前缀
            if (filename.startsWith("filename*=")) {
                filename = filename.substring(10);
            } else if (filename.startsWith("filename=")) {
                filename = filename.substring(9);
            }

            // 移除引号
            if (filename.startsWith("\"") && filename.endsWith("\"")) {
                filename = filename.substring(1, filename.length() - 1);
            }

            // 移除RFC 5987编码前缀 (UTF-8'' 等)
            if (filename.contains("''")) {
                filename = filename.substring(filename.indexOf("''") + 2);
            }

            return filename.isEmpty() ? null : filename;
        }

        /**
         * 从URL路径提取文件名
         */
        private static String extractFilenameFromUrl(String url) {
            try {
                // 移除查询参数
                String path = url.split("\\?")[0];
                // 获取最后一个 / 之后的部分
                int lastSlashIndex = path.lastIndexOf('/');
                if (lastSlashIndex != -1 && lastSlashIndex < path.length() - 1) {
                    return path.substring(lastSlashIndex + 1);
                }
            } catch (Exception ignored) {}
            return null;
        }

        /**
         * 下载结果对象
         *
         */
                public static final class DownloadResult {
            private final boolean success;
            private final String filename;
            private final String errorMessage;

            /**
             * @param filename     下载成功时为实际文件名，失败时为null
             * @param errorMessage 错误信息，成功时为null
             */
            private DownloadResult(boolean success, String filename, String errorMessage) {
                this.success = success;
                this.filename = filename;
                this.errorMessage = errorMessage;
            }

            public boolean success() {
                return success;
            }

            public String filename() {
                return filename;
            }

            public String errorMessage() {
                return errorMessage;
            }

            @Override
            public boolean equals(Object obj) {
                if (obj == this) return true;
                if (obj == null || obj.getClass() != this.getClass()) return false;
                var that = (DownloadResult) obj;
                return this.success == that.success &&
                        Objects.equals(this.filename, that.filename) &&
                        Objects.equals(this.errorMessage, that.errorMessage);
            }

            @Override
            public int hashCode() {
                return Objects.hash(success, filename, errorMessage);
            }

            @Override
            public String toString() {
                return "DownloadResult[" +
                        "success=" + success + ", " +
                        "filename=" + filename + ", " +
                        "errorMessage=" + errorMessage + ']';
            }
        }
    }

    /**
     * 文件相关的工具方法
     */
    public static class File {
        /**
         * 计算文件的哈希值
         * @param filePath 文件路径
         * @param algorithm 哈希算法（如 "SHA-256", "SHA-1", "SHA-512"）
         * @return 十六进制的哈希字符串
         */
        public static String calculateHash(Path filePath, String algorithm) throws Exception {
            MessageDigest digest = MessageDigest.getInstance(algorithm);
            byte[] buffer = new byte[8192];
            int bytesRead;

            try (InputStream fis = Files.newInputStream(filePath)) {
                while ((bytesRead = fis.read(buffer)) != -1) {
                    digest.update(buffer, 0, bytesRead);
                }
            }

            byte[] hashBytes = digest.digest();
            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        }

        /**
         * 验证文件哈希值
         * @param filePath 文件路径
         * @param algorithm 哈希算法
         * @param expectedHash 期望的哈希值（十六进制，不区分大小写）
         * @return 是否匹配
         */
        public static boolean verifyHash(Path filePath, String algorithm, String expectedHash) {
            try {
                String actualHash = calculateHash(filePath, algorithm);
                return actualHash.equalsIgnoreCase(expectedHash);
            } catch (Exception e) {
                return false;
            }
        }
    }

    public static void debug(String message, Object... args) {
        logger.log((Config.Verbose ? Level.INFO : Level.FINE), MessageFormat.format(message, args));
    }
}
