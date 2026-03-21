package me.dreamvoid.universalpluginupdater.update;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import me.dreamvoid.universalpluginupdater.Utils;
import me.dreamvoid.universalpluginupdater.platform.IPlatformProvider;

import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public class URLUpdate extends AbstractUpdate {
    private static final Gson gson = new Gson();

    private final String updateUrl;
    private final IPlatformProvider platform;
    private UpdateInfo updateInfo;

    private String lastModified;

    public URLUpdate(String updateUrl, IPlatformProvider platform) {
        this.updateType = UpdateType.URL;
        this.updateUrl = updateUrl;
        this.platform = platform;
    }

    /**
     * 从远程URL获取更新信息
     * 使用HTTP缓存机制减少网络请求
     * 返回的JSON格式应为：
     * {
     *     "version": "string（必选）",
     *     "versionCode": "int（可选）",
     *     "downloadUrl": "url（必选）",
     *     "changelog": "url（可选）"
     * }
     */
    @Override
    public boolean update() {
        try {
            Utils.Http.Response response = Utils.Http.get(updateUrl, lastModified);

            if (response.isNotModified()) {
                // 返回304 Not Modified，使用缓存
                if (updateInfo == null) {
                    return false;
                }
                this.lastModified = response.lastModified;
                return true;
            }

            if (response.isSuccess()) {
                String jsonResponse = response.content;
                if (jsonResponse == null) {
                    return false;
                }

                this.updateInfo = gson.fromJson(jsonResponse, UpdateInfo.class);
                this.lastModified = response.lastModified;

                if (updateInfo == null || updateInfo.version == null || updateInfo.downloadUrl == null) {
                    this.updateInfo = null;
                    return false;
                }

                return true;
            }

            return false;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public String getCachedVersion() {
        return updateInfo != null ? updateInfo.version : null;
    }

    @Override
    public boolean download() {
        try {
            // 从缓存的更新信息中获取下载链接
            if (updateInfo == null || updateInfo.downloadUrl == null) {
                return false;
            }

            String downloadUrl = updateInfo.downloadUrl;
            String preferredHash = updateInfo.getPreferredHash();
            String hashAlgorithm = updateInfo.getPreferredHashAlgorithm();

            // TODO: 从配置文件中读取自定义文件名配置

            // 获取数据目录下的downloads文件夹
            Path downloadDir = platform.getDataPath().resolve("downloads");

            // 首先尝试获取文件名（从URL或其他地方）
            // 此处我们让Utils自动从Content-Disposition或URL路径提取
            // 第一次下载时，我们不知道最终的文件名，所以先执行一次下载获取文件名
            Utils.Http.DownloadResult result = Utils.Http.downloadFile(downloadUrl, downloadDir, null);

            if (!result.success) {
                return false;
            }

            Path filePath = downloadDir.resolve(result.filename);

            // 验证下载文件的完整性
            if (preferredHash != null && hashAlgorithm != null) {
                if (Utils.File.verifyHash(filePath, hashAlgorithm, preferredHash)) {
                    return true;
                } else {
                    // 删除不完整的文件
                    Files.delete(filePath);
                    return false;
                }
            } else {
                // 没有哈希值，直接返回成功
                return true;
            }
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public boolean upgrade() {
        return false;
    }

    /**
     * 获取缓存的版本代码（若存在）
     */
    public Integer getCachedVersionCode() {
        return updateInfo != null ? updateInfo.versionCode : null;
    }

    /**
     * 获取缓存的更新日志链接（若存在）
     */
    public URL getCachedChangelogLink() {
        if (updateInfo != null && updateInfo.changelog != null) {
            try {
                return new URI(updateInfo.changelog).toURL();
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }

    /**
     * 内部类：表示更新信息JSON结构
     */
    private static class UpdateInfo {
        @SerializedName("version")
        private String version;

        @SerializedName("versionCode")
        private Integer versionCode;

        @SerializedName("downloadUrl")
        private String downloadUrl;

        @SerializedName("changelog")
        private String changelog;

        @SerializedName("hashes")
        private Map<String, String> hashes;  // 哈希值映射，如 {"sha256": "...", "sha1": "..."}

        /**
         * 获取文件哈希值
         * 优先级：sha256 > sha1 > sha512 > md5
         */
        public String getPreferredHash() {
            if (hashes == null || hashes.isEmpty()) {
                return null;
            }
            if (hashes.containsKey("sha256")) {
                return hashes.get("sha256");
            } else if (hashes.containsKey("sha1")) {
                return hashes.get("sha1");
            } else if (hashes.containsKey("sha512")) {
                return hashes.get("sha512");
            } else if (hashes.containsKey("md5")) {
                return hashes.get("md5");
            }
            return null;
        }

        /**
         * 获取文件哈希算法
         */
        public String getPreferredHashAlgorithm() {
            if (hashes == null || hashes.isEmpty()) {
                return null;
            }
            if (hashes.containsKey("sha256")) {
                return "SHA-256";
            } else if (hashes.containsKey("sha1")) {
                return "SHA-1";
            } else if (hashes.containsKey("sha512")) {
                return "SHA-512";
            } else if (hashes.containsKey("md5")) {
                return "MD5";
            }
            return null;
        }
    }
}
