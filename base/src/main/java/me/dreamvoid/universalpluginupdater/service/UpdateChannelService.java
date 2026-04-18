package me.dreamvoid.universalpluginupdater.service;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import me.dreamvoid.universalpluginupdater.Utils;
import me.dreamvoid.universalpluginupdater.objects.ChannelConfig;
import me.dreamvoid.universalpluginupdater.objects.channel.info.ModrinthChannelInfo;
import me.dreamvoid.universalpluginupdater.objects.channel.info.HangarChannelInfo;
import me.dreamvoid.universalpluginupdater.objects.channel.info.SpigotMCChannelInfo;
import me.dreamvoid.universalpluginupdater.objects.channel.UpdateConfig;
import me.dreamvoid.universalpluginupdater.objects.channel.info.UrlChannelInfo;
import me.dreamvoid.universalpluginupdater.objects.channel.info.GitHubChannelInfo;
import me.dreamvoid.universalpluginupdater.platform.Platform;
import me.dreamvoid.universalpluginupdater.update.AbstractUpdate;
import me.dreamvoid.universalpluginupdater.update.ModrinthUpdate;
import me.dreamvoid.universalpluginupdater.update.URLUpdate;
import me.dreamvoid.universalpluginupdater.update.GitHubUpdate;
import me.dreamvoid.universalpluginupdater.update.HangarUpdate;
import me.dreamvoid.universalpluginupdater.update.SpigotMCUpdate;
import me.dreamvoid.universalpluginupdater.update.UpdateType;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.logging.Logger;

import static me.dreamvoid.universalpluginupdater.Utils.debug;
import static me.dreamvoid.universalpluginupdater.service.LanguageManager.*;

/**
 * 更新渠道服务<br>
 * 负责读取配置文件并选择合适的更新渠道<br>
 * 此服务在 {@link UpdateManager} 实例化，并由其内部调用
 */
public final class UpdateChannelService {
    private final Platform platform;
    private final Logger logger;
    private Long globalConfigFingerprint = null;

    /**
     * 可用更新渠道注册
     */
    private static final Map<UpdateType, ChannelDescriptor<?>> INTERNAL_CHANNEL_DESCRIPTORS = new HashMap<>();
    private static final Map<String, AbstractUpdate> EXTERNAL_CHANNEL_INSTANCES = new HashMap<>();
    /**
     * 缓存AbstractUpdate实例，键为"pluginId:channelType"
     */
    private final Map<String, AbstractUpdate> updateInstanceCache = new HashMap<>();
    private final Map<String, Long> pluginConfigFingerprints = new HashMap<>();

    UpdateChannelService(Platform platform) {
        this.platform = platform;
        this.logger = platform.getPlatformLogger();

        // 注册内部更新渠道
        registerChannel(UpdateType.URL, UrlChannelInfo.class, new UrlChannelInfo(null), (pluginId, info) -> new URLUpdate(pluginId, info, platform));
        registerChannel(UpdateType.Modrinth, ModrinthChannelInfo.class, new ModrinthChannelInfo(null, false), (pluginId, info) -> new ModrinthUpdate(pluginId, info, platform));
        registerChannel(UpdateType.GitHub, GitHubChannelInfo.class, new GitHubChannelInfo(null, null, "application/java-archive", null), (pluginId, info) -> new GitHubUpdate(pluginId, info, platform));
        registerChannel(UpdateType.Hangar, HangarChannelInfo.class, new HangarChannelInfo(null, null, null, null), (pluginId, info) -> new HangarUpdate(pluginId, info, platform));
        registerChannel(UpdateType.SpigotMC, SpigotMCChannelInfo.class, new SpigotMCChannelInfo(null, false), (pluginId, info) -> new SpigotMCUpdate(pluginId, info, platform));
    }

    /**
     * 注册内部更新渠道
     */
    private static synchronized <T> void registerChannel(UpdateType type, Class<T> infoClass, T defaults, ChannelFactory<T> factory) {
        if (type == null || infoClass == null || defaults == null || factory == null) {
            throw new IllegalArgumentException("Invalid update channel registration arguments");
        }
        INTERNAL_CHANNEL_DESCRIPTORS.put(type, new ChannelDescriptor<>(infoClass, defaults, factory));
    }

