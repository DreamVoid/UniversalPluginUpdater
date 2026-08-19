package me.dreamvoid.universalpluginupdater.update;

import com.google.gson.annotations.SerializedName;
import me.dreamvoid.universalpluginupdater.Utils;
import me.dreamvoid.universalpluginupdater.objects.channel.info.UrlChannelInfo;
import me.dreamvoid.universalpluginupdater.platform.Platform;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static me.dreamvoid.universalpluginupdater.service.LanguageManager.tr;

public class URLUpdate extends AbstractUpdate {
    private final UrlChannelInfo info;

    private UpdateInfo updateInfo;
    private String cacheToken;

    public URLUpdate(String pluginId, UrlChannelInfo info, Platform platform) {
        super(pluginId, platform);
        this.updateType = UpdateType.URL;
        this.info = info;

        if (this.info.url() == null || this.info.url().isEmpty()) {
            throw new IllegalArgumentException("URL 不存在或为空");
        }
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
    public boolean checkUpdate() {
        String url = info.url();
        try {
            Utils.Http.Response response = Utils.Http.get(url, cacheToken);

            if (response.statusCode() == 304) {
                // 返回304 Not Modified，使用缓存
                if (updateInfo != null) {
                    this.cacheToken = response.cacheToken();
                    logger.info(tr("message.update.hit", url));
                    return true;
                } else {
                    logger.warning(tr("message.update.error", url, tr("tag.update.error.no-cache-304")));
                    return false;
                }
            } else if (response.statusCode() == 200) {
                String content = response.content();
                if (content == null) {
                    logger.info(tr("message.update.ignore", url, tr("tag.update.ignore.response-null")));
                    return false;
                }

                this.updateInfo = Utils.getGson().fromJson(content, UpdateInfo.class);
                this.cacheToken = response.cacheToken();

                if (updateInfo != null && updateInfo.version != null && updateInfo.downloadUrl != null) {
                    logger.info(tr("message.update.get", url));
                    return true;
                } else {
                    this.updateInfo = null;
                    this.cacheToken = null;
                    logger.warning(tr("message.update.error", url, tr("tag.update.error.response-invalid")));
                    return false;
                }
            } else {
                logger.info(tr("message.update.ignore", url, tr("tag.update.ignore.status-code", response.statusCode())));
                return false;
            }
        } catch (Exception e) {
            logger.warning(tr("message.update.error", url, e));
            return false;
        }
    }

    @Override
    public String getVersion() {
        return updateInfo != null ? updateInfo.version : null;
    }

    @Override
    @Nullable
    public Path download() {
        // 从缓存的更新信息中获取下载链接
        if (updateInfo == null || updateInfo.downloadUrl == null) {
            logger.warning(tr("message.update.failed", tr("tag.update.url.failed.no-download-url")));
            return null;
        }

        String downloadUrl = updateInfo.downloadUrl;
        String preferredHash = updateInfo.getPreferredHash();
        String hashAlgorithm = updateInfo.getPreferredHashAlgorithm();

        try {
            String desiredFilename = Utils.parseFileName(pluginId, updateType);

            // 获取下载目录
            Path downloadDir = getDownloadPath();

            // 若配置包含 ${originName}，desiredFilename 会被解析为 null，交给 Http 层按服务器原始文件名处理
            Utils.Http.DownloadResult result = Utils.Http.download(downloadUrl, downloadDir, desiredFilename);

            if (!result.success()) {
                logger.warning(tr("message.update.error", downloadUrl, result.errorMessage()));
                return null;
            }

            Path filePath = downloadDir.resolve(result.filename());

            // 验证下载文件的完整性
            if (preferredHash != null && hashAlgorithm != null) {
                if (Utils.File.verifyHash(filePath, hashAlgorithm, preferredHash)) {
                    logger.info(tr("message.update.get", downloadUrl));
                    return filePath;
                } else {
                    // 删除不完整的文件
                    Files.delete(filePath);
                    logger.warning(tr("message.update.error", downloadUrl, tr("tag.update.error.checksum-mismatch")));
                    return null;
                }
            } else {
                logger.info(tr("message.update.get", downloadUrl));
                return filePath;
            }
        } catch (Exception e) {
            logger.warning(tr("message.update.error", downloadUrl, e));
            return null;
        }
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
