package me.dreamvoid.universalpluginupdater.update;

import me.dreamvoid.universalpluginupdater.Utils;
import me.dreamvoid.universalpluginupdater.objects.channel.ModrinthChannelInfo;
import me.dreamvoid.universalpluginupdater.objects.update.modrinth.ModrinthFile;
import me.dreamvoid.universalpluginupdater.objects.update.modrinth.ModrinthVersion;
import me.dreamvoid.universalpluginupdater.platform.IPlatformProvider;
import me.dreamvoid.universalpluginupdater.service.LanguageService;
import me.dreamvoid.universalpluginupdater.service.UpgradeService;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

public class ModrinthUpdate extends AbstractUpdate {
    private static final String MODRINTH_API = "https://api.modrinth.com/v2";

    private final Logger logger ;
    private final String pluginId;
    private final ModrinthChannelInfo info;
    private final IPlatformProvider platform;
    private ModrinthVersion selectedVersion;
    private String lastModified;
    private Path downloadedFilePath;

    public ModrinthUpdate(String pluginId, ModrinthChannelInfo info, IPlatformProvider platform) {
        this.updateType = UpdateType.Modrinth;
        this.pluginId = pluginId;
        this.info = info;
        this.platform = platform;
        this.logger = platform.getPlatformLogger();

        if(info.projectId() == null || info.projectId().isEmpty()){
            throw new IllegalArgumentException("projectId 不存在或为空");
        }
    }

    /**
     * 从Modrinth API获取版本信息并选择合适的版本
     * 使用HTTP缓存机制减少网络请求和Modrinth负载
     */
    @Override
    public boolean update() {
        String url = buildApiUrl();
        try {
            Utils.Http.Response response = Utils.Http.get(url, lastModified);

            if (response.isNotModified()) {
                // 返回304 Not Modified，使用缓存
                if (selectedVersion == null) {
                    logger.warning(LanguageService.instance().tr("message.update.modrinth.error.no-cache-304", url));
                    return false;
                }
                this.lastModified = response.lastModified;
                logger.info(LanguageService.instance().tr("message.update.hit", url));
                return true;
            }

            if (response.isSuccess()) {
                String content = response.content;
                if (content == null) {
                    logger.warning(LanguageService.instance().tr("message.update.modrinth.error.response-null", url));
                    return false;
                }

                // 解析JSON数组
                ModrinthVersion[] versions = Utils.getGson().fromJson(content, ModrinthVersion[].class);
                if (versions == null || versions.length == 0) {
                    logger.warning(LanguageService.instance().tr("message.update.modrinth.error.no-versions", url));
                    return false;
                }

                // 选择第一个版本（Modrinth API已按时间排序，最新的在前）
                this.selectedVersion = versions[0];
                this.lastModified = response.lastModified;
                logger.info(LanguageService.instance().tr("message.update.get", url));
                return true;
            }

            logger.warning(LanguageService.instance().tr("message.update.error.status-code", url, response.statusCode));
            return false;
        } catch (Exception e) {
            logger.warning(LanguageService.instance().tr("message.update.error", url, e));
            return false;
        }
    }

    /**
     * 构建Modrinth API URL
     */
    private String buildApiUrl() {
        StringBuilder url = new StringBuilder();
        url.append(MODRINTH_API)
                .append("/project/")
                .append(info.projectId())
                .append("/version");

        // 构建查询参数
        Set<String> queries = new HashSet<>();

        // 添加changelog参数（不需要更新日志）
        queries.add("include_changelog=false");

        // 添加featured参数（默认true，优先选择推荐版本）
        if (info.featured()) {
            queries.add("featured=true");
        }

        // 添加加载器参数
        List<String> loaders = platform.getLoaders();
        if (loaders != null && !loaders.isEmpty()) {
            queries.add("loaders=[\"" + String.join("\",\"", loaders) + "\"]");
        }

        // 添加游戏版本参数
        List<String> gameVersions = platform.getGameVersions();
        if (gameVersions != null && !gameVersions.isEmpty()) {
            queries.add("game_versions=[\"" + String.join("\",\"", gameVersions) + "\"]");
        }

        url.append("?").append(String.join("&", queries));
        return url.toString();
    }