    /**
     * 注册外部更新实例
     * @throws IllegalArgumentException updateInstance 为 null 时<br>{@link AbstractUpdate#getPluginId()} 为 null 时<br>{@link AbstractUpdate#getType()} 不为 {@link UpdateType#Plugin} 时
     */
    static synchronized void registerInstance(AbstractUpdate updateInstance) throws IllegalArgumentException {
        validateExternalInstance(updateInstance);
        EXTERNAL_CHANNEL_INSTANCES.put(updateInstance.getPluginId().toLowerCase(), updateInstance);
    }

    /**
     * 验证外部更新实例是否有效
     * @param updateInstance 更新实例
     * @throws IllegalArgumentException updateInstance 为 null 时<br>{@link AbstractUpdate#getPluginId()} 为 null 时<br>{@link AbstractUpdate#getType()} 不为 {@link UpdateType#Plugin} 时
     */
    private static void validateExternalInstance(AbstractUpdate updateInstance) throws IllegalArgumentException {
        if (updateInstance == null || updateInstance.getPluginId() == null || updateInstance.getPluginId().isBlank()) {
            throw new IllegalArgumentException("Invalid instance or pluginId is empty");
        }

        UpdateType updateType = updateInstance.getType();
        if (updateType != UpdateType.Plugin) {
            // 获取来源
            String callerSource = null;
            StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
            boolean foundThisClass = false;
            for (StackTraceElement element : stackTrace) {
                String className = element.getClassName();
                String methodName = element.getMethodName();

                if (UpdateChannelService.class.getName().equals(className)) {
                    foundThisClass = true;
                    continue;
                }

                if (foundThisClass && !"java.lang.Thread".equals(className) && !"getStackTrace".equals(methodName)) {
                    callerSource = className + "#" + methodName + ":" + element.getLineNumber();
                    break;
                }
            }
            if (callerSource == null) {
                callerSource = "unknown";
            }

            // 获取来源
            Utils.getLogger().warning(tr("message.service.channel.warn.illegal-type", callerSource, updateType, UpdateType.Plugin));
            throw new IllegalArgumentException("Except update type \"" + UpdateType.Plugin + "\", but got \"" + updateType + "\"");
        }
    }

    /**
     * 注销外部更新实例
     */
    static synchronized void unregisterUpdateInstance(String pluginId) {
        if (pluginId != null && !pluginId.isBlank()) {
            EXTERNAL_CHANNEL_INSTANCES.remove(pluginId.toLowerCase());
        }
    }

    /**
     * 验证缓存，并移除失效的缓存
     */
    synchronized void validateCache() {
        Path channelsDir = platform.getDataPath().resolve("channels");
        Path globalFile = platform.getDataPath().resolve("global.json");

        Map<String, Long> currentPluginFingerprints = collectPluginConfigFingerprints(channelsDir);
        Long globalFingerprint = getFileFingerprint(globalFile);

        if (!Objects.equals(globalConfigFingerprint, globalFingerprint)) {
            updateInstanceCache.clear(); // 全局配置更改
            debug("全局配置更改，清除缓存");
        } else {
            Set<String> changedPlugins = new HashSet<>();

            for (Map.Entry<String, Long> entry : currentPluginFingerprints.entrySet()) {
                String pluginId = entry.getKey();
                Long oldFingerprint = pluginConfigFingerprints.get(pluginId);
                if (!Objects.equals(oldFingerprint, entry.getValue())) {
                    changedPlugins.add(pluginId);
                }
            }

            for (String pluginId : pluginConfigFingerprints.keySet()) {
                if (!currentPluginFingerprints.containsKey(pluginId)) {
                    changedPlugins.add(pluginId);
                }
            }

            for (String pluginId : changedPlugins) {
                updateInstanceCache.keySet().removeIf(key -> key.startsWith(pluginId + ":"));
                debug("{0}: 插件更新配置更改，清除缓存", pluginId);
            }
        }

        pluginConfigFingerprints.clear();
        pluginConfigFingerprints.putAll(currentPluginFingerprints);
        globalConfigFingerprint = globalFingerprint;
    }

