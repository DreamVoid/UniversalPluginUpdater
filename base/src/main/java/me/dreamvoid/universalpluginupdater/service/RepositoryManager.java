package me.dreamvoid.universalpluginupdater.service;

import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;
import me.dreamvoid.universalpluginupdater.Config;
import me.dreamvoid.universalpluginupdater.Utils;
import me.dreamvoid.universalpluginupdater.objects.ChannelConfig;
import me.dreamvoid.universalpluginupdater.objects.channel.UpdateConfig;
import me.dreamvoid.universalpluginupdater.platform.Platform;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static java.text.MessageFormat.format;
import static me.dreamvoid.universalpluginupdater.Utils.debug;
import static me.dreamvoid.universalpluginupdater.service.LanguageManager.tr;

/**
 * 仓库同步服务
 */
public class RepositoryManager {
    private static final String DEFAULT_REPOSITORY = "https://repo.upu.dreamvoid.me";
    private static final String REPOSITORIES_RESOURCE = "repositories.json";

    private final Platform platform;
    private final Logger logger;
    private final List<ChannelUpdateResult> updateResults = new ArrayList<>();
    private final Map<String, RepositoryAccessor> remoteAccessorCache = new HashMap<>();

    public RepositoryManager(Platform platform) {
        this.platform = platform;
        logger = platform.getPlatformLogger();
    }

    /**
     * 从远程仓库检查更新配置
     */
    public List<ChannelUpdateResult> update() {
        List<String> repositories = getRepositories();
        String platformName = platform.getPlatformName().toLowerCase();

        updateResults.clear();

        for (String pluginIdRaw : platform.getPlugins()) {
            String pluginId = pluginIdRaw.toLowerCase();

            // 检查是否在更新列表或更新排除列表中
            boolean inCheckList = Config.Repository_CheckList.stream().anyMatch(pluginId::equalsIgnoreCase);
            if (((Config.Repository_CheckMode == 0) && !inCheckList) || ((Config.Repository_CheckMode == 1) && inCheckList)) {
                debug("{0}: 根据配置文件名单，跳过此插件", pluginIdRaw);
                continue;
            }

            debug(format("{0}: 开始检查更新配置", pluginIdRaw));

            ChannelUpdateResult result = getUpdateChannel(pluginId, platformName, repositories);
            if (result != null) {
                updateResults.add(result);
            } else {
                debug(format("{0}: 未找到插件配置索引", pluginIdRaw));
            }
        }

        return new ArrayList<>(updateResults);
    }

    public List<ChannelUpdateResult> getChannelUpdateResults() {
        return new ArrayList<>(updateResults);
    }

    public RepositoryDownloadResult download(Set<String> pluginIds) {
        Set<ChannelUpdateResult> availableResults = updateResults.stream().filter(r -> pluginIds.contains(r.pluginId())).collect(Collectors.toSet());

        int success = 0, failed = 0;

        for (ChannelUpdateResult candidate : availableResults) {
            String pluginId = candidate.pluginId();
            try {
                Path localConfigPath = Files.createDirectories(platform.getDataPath().resolve("channels")).resolve(pluginId + ".json");
                
                RepositoryAccessor activeAccessor = remoteAccessorCache.values().stream()
                        .filter(acc -> acc.pluginId.equals(pluginId) && acc.configUrl != null && acc.configContent != null)
                        .findFirst()
                        .orElse(null);

                if (activeAccessor == null) {
                    failed += 1;
                    debug(format("{0}: 找不到对应的缓存请求对象", pluginId));
                    continue;
                }

                Utils.Http.Response response = Utils.Http.get(activeAccessor.configUrl, activeAccessor.configCacheToken);
                
                if (response.statusCode() == 304) {
                    Files.writeString(localConfigPath, activeAccessor.configContent);
                    success += 1;
                    logger.info(tr("message.update.hit", activeAccessor.configUrl));
                } else if (response.statusCode() == 200) {
                    if (response.content() != null && !response.content().isBlank()) {
                        activeAccessor.configContent = response.content();
                        activeAccessor.configCacheToken = response.cacheToken();
                        Files.writeString(localConfigPath, activeAccessor.configContent);
                        success += 1;
                        logger.info(tr("message.update.get", activeAccessor.configUrl));
                    } else {
                        failed += 1;
                        logger.info(tr("message.update.ignore", activeAccessor.configUrl, tr("tag.update.error.response-null")));
                    }
                } else {
                    failed += 1;
                    logger.info(tr("message.update.ignore", activeAccessor.configUrl, tr("tag.update.error.status-code", response.statusCode())));
                }

            } catch (Exception e) {
                failed += 1;
                debug(format("{0}: 配置写入失败，原因: {1}", pluginId, e));
            }
        }

        return new RepositoryDownloadResult(success, failed, pluginIds.size() - availableResults.size());
    }

