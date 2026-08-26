package me.dreamvoid.universalpluginupdater.service;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import me.dreamvoid.universalpluginupdater.Utils;
import me.dreamvoid.universalpluginupdater.objects.ChannelConfig;
import me.dreamvoid.universalpluginupdater.objects.channel.UpdateConfig;
import me.dreamvoid.universalpluginupdater.platform.Platform;
import me.dreamvoid.universalpluginupdater.update.AbstractPluginUpdate;
import me.dreamvoid.universalpluginupdater.update.AbstractUpdate;
import me.dreamvoid.universalpluginupdater.update.UpdateChannel;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.logging.Logger;

import static me.dreamvoid.universalpluginupdater.Utils.debug;
import static me.dreamvoid.universalpluginupdater.service.LanguageManager.tr;

/**
 * 更新渠道服务（仅供内部使用）<br>
 * 负责读取配置文件并选择合适的更新渠道<br>
 * 渠道注册请通过 {@link UpdateManager#registerChannel(Class)}（通用渠道）
 */
final class UpdateChannelService {
    private final Platform platform;
    private final Logger logger;
    private Long globalConfigFingerprint = null;

    /**
     * 通用渠道注册表，键为渠道标识（小写）
     */
    private final Map<String, Class<? extends AbstractUpdate>> genericChannels = new HashMap<>();
    /**
     * 插件专属渠道注册表，键为插件 ID（小写）
     */
    private final Map<String, AbstractPluginUpdate> pluginChannels = new HashMap<>();
    /**
     * 缓存AbstractUpdate实例，键为"pluginId:channelType"
     */
    private final Map<String, AbstractUpdate> updateInstanceCache = new HashMap<>();
    private final Map<String, Long> pluginConfigFingerprints = new HashMap<>();

    UpdateChannelService(Platform platform) {
        this.platform = platform;
        this.logger = platform.getPlatformLogger();
    }

    /**
     * 注册通用更新渠道
     * @param updateClass {@link AbstractUpdate} 的实现类
     * @throws IllegalArgumentException updateClass 为 null、缺少 {@link UpdateChannel} 注解、渠道标识为空或重复时
     */
    synchronized void registerChannel(Class<? extends AbstractUpdate> updateClass) throws IllegalArgumentException {
        if (updateClass == null) {
            throw new IllegalArgumentException("updateClass 不能为 null");
        }

        UpdateChannel annotation = updateClass.getAnnotation(UpdateChannel.class);
        if (annotation == null) {
            throw new IllegalArgumentException("更新渠道类缺少 @UpdateChannel 注解");
        }
        String channelId = annotation.value();
        if (channelId == null || channelId.isBlank()) {
            throw new IllegalArgumentException("更新渠道标识不能为空");
        }

        String key = channelId.toLowerCase();
        if (key.equalsIgnoreCase("plugin")) {
            throw new IllegalArgumentException("非插件更新渠道不能为 \"plugin\"");
        }
        if (genericChannels.containsKey(key)) {
            throw new IllegalArgumentException("更新渠道 \"" + channelId + "\" 已注册");
        }

        genericChannels.put(key, updateClass);
        debug("注册更新渠道: {0}", channelId);
    }