    /**
     * 获取指定插件ID对应的渠道候选列表（按配置顺序）
     * @param pluginId 插件标识符（小写）
     * @return 渠道候选配置列表
     */
    List<ChannelConfig> getChannelCandidates(String pluginId) {
        if (pluginId == null || pluginId.isBlank()) {
            return Collections.emptyList();
        }

        pluginId = pluginId.toLowerCase();
        UpdateConfig pluginConfig = getPluginConfig(pluginId);
        UpdateConfig mergedConfig = pluginConfig != null ? mergeUpdateConfig(pluginConfig, getGlobalConfig()) : null;
        List<ChannelConfig> candidates = buildChannelCandidates(pluginId, mergedConfig);

        if (candidates.isEmpty()) {
            debug("{0}: 未找到可用更新渠道", pluginId);
        }

        return candidates;
    }

    /**
     * 按需获取或创建指定渠道的更新实例（不执行 update）
     */
    AbstractUpdate getUpdateInstance(String pluginId, ChannelConfig candidate) {
        if (pluginId == null || pluginId.isBlank()) {
            return null;
        }

        pluginId = pluginId.toLowerCase();

        if (candidate == null) {
            for (Map.Entry<String, AbstractUpdate> entry : updateInstanceCache.entrySet()) {
                if (entry.getKey() != null && entry.getKey().startsWith(pluginId + ":")) {
                    return entry.getValue();
                }
            }
            return null;
        }

        String channelType = candidate.type();
        UpdateType type = UpdateType.fromIdentifier(channelType);
        if (type == null) {
            return null;
        }

        String cacheKey = pluginId + ":" + channelType.toLowerCase();

        AbstractUpdate externalUpdate = EXTERNAL_CHANNEL_INSTANCES.get(pluginId);
        if (externalUpdate != null && externalUpdate.getType() == type) {
            updateInstanceCache.put(cacheKey, externalUpdate);
            return externalUpdate;
        }

        if (updateInstanceCache.containsKey(cacheKey)) {
            debug("{0}: 使用缓存更新实例，渠道 {1}", pluginId, channelType);
            return updateInstanceCache.get(cacheKey);
        }

        try {
            Object config = candidate.config();
            ChannelDescriptor<?> descriptor = getChannelDescriptor(type);
            if (descriptor == null) {
                logger.warning(tr("message.service.channel.error.unknown", channelType));
                return null;
            }

            AbstractUpdate configUpdate = createWithDescriptor(descriptor, pluginId, config);
            updateInstanceCache.put(cacheKey, configUpdate);
            debug("{0}: 创建更新实例，渠道 {1}", pluginId, channelType);
            return configUpdate;
        } catch (IllegalArgumentException e) {
            debug("{0}: 忽略无效的渠道配置: {1}", pluginId, channelType);
            return null;
        } catch (Exception e) {
            logger.warning(tr("message.service.channel.error.exception", channelType, e));
            return null;
        }
    }

    /**
     * 从配置文件加载插件的更新配置
     * @param pluginId 插件标识符（小写）
     * @return 插件更新配置，如果文件不存在或解析失败返回null
     */
    private UpdateConfig getPluginConfig(String pluginId) {
        try {
            Path configPath = platform.getDataPath().resolve("channels").resolve(pluginId + ".json");
            if (Files.exists(configPath)) {
                String jsonContent = new String(Files.readAllBytes(configPath));
                return Utils.getGson().fromJson(jsonContent, UpdateConfig.class);
            } else {
                debug("{0}: 渠道配置文件不存在: {1}", pluginId, configPath);
            }
        } catch (IOException e) {
            logger.warning(tr("message.service.channel.error.config.failed", pluginId));
        }
        return null;
    }