    private List<String> getRepositories() {
        try {
            Path repositoriesPath = Files.createDirectories(platform.getDataPath()).resolve("repositories.json");
            if (Files.notExists(repositoriesPath)) {
                try (InputStream inputStream = RepositoryManager.class.getClassLoader().getResourceAsStream(REPOSITORIES_RESOURCE)) {
                    if (inputStream != null) {
                        Files.copy(inputStream, repositoriesPath);
                    } else {
                        Files.writeString(repositoriesPath, Utils.getGson().toJson(List.of(DEFAULT_REPOSITORY)));
                    }
                }
            }

            List<String> result = Utils.getGson().fromJson(Files.readString(repositoriesPath), new TypeToken<List<String>>() {}.getType());
            result.replaceAll(String::trim);
            debug("repositories.json 解析成功，仓库数量: {0}", result.size());
            return result;
        } catch (Exception e) {
            debug("repositories.json 解析失败，使用默认仓库。原因: {0}", e);
            return List.of(DEFAULT_REPOSITORY);
        }
    }

    @Nullable
    private ChannelUpdateResult getUpdateChannel(String pluginId, String platformName, List<String> repositories) {
        for (String repository : repositories) { // 每个仓库依次检查
            String cacheKey = pluginId + "@" + repository.hashCode();
            RepositoryAccessor accessor = remoteAccessorCache.computeIfAbsent(cacheKey, k -> new RepositoryAccessor(pluginId, repository));

            ChannelUpdateResult result = accessor.fetch(platformName);
            if (result != null) {
                return result;
            }
        }
        return null;
    }

    private class RepositoryAccessor {
        private final String pluginId;
        private final String repository;

        private String configContent;
        private String configCacheToken;

        private String indexContent;
        private String indexCacheToken;

        private String configUrl;

        public RepositoryAccessor(String pluginId, String repository) {
            this.pluginId = pluginId;
            this.repository = repository;
        }

