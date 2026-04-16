package me.dreamvoid.universalpluginupdater.service;

import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;
import me.dreamvoid.universalpluginupdater.Config;
import me.dreamvoid.universalpluginupdater.Utils;
import me.dreamvoid.universalpluginupdater.objects.ChannelConfig;
import me.dreamvoid.universalpluginupdater.objects.channel.UpdateConfig;
import me.dreamvoid.universalpluginupdater.platform.Platform;
import org.jetbrains.annotations.Nullable;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static java.text.MessageFormat.format;
import static me.dreamvoid.universalpluginupdater.Utils.debug;

/**
 * 仓库同步服务
 */
public class RepositoryService {
    private static final String DEFAULT_REPOSITORY = "https://repo.upu.dreamvoid.me/";
    private static final String REPOSITORIES_RESOURCE = "repositories.json";


    public enum StatusCode {
        NORMAL(0b00), AVAILABLE(0b01), UPDATABLE(0b10), SKIPPED(0b100);

        private final int code;

        StatusCode(int code) {
            this.code = code;
        }

        public int getCode() {
            return code;
        }
    }

    private final Platform platform;
    private final Logger logger;
    private final List<ChannelUpdateResult> updateResults = new ArrayList<>();
    private final Map<String, ChannelUpdateResult> remoteConfigCache = new HashMap<>();

    public RepositoryService(Platform platform) {
        this.platform = platform;
        this.logger = platform.getPlatformLogger();
    }

    /**
     * 从远程仓库检查更新配置
     */
    public List<ChannelUpdateResult> update() {
        List<String> repositories = getRepositories();
        debug("仓库列表加载完成，数量: " + repositories.size());

        String platformName = platform.getPlatformName().toLowerCase();
        List<String> plugins = platform.getPlugins();
        debug("开始同步，平台: " + platformName + "，插件数量: " + plugins.size());

        updateResults.clear();

        for (String pluginIdRaw : plugins) {
            if (pluginIdRaw == null || pluginIdRaw.isBlank()) {
                debug("跳过空插件 ID");
                continue;
            }

            boolean inCheckList = Config.Repository_CheckList.stream().anyMatch(pluginIdRaw::equalsIgnoreCase);
            if (((Config.Repository_CheckMode == 0) && !inCheckList) || ((Config.Repository_CheckMode == 1) && inCheckList)) {
                debug("{0}: 根据配置文件名单，跳过此插件", pluginIdRaw);
                continue;
            }

            debug(format("{0}: 开始检查更新配置", pluginIdRaw));

            String pluginId = pluginIdRaw.toLowerCase();
            ChannelUpdateResult result = resolvePluginConfig(pluginId, platformName, repositories);
            if (!result.available() && !result.skipped()) {
                debug(format("{0}: 未在任何仓库中找到插件配置索引", pluginId));
                continue;
            }

            if (result.skipped()) {
                debug(format("{0}: 仓库中存在插件索引但解析/下载失败", pluginId));
            }

            updateResults.add(result);
        }

        return new ArrayList<>(updateResults);
    }

    public List<ChannelUpdateResult> getUpdateResult() {
        return new ArrayList<>(updateResults);
    }

    public RepositoryDownloadResult download(Set<String> pluginIds) {
        List<String> successList = new ArrayList<>();
        List<String> failedList = new ArrayList<>();
        List<String> skippedList = new ArrayList<>();

        if (updateResults.isEmpty()) {
            return new RepositoryDownloadResult(successList, failedList, skippedList, true);
        }

        List<String> targets = new ArrayList<>();
        for (String pluginId : pluginIds) {
            String normalized = pluginId.toLowerCase();
            if (!targets.contains(normalized)) {
                targets.add(normalized);
            }
        }

        for (String pluginId : targets) {
            ChannelUpdateResult candidate = updateResults.stream()
                    .filter(r -> r.pluginId().equals(pluginId) && r.available())
                    .findFirst()
                    .orElse(null);

            if (candidate == null || candidate.configText() == null) {
                skippedList.add(pluginId);
                continue;
            }

            Path localConfigPath = platform.getDataPath().resolve("channels").resolve(pluginId + ".json");

            try {
                Files.createDirectories(localConfigPath.getParent());
            } catch (Exception e) {
                logger.warning("创建渠道配置目录失败: " + e.getMessage());
                failedList.add(pluginId);
                continue;
            }

            try {
                Files.writeString(localConfigPath, candidate.configText());
                successList.add(pluginId);
                debug(format("{0}: 配置写入成功: {1}", pluginId, localConfigPath));
            } catch (Exception e) {
                logger.warning(format("{0}: 配置写入失败: {1}，原因: {2}", pluginId, localConfigPath, e));
                failedList.add(pluginId);
            }
        }

        return new RepositoryDownloadResult(successList, failedList, skippedList, false);
    }