    /**
     * 构建渠道候选列表
     * 优先级：
     * 1. 用户显式选择的渠道（若存在于候选中）
     * 2. 其余渠道按声明顺序
     * <p>
    * 注意：这里不判定“有效性”，有效性由 getOrCreateUpdateInstance 决定。
     */
    private List<ChannelConfig> buildChannelCandidates(String pluginId, UpdateConfig config) {
        List<ChannelConfig> channels = new ArrayList<>();

        AbstractUpdate externalUpdate = EXTERNAL_CHANNEL_INSTANCES.get(pluginId.toLowerCase());
        if (externalUpdate != null && externalUpdate.getType() != null) {
            channels.add(new ChannelConfig(externalUpdate.getType().getIdentifier(), new JsonObject(), null));
        }

        if (config != null && config.channels() != null) {
            for (ChannelConfig c : config.channels()) {
                if (c == null) continue;
                boolean exists = false;
                for (int i = 0; i < channels.size(); i++) {
                    if (channels.get(i).type().equalsIgnoreCase(c.type())) {
                        channels.set(i, c); // 使用用户配置覆盖默认配置
                        exists = true;
                        break;
                    }
                }
                if (!exists) {
                    channels.add(c);
                }
            }
        }

        if (channels.isEmpty()) {
            return Collections.emptyList();
        }

        UpdateType selectedType = UpdateType.fromIdentifier(config == null ? null : config.selectedChannel());
        if (selectedType == null) {
            return channels;
        }

        int selectedIndex = -1;
        for (int i = 0; i < channels.size(); i++) {
            UpdateType type = UpdateType.fromIdentifier(channels.get(i).type());
            if (selectedType.equals(type)) {
                selectedIndex = i;
                break;
            }
        }

        if (selectedIndex <= 0) {
            return channels;
        }

        ChannelConfig selected = channels.remove(selectedIndex);
        channels.add(0, selected);
        return channels;
    }

    private UpdateConfig getGlobalConfig() {
        Path globalPath = platform.getDataPath().resolve("global.json");

        try {
            if (!Files.exists(globalPath)) {
                try (InputStream inputStream = UpdateChannelService.class.getClassLoader().getResourceAsStream("global.json")) {
                    Files.createDirectories(platform.getDataPath());

                    if (inputStream != null) {
                        Files.copy(inputStream, globalPath);
                    }
                }
            }

            String jsonContent = Files.readString(globalPath);
            return Utils.getGson().fromJson(jsonContent, UpdateConfig.class);
        } catch (IOException e) {
            logger.warning(tr("message.service.channel.error.config.exception", "global", e));
            return null;
        }
    }

    private UpdateConfig mergeUpdateConfig(UpdateConfig pluginConfig, UpdateConfig globalConfig) {
        if (pluginConfig == null) return globalConfig;
        if (globalConfig == null) return pluginConfig;

        String selectedChannel = null,
                pluginChannel = pluginConfig.selectedChannel(),
                globalChannel = globalConfig.selectedChannel();

        if (pluginChannel != null && !pluginChannel.isBlank()) {
            selectedChannel = pluginChannel;
        } else if (globalChannel != null && !globalChannel.isBlank()) {
            selectedChannel = globalChannel;
        }

        List<ChannelConfig> channels = mergeChannels(pluginConfig.channels(), globalConfig.channels());
        return new UpdateConfig(channels, selectedChannel);
    }

    private List<ChannelConfig> mergeChannels(List<ChannelConfig> pluginChannels, List<ChannelConfig> globalChannels) {
        Map<UpdateType, ChannelConfig> globalByType = new LinkedHashMap<>();
        if (globalChannels != null) {
            for (ChannelConfig channel : globalChannels) {
                UpdateType channelType = UpdateType.fromIdentifier(channel == null ? null : channel.type());
                if (channelType != null) {
                    globalByType.put(channelType, normalizeChannelConfig(channel));
                }
            }
        }

        List<ChannelConfig> merged = new ArrayList<>();
        if (pluginChannels != null) {
            for (ChannelConfig pluginChannel : pluginChannels) {
                UpdateType pluginChannelType = UpdateType.fromIdentifier(pluginChannel == null ? null : pluginChannel.type());
                if (pluginChannelType == null) {
                    continue;
                }
                ChannelConfig globalChannel = globalByType.get(pluginChannelType);
                Object mergedConfig = mergeConfig(pluginChannel.config(), globalChannel == null ? null : globalChannel.config());
                merged.add(normalizeChannelConfig(new ChannelConfig(pluginChannel.type(), mergedConfig, pluginChannel.lastUpdate())));
            }
        }

        return merged;
    }

