package me.dreamvoid.universalpluginupdater.service;

import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;
import me.dreamvoid.universalpluginupdater.Utils;
import me.dreamvoid.universalpluginupdater.objects.ChannelConfig;
import me.dreamvoid.universalpluginupdater.objects.channel.UpdateConfig;
import me.dreamvoid.universalpluginupdater.platform.Platform;

import java.io.InputStream;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.*;
import java.util.logging.Logger;

import static me.dreamvoid.universalpluginupdater.Utils.debug;

/**
 * 仓库同步服务
 */
public class RepositorySyncService {
    private static final String DEFAULT_REPOSITORY = "https://repo.upu.dreamvoid.me/";
    private static final String REPOSITORIES_RESOURCE = "repositories.json";
    public static final int STATUS_AVAILABLE = 0b01;
    public static final int STATUS_UPDATABLE = 0b10;

    private final Platform platform;
    private final Logger logger;
    private final Map<String, RepoDownloadCandidate> cachedCandidates = new LinkedHashMap<>();
    private final List<RepoCheckEntry> cachedCheckEntries = new ArrayList<>();
    private final List<String> cachedFailedList = new ArrayList<>();

    public RepositorySyncService(Platform platform) {
        this.platform = platform;
        this.logger = platform.getPlatformLogger();
    }

    public RepositoryCheckResult check() {
        List<String> repositories = loadRepositories();
        debug("仓库列表加载完成，数量: " + repositories.size());

        String platformName = platform.getPlatformName() == null ? "" : platform.getPlatformName().toLowerCase(Locale.ROOT);
        List<String> plugins = platform.getPlugins();
        if (plugins == null) {
            plugins = new ArrayList<>();
        }
        debug("开始同步，平台: " + platformName + "，插件数量: " + plugins.size());

        List<String> failedList = new ArrayList<>();
        cachedCandidates.clear();
        cachedCheckEntries.clear();
        cachedFailedList.clear();

        for (String pluginIdRaw : plugins) {
            if (pluginIdRaw == null || pluginIdRaw.isBlank()) {
                debug("跳过空插件ID");
                continue;
            }

            debug(MessageFormat.format("{0}: 开始检查更新配置", pluginIdRaw));

            String pluginId = pluginIdRaw.toLowerCase(Locale.ROOT);
            PluginRepoConfigResult result = resolvePluginConfig(pluginId, platformName, repositories);
            if (!result.foundInRepository()) {
                debug(MessageFormat.format("{0}: 未在任何仓库中找到插件配置索引", pluginId));
                continue;
            }

            if (!result.success()) {
                debug(MessageFormat.format("{0}: 仓库中存在插件索引但解析/下载失败", pluginId));
                failedList.add(pluginId);
                continue;
            }

            long remoteLastUpdate = extractLastUpdate(result.configText());
            Path localConfigPath = platform.getDataPath().resolve("channels").resolve(pluginId + ".json");
            debug(MessageFormat.format("{2}: 远程 last_update: {0}，本地路径: {1}", remoteLastUpdate, localConfigPath, pluginId));

            if (Files.exists(localConfigPath)) {
                long localLastUpdate = extractLastUpdateFromLocal(localConfigPath);
                debug(MessageFormat.format("{1}: 本地 last_update: {0}", localLastUpdate, pluginId));
                if (remoteLastUpdate > localLastUpdate) {
                    debug(MessageFormat.format("{0}: 检测到可更新配置，加入待下载列表", pluginId));
                    cachedCheckEntries.add(new RepoCheckEntry(pluginId, STATUS_UPDATABLE));
                    cachedCandidates.put(pluginId, new RepoDownloadCandidate(pluginId, result.configText(), localConfigPath));
                } else {
                    debug(MessageFormat.format("{0}: 本地配置已是最新，跳过写入", pluginId));
                    cachedCheckEntries.add(new RepoCheckEntry(pluginId, 0));
                }
            } else {
                debug(MessageFormat.format("{0}: 本地无配置，加入待下载列表", pluginId));
                cachedCheckEntries.add(new RepoCheckEntry(pluginId, STATUS_AVAILABLE));
                cachedCandidates.put(pluginId, new RepoDownloadCandidate(pluginId, result.configText(), localConfigPath));
            }
        }

        debug(MessageFormat.format("同步结束，可获取: {0}，可更新: {1}，已最新: {2}，失败: {3}",
                cachedCheckEntries.stream().filter(entry -> isAvailable(entry.statusCode())).count(),
                cachedCheckEntries.stream().filter(entry -> isUpdatable(entry.statusCode())).count(),
                cachedCheckEntries.stream().filter(entry -> entry.statusCode() == 0).count(),
                failedList.size()));

        cachedFailedList.addAll(failedList);

        return new RepositoryCheckResult(new ArrayList<>(cachedCheckEntries), failedList);
    }