    private List<String> getRepositories() {
        try {
            Path repositoriesPath = Files.createDirectories(platform.getDataPath()).resolve("repositories.json");
            if (!Files.exists(repositoriesPath)) {
                debug("repositories.json 不存在，尝试从资源释放: " + repositoriesPath);
                try (InputStream inputStream = RepositoryService.class.getClassLoader().getResourceAsStream(REPOSITORIES_RESOURCE)) {
                    if (inputStream != null) {
                        Files.copy(inputStream, repositoriesPath);
                        debug("已从资源释放 repositories.json");
                    } else {
                        Files.writeString(repositoriesPath, Utils.getGson().toJson(List.of(DEFAULT_REPOSITORY)));
                        debug("资源不存在，写入默认仓库配置");
                    }
                }
            }

            List<String> list = Utils.getGson().fromJson(Files.readString(repositoriesPath), new TypeToken<List<String>>() {}.getType());
            if (list == null) {
                debug("repositories.json 解析结果为 null，视为无仓库");
                return new ArrayList<>();
            }

            List<String> result = list.stream().map(String::trim).collect(Collectors.toList());
            debug("repositories.json 解析成功，仓库数量: " + result.size());
            return result;
        } catch (Exception e) {
            logger.warning("加载 repositories.json 失败，使用默认仓库。原因: " + e.getMessage());
            return List.of(DEFAULT_REPOSITORY);
        }
    }

