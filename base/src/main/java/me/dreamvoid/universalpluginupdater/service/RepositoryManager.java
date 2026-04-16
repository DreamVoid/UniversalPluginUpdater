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
import java.util.stream.Collectors;

import static java.text.MessageFormat.format;
import static me.dreamvoid.universalpluginupdater.Utils.debug;

/**
 * 仓库同步服务
 */
public class RepositoryManager {
    private static final String DEFAULT_REPOSITORY = "https://repo.upu.dreamvoid.me/";
    private static final String REPOSITORIES_RESOURCE = "repositories.json";

    private final Platform platform;
    private final List<ChannelUpdateResult> updateResults = new ArrayList<>();
    private final Map<String, RepositoryAccessor> remoteAccessorCache = new HashMap<>();

    public RepositoryManager(Platform platform) {
        this.platform = platform;
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
        Set<ChannelUpdateResult> availableResults = updateResults.stream().filter(r -> pluginIds.contains(r.pluginId)).collect(Collectors.toSet());

        int success = 0, failed = 0;

        for (ChannelUpdateResult candidate : availableResults) {
            try {
                Path localConfigPath = Files.createDirectories(platform.getDataPath().resolve("channels")).resolve(candidate.pluginId + ".json");
                Files.writeString(localConfigPath, candidate.configText());
                success += 1;
                debug(format("{0}: 配置写入成功: {1}", candidate.pluginId, localConfigPath));
            } catch (Exception e) {
                failed += 1;
                debug(format("{0}: 配置写入失败，原因: {1}", candidate.pluginId, e));
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

    /**
     * @param pluginId 小写插件 ID
     * @param platformName 平台名称
     * @param repositories 仓库地址列表
     * @return 任意仓库获取成功返回 {@link ChannelUpdateResult} 实例，否则返回null
     */
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
        private String configLastModified;

        private String indexContent;
        private String indexLastModified;

        public RepositoryAccessor(String pluginId, String repository) {
            this.pluginId = pluginId;
            this.repository = repository;
        }

        public ChannelUpdateResult fetch(String platformName) {
            try {
                String indexUrl = repository + "/channels/" + pluginId + "/index.json";
                debug("{0}: 尝试仓库索引: {1}", pluginId, indexUrl);

                Utils.Http.Response indexResponse = Utils.Http.get(indexUrl, indexLastModified);

                debug("{0}: 索引响应状态: {1}，URL: {2}", pluginId, indexResponse.statusCode(), indexUrl);

                if(indexResponse.statusCode() == 304) {
                    if(indexContent != null){
                        indexLastModified = indexResponse.lastModified();
                        debug("{0}: 响应代码 304，使用缓存。", pluginId);
                    } else {
                        debug("{0}: 响应代码 304 但没有缓存！", pluginId);
                        return null;
                    }
                } else if(indexResponse.statusCode() == 200) {

                    if (indexResponse.content() != null && !indexResponse.content().isBlank()) {
                        indexContent = indexResponse.content();
                        indexLastModified =  indexResponse.lastModified();
                    } else { // 响应为空
                        debug("{0}: 仓库存在索引但内容为空: {1}", pluginId, indexUrl);
                        return null;
                    }
                } else {
                    // 响应不正常
                    debug("{0}: 响应代码 {1} 异常！", pluginId, indexResponse.statusCode());
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
                    debug("{0}: 在仓库 {1} 中没有可用配置。", pluginId, repository);
                    return null;
                }
                String filename = optFilename.get();

                // 构造最终配置文件链接
                String configUrl = repository + "/channels/" + pluginId + "/" + filename;
                debug("{0}: 尝试获取配置: {1}", pluginId, configUrl);

                Utils.Http.Response configResponse = Utils.Http.get(configUrl, configLastModified);

                if (configResponse.statusCode() == 200) {
                    if (configResponse.content() != null && !configResponse.content().isBlank()) {
                        debug("{0}: 配置下载成功: {1}", pluginId, configUrl);
                        configContent = configResponse.content();
                        configLastModified = configResponse.lastModified();
                    }
                } else if (configResponse.statusCode() == 304) {
                    configLastModified = configResponse.lastModified();
                    debug("{0}: 返回 304，使用上一次拉取的更新配置", pluginId);
                } else {
                    debug("{0}: 下载配置失败，状态码: {1}", configUrl, configResponse.statusCode());
                    return null;
                }

                if (configContent != null) {
                    long remoteLastUpdate = getLastUpdateFromJson(configContent);
                    Path localConfigPath = platform.getDataPath().resolve("channels").resolve(pluginId + ".json");
                    debug("{2}: 远程 last_update: {0}，本地路径: {1}", remoteLastUpdate, localConfigPath, pluginId);

                    short hasUpdate = 0;

                    if (Files.exists(localConfigPath)) {
                        hasUpdate = 2; // 存在更新
                        try {
                            long localLastUpdate = getLastUpdateFromJson(Files.readString(localConfigPath));
                            debug("{0}: 本地 last_update: {1}", pluginId, localLastUpdate);
                            if (remoteLastUpdate <= localLastUpdate) {
                                hasUpdate = 1; // 存在且已是最新
                            }
                        } catch (Exception e) {
                            debug("{0}: 读取本地配置失败: {1}，原因: {2}", pluginId, localConfigPath, e);
                        }
                    }

                    return new ChannelUpdateResult(pluginId, hasUpdate, configContent);
                } else {
                    return null;
                }
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
