package me.dreamvoid.universalpluginupdater.update;

import com.google.gson.JsonObject;
import me.dreamvoid.universalpluginupdater.Config;
import me.dreamvoid.universalpluginupdater.Utils;
import me.dreamvoid.universalpluginupdater.objects.channel.info.ModrinthChannelInfo;
import me.dreamvoid.universalpluginupdater.objects.update.modrinth.ModrinthFile;
import me.dreamvoid.universalpluginupdater.objects.update.modrinth.ModrinthVersion;
import me.dreamvoid.universalpluginupdater.platform.Platform;
import me.dreamvoid.universalpluginupdater.service.UpdateManager;
import okhttp3.HttpUrl;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import static me.dreamvoid.universalpluginupdater.service.LanguageManager.tr;

@UpdateChannel("modrinth")
public class ModrinthUpdate extends AbstractUpdate {
    private static final String MODRINTH_API = "https://api.modrinth.com/v2";

    private final ModrinthChannelInfo info;

    private ModrinthVersion selectedVersion;
    private Utils.Http.CacheToken cacheToken;

    public ModrinthUpdate(String pluginId, JsonObject config, Platform platform) {
        super(pluginId, platform);
        this.info = applyDefaults(Utils.gson.fromJson(config, ModrinthChannelInfo.class));
        if(this.info.projectId() == null || this.info.projectId().isEmpty()){
            throw new IllegalArgumentException("projectId 不存在或为空");
        }
    }

    /**
     * 获取该渠道的默认配置（仅本渠道使用）
     */
    private ModrinthChannelInfo defaults() {
        return new ModrinthChannelInfo(null, false, "name", null);
    }

    /**
     * 将配置中缺失（为 null）的字段用默认值替换
     */
    private ModrinthChannelInfo applyDefaults(ModrinthChannelInfo info) {
        ModrinthChannelInfo defaults = defaults();
        return new ModrinthChannelInfo(
                info.projectId(),
                info.featured(),
                info.versionKey() != null ? info.versionKey() : defaults.versionKey(),
                info.versionRegex()
        );
    }