    /**
     * 注册插件专用更新渠道
     * @param updateInstance 实现 {@link AbstractPluginUpdate} 的对象
     * @throws IllegalArgumentException updateInstance 为 null、未指定插件 ID 时
     */
    synchronized void registerChannel(AbstractPluginUpdate updateInstance) throws IllegalArgumentException {
        if (updateInstance == null) {
            throw new IllegalArgumentException("updateInstance 不能为 null");
        }

        UpdateChannel annotation = updateInstance.getClass().getAnnotation(UpdateChannel.class);
        if (annotation == null) {
            throw new IllegalArgumentException("更新渠道类缺少 @UpdateChannel 注解");
        }
        String channelId = annotation.value();
        if (channelId == null || channelId.isBlank()) {
            throw new IllegalArgumentException("更新渠道标识不能为空");
        }

        String key = channelId.toLowerCase();
        if (!key.equalsIgnoreCase("plugin")) {
            throw new IllegalArgumentException("插件更新渠道必须为 \"plugin\"");
        }

        String pluginId = updateInstance.getPluginId();
        if (pluginId == null || pluginId.isBlank()) {
            throw new IllegalArgumentException("插件更新渠道必须指定插件 ID");
        }
        pluginChannels.put(pluginId.toLowerCase(), updateInstance);
        debug("注册插件专属更新渠道: {0}", pluginId);
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
     * 获取已知渠道名的更新实例缓存
     * @param pluginId 插件ID
     * @param channelId 渠道ID
     * @return 渠道实例，没有缓存时返回 null
     */
    @Nullable
    AbstractUpdate getUpdateInstance(String pluginId, String channelId) {
        if (pluginId == null || pluginId.isBlank() || channelId == null || channelId.isBlank()) return null;

        return updateInstanceCache.getOrDefault(pluginId.toLowerCase() + ":" + channelId.toLowerCase(), null);
    }

    /**
     * 按需获取或创建指定渠道的更新实例
     */
    @Nullable
    AbstractUpdate getUpdateInstance(String pluginId, ChannelConfig candidate) {
        if (pluginId == null || pluginId.isBlank()) return null;

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
        if (channelType == null || channelType.isBlank()) return null;

        String cacheKey = pluginId + ":" + channelType.toLowerCase();

        // 插件专属渠道（渠道标识固定为 "plugin"）
        if ("plugin".equalsIgnoreCase(channelType)) {
            AbstractUpdate pluginUpdate = pluginChannels.get(pluginId);
            if (pluginUpdate != null) {
                updateInstanceCache.put(cacheKey, pluginUpdate);
                return pluginUpdate;
            }
            return null;
        }

        if (updateInstanceCache.containsKey(cacheKey)) {
            debug("{0}: 使用缓存更新实例，渠道 {1}", pluginId, channelType);
            return updateInstanceCache.get(cacheKey);
        }

        try {
            Object config = candidate.config();
            Class<? extends AbstractUpdate> channelClass = getChannelClass(channelType);
            if (channelClass == null) {
                logger.warning(tr("message.service.channel.error.unknown", channelType));
                return null;
            }

            AbstractUpdate configUpdate = createWithDescriptor(channelClass, pluginId, config);
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
     * 1. 插件专属渠道（若存在）
     * 2. 用户显式选择的渠道（若存在于候选中）
     * 3. 其余渠道按声明顺序
     */
    private List<ChannelConfig> buildChannelCandidates(String pluginId, UpdateConfig config) {
        List<ChannelConfig> channels = new ArrayList<>();

        // 插件专属渠道（若有）优先
        AbstractUpdate pluginUpdate = pluginChannels.get(pluginId.toLowerCase());
        if (pluginUpdate != null) {
            channels.add(new ChannelConfig("plugin", new JsonObject(), null));
        }

        if (config != null && config.channels() != null) {
            for (ChannelConfig c : config.channels()) {
                if (c == null || c.type() == null) continue;
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

        String selectedId = config == null ? null : config.selectedChannel();
        if (selectedId == null || selectedId.isBlank()) {
            return channels;
        }

        int selectedIndex = -1;
        for (int i = 0; i < channels.size(); i++) {
            if (channels.get(i).type().equalsIgnoreCase(selectedId)) {
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
        Map<String, ChannelConfig> globalById = new LinkedHashMap<>();
        if (globalChannels != null) {
            for (ChannelConfig channel : globalChannels) {
                if (channel == null || channel.type() == null || channel.type().isBlank()) continue;
                String channelId = channel.type().toLowerCase();
                if (getChannelClass(channelId) == null) {
                    continue; // 未知渠道，丢弃
                }
                globalById.put(channelId, channel);
            }
        }

        List<ChannelConfig> merged = new ArrayList<>();
        if (pluginChannels != null) {
            for (ChannelConfig pluginChannel : pluginChannels) {
                if (pluginChannel == null || pluginChannel.type() == null || pluginChannel.type().isBlank()) continue;
                String pluginChannelId = pluginChannel.type().toLowerCase();
                if (getChannelClass(pluginChannelId) == null) {
                    continue; // 未知渠道，丢弃
                }
                ChannelConfig globalChannel = globalById.get(pluginChannelId);
                Object mergedConfig = mergeConfig(pluginChannel.config(), globalChannel == null ? null : globalChannel.config());
                merged.add(new ChannelConfig(pluginChannel.type(), mergedConfig, pluginChannel.lastUpdate()));
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

    private Class<? extends AbstractUpdate> getChannelClass(String channelId) {
        return channelId == null ? null : genericChannels.get(channelId.toLowerCase());
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

    /**
     * 创建更新渠道实例<br>
     * 将合并后的配置转换为 {@link JsonObject}，使用约定构造器 {@code (String, JsonObject, Platform)} 反射实例化；渠道实现自行解析配置并应用默认值
     */
    private AbstractUpdate createWithDescriptor(Class<? extends AbstractUpdate> channelClass, String pluginId, Object config) {
        JsonObject configJson = toConfigJson(config);
        try {
            Constructor<? extends AbstractUpdate> constructor = channelClass.getConstructor(String.class, JsonObject.class, Platform.class);
            return constructor.newInstance(pluginId, configJson, platform);
        } catch (NoSuchMethodException e) {
            throw new IllegalArgumentException("更新渠道类缺少约定构造器 (String, JsonObject, Platform): " + channelClass.getName());
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalArgumentException("实例化更新渠道失败: " + channelClass.getName(), cause);
        } catch (ReflectiveOperationException e) {
            throw new IllegalArgumentException("实例化更新渠道失败: " + channelClass.getName(), e);
        }
    }

    /**
     * 将渠道配置转换为 {@link JsonObject}（配置为 null 时返回空对象）
     */
    private JsonObject toConfigJson(Object config) {
        if (config == null) {
            return new JsonObject();
        }
        JsonElement tree = Utils.getGson().toJsonTree(config);
        return tree.isJsonObject() ? tree.getAsJsonObject() : new JsonObject();
    }
}