    public List<RepoCheckEntry> getCachedCheckEntries() {
        return new ArrayList<>(cachedCheckEntries);
    }

    public List<String> getCachedFailedList() {
        return new ArrayList<>(cachedFailedList);
    }

    public RepositoryDownloadResult download(List<String> pluginIds, boolean downloadAll) {
        List<String> successList = new ArrayList<>();
        List<String> failedList = new ArrayList<>();
        List<String> skippedList = new ArrayList<>();

        if (cachedCandidates.isEmpty()) {
            return new RepositoryDownloadResult(successList, failedList, skippedList, true);
        }

        List<String> targets = new ArrayList<>();
        if (downloadAll) {
            targets.addAll(cachedCandidates.keySet());
        } else {
            for (String pluginId : pluginIds) {
                if (pluginId == null || pluginId.isBlank()) {
                    continue;
                }
                String normalized = pluginId.toLowerCase(Locale.ROOT);
                if (!targets.contains(normalized)) {
                    targets.add(normalized);
                }
            }
        }

        for (String pluginId : targets) {
            RepoDownloadCandidate candidate = cachedCandidates.get(pluginId);
            if (candidate == null) {
                skippedList.add(pluginId);
                continue;
            }

            try {
                Files.createDirectories(candidate.localConfigPath().getParent());
            } catch (Exception e) {
                logger.warning("创建渠道配置目录失败: " + e.getMessage());
                failedList.add(pluginId);
                continue;
            }

            if (writeConfig(candidate.localConfigPath(), candidate.configText())) {
                successList.add(pluginId);
                debug(MessageFormat.format("{0}: 配置下载并写入成功", pluginId));
            } else {
                failedList.add(pluginId);
            }
        }

        return new RepositoryDownloadResult(successList, failedList, skippedList, false);
    }

    private List<String> loadRepositories() {
        Path repositoriesPath = platform.getDataPath().resolve("repositories.json");
        try {
            if (!Files.exists(repositoriesPath)) {
                debug("repositories.json 不存在，尝试从资源释放: " + repositoriesPath);
                Files.createDirectories(platform.getDataPath());
                try (InputStream inputStream = RepositorySyncService.class.getClassLoader().getResourceAsStream(REPOSITORIES_RESOURCE)) {
                    if (inputStream != null) {
                        Files.copy(inputStream, repositoriesPath);
                        debug("已从资源释放 repositories.json");
                    } else {
                        Files.writeString(repositoriesPath, Utils.getGson().toJson(List.of(DEFAULT_REPOSITORY)));
                        debug("资源不存在，写入默认仓库配置");
                    }
                }
            }

            String json = Files.readString(repositoriesPath);
            Type listType = new TypeToken<List<String>>() {}.getType();
            List<String> list = Utils.getGson().fromJson(json, listType);
            if (list == null) {
                debug("repositories.json 解析结果为 null，视为无仓库");
                return new ArrayList<>();
            }

            List<String> normalized = new ArrayList<>();
            for (String item : list) {
                if (item != null && !item.isBlank()) {
                    normalized.add(item.trim());
                }
            }
            debug("repositories.json 解析成功，仓库数量: " + normalized.size());
            return normalized;
        } catch (Exception e) {
            logger.warning("加载 repositories.json 失败，使用默认仓库。原因: " + e.getMessage());
            return List.of(DEFAULT_REPOSITORY);
        }
    }