    /**
     * 从Modrinth API获取版本信息并选择合适的版本
     * 使用HTTP缓存机制减少网络请求和Modrinth负载
     */
    @Override
    public boolean checkUpdate() {
        String url = buildUrl();
        try {
            Utils.Http.Response response = Utils.Http.get(url, cacheToken);

            if (response.statusCode() == 304) {
                // 返回304 Not Modified，使用缓存
                if (selectedVersion != null) {
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

                // 解析JSON数组
                ModrinthVersion[] versions = Utils.gson.fromJson(content, ModrinthVersion[].class);
                if (versions == null || versions.length == 0) {
                    logger.info(tr("message.update.ignore", url, tr("tag.update.ignore.no-version")));
                    return false;
                }

                // 选择第一个版本（Modrinth API已按时间排序，最新的在前）
                this.selectedVersion = versions[0];
                this.cacheToken = response.cacheToken();
                logger.info(tr("message.update.get", url));
                return true;
            } else {
                logger.info(tr("message.update.ignore", url, tr("tag.update.ignore.status-code", response.statusCode())));
                return false;
            }
        } catch (Exception e) {
            logger.warning(tr("message.update.error", url, e));
            return false;
        }
    }

    /**
     * 构建Modrinth API URL
     */
    private String buildUrl() {
        HttpUrl.Builder url = HttpUrl.get(MODRINTH_API + "/project/" + info.projectId() + "/version").newBuilder();

        // 构建查询参数
        url.addQueryParameter("include_changelog", "false");

        // 添加featured参数（默认true，优先选择推荐版本）
        if (info.featured()) url.addQueryParameter("featured", "true");

        // 添加加载器参数
        List<String> loaders = (Config.Platform_Loaders != null && !Config.Platform_Loaders.isEmpty())
            ? Config.Platform_Loaders
            : platform.getLoaders();
        if (!loaders.isEmpty()) {
            url.addQueryParameter("loaders", Utils.gson.toJson(loaders));
        }

        // 添加游戏版本参数
        List<String> gameVersions = (Config.Platform_GameVersions != null)
            ? Config.Platform_GameVersions
            : platform.getGameVersions();
        if (gameVersions != null && !gameVersions.isEmpty()) {
            url.addQueryParameter("game_versions", Utils.gson.toJson(gameVersions));
        }

        return url.build().toString();
    }

    @Override
    public String getVersion() {
        if (selectedVersion == null) return null;
        String version;

        String versionKey = info.versionKey();
        if (versionKey == null || versionKey.isBlank()) {
            version = selectedVersion.name();
        } else {
            switch (versionKey) {
                case "version_number" -> {
                    String value = selectedVersion.versionNumber();
                    version = (value != null && !value.isBlank()) ? value : selectedVersion.name();
                }
                case "name" -> version = selectedVersion.name();
                default -> version = selectedVersion.name();
            }
        }

        if (version == null || version.isBlank()) {
            return null;
        }

        String regex = info.versionRegex();

        if (regex != null && !regex.isBlank()) {
            try {
                Pattern pattern = Pattern.compile(regex);
                Matcher matcher = pattern.matcher(version);
                if (matcher.find()) {
                    if (matcher.groupCount() >= 1) {
                        String group = matcher.group(1);
                        if (group != null && !group.isBlank()) {
                            return group.trim();
                        }
                    }

                    String matched = matcher.group();
                    if (matched != null && !matched.isBlank()) {
                        return matched.trim();
                    }
                }
            } catch (PatternSyntaxException ignored) {
                logger.warning(tr("message.update.warn.invalid-regex", regex));
            }
        }

        version = version.trim();
        if (version.regionMatches(true, 0, "v", 0, 1)) {
            version = version.substring(1).trim();
        }

        return version.split(" ")[0];

    }

    @Override
    @Nullable
    public Path download() {
        // 从缓存的版本信息中获取下载链接
        if (selectedVersion == null) {
            logger.warning(tr("message.update.failed", tr("tag.update.modrinth.failed.no-selected-version")));
            return null;
        }

        ModrinthFile file = selectedVersion.getPrimaryFile();
        if (file == null || file.url() == null) {
            logger.warning(tr("message.update.failed", tr("tag.update.modrinth.failed.no-primary-file")));
            return null;
        }

        String downloadUrl = file.url();
        String originFilename = file.filename();
        String preferredHash = file.getHash();
        String hashAlgorithm = file.getHashAlgorithm();

        try {
            String desiredFilename = Utils.parseFileName(pluginId, getChannelId());
            String expectedFilename = desiredFilename != null ? desiredFilename : originFilename;

            // 获取下载目录
            Path downloadDir = UpdateManager.instance().getDownloadPath();
            Path filePath = downloadDir.resolve(expectedFilename);

            // 检查文件是否已存在：有 hash 时校验完整，无 hash 时视为已下载
            if (filePath.toFile().exists()) {
                boolean noHash = preferredHash == null || hashAlgorithm == null;
                if (noHash || Utils.File.verifyHash(filePath, hashAlgorithm, preferredHash)) {
                    logger.info(tr("message.update.hit", downloadUrl));
                    return filePath;  // 文件完整，不必重新下载
                }
                Files.delete(filePath);
            }

            // 执行下载
            Utils.Http.DownloadResult result = Utils.Http.download(downloadUrl, downloadDir, desiredFilename, Config.Updater_ForceFilename);

            if (!result.success()) {
                logger.warning(tr("message.update.error", downloadUrl, result.errorMessage()));
                return null;
            }

            Path downloadedPath = downloadDir.resolve(result.filename());

            // 验证下载文件的完整性
            if (preferredHash != null && hashAlgorithm != null) {
                if (Utils.File.verifyHash(downloadedPath, hashAlgorithm, preferredHash)) {
                    logger.info(tr("message.update.get", downloadUrl));
                    return downloadedPath;
                } else {
                    logger.warning(tr("message.update.error", downloadUrl, tr("tag.update.error.checksum-mismatch")));
                    Files.delete(downloadedPath);  // 删除不完整的文件
                    return null;
                }
            } else {
                logger.info(tr("message.update.get", downloadUrl));
                return downloadedPath;
            }
        } catch (Exception e) {
            logger.warning(tr("message.update.error", downloadUrl, e));
            return null;
        }
    }
}