        public ChannelUpdateResult fetch(String platformName) {
            try {
                String indexUrl = repository + "/channels/" + pluginId + "/index.json";
                debug("{0}: 尝试仓库索引: {1}", pluginId, indexUrl);

                Utils.Http.Response indexResponse = Utils.Http.get(indexUrl, indexCacheToken);

                debug("{0}: 索引响应状态: {1}，URL: {2}", pluginId, indexResponse.statusCode(), indexUrl);

                if(indexResponse.statusCode() == 304) {
                    if(indexContent != null && !indexContent.isBlank()) {
                        indexCacheToken = indexResponse.cacheToken();
                        logger.info(tr("message.update.hit", indexUrl));
                    } else {
                        logger.warning(tr("message.update.error", indexUrl, tr("tag.update.error.no-cache-304")));
                        return null;
                    }
                } else if(indexResponse.statusCode() == 200) {
                    if (indexResponse.content() != null && !indexResponse.content().isBlank()) {
                        indexContent = indexResponse.content();
                        indexCacheToken =  indexResponse.cacheToken();
                        logger.info(tr("message.update.get", indexUrl));
                    } else { // 响应为空
                        logger.warning(tr("message.update.error", indexUrl, tr("tag.update.error.response-null")));
                        return null;
                    }
                } else {
                    // 响应不正常
                    logger.info(tr("message.update.ignore", indexUrl, tr("tag.update.error.status-code", indexResponse.statusCode())));
                    return null;
                }

                RepoIndex index = Utils.getGson().fromJson(indexContent, RepoIndex.class);

                if (index.platform == null || index.platform.isEmpty()) {
                    debug("{0}: 仓库索引缺少 platform 定义: {1}", pluginId, indexUrl);
                    return null;
                }

                // 检查当前平台
                Optional<String> optFilename = Optional.ofNullable(index.platform.get(platformName))
                        .filter(s -> !s.isBlank())
                        .or(() -> Optional.ofNullable(index.platform.get("universal")).filter(s -> !s.isBlank()));
                if (optFilename.isEmpty()) {
                    logger.info(tr("message.update.ignore", indexUrl, "没有可用配置"));
                    return null;
                }
                String filename = optFilename.get();

                // 构造最终配置文件链接
                configUrl = repository + "/channels/" + pluginId + "/" + filename;
                debug("{0}: 尝试获取配置: {1}", pluginId, configUrl);

                Utils.Http.Response configResponse = Utils.Http.get(configUrl, configCacheToken);

                if (configResponse.statusCode() == 200) {
                    if (configResponse.content() != null && !configResponse.content().isBlank()) {
                        configContent = configResponse.content();
                        configCacheToken = configResponse.cacheToken();
                        logger.info(tr("message.update.get", configUrl));
                    } else {
                        logger.warning(tr("message.update.error", configUrl, tr("tag.update.error.response-null")));
                        return null;
                    }
                } else if (configResponse.statusCode() == 304) {
                    if(configContent != null && !configContent.isBlank()){
                        configCacheToken = configResponse.cacheToken();
                        logger.info(tr("message.update.hit", configUrl));
                    } else {
                        logger.warning(tr("message.update.error", configUrl, tr("tag.update.error.no-cache-304")));
                        return null;
                    }
                } else {
                    logger.info(tr("message.update.ignore", configUrl, tr("tag.update.error.status-code", configResponse.statusCode())));
                    return null;
                }

                long remoteLastUpdate = getLastUpdateFromJson(configContent);
                Path localConfigPath = platform.getDataPath().resolve("channels").resolve(pluginId + ".json");
                debug("{0}: 远程 last_update: {1}", pluginId, String.valueOf(remoteLastUpdate));

                short 本地文件状态 = 0;

                if (Files.exists(localConfigPath)) {
                    本地文件状态 = 2; // 存在更新
                    try {
                        long localLastUpdate = getLastUpdateFromJson(Files.readString(localConfigPath));
                        debug("{0}: 本地 last_update: {1}", pluginId, String.valueOf(localLastUpdate));
                        if (remoteLastUpdate <= localLastUpdate) {
                            本地文件状态 = 1; // 存在且已是最新
                        }
                    } catch (Exception e) {
                        debug("{0}: 读取本地配置失败: {1}，原因: {2}", pluginId, localConfigPath, e);
                    }
                }

                return new ChannelUpdateResult(pluginId, 本地文件状态, configContent);
            } catch (Exception e) {
                debug("{0}: 解析出错，原因: {1}", pluginId, e);
                return null;
            }
        }
    }

    private long getLastUpdateFromJson(String jsonText) {
        if (jsonText == null || jsonText.isBlank()) {
            return 0L;
        }

        try {
            JsonObject obj = Utils.getGson().fromJson(jsonText, JsonObject.class);
            if (obj != null && obj.has("last_update") && obj.get("last_update").isJsonPrimitive()) {
                return obj.get("last_update").getAsLong();
            } else {
                UpdateConfig cfg = Utils.getGson().fromJson(jsonText, UpdateConfig.class);
                long max = 0L;
                if (cfg != null && cfg.channels() != null) {
                    for (ChannelConfig channel : cfg.channels()) {
                        if (channel != null && channel.lastUpdate() != null && channel.lastUpdate() > max) {
                            max = channel.lastUpdate();
                        }
                    }
                }
                return max;
            }
        } catch (Exception ignored) {
            return 0L;
        }
    }

    private record RepoIndex (
        @SerializedName("name") String name,
        @SerializedName("platform") Map<String, String> platform
    ){}

    /**
     * 插件更新渠道结果
     * @param pluginId 插件ID
     * @param localFileStatus 本地文件状态（0: 不存在；1: 存在且最新；2: 可更新）
     */
    public record ChannelUpdateResult(
            String pluginId,
            short localFileStatus,
            @NotNull String configText
    ) { }

    public record RepositoryDownloadResult(
            int success,
            int failed,
            int skipped
    ) {}
}
