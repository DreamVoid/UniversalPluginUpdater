package me.dreamvoid.universalpluginupdater;

import com.google.gson.Gson;
import lombok.Getter;
import lombok.Setter;
import okhttp3.Authenticator;
import okhttp3.Credentials;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.text.MessageFormat;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 实用工具类
 */
public final class Utils {
    private Utils() {}

    @Getter
    @Setter
    @NotNull
    private static Logger logger = Logger.getLogger("UPU");

    public static final Gson gson = new Gson();

    @Nullable
    public static String parseFileName(String pluginId, @Nullable String channelId) {
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

        String channelValue = channelId == null ? "" : channelId;
        String timestamp = String.valueOf(System.currentTimeMillis());

        filename = filename
                .replace("${pluginId}", pluginId == null ? "" : pluginId)
                .replace("${channel}", channelValue)
                .replace("${timestamp}", timestamp)
                .trim();

        return filename.isEmpty() ? null : filename;
    }

    /**
     * 判断新版本是否比旧版本更新（语义化版本比较）<br>
     * 逐段比较点分版本号，数值段按数值比较，非数值段回退字符串比较<br>
     * 预发布段（"-" 之后）与构建元数据不参与比较
     * @param newVersion 新版本
     * @param oldVersion 旧版本
     * @return 新版本比旧版本新时返回 true
     */
    public static boolean isVersionNewer(@Nullable String newVersion, @Nullable String oldVersion) {
        if (oldVersion == null || newVersion == null) {
            return !Objects.equals(oldVersion, newVersion);
        }

        String[] currentParts = oldVersion.split("-")[0].split("\\.");
        String[] newParts = newVersion.split("-")[0].split("\\.");

        int max = Math.max(currentParts.length, newParts.length);
        for (int i = 0; i < max; i++) {
            String currentPart = i < currentParts.length ? currentParts[i].trim() : "0";
            String newPart = i < newParts.length ? newParts[i].trim() : "0";

            try {
                int currentValue = Integer.parseInt(currentPart);
                int newValue = Integer.parseInt(newPart);

                if (newValue > currentValue) {
                    return true;
                } else if (newValue < currentValue) {
                    return false;
                }
            } catch (NumberFormatException e) {
                if (!currentPart.equals(newPart)) {
                    return true;
                }
            }
        }

        return false;
    }

    public static class Http {
        private static final String USER_AGENT = MessageFormat.format("UniversalPluginUpdater/{0} ({1})", BuildConstants.VERSION, System.getProperty("os.name"));
        private static final OkHttpClient defaultClient = new OkHttpClient.Builder()
                .followRedirects(true)
                .followSslRedirects(true)
                .connectTimeout(Duration.ofSeconds(10))
                .readTimeout(Duration.ofSeconds(15))
                .writeTimeout(Duration.ofSeconds(15))
                .build();

        private static final Object CLIENT_LOCK = new Object();
        private static OkHttpClient client = defaultClient;
        private static OkHttpClient downloadClient = defaultClient.newBuilder()
                .readTimeout(Duration.ofSeconds(60))
                .writeTimeout(Duration.ofSeconds(30))
                .build();
        private static String clientProxyUri = "";
        private static String clientProxyUsername = "";
        private static String clientProxyPassword = "";

        /**
         * HTTP 缓存验证令牌
         * @param value 令牌内容
         * @param etag 为 true 表示 ETag（If-None-Match），否则表示 Last-Modified（If-Modified-Since）
         */
        public record CacheToken (String value, boolean etag) { }

        /**
         * HTTP响应缓存对象
         */
        public record Response (
                int statusCode,
                @Nullable String content,
                @Nullable CacheToken cacheToken
        ){ }

        /**
         * 发送HTTP GET请求并返回响应文本
         * @param url 请求URL
         * @return 响应文本（JSON格式）
         */
        public static String get(String url) throws IOException {
            return get(url, null).content();
        }

        /**
         * 发送带有缓存支持的HTTP GET请求
         * @param url 请求URL
         * @param cacheToken 缓存验证令牌，null 表示无缓存，首次请求后可使用 {@link Response#cacheToken} 传递
         * @return {@link Response}对象
         */
        public static Response get(String url, @Nullable CacheToken cacheToken) throws IOException {
            return get(url, cacheToken, null);
        }

