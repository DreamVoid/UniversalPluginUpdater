package me.dreamvoid.universalpluginupdater.service;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import me.dreamvoid.universalpluginupdater.Utils;
import me.dreamvoid.universalpluginupdater.objects.ChannelConfig;
import me.dreamvoid.universalpluginupdater.objects.channel.ModrinthChannelInfo;
import me.dreamvoid.universalpluginupdater.objects.channel.UpdateConfig;
import me.dreamvoid.universalpluginupdater.objects.channel.UrlChannelInfo;
import me.dreamvoid.universalpluginupdater.platform.IPlatformProvider;
import me.dreamvoid.universalpluginupdater.update.AbstractUpdate;
import me.dreamvoid.universalpluginupdater.update.ModrinthUpdate;
import me.dreamvoid.universalpluginupdater.update.URLUpdate;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.logging.Logger;

/**
 * 更新渠道管理器
 * 负责读取配置文件并选择合适的更新渠道
 */
public class UpdateChannelManager {
    private static final Gson gson = new Gson();
    private static final Logger logger = Utils.getLogger();
    private static final Map<String, ChannelDescriptor<?>> CHANNEL_DESCRIPTORS = Map.of(
            // 增加新的更新渠道时，在这里注册
            "url", new ChannelDescriptor<>(
                    UrlChannelInfo.class,
                    new UrlChannelInfo(null),
                    URLUpdate::new
            ),
            "modrinth", new ChannelDescriptor<>(
                    ModrinthChannelInfo.class,
                    new ModrinthChannelInfo(null, false),
                    ModrinthUpdate::new
            )
    );

    private final IPlatformProvider platform;
    /**
     * 缓存AbstractUpdate实例，键为"pluginId:channelType"
     * 这样可以保留HTTP缓存信息（lastModified）供后续请求使用
     */
    private final Map<String, AbstractUpdate> updateInstanceCache = new HashMap<>();
    private final Map<String, Long> pluginConfigFingerprints = new HashMap<>();
    private Long globalConfigFingerprint = null;

    public UpdateChannelManager(IPlatformProvider platform) {
        this.platform = platform;
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
                removePluginCache(pluginId);
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
        UpdateConfig config = merge(getPluginConfig(pluginId));
        if (config == null || config.channels() == null || config.channels().isEmpty()) {
            return null;
        }

        // 获取选择的渠道
        ChannelConfig selectedChannelConfig = selectChannel(config);
        if (selectedChannelConfig == null) {
            return null;
        }

        // 生成缓存键
        String cacheKey = pluginId + ":" + selectedChannelConfig.type();
        
        // 检查缓存中是否已存在该实例
        if (updateInstanceCache.containsKey(cacheKey)) {
            return updateInstanceCache.get(cacheKey);
        }

        // 缓存中不存在，创建新实例
        AbstractUpdate updateInstance = createUpdateInstance(pluginId, selectedChannelConfig);
        if (updateInstance != null) {
            updateInstanceCache.put(cacheKey, updateInstance);
        }
        
        return updateInstance;
    }

    /**
     * 从配置文件加载插件的更新配置
     * @param pluginId 插件标识符（小写）
     * @return 插件更新配置，如果文件不存在或解析失败返回null
     */
    private UpdateConfig getPluginConfig(String pluginId) {
        try {
            Path configPath = getChannelConfigPath(pluginId);
            if (!Files.exists(configPath)) {
                return null;
            }

            String jsonContent = new String(Files.readAllBytes(configPath));
            return gson.fromJson(jsonContent, UpdateConfig.class);
        } catch (IOException e) {
            logger.warning(LanguageService.instance().tr("message.service.channel.error.config.failed", pluginId));
            return null;
        }
    }

    /**
     * 根据配置和用户选择确定使用哪个渠道
     * 优先级：
     * 1. 用户显式选择的渠道
     * 2. 第一个可用的渠道
     * @param config 插件更新配置
     * @return 选中的渠道配置
     */
    private ChannelConfig selectChannel(UpdateConfig config) {
        List<ChannelConfig> channels = config.channels();
        if (channels == null || channels.isEmpty()) {
            return null;
        }

        // 如果只有一个渠道，直接使用
        if (channels.size() == 1) {
            return channels.getFirst();
        }

        // 多个渠道的情况
        String selectedChannel = config.selectedChannel();
        if (selectedChannel != null && !selectedChannel.isEmpty()) {
            // 用户选择了特定的渠道
            for (ChannelConfig channel : channels) {
                if (selectedChannel.equalsIgnoreCase(channel.type())) {
                    return channel;
                }
            }
        }

        // 用户选择的渠道不存在或没有选择，返回第一个可用渠道
        return channels.getFirst();
    }

    /**
     * 根据渠道配置创建对应的AbstractUpdate实现
     *
     * @param pluginId 插件标识符
     * @param channelConfig 渠道配置
     * @return AbstractUpdate实现实例
     */
    private AbstractUpdate createUpdateInstance(String pluginId, ChannelConfig channelConfig) {
        String type = channelConfig.type();
        Object config = channelConfig.config();

        if (type == null) {
            return null;
        }

        try {
            ChannelDescriptor<?> descriptor = getChannelDescriptor(type);
            if (descriptor == null) {
                logger.warning(LanguageService.instance().tr("message.service.channel.error.unknown", type));
                return null;
            }

            return createWithDescriptor(descriptor, pluginId, config);
        } catch (Exception e) {
            logger.warning(LanguageService.instance().tr("message.service.channel.error.exception", type, e));
        }
        return null;
    }

    /**
     * 获取插件配置文件的路径
     * @param pluginId 插件标识符（小写）
     * @return 配置文件路径
     */
    private Path getChannelConfigPath(String pluginId) {
        return platform.getDataPath()
                .resolve("channels")
                .resolve(pluginId + ".json");
    }

