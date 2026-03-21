package me.dreamvoid.universalpluginupdater.plugin;

import com.google.gson.Gson;
import me.dreamvoid.universalpluginupdater.Utils;
import me.dreamvoid.universalpluginupdater.objects.ChannelConfig;
import me.dreamvoid.universalpluginupdater.objects.channel.ModrinthChannelInfo;
import me.dreamvoid.universalpluginupdater.objects.PluginUpdateConfig;
import me.dreamvoid.universalpluginupdater.objects.channel.UrlChannelInfo;
import me.dreamvoid.universalpluginupdater.platform.IPlatformProvider;
import me.dreamvoid.universalpluginupdater.update.AbstractUpdate;
import me.dreamvoid.universalpluginupdater.update.ModrinthUpdate;
import me.dreamvoid.universalpluginupdater.update.URLUpdate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * 更新渠道管理器
 * 负责读取配置文件并选择合适的更新渠道
 */
public class UpdateChannelManager {
    private static final Gson gson = new Gson();
    private static final Logger logger = Utils.getLogger();

    private final IPlatformProvider platform;
    /**
     * 缓存AbstractUpdate实例，键为"pluginId:channelType"
     * 这样可以保留HTTP缓存信息（lastModified）供后续请求使用
     */
    private final Map<String, AbstractUpdate> updateInstanceCache = new HashMap<>();

    public UpdateChannelManager(IPlatformProvider platform) {
        this.platform = platform;
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
        String lowerPluginId = pluginId.toLowerCase();

        // 读取配置文件
        PluginUpdateConfig config = loadPluginConfig(lowerPluginId);
        if (config == null || config.getChannels() == null || config.getChannels().isEmpty()) {
            return null;
        }

        // 获取选择的渠道
        ChannelConfig selectedChannelConfig = selectChannel(config);
        if (selectedChannelConfig == null) {
            return null;
        }

        // 生成缓存键
        String cacheKey = lowerPluginId + ":" + selectedChannelConfig.getType();
        
        // 检查缓存中是否已存在该实例
        if (updateInstanceCache.containsKey(cacheKey)) {
            return updateInstanceCache.get(cacheKey);
        }

        // 缓存中不存在，创建新实例
        AbstractUpdate updateInstance = createUpdateInstance(selectedChannelConfig);
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
    private PluginUpdateConfig loadPluginConfig(String pluginId) {
        try {
            Path configPath = getChannelConfigPath(pluginId);
            if (!Files.exists(configPath)) {
                return null;
            }

            String jsonContent = new String(Files.readAllBytes(configPath));
            return gson.fromJson(jsonContent, PluginUpdateConfig.class);
        } catch (IOException e) {
            logger.warning("无法加载 " + pluginId + " 的更新配置");
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
    private ChannelConfig selectChannel(PluginUpdateConfig config) {
        List<ChannelConfig> channels = config.getChannels();
        if (channels == null || channels.isEmpty()) {
            return null;
        }

        // 如果只有一个渠道，直接使用
        if (channels.size() == 1) {
            return channels.getFirst();
        }

        // 多个渠道的情况
        String selectedChannel = config.getSelectedChannel();
        if (selectedChannel != null && !selectedChannel.isEmpty()) {
            // 用户选择了特定的渠道
            for (ChannelConfig channel : channels) {
                if (selectedChannel.equalsIgnoreCase(channel.getType())) {
                    return channel;
                }
            }
        }

        // 用户选择的渠道不存在或没有选择，返回第一个可用渠道
        return channels.getFirst();
    }

    /**
     * 根据渠道配置创建对应的AbstractUpdate实现
     * @param channelConfig 渠道配置
     * @return AbstractUpdate实现实例
     */
    private AbstractUpdate createUpdateInstance(ChannelConfig channelConfig) {
        String type = channelConfig.getType();
        Object config = channelConfig.getConfig();

        if (type == null || config == null) {
            return null;
        }

        try {
            switch (type.toLowerCase()) {
                case "url" -> {
                    UrlChannelInfo urlInfo = gson.fromJson(gson.toJsonTree(config), UrlChannelInfo.class);
                    if (urlInfo.getUrl() != null) {
                        return new URLUpdate(urlInfo.getUrl(), platform);
                    }
                }
                case "modrinth" -> {
                    ModrinthChannelInfo modrinthInfo = gson.fromJson(gson.toJsonTree(config), ModrinthChannelInfo.class);
                    if (modrinthInfo.getProjectId() != null) {
                        return new ModrinthUpdate(modrinthInfo.getProjectId(), platform);
                    }
                }
                default -> logger.warning("未知更新渠道: " + type);
            }
        } catch (Exception e) {
                logger.warning("Failed to create update instance for channel type: " + type);
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
}