    private ChannelUpdateResult resolvePluginConfig(String pluginId, String platformName, List<String> repositories) {
        for (String repository : repositories) {
            String indexUrl = repository + "/channels/" + pluginId + "/index.json";
            debug(format("{0}: 尝试仓库索引: {1}", pluginId, indexUrl));

            Utils.Http.Response indexResponse;
            try {
                indexResponse = Utils.Http.get(indexUrl, null);
            } catch (Exception e) {
                logger.warning(format("{0}: 访问索引失败: {1}，原因: {2}", pluginId, indexUrl, e.getMessage()));
                continue;
            }

            debug(format("{0}: 索引响应状态: {1}，URL: {2}", pluginId, indexResponse.statusCode, indexUrl));

            if (!(indexResponse.statusCode == 200 || indexResponse.statusCode == 304)) {
                continue;
            }

            if (indexResponse.content == null || indexResponse.content.isBlank()) {
                logger.warning(format("{0}: 仓库存在索引但内容为空: {1}", pluginId, indexUrl));
                return new ChannelUpdateResult(pluginId, StatusCode.SKIPPED.getCode(), null, null);
            }

            RepoIndex index;
            try {
                index = Utils.getGson().fromJson(indexResponse.content, RepoIndex.class);
            } catch (Exception e) {
                logger.warning("解析仓库索引失败: " + indexUrl + "，原因: " + e.getMessage());
                return new ChannelUpdateResult(pluginId, StatusCode.SKIPPED.getCode(), null, null);
            }

            if (index == null || index.platform == null || index.platform.isEmpty()) {
                logger.warning("仓库索引缺少 platform 定义: " + indexUrl);
                return new ChannelUpdateResult(pluginId, StatusCode.SKIPPED.getCode(), null, null);
            }

            String filename = index.platform.get(platformName);
            if (filename == null || filename.isBlank()) {
                filename = index.platform.get("universal");
                debug(format("{0}: 未命中平台配置，回退 universal", pluginId));
            }

            if (filename == null || filename.isBlank()) {
                logger.warning("插件 " + pluginId + " 在仓库 " + repository + " 中没有可用配置。");
                return new ChannelUpdateResult(pluginId, StatusCode.SKIPPED.getCode(), null, null);
            }

            String configUrl = repository + "/channels/" + pluginId + "/" + filename;
            debug(format("{0}: 尝试下载配置: {1}", pluginId, configUrl));
            try {
                ChannelUpdateResult cachedConfig = remoteConfigCache.get(pluginId);
                String reqIfModifiedSince = cachedConfig != null ? cachedConfig.lastModified() : null;
                Utils.Http.Response configResponse = Utils.Http.get(configUrl, reqIfModifiedSince);

                String remoteConfigText = null;
                String lastModified = null;

                if (configResponse.statusCode == 200) {
                    if (configResponse.content != null && !configResponse.content.isBlank()) {
                        debug("配置下载成功: " + pluginId + " @ " + configUrl);
                        remoteConfigText = configResponse.content;
                        lastModified = configResponse.lastModified;
                    }
                } else if (configResponse.statusCode == 304) {
                    debug(format("{0}: 返回 304，使用上一次拉取的更新配置", pluginId));
                    if (cachedConfig != null) {
                        remoteConfigText = cachedConfig.configText();
                        lastModified = cachedConfig.lastModified();
                    }
                } else {
                    logger.warning("下载配置失败: " + configUrl + "，状态码: " + configResponse.statusCode);
                }

                if (remoteConfigText != null) {
                    long remoteLastUpdate = getLastUpdateFromJson(remoteConfigText);
                    Path localConfigPath = platform.getDataPath().resolve("channels").resolve(pluginId + ".json");
                    debug(format("{2}: 远程 last_update: {0}，本地路径: {1}", remoteLastUpdate, localConfigPath, pluginId));

                    int pluginStatusCode = StatusCode.AVAILABLE.getCode();

                    if (Files.exists(localConfigPath)) {
                        long localLastUpdate = 0L;
                        try {
                            localLastUpdate = getLastUpdateFromJson(Files.readString(localConfigPath));
                        } catch (Exception e) {
                            logger.warning(pluginId + ": 读取本地配置失败: " + localConfigPath + "，原因: " + e.getMessage());
                        }

                        debug(format("{0}: 本地 last_update: {1}", pluginId, localLastUpdate));
                        if (remoteLastUpdate > localLastUpdate) {
                            debug(format("{0}: 检测到可更新配置", pluginId));
                            pluginStatusCode |= StatusCode.UPDATABLE.getCode();
                        }
                    }

                    ChannelUpdateResult updateResult = new ChannelUpdateResult(pluginId, pluginStatusCode, remoteConfigText, lastModified);
                    if (configResponse.statusCode == 200) {
                        remoteConfigCache.put(pluginId, updateResult);
                    }
                    return updateResult;
                } else {
                    return new ChannelUpdateResult(pluginId, StatusCode.SKIPPED.getCode(), null, null);
                }
            } catch (Exception e) {
                logger.warning("下载配置失败: " + configUrl + "，原因: " + e);
                return new ChannelUpdateResult(pluginId, StatusCode.SKIPPED.getCode(), null, null);
            }
        }

        return new ChannelUpdateResult(pluginId, StatusCode.NORMAL.getCode(), null, null);
    }

    private long getLastUpdateFromJson(String jsonText) {
        if (jsonText == null || jsonText.isBlank()) {
            return 0L;
        }

        try {
            JsonObject obj = Utils.getGson().fromJson(jsonText, JsonObject.class);
            if (obj != null && obj.has("last_update") && obj.get("last_update").isJsonPrimitive()) {
                return obj.get("last_update").getAsLong();
            }
        } catch (Exception ignored) {
        }

        try {
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
        } catch (Exception ignored) {
            return 0L;
        }
    }

    private static class RepoIndex {
        @SerializedName("name") String name;
        @SerializedName("platform") Map<String, String> platform;
    }

    /**
     * 插件更新渠道结果
     * @param pluginId 插件ID
     * @param statusCode 状态码
     */
    public record ChannelUpdateResult(
            String pluginId,
            int statusCode,
            @Nullable String configText,
            @Nullable String lastModified
    ) {
        public boolean available(){
            return (statusCode & StatusCode.AVAILABLE.getCode()) != 0;
        }
        public boolean updatable(){
            return (statusCode & StatusCode.UPDATABLE.getCode()) != 0;
        }
        public boolean skipped(){
            return (statusCode & StatusCode.SKIPPED.getCode()) != 0;
        }
        public boolean latest(){
            return statusCode == StatusCode.NORMAL.getCode();
        }
    }

    public record RepositoryDownloadResult(
            List<String> successList,
            List<String> failedList,
            List<String> skippedList,
            boolean emptyCache
    ) {}
}
