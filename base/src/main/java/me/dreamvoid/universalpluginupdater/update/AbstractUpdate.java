package me.dreamvoid.universalpluginupdater.update;

import me.dreamvoid.universalpluginupdater.Utils;
import me.dreamvoid.universalpluginupdater.platform.Platform;
import me.dreamvoid.universalpluginupdater.service.UpdateManager;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.logging.Logger;

/**
 * 更新渠道抽象基类<br>
 * 实现类需直接继承本类：通用渠道在类上标注 {@link UpdateChannel} 并提供约定构造器 {@code (String, JsonObject, Platform)}（自行解析渠道配置并应用默认值），插件专属渠道实现（渠道标识固定为 "plugin"）
 */
public abstract class AbstractUpdate {
    protected final String pluginId;
    protected final Platform platform;
    protected final Logger logger;

    protected AbstractUpdate(String pluginId, Platform platform) {
        this.pluginId = pluginId;
        this.platform = platform;
        this.logger = platform.getPlatformLogger();
    }

    /**
     * 执行更新检查，联网获取最新版本信息<br>
     * 此方法应当在用户执行 /upu update 时调用
     * @return 检查更新是否成功
     */
    public abstract boolean checkUpdate();

    /**
     * 下载更新文件<br>
     * 此方法应当在用户执行 /upu download 时调用，文件保存到 {@link UpdateManager#getDownloadPath()} 目录
     * @return 下载成功时返回插件文件路径，失败返回 null
     */
    @Nullable
    public abstract Path download();

    /**
     * 获取更新的版本名
     * @return 新版本
     */
    @Nullable
    public abstract String getVersion();

    /**
     * 是否存在更新<br>
     * 将缓存的远程最新版本与本地插件版本进行语义化版本比较，本地版本较旧时返回 true
     * @return 本地版本较旧时返回 true
     */
    public boolean hasUpdate() {
        String newVersion = getVersion();
        if (newVersion != null) {
            return Utils.isVersionNewer(newVersion, platform.getPluginVersion(getPluginId()));
        } else {
            return false;
        }
    }

    /**
     * 获取当前更新实例对应的插件 ID
     * @return 插件 ID
     */
    public final String getPluginId() {
        return pluginId;
    }

    /**
     * 获取更新渠道标识<br>
     * @return 渠道标识
     */
    public final String getChannelId(){
        UpdateChannel annotation = getClass().getAnnotation(UpdateChannel.class);
        return annotation != null ? annotation.value() : null;
    };
}
