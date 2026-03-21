package me.dreamvoid.universalpluginupdater.update;

import com.google.gson.Gson;
import me.dreamvoid.universalpluginupdater.Utils;
import me.dreamvoid.universalpluginupdater.platform.IPlatformProvider;
import me.dreamvoid.universalpluginupdater.upgrade.UpgradeStrategyRegistry;
import me.dreamvoid.universalpluginupdater.upgrade.IUpgradeStrategy;
import me.dreamvoid.universalpluginupdater.update.modrinth.ModrinthFile;
import me.dreamvoid.universalpluginupdater.update.modrinth.ModrinthVersion;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

public class ModrinthUpdate extends AbstractUpdate {
    private static final String MODRINTH_API = "https://api.modrinth.com/v2";
    private static final Gson gson = new Gson();
    private static final Logger logger = Utils.getLogger();

    private final String pluginId;
    private final String projectId;
    private final IPlatformProvider platform;
    private ModrinthVersion selectedVersion;
    private String lastModified;

    public ModrinthUpdate(String pluginId, String projectId, IPlatformProvider platform) {
        this.updateType = UpdateType.Modrinth;
        this.pluginId = pluginId;
        this.projectId = projectId;
        this.platform = platform;
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
                    logger.warning("Err: " + url + " [304 but no cache]");
                    return false;
                }
                logger.info("Hit: " + url);
                this.lastModified = response.lastModified;
                return true;
            }

            if (response.isSuccess()) {
                String content = response.content;
                if (content == null) {
                    logger.warning("Err: " + url + " [response is null]");
                    return false;
                }

                // 解析JSON数组
                ModrinthVersion[] versions = gson.fromJson(content, ModrinthVersion[].class);
                if (versions == null || versions.length == 0) {
                    logger.warning("Err: " + url + " [no versions]");
                    return false;
                }

                // 选择第一个版本（Modrinth API已按时间排序，最新的在前）
                this.selectedVersion = versions[0];
                this.lastModified = response.lastModified;
                logger.info("Get: " + url);
                return true;
            }

            logger.warning("Err: " + url + " [status code: " + response.statusCode + "]");
            return false;
        } catch (Exception e) {
            logger.warning("Err: " + url + " [" + e + "]");
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
                .append(projectId)
                .append("/version");

        // 构建查询参数
        Set<String> queries = new HashSet<>();

        // 添加changelog参数（不需要更新日志）
        queries.add("include_changelog=false");

        // 添加featured参数（默认true，优先选择推荐版本）
        if (true) { // TODO: 由用户控制是否featured
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
        return selectedVersion != null ? selectedVersion.getName() : null;
    }

    @Override
    public String getPluginId() {
        return pluginId;
    }

    @Override
    public boolean upgrade() {
        // 升级逻辑：下载文件 → 获取升级策略 → 执行升级
        try {
            // 首先执行下载（如果还没下载）
            if (!download()) {
                logger.warning("Failed to download plugin for upgrade");
                return false;
            }

            // 获取当前插件文件
            Path currentPluginFile = platform.getPluginFile(pluginId);

            // 获取下载的新文件路径
            Path newPluginFile = platform.getDataPath().resolve("downloads").resolve(selectedVersion.getPrimaryFile().getFilename());

            if (!Files.exists(newPluginFile)) {
                logger.warning("Downloaded plugin file not found: " + newPluginFile);
                return false;
            }

            // 获取当前活跃的升级策略
            IUpgradeStrategy strategy = UpgradeStrategyRegistry.getInstance().getActiveStrategy();
            if (strategy == null) {
                logger.warning("No active upgrade strategy configured");
                return false;
            }

            // 执行升级
            boolean result = strategy.upgrade(pluginId, newPluginFile, currentPluginFile);

            if (result) {
                logger.info("Plugin upgraded successfully using strategy: " + UpgradeStrategyRegistry.getInstance().getActiveStrategyId());
            } else {
                logger.warning("Plugin upgrade failed using strategy: " + UpgradeStrategyRegistry.getInstance().getActiveStrategyId());
            }

            return result;
        } catch (Exception e) {
            logger.warning("Upgrade error: " + e);
            return false;
        }
    }

    @Override
    public boolean download() {
        try {
            // 从缓存的版本信息中获取下载链接
            if (selectedVersion == null) {
                logger.warning("no selectedVersion");
                return false;
            }

            ModrinthFile file = selectedVersion.getPrimaryFile();
            if (file == null || file.getUrl() == null) {
                logger.warning("no primary file");
                return false;
            }

            String downloadUrl = file.getUrl();
            String filename = file.getFilename();
            String preferredHash = file.getPreferredHash();
            String hashAlgorithm = file.getPreferredHashAlgorithm();

            // TODO: 从配置文件中读取自定义文件名配置

            // 获取数据目录下的downloads文件夹
            Path downloadDir = platform.getDataPath().resolve("downloads");
            Path filePath = downloadDir.resolve(filename);

            // 检查文件是否已存在且完整
            if (filePath.toFile().exists()) {
                if (preferredHash != null && hashAlgorithm != null) {
                    // 验证现有文件的完整性
                    if (Utils.File.verifyHash(filePath, hashAlgorithm, preferredHash)) {
                        logger.info("File already exists and is complete: " + filename);
                        return true;  // 文件完整，不必重新下载
                    } else {
                        logger.warning("File hash mismatch, will re-download: " + filename);
                        // 删除不完整的文件
                        Files.delete(filePath);
                    }
                } else {
                    // 没有哈希值，始终重新下载
                    logger.info("No hash provided, will re-download: " + filename);
                    Files.delete(filePath);
                }
            }

            logger.info("download: " + downloadUrl);
            // 执行下载
            Utils.Http.DownloadResult result = Utils.Http.downloadFile(downloadUrl, downloadDir, filename);

            if (!result.success) {
                logger.warning("Failed to download: " + result.errorMessage);
                return false;
            }

            // 验证下载文件的完整性
            if (preferredHash != null && hashAlgorithm != null) {
                if (Utils.File.verifyHash(filePath, hashAlgorithm, preferredHash)) {
                    logger.info("Downloaded and verified: " + result.filename);
                    return true;
                } else {
                    logger.warning("Downloaded file hash mismatch: " + result.filename);
                    Files.delete(filePath);  // 删除不完整的文件
                    return false;
                }
            } else {
                logger.info("Downloaded (no hash verification): " + result.filename);
                return true;
            }
        } catch (Exception e) {
            logger.warning("Download error: " + e);
            return false;
        }
    }
}