        /**
         * 发送带有缓存支持的HTTP GET请求
         * @param url 请求URL
         * @param cacheToken 缓存验证令牌，null 表示无缓存，首次请求后可使用 {@link Response#cacheToken} 传递
         * @param authorization Authorization 标头，为null或空串表示不附带
         * @return {@link Response}对象
         */
        public static Response get(String url, @Nullable CacheToken cacheToken, @Nullable String authorization) throws IOException {
            OkHttpClient httpClient = getClient(false);
            Request.Builder requestBuilder = new Request.Builder().url(url)
                    .header("User-Agent", USER_AGENT);

            if (authorization != null && !authorization.isBlank()) {
                requestBuilder.header("Authorization", authorization);
            }

            if (cacheToken != null && cacheToken.value() != null && !cacheToken.value().isEmpty()) {
                if (cacheToken.etag()) {
                    requestBuilder.header("If-None-Match", cacheToken.value());
                } else {
                    requestBuilder.header("If-Modified-Since", cacheToken.value());
                }
            }

            Request request = requestBuilder.build();

            try (okhttp3.Response response = httpClient.newCall(request).execute()) {
                int code = response.code();
                String content = null;

                if (response.body() != null) {
                    content = response.body().string();
                }

                CacheToken newCacheToken = cacheToken;
                String etag = response.header("ETag");
                String lastModified = response.header("Last-Modified");
                if (etag != null && !etag.isBlank()) {
                    newCacheToken = new CacheToken(etag, true);
                } else if (lastModified != null && !lastModified.isBlank()) {
                    newCacheToken = new CacheToken(lastModified, false);
                }

                return new Response(code, content, newCacheToken);
            }
        }

