package me.dreamvoid.universalpluginupdater.service;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import me.dreamvoid.universalpluginupdater.Utils;
import me.dreamvoid.universalpluginupdater.objects.ChannelConfig;
import me.dreamvoid.universalpluginupdater.objects.channel.ModrinthChannelInfo;
import me.dreamvoid.universalpluginupdater.objects.channel.UpdateConfig;
import me.dreamvoid.universalpluginupdater.objects.channel.UrlChannelInfo;
import me.dreamvoid.universalpluginupdater.platform.Platform;
import me.dreamvoid.universalpluginupdater.update.AbstractUpdate;
import me.dreamvoid.universalpluginupdater.update.ModrinthUpdate;
import me.dreamvoid.universalpluginupdater.update.URLUpdate;
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
 * 更新渠道管理器
 * 负责读取配置文件并选择合适的更新渠道
 */
public final class UpdateChannelManager {
    /**
     * 可用更新渠道注册
     */
    private static final Map<UpdateType, ChannelDescriptor<?>> CHANNEL_DESCRIPTORS = new HashMap<>();
    private static final Map<String, AbstractUpdate> EXTERNAL_UPDATE_INSTANCES = new HashMap<>();

    /**
     * 注册更新渠道（供扩展调用）
     */
    public static synchronized <T> void registerChannel(UpdateType type, Class<T> infoClass, T defaults, ChannelFactory<T> factory) {
        if (type == null || infoClass == null || defaults == null || factory == null) {
            throw new IllegalArgumentException("Invalid update channel registration arguments");
        }
        CHANNEL_DESCRIPTORS.put(type, new ChannelDescriptor<>(infoClass, defaults, factory));
    }

    /**
     * 注册外部更新实例
     */
    public static synchronized void registerUpdateInstance(AbstractUpdate updateInstance) {
        validateExternalUpdateInstance(updateInstance);
        EXTERNAL_UPDATE_INSTANCES.put(updateInstance.getPluginId().toLowerCase(), updateInstance);
    }