    @Override
    public String getCachedVersion() {
        // 返回版本名而不是版本号
        // 原因：本地插件版本号暂无法获取，且Modrinth版本号通常非纯数字
        // 仅当版本号为纯数字时才适合直接比较大小
        // 其他平台的更新渠道实现时应注意此点
        return selectedVersion != null ? selectedVersion.name() : null;
    }

    @Override
    public String getPluginId() {
        return pluginId;
    }

    @Override
    public boolean upgrade(boolean now) {
        // 升级逻辑：下载文件 → 获取升级策略 → 执行升级
        try {
            // 首先执行下载（如果还没下载）
            if (!download()) return false;

            // 获取当前插件文件
            Path currentPluginFile = platform.getPluginFile(pluginId);

            // 获取下载的新文件路径
            Path newPluginFile = downloadedFilePath;

            if (newPluginFile == null || !Files.exists(newPluginFile)) {
                logger.warning(LanguageService.instance().tr("message.update.error.downloaded-file-missing", newPluginFile));
                return false;
            }

            return UpgradeService.getInstance().upgrade(pluginId, newPluginFile, currentPluginFile, now);
        } catch (Exception e) {
            logger.warning(LanguageService.instance().tr("message.update.error.upgrade-failed", e));
            return false;
        }
    }

    @Override
    public boolean download() {
        // 从缓存的版本信息中获取下载链接
        if (selectedVersion == null) {
            logger.warning(LanguageService.instance().tr("message.update.modrinth.error.no-selected-version"));
            return false;
        }

        ModrinthFile file = selectedVersion.getPrimaryFile();
        if (file == null || file.url() == null) {
            logger.warning(LanguageService.instance().tr("message.update.modrinth.error.no-primary-file"));
            return false;
        }

        String downloadUrl = file.url();
        String originFilename = file.filename();
        String preferredHash = file.getHash();
        String hashAlgorithm = file.getHashAlgorithm();

        try {
            String desiredFilename = Utils.parseFileName(pluginId, updateType);
            String expectedFilename = desiredFilename != null ? desiredFilename : originFilename;

            // 获取数据目录下的downloads文件夹
            Path downloadDir = platform.getDataPath().resolve("downloads");
            Path filePath = downloadDir.resolve(expectedFilename);

            // 检查文件是否已存在且完整
            if (filePath.toFile().exists()) {
                if (preferredHash != null && hashAlgorithm != null
                        && Utils.File.verifyHash(filePath, hashAlgorithm, preferredHash)) {
                    this.downloadedFilePath = filePath;
                    logger.info(LanguageService.instance().tr("message.update.hit", downloadUrl));
                    return true;  // 文件完整，不必重新下载
                } else {
                    Files.delete(filePath);
                }
            }

            // 执行下载
            Utils.Http.DownloadResult result = Utils.Http.download(downloadUrl, downloadDir, desiredFilename);

            if (!result.success()) {
                logger.warning(LanguageService.instance().tr("message.update.error", downloadUrl, result.errorMessage()));
                return false;
            }

            Path downloadedPath = downloadDir.resolve(result.filename());

            // 验证下载文件的完整性
            if (preferredHash != null && hashAlgorithm != null) {
                if (Utils.File.verifyHash(downloadedPath, hashAlgorithm, preferredHash)) {
                    this.downloadedFilePath = downloadedPath;
                    logger.info(LanguageService.instance().tr("message.update.get", downloadUrl));
                    return true;
                } else {
                    logger.warning(LanguageService.instance().tr("message.update.error.checksum", downloadUrl));
                    Files.delete(downloadedPath);  // 删除不完整的文件
                    this.downloadedFilePath = null;
                    return false;
                }
            } else {
                this.downloadedFilePath = downloadedPath;
                logger.info(LanguageService.instance().tr("message.update.get", downloadUrl));
                return true;
            }
        } catch (Exception e) {
            logger.warning(LanguageService.instance().tr("message.update.error", downloadUrl, e));
            return false;
        }
    }
}