    private PluginRepoConfigResult resolvePluginConfig(String pluginId, String platformName, List<String> repositories) {
        for (String repository : repositories) {
            String baseUrl = trimRepositoryBase(repository);
            String indexUrl = baseUrl + "/channels/" + pluginId + "/index.json";
            debug(MessageFormat.format("{0}: 尝试仓库索引: {1}", pluginId, indexUrl));

            Utils.Http.Response indexResponse;
            try {
                indexResponse = Utils.Http.get(indexUrl, null);
            } catch (Exception e) {
                logger.warning(MessageFormat.format("{0}: 访问索引失败: {1}，原因: {2}", pluginId, indexUrl, e.getMessage()));
                continue;
            }

            debug(MessageFormat.format("{0}: 索引响应状态: {1}，URL: {2}", pluginId, indexResponse.statusCode, indexUrl));

            if (!(indexResponse.statusCode == 200 || indexResponse.statusCode == 304)) {
                continue;
            }

            if (indexResponse.content == null || indexResponse.content.isBlank()) {
                logger.warning(MessageFormat.format("{0}: 仓库存在索引但内容为空: {1}", pluginId, indexUrl));
                return new PluginRepoConfigResult(true, false, null);
            }

            RepoIndex index;
            try {
                index = Utils.getGson().fromJson(indexResponse.content, RepoIndex.class);
            } catch (Exception e) {
                logger.warning("解析仓库索引失败: " + indexUrl + "，原因: " + e.getMessage());
                return new PluginRepoConfigResult(true, false, null);
            }

            if (index == null || index.platform == null || index.platform.isEmpty()) {
                logger.warning("仓库索引缺少 platform 定义: " + indexUrl);
                return new PluginRepoConfigResult(true, false, null);
            }

            String configFile = index.platform.get(platformName);
            if (configFile == null || configFile.isBlank()) {
                configFile = index.platform.get("universal");
                debug(MessageFormat.format("{0}: 未命中平台配置，回退 universal", pluginId));
            }

            if (configFile == null || configFile.isBlank()) {
                logger.warning("插件 " + pluginId + " 在仓库 " + baseUrl + " 中没有平台(" + platformName + ")或通用配置。");
                return new PluginRepoConfigResult(true, false, null);
            }

            String configUrl = baseUrl + "/channels/" + pluginId + "/" + configFile;
            debug(MessageFormat.format("{0}: 尝试下载配置: {1}", pluginId, configUrl));
            try {
                Utils.Http.Response configResponse = Utils.Http.get(configUrl, null);
                debug(MessageFormat.format("{0}: 响应代码: {1}，URL: {2}", pluginId, configResponse.statusCode, configUrl));
                if (configResponse.statusCode == 200 && configResponse.content != null && !configResponse.content.isBlank()) {
                    debug("配置下载成功: " + pluginId + " @ " + configUrl);
                    return new PluginRepoConfigResult(true, true, configResponse.content);
                }

                if (configResponse.statusCode == 304) {
                    Path localConfigPath = platform.getDataPath().resolve("channels").resolve(pluginId + ".json");
                    if (Files.exists(localConfigPath)) {
                        debug(MessageFormat.format("{0}: 远程304，使用本地配置: {1}", pluginId, localConfigPath));
                        return new PluginRepoConfigResult(true, true, Files.readString(localConfigPath));
                    }
                    debug(MessageFormat.format("{0}: 远程304但本地配置不存在: {1}", pluginId, localConfigPath));
                }

                logger.warning("下载配置失败: " + configUrl + "，状态码: " + configResponse.statusCode);
                return new PluginRepoConfigResult(true, false, null);
            } catch (Exception e) {
                logger.warning("下载配置失败: " + configUrl + "，原因: " + e.getMessage());
                return new PluginRepoConfigResult(true, false, null);
            }
        }

        return new PluginRepoConfigResult(false, false, null);
    }

    private String trimRepositoryBase(String repository) {
        String base = repository == null ? "" : repository.trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base;
    }

    private long extractLastUpdate(String configText) {
        if (configText == null || configText.isBlank()) {
            return 0L;
        }

        try {
            JsonObject obj = Utils.getGson().fromJson(configText, JsonObject.class);
            if (obj != null && obj.has("last_update") && obj.get("last_update").isJsonPrimitive()) {
                return obj.get("last_update").getAsLong();
            }
        } catch (Exception ignored) {
        }

        try {
            UpdateConfig cfg = Utils.getGson().fromJson(configText, UpdateConfig.class);
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

    private long extractLastUpdateFromLocal(Path localConfigPath) {
        try {
            return extractLastUpdate(Files.readString(localConfigPath));
        } catch (Exception e) {
            logger.warning("读取本地配置失败: " + localConfigPath + "，原因: " + e.getMessage());
            return 0L;
        }
    }

    private boolean writeConfig(Path localConfigPath, String content) {
        try {
            Files.writeString(localConfigPath, content);
            return true;
        } catch (Exception e) {
            logger.warning("写入配置失败: " + localConfigPath + "，原因: " + e.getMessage());
            return false;
        }
    }

    private static class RepoIndex {
        @SerializedName("name") String name;
        @SerializedName("platform") Map<String, String> platform;
    }

    private record PluginRepoConfigResult(boolean foundInRepository, boolean success, String configText) {}

    public static boolean isAvailable(int statusCode) {
        return (statusCode & STATUS_AVAILABLE) != 0;
    }

    public static boolean isUpdatable(int statusCode) {
        return (statusCode & STATUS_UPDATABLE) != 0;
    }

    public record RepoCheckEntry(String pluginId, int statusCode) {}

    private record RepoDownloadCandidate(
            String pluginId,
            String configText,
            Path localConfigPath
    ) {}

    public record RepositoryCheckResult(List<RepoCheckEntry> entries, List<String> failedList) {
        public long availableCount() {
            return entries.stream().filter(entry -> isAvailable(entry.statusCode())).count();
        }

        public long updatableCount() {
            return entries.stream().filter(entry -> isUpdatable(entry.statusCode())).count();
        }

        public long latestCount() {
            return entries.stream().filter(entry -> entry.statusCode() == 0).count();
        }
    }

    public record RepositoryDownloadResult(
            List<String> successList,
            List<String> failedList,
            List<String> skippedList,
            boolean emptyCache
    ) {}

}