    private static void validateExternalUpdateInstance(AbstractUpdate updateInstance) {
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

                if (UpdateChannelManager.class.getName().equals(className)) {
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
    public static synchronized void unregisterUpdateInstance(String pluginId) {
        if (pluginId != null && !pluginId.isBlank()) {
            EXTERNAL_UPDATE_INSTANCES.remove(pluginId.toLowerCase());
        }
    }

    private final Platform platform;
    private final Logger logger;
    /**
     * 缓存AbstractUpdate实例，键为"pluginId:channelType"
     * 这样可以保留HTTP缓存信息（lastModified）供后续请求使用
     */
    private final Map<String, AbstractUpdate> updateInstanceCache = new HashMap<>();
    private final Map<String, Long> pluginConfigFingerprints = new HashMap<>();
    private Long globalConfigFingerprint = null;

    public UpdateChannelManager(Platform platform) {
        this.platform = platform;
        this.logger = platform.getPlatformLogger();

        registerChannel(UpdateType.URL, UrlChannelInfo.class, new UrlChannelInfo(null), (pluginId, info) -> new URLUpdate(pluginId, info, platform));
        registerChannel(UpdateType.Modrinth, ModrinthChannelInfo.class, new ModrinthChannelInfo(null, false), (pluginId, info) -> new ModrinthUpdate(pluginId, info, platform));
    }

    /**
     * 验证缓存，并移除失效的缓存
     */
    public synchronized void validateCache() {
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
     * 获取指定插件ID对应的更新实例
     * @param pluginId 插件标识符（小写）
     * @return 选中的AbstractUpdate实现，如果配置不存在或解析失败返回null
     */
    public AbstractUpdate getUpdateChannelForPlugin(String pluginId) {
        if (pluginId == null || pluginId.isEmpty()) {
            return null;
        }

        // 将pluginId转换为小写确保统一
        pluginId = pluginId.toLowerCase();

        // 读取并合并配置文件
        UpdateConfig pluginConfig = getPluginConfig(pluginId);
        UpdateConfig config = pluginConfig != null ? mergeUpdateConfig(pluginConfig, getGlobalConfig()) : null;

        // 按原有配置逻辑选择渠道并创建实例（保留缓存行为）
        ChannelConfig selectedChannelConfig = selectChannel(pluginId, config);
        if (selectedChannelConfig != null) {
            String channelType = selectedChannelConfig.type();
            String cacheKey = (channelType == null || channelType.isBlank()) ? null : pluginId + ":" + channelType.toLowerCase();

            if (cacheKey != null && updateInstanceCache.containsKey(cacheKey)) {
                debug("{0}: 使用缓存更新实例，渠道 {1}", pluginId, channelType);
                return updateInstanceCache.get(cacheKey);
            }

            AbstractUpdate configUpdate = createUpdateInstance(pluginId, selectedChannelConfig);
            if (configUpdate != null) {
                if (cacheKey != null) {
                    updateInstanceCache.put(cacheKey, configUpdate);
                    debug("{0}: 创建更新实例，渠道 {1}", pluginId, channelType);
                }
                return configUpdate;
            }
        }

        debug("未找到可用更新渠道: {0}", pluginId);
        return null;
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
     * 根据配置和用户选择确定使用哪个渠道
     * 优先级：
     * 1. 用户显式选择的渠道
     * 2. 第一个可用的渠道（插件注册的渠道作为第一个可用候选）
     * @param pluginId 插件标识符
     * @param config 插件更新配置
     * @return 选中的渠道配置
     */
    private ChannelConfig selectChannel(String pluginId, UpdateConfig config) {
        List<ChannelConfig> channels = new ArrayList<>();

        AbstractUpdate externalUpdate = EXTERNAL_UPDATE_INSTANCES.get(pluginId.toLowerCase());
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
            return null;
        }

        // 如果只有一个渠道，直接使用
        if (channels.size() == 1) {
            return channels.getFirst();
        }

        // 多个渠道的情况，优先使用用户选择
        UpdateType selectedType = UpdateType.fromIdentifier(config.selectedChannel());
        for (ChannelConfig channel : channels) {
            UpdateType type = UpdateType.fromIdentifier(channel.type());
            if (selectedType != null) {
                if (selectedType.equals(type)) {
                    return channel;
                }
            } else if (type != null) { // 用户没有选择，返回第一个可用渠道
                return channel;
            }
        }

        return null;
    }

    /**
     * 根据渠道配置创建对应的AbstractUpdate实现
     *
     * @param pluginId 插件标识符
     * @param channelConfig 渠道配置
     * @return AbstractUpdate实现实例
     */
    private AbstractUpdate createUpdateInstance(String pluginId, ChannelConfig channelConfig) {
        UpdateType type = UpdateType.fromIdentifier(channelConfig == null ? null : channelConfig.type());
        if (type == null) {
            return null;
        }

        AbstractUpdate externalUpdate = EXTERNAL_UPDATE_INSTANCES.get(pluginId.toLowerCase());
        if (externalUpdate != null && externalUpdate.getType() == type) {
            return externalUpdate;
        }

        try {
            Object config = channelConfig.config();
            ChannelDescriptor<?> descriptor = getChannelDescriptor(type);
            if (descriptor != null) {
                return createWithDescriptor(descriptor, pluginId, config);
            }
            logger.warning(tr("message.service.channel.error.unknown", channelConfig.type()));
            return null;
        } catch (Exception e) {
            logger.warning(tr("message.service.channel.error.exception", channelConfig.type(), e));
        }
        return null;
    }

    private UpdateConfig getGlobalConfig() {
        Path globalPath = platform.getDataPath().resolve("global.json");

        try {
            if (!Files.exists(globalPath)) {
                try (InputStream inputStream = UpdateChannelManager.class.getClassLoader().getResourceAsStream("global.json")) {
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
        return channelType != null ? CHANNEL_DESCRIPTORS.get(channelType) : null;
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
    public interface ChannelFactory<T> {
        AbstractUpdate create(String pluginId, T info);
    }

    private record ChannelDescriptor<T>(Class<T> infoClass, T defaults, ChannelFactory<T> factory) { }
}