        /**
         * 下载文件到指定目录
         * @param url 文件URL
         * @param saveDir 目标目录
         * @param filename 期望的文件名，作为服务器未提供文件名时的回退值
         * @param forceFilename 为 true 时始终使用 filename，为 false 时优先使用服务器返回的文件名
         * @return {@link DownloadResult}对象
         */
        public static DownloadResult download(String url, Path saveDir, @Nullable String filename, boolean forceFilename) throws IOException {
            // 确保目标目录存在
            Files.createDirectories(saveDir);
            OkHttpClient httpClient = getClient(true);

            Request request = new Request.Builder().url(url)
                    .header("User-Agent", USER_AGENT)
                    .build();

            try (okhttp3.Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) {
                    return new DownloadResult(false, null, "HTTP " + response.code());
                }

                // 确定文件名
                String expectedName = Optional.ofNullable(filename).filter(s -> !s.isBlank()).orElse(null);
                String serverName = Optional.ofNullable(response.header("Content-Disposition"))
                        .filter(h -> h.contains("filename"))
                        .map(Http::extractFilenameFromContentDisposition)
                        .filter(s -> !s.isBlank())
                        .orElse(null);

                String resolvedName = forceFilename ? expectedName : serverName;
                if (resolvedName == null || resolvedName.isBlank()) {
                    resolvedName = forceFilename ? serverName : expectedName;
                }
                if (resolvedName == null || resolvedName.isBlank()) {
                    resolvedName = extractFilenameFromUrl(url);
                }
                if (resolvedName == null || resolvedName.isBlank()) {
                    resolvedName = "download-" + System.currentTimeMillis();
                }

                // 净化文件名，防止路径穿越（如 "../../evil.jar"、绝对路径、路径分隔符等）
                String safeName;
                try {
                    safeName = Path.of(resolvedName.replace('\\', '/')).getFileName().toString().trim();
                } catch (InvalidPathException e) {
                    safeName = resolvedName.replace('\\', '/');
                    int slash = safeName.lastIndexOf('/');
                    safeName = (slash >= 0 ? safeName.substring(slash + 1) : safeName)
                            .replaceAll("[\\\\/:*?\"<>|]", "").trim();
                }
                while (safeName.startsWith(".")) {
                    safeName = safeName.substring(1);
                }
                if (safeName.isBlank()) {
                    safeName = "download-" + System.currentTimeMillis();
                }

                // 构建完整的文件路径
                Path filePath = saveDir.toAbsolutePath().normalize().resolve(safeName).normalize();
                // 二次校验，确保最终路径仍位于目标目录内
                if (!filePath.startsWith(saveDir.toAbsolutePath().normalize())) {
                    return new DownloadResult(false, null, "Invalid filename");
                }

                // 先写入临时文件，成功后再原子移动到目标位置，避免中断时留下半成品
                Path partPath = filePath.resolveSibling(filePath.getFileName().toString() + ".part");
                try (InputStream inputStream = response.body().byteStream()) {
                    Files.copy(inputStream, partPath, StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException e) {
                    Files.deleteIfExists(partPath);
                    throw e;
                }

                try {
                    Files.move(partPath, filePath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException e) {
                    Files.move(partPath, filePath, StandardCopyOption.REPLACE_EXISTING);
                }

                return new DownloadResult(true, safeName, null);
            }
        }

        private static OkHttpClient getClient(boolean forDownload) {
            synchronized (CLIENT_LOCK) {
                String proxyUri = trimToEmpty(Config.Updater_Proxy_Uri);
                String username = trimToEmpty(Config.Updater_Proxy_Username);
                String password = trimToEmpty(Config.Updater_Proxy_Password);

                if (!Objects.equals(proxyUri, clientProxyUri)
                        || !Objects.equals(username, clientProxyUsername)
                        || !Objects.equals(password, clientProxyPassword)) {
                    OkHttpClient.Builder builder = defaultClient.newBuilder();
                    Proxy proxy = proxyUri.isEmpty() ? null : createProxy(proxyUri);
                    if (proxy != null) {
                        builder.proxy(proxy);
                        if (!username.isEmpty() && !password.isEmpty()) {
                            builder.proxyAuthenticator(proxyAuthenticator(username, password));
                        }
                    }

                    client = builder.build();
                    downloadClient = client.newBuilder()
                            .readTimeout(Duration.ofSeconds(60))
                            .writeTimeout(Duration.ofSeconds(30))
                            .build();
                    clientProxyUri = proxyUri;
                    clientProxyUsername = username;
                    clientProxyPassword = password;
                }

                return forDownload ? downloadClient : client;
            }
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
            // 优先解析 RFC 5987 的 filename*，其值可携带非 ASCII 文件名
            Matcher extended = Pattern.compile("filename\\*\\s*=\\s*([^;]+)", Pattern.CASE_INSENSITIVE).matcher(contentDisposition);
            if (extended.find()) {
                String value = extended.group(1).trim();
                if (value.length() > 1 && value.startsWith("\"") && value.endsWith("\"")) {
                    value = value.substring(1, value.length() - 1);
                }

                int charsetEnd = value.indexOf('\'');
                int encodedStart = charsetEnd < 0 ? -1 : value.indexOf('\'', charsetEnd + 1);
                if (encodedStart > charsetEnd) {
                    String charsetName = value.substring(0, charsetEnd).trim();
                    String encoded = value.substring(encodedStart + 1);

                    Charset charset;
                    try {
                        charset = charsetName.isEmpty() ? StandardCharsets.UTF_8 : Charset.forName(charsetName);
                    } catch (Exception ignored) {
                        charset = StandardCharsets.UTF_8;
                    }

                    try {
                        return URLDecoder.decode(encoded, charset);
                    } catch (Exception ignored) {
                        return encoded;
                    }
                }

                if (!value.isEmpty()) {
                    return value;
                }
            }

            // 回退到普通的 filename 参数
            Matcher plain = Pattern.compile("filename\\s*=\\s*(\"([^\"]*)\"|[^;]+)", Pattern.CASE_INSENSITIVE).matcher(contentDisposition);
            if (!plain.find()) {
                return null;
            }

            String value = (plain.group(2) != null ? plain.group(2) : plain.group(1)).trim();
            if (value.length() > 1 && value.startsWith("\"") && value.endsWith("\"")) {
                value = value.substring(1, value.length() - 1);
            }

            return value.isEmpty() ? null : value;
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
        Level level = Config.Verbose ? Level.INFO : Level.FINE;
        if (!logger.isLoggable(level)) {
            return;
        }
        logger.log(level, "[DEBUG] " + MessageFormat.format(message, args));
    }

    public static boolean findClass(String className) {
        try{
            Class.forName(className);
            return true;
        } catch (ClassNotFoundException e){
            return false;
        }
    }
}