    private Object mergeConfig(Object pluginConfig, Object globalConfig) {
        if (pluginConfig == null) {
            return globalConfig;
        }
        if (globalConfig == null) {
            return pluginConfig;
        }

        JsonElement pluginTree = Utils.getGson().toJsonTree(pluginConfig);
        JsonElement globalTree = Utils.getGson().toJsonTree(globalConfig);
        if (!pluginTree.isJsonObject() || !globalTree.isJsonObject()) {
            return pluginConfig;
        }

        JsonObject merged = globalTree.getAsJsonObject().deepCopy();
        for (Map.Entry<String, JsonElement> entry : pluginTree.getAsJsonObject().entrySet()) {
            JsonElement value = entry.getValue();
            if (value != null && !value.isJsonNull()) {
                merged.add(entry.getKey(), value);
            }
        }
        return merged;
    }

    private ChannelConfig normalizeChannelConfig(ChannelConfig channel) {
        UpdateType type = UpdateType.fromIdentifier(channel == null ? null : channel.type());
        return type == null ? channel : new ChannelConfig(channel.type(), normalizeConfigObject(type, channel.config()), channel.lastUpdate());
    }

    private Object normalizeConfigObject(UpdateType channelType, Object source) {
        ChannelDescriptor<?> descriptor = getChannelDescriptor(channelType);
        return descriptor != null ? normalizeWithDescriptor(descriptor, source) : source;
    }

    private static ChannelDescriptor<?> getChannelDescriptor(UpdateType channelType) {
        return channelType != null ? INTERNAL_CHANNEL_DESCRIPTORS.get(channelType) : null;
    }

    private Map<String, Long> collectPluginConfigFingerprints(Path channelsDir) {
        Map<String, Long> result = new HashMap<>();
        if (Files.isDirectory(channelsDir)) {
            try (var paths = Files.list(channelsDir)) {
                paths.filter(Files::isRegularFile)
                        .filter(path -> path.getFileName().toString().toLowerCase().endsWith(".json"))
                        .forEach(path -> {
                            String filename = path.getFileName().toString();
                            int dotIndex = filename.lastIndexOf('.');
                            if (dotIndex <= 0) {
                                return;
                            }
                            String pluginId = filename.substring(0, dotIndex).toLowerCase();
                            Long fingerprint = getFileFingerprint(path);
                            if (fingerprint != null) {
                                result.put(pluginId, fingerprint);
                            }
                        });
            } catch (IOException ignored) {}
        }
        return result;
    }

    private Long getFileFingerprint(Path file) {
        if (Files.isRegularFile(file)) try {
            long size = Files.size(file);
            long modified = Files.getLastModifiedTime(file).toMillis();
            return (long) Objects.hash(size, modified);
        } catch (IOException ignored) {}
        return null;
    }

    private <T> Object normalizeWithDescriptor(ChannelDescriptor<T> descriptor, Object source) {
        return parseWithDefaults(source, descriptor.infoClass(), descriptor.defaults());
    }

    private <T> AbstractUpdate createWithDescriptor(ChannelDescriptor<T> descriptor, String pluginId, Object source) {
        T info = parseWithDefaults(source, descriptor.infoClass(), descriptor.defaults());
        return descriptor.factory().create(pluginId, info);
    }

    private <T> T parseWithDefaults(Object source, Class<T> clazz, T defaults) {
        JsonElement defaultTree = Utils.getGson().toJsonTree(defaults);
        if (defaultTree.isJsonObject()) {
            JsonObject merged = defaultTree.getAsJsonObject().deepCopy();
            JsonElement sourceTree = Utils.getGson().toJsonTree(source);
            if (sourceTree != null && sourceTree.isJsonObject()) {
                for (Map.Entry<String, JsonElement> entry : sourceTree.getAsJsonObject().entrySet()) {
                    JsonElement value = entry.getValue();
                    if (value != null && !value.isJsonNull()) {
                        merged.add(entry.getKey(), value);
                    }
                }
            }
            return Utils.getGson().fromJson(merged, clazz);
        } else {
            return defaults;
        }
    }

    @FunctionalInterface
    private interface ChannelFactory<T> {
        AbstractUpdate create(String pluginId, T info);
    }

    private record ChannelDescriptor<T>(Class<T> infoClass, T defaults, ChannelFactory<T> factory) { }
}