    private UpdateConfig merge(UpdateConfig pluginConfig) {
        if (pluginConfig == null) {
            return null;
        }
        return mergeUpdateConfig(pluginConfig, getGlobalConfig());
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
            return gson.fromJson(jsonContent, UpdateConfig.class);
        } catch (IOException e) {
            logger.warning(LanguageService.instance().tr("message.service.channel.error.config.exception", "global", e));
            return null;
        }
    }

    private UpdateConfig mergeUpdateConfig(UpdateConfig pluginConfig, UpdateConfig globalConfig) {
        if (pluginConfig == null) {
            return globalConfig;
        }
        if (globalConfig == null) {
            return pluginConfig;
        }

        String selectedChannel = firstNonBlank(pluginConfig.selectedChannel(), globalConfig.selectedChannel());
        List<ChannelConfig> channels = mergeChannels(pluginConfig.channels(), globalConfig.channels());
        return new UpdateConfig(channels, selectedChannel);
    }

    private List<ChannelConfig> mergeChannels(List<ChannelConfig> pluginChannels, List<ChannelConfig> globalChannels) {
        Map<String, ChannelConfig> globalByType = new LinkedHashMap<>();
        if (globalChannels != null) {
            for (ChannelConfig channel : globalChannels) {
                if (channel != null && channel.type() != null && !channel.type().isBlank()) {
                    globalByType.put(channel.type().toLowerCase(), normalizeChannelConfig(channel));
                }
            }
        }

        List<ChannelConfig> merged = new ArrayList<>();
        if (pluginChannels != null) {
            for (ChannelConfig pluginChannel : pluginChannels) {
                if (pluginChannel == null || pluginChannel.type() == null || pluginChannel.type().isBlank()) {
                    continue;
                }
                String typeKey = pluginChannel.type().toLowerCase();
                ChannelConfig globalChannel = globalByType.get(typeKey);
                Object mergedConfig = mergeConfigObject(pluginChannel.config(), globalChannel == null ? null : globalChannel.config());
                merged.add(normalizeChannelConfig(new ChannelConfig(pluginChannel.type(), mergedConfig)));
            }
        }

        return merged;
    }

    private Object mergeConfigObject(Object pluginConfig, Object globalConfig) {
        if (pluginConfig == null) {
            return globalConfig;
        }
        if (globalConfig == null) {
            return pluginConfig;
        }

        JsonElement pluginTree = gson.toJsonTree(pluginConfig);
        JsonElement globalTree = gson.toJsonTree(globalConfig);
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
        if (channel == null || channel.type() == null) {
            return channel;
        }

        Object normalizedConfig = normalizeConfigObject(channel.type(), channel.config());
        return new ChannelConfig(channel.type(), normalizedConfig);
    }

    private Object normalizeConfigObject(String channelType, Object source) {
        ChannelDescriptor<?> descriptor = getChannelDescriptor(channelType);
        if (descriptor == null) {
            return source;
        }

        return normalizeWithDescriptor(descriptor, source);
    }

    private static ChannelDescriptor<?> getChannelDescriptor(String channelType) {
        if (channelType == null) {
            return null;
        }
        return CHANNEL_DESCRIPTORS.get(channelType.toLowerCase());
    }

    private Map<String, Long> collectPluginConfigFingerprints(Path channelsDir) {
        Map<String, Long> result = new HashMap<>();
        if (!Files.isDirectory(channelsDir)) {
            return result;
        }

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

        return result;
    }

    private Long getFileFingerprint(Path file) {
        if (!Files.isRegularFile(file)) {
            return null;
        }
        try {
            long size = Files.size(file);
            long modified = Files.getLastModifiedTime(file).toMillis();
            return (long) Objects.hash(size, modified);
        } catch (IOException ignored) {
            return null;
        }
    }

    private void removePluginCache(String pluginId) {
        String prefix = pluginId + ":";
        updateInstanceCache.keySet().removeIf(key -> key.startsWith(prefix));
    }

    private <T> Object normalizeWithDescriptor(ChannelDescriptor<T> descriptor, Object source) {
        return parseWithDefaults(source, descriptor.infoClass(), descriptor.defaults());
    }

    private <T> AbstractUpdate createWithDescriptor(ChannelDescriptor<T> descriptor, String pluginId, Object source) {
        T info = parseWithDefaults(source, descriptor.infoClass(), descriptor.defaults());
        return descriptor.factory().create(pluginId, info, platform);
    }

    private <T> T parseWithDefaults(Object source, Class<T> clazz, T defaults) {
        JsonElement defaultTree = gson.toJsonTree(defaults);
        if (!defaultTree.isJsonObject()) {
            return defaults;
        }

        JsonObject merged = defaultTree.getAsJsonObject().deepCopy();
        JsonElement sourceTree = gson.toJsonTree(source);
        if (sourceTree != null && sourceTree.isJsonObject()) {
            for (Map.Entry<String, JsonElement> entry : sourceTree.getAsJsonObject().entrySet()) {
                JsonElement value = entry.getValue();
                if (value != null && !value.isJsonNull()) {
                    merged.add(entry.getKey(), value);
                }
            }
        }

        return gson.fromJson(merged, clazz);
    }

    private static String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        if (second != null && !second.isBlank()) {
            return second;
        }
        return null;
    }

    @FunctionalInterface
    private interface ChannelFactory<T> {
        AbstractUpdate create(String pluginId, T info, IPlatformProvider platform);
    }

    private record ChannelDescriptor<T>(Class<T> infoClass, T defaults, ChannelFactory<T> factory) { }

}
